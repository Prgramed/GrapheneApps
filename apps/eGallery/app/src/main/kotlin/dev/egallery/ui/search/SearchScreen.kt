package dev.egallery.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPhotoClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val resultCount by viewModel.resultCount.collectAsState()
    val selectedMediaType by viewModel.selectedMediaType.collectAsState()
    val selectedCountry by viewModel.selectedCountry.collectAsState()
    val selectedCity by viewModel.selectedCity.collectAsState()
    val countrySuggestions by viewModel.countrySuggestions.collectAsState()
    val citySuggestions by viewModel.citySuggestions.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val fromDate by viewModel.fromDate.collectAsState()
    val toDate by viewModel.toDate.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar with search field
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            },
        )

        // Search input
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            placeholder = { Text("Search photos (AI-powered)...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { viewModel.query.value = "" }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(8.dp))

        // Filter chips row
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Media type filters
            FilterChip(
                selected = selectedMediaType == null,
                onClick = { viewModel.setMediaTypeFilter(null) },
                label = { Text("All") },
            )
            FilterChip(
                selected = selectedMediaType == "IMAGE",
                onClick = { viewModel.setMediaTypeFilter(if (selectedMediaType == "IMAGE") null else "IMAGE") },
                label = { Text("Photos") },
            )
            FilterChip(
                selected = selectedMediaType == "VIDEO",
                onClick = { viewModel.setMediaTypeFilter(if (selectedMediaType == "VIDEO") null else "VIDEO") },
                label = { Text("Videos") },
            )

            // Date range
            FilterChip(
                selected = fromDate != null || toDate != null,
                onClick = { showDatePicker = true },
                label = {
                    if (fromDate != null || toDate != null) {
                        val fmt = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
                        val from = fromDate?.let { fmt.format(java.util.Date(it)) } ?: "..."
                        val to = toDate?.let { fmt.format(java.util.Date(it)) } ?: "..."
                        Text("$from - $to")
                    } else {
                        Text("Date")
                    }
                },
            )

            // Country filter
            var showCountryMenu by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = selectedCountry != null,
                    onClick = { showCountryMenu = true },
                    label = { Text(selectedCountry ?: "Country") },
                )
                DropdownMenu(expanded = showCountryMenu, onDismissRequest = { showCountryMenu = false }) {
                    DropdownMenuItem(text = { Text("All countries") }, onClick = {
                        viewModel.setCountryFilter(null)
                        showCountryMenu = false
                    })
                    countrySuggestions.forEach { country ->
                        DropdownMenuItem(text = { Text(country) }, onClick = {
                            viewModel.setCountryFilter(country)
                            showCountryMenu = false
                        })
                    }
                }
            }

            // City filter (only if country selected)
            if (selectedCountry != null && citySuggestions.isNotEmpty()) {
                var showCityMenu by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = selectedCity != null,
                        onClick = { showCityMenu = true },
                        label = { Text(selectedCity ?: "City") },
                    )
                    DropdownMenu(expanded = showCityMenu, onDismissRequest = { showCityMenu = false }) {
                        DropdownMenuItem(text = { Text("All cities") }, onClick = {
                            viewModel.setCityFilter(null)
                            showCityMenu = false
                        })
                        citySuggestions.forEach { city ->
                            DropdownMenuItem(text = { Text(city) }, onClick = {
                                viewModel.setCityFilter(city)
                                showCityMenu = false
                            })
                        }
                    }
                }
            }

            // Clear all filters
            if (selectedMediaType != null || selectedCountry != null || fromDate != null) {
                FilterChip(
                    selected = false,
                    onClick = { viewModel.clearFilters() },
                    label = { Text("Clear") },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Progress / result count
        if (isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (resultCount > 0) {
            Text(
                text = "$resultCount results",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Results grid or recent searches
        if (results.isEmpty() && query.isBlank() && !isSearching) {
            // Show recent searches
            if (recentSearches.isNotEmpty()) {
                Text(
                    text = "Recent searches",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                recentSearches.forEach { recent ->
                    Text(
                        text = recent,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.query.value = recent
                                viewModel.saveRecentSearch(recent)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                TextButton(
                    onClick = { viewModel.clearRecentSearches() },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text("Clear recent")
                }
            }
        } else if (results.isEmpty() && !isSearching && query.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (results.isNotEmpty()) {
            // Results grid
            val context = LocalContext.current
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(results, key = { it.nasId }) { item ->
                    AsyncImage(
                        model = remember(item.nasId) {
                            ImageRequest.Builder(context)
                                .data(viewModel.thumbnailUrl(item.nasId))
                                .size(360)
                                .memoryCacheKey("thumb_${item.nasId}")
                                .diskCacheKey("thumb_${item.nasId}")
                                .build()
                        },
                        contentDescription = item.filename,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onPhotoClick(item.nasId) }
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }

    // Date range picker dialog
    if (showDatePicker) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDateRange(state.selectedStartDateMillis, state.selectedEndDateMillis)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setDateRange(null, null)
                    showDatePicker = false
                }) { Text("Clear") }
            },
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(500.dp))
        }
    }
}
