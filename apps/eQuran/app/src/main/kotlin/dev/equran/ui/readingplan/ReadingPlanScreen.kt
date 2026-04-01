package dev.equran.ui.readingplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.equran.data.repository.getVersesPerDay

private val DURATION_OPTIONS = listOf(30, 60, 90, 120, 180, 365)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReadingPlanScreen(
    onBack: () -> Unit,
    onStartReading: (surah: Int, ayah: Int) -> Unit,
    viewModel: ReadingPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showArchiveDialog by remember { mutableStateOf(false) }

    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text("Archive Plan?") },
            text = { Text("You can start a new plan after archiving this one.") },
            confirmButton = {
                TextButton(onClick = { showArchiveDialog = false; viewModel.archivePlan() }) { Text("Archive") }
            },
            dismissButton = { TextButton(onClick = { showArchiveDialog = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading Plan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.plan == null) {
                // ── Setup View ──
                Text("Start a Khatma", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Complete the entire Quran (6236 verses) in your chosen timeframe.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.planName,
                    onValueChange = { viewModel.setPlanName(it) },
                    label = { Text("Plan name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                Text("Duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DURATION_OPTIONS.forEach { days ->
                        val selected = state.selectedDays == days
                        val label = if (days == 365) "1 year" else "$days days"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { viewModel.setSelectedDays(days) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                Text(
                                    "~${getVersesPerDay(days)}/day",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "You'll read approximately ${getVersesPerDay(state.selectedDays)} verses per day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(24.dp))

                Button(onClick = { viewModel.createPlan() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Plan")
                }
            } else {
                // ── Dashboard View ──
                val plan = state.plan!!

                if (state.isFinished) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Khatma Complete!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("You finished your ${plan.totalDays}-day reading plan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(onClick = { showArchiveDialog = true }) { Text("Archive & Start New") }
                    }
                } else {
                    Text(plan.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${plan.totalDays}-day plan \u2022 Started ${plan.startDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(Modifier.height(16.dp))

                    // Stats row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem("Progress", "${state.progressPct}%")
                        StatItem("Days Left", "${state.daysRemaining}")
                        StatItem("Streak", "${state.streak}")
                        StatItem("Verses", "${state.stats?.versesRead ?: 0}")
                    }

                    Spacer(Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { state.progressPct / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )

                    Spacer(Modifier.height(20.dp))

                    // Today's assignment
                    state.assignment?.let { a ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Day ${a.dayNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${viewModel.getSurahName(a.startSurah)} ${a.startSurah}:${a.startAyah} — ${viewModel.getSurahName(a.endSurah)} ${a.endSurah}:${a.endAyah}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text("${a.versesCount} verses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onStartReading(a.startSurah, a.startAyah) }) {
                                        Text("Start Reading")
                                    }
                                    OutlinedButton(onClick = { viewModel.markTodayRead() }) {
                                        Text("Mark Done")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Day grid calendar
                    Text("Daily Progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    DayGrid(
                        totalDays = plan.totalDays,
                        currentDay = state.dayNumber,
                        readDates = state.readDates,
                        startDate = plan.startDate,
                    )

                    Spacer(Modifier.height(20.dp))

                    OutlinedButton(onClick = { showArchiveDialog = true }) {
                        Text("Archive Plan", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayGrid(totalDays: Int, currentDay: Int, readDates: Set<String>, startDate: String) {
    val start = try { java.time.LocalDate.parse(startDate) } catch (_: Exception) { return }
    val columns = 7
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (day in 1..totalDays) {
            val date = start.plusDays((day - 1).toLong()).toString()
            val isCompleted = date in readDates
            val isCurrent = day == currentDay
            val color = when {
                isCompleted -> Color(0xFF4CAF50)
                isCurrent -> MaterialTheme.colorScheme.primary
                day < currentDay -> MaterialTheme.colorScheme.surfaceContainerHighest
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
                    .then(if (isCurrent) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)) else Modifier),
            )
        }
    }
}
