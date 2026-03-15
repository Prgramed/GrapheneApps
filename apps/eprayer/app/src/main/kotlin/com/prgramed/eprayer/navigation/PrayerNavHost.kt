package com.prgramed.eprayer.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prgramed.eprayer.feature.prayertimes.PrayerTimesScreen
import com.prgramed.eprayer.feature.qibla.QiblaScreen
import com.prgramed.eprayer.feature.settings.SettingsScreen

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem(PrayerDestinations.PRAYER_TIMES, "Prayers", Icons.Default.Schedule),
    NavItem(PrayerDestinations.QIBLA, "Qibla", Icons.Default.Explore),
    NavItem(PrayerDestinations.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
fun PrayerNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                navItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any {
                            it.route == item.route
                        } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = PrayerDestinations.PRAYER_TIMES,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(PrayerDestinations.PRAYER_TIMES) { PrayerTimesScreen() }
            composable(PrayerDestinations.QIBLA) { QiblaScreen() }
            composable(PrayerDestinations.SETTINGS) { SettingsScreen() }
        }
    }
}
