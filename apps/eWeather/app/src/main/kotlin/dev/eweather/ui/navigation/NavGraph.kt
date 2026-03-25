package dev.eweather.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.eweather.ui.alerts.AlertsScreen
import dev.eweather.ui.settings.SettingsScreen
import dev.eweather.ui.locations.LocationSearchScreen
import dev.eweather.ui.locations.LocationsScreen
import dev.eweather.ui.radar.RadarScreen
import dev.eweather.ui.weather.WeatherScreen

object Routes {
    const val WEATHER = "weather"
    const val LOCATIONS = "locations"
    const val SEARCH = "search"
    const val RADAR = "radar"
    const val ALERTS = "alerts"
    const val SETTINGS = "settings"
}

@Composable
fun EWeatherNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.WEATHER,
        modifier = modifier,
    ) {
        composable(Routes.WEATHER) {
            WeatherScreen(
                onLocationClick = { navController.navigate(Routes.LOCATIONS) },
                onAddLocation = { navController.navigate(Routes.SEARCH) },
                onRadarClick = { navController.navigate(Routes.RADAR) },
                onAlertsClick = { navController.navigate(Routes.ALERTS) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.LOCATIONS) {
            LocationsScreen(
                onBack = { navController.popBackStack() },
                onAddLocation = { navController.navigate(Routes.SEARCH) },
            )
        }
        composable(Routes.SEARCH) {
            LocationSearchScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.RADAR) {
            RadarScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ALERTS) {
            AlertsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
