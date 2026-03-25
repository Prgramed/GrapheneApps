package dev.emusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.emusic.domain.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackContextMenu(
    track: Track,
    onDismiss: () -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onToggleStar: (Track) -> Unit,
    onGoToArtist: ((String) -> Unit)? = null,
    onGoToAlbum: ((String) -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            // Track header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${track.artist} \u00b7 ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Play Next
            MenuItem(
                icon = Icons.Default.PlaylistPlay,
                label = "Play Next",
                onClick = { onPlayNext(track); onDismiss() },
            )

            // Add to Queue
            MenuItem(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = "Add to Queue",
                onClick = { onAddToQueue(track); onDismiss() },
            )

            // Add to Playlist
            MenuItem(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = "Add to Playlist",
                onClick = { onAddToPlaylist(track); onDismiss() },
            )

            // Star/Unstar
            MenuItem(
                icon = if (track.starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                label = if (track.starred) "Remove from Favorites" else "Add to Favorites",
                onClick = { onToggleStar(track); onDismiss() },
            )

            // Go to Artist
            if (onGoToArtist != null) {
                MenuItem(
                    icon = Icons.Default.Person,
                    label = "Go to Artist",
                    onClick = { onGoToArtist(track.artistId); onDismiss() },
                )
            }

            // Go to Album
            if (onGoToAlbum != null) {
                MenuItem(
                    icon = Icons.Default.Album,
                    label = "Go to Album",
                    onClick = { onGoToAlbum(track.albumId); onDismiss() },
                )
            }
        }
    }
}

@Composable
private fun MenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
