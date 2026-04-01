package dev.ecalendar.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ecalendar.domain.model.CalendarSource
import dev.ecalendar.util.CalendarColor
import dev.ecalendar.util.ColorPalette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private val TRAVEL_OPTIONS = listOf(
    null to "None",
    5 to "5 min",
    15 to "15 min",
    30 to "30 min",
    60 to "1 hour",
    90 to "1.5 hours",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    viewModel: EventEditViewModel,
    onDismiss: () -> Unit,
) {
    val event by viewModel.event.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val saveResult by viewModel.saveResult.collectAsStateWithLifecycle()
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var showTravelPicker by remember { mutableStateOf(false) }
    var showRecurrenceSheet by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var attendeeInput by remember { mutableStateOf("") }

    val zone = ZoneId.systemDefault()

    // Navigate back on save success
    LaunchedEffect(saveResult) {
        if (saveResult is SaveResult.Success) onDismiss()
    }

    val onBack: () -> Unit = {
        if (isDirty) showDiscardDialog = true else onDismiss()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (viewModel.isNew) "New Event" else "Edit Event",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = saveResult !is SaveResult.Saving,
                    ) {
                        Text(
                            if (viewModel.isNew) "Add" else "Save",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
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
            // Title
            OutlinedTextField(
                value = event.title,
                onValueChange = { viewModel.updateTitle(it) },
                placeholder = {
                    Text(
                        "Event title",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = saveResult is SaveResult.Error,
            )

            if (saveResult is SaveResult.Error) {
                Text(
                    text = (saveResult as SaveResult.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // All-day toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("All day", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = event.isAllDay,
                    onCheckedChange = { viewModel.toggleAllDay() },
                )
            }

            Spacer(Modifier.height(12.dp))

            // Start date/time
            val startZoned = Instant.ofEpochMilli(event.startMillis).atZone(zone)
            val endZoned = Instant.ofEpochMilli(event.endMillis).atZone(zone)

            Text("Starts", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateChip(
                    text = startZoned.toLocalDate().format(DATE_FORMAT),
                    onClick = { showStartDatePicker = true },
                )
                if (!event.isAllDay) {
                    TimeChip(
                        text = startZoned.toLocalTime().format(TIME_FORMAT),
                        onClick = { showStartTimePicker = true },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // End date/time
            Text("Ends", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateChip(
                    text = endZoned.toLocalDate().format(DATE_FORMAT),
                    onClick = { showEndDatePicker = true },
                )
                if (!event.isAllDay) {
                    TimeChip(
                        text = endZoned.toLocalTime().format(TIME_FORMAT),
                        onClick = { showEndTimePicker = true },
                    )
                }
                // Duration hint
                val durationMins = ((event.endMillis - event.startMillis) / 60_000).toInt()
                val durationText = when {
                    durationMins < 60 -> "(${durationMins}m)"
                    durationMins % 60 == 0 -> "(${durationMins / 60}h)"
                    else -> "(${durationMins / 60}h ${durationMins % 60}m)"
                }
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Calendar picker
            Text("Calendar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Box {
                val selected = calendars.find { it.id == event.calendarSourceId }
                val isDark = isSystemInDarkTheme()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable { showCalendarPicker = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected != null) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(ColorPalette.forTheme(selected.colorHex, isDark)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(selected.displayName, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(
                            "Select calendar",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }

                DropdownMenu(
                    expanded = showCalendarPicker,
                    onDismissRequest = { showCalendarPicker = false },
                ) {
                    calendars.forEach { cal ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(ColorPalette.forTheme(cal.colorHex, isDark)),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(cal.displayName)
                                }
                            },
                            onClick = {
                                viewModel.updateCalendarSource(cal.id)
                                showCalendarPicker = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Location
            OutlinedTextField(
                value = event.location ?: "",
                onValueChange = { viewModel.updateLocation(it) },
                placeholder = { Text("Location") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(12.dp))

            // Notes
            OutlinedTextField(
                value = event.notes ?: "",
                onValueChange = { viewModel.updateNotes(it) },
                placeholder = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
            )

            Spacer(Modifier.height(12.dp))

            // URL
            OutlinedTextField(
                value = event.url ?: "",
                onValueChange = { viewModel.updateUrl(it) },
                placeholder = { Text("URL") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Color picker
            Text("Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            ColorPickerRow(
                colors = ColorPalette.defaultColors(),
                selectedHex = event.colorHex,
                onSelect = { viewModel.updateColor(it) },
            )

            Spacer(Modifier.height(16.dp))

            // Travel time
            Text("Travel time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Box {
                val selectedTravel = TRAVEL_OPTIONS.find { it.first == event.travelTimeMins }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable { showTravelPicker = true }
                        .padding(12.dp),
                ) {
                    Text(
                        selectedTravel?.second ?: "None",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                DropdownMenu(
                    expanded = showTravelPicker,
                    onDismissRequest = { showTravelPicker = false },
                ) {
                    TRAVEL_OPTIONS.forEach { (mins, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateTravelTime(mins)
                                showTravelPicker = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Recurrence
            Text("Repeats", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .clickable { showRecurrenceSheet = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = rruleDisplayText(event.rruleString, event.startMillis),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Attendees
            AttendeesSection(
                attendees = event.attendees,
                input = attendeeInput,
                onInputChange = { attendeeInput = it },
                onAdd = {
                    viewModel.addAttendee(attendeeInput)
                    attendeeInput = ""
                },
                onRemove = { viewModel.removeAttendee(it) },
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Reminders
            RemindersSection(
                alarms = event.alarms,
                showPicker = showReminderPicker,
                onShowPicker = { showReminderPicker = it },
                onAdd = { viewModel.addAlarm(it) },
                onRemove = { viewModel.removeAlarm(it) },
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    // Dialogs
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes that will be lost.") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onDismiss() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            },
        )
    }

    if (showStartDatePicker) {
        val startZoned = Instant.ofEpochMilli(event.startMillis).atZone(zone)
        CalendarDatePickerDialog(
            initialDate = startZoned.toLocalDate(),
            onConfirm = { date ->
                val time = if (event.isAllDay) LocalTime.MIDNIGHT else startZoned.toLocalTime()
                val newMillis = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                viewModel.updateStartMillis(newMillis)
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false },
        )
    }

    if (showStartTimePicker) {
        val startZoned = Instant.ofEpochMilli(event.startMillis).atZone(zone)
        CalendarTimePickerDialog(
            initialTime = startZoned.toLocalTime(),
            onConfirm = { time ->
                val newMillis = startZoned.toLocalDate().atTime(time).atZone(zone).toInstant().toEpochMilli()
                viewModel.updateStartMillis(newMillis)
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false },
        )
    }

    if (showEndDatePicker) {
        val endZoned = Instant.ofEpochMilli(event.endMillis).atZone(zone)
        CalendarDatePickerDialog(
            initialDate = endZoned.toLocalDate(),
            onConfirm = { date ->
                val time = if (event.isAllDay) LocalTime.MIDNIGHT else endZoned.toLocalTime()
                val newMillis = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                viewModel.updateEndMillis(newMillis)
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false },
        )
    }

    if (showEndTimePicker) {
        val endZoned = Instant.ofEpochMilli(event.endMillis).atZone(zone)
        CalendarTimePickerDialog(
            initialTime = endZoned.toLocalTime(),
            onConfirm = { time ->
                val newMillis = endZoned.toLocalDate().atTime(time).atZone(zone).toInstant().toEpochMilli()
                viewModel.updateEndMillis(newMillis)
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false },
        )
    }

    if (showRecurrenceSheet) {
        RecurrenceSheet(
            currentRRule = event.rruleString,
            eventStartMillis = event.startMillis,
            onConfirm = { rrule ->
                viewModel.updateRRule(rrule)
                showRecurrenceSheet = false
            },
            onDismiss = { showRecurrenceSheet = false },
        )
    }
}

@Composable
private fun DateChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun ColorPickerRow(
    colors: List<CalendarColor>,
    selectedHex: String?,
    onSelect: (String?) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // "None" option
        item {
            val isSelected = selectedHex == null
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = CircleShape,
                    )
                    .clickable { onSelect(null) },
                contentAlignment = Alignment.Center,
            ) {
                Text("—", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(colors) { calColor ->
            val hex = if (isDark) calColor.darkHex else calColor.lightHex
            val isSelected = selectedHex?.equals(calColor.lightHex, ignoreCase = true) == true ||
                selectedHex?.equals(calColor.darkHex, ignoreCase = true) == true
            val color = ColorPalette.parseHex(hex)

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier,
                    )
                    .clickable { onSelect(calColor.lightHex) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    onConfirm(date)
                }
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = state)
    }
}

private val REMINDER_PRESETS = listOf(
    0 to "At time of event",
    5 to "5 minutes before",
    10 to "10 minutes before",
    15 to "15 minutes before",
    30 to "30 minutes before",
    60 to "1 hour before",
    1440 to "1 day before",
    2880 to "2 days before",
)

private fun alarmDisplayText(mins: Int): String = when {
    mins == 0 -> "At time of event"
    mins < 60 -> "$mins min before"
    mins < 1440 -> "${mins / 60}h before"
    mins % 1440 == 0 -> "${mins / 1440}d before"
    else -> "${mins / 60}h before"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemindersSection(
    alarms: List<Int>,
    showPicker: Boolean,
    onShowPicker: (Boolean) -> Unit,
    onAdd: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Text("Reminders", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))

    if (alarms.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            alarms.forEach { mins ->
                AssistChip(
                    onClick = { onRemove(mins) },
                    label = { Text(alarmDisplayText(mins), style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    Box {
        TextButton(onClick = { onShowPicker(true) }) {
            Text("+ Add reminder")
        }
        DropdownMenu(
            expanded = showPicker,
            onDismissRequest = { onShowPicker(false) },
        ) {
            REMINDER_PRESETS.forEach { (mins, label) ->
                val alreadyAdded = mins in alarms
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            color = if (alreadyAdded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        if (!alreadyAdded) onAdd(mins)
                        onShowPicker(false)
                    },
                    enabled = !alreadyAdded,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttendeesSection(
    attendees: List<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Text("Attendees", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = { Text("Email address") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onAdd,
            enabled = input.contains("@"),
        ) {
            Text("Add")
        }
    }

    if (attendees.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            attendees.forEach { email ->
                AssistChip(
                    onClick = { onRemove(email) },
                    label = { Text(email, style = MaterialTheme.typography.bodySmall) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(4.dp))
    Text(
        "Invites will be sent via Thunderbird",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(state.hour, state.minute))
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = { TimePicker(state = state) },
    )
}
