package dev.egallery.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadStatus @Inject constructor() {
    private val _progress = MutableStateFlow("Idle")
    val progress: StateFlow<String> = _progress.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun update(message: String) { _progress.value = message }
    fun setRunning(running: Boolean) { _isRunning.value = running }
}
