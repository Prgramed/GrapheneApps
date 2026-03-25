package com.prgramed.edoist.feature.taskdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDateSelector(
    dueDate: LocalDate?,
    dueTime: LocalTime?,
    onDateSelected: (LocalDate?) -> Unit,
    onTimeSelected: (LocalTime?) -> Unit,
    onNaturalDateParsed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var naturalDateInput by remember { mutableStateOf("") }

    if (showDatePicker) {
        val initialMillis = dueDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val instant = Instant.fromEpochMilliseconds(millis)
                            val dateTime = instant.toLocalDateTime(TimeZone.UTC)
                            onDateSelected(dateTime.date)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = dueDate?.let { date ->
                        buildString {
                            val month = date.month.name.take(3).lowercase()
                                .replaceFirstChar { it.uppercase() }
                            append("$month ${date.dayOfMonth}, ${date.year}")
                            if (dueTime != null) {
                                val hour = if (dueTime.hour % 12 == 0) 12 else dueTime.hour % 12
                                val amPm = if (dueTime.hour < 12) "AM" else "PM"
                                val minute = dueTime.minute.toString().padStart(2, '0')
                                append(" at $hour:$minute $amPm")
                            }
                        }
                    } ?: "No date",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (dueDate != null) {
                IconButton(onClick = {
                    onDateSelected(null)
                    onTimeSelected(null)
                }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear date",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Natural date input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = naturalDateInput,
                onValueChange = { input ->
                    naturalDateInput = input
                    if (input.isNotBlank()) {
                        onNaturalDateParsed(input)
                    }
                },
                placeholder = {
                    Text(
                        text = "Type a date...",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                singleLine = true,
            )
        }
    }
}
