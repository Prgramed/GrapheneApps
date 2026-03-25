package com.grapheneapps.enotes.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.grapheneapps.enotes.feature.notes.NoteListScreen

object Routes {
    const val ALL_NOTES = "all_notes"
    const val FOLDERS = "folders"
    const val FOLDER_NOTES = "folder_notes/{folderId}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{noteId}"
    const val NEW_NOTE = "new_note"
    const val RECENTLY_DELETED = "recently_deleted"
    const val PINNED_NOTES = "pinned_notes"
    const val LOCKED_NOTES = "locked_notes"
    const val CONFLICTS = "conflicts"

    fun folderNotes(folderId: String) = "folder_notes/$folderId"
    fun editor(noteId: String) = "editor/$noteId"
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomNavItems = listOf(
    NavItem(Routes.ALL_NOTES, "Notes", Icons.Default.Description),
    NavItem(Routes.FOLDERS, "Folders", Icons.Default.Folder),
    NavItem(Routes.SEARCH, "Search", Icons.Default.Search),
    NavItem(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
fun ENotesNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Hide bottom bar on editor screen
    val showBottomBar = currentDestination?.route?.startsWith("editor") != true &&
        currentDestination?.route != Routes.NEW_NOTE

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.route
                        } == true
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.ALL_NOTES,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.ALL_NOTES) {
                NoteListScreen(
                    folderId = null,
                    folderName = "All Notes",
                    onNoteClick = { noteId -> navController.navigate(Routes.editor(noteId)) },
                    onNewNote = { navController.navigate(Routes.NEW_NOTE) },
                )
            }
            composable(Routes.FOLDERS) {
                com.grapheneapps.enotes.feature.notes.FolderListScreen(
                    onFolderClick = { folderId -> navController.navigate(Routes.folderNotes(folderId)) },
                    onRecentlyDeletedClick = { navController.navigate(Routes.RECENTLY_DELETED) },
                    onPinnedClick = { navController.navigate(Routes.PINNED_NOTES) },
                    onLockedClick = { navController.navigate(Routes.LOCKED_NOTES) },
                    onConflictsClick = { navController.navigate(Routes.CONFLICTS) },
                )
            }
            composable(Routes.RECENTLY_DELETED) {
                com.grapheneapps.enotes.feature.notes.DeletedNotesScreen(
                    onNoteClick = { noteId -> navController.navigate(Routes.editor(noteId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PINNED_NOTES) {
                NoteListScreen(
                    folderId = "__pinned__",
                    folderName = "Pinned Notes",
                    onNoteClick = { noteId -> navController.navigate(Routes.editor(noteId)) },
                    onNewNote = {},
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LOCKED_NOTES) {
                NoteListScreen(
                    folderId = "__locked__",
                    folderName = "Locked Notes",
                    onNoteClick = { noteId -> navController.navigate(Routes.editor(noteId)) },
                    onNewNote = {},
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.CONFLICTS) {
                NoteListScreen(
                    folderId = "__conflicts__",
                    folderName = "Conflicts",
                    onNoteClick = { noteId -> navController.navigate(Routes.editor(noteId)) },
                    onNewNote = {},
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.FOLDER_NOTES,
                arguments = listOf(navArgument("folderId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val folderId = backStackEntry.arguments?.getString("folderId") ?: ""
                NoteListScreen(
                    folderId = folderId,
                    onNoteClick = { noteId -> navController.navigate(Routes.editor(noteId)) },
                    onNewNote = { navController.navigate(Routes.NEW_NOTE) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SEARCH) {
                com.grapheneapps.enotes.feature.notes.SearchScreen(
                    onNoteClick = { noteId -> navController.navigate(Routes.editor(noteId)) },
                )
            }
            composable(Routes.SETTINGS) {
                com.grapheneapps.enotes.feature.settings.SettingsScreen()
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
            ) {
                com.grapheneapps.enotes.feature.editor.EditorScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.NEW_NOTE) {
                // New note creates a note in NoteListViewModel.createNote() then navigates to editor
                // This route shouldn't be reached directly, but handle gracefully
                com.grapheneapps.enotes.feature.editor.EditorScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
