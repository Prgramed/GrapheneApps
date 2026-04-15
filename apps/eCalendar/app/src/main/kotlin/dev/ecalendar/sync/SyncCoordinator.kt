package dev.ecalendar.sync

import dev.ecalendar.alarm.EventAlarmScheduler
import dev.ecalendar.caldav.CalDavClient
import dev.ecalendar.caldav.CalDavDiscovery
import dev.ecalendar.caldav.CalDavSyncEngine
import dev.ecalendar.caldav.ICalSubscriptionSyncer
import dev.ecalendar.caldav.ZohoRateLimitInterceptor
import dev.ecalendar.data.CredentialStore
import dev.ecalendar.data.db.dao.AccountDao
import dev.ecalendar.data.db.dao.CalendarDao
import dev.ecalendar.data.db.dao.EventDao
import dev.ecalendar.data.db.entity.toDomain
import dev.ecalendar.domain.model.AccountType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Error(val message: String) : SyncState()
    data class LastSyncedAt(val timestamp: Long) : SyncState()
}

@Singleton
class SyncCoordinator @Inject constructor(
    private val accountDao: AccountDao,
    private val calendarDao: CalendarDao,
    private val eventDao: EventDao,
    private val credentialStore: CredentialStore,
    private val offlineQueueProcessor: OfflineQueueProcessor,
    private val alarmScheduler: EventAlarmScheduler,
    private val baseOkHttpClient: OkHttpClient,
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Per-account rate limiters for Zoho (persist across syncs within same session)
    private val zohoLimiters = mutableMapOf<Long, ZohoRateLimitInterceptor>()

    // Application-lifetime scope for UI-initiated syncs. Using viewModelScope meant a
    // sync got cancelled as soon as the user left the Settings screen mid-sync; here
    // the sync survives navigation and finishes in the background.
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeSyncJob: Job? = null

    /** Fire-and-forget launch that survives the caller's ViewModel being cleared. */
    fun launchSync(includeMirror: Boolean = true) {
        // Coalesce: if a sync is already running, don't start another.
        if (activeSyncJob?.isActive == true) return
        activeSyncJob = syncScope.launch {
            syncAll(includeMirror = includeMirror)
        }
    }

    suspend fun syncAll(includeMirror: Boolean = true) {
        _syncState.value = SyncState.Syncing
        var errors = 0
        val errorMessages = mutableListOf<String>()

        try {
            val accounts = accountDao.getEnabled().map { it.toDomain() }
            val icalAccounts = accounts.filter { it.type == AccountType.ICAL_SUBSCRIPTION }
            val caldavAccounts = accounts.filter { it.type != AccountType.ICAL_SUBSCRIPTION }

            // Sync iCal subscriptions first (mirror to Synology before CalDAV sync reads them).
            // Skipped on fast/pull-to-refresh syncs — the iCal mirror is slow (serial PUTs of
            // every event) and the background SyncWorker handles it on its own cadence.
            val synoAccount = caldavAccounts.firstOrNull { it.type == AccountType.SYNOLOGY }
            if (includeMirror && icalAccounts.isNotEmpty() && synoAccount != null) {
                val synoPw = credentialStore.getPassword(synoAccount.id) ?: ""
                val synoClient = CalDavClient(synoAccount.baseUrl, synoAccount.username, synoPw, baseOkHttpClient)

                // Discover Synology calendar home URL
                val synoHomeUrl = try {
                    val discovery = CalDavDiscovery.discoverCalendarHomeUrl(synoClient, synoAccount.baseUrl)
                    discovery ?: synoAccount.baseUrl
                } catch (_: Exception) {
                    synoAccount.baseUrl
                }

                // Parallelize iCal subscription syncs — each one downloads a
                // potentially large ICS and PUTs dozens of events to Synology.
                // Sequentially this dominates sync time; 3 in flight keeps it
                // fast without hammering the server.
                coroutineScope {
                    val icalSemaphore = Semaphore(3)
                    icalAccounts.map { ical ->
                        async {
                            icalSemaphore.withPermit {
                                try {
                                    ICalSubscriptionSyncer.sync(
                                        subscriptionUrl = ical.baseUrl,
                                        displayName = ical.displayName,
                                        synoAccountId = synoAccount.id,
                                        synoClient = synoClient,
                                        synoCalHomeUrl = synoHomeUrl,
                                        httpClient = baseOkHttpClient,
                                        calendarDao = calendarDao,
                                    )
                                } catch (e: Exception) {
                                    errors++
                                    synchronized(errorMessages) {
                                        errorMessages += "${ical.displayName}: ${e.message ?: e.javaClass.simpleName}"
                                    }
                                    Timber.w(e, "iCal subscription sync failed for ${ical.displayName}")
                                }
                            }
                        }
                    }
                }
            }

            coroutineScope {
                val semaphore = Semaphore(3)
                for (account in caldavAccounts) {

                    val password = credentialStore.getPassword(account.id) ?: ""
                    val rateLimiter = if (account.type == AccountType.ZOHO) {
                        zohoLimiters.getOrPut(account.id) { ZohoRateLimitInterceptor() }
                    } else null
                    val client = CalDavClient(account.baseUrl, account.username, password, baseOkHttpClient, rateLimiter)
                    val sources = calendarDao.getByAccountId(account.id).map { it.toDomain() }

                    for (source in sources) {
                        async {
                            semaphore.withPermit {
                                try {
                                    CalDavSyncEngine.sync(client, source, eventDao, calendarDao)
                                } catch (e: Exception) {
                                    errors++
                                    synchronized(errorMessages) {
                                        errorMessages += "${source.displayName}: ${e.message ?: e.javaClass.simpleName}"
                                    }
                                    Timber.w(e, "Sync failed for ${source.displayName} (${source.calDavUrl})")
                                }
                            }
                        }
                    }
                }
            }

            // Drain offline queue
            try {
                offlineQueueProcessor.drainQueue()
            } catch (e: Exception) {
                Timber.w(e, "Queue drain failed")
            }

            // Refresh alarms from Room after sync
            try {
                alarmScheduler.rescheduleAll()
            } catch (e: Exception) {
                Timber.w(e, "Alarm reschedule failed")
            }

            // Cleanup old instances (> 90 days ago)
            try {
                val cutoff = LocalDate.now().minusDays(90)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
                eventDao.deleteOldInstances(cutoff)
            } catch (_: Exception) { }

            _syncState.value = if (errors > 0) {
                SyncState.Error(errorMessages.firstOrNull() ?: "$errors source(s) failed")
            } else {
                SyncState.LastSyncedAt(System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Timber.e(e, "syncAll failed")
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
        }
    }
}
