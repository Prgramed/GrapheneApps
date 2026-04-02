package dev.egallery.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.repository.AlbumRepository
import dev.egallery.data.preferences.AppPreferencesRepository
import dev.egallery.data.repository.MediaRepository
import dev.egallery.domain.model.Album
import dev.egallery.domain.model.MediaItem
import dev.egallery.sync.DeviceMediaScanner
import dev.egallery.sync.NasSyncEngine
import dev.egallery.sync.SyncState
import dev.egallery.sync.ThumbnailPrefetchWorker
import dev.egallery.util.ThumbnailUrlBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import timber.log.Timber
import javax.inject.Inject

sealed interface TimelineItem {
    data class DateHeader(val label: String) : TimelineItem
    data class PhotoCell(val item: MediaItem) : TimelineItem
}

@HiltViewModel
class TimelineViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val albumRepository: AlbumRepository,
    private val syncEngine: NasSyncEngine,
    private val deviceMediaScanner: DeviceMediaScanner,
    private val mediaDao: MediaDao,
    private val credentialStore: CredentialStore,
    private val preferencesRepository: AppPreferencesRepository,
    private val immichApi: dev.egallery.api.ImmichPhotoService,
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Server URL for UI (thumbnails, memories)
    val serverUrl: StateFlow<String> = MutableStateFlow(credentialStore.serverUrl)

    // Memories
    private val _memories = MutableStateFlow<List<dev.egallery.api.dto.ImmichMemory>>(emptyList())
    val memories: StateFlow<List<dev.egallery.api.dto.ImmichMemory>> = _memories.asStateFlow()

    fun fetchMemories() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _memories.value = immichApi.getMemories().filter { it.assets.isNotEmpty() }
            } catch (_: Exception) { }
        }
    }

    // Multi-select state
    private val _selectedNasIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNasIds: StateFlow<Set<String>> = _selectedNasIds.asStateFlow()
    val isMultiSelectMode: StateFlow<Boolean> = _selectedNasIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // #17: Scroll position in SavedStateHandle (survives process death)
    var savedScrollIndex: Int
        get() = savedStateHandle["scrollIndex"] ?: 0
        set(value) { savedStateHandle["scrollIndex"] = value }
    var savedScrollOffset: Int
        get() = savedStateHandle["scrollOffset"] ?: 0
        set(value) { savedStateHandle["scrollOffset"] = value }

    // Photo count
    private val _photoCount = MutableStateFlow(0)
    val photoCount: StateFlow<Int> = _photoCount.asStateFlow()

    // Albums for picker
    val albums: Flow<List<Album>> = albumRepository.observeAll()

    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    val timeline: Flow<PagingData<TimelineItem>> =
        mediaRepository.observeTimeline()
            .map { pagingData ->
                pagingData
                    .map<MediaItem, TimelineItem> { TimelineItem.PhotoCell(it) }
                    .insertSeparators { before, after ->
                        val beforeMonth = (before as? TimelineItem.PhotoCell)
                            ?.let { monthFormat.format(Date(it.item.captureDate)) }
                        val afterMonth = (after as? TimelineItem.PhotoCell)
                            ?.let { monthFormat.format(Date(it.item.captureDate)) }

                        when {
                            // First item — insert header
                            before == null && after != null -> TimelineItem.DateHeader(afterMonth!!)
                            // Month boundary
                            beforeMonth != null && afterMonth != null && beforeMonth != afterMonth ->
                                TimelineItem.DateHeader(afterMonth)
                            else -> null
                        }
                    }
            }
            .cachedIn(viewModelScope)

    fun syncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            _syncState.value = SyncState.Syncing
            try {
                deviceMediaScanner.quickScanRecentFiles()
            } catch (e: Exception) {
                Timber.w(e, "Quick scan failed")
            }
            _syncState.value = SyncState.Idle(System.currentTimeMillis())
            _photoCount.value = mediaRepository.getCount()
        }
    }

    fun toggleSelection(nasId: String) {
        _selectedNasIds.value = _selectedNasIds.value.let {
            if (nasId in it) it - nasId else it + nasId
        }
    }

    fun clearSelection() {
        _selectedNasIds.value = emptySet()
    }

    // Move selected to trash
    fun deleteSelected() {
        val ids = _selectedNasIds.value.toList()
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            ids.forEach { nasId -> mediaDao.trash(nasId, now) }
            _selectedNasIds.value = emptySet()
            _photoCount.value = mediaRepository.getCount()
        }
    }

    // #14: track failures
    fun addSelectedToAlbum(albumId: String) {
        val ids = _selectedNasIds.value.toList()
        viewModelScope.launch {
            var failures = 0
            for (nasId in ids) {
                val result = mediaRepository.addToAlbum(nasId, albumId)
                if (result.isFailure) failures++
            }
            _selectedNasIds.value = emptySet()
            if (failures > 0) {
                Timber.w("Failed to add $failures/${ids.size} items to album $albumId")
            }
        }
    }

    fun getDeviceFolders(): List<String> {
        return listOf("DCIM/Camera", "Pictures", "Pictures/Screenshots", "Download", "Movies")
            .map { "/storage/emulated/0/$it" }
            .filter { java.io.File(it).exists() }
            .sorted()
    }

    fun moveSelectedTo(destDir: String) {
        val ids = _selectedNasIds.value.toList()
        viewModelScope.launch(Dispatchers.IO) {
            for (nasId in ids) {
                val entity = mediaRepository.getItemDetail(nasId) ?: continue
                val srcPath = entity.localPath ?: continue
                if (srcPath.startsWith("content://")) continue
                val srcFile = java.io.File(srcPath)
                if (!srcFile.exists()) continue
                val destFile = java.io.File(destDir, entity.filename)
                destFile.parentFile?.mkdirs()
                try {
                    srcFile.copyTo(destFile, overwrite = true)
                    srcFile.delete()
                    mediaDao.updateStorageStatus(nasId, "ON_DEVICE", destFile.absolutePath)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to move $nasId")
                }
            }
            _selectedNasIds.value = emptySet()
        }
    }

    fun thumbnailUrl(nasId: String, cacheKey: String, isSharedSpace: Boolean = false): String {
        return ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, nasId)
    }

    fun thumbnailModel(item: MediaItem): Any {
        // 1. Local file (on-device photos, uploaded items that still have local copy)
        if (item.localPath != null) {
            if (item.localPath.startsWith("content://")) return android.net.Uri.parse(item.localPath)
            val file = java.io.File(item.localPath)
            if (file.exists()) return file
        }
        // 2. Server thumbnail
        if (credentialStore.serverUrl.isNotBlank()) {
            return ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, item.nasId)
        }
        return ""
    }

    fun prefetchVisibleThumbnails(visibleNasIds: List<String>) {
        if (visibleNasIds.isEmpty()) return
        val request = OneTimeWorkRequestBuilder<ThumbnailPrefetchWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                workDataOf(ThumbnailPrefetchWorker.KEY_PRIORITY_IDS to visibleNasIds.toTypedArray()),
            )
            .build()
        WorkManager.getInstance(appContext).enqueue(request)
    }

    // Auto-detect new photos/videos while app is open
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        private var pendingJob: kotlinx.coroutines.Job? = null
        override fun onChange(selfChange: Boolean) {
            // Debounce: wait 1.5s after last change before scanning
            pendingJob?.cancel()
            pendingJob = viewModelScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(1500)
                try {
                    val count = deviceMediaScanner.quickScanRecentFiles()
                    if (count > 0) _photoCount.value = mediaRepository.getCount()
                } catch (_: Exception) { }
            }
        }
    }

    init {
        viewModelScope.launch { _photoCount.value = mediaRepository.getCount() }
        viewModelScope.launch {
            preferencesRepository.lastSyncAt.collect { lastSyncAt ->
                if (_syncState.value is SyncState.Idle) {
                    _syncState.value = SyncState.Idle(lastSyncAt)
                }
                _photoCount.value = mediaRepository.getCount()
            }
        }

        // Register ContentObserver for MediaStore changes
        appContext.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver,
        )
        appContext.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver,
        )
    }

    override fun onCleared() {
        super.onCleared()
        appContext.contentResolver.unregisterContentObserver(mediaObserver)
    }
}
