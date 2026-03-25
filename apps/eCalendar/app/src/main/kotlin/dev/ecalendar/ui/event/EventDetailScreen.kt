package dev.ecalendar.ui.event

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ecalendar.domain.model.EditScope
import dev.ecalendar.util.ColorPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventDetailScreen(
    viewModel: EventDetailViewModel,
    onDismiss: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleteResult by viewModel.deleteResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val isDark = isSystemInDarkTheme()

    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deleteResult) {
        if (deleteResult is DeleteResult.Success) onDismiss()
    }

    when (val s = state) {
        is EventDetailState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is EventDetailState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
        }

        is EventDetailState.Loaded -> {
            val event = s.event
            val source = s.source
            val isReadOnly = source?.isReadOnly == true
            val isMirror = source?.isMirror == true
            val eventColor = ColorPalette.forTheme(
                event.colorHex ?: source?.colorHex ?: "#4285F4",
                isDark,
            )

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {},
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (!isReadOnly) {
                                IconButton(onClick = { onEdit(event.uid) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = { showDeleteDialog = true }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Color header strip
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(eventColor),
                    )

                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(16.dp))

                        // Title
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(Modifier.height(8.dp))

                        // Calendar name + color dot
                        if (source != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(ColorPalette.forTheme(source.colorHex, isDark)),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = source.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Read-only / mirror banner
                        if (isReadOnly) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isMirror) "Synced from iCloud — edit on your Mac"
                                else "This calendar is read-only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        // Date + time
                        val startZoned = Instant.ofEpochMilli(event.instanceStart).atZone(zone)
                        val endZoned = Instant.ofEpochMilli(event.instanceEnd).atZone(zone)

                        if (event.isAllDay) {
                            DetailRow(icon = null) {
                                Text(
                                    startZoned.toLocalDate().format(DATE_FORMAT),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    "All day",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            DetailRow(icon = null) {
                                Text(
                                    startZoned.toLocalDate().format(DATE_FORMAT),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    "${startZoned.toLocalTime().format(TIME_FORMAT)} – ${endZoned.toLocalTime().format(TIME_FORMAT)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Recurrence
                        if (!s.rruleString.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            DetailRow(icon = Icons.Default.Repeat) {
                                Text(
                                    rruleDisplayText(s.rruleString, event.instanceStart),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Location
                        if (!event.location.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            DetailRow(icon = Icons.Default.LocationOn) {
                                Text(
                                    text = event.location,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable {
                                        val uri = Uri.parse("geo:0,0?q=${Uri.encode(event.location)}")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    },
                                )
                            }
                        }

                        // Notes
                        if (!event.notes.isNullOrBlank()) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = event.notes,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        // URL
                        if (!event.url.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            DetailRow(icon = Icons.Default.Link) {
                                Text(
                                    text = event.url,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable {
                                        val uri = Uri.parse(
                                            if (event.url.startsWith("http")) event.url
                                            else "https://${event.url}",
                                        )
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    },
                                )
                            }
                        }

                        // Attendees
                        if (s.attendees.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))
                            DetailRow(icon = Icons.Default.People) {
                                Text(
                                    "Attendees",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(start = 28.dp),
                            ) {
                                s.attendees.forEach { email ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(email, fontSize = 12.sp) },
                                    )
                                }
                            }
                        }

                        // Reminders
                        if (s.alarmMins.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))
                            DetailRow(icon = Icons.Default.NotificationsActive) {
                                Column {
                                    s.alarmMins.forEach { mins ->
                                        Text(
                                            text = alarmDisplayText(mins),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        // Travel time
                        if (event.travelTimeMins != null && event.travelTimeMins > 0) {
                            Spacer(Modifier.height(12.dp))
                            DetailRow(icon = null) {
                                Text(
                                    text = "Travel time: ${event.travelTimeMins} min",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }

            // Delete dialog
            if (showDeleteDialog) {
                if (s.isRecurring) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Delete recurring event") },
                        text = { Text("This is a recurring event. What would you like to delete?") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteDialog = false
                                viewModel.deleteEvent(EditScope.ALL)
                            }) {
                                Text("All events", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            Row {
                                TextButton(onClick = { showDeleteDialog = false }) {
                                    Text("Cancel")
                                }
                                TextButton(onClick = {
                                    showDeleteDialog = false
                                    viewModel.deleteEvent(EditScope.THIS_ONLY)
                                }) {
                                    Text("This event only")
                                }
                            }
                        },
                    )
                } else {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Delete event") },
                        text = { Text("Are you sure you want to delete \"${event.title}\"?") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteDialog = false
                                viewModel.deleteEvent(EditScope.ALL)
                            }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    content: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
        Column { content() }
    }
}

private fun alarmDisplayText(mins: Int): String = when {
    mins == 0 -> "At time of event"
    mins < 60 -> "$mins min before"
    mins < 1440 -> "${mins / 60}h before"
    mins % 1440 == 0 -> "${mins / 1440}d before"
    else -> "${mins / 60}h before"
}
