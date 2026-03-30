package dev.egallery.ui.editor

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.data.repository.MediaRepository
import dev.egallery.domain.model.MediaItem
import dev.egallery.edit.EditUploadCoordinator
import dev.egallery.edit.PhotoEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class EditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val coordinator: EditUploadCoordinator,
) : ViewModel() {

    private val nasId: String = savedStateHandle["nasId"] ?: ""

    private val _item = MutableStateFlow<MediaItem?>(null)
    val item: StateFlow<MediaItem?> = _item.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _saveResult = MutableStateFlow<Result<Unit>?>(null)
    val saveResult: StateFlow<Result<Unit>?> = _saveResult.asStateFlow()

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    // Undo stack — max 10 entries
    private val undoStack = mutableListOf<Bitmap>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    // Color adjustment base bitmap — snapshot before color changes
    private var colorBaseBitmap: Bitmap? = null

    // Crop rect (normalized 0..1 fractions of image dimensions)
    private val _cropRect = MutableStateFlow<android.graphics.RectF?>(null)
    val cropRect: StateFlow<android.graphics.RectF?> = _cropRect.asStateFlow()

    private val _showCropOverlay = MutableStateFlow(false)
    val showCropOverlay: StateFlow<Boolean> = _showCropOverlay.asStateFlow()

    init {
        viewModelScope.launch {
            val mediaItem = mediaRepository.getItemDetail(nasId) ?: return@launch
            _item.value = mediaItem

            withContext(Dispatchers.IO) {
                try {
                    val file = coordinator.downloadOriginal(mediaItem)
                    val bitmap = coordinator.decodeBitmap(file)
                    _previewBitmap.value = bitmap
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load image for editing")
                }
            }
            _loading.value = false
        }
    }

    private var undoStackBytes = 0L

    private fun pushUndo() {
        val current = _previewBitmap.value ?: return
        val copy = current.copy(current.config ?: Bitmap.Config.ARGB_8888, true)
        undoStackBytes += copy.byteCount
        undoStack.add(copy)
        // #10: limit by memory (100MB) not count
        while (undoStackBytes > MAX_UNDO_BYTES && undoStack.size > 1) {
            val removed = undoStack.removeAt(0)
            undoStackBytes -= removed.byteCount
        }
        _canUndo.value = undoStack.isNotEmpty()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeAt(undoStack.lastIndex)
        _previewBitmap.value = previous
        _canUndo.value = undoStack.isNotEmpty()
        _dirty.value = undoStack.isNotEmpty()
        colorBaseBitmap = null // reset color base
    }

    fun rotateLeft() = applyEdit { PhotoEditor.rotate(it, -90f) }
    fun rotateRight() = applyEdit { PhotoEditor.rotate(it, 90f) }
    fun flipH() = applyEdit { PhotoEditor.flip(it, horizontal = true) }
    fun flipV() = applyEdit { PhotoEditor.flip(it, horizontal = false) }

    fun applyCrop(aspectRatio: Float?) {
        applyEdit { bitmap ->
            if (aspectRatio == null) return@applyEdit bitmap
            val bw = bitmap.width
            val bh = bitmap.height
            val currentRatio = bw.toFloat() / bh.toFloat()

            val (cropW, cropH) = if (currentRatio > aspectRatio) {
                (bh * aspectRatio).toInt() to bh
            } else {
                bw to (bw / aspectRatio).toInt()
            }

            val left = (bw - cropW) / 2
            val top = (bh - cropH) / 2
            PhotoEditor.crop(bitmap, Rect(left, top, left + cropW, top + cropH))
        }
    }

    // Crop from drag overlay rect (normalized 0..1)
    fun setCropRect(rect: android.graphics.RectF) {
        _cropRect.value = rect
    }

    fun enableCropOverlay() {
        _showCropOverlay.value = true
        // Default: full image
        _cropRect.value = android.graphics.RectF(0.05f, 0.05f, 0.95f, 0.95f)
    }

    fun disableCropOverlay() {
        _showCropOverlay.value = false
        _cropRect.value = null
    }

    fun applyCropFromRect() {
        val rect = _cropRect.value ?: return
        applyEdit { bitmap ->
            val left = (rect.left * bitmap.width).toInt().coerceIn(0, bitmap.width)
            val top = (rect.top * bitmap.height).toInt().coerceIn(0, bitmap.height)
            val right = (rect.right * bitmap.width).toInt().coerceIn(0, bitmap.width)
            val bottom = (rect.bottom * bitmap.height).toInt().coerceIn(0, bitmap.height)
            PhotoEditor.crop(bitmap, Rect(left, top, right, bottom))
        }
        disableCropOverlay()
    }

    fun snapCropToRatio(aspectRatio: Float) {
        val bitmap = _previewBitmap.value ?: return
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val imgRatio = bw / bh

        val (cropW, cropH) = if (imgRatio > aspectRatio) {
            (bh * aspectRatio / bw) to 1f
        } else {
            1f to (bw / aspectRatio / bh)
        }
        val left = (1f - cropW) / 2f
        val top = (1f - cropH) / 2f
        _cropRect.value = android.graphics.RectF(left, top, left + cropW, top + cropH)
    }

    // Real-time color preview — applies to base bitmap (not cumulative)
    fun beginColorAdjust() {
        if (colorBaseBitmap == null) {
            colorBaseBitmap = _previewBitmap.value?.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    // #16: track preview job to cancel previous
    private var colorPreviewJob: kotlinx.coroutines.Job? = null

    fun adjustColorsPreview(brightness: Float, contrast: Float, saturation: Float) {
        val base = colorBaseBitmap ?: _previewBitmap.value ?: return
        colorPreviewJob?.cancel()
        colorPreviewJob = viewModelScope.launch(Dispatchers.Default) {
            val adjusted = PhotoEditor.adjustColors(base, brightness, contrast, saturation)
            _previewBitmap.value = adjusted
        }
    }

    fun commitColorAdjust(brightness: Float, contrast: Float, saturation: Float) {
        val base = colorBaseBitmap ?: return
        pushUndo()
        viewModelScope.launch(Dispatchers.Default) {
            val adjusted = PhotoEditor.adjustColors(base, brightness, contrast, saturation)
            _previewBitmap.value = adjusted
            _dirty.value = true
        }
        colorBaseBitmap = null
    }

    fun save() {
        val bitmap = _previewBitmap.value ?: return
        val mediaItem = _item.value ?: return

        viewModelScope.launch {
            _saving.value = true
            val result = coordinator.saveAndUpload(mediaItem, bitmap)
            _saveResult.value = result
            _saving.value = false
        }
    }

    private fun applyEdit(transform: (Bitmap) -> Bitmap) {
        val bitmap = _previewBitmap.value ?: return
        pushUndo()
        colorBaseBitmap = null // reset color base after structural edit
        viewModelScope.launch(Dispatchers.Default) {
            _previewBitmap.value = transform(bitmap)
            _dirty.value = true
        }
    }

    companion object {
        private const val MAX_UNDO_BYTES = 100L * 1024 * 1024 // 100MB
    }
}
