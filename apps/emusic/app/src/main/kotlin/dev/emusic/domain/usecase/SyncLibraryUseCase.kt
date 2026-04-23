package dev.emusic.domain.usecase

import dev.emusic.data.preferences.AppPreferencesRepository
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.domain.repository.PlaylistRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class SyncProgress(
    val stage: String,
    val current: Int = 0,
    val total: Int = 0,
    val done: Boolean = false,
)

@Singleton
class SyncLibraryUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val preferencesRepository: AppPreferencesRepository,
) {
    private val mutex = Mutex()

    private val _progress = MutableStateFlow<SyncProgress?>(null)
    val progress: StateFlow<SyncProgress?> = _progress.asStateFlow()

    var activeJob: Job? = null

    fun cancelSync() {
        activeJob?.cancel()
        activeJob = null
        _progress.value = SyncProgress(stage = "Sync cancelled", done = true)
        _progress.value = null
    }

    operator fun invoke(forceFullSync: Boolean = false): Flow<SyncProgress> = channelFlow {
        if (mutex.isLocked) {
            // Already syncing — just emit current state and return
            _progress.value?.let { send(it) }
            return@channelFlow
        }

        mutex.withLock {
            val lastSyncMs = preferencesRepository.getLastSyncMs()
            val isFirstSync = lastSyncMs == 0L || forceFullSync
            var errors = 0

            fun emit(p: SyncProgress) {
                _progress.value = p
                trySend(p)
            }

            emit(SyncProgress(stage = "Syncing artists…"))
            try {
                libraryRepository.syncArtists()
            } catch (e: Exception) {
                errors++
                timber.log.Timber.w(e, "Sync artists failed")
            }

            if (isFirstSync) {
                emit(SyncProgress(stage = "Syncing all albums…"))
                try {
                    libraryRepository.syncAlbums()
                } catch (e: Exception) {
                    errors++
                    timber.log.Timber.w(e, "Sync albums failed")
                }

                emit(SyncProgress(stage = "Syncing tracks…"))
                try {
                    libraryRepository.syncAllTracks { currentAlbum, totalAlbums ->
                        val stageText = "Syncing album $currentAlbum of $totalAlbums…"
                        val p = SyncProgress(stage = stageText, current = currentAlbum, total = totalAlbums.coerceAtLeast(0))
                        _progress.value = p
                        trySend(p)
                    }
                } catch (e: Exception) {
                    errors++
                    timber.log.Timber.w(e, "Sync tracks failed")
                }
            } else {
                emit(SyncProgress(stage = "Checking for new albums…"))
                try {
                    val newAlbumIds = libraryRepository.syncAlbumsIncremental()
                    if (newAlbumIds.isNotEmpty()) {
                        emit(SyncProgress(stage = "${newAlbumIds.size} new albums — syncing tracks…"))
                        for (albumId in newAlbumIds) {
                            try {
                                libraryRepository.syncAlbumTracks(albumId)
                            } catch (_: Exception) { }
                        }
                    }
                } catch (e: Exception) {
                    errors++
                    timber.log.Timber.w(e, "Sync albums incremental failed")
                }

                // Detect tracks added/removed in existing albums by comparing
                // the server-reported songCount against local track count per album.
                // Without this, tracks added to known albums are invisible until
                // a full resync.
                emit(SyncProgress(stage = "Checking album freshness…"))
                try {
                    val staleAlbumIds = libraryRepository.findStaleAlbums()
                    if (staleAlbumIds.isNotEmpty()) {
                        emit(SyncProgress(stage = "${staleAlbumIds.size} albums changed — re-syncing…"))
                        for (albumId in staleAlbumIds) {
                            try {
                                libraryRepository.syncAlbumTracks(albumId)
                            } catch (_: Exception) { }
                        }
                    }
                } catch (e: Exception) {
                    timber.log.Timber.w(e, "Album freshness check failed")
                }
            }

            emit(SyncProgress(stage = "Syncing playlists…"))
            try {
                playlistRepository.syncPlaylists()
            } catch (e: Exception) {
                errors++
                timber.log.Timber.w(e, "Sync playlists failed")
            }

            emit(SyncProgress(stage = "Syncing starred items…"))
            try {
                libraryRepository.syncStarred()
            } catch (e: Exception) {
                errors++
                timber.log.Timber.w(e, "Sync starred failed")
            }

            preferencesRepository.setLastSyncMs(System.currentTimeMillis())
            val statusMsg = if (errors > 0) "Sync completed with $errors errors" else "Library sync complete"
            val done = SyncProgress(stage = statusMsg, done = true)
            _progress.value = done
            send(done)
            _progress.value = null
        }
    }
}
