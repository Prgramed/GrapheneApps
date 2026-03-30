package dev.egallery.ui.viewer

import android.graphics.BitmapFactory
import android.media.ExifInterface
import dev.egallery.api.dto.ImmichExifInfo
import java.io.File

data class ExifData(
    val camera: String? = null,
    val lens: String? = null,
    val iso: String? = null,
    val aperture: String? = null,
    val shutter: String? = null,
    val focalLength: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val lat: Double? = null,
    val lng: Double? = null,
) {
    companion object {
        fun fromLocalFile(path: String): ExifData? {
            val file = File(path)
            if (!file.exists()) return null

            return try {
                val exif = ExifInterface(file.absolutePath)

                // Get lat/lng
                val latLng = FloatArray(2)
                val hasGps = exif.getLatLong(latLng)

                // Get resolution without loading full bitmap
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, opts)

                ExifData(
                    camera = exif.getAttribute(ExifInterface.TAG_MAKE)?.let { make ->
                        val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: ""
                        if (model.startsWith(make)) model else "$make $model"
                    } ?: exif.getAttribute(ExifInterface.TAG_MODEL),
                    lens = exif.getAttribute("LensModel"),
                    iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS),
                    aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" },
                    shutter = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { raw ->
                        val d = raw.toDoubleOrNull() ?: return@let raw
                        if (d < 1) "1/${(1 / d).toInt()}" else "${d.toInt()}s"
                    },
                    focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { raw ->
                        val parts = raw.split("/")
                        if (parts.size == 2) {
                            val mm = parts[0].toDoubleOrNull()?.div(parts[1].toDoubleOrNull() ?: 1.0)
                            mm?.let { "${it.toInt()}mm" }
                        } else {
                            "${raw}mm"
                        }
                    },
                    width = if (opts.outWidth > 0) opts.outWidth else null,
                    height = if (opts.outHeight > 0) opts.outHeight else null,
                    lat = if (hasGps) latLng[0].toDouble() else null,
                    lng = if (hasGps) latLng[1].toDouble() else null,
                )
            } catch (_: Exception) {
                null
            }
        }

        fun fromImmichExifInfo(info: ImmichExifInfo): ExifData {
            val camera = listOfNotNull(info.make, info.model).joinToString(" ").ifBlank { null }
            return ExifData(
                camera = camera,
                lens = info.lensModel,
                iso = info.iso,
                aperture = info.fNumber?.let { "f/$it" },
                shutter = info.exposureTime,
                focalLength = info.focalLength?.let { "${it.toInt()}mm" },
                width = info.exifImageWidth,
                height = info.exifImageHeight,
                lat = info.latitude,
                lng = info.longitude,
            )
        }
    }
}
