package dev.egallery.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.api.ImmichPhotoService
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.db.dao.UploadQueueDao
import dev.egallery.data.preferences.AppPreferencesRepository
import dev.egallery.sync.NasSyncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val version: String = "") : ConnectionTestState
    data class Failed(val message: String) : ConnectionTestState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialStore: CredentialStore,
    private val preferencesRepository: AppPreferencesRepository,
    private val syncEngine: NasSyncEngine,
    private val uploadQueueDao: UploadQueueDao,
    private val mediaDao: MediaDao,
    private val immichApi: ImmichPhotoService,
    private val uploadStatus: dev.egallery.sync.UploadStatus,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    val serverUrl = MutableStateFlow(credentialStore.serverUrl)
    val apiKey = MutableStateFlow(credentialStore.apiKey)

    private val _connectionTest = MutableStateFlow<ConnectionTestState>(
        if (credentialStore.isConfigured) ConnectionTestState.Success() else ConnectionTestState.Idle,
    )
    val connectionTest: StateFlow<ConnectionTestState> = _connectionTest.asStateFlow()

    val wifiOnlyUpload: StateFlow<Boolean> = preferencesRepository.wifiOnlyUpload
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoUploadEnabled: StateFlow<Boolean> = preferencesRepository.autoUploadEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoDeleteCovers: StateFlow<Boolean> = preferencesRepository.autoDeleteCovers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoEvictEnabled: StateFlow<Boolean> = preferencesRepository.autoEvictEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val uploadConcurrency: StateFlow<Int> = preferencesRepository.uploadConcurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    fun setUploadConcurrency(count: Int) {
        viewModelScope.launch { preferencesRepository.setUploadConcurrency(count) }
    }

    val lastSyncAt: StateFlow<Long> = preferencesRepository.lastSyncAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val pendingUploadCount: StateFlow<Int> = uploadQueueDao.getPendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedUploadCount: StateFlow<Int> = uploadQueueDao.getFailedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalUploadCount: StateFlow<Int> = uploadQueueDao.getAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val syncStatus: StateFlow<String> = syncEngine.progress
    val isSyncing: StateFlow<Boolean> = syncEngine.isRunning
    val uploadProgress: StateFlow<String> = uploadStatus.progress
    val isUploading: StateFlow<Boolean> = uploadStatus.isRunning

    private val _localStorageBytes = MutableStateFlow(0L)
    val localStorageBytes: StateFlow<Long> = _localStorageBytes.asStateFlow()

    init {
        viewModelScope.launch {
            _localStorageBytes.value = mediaDao.getLocalStorageBytes()
        }
    }

    fun saveCredentials() {
        credentialStore.serverUrl = serverUrl.value.trimEnd('/')
        credentialStore.apiKey = apiKey.value.trim()
    }

    fun testConnection() {
        saveCredentials()
        if (credentialStore.serverUrl.isBlank() || credentialStore.apiKey.isBlank()) {
            _connectionTest.value = ConnectionTestState.Failed("Server URL and API Key are required")
            return
        }

        viewModelScope.launch {
            _connectionTest.value = ConnectionTestState.Testing
            try {
                val info = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    immichApi.getServerInfo()
                }
                if (info != null) {
                    _connectionTest.value = ConnectionTestState.Success(info.version)
                    // Clear Coil caches on new connection
                    val imageLoader = coil3.SingletonImageLoader.get(appContext)
                    imageLoader.memoryCache?.clear()
                    imageLoader.diskCache?.clear()
                    Timber.d("Connected to Immich ${info.version}")
                } else {
                    _connectionTest.value = ConnectionTestState.Failed("Connection timed out")
                }
            } catch (e: Exception) {
                Timber.e(e, "Connection test failed")
                _connectionTest.value = ConnectionTestState.Failed(e.message?.take(80) ?: "Connection failed")
            }
        }
    }

    fun setAutoUploadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoUploadEnabled(enabled)
            val wm = androidx.work.WorkManager.getInstance(appContext)
            if (enabled) {
                val wifiOnly = preferencesRepository.wifiOnlyUpload.first()
                val networkType = if (wifiOnly) androidx.work.NetworkType.UNMETERED else androidx.work.NetworkType.CONNECTED
                val request = androidx.work.PeriodicWorkRequestBuilder<dev.egallery.sync.UploadScanWorker>(
                    30, java.util.concurrent.TimeUnit.MINUTES,
                ).setConstraints(
                    androidx.work.Constraints.Builder().setRequiredNetworkType(networkType).build(),
                ).build()
                wm.enqueueUniquePeriodicWork(
                    dev.egallery.sync.UploadScanWorker.PERIODIC_WORK_NAME,
                    androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                    request,
                )
                // Also run immediately
                scanAndUploadNow()
            } else {
                wm.cancelUniqueWork(dev.egallery.sync.UploadScanWorker.PERIODIC_WORK_NAME)
            }
        }
    }

    private val _isScanning = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isScanning: kotlinx.coroutines.flow.StateFlow<Boolean> = _isScanning

    private val _scanStatus = kotlinx.coroutines.flow.MutableStateFlow("")
    val scanStatus: kotlinx.coroutines.flow.StateFlow<String> = _scanStatus

    fun scanAndUploadNow() {
        if (_isScanning.value) return // Already running
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isScanning.value = true
            _scanStatus.value = "Scanning device photos..."
            try {
                val queued = syncEngine.scanAndQueueUploads()
                _scanStatus.value = if (queued > 0) "$queued new photos queued for upload" else "No new photos found"
            } catch (e: Exception) {
                _scanStatus.value = "Scan failed: ${e.message?.take(50)}"
                timber.log.Timber.w(e, "Manual upload scan failed")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun setAutoDeleteCovers(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoDeleteCovers(enabled) }
    }

    fun setWifiOnlyUpload(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setWifiOnlyUpload(enabled) }
    }

    fun setAutoEvictEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoEvictEnabled(enabled) }
    }

    fun quickSync() {
        syncEngine.startQuickSync()
    }

    fun forceFullResync() {
        syncEngine.startFullSync()
    }

    fun cancelSync() {
        syncEngine.cancelSync()
    }

    fun triggerUpload() {
        androidx.work.WorkManager.getInstance(appContext).enqueueUniqueWork(
            dev.egallery.sync.UploadWorker.WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            androidx.work.OneTimeWorkRequestBuilder<dev.egallery.sync.UploadWorker>()
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build(),
                )
                .build(),
        )
    }

    private val _failedPaths = MutableStateFlow<List<String>>(emptyList())
    val failedPaths: StateFlow<List<String>> = _failedPaths.asStateFlow()

    fun loadFailedPaths() {
        viewModelScope.launch {
            val failed = uploadQueueDao.getFailed()
            _failedPaths.value = failed.map { java.io.File(it.localPath).name }
        }
    }

    fun clearFailedUploads() {
        viewModelScope.launch {
            val failed = uploadQueueDao.getFailed()
            for (item in failed) {
                uploadQueueDao.delete(item.id)
                mediaDao.getByLocalPath(item.localPath)?.let { entity ->
                    if (entity.storageStatus == "DEVICE") {
                        mediaDao.updateStorageStatus(entity.nasId, "SYNCED", entity.localPath)
                    }
                }
            }
            val failedMedia = mediaDao.getByStorageStatus("DEVICE")
            for (entity in failedMedia) {
                mediaDao.updateStorageStatus(entity.nasId, "SYNCED", entity.localPath)
            }
            _failedPaths.value = emptyList()
        }
    }

    fun cancelUpload() {
        androidx.work.WorkManager.getInstance(appContext)
            .cancelUniqueWork(dev.egallery.sync.UploadWorker.WORK_NAME)
        uploadStatus.update("Cancelled")
        uploadStatus.setRunning(false)
    }

    fun retryFailedUploads() {
        viewModelScope.launch {
            var requeued = 0
            timber.log.Timber.d("retryFailedUploads: checking queue and media DB...")

            // 1. Reset queue items marked FAILED
            val failedQueue = uploadQueueDao.getFailed()
            timber.log.Timber.d("retryFailedUploads: ${failedQueue.size} failed in queue")
            for (item in failedQueue) {
                uploadQueueDao.updateStatus(item.id, "PENDING", 0)
                val entity = mediaDao.getByLocalPath(item.localPath)
                if (entity != null) {
                    mediaDao.updateStorageStatus(entity.nasId, "DEVICE", entity.localPath)
                }
                requeued++
            }

            // 2. Find orphaned UPLOAD_FAILED media entities with no queue entry
            val failedMedia = mediaDao.getByStorageStatus("DEVICE")
            timber.log.Timber.d("retryFailedUploads: ${failedMedia.size} UPLOAD_FAILED in media DB")
            for (entity in failedMedia) {
                // Try to find the file: check localPath, then search by filename in DCIM
                var filePath = entity.localPath
                if (filePath == null || !java.io.File(filePath).exists()) {
                    // Search DCIM/Camera for the file by filename
                    val dcim = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DCIM,
                    ).resolve("Camera")
                    val match = dcim.listFiles()?.find { it.name == entity.filename }
                    filePath = match?.absolutePath
                }

                if (filePath != null && java.io.File(filePath).exists()) {
                    uploadQueueDao.insert(
                        dev.egallery.data.db.entity.UploadQueueEntity(localPath = filePath, targetFolderId = 0),
                    )
                    mediaDao.updateStorageStatus(entity.nasId, "DEVICE", filePath)
                    requeued++
                    timber.log.Timber.d("Re-queued: ${entity.nasId} -> $filePath")
                } else {
                    // Truly orphaned — no file found anywhere
                    mediaDao.deleteByNasId(entity.nasId)
                    timber.log.Timber.d("Deleted truly orphaned: ${entity.nasId}")
                }
            }

            if (requeued > 0) {
                androidx.work.WorkManager.getInstance(appContext).enqueueUniqueWork(
                    dev.egallery.sync.UploadWorker.WORK_NAME,
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    androidx.work.OneTimeWorkRequestBuilder<dev.egallery.sync.UploadWorker>()
                        .setConstraints(
                            androidx.work.Constraints.Builder()
                                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                .build(),
                        )
                        .build(),
                )
                timber.log.Timber.d("Retrying $requeued failed uploads")
            }
        }
    }
}
