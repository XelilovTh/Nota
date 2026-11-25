package com.xelilovth.nota.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.xelilovth.nota.presentation.home.HomeScreen
import com.xelilovth.nota.presentation.library.LibraryScreen
import com.xelilovth.nota.presentation.player.MiniPlayer
import com.xelilovth.nota.presentation.player.NowPlayingScreen
import com.xelilovth.nota.presentation.player.PlayerViewModel
import com.xelilovth.nota.presentation.playlists.PlaylistsScreen
import com.xelilovth.nota.presentation.search.SearchScreen
import com.xelilovth.nota.presentation.settings.SettingsScreen
import com.xelilovth.nota.presentation.settings.SleepTimerScreen
import com.xelilovth.nota.ui.components.ComingSoonScreen

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabItems = listOf(
    TabItem(AppRoute.HOME, "Home", Icons.Outlined.Home),
    TabItem(AppRoute.PLAYLISTS, "Playlists", Icons.Outlined.MusicNote),
    TabItem(AppRoute.SEARCH, "Search", Icons.Outlined.Search),
    TabItem(AppRoute.LIBRARY, "Library", Icons.Outlined.LibraryMusic),
)

@Composable
fun NotaApp() {
    val context = LocalContext.current
    var notificationPermissionRequested by remember { mutableStateOf(false) }
    var pendingPlaybackAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                "Bildiriş icazəsi olmadan arxa planda idarəetmə işləməyəcək",
                Toast.LENGTH_LONG,
            ).show()
        }
        val action = pendingPlaybackAction
        pendingPlaybackAction = null
        action?.invoke()
    }
    val requestPlaybackPermission: (() -> Unit) -> Unit = remember(context) {
        { action ->
            val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (permissionGranted) {
                action()
            } else if (!notificationPermissionRequested) {
                notificationPermissionRequested = true
                pendingPlaybackAction = action
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(
                    context,
                    "Bildiriş icazəsi olmadan arxa planda idarəetmə işləməyəcək",
                    Toast.LENGTH_LONG,
                ).show()
                action()
            }
        }
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val showTabs = currentRoute in AppRoute.bottomTabs

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                MiniPlayerHost(
                    playerViewModel = playerViewModel,
                    visible = currentRoute != AppRoute.NOW_PLAYING,
                    onOpen = { navController.navigate(AppRoute.NOW_PLAYING) },
                )
                if (showTabs) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
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
            composable(AppRoute.HOME) { HomeScreen(navController, onPlayWithNotificationPermission = requestPlaybackPermission) }
            composable(AppRoute.PLAYLISTS) { PlaylistsScreen(onPlayWithNotificationPermission = requestPlaybackPermission) }
            composable(AppRoute.SEARCH) { SearchScreen(onPlayWithNotificationPermission = requestPlaybackPermission) }
            composable(AppRoute.LIBRARY) { LibraryScreen(onPlayWithNotificationPermission = requestPlaybackPermission) }
            composable(AppRoute.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSleepTimer = { navController.navigate(AppRoute.SLEEP_TIMER) },
                )
            }
            composable(AppRoute.SLEEP_TIMER) {
                SleepTimerScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoute.LYRICS) {
                ComingSoonScreen("Lyrics", onBack = { navController.popBackStack() })
            }
            composable(AppRoute.NOW_PLAYING) {
                val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
                NowPlayingScreen(
                    state = playbackState,
                    viewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onSleepTimer = { navController.navigate(AppRoute.SLEEP_TIMER) },
                    onLyrics = { navController.navigate(AppRoute.LYRICS) },
                    onSettings = { navController.navigate(AppRoute.SETTINGS) },
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerHost(
    playerViewModel: PlayerViewModel,
    visible: Boolean,
    onOpen: () -> Unit,
) {
    if (!visible) return
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    if (playbackState.track != null) {
        MiniPlayer(playbackState, playerViewModel, onOpen)
    }
}
