package dev.ecalendar.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.FilterChip
import dev.ecalendar.sync.SyncState
import dev.ecalendar.util.ColorPalette

private val REMINDER_OPTIONS = listOf(
    0 to "At time of event",
    5 to "5 minutes",
    10 to "10 minutes",
    15 to "15 minutes",
    30 to "30 minutes",
    60 to "1 hour",
    1440 to "1 day",
)

private val SYNC_OPTIONS = listOf(
    0 to "Manual only",
    1 to "Every hour",
    2 to "Every 2 hours",
    6 to "Every 6 hours",
    24 to "Daily",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onAccounts: () -> Unit,
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val calendars by viewModel.writableCalendars.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // General section
            SectionHeader("General")

            // First day of week
            SettingsRow(
                label = "First day of week",
                value = if (prefs.firstDayOfWeek == 1) "Monday" else "Sunday",
                onClick = { viewModel.updateFirstDayOfWeek(if (prefs.firstDayOfWeek == 1) 7 else 1) },
            )

            // Time format
            ToggleRow(
                label = "24-hour time",
                checked = prefs.timeFormat24h,
                onCheckedChange = { viewModel.updateTimeFormat(it) },
            )

            // Default calendar
            var showCalPicker by remember { mutableStateOf(false) }
            val defaultCal = calendars.find { it.id == prefs.defaultCalendarSourceId }
            Box {
                SettingsRow(
                    label = "Default calendar",
                    value = defaultCal?.displayName ?: "Not set",
                    onClick = { showCalPicker = true },
                )
                DropdownMenu(expanded = showCalPicker, onDismissRequest = { showCalPicker = false }) {
                    calendars.forEach { cal ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(10.dp).clip(CircleShape)
                                            .background(ColorPalette.forTheme(cal.colorHex, isDark)),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(cal.displayName)
                                }
                            },
                            onClick = {
                                viewModel.updateDefaultCalendar(cal.id)
                                showCalPicker = false
                            },
                        )
                    }
                }
            }

            // Default reminder
            var showReminderPicker by remember { mutableStateOf(false) }
            val reminderLabel = REMINDER_OPTIONS.find { it.first == prefs.defaultReminderMins }?.second ?: "${prefs.defaultReminderMins} min"
            Box {
                SettingsRow(
                    label = "Default reminder",
                    value = reminderLabel,
                    onClick = { showReminderPicker = true },
                )
                DropdownMenu(expanded = showReminderPicker, onDismissRequest = { showReminderPicker = false }) {
                    REMINDER_OPTIONS.forEach { (mins, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateDefaultReminder(mins)
                                showReminderPicker = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Sync section
            SectionHeader("Sync")

            var showSyncPicker by remember { mutableStateOf(false) }
            val syncLabel = SYNC_OPTIONS.find { it.first == prefs.syncIntervalHours }?.second ?: "Every ${prefs.syncIntervalHours}h"
            Box {
                SettingsRow(
                    label = "Sync interval",
                    value = syncLabel,
                    onClick = { showSyncPicker = true },
                )
                DropdownMenu(expanded = showSyncPicker, onDismissRequest = { showSyncPicker = false }) {
                    SYNC_OPTIONS.forEach { (hours, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateSyncInterval(hours)
                                showSyncPicker = false
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val syncText = when (syncState) {
                    is SyncState.Syncing -> "Syncing..."
                    is SyncState.LastSyncedAt -> {
                        val ago = (System.currentTimeMillis() - (syncState as SyncState.LastSyncedAt).timestamp) / 60_000
                        when {
                            ago < 1 -> "Last synced just now"
                            ago < 60 -> "Last synced ${ago}m ago"
                            else -> "Last synced ${ago / 60}h ago"
                        }
                    }
                    is SyncState.Error -> "Sync error: ${(syncState as SyncState.Error).message}"
                    is SyncState.Idle -> "Not synced yet"
                }
                Text(
                    syncText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { viewModel.syncNow() },
                    enabled = syncState !is SyncState.Syncing,
                ) {
                    Text("Sync now")
                }
            }

            SettingsRow(
                label = "Manage accounts",
                value = "",
                onClick = onAccounts,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Notifications section
            SectionHeader("Notifications")

            ToggleRow(
                label = "Event reminders",
                checked = prefs.notificationsEnabled,
                onCheckedChange = { viewModel.updateNotifications(it) },
            )

            SettingsRow(
                label = "Exact alarm permission",
                value = "Tap to open system settings",
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    context.startActivity(intent)
                },
            )

            // Battery optimization
            val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
            val isExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            SettingsRow(
                label = "Battery optimization",
                value = if (isExempt) "Exempt" else "Not exempt — tap to fix",
                onClick = {
                    if (!isExempt) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Appearance section
            SectionHeader("Appearance")

            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                    FilterChip(
                        selected = prefs.themeMode == mode,
                        onClick = { viewModel.updateThemeMode(mode) },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // About section
            SectionHeader("About")

            Text(
                "eCalendar",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "Privacy-first calendar for GrapheneOS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Your data stays on your devices. Calendar sync via CalDAV.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
