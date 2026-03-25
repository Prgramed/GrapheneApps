package dev.ecalendar.sync

import dev.ecalendar.alarm.EventNotificationManager
import dev.ecalendar.caldav.CalDavClient
import dev.ecalendar.data.CredentialStore
import dev.ecalendar.data.db.dao.AccountDao
import dev.ecalendar.data.db.dao.CalendarDao
import dev.ecalendar.data.db.dao.EventDao
import dev.ecalendar.data.db.dao.SyncQueueDao
import dev.ecalendar.data.db.entity.toDomain
import dev.ecalendar.domain.model.SyncOp
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineQueueProcessor @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val eventDao: EventDao,
    private val accountDao: AccountDao,
    private val calendarDao: CalendarDao,
    private val credentialStore: CredentialStore,
    private val notificationManager: EventNotificationManager,
    private val baseOkHttpClient: OkHttpClient,
) {
    private companion object {
        const val MAX_RETRIES = 3
    }

    /**
     * Drains the sync queue, processing one item at a time in createdAt order.
     * Should be called when network is available.
     */
    suspend fun drainQueue() {
        while (true) {
            val item = syncQueueDao.getOldest() ?: break

            if (item.retryCount >= MAX_RETRIES) {
                Timber.w("Queue item ${item.id} exceeded max retries, skipping: ${item.eventUid}")
                notificationManager.showSyncErrorNotification(
                    eventUid = item.eventUid,
                    title = item.eventUid,
                    message = "Failed to sync after $MAX_RETRIES attempts",
                )
                syncQueueDao.deleteById(item.id)
                continue
            }

            val account = accountDao.getById(item.accountId)?.toDomain()
            if (account == null) {
                Timber.w("Queue item ${item.id} has no account, removing")
                syncQueueDao.deleteById(item.id)
                continue
            }

            val password = credentialStore.getPassword(account.id) ?: ""
            val client = CalDavClient(account.baseUrl, account.username, password, baseOkHttpClient)

            // Guard: skip operations targeting mirror calendars
            if (item.calendarUrl.isNotBlank()) {
                val mirrorSource = calendarDao.getByUrl(item.calendarUrl)
                if (mirrorSource?.isMirror == true) {
                    Timber.d("Skipping queue item for mirror calendar: ${item.eventUid}")
                    syncQueueDao.deleteById(item.id)
                    continue
                }
            }

            val op = try { SyncOp.valueOf(item.operation) } catch (_: Exception) {
                syncQueueDao.deleteById(item.id); continue
            }

            val success = when (op) {
                SyncOp.CREATE -> processCreate(client, item.calendarUrl, item.eventUid, item.icsPayload)
                SyncOp.UPDATE -> processUpdate(client, item.eventUid, item.icsPayload)
                SyncOp.DELETE -> processDelete(client, item.eventUid)
            }

            if (success) {
                syncQueueDao.deleteById(item.id)
            } else {
                syncQueueDao.incrementRetry(item.id)
            }
        }
    }

    private suspend fun processCreate(
        client: CalDavClient,
        calendarUrl: String,
        uid: String,
        icsPayload: String?,
    ): Boolean {
        if (icsPayload == null) return true // Nothing to push

        val eventUrl = "${calendarUrl.trimEnd('/')}/$uid.ics"

        return try {
            val response = client.put(eventUrl, icsPayload, etag = null)
            when (response.code) {
                201, 204 -> {
                    // Success — mark as synced
                    val etag = response.header("ETag")?.trim('"') ?: ""
                    val series = eventDao.getSeriesByUid(uid)
                    if (series != null) {
                        eventDao.upsertSeries(series.copy(isLocal = false, etag = etag, serverUrl = eventUrl))
                    }
                    response.close()
                    Timber.d("CREATE pushed: $uid")
                    true
                }
                412 -> {
                    // UID conflict — already exists on server
                    response.close()
                    Timber.w("CREATE conflict for $uid (412), skipping")
                    true // Remove from queue — server has it
                }
                else -> {
                    Timber.w("CREATE failed: ${response.code} for $uid")
                    response.close()
                    false
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "CREATE exception for $uid")
            false
        }
    }

    private suspend fun processUpdate(
        client: CalDavClient,
        uid: String,
        icsPayload: String?,
    ): Boolean {
        if (icsPayload == null) return true

        val series = eventDao.getSeriesByUid(uid) ?: return true // Nothing to update
        val serverUrl = series.serverUrl
        if (serverUrl.isBlank()) return true

        return try {
            val response = client.put(serverUrl, icsPayload, etag = series.etag)
            when (response.code) {
                200, 201, 204 -> {
                    val newEtag = response.header("ETag")?.trim('"') ?: ""
                    eventDao.upsertSeries(series.copy(etag = newEtag, rawIcs = icsPayload))
                    response.close()
                    Timber.d("UPDATE pushed: $uid")
                    true
                }
                412 -> {
                    // ETag conflict — last-write-wins: discard local, re-fetch later
                    response.close()
                    Timber.w("UPDATE conflict for $uid (412), discarding local change")
                    true // Remove from queue
                }
                else -> {
                    Timber.w("UPDATE failed: ${response.code} for $uid")
                    response.close()
                    false
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "UPDATE exception for $uid")
            false
        }
    }

    private suspend fun processDelete(
        client: CalDavClient,
        uid: String,
    ): Boolean {
        val series = eventDao.getSeriesByUid(uid)
        val serverUrl = series?.serverUrl
        if (serverUrl.isNullOrBlank()) return true // Nothing to delete on server

        return try {
            val etag = series.etag.ifBlank { "*" }
            val response = client.delete(serverUrl, etag)
            when (response.code) {
                204, 200 -> {
                    response.close()
                    Timber.d("DELETE pushed: $uid")
                    true
                }
                404 -> {
                    // Already deleted on server
                    response.close()
                    Timber.d("DELETE: $uid already gone (404)")
                    true
                }
                else -> {
                    Timber.w("DELETE failed: ${response.code} for $uid")
                    response.close()
                    false
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "DELETE exception for $uid")
            false
        }
    }
}
