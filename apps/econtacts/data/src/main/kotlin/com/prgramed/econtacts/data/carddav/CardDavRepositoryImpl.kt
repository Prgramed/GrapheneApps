package com.prgramed.econtacts.data.carddav

import android.content.Context
import android.content.SharedPreferences
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.prgramed.econtacts.domain.model.CardDavAccount
import com.prgramed.econtacts.domain.model.SyncResult
import com.prgramed.econtacts.domain.repository.CardDavRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardDavRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cardDavClient: CardDavClient,
    private val syncEngine: CardDavSyncEngine,
    private val workManager: WorkManager,
) : CardDavRepository {

    private val _account = MutableStateFlow<CardDavAccount?>(null)

    // Use regular SharedPreferences — device encryption (FBE) already protects app-private data.
    // EncryptedSharedPreferences causes issues on reinstall due to Keystore key invalidation.
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("econtacts_carddav_prefs", Context.MODE_PRIVATE)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { loadAccount() }
    }

    override suspend fun sync(): SyncResult {
        val account = _account.value ?: return SyncResult(errors = listOf("No account configured"))
        val password = getPassword() ?: return SyncResult(errors = listOf("No password stored"))
        return try {
            syncEngine.sync(account, password)
        } catch (e: SecurityException) {
            SyncResult(errors = listOf("Network permission denied. Enable network access for eContacts in Settings > Apps > eContacts."))
        } catch (e: Exception) {
            SyncResult(errors = listOf("Sync failed: ${e.message}"))
        }
    }

    override fun getAccount(): Flow<CardDavAccount?> = _account

    override suspend fun saveAccount(account: CardDavAccount, password: String) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_SERVER_URL, account.serverUrl)
                .putString(KEY_USERNAME, account.username)
                .putString(KEY_ADDRESS_BOOK_PATH, account.addressBookPath)
                .putString(KEY_PASSWORD, password)
                .apply()
            _account.value = account
            enqueuePeriodicSync()
        }
    }

    override suspend fun removeAccount() {
        withContext(Dispatchers.IO) {
            prefs.edit().clear().apply()
            _account.value = null
            workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
        }
    }

    override suspend fun testConnection(
        serverUrl: String,
        username: String,
        password: String,
    ): Boolean = try {
        cardDavClient.testConnection(serverUrl, username, password)
    } catch (_: SecurityException) {
        false
    }

    override suspend fun discover(
        serverUrl: String,
        username: String,
        password: String,
    ): String? = try {
        cardDavClient.discover(serverUrl, username, password)
    } catch (_: Exception) {
        null
    }

    override suspend fun ensurePeriodicSyncIfConfigured() {
        if (_account.value != null) {
            enqueuePeriodicSync()
        }
    }

    private fun enqueuePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun getPassword(): String? = try {
        prefs.getString(KEY_PASSWORD, null)
    } catch (_: Exception) {
        null
    }

    private fun loadAccount() {
        try {
            val url = prefs.getString(KEY_SERVER_URL, null) ?: return
            val username = prefs.getString(KEY_USERNAME, null) ?: return
            val path = prefs.getString(KEY_ADDRESS_BOOK_PATH, null) ?: ""
            _account.value = CardDavAccount(url, username, path)
        } catch (_: Exception) {
            // Preferences corrupted — start fresh
        }
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_ADDRESS_BOOK_PATH = "address_book_path"
        private const val KEY_PASSWORD = "password"
    }
}
