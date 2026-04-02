package dev.egallery.util

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer

object MemoryVideoEncoder {

    private const val WIDTH = 1080
    private const val HEIGHT = 1080
    private const val BIT_RATE = 2_000_000
    private const val FRAME_RATE = 30
    private const val I_FRAME_INTERVAL = 5
    private const val SECONDS_PER_PHOTO = 5
    private const val FRAMES_PER_PHOTO = FRAME_RATE * SECONDS_PER_PHOTO

    /**
     * Encodes a list of bitmaps into an MP4 video with background audio.
     * Each bitmap is shown for [SECONDS_PER_PHOTO] seconds.
     */
    fun encode(
        context: Context,
        bitmaps: List<Bitmap>,
        audioResId: Int,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        if (bitmaps.isEmpty()) return
        outputFile.parentFile?.mkdirs()

        val format = MediaFormat.createVideoFormat("video/avc", WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        val codec = MediaCodec.createEncoderByType("video/avc")
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val state = EncoderState()
        val bufferInfo = MediaCodec.BufferInfo()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val totalFrames = bitmaps.size * FRAMES_PER_PHOTO

        for ((photoIndex, bitmap) in bitmaps.withIndex()) {
            val scaled = centerCropScale(bitmap, WIDTH, HEIGHT)

            for (f in 0 until FRAMES_PER_PHOTO) {
                val canvas = inputSurface.lockHardwareCanvas()
                canvas.drawBitmap(scaled, 0f, 0f, paint)
                inputSurface.unlockCanvasAndPost(canvas)
                drainEncoder(codec, bufferInfo, muxer, state, false)
            }

            scaled.recycle()
            onProgress(photoIndex + 1, bitmaps.size)
        }

        codec.signalEndOfInputStream()
        drainEncoder(codec, bufferInfo, muxer, state, true)

        codec.stop()
        codec.release()

        // Mux audio track
        if (audioResId != 0) {
            try {
                muxAudio(context, audioResId, muxer, totalFrames.toLong() * 1_000_000L / FRAME_RATE)
            } catch (e: Exception) {
                Timber.w(e, "Failed to mux audio — video will be silent")
            }
        }

        muxer.stop()
        muxer.release()

        Timber.d("Video encoded: ${outputFile.absolutePath} (${outputFile.length() / 1024}KB)")
    }

    private class EncoderState(var videoTrackIndex: Int = -1, var muxerStarted: Boolean = false)

    private fun drainEncoder(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        state: EncoderState,
        endOfStream: Boolean,
    ) {
        val timeoutUs = if (endOfStream) 10_000L else 0L
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    state.videoTrackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    state.muxerStarted = true
                }
                outputIndex >= 0 -> {
                    val data = codec.getOutputBuffer(outputIndex) ?: continue
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && state.muxerStarted) {
                        data.position(bufferInfo.offset)
                        data.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(state.videoTrackIndex, data, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun muxAudio(context: Context, audioResId: Int, muxer: MediaMuxer, videoDurationUs: Long) {
        val afd: AssetFileDescriptor = context.resources.openRawResourceFd(audioResId)
        val extractor = MediaExtractor()
        extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)

        var audioTrack = -1
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrack = i
                break
            }
        }
        if (audioTrack < 0) return

        extractor.selectTrack(audioTrack)
        val audioFormat = extractor.getTrackFormat(audioTrack)
        val muxerTrackIndex = muxer.addTrack(audioFormat)

        val buffer = ByteBuffer.allocate(256 * 1024)
        val info = MediaCodec.BufferInfo()

        // Copy audio samples up to video duration (loop if needed)
        var totalUs = 0L
        while (totalUs < videoDurationUs) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) {
                // Loop audio
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                continue
            }
            info.offset = 0
            info.size = sampleSize
            info.presentationTimeUs = totalUs
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerTrackIndex, buffer, info)
            totalUs = extractor.sampleTime
            extractor.advance()
        }

        extractor.release()
        afd.close()
    }

    private fun centerCropScale(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcRatio = src.width.toFloat() / src.height
        val targetRatio = targetW.toFloat() / targetH

        val (cropW, cropH) = if (srcRatio > targetRatio) {
            (src.height * targetRatio).toInt() to src.height
        } else {
            src.width to (src.width / targetRatio).toInt()
        }

        val left = (src.width - cropW) / 2
        val top = (src.height - cropH) / 2
        val cropped = Bitmap.createBitmap(src, left, top, cropW, cropH)

        val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
        if (cropped !== src) cropped.recycle()
        return scaled
    }
}
