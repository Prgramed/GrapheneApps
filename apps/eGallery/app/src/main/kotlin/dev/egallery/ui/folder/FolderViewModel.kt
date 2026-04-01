package dev.egallery.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.repository.MediaRepository
import dev.egallery.data.repository.toDomain
import dev.egallery.domain.model.MediaItem
import dev.egallery.util.ThumbnailUrlBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Folder(
    val id: Int,
    val name: String,
    val photoCount: Int = 0,
    val coverNasId: String? = null,
    val coverCacheKey: String = "",
    val coverLocalPath: String? = null,
    val localDirPath: String? = null,
    val isIgnored: Boolean = false,
)

data class Breadcrumb(val id: Int, val name: String)

private val IGNORED_PATH_PATTERNS = listOf("/Android/media/", "/WhatsApp/", "/Telegram/")
private val HIDDEN_PATH_PATTERNS = listOf("/data/data/", "/data/user/") // App-private directories (cache/downloads)

@HiltViewModel
class FolderViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val credentialStore: CredentialStore,
    private val mediaRepository: MediaRepository,
    private val mediaDao: MediaDao,
) : ViewModel() {

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _breadcrumbs = MutableStateFlow<List<Breadcrumb>>(listOf(Breadcrumb(0, "Folders")))
    val breadcrumbs: StateFlow<List<Breadcrumb>> = _breadcrumbs.asStateFlow()

    private val _currentFolderId = MutableStateFlow(0)

    private val _photos = MutableStateFlow<Flow<List<MediaItem>>>(emptyFlow())
    val photos: StateFlow<Flow<List<MediaItem>>> = _photos.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _showIgnored = MutableStateFlow(false)
    val showIgnored: StateFlow<Boolean> = _showIgnored.asStateFlow()

    private var allLocalFolders: List<Folder> = emptyList()

    init {
        loadRootFolders()
    }

    fun toggleShowIgnored() {
        _showIgnored.value = !_showIgnored.value
        applyFolderFilter()
    }

    private fun applyFolderFilter() {
        _folders.value = if (_showIgnored.value) {
            allLocalFolders
        } else {
            allLocalFolders.filter { !it.isIgnored }
        }
    }

    private fun loadRootFolders() {
        viewModelScope.launch {
            _loading.value = true
            allLocalFolders = loadLocalDeviceFolders()
            applyFolderFilter()

            _loading.value = false
        }
    }

    private suspend fun loadLocalDeviceFolders(): List<Folder> {
        val paths = mediaDao.getAllLocalPaths().take(5000)
        val folderMap = mutableMapOf<String, Int>()

        for (path in paths) {
            if (path.startsWith("content://")) continue
            // Skip app-private directories (cached downloads)
            if (HIDDEN_PATH_PATTERNS.any { path.contains(it) }) continue
            val parent = java.io.File(path).parent ?: continue
            folderMap[parent] = (folderMap[parent] ?: 0) + 1
        }

        val rawFolders = folderMap.entries
            .sortedByDescending { it.value }
            .mapIndexed { index, (dirPath, count) ->
                val dirName = java.io.File(dirPath).name
                val isIgnored = IGNORED_PATH_PATTERNS.any { dirPath.contains(it) }
                val cover = mediaDao.getByLocalPath(
                    paths.first { it.startsWith(dirPath) },
                )
                Folder(
                    id = -(index + 1),
                    name = dirName,
                    photoCount = count,
                    coverNasId = cover?.nasId,
                    coverCacheKey = cover?.cacheKey ?: "",
                    coverLocalPath = cover?.localPath,
                    localDirPath = dirPath,
                    isIgnored = isIgnored,
                )
            }

        // Disambiguate duplicate names: append parent dir in parentheses
        val nameCounts = rawFolders.groupBy { it.name }
        return rawFolders.map { folder ->
            if ((nameCounts[folder.name]?.size ?: 0) > 1 && folder.localDirPath != null) {
                val parentName = java.io.File(folder.localDirPath).parentFile?.name ?: ""
                folder.copy(name = "${folder.name} ($parentName)")
            } else {
                folder
            }
        }
    }

    private val localFolderPaths = mutableMapOf<Int, String>()

    fun navigateToFolder(id: Int, name: String, localDirPath: String? = null) {
        _currentFolderId.value = id
        _breadcrumbs.value = _breadcrumbs.value + Breadcrumb(id, name)
        if (localDirPath != null) {
            localFolderPaths[id] = localDirPath
        }

        val dirPath = localDirPath ?: localFolderPaths[id]
        if (id < 0 && dirPath != null) {
            _photos.value = mediaDao.getByLocalDir("$dirPath/").map { entities ->
                entities.map { it.toDomain() }
            }
            _folders.value = emptyList()
            _loading.value = false
        } else {
            _photos.value = mediaRepository.observeFolder(id)
            _folders.value = emptyList()
            _loading.value = false
        }
    }

    fun navigateUp() {
        val crumbs = _breadcrumbs.value
        if (crumbs.size <= 1) return
        val parent = crumbs[crumbs.size - 2]
        _breadcrumbs.value = crumbs.dropLast(1)
        _currentFolderId.value = parent.id
        if (parent.id == 0) {
            _photos.value = emptyFlow()
            loadRootFolders()
        } else {
            val dirPath = localFolderPaths[parent.id]
            if (parent.id < 0 && dirPath != null) {
                _photos.value = mediaDao.getByLocalDir("$dirPath/").map { it.map { e -> e.toDomain() } }
                _folders.value = emptyList()
            } else {
                _photos.value = mediaRepository.observeFolder(parent.id)
                _folders.value = emptyList()
            }
        }
    }

    fun navigateToBreadcrumb(index: Int) {
        val crumbs = _breadcrumbs.value
        if (index >= crumbs.size) return
        val target = crumbs[index]
        _breadcrumbs.value = crumbs.take(index + 1)
        _currentFolderId.value = target.id
        if (target.id == 0) {
            _photos.value = emptyFlow()
            loadRootFolders()
        } else {
            val dirPath = localFolderPaths[target.id]
            if (target.id < 0 && dirPath != null) {
                _photos.value = mediaDao.getByLocalDir("$dirPath/").map { it.map { e -> e.toDomain() } }
                _folders.value = emptyList()
            } else {
                _photos.value = mediaRepository.observeFolder(target.id)
                _folders.value = emptyList()
            }
        }
    }

    fun thumbnailUrl(nasId: String, cacheKey: String, isSharedSpace: Boolean = false): String {
        return ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, nasId)
    }

    fun thumbnailModel(item: dev.egallery.domain.model.MediaItem): Any {
        if (item.localPath != null) {
            if (item.localPath.startsWith("content://")) return android.net.Uri.parse(item.localPath)
            val file = java.io.File(item.localPath)
            if (file.exists()) return file
        }
        if (credentialStore.serverUrl.isNotBlank()) {
            return ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, item.nasId)
        }
        return ""
    }
}
