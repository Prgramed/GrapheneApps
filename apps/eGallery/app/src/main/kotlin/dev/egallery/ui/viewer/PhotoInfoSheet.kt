package dev.egallery.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.egallery.domain.model.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoInfoSheet(
    item: MediaItem,
    exifData: ExifData?,
    onDismiss: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM yyyy · HH:mm:ss", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Section: File
            SectionTitle("File")
            InfoRow("Name", item.filename)
            InfoRow("Size", formatFileSize(item.fileSize))
            InfoRow("Type", item.mediaType.name)
            exifData?.let { exif ->
                if (exif.width != null && exif.height != null) {
                    InfoRow("Resolution", "${exif.width} × ${exif.height}")
                }
            }
            InfoRow("Date", dateFormat.format(Date(item.captureDate)))

            // Section: Camera
            if (exifData != null && (exifData.camera != null || exifData.iso != null)) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionTitle("Camera")
                exifData.camera?.let { InfoRow("Camera", it) }
                exifData.lens?.let { InfoRow("Lens", it) }
                exifData.focalLength?.let { InfoRow("Focal length", it) }
                exifData.iso?.let { InfoRow("ISO", it) }
                exifData.aperture?.let { InfoRow("Aperture", it) }
                exifData.shutter?.let { InfoRow("Shutter", it) }
            }

            // Section: Location
            if (item.lat != null && item.lng != null || exifData?.lat != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionTitle("Location")
                val lat = exifData?.lat ?: item.lat
                val lng = exifData?.lng ?: item.lng
                if (lat != null && lng != null) {
                    InfoRow("Coordinates", "%.6f, %.6f".format(lat, lng))
                }
            }

            // Section: Storage
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("Storage")
            InfoRow("Status", item.storageStatus.name.replace("_", " "))
            item.localPath?.let { InfoRow("Path", it) }
            if ((item.nasId.toIntOrNull() ?: 0) > 0) InfoRow("NAS ID", item.nasId)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.65f),
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
