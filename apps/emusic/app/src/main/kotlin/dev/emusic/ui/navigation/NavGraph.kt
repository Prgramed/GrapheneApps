package dev.emusic.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import dev.emusic.domain.model.Track
import dev.emusic.ui.components.MiniPlayerBar
import dev.emusic.ui.components.OfflineBanner
import dev.emusic.ui.home.HomeScreen
import dev.emusic.ui.internetradio.CountryStationsScreen
import dev.emusic.ui.internetradio.RadioBrowseScreen
import dev.emusic.ui.internetradio.RadioCountriesScreen
import dev.emusic.ui.internetradio.RadioSearchScreen
import dev.emusic.ui.internetradio.TopStationsScreen
import dev.emusic.ui.settings.equalizer.EqualizerScreen
import dev.emusic.ui.stats.StatsScreen
import dev.emusic.ui.library.genres.GenreBrowseScreen
import dev.emusic.ui.library.recent.RecentlyPlayedScreen
import dev.emusic.ui.library.LibraryScreen
import dev.emusic.ui.search.SearchScreen
import dev.emusic.ui.library.albums.AlbumDetailScreen
import dev.emusic.ui.library.artists.ArtistDetailScreen
import dev.emusic.ui.player.NowPlayingScreen
import dev.emusic.ui.playlists.PlaylistDetailScreen
import dev.emusic.ui.queue.QueueScreen
import dev.emusic.ui.settings.SettingsScreen

// --- Routes ---

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val RADIO = "radio"
    const val RADIO_SEARCH = "radio_search"
    const val SETTINGS = "settings"
    const val NOW_PLAYING = "now_playing"
    const val ARTIST_DETAIL = "artist/{artistId}"
    const val ALBUM_DETAIL = "album/{albumId}"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val QUEUE = "queue"
    const val GENRE_BROWSE = "genre_browse"
    const val RECENTLY_PLAYED = "recently_played"
    const val COUNTRY_STATIONS = "country_stations/{countryCode}/{countryName}"
    const val GENRE_DETAIL = "genre_detail/{genre}"
    const val EQUALIZER = "equalizer"

    fun genreDetail(genre: String) = "genre_detail/${android.net.Uri.encode(genre)}"
    const val LISTENING_STATS = "listening_stats"
    const val RADIO_TOP_STATIONS = "radio_top_stations/{mode}"
    const val RADIO_COUNTRIES = "radio_countries"

    fun radioTopStations(mode: String) = "radio_top_stations/$mode"

    fun artistDetail(artistId: String) = "artist/$artistId"
    fun albumDetail(albumId: String) = "album/$albumId"
    fun playlistDetail(playlistId: String) = "playlist/$playlistId"
    fun countryStations(code: String, name: String) =
        "country_stations/$code/${android.net.Uri.encode(name)}"
}

