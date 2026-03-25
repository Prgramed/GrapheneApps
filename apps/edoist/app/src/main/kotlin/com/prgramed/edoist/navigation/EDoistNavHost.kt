package com.prgramed.edoist.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prgramed.edoist.feature.inbox.InboxScreen
import com.prgramed.edoist.feature.projects.ProjectDetailScreen
import com.prgramed.edoist.feature.projects.ProjectListScreen
import com.prgramed.edoist.feature.search.SearchScreen
import com.prgramed.edoist.feature.settings.SettingsScreen
import com.prgramed.edoist.feature.taskdetail.TaskDetailScreen
import com.prgramed.edoist.feature.today.TodayScreen

private val FabRed = Color(0xFFDC4C3E)

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem(EDoistDestinations.TODAY, "Today", Icons.Default.CalendarToday),
    NavItem(EDoistDestinations.INBOX, "Inbox", Icons.Default.MoveToInbox),
    NavItem(EDoistDestinations.PROJECTS, "Browse", Icons.Default.Menu),
)

private val bottomBarRoutes = setOf(
    EDoistDestinations.TODAY,
    EDoistDestinations.INBOX,
    EDoistDestinations.PROJECTS,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EDoistNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomBarRoutes

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    navItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.route
                        } == true
                        NavigationBarItem(
                            icon = {
                                Icon(item.icon, contentDescription = item.label)
                            },
                            label = { Text(item.label) },
                            selected = selected,
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
            }
        },
        floatingActionButton = {
            if (showBottomBar) {
                FloatingActionButton(
                    onClick = { navController.navigate(EDoistDestinations.taskNew()) },
                    containerColor = FabRed,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New task")
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = EDoistDestinations.INBOX,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(EDoistDestinations.TODAY) {
                TodayScreen(
                    onTaskClick = { taskId ->
                        navController.navigate(EDoistDestinations.taskDetail(taskId))
                    },
                )
            }
            composable(EDoistDestinations.INBOX) {
                InboxScreen(
                    onTaskClick = { taskId ->
                        navController.navigate(EDoistDestinations.taskDetail(taskId))
                    },
                )
            }
            composable(EDoistDestinations.SEARCH) {
                SearchScreen(
                    onTaskClick = { taskId ->
                        navController.navigate(EDoistDestinations.taskDetail(taskId))
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(EDoistDestinations.PROJECTS) {
                ProjectListScreen(
                    onProjectClick = { projectId ->
                        navController.navigate(EDoistDestinations.projectDetail(projectId))
                    },
                    onNavigateToSettings = {
                        navController.navigate(EDoistDestinations.SETTINGS)
                    },
                    onNavigateToSearch = {
                        navController.navigate(EDoistDestinations.SEARCH)
                    },
                )
            }
            composable(
                route = EDoistDestinations.PROJECT_DETAIL,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
            ) {
                ProjectDetailScreen(
                    onBack = { navController.popBackStack() },
                    onTaskClick = { taskId ->
                        navController.navigate(EDoistDestinations.taskDetail(taskId))
                    },
                )
            }
            composable(
                route = EDoistDestinations.TASK_DETAIL,
                arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
            ) {
                TaskDetailScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = EDoistDestinations.TASK_NEW,
                arguments = listOf(
                    navArgument("projectId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("sectionId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                TaskDetailScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(EDoistDestinations.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
