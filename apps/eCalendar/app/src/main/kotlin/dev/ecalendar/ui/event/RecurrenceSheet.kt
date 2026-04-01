package dev.ecalendar.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Frequency { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }
enum class EndType { NEVER, ON_DATE, AFTER_COUNT }
enum class MonthlyMode { DAY_OF_MONTH, NTH_WEEKDAY }

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")
private val DAY_VALUES = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)
private val RRULE_DAY_MAP = mapOf(
    DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
    DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA",
    DayOfWeek.SUNDAY to "SU",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurrenceSheet(
    currentRRule: String?,
    eventStartMillis: Long,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = ZoneId.systemDefault()
    val eventDate = Instant.ofEpochMilli(eventStartMillis).atZone(zone).toLocalDate()

    // Parse existing RRULE or use defaults
    val parsed = remember(currentRRule) { parseRRule(currentRRule, eventDate) }

    var frequency by remember { mutableStateOf(parsed.frequency) }
    var interval by remember { mutableIntStateOf(parsed.interval) }
    val selectedDays = remember { mutableStateListOf(*parsed.byDay.toTypedArray()) }
    var monthlyMode by remember { mutableStateOf(parsed.monthlyMode) }
    var endType by remember { mutableStateOf(parsed.endType) }
    var untilDate by remember { mutableStateOf(parsed.untilDate ?: eventDate.plusMonths(3)) }
    var count by remember { mutableIntStateOf(parsed.count) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Repeat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            // Frequency selection
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Frequency.entries.forEach { freq ->
                    FilterChip(
                        selected = frequency == freq,
                        onClick = {
                            frequency = freq
                            if (freq == Frequency.WEEKLY && selectedDays.isEmpty()) {
                                selectedDays.add(eventDate.dayOfWeek)
                            }
                        },
                        label = {
                            Text(
                                when (freq) {
                                    Frequency.NONE -> "Never"
                                    Frequency.DAILY -> "Daily"
                                    Frequency.WEEKLY -> "Weekly"
                                    Frequency.MONTHLY -> "Monthly"
                                    Frequency.YEARLY -> "Yearly"
                                },
                            )
                        },
                    )
                }
            }

            if (frequency != Frequency.NONE) {
                Spacer(Modifier.height(16.dp))

                // Interval
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Every", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = interval.toString(),
                        onValueChange = { interval = it.toIntOrNull()?.coerceIn(1, 99) ?: 1 },
                        modifier = Modifier.width(64.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (frequency) {
                            Frequency.DAILY -> if (interval == 1) "day" else "days"
                            Frequency.WEEKLY -> if (interval == 1) "week" else "weeks"
                            Frequency.MONTHLY -> if (interval == 1) "month" else "months"
                            Frequency.YEARLY -> if (interval == 1) "year" else "years"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Weekly: day-of-week chips
                if (frequency == Frequency.WEEKLY) {
                    Spacer(Modifier.height(12.dp))
                    Text("On", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DAY_VALUES.forEachIndexed { idx, day ->
                            val isSelected = day in selectedDays
                            Text(
                                text = DAY_LABELS[idx],
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    .clickable {
                                        if (isSelected) selectedDays.remove(day)
                                        else selectedDays.add(day)
                                    }
                                    .padding(top = 8.dp),
                            )
                        }
                    }
                }

                // Monthly mode
                if (frequency == Frequency.MONTHLY) {
                    Spacer(Modifier.height(12.dp))
                    val dayOfMonth = eventDate.dayOfMonth
                    val weekNum = (dayOfMonth - 1) / 7 + 1
                    val weekLabel = when (weekNum) {
                        1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; 4 -> "4th"; else -> "last"
                    }
                    val dayName = eventDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = monthlyMode == MonthlyMode.DAY_OF_MONTH,
                            onClick = { monthlyMode = MonthlyMode.DAY_OF_MONTH },
                        )
                        Text("On day $dayOfMonth", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = monthlyMode == MonthlyMode.NTH_WEEKDAY,
                            onClick = { monthlyMode = MonthlyMode.NTH_WEEKDAY },
                        )
                        Text("On the $weekLabel $dayName", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // End condition
                Text("Ends", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endType == EndType.NEVER, onClick = { endType = EndType.NEVER })
                    Text("Never", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endType == EndType.ON_DATE, onClick = { endType = EndType.ON_DATE })
                    Text("On ", style = MaterialTheme.typography.bodyMedium)
                    if (endType == EndType.ON_DATE) {
                        Text(
                            untilDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                .clickable {
                                    // Simple: advance by 1 month each tap, or user can type
                                    untilDate = untilDate.plusMonths(1)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endType == EndType.AFTER_COUNT, onClick = { endType = EndType.AFTER_COUNT })
                    Text("After ", style = MaterialTheme.typography.bodyMedium)
                    if (endType == EndType.AFTER_COUNT) {
                        OutlinedTextField(
                            value = count.toString(),
                            onValueChange = { count = it.toIntOrNull()?.coerceIn(1, 999) ?: 10 },
                            modifier = Modifier.width(64.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("occurrences", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Preview
                val preview = buildPreviewText(frequency, interval, selectedDays, monthlyMode, endType, untilDate, count, eventDate)
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    val rrule = buildRRule(frequency, interval, selectedDays, monthlyMode, endType, untilDate, count, eventDate)
                    onConfirm(rrule)
                }) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun buildRRule(
    frequency: Frequency,
    interval: Int,
    selectedDays: List<DayOfWeek>,
    monthlyMode: MonthlyMode,
    endType: EndType,
    untilDate: LocalDate,
    count: Int,
    eventDate: LocalDate,
): String? {
    if (frequency == Frequency.NONE) return null

    val parts = mutableListOf<String>()
    parts.add(
        "FREQ=" + when (frequency) {
            Frequency.DAILY -> "DAILY"
            Frequency.WEEKLY -> "WEEKLY"
            Frequency.MONTHLY -> "MONTHLY"
            Frequency.YEARLY -> "YEARLY"
            Frequency.NONE -> return null
        },
    )

    if (interval > 1) parts.add("INTERVAL=$interval")

    if (frequency == Frequency.WEEKLY && selectedDays.isNotEmpty()) {
        val days = selectedDays.sortedBy { it.value }.mapNotNull { RRULE_DAY_MAP[it] }
        parts.add("BYDAY=${days.joinToString(",")}")
    }

    if (frequency == Frequency.MONTHLY) {
        if (monthlyMode == MonthlyMode.NTH_WEEKDAY) {
            val weekNum = (eventDate.dayOfMonth - 1) / 7 + 1
            val dayCode = RRULE_DAY_MAP[eventDate.dayOfWeek] ?: "MO"
            parts.add("BYDAY=$weekNum$dayCode")
        } else {
            parts.add("BYMONTHDAY=${eventDate.dayOfMonth}")
        }
    }

    when (endType) {
        EndType.ON_DATE -> {
            val formatted = untilDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            parts.add("UNTIL=${formatted}T235959Z")
        }
        EndType.AFTER_COUNT -> parts.add("COUNT=$count")
        EndType.NEVER -> {} // no end clause
    }

    return parts.joinToString(";")
}

private fun buildPreviewText(
    frequency: Frequency,
    interval: Int,
    selectedDays: List<DayOfWeek>,
    monthlyMode: MonthlyMode,
    endType: EndType,
    untilDate: LocalDate,
    count: Int,
    eventDate: LocalDate,
): String {
    if (frequency == Frequency.NONE) return "Does not repeat"

    val freqText = when (frequency) {
        Frequency.DAILY -> if (interval == 1) "day" else "$interval days"
        Frequency.WEEKLY -> if (interval == 1) "week" else "$interval weeks"
        Frequency.MONTHLY -> if (interval == 1) "month" else "$interval months"
        Frequency.YEARLY -> if (interval == 1) "year" else "$interval years"
        Frequency.NONE -> ""
    }

    val sb = StringBuilder("Repeats every $freqText")

    if (frequency == Frequency.WEEKLY && selectedDays.isNotEmpty()) {
        val dayNames = selectedDays.sortedBy { it.value }
            .map { it.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) }
        sb.append(" on ${dayNames.joinToString(", ")}")
    }

    if (frequency == Frequency.MONTHLY) {
        if (monthlyMode == MonthlyMode.NTH_WEEKDAY) {
            val weekNum = (eventDate.dayOfMonth - 1) / 7 + 1
            val weekLabel = when (weekNum) { 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; 4 -> "4th"; else -> "last" }
            val dayName = eventDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
            sb.append(" on the $weekLabel $dayName")
        } else {
            sb.append(" on day ${eventDate.dayOfMonth}")
        }
    }

    when (endType) {
        EndType.ON_DATE -> sb.append(" until ${untilDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}")
        EndType.AFTER_COUNT -> sb.append(", $count times")
        EndType.NEVER -> {}
    }

    return sb.toString()
}

private data class ParsedRRule(
    val frequency: Frequency = Frequency.NONE,
    val interval: Int = 1,
    val byDay: List<DayOfWeek> = emptyList(),
    val monthlyMode: MonthlyMode = MonthlyMode.DAY_OF_MONTH,
    val endType: EndType = EndType.NEVER,
    val untilDate: LocalDate? = null,
    val count: Int = 10,
)

private val REVERSE_DAY_MAP = mapOf(
    "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
    "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY,
    "SU" to DayOfWeek.SUNDAY,
)

private fun parseRRule(rrule: String?, eventDate: LocalDate): ParsedRRule {
    if (rrule.isNullOrBlank()) return ParsedRRule()

    val parts = rrule.split(";").mapNotNull {
        val split = it.split("=", limit = 2)
        if (split.size == 2 && split[0].isNotBlank()) split[0] to split[1] else null
    }.toMap()

    val freq = when (parts["FREQ"]) {
        "DAILY" -> Frequency.DAILY
        "WEEKLY" -> Frequency.WEEKLY
        "MONTHLY" -> Frequency.MONTHLY
        "YEARLY" -> Frequency.YEARLY
        else -> Frequency.NONE
    }

    val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1

    val byDay = parts["BYDAY"]?.split(",")?.mapNotNull { token ->
        val dayCode = token.filter { it.isLetter() }
        REVERSE_DAY_MAP[dayCode]
    } ?: emptyList()

    val monthlyMode = if (freq == Frequency.MONTHLY && parts["BYDAY"] != null) {
        MonthlyMode.NTH_WEEKDAY
    } else {
        MonthlyMode.DAY_OF_MONTH
    }

    val endType: EndType
    val untilDate: LocalDate?
    val count: Int

    when {
        parts.containsKey("UNTIL") -> {
            endType = EndType.ON_DATE
            val untilStr = parts["UNTIL"]!!.take(8) // yyyyMMdd
            untilDate = try {
                LocalDate.parse(untilStr, DateTimeFormatter.ofPattern("yyyyMMdd"))
            } catch (_: Exception) {
                eventDate.plusMonths(3)
            }
            count = 10
        }
        parts.containsKey("COUNT") -> {
            endType = EndType.AFTER_COUNT
            count = parts["COUNT"]?.toIntOrNull() ?: 10
            untilDate = null
        }
        else -> {
            endType = EndType.NEVER
            untilDate = null
            count = 10
        }
    }

    return ParsedRRule(freq, interval, byDay, monthlyMode, endType, untilDate, count)
}

fun rruleDisplayText(rrule: String?, eventStartMillis: Long): String {
    if (rrule.isNullOrBlank()) return "Does not repeat"
    val zone = ZoneId.systemDefault()
    val eventDate = Instant.ofEpochMilli(eventStartMillis).atZone(zone).toLocalDate()
    val parsed = parseRRule(rrule, eventDate)
    return buildPreviewText(
        parsed.frequency, parsed.interval, parsed.byDay, parsed.monthlyMode,
        parsed.endType, parsed.untilDate ?: eventDate.plusMonths(3), parsed.count, eventDate,
    )
}
