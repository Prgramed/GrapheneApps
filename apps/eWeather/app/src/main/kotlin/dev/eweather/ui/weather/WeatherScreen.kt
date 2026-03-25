package dev.eweather.ui.weather

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.eweather.domain.model.SavedLocation
import dev.eweather.ui.alerts.AlertBanner
import dev.eweather.ui.components.OfflineBanner
import dev.eweather.ui.components.ShimmerLoadingScreen
import dev.eweather.ui.components.StaleBanner
import dev.eweather.ui.weather.components.AstronomyCard
import dev.eweather.ui.weather.components.CurrentConditionsCard
import dev.eweather.ui.weather.components.DailyForecastCard
import dev.eweather.ui.weather.components.DetailCardsGrid
import dev.eweather.ui.weather.components.HourlyForecastStrip
import dev.eweather.ui.weather.components.SkyBackground
import dev.eweather.ui.weather.components.WeatherAnimationLayer
import dev.eweather.util.SkyCategory
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    onLocationClick: () -> Unit = {},
    onAddLocation: () -> Unit = {},
    onRadarClick: () -> Unit = {},
    onAlertsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: WeatherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val pageStates by viewModel.pageStates.collectAsStateWithLifecycle()
    val currentPageIndex by viewModel.currentPageIndex.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Permission handling
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.values.any { it }
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPerm) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
    when {
        locations.isNotEmpty() -> {
            WeatherCarousel(
                locations = locations,
                pageStates = pageStates,
                currentPageIndex = currentPageIndex,
                onLocationClick = onLocationClick,
                onRadarClick = onRadarClick,
                onAlertsClick = onAlertsClick,
                onSettingsClick = onSettingsClick,
                onRefresh = viewModel::refresh,
                onPageSettled = viewModel::onPageSettled,
            )
        }
        uiState is WeatherUiState.NoLocation -> NoLocationScreen(onAddLocation)
        else -> LoadingScreen()
    }

    // Offline banner overlay
    OfflineBanner(modifier = Modifier.align(Alignment.TopCenter))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherCarousel(
    locations: List<SavedLocation>,
    pageStates: Map<Long, WeatherUiState>,
    currentPageIndex: Int,
    onLocationClick: () -> Unit,
    onRadarClick: () -> Unit = {},
    onAlertsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onRefresh: () -> Unit,
    onPageSettled: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = currentPageIndex,
        pageCount = { locations.size },
    )

    // Sync pager with external changes (e.g., location selected in LocationsScreen)
    LaunchedEffect(currentPageIndex) {
        if (pagerState.currentPage != currentPageIndex) {
            pagerState.animateScrollToPage(currentPageIndex)
        }
    }

    // Notify ViewModel when page settles
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> onPageSettled(page) }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val locationId = locations.getOrNull(page)?.id ?: return@HorizontalPager
            val state = pageStates[locationId]

            when (state) {
                is WeatherUiState.Success -> SuccessScreen(state, onLocationClick, onRadarClick, onAlertsClick, onSettingsClick, onRefresh)
                is WeatherUiState.Error -> ErrorScreen(state.message, onRefresh)
                else -> LoadingScreen()
            }
        }

        // Dot indicator (only when multiple locations)
        if (locations.size > 1) {
            DotIndicator(
                pageCount = locations.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
            )
        }
    }
}

@Composable
private fun DotIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val width by animateDpAsState(
                targetValue = if (index == currentPage) 20.dp else 6.dp,
                label = "dot",
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) Color.White
                        else Color.White.copy(alpha = 0.4f),
                    ),
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    ShimmerLoadingScreen()
}

@Composable
private fun NoLocationScreen(onAddLocation: () -> Unit = {}) {
    Box(Modifier.fillMaxSize()) {
        SkyBackground(SkyCategory.CLEAR_DAY)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("\uD83D\uDCCD", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text("No location available", color = Color.White, fontSize = 18.sp)
            Text("GPS unavailable or permission denied", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onAddLocation) {
                Text("Add a city manually", color = Color.White)
            }
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        SkyBackground(SkyCategory.OVERCAST)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("\u26A0\uFE0F", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text(message, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text("Retry", color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuccessScreen(
    state: WeatherUiState.Success,
    onLocationClick: () -> Unit = {},
    onRadarClick: () -> Unit = {},
    onAlertsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onRefresh: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollOffset = listState.firstVisibleItemScrollOffset.toFloat()
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        // Sky background
        SkyBackground(
            skyCategory = state.skyCategory,
            currentHour = LocalTime.now().hour,
        )

        // Weather animation layer
        WeatherAnimationLayer(
            skyCategory = state.skyCategory,
            intensity = state.animationIntensity,
        )

        // Scrollable content
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                onRefresh()
                scope.launch {
                    kotlinx.coroutines.delay(2000)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                // Alert banner
                if (state.alerts.isNotEmpty()) {
                    item {
                        AlertBanner(
                            alerts = state.alerts,
                            onClick = onAlertsClick,
                        )
                    }
                }

                // Stale data banner
                val isStale = System.currentTimeMillis() - state.fetchedAt > 60 * 60 * 1000
                if (isStale) {
                    item {
                        StaleBanner(
                            fetchedAt = state.fetchedAt,
                            onRetry = onRefresh,
                        )
                    }
                }

                // Current conditions (hero)
                item {
                    CurrentConditionsCard(
                        current = state.weatherData.current,
                        todayDaily = state.weatherData.daily.firstOrNull(),
                        locationName = state.location.name,
                        onLocationClick = onLocationClick,
                        onRadarClick = onRadarClick,
                        onSettingsClick = onSettingsClick,
                    )
                }

                // Hourly forecast
                item {
                    HourlyForecastStrip(
                        hourlyPoints = state.weatherData.hourly,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                // Daily forecast
                item {
                    DailyForecastCard(
                        dailyPoints = state.weatherData.daily,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                // Detail cards grid
                item {
                    DetailCardsGrid(
                        current = state.weatherData.current,
                        airQuality = state.airQuality,
                        hourlyPoints = state.weatherData.hourly,
                        todayDaily = state.weatherData.daily.firstOrNull(),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                // Astronomy card
                item {
                    val todayDaily = state.weatherData.daily.firstOrNull()
                    if (todayDaily != null) {
                        AstronomyCard(
                            todayDaily = todayDaily,
                            moonPhase = state.moonPhase,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                // Bottom spacing
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}