// --- Bottom nav items ---

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomNavItems = listOf(
    NavItem(Routes.HOME, "Home", Icons.Default.Home),
    NavItem(Routes.LIBRARY, "Library", Icons.Default.LibraryMusic),
    NavItem(Routes.SEARCH, "Search", Icons.Default.Search),
    NavItem(Routes.RADIO, "Radio", Icons.Default.Radio),
    NavItem(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

// --- Nav Host ---

@Composable
fun EMusicNavHost(
    currentTrack: Track?,
    coverArtUrl: String?,
    isPlaying: Boolean,
    isServerConfigured: Boolean,
    isOffline: Boolean,
    playbackProgress: Float = 0f,
    isLiveStream: Boolean = false,
    radioStationName: String? = null,
    radioFavicon: String? = null,
    radioNowPlayingText: String? = null,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val startDestination = if (isServerConfigured) Routes.HOME else Routes.SETTINGS
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isNowPlayingVisible = currentDestination?.route == Routes.NOW_PLAYING

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (!isNowPlayingVisible) {
            Column {
                MiniPlayerBar(
                    currentTrack = currentTrack,
                    coverArtUrl = coverArtUrl,
                    isPlaying = isPlaying,
                    progress = playbackProgress,
                    isLiveStream = isLiveStream,
                    radioStationName = radioStationName,
                    radioFavicon = radioFavicon,
                    radioNowPlayingText = radioNowPlayingText,
                    onPlayPause = onPlayPause,
                    onSkipNext = onSkipNext,
                    onClick = {
                        navController.navigate(Routes.NOW_PLAYING) {
                            launchSingleTop = true
                        }
                    },
                )
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
            } // if !isNowPlayingVisible
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            OfflineBanner(isOffline = isOffline)
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.weight(1f),
                enterTransition = { fadeIn(tween(250)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(250)) },
                popExitTransition = { fadeOut(tween(200)) },
            ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onAlbumClick = { navController.navigate(Routes.albumDetail(it)) },
                    onGenreBrowse = { navController.navigate(Routes.GENRE_BROWSE) },
                    onRecentlyPlayed = { navController.navigate(Routes.RECENTLY_PLAYED) },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onArtistClick = { navController.navigate(Routes.artistDetail(it)) },
                    onAlbumClick = { navController.navigate(Routes.albumDetail(it)) },
                    onPlaylistClick = { navController.navigate(Routes.playlistDetail(it)) },
                    onStatsClick = { navController.navigate(Routes.LISTENING_STATS) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onArtistClick = { navController.navigate(Routes.artistDetail(it)) },
                    onAlbumClick = { navController.navigate(Routes.albumDetail(it)) },
                )
            }
            composable(Routes.RADIO) {
                RadioBrowseScreen(
                    onSearchClick = { navController.navigate(Routes.RADIO_SEARCH) },
                    onMostPopularClick = { navController.navigate(Routes.radioTopStations("voted")) },
                    onMostListenedClick = { navController.navigate(Routes.radioTopStations("clicked")) },
                    onBrowseCountriesClick = { navController.navigate(Routes.RADIO_COUNTRIES) },
                )
            }
            composable(
                Routes.RADIO_TOP_STATIONS,
                arguments = listOf(navArgument("mode") { type = NavType.StringType }),
            ) {
                TopStationsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.RADIO_COUNTRIES) {
                RadioCountriesScreen(
                    onBack = { navController.popBackStack() },
                    onCountryClick = { code, name ->
                        navController.navigate(Routes.countryStations(code, name))
                    },
                )
            }
            composable(Routes.RADIO_SEARCH) {
                val radioBackStackEntry = remember(it) {
                    navController.getBackStackEntry(Routes.RADIO)
                }
                RadioSearchScreen(
                    viewModel = androidx.hilt.navigation.compose.hiltViewModel(
                        radioBackStackEntry,
                    ),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onEqualizerClick = { navController.navigate(Routes.EQUALIZER) },
                )
            }
            composable(Routes.EQUALIZER) {
                EqualizerScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LISTENING_STATS) {
                StatsScreen(
                    onBack = { navController.popBackStack() },
                    onArtistClick = { navController.navigate(Routes.artistDetail(it)) },
                    onAlbumClick = { navController.navigate(Routes.albumDetail(it)) },
                )
            }
            composable(
                Routes.NOW_PLAYING,
                enterTransition = { slideInVertically(tween(450)) { it } },
                exitTransition = { slideOutVertically(tween(400)) { it } },
                popEnterTransition = { slideInVertically(tween(450)) { it } },
                popExitTransition = { slideOutVertically(tween(400)) { it } },
            ) {
                NowPlayingScreen(
                    onBack = { navController.popBackStack() },
                    onAlbumClick = { navController.navigate(Routes.albumDetail(it)) },
                    onArtistClick = { navController.navigate(Routes.artistDetail(it)) },
                    onEqualizerClick = { navController.navigate(Routes.EQUALIZER) },
                )
            }
            composable(
                Routes.ARTIST_DETAIL,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) {
                ArtistDetailScreen(
                    onAlbumClick = { navController.navigate(Routes.albumDetail(it)) },
                    onArtistClick = { navController.navigate(Routes.artistDetail(it)) },
                )
            }
            composable(
                Routes.ALBUM_DETAIL,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
            ) {
                AlbumDetailScreen(
                    onArtistClick = { navController.navigate(Routes.artistDetail(it)) },
                    onAlbumClick = { navController.navigate(Routes.albumDetail(it)) },
                )
            }
            composable(Routes.QUEUE) {
                QueueScreen()
            }
            composable(
                Routes.GENRE_DETAIL,
                arguments = listOf(navArgument("genre") { type = NavType.StringType }),
            ) {
                dev.emusic.ui.library.genres.GenreDetailScreen(
                    onBack = { navController.popBackStack() },
                    onAlbumClick = { navController.navigate(Routes.albumDetail(it)) },
                )
            }
            composable(Routes.GENRE_BROWSE) {
                GenreBrowseScreen(
                    onGenreClick = { genre -> navController.navigate(Routes.genreDetail(genre)) },
                )
            }
            composable(
                Routes.PLAYLIST_DETAIL,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
            ) {
                PlaylistDetailScreen()
            }
            composable(
                Routes.COUNTRY_STATIONS,
                arguments = listOf(
                    navArgument("countryCode") { type = NavType.StringType },
                    navArgument("countryName") { type = NavType.StringType },
                ),
            ) {
                CountryStationsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.RECENTLY_PLAYED) {
                RecentlyPlayedScreen(
                    onAlbumClick = { navController.navigate(Routes.albumDetail(it)) },
                    onArtistClick = { navController.navigate(Routes.artistDetail(it)) },
                )
            }
        }
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
