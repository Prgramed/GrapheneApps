package dev.ecalendar.ui.ics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ecalendar.util.ColorPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IcsImportScreen(
    viewModel: IcsImportViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val calendars by viewModel.writableCalendars.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val isDark = isSystemInDarkTheme()

    var selectedCalendarId by remember { mutableLongStateOf(0L) }
    var showCalendarPicker by remember { mutableStateOf(false) }

    // Auto-select first writable calendar
    LaunchedEffect(calendars) {
        if (selectedCalendarId == 0L && calendars.isNotEmpty()) {
            selectedCalendarId = calendars.first().id
        }
    }

    LaunchedEffect(state) {
        if (state is ImportState.Saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Event") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val s = state) {
            is ImportState.Idle -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is ImportState.Error -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is ImportState.Saved -> {} // Handled by LaunchedEffect

            is ImportState.Parsed -> {
                val event = s.event
                val startZoned = Instant.ofEpochMilli(event.startMillis).atZone(zone)
                val endZoned = Instant.ofEpochMilli(event.endMillis).atZone(zone)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    // Duplicate warning
                    if (event.isDuplicate) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "This event already exists in your calendar — saving will update it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Title
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(Modifier.height(16.dp))

                    // Date/time
                    if (event.isAllDay) {
                        Text(
                            startZoned.toLocalDate().format(DATE_FORMAT),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text("All day", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
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

                    // Organizer
                    if (!event.organizer.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "From: ${event.organizer}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    // Location
                    if (!event.location.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(event.location, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Notes
                    if (!event.notes.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(event.notes, style = MaterialTheme.typography.bodyMedium)
                    }

                    // Attendees
                    if (event.attendees.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.People, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Column {
                                event.attendees.forEach { email ->
                                    Text(email, style = MaterialTheme.typography.bodySmall, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    // Calendar picker
                    Text("Save to calendar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Box {
                        val selected = calendars.find { it.id == selectedCalendarId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .clickable { showCalendarPicker = true }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selected != null) {
                                Box(
                                    Modifier.size(10.dp).clip(CircleShape)
                                        .background(ColorPalette.forTheme(selected.colorHex, isDark)),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(selected.displayName, style = MaterialTheme.typography.bodyLarge)
                            } else {
                                Text("Select calendar", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                        DropdownMenu(expanded = showCalendarPicker, onDismissRequest = { showCalendarPicker = false }) {
                            calendars.forEach { cal ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(10.dp).clip(CircleShape).background(ColorPalette.forTheme(cal.colorHex, isDark)))
                                            Spacer(Modifier.width(8.dp))
                                            Text(cal.displayName)
                                        }
                                    },
                                    onClick = {
                                        selectedCalendarId = cal.id
                                        showCalendarPicker = false
                                    },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Decline")
                        }
                        Button(
                            onClick = { viewModel.saveToCalendar(selectedCalendarId) },
                            modifier = Modifier.weight(1f),
                            enabled = selectedCalendarId > 0,
                        ) {
                            Text(if (event.isDuplicate) "Update" else "Save to Calendar")
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}
