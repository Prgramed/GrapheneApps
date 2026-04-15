package dev.ecalendar.data.repository

import dev.ecalendar.caldav.CalDavClient
import dev.ecalendar.caldav.CalDavDiscovery
import dev.ecalendar.caldav.DiscoveryResult
import dev.ecalendar.data.CredentialStore
import dev.ecalendar.data.db.dao.AccountDao
import dev.ecalendar.data.db.dao.CalendarDao
import dev.ecalendar.data.db.entity.CalendarSourceEntity
import dev.ecalendar.data.db.entity.toDomain
import dev.ecalendar.data.db.entity.toEntity
import dev.ecalendar.data.preferences.AppPreferencesRepository
import dev.ecalendar.domain.model.AccountType
import dev.ecalendar.domain.model.CalendarAccount
import dev.ecalendar.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val calendarDao: CalendarDao,
    private val credentialStore: CredentialStore,
    private val preferencesRepository: AppPreferencesRepository,
    private val baseOkHttpClient: OkHttpClient,
) : AccountRepository {

    override fun observeAccounts(): Flow<List<CalendarAccount>> =
        accountDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun addAccount(account: CalendarAccount, password: String): DiscoveryResult {
        // Store account
        val accountId = accountDao.upsert(account.toEntity())

        // iCal subscriptions don't use CalDAV discovery — just save the account
        if (account.type == AccountType.ICAL_SUBSCRIPTION) {
            return DiscoveryResult.Success(emptyList())
        }

        // Store credentials
        credentialStore.setPassword(accountId, password)

        // Discover calendars
        val client = CalDavClient(account.baseUrl, account.username, password, baseOkHttpClient)
        val result = CalDavDiscovery.discoverAccount(client, account.baseUrl)

        if (result is DiscoveryResult.Success) {
            // Save discovered calendars, collecting row ids so we can promote the
            // first writable one to the user's default if no default is set yet.
            var firstWritableId: Long? = null
            for (cal in result.calendars) {
                val rowId = calendarDao.upsert(
                    CalendarSourceEntity(
                        accountId = accountId,
                        calDavUrl = cal.url,
                        displayName = cal.displayName,
                        colorHex = cal.colorHex ?: "#4285F4",
                        isReadOnly = !cal.isWritable,
                    ),
                )
                if (cal.isWritable && firstWritableId == null) firstWritableId = rowId
            }

            // Promote first writable calendar to default, so newly-created events
            // are automatically associated with a remote source and sync-queued.
            // Previously the default stayed at 0 (local-only) until the user set
            // it manually, which caused every new event to be silently unsynced.
            val currentDefault = preferencesRepository.preferencesFlow.first().defaultCalendarSourceId
            if (currentDefault == 0L && firstWritableId != null) {
                preferencesRepository.updateDefaultCalendar(firstWritableId)
            }
        } else {
            // Discovery failed — clean up
            accountDao.delete(accountId)
            credentialStore.deletePassword(accountId)
        }

        return result
    }

    override suspend fun updateAccount(account: CalendarAccount, password: String): DiscoveryResult {
        require(account.id > 0) { "updateAccount called on account with no id" }
        // Write the (possibly changed) fields and password first — if discovery
        // fails we don't want to leave an inconsistent account behind, but we
        // also don't want to delete a pre-existing working account.
        accountDao.upsert(account.toEntity())
        credentialStore.setPassword(account.id, password)

        if (account.type == AccountType.ICAL_SUBSCRIPTION) {
            return DiscoveryResult.Success(emptyList())
        }

        val client = CalDavClient(account.baseUrl, account.username, password, baseOkHttpClient)
        val result = CalDavDiscovery.discoverAccount(client, account.baseUrl)

        if (result is DiscoveryResult.Success) {
            // Refresh calendar list. Drop existing non-mirror rows for this account
            // and re-insert; mirror calendars are owned by iCal subscription sync
            // and shouldn't be touched here.
            val existing = calendarDao.getByAccountId(account.id)
            for (src in existing) {
                if (!src.isMirror) calendarDao.deleteById(src.id)
            }
            for (cal in result.calendars) {
                calendarDao.upsert(
                    CalendarSourceEntity(
                        accountId = account.id,
                        calDavUrl = cal.url,
                        displayName = cal.displayName,
                        colorHex = cal.colorHex ?: "#4285F4",
                        isReadOnly = !cal.isWritable,
                    ),
                )
            }
        }

        return result
    }

    override suspend fun deleteAccount(id: Long) {
        // Delete all calendar sources for this account
        val sources = calendarDao.getByAccountId(id)
        sources.forEach { calendarDao.deleteById(it.id) }

        // Delete account + credential
        accountDao.delete(id)
        credentialStore.deletePassword(id)
    }

    override suspend fun getById(id: Long): CalendarAccount? =
        accountDao.getById(id)?.toDomain()
}
