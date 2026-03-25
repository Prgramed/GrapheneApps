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
import dev.ecalendar.domain.model.AccountType
import dev.ecalendar.domain.model.CalendarAccount
import dev.ecalendar.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val calendarDao: CalendarDao,
    private val credentialStore: CredentialStore,
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
            // Save discovered calendars
            for (cal in result.calendars) {
                calendarDao.upsert(
                    CalendarSourceEntity(
                        accountId = accountId,
                        calDavUrl = cal.url,
                        displayName = cal.displayName,
                        colorHex = cal.colorHex ?: "#4285F4",
                        isReadOnly = !cal.isWritable,
                    ),
                )
            }
        } else {
            // Discovery failed — clean up
            accountDao.delete(accountId)
            credentialStore.deletePassword(accountId)
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
