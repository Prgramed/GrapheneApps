package dev.egallery.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import java.io.File

object PhotoEditor {

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun flip(bitmap: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) {
                postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            } else {
                postScale(1f, -1f, bitmap.width / 2f, bitmap.height / 2f)
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun crop(bitmap: Bitmap, rect: Rect): Bitmap {
        val safeRect = Rect(
            rect.left.coerceIn(0, bitmap.width),
            rect.top.coerceIn(0, bitmap.height),
            rect.right.coerceIn(0, bitmap.width),
            rect.bottom.coerceIn(0, bitmap.height),
        )
        if (safeRect.width() <= 0 || safeRect.height() <= 0) return bitmap
        return Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
    }

    /**
     * @param brightness -1f to 1f (0 = no change)
     * @param contrast -1f to 1f (0 = no change)
     * @param saturation 0f to 2f (1 = no change)
     */
    fun adjustColors(
        bitmap: Bitmap,
        brightness: Float = 0f,
        contrast: Float = 0f,
        saturation: Float = 1f,
    ): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()

        // Combine brightness, contrast, and saturation into a single ColorMatrix
        val cm = ColorMatrix()

        // Saturation
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(saturation)
        cm.postConcat(satMatrix)

        // Brightness (translate RGB channels)
        val brightnessValue = brightness * 255f
        val brightnessMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightnessValue,
                0f, 1f, 0f, 0f, brightnessValue,
                0f, 0f, 1f, 0f, brightnessValue,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        cm.postConcat(brightnessMatrix)

        // Contrast (scale RGB channels around midpoint)
        val contrastScale = 1f + contrast
        val translate = (-0.5f * contrastScale + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrastScale, 0f, 0f, 0f, translate,
                0f, contrastScale, 0f, 0f, translate,
                0f, 0f, contrastScale, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        cm.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    fun save(bitmap: Bitmap, outputFile: File, quality: Int = 95) {
        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
    }
}
