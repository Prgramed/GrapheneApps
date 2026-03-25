package com.prgramed.edoist.feature.taskdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.prgramed.edoist.domain.model.RecurrenceRule
import com.prgramed.edoist.domain.model.RecurrenceRule.Frequency
import kotlinx.datetime.DayOfWeek

private enum class RecurrencePreset {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    CUSTOM,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrenceSelector(
    current: RecurrenceRule?,
    onSelected: (RecurrenceRule?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPreset by remember {
        mutableStateOf(
            when {
                current == null -> RecurrencePreset.NONE
                current.interval == 1 && current.daysOfWeek.isEmpty() -> when (current.frequency) {
                    Frequency.DAILY -> RecurrencePreset.DAILY
                    Frequency.WEEKLY -> RecurrencePreset.WEEKLY
                    Frequency.MONTHLY -> RecurrencePreset.MONTHLY
                    Frequency.YEARLY -> RecurrencePreset.YEARLY
                }
                else -> RecurrencePreset.CUSTOM
            },
        )
    }

    var customFrequency by remember {
        mutableStateOf(current?.frequency ?: Frequency.WEEKLY)
    }
    var customInterval by remember {
        mutableIntStateOf(current?.interval ?: 1)
    }
    var customDaysOfWeek by remember {
        mutableStateOf(current?.daysOfWeek ?: emptySet())
    }
    var showFrequencyDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(text = "Repeat")
        },
        text = {
            Column {
                // Preset chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RecurrencePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = preset == selectedPreset,
                            onClick = { selectedPreset = preset },
                            label = {
                                Text(
                                    text = when (preset) {
                                        RecurrencePreset.NONE -> "None"
                                        RecurrencePreset.DAILY -> "Daily"
                                        RecurrencePreset.WEEKLY -> "Weekly"
                                        RecurrencePreset.MONTHLY -> "Monthly"
                                        RecurrencePreset.YEARLY -> "Yearly"
                                        RecurrencePreset.CUSTOM -> "Custom"
                                    },
                                )
                            },
                        )
                    }
                }

                // Custom options
                if (selectedPreset == RecurrencePreset.CUSTOM) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Frequency dropdown
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Every",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Interval input
                        TextField(
                            value = if (customInterval > 0) customInterval.toString() else "",
                            onValueChange = { value ->
                                customInterval = value.toIntOrNull()?.coerceIn(1, 99) ?: 1
                            },
                            modifier = Modifier.width(60.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Frequency selector
                        TextButton(onClick = { showFrequencyDropdown = true }) {
                            Text(
                                text = when (customFrequency) {
                                    Frequency.DAILY -> if (customInterval > 1) "days" else "day"
                                    Frequency.WEEKLY -> if (customInterval > 1) "weeks" else "week"
                                    Frequency.MONTHLY -> if (customInterval > 1) "months" else "month"
                                    Frequency.YEARLY -> if (customInterval > 1) "years" else "year"
                                },
                            )
                        }

                        DropdownMenu(
                            expanded = showFrequencyDropdown,
                            onDismissRequest = { showFrequencyDropdown = false },
                        ) {
                            Frequency.entries.forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text(freq.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        customFrequency = freq
                                        showFrequencyDropdown = false
                                    },
                                )
                            }
                        }
                    }

                    // Day of week checkboxes (for weekly)
                    if (customFrequency == Frequency.WEEKLY) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "On days:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            DayOfWeek.entries.filter { it != DayOfWeek.entries.last() || it.ordinal < 7 }
                                .take(7)
                                .forEach { day ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = day in customDaysOfWeek,
                                            onCheckedChange = { checked ->
                                                customDaysOfWeek = if (checked) {
                                                    customDaysOfWeek + day
                                                } else {
                                                    customDaysOfWeek - day
                                                }
                                            },
                                        )
                                        Text(
                                            text = day.name.take(3).lowercase()
                                                .replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val rule = when (selectedPreset) {
                        RecurrencePreset.NONE -> null
                        RecurrencePreset.DAILY -> RecurrenceRule(frequency = Frequency.DAILY)
                        RecurrencePreset.WEEKLY -> RecurrenceRule(frequency = Frequency.WEEKLY)
                        RecurrencePreset.MONTHLY -> RecurrenceRule(frequency = Frequency.MONTHLY)
                        RecurrencePreset.YEARLY -> RecurrenceRule(frequency = Frequency.YEARLY)
                        RecurrencePreset.CUSTOM -> RecurrenceRule(
                            frequency = customFrequency,
                            interval = customInterval,
                            daysOfWeek = if (customFrequency == Frequency.WEEKLY) customDaysOfWeek else emptySet(),
                        )
                    }
                    onSelected(rule)
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
