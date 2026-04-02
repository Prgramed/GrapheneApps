package dev.egallery.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.egallery.ui.album.AlbumDetailScreen
import dev.egallery.ui.album.AlbumsScreen
import dev.egallery.ui.editor.EditScreen
import dev.egallery.ui.folder.FolderScreen
import dev.egallery.ui.map.MapScreen
import dev.egallery.ui.people.PeopleScreen
import dev.egallery.ui.people.PersonDetailScreen
import dev.egallery.ui.search.SearchScreen
import dev.egallery.ui.settings.SettingsScreen
import dev.egallery.ui.trash.TrashScreen
import dev.egallery.ui.viewer.UriViewerScreen
import dev.egallery.ui.timeline.TimelineScreen
import dev.egallery.ui.viewer.PhotoViewerScreen
import dev.egallery.ui.viewer.VideoPlayerScreen

private data class BottomNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomNavTabs = listOf(
    BottomNavTab(Routes.TIMELINE, "Timeline", Icons.Default.PhotoLibrary),
    BottomNavTab(Routes.FOLDER_BROWSER, "Folders", Icons.Default.Folder),
    BottomNavTab(Routes.ALBUMS, "Albums", Icons.Default.PhotoAlbum),
    BottomNavTab(Routes.PEOPLE, "People", Icons.Default.People),
    BottomNavTab(Routes.MAP, "Map", Icons.Default.Map),
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun EGalleryNavGraph(
    credentialStore: dev.egallery.data.CredentialStore,
    pendingUploadCount: Int = 0,
    isNasReachable: Boolean = true,
    pickerMode: Boolean = false,
    onPickResult: ((android.net.Uri) -> Unit)? = null,
    startUri: String? = null,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var scrollToTopTrigger by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // Hide bottom bar on viewer/player screens
    val showBottomBar = currentDestination?.route in bottomNavTabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                val isAlreadySelected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                if (isAlreadySelected && tab.route == Routes.TIMELINE) {
                                    scrollToTopTrigger++
                                } else {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                if (tab.route == Routes.TIMELINE && pendingUploadCount > 0) {
                                    BadgedBox(badge = { Badge { Text("$pendingUploadCount") } }) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) },
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
        },
    ) { padding ->
        SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = if (startUri != null) Routes.uriViewer(startUri) else Routes.TIMELINE,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(250)) },
            exitTransition = { fadeOut(tween(200)) },
        ) {
            composable(Routes.TIMELINE) {
                TimelineScreen(
                    animatedVisibilityScope = this@composable,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    onMemoryClick = { memory ->
                        MemoryHolder.current = memory
                        navController.navigate("memory_viewer")
                    },
                    onPhotoClick = { nasId: String ->
                        if (pickerMode && onPickResult != null) {
                            onPickResult(android.net.Uri.parse("content://dev.egallery/media/$nasId"))
                        } else {
                            navController.navigate(Routes.photoViewer(nasId))
                        }
                    },
                    onSearchClick = {
                        navController.navigate(Routes.SEARCH)
                    },
                    onSettingsClick = {
                        navController.navigate(Routes.SETTINGS)
                    },
                    pendingUploadCount = pendingUploadCount,
                    isNasReachable = isNasReachable,
                    pickerMode = pickerMode,
                    scrollToTopTrigger = scrollToTopTrigger,
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onPhotoClick = { nasId: String ->
                        navController.navigate(Routes.photoViewer(nasId))
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onTrashClick = { navController.navigate(Routes.TRASH) },
                )
            }

            composable(Routes.TRASH) {
                TrashScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.FOLDER_BROWSER) {
                FolderScreen(
                    onPhotoClick = { nasId: String ->
                        navController.navigate(Routes.photoViewer(nasId))
                    },
                )
            }

            composable(Routes.ALBUMS) {
                AlbumsScreen(
                    onAlbumClick = { albumId: String ->
                        navController.navigate(Routes.albumDetail(albumId))
                    },
                )
            }

            composable(
                route = Routes.ALBUM_DETAIL,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
            ) {
                AlbumDetailScreen(
                    onBack = { navController.popBackStack() },
                    onPhotoClick = { nasId: String ->
                        navController.navigate(Routes.photoViewer(nasId))
                    },
                )
            }

            composable(Routes.PEOPLE) {
                PeopleScreen(
                    onPersonClick = { personId: String ->
                        navController.navigate(Routes.personDetail(personId))
                    },
                )
            }

            composable(
                route = Routes.PERSON_DETAIL,
                arguments = listOf(navArgument("personId") { type = NavType.StringType }),
            ) {
                PersonDetailScreen(
                    onBack = { navController.popBackStack() },
                    onPhotoClick = { nasId: String ->
                        navController.navigate(Routes.photoViewer(nasId))
                    },
                )
            }

            composable(Routes.MAP) {
                MapScreen(
                    onPhotoClick = { nasId: String ->
                        navController.navigate(Routes.photoViewer(nasId))
                    },
                )
            }

            composable(
                route = Routes.URI_VIEWER,
                arguments = listOf(navArgument("uri") { type = NavType.StringType }),
            ) { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("uri") ?: ""
                UriViewerScreen(
                    uri = android.net.Uri.decode(uri),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.PHOTO_VIEWER,
                arguments = listOf(navArgument("nasId") { type = NavType.StringType }),
            ) {
                PhotoViewerScreen(
                    animatedVisibilityScope = this@composable,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    onBack = { navController.popBackStack() },
                    onVideoPlay = { nasId: String ->
                        navController.navigate(Routes.videoPlayer(nasId))
                    },
                    onEdit = { nasId: String ->
                        navController.navigate(Routes.edit(nasId))
                    },
                )
            }

            composable(
                route = Routes.EDIT,
                arguments = listOf(navArgument("nasId") { type = NavType.StringType }),
            ) {
                EditScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.VIDEO_PLAYER,
                arguments = listOf(navArgument("nasId") { type = NavType.StringType }),
            ) {
                VideoPlayerScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable("memory_viewer") {
                val memory = MemoryHolder.current
                if (memory != null) {
                    val serverUrl = credentialStore.serverUrl
                    dev.egallery.ui.memories.MemoryViewerScreen(
                        memory = memory,
                        serverUrl = serverUrl,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
        }
    }
}

/** Simple holder to pass memory data between nav destinations */
object MemoryHolder {
    var current: dev.egallery.api.dto.ImmichMemory? = null
    var serverUrl: String = ""
}
