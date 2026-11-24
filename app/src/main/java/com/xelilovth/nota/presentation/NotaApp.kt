package com.xelilovth.nota.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.xelilovth.nota.presentation.home.HomeScreen
import com.xelilovth.nota.presentation.library.LibraryScreen
import com.xelilovth.nota.presentation.playlists.PlaylistsScreen
import com.xelilovth.nota.presentation.player.MiniPlayer
import com.xelilovth.nota.presentation.player.NowPlayingScreen
import com.xelilovth.nota.presentation.player.PlayerViewModel
import com.xelilovth.nota.presentation.search.SearchScreen
import com.xelilovth.nota.ui.components.ComingSoonScreen
import com.xelilovth.nota.ui.components.PlaceholderAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class TabItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val tabItems = listOf(
    TabItem(AppRoute.HOME, "Home", Icons.Outlined.Home),
    TabItem(AppRoute.PLAYLISTS, "Playlists", Icons.Outlined.MusicNote),
    TabItem(AppRoute.SEARCH, "Search", Icons.Outlined.Search),
    TabItem(AppRoute.LIBRARY, "Library", Icons.Outlined.LibraryMusic),
)

@Composable
fun NotaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val showTabs = currentRoute in AppRoute.bottomTabs

    Scaffold(
        bottomBar = {
            Column {
                if (playbackState.track != null && currentRoute != AppRoute.NOW_PLAYING) {
                    MiniPlayer(playbackState, playerViewModel) { navController.navigate(AppRoute.NOW_PLAYING) }
                }
                if (showTabs) {
                    NavigationBar {
                        tabItems.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route, navOptions {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    })
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppRoute.HOME) { HomeScreen(navController) }
            composable(AppRoute.PLAYLISTS) { PlaylistsScreen() }
            composable(AppRoute.SEARCH) { SearchScreen() }
            composable(AppRoute.LIBRARY) { LibraryScreen() }
            composable(AppRoute.SETTINGS) {
                ComingSoonScreen(
                    title = "Settings",
                    onBack = { navController.popBackStack() },
                    actions = listOf(
                        PlaceholderAction("Equalizer") { navController.navigate(AppRoute.EQUALIZER) },
                        PlaceholderAction("Sleep Timer") { navController.navigate(AppRoute.SLEEP_TIMER) },
                        PlaceholderAction("Lyrics") { navController.navigate(AppRoute.LYRICS) },
                    ),
                )
            }
            composable(AppRoute.EQUALIZER) {
                ComingSoonScreen("Equalizer", onBack = { navController.popBackStack() })
            }
            composable(AppRoute.SLEEP_TIMER) {
                ComingSoonScreen("Sleep Timer", onBack = { navController.popBackStack() })
            }
            composable(AppRoute.LYRICS) {
                ComingSoonScreen("Lyrics", onBack = { navController.popBackStack() })
            }
            composable(AppRoute.NOW_PLAYING) {
                NowPlayingScreen(
                    state = playbackState,
                    viewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onEqualizer = { navController.navigate(AppRoute.EQUALIZER) },
                    onSleepTimer = { navController.navigate(AppRoute.SLEEP_TIMER) },
                    onLyrics = { navController.navigate(AppRoute.LYRICS) },
                )
            }
        }
    }
}
