package dev.equran.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.equran.ui.home.HomeScreen
import dev.equran.ui.reader.SurahReaderScreen
import dev.equran.ui.search.SearchScreen
import dev.equran.ui.bookmarks.BookmarksScreen
import dev.equran.ui.memorization.MemorizationDashboardScreen
import dev.equran.ui.memorization.MemorizationPracticeScreen
import dev.equran.ui.readingplan.ReadingPlanScreen
import dev.equran.ui.settings.SettingsScreen
import dev.equran.ui.topics.TopicDetailScreen
import dev.equran.ui.topics.TopicsScreen

sealed class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    data object Home : BottomTab("home", "Home", Icons.Default.Book)
    data object Search : BottomTab("search", "Search", Icons.Default.Search)
    data object Bookmarks : BottomTab("bookmarks", "Bookmarks", Icons.Default.Bookmark)
    data object More : BottomTab("more", "More", Icons.Default.MoreHoriz)
}

private val tabs = listOf(BottomTab.Home, BottomTab.Search, BottomTab.Bookmarks, BottomTab.More)

@Composable
fun EQuranNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = BottomTab.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(BottomTab.Home.route) {
                HomeScreen(
                    onSurahClick = { surahNumber ->
                        navController.navigate("surah/$surahNumber")
                    },
                    onJuzClick = { surah, ayah ->
                        navController.navigate("surah/$surah?scrollToAyah=$ayah")
                    },
                )
            }

            composable(BottomTab.Search.route) {
                SearchScreen(
                    onResultClick = { surah, ayah ->
                        navController.navigate("surah/$surah?scrollToAyah=$ayah")
                    },
                )
            }

            composable(BottomTab.Bookmarks.route) {
                BookmarksScreen(
                    onVerseClick = { surah, ayah ->
                        navController.navigate("surah/$surah?scrollToAyah=$ayah")
                    },
                )
            }

            composable(BottomTab.More.route) {
                MoreScreen(
                    onSettingsClick = { navController.navigate("settings") },
                    onTopicsClick = { navController.navigate("topics") },
                    onMemorizationClick = { navController.navigate("memorization") },
                    onReadingPlanClick = { navController.navigate("reading-plan") },
                )
            }

            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }

            composable("topics") {
                TopicsScreen(
                    onBack = { navController.popBackStack() },
                    onTopicClick = { topicId -> navController.navigate("topics/$topicId") },
                )
            }

            composable(
                route = "topics/{topicId}",
                arguments = listOf(navArgument("topicId") { type = NavType.LongType }),
            ) {
                TopicDetailScreen(
                    onBack = { navController.popBackStack() },
                    onVerseClick = { surah, ayah -> navController.navigate("surah/$surah?scrollToAyah=$ayah") },
                )
            }

            composable("memorization") {
                MemorizationDashboardScreen(
                    onBack = { navController.popBackStack() },
                    onPracticeClick = { navController.navigate("memorization/practice") },
                    onSurahClick = { surahNumber -> navController.navigate("surah/$surahNumber") },
                )
            }

            composable("memorization/practice") {
                MemorizationPracticeScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable("reading-plan") {
                ReadingPlanScreen(
                    onBack = { navController.popBackStack() },
                    onStartReading = { surah, ayah ->
                        navController.navigate("surah/$surah?scrollToAyah=$ayah")
                    },
                )
            }

            composable(
                route = "surah/{surahNumber}?scrollToAyah={scrollToAyah}",
                arguments = listOf(
                    navArgument("surahNumber") { type = NavType.IntType },
                    navArgument("scrollToAyah") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) {
                SurahReaderScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToSurah = { surahNumber ->
                        navController.navigate("surah/$surahNumber") {
                            popUpTo("surah/{surahNumber}") { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
