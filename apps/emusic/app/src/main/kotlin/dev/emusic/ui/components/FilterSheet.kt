package dev.emusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.emusic.ui.library.LibraryFilter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    currentFilter: LibraryFilter,
    availableGenres: List<String>,
    onApply: (LibraryFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(currentFilter) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    filter = LibraryFilter()
                }) { Text("Clear") }
            }

            Spacer(Modifier.height(12.dp))

            // Toggles
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Starred only", modifier = Modifier.weight(1f))
                Switch(
                    checked = filter.starredOnly,
                    onCheckedChange = { filter = filter.copy(starredOnly = it) },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Downloaded only", modifier = Modifier.weight(1f))
                Switch(
                    checked = filter.downloadedOnly,
                    onCheckedChange = { filter = filter.copy(downloadedOnly = it) },
                )
            }

            // Genres
            if (availableGenres.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Genres", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableGenres.take(20).forEach { genre ->
                        FilterChip(
                            selected = genre in filter.genres,
                            onClick = {
                                filter = if (genre in filter.genres) {
                                    filter.copy(genres = filter.genres - genre)
                                } else {
                                    filter.copy(genres = filter.genres + genre)
                                }
                            },
                            label = { Text(genre) },
                        )
                    }
                }
            }

            // Decades
            Spacer(Modifier.height(12.dp))
            Text("Decades", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            val decades = listOf("2020s", "2010s", "2000s", "1990s", "1980s", "1970s", "1960s", "Older")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                decades.forEach { decade ->
                    FilterChip(
                        selected = decade in filter.decades,
                        onClick = {
                            filter = if (decade in filter.decades) {
                                filter.copy(decades = filter.decades - decade)
                            } else {
                                filter.copy(decades = filter.decades + decade)
                            }
                        },
                        label = { Text(decade) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    onApply(filter)
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
