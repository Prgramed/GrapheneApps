package dev.emusic.domain.model

sealed interface DownloadState {
    data object NotDownloaded : DownloadState
    data class Queued(val trackId: String) : DownloadState
    data class Downloading(val trackId: String, val progress: Int) : DownloadState
    data class Downloaded(val trackId: String, val localPath: String) : DownloadState
    data class Failed(val trackId: String, val error: String) : DownloadState
}
