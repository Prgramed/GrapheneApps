package dev.egallery.util

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.media.Image
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
    private const val BIT_RATE = 4_000_000
    private const val FRAME_RATE = 30
    private const val I_FRAME_INTERVAL = 5
    private const val SECONDS_PER_PHOTO = 5
    private const val FRAMES_PER_PHOTO = FRAME_RATE * SECONDS_PER_PHOTO
    private const val FRAME_DURATION_US = 1_000_000L / FRAME_RATE // ~33333 us

    fun encode(
        context: Context,
        bitmaps: List<Bitmap>,
        audioResId: Int,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        if (bitmaps.isEmpty()) return
        outputFile.parentFile?.mkdirs()

        // Step 1: Encode video to temp file
        val tempVideoFile = File(outputFile.parent, "temp_video.mp4")
        encodeVideoOnly(bitmaps, tempVideoFile, onProgress)

        // Step 2: Mux video + audio into final file
        if (audioResId != 0) {
            try {
                muxVideoAndAudio(context, tempVideoFile, audioResId, outputFile)
                tempVideoFile.delete()
            } catch (e: Exception) {
                Timber.w(e, "Audio mux failed, using video-only")
                tempVideoFile.renameTo(outputFile)
            }
        } else {
            tempVideoFile.renameTo(outputFile)
        }

        Timber.d("Video encoded: ${outputFile.absolutePath} (${outputFile.length() / 1024}KB)")
    }

    private fun encodeVideoOnly(bitmaps: List<Bitmap>, outputFile: File, onProgress: (Int, Int) -> Unit) {
        val format = MediaFormat.createVideoFormat("video/avc", WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        val codec = MediaCodec.createEncoderByType("video/avc")
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        var presentationTimeUs = 0L

        for ((photoIndex, bitmap) in bitmaps.withIndex()) {
            val scaled = centerCropScale(bitmap, WIDTH, HEIGHT)
            // Pre-extract ARGB pixels once per photo (shared across all 150 frames)
            val argb = IntArray(WIDTH * HEIGHT)
            scaled.getPixels(argb, 0, WIDTH, 0, 0, WIDTH, HEIGHT)

            for (f in 0 until FRAMES_PER_PHOTO) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    // Use Image API — handles device-specific YUV layout/stride automatically
                    val image = codec.getInputImage(inputIndex)
                    if (image != null) {
                        fillImageFromArgb(image, argb, WIDTH, HEIGHT)
                    }
                    val bufSize = codec.getInputBuffer(inputIndex)?.capacity() ?: 0
                    codec.queueInputBuffer(inputIndex, 0, bufSize, presentationTimeUs, 0)
                    presentationTimeUs += FRAME_DURATION_US
                }

                // Drain output
                drainOutput(codec, bufferInfo, muxer, { videoTrackIndex }, { videoTrackIndex = it }, { muxerStarted }, { muxerStarted = it })
            }

            scaled.recycle()
            onProgress(photoIndex + 1, bitmaps.size)
        }

        // Signal EOS
        val eosIndex = codec.dequeueInputBuffer(10_000)
        if (eosIndex >= 0) {
            codec.queueInputBuffer(eosIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drainOutput(codec, bufferInfo, muxer, { videoTrackIndex }, { videoTrackIndex = it }, { muxerStarted }, { muxerStarted = it }, eos = true)

        codec.stop()
        codec.release()
        if (muxerStarted) {
            muxer.stop()
        }
        muxer.release()
    }

    /** Write ARGB pixel data into a YUV Image, respecting the codec's actual plane strides */
    private fun fillImageFromArgb(image: Image, argb: IntArray, w: Int, h: Int) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        for (j in 0 until h) {
            for (i in 0 until w) {
                val pixel = argb[j * w + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // BT.601 full-range RGB to YUV
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuf.put(j * yRowStride + i, y.coerceIn(0, 255).toByte())

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val uvIndex = (j / 2) * uvRowStride + (i / 2) * uvPixelStride
                    uBuf.put(uvIndex, u.coerceIn(0, 255).toByte())
                    vBuf.put(uvIndex, v.coerceIn(0, 255).toByte())
                }
            }
        }
    }

    private fun drainOutput(
        codec: MediaCodec,
        info: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        getTrack: () -> Int,
        setTrack: (Int) -> Unit,
        getStarted: () -> Boolean,
        setStarted: (Boolean) -> Unit,
        eos: Boolean = false,
    ) {
        val timeout = if (eos) 10_000L else 0L
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(info, timeout)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    setTrack(muxer.addTrack(codec.outputFormat))
                    muxer.start()
                    setStarted(true)
                }
                outputIndex >= 0 -> {
                    val buf = codec.getOutputBuffer(outputIndex) ?: break
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && getStarted()) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(getTrack(), buf, info)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun muxVideoAndAudio(context: Context, videoFile: File, audioResId: Int, outputFile: File) {
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Add video track
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoFile.absolutePath)
        var videoTrackSrc = -1
        for (i in 0 until videoExtractor.trackCount) {
            val fmt = videoExtractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                videoTrackSrc = i
                break
            }
        }
        if (videoTrackSrc < 0) { muxer.release(); return }
        videoExtractor.selectTrack(videoTrackSrc)
        val videoFormat = videoExtractor.getTrackFormat(videoTrackSrc)
        val videoTrackDst = muxer.addTrack(videoFormat)

        // Add audio track (must be AAC for MP4 muxer — MP3 silently fails)
        val afd: AssetFileDescriptor = context.resources.openRawResourceFd(audioResId)
        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        var audioTrackSrc = -1
        for (i in 0 until audioExtractor.trackCount) {
            val fmt = audioExtractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrackSrc = i
                break
            }
        }
        if (audioTrackSrc < 0) { muxer.release(); afd.close(); return }
        audioExtractor.selectTrack(audioTrackSrc)
        val audioFormat = audioExtractor.getTrackFormat(audioTrackSrc)
        val audioTrackDst = muxer.addTrack(audioFormat)

        muxer.start()

        val buffer = ByteBuffer.allocate(1024 * 1024)
        val info = MediaCodec.BufferInfo()

        // Copy video samples and track duration
        var videoDurationUs = 0L
        while (true) {
            val size = videoExtractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = videoExtractor.sampleTime
            info.flags = videoExtractor.sampleFlags
            videoDurationUs = info.presentationTimeUs
            muxer.writeSampleData(videoTrackDst, buffer, info)
            videoExtractor.advance()
        }

        // Copy audio samples, looping to fill video duration
        var audioWriteTimeUs = 0L
        while (audioWriteTimeUs < videoDurationUs) {
            audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val loopStartUs = audioWriteTimeUs

            while (true) {
                val size = audioExtractor.readSampleData(buffer, 0)
                if (size < 0) break // End of audio file, loop again

                val pts = loopStartUs + audioExtractor.sampleTime
                if (pts >= videoDurationUs) break // Filled up to video duration

                info.offset = 0
                info.size = size
                info.presentationTimeUs = pts
                info.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(audioTrackDst, buffer, info)
                audioWriteTimeUs = pts
                audioExtractor.advance()
            }

            // Advance past the last written sample to prevent overlap on next loop
            audioWriteTimeUs += 23_000 // ~one AAC frame duration
        }

        videoExtractor.release()
        audioExtractor.release()
        afd.close()
        muxer.stop()
        muxer.release()
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
