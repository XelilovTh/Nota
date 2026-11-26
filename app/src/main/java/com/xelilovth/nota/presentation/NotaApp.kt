package com.xelilovth.nota.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navOptions
import com.xelilovth.nota.presentation.home.HomeScreen
import com.xelilovth.nota.presentation.library.LibraryScreen
import com.xelilovth.nota.presentation.library.LibraryViewModel
import com.xelilovth.nota.presentation.player.MiniPlayer
import com.xelilovth.nota.presentation.player.NowPlayingScreen
import com.xelilovth.nota.presentation.player.PlayerViewModel
import com.xelilovth.nota.presentation.playlists.PlaylistDetailScreen
import com.xelilovth.nota.presentation.playlists.PlaylistsScreen
import com.xelilovth.nota.presentation.search.SearchScreen
import com.xelilovth.nota.presentation.settings.SettingsScreen
import com.xelilovth.nota.presentation.settings.SettingsViewModel
import com.xelilovth.nota.presentation.settings.SleepTimerScreen
import com.xelilovth.nota.ui.components.BreathingAmbientBackground
import com.xelilovth.nota.ui.components.LiquidGlassBottomBar
import com.xelilovth.nota.ui.components.LiquidNavTab
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalBottomContentPadding
import com.xelilovth.nota.ui.theme.LocalHazeState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private val tabItems = listOf(
    LiquidNavTab(AppRoute.HOME, "Home", Icons.Outlined.Home),
    LiquidNavTab(AppRoute.PLAYLISTS, "Playlists", Icons.Outlined.MusicNote),
    LiquidNavTab(AppRoute.SEARCH, "Search", Icons.Outlined.Search),
    LiquidNavTab(AppRoute.LIBRARY, "Library", Icons.Outlined.LibraryMusic),
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
                "Notification permission is required for background playback controls",
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
                    "Notification permission is required for background playback controls",
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
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val showTabs = currentRoute in AppRoute.bottomTabs
    val hazeState = remember { HazeState() }
    var isPlayerOpen by rememberSaveable { mutableStateOf(false) }
    val hasMiniPlayer = remember(playerViewModel) {
        playerViewModel.playbackState
            .map { it.track != null }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false).value
    LaunchedEffect(hasMiniPlayer, isPlayerOpen) {
        if (!hasMiniPlayer && isPlayerOpen) {
            isPlayerOpen = false
        }
    }
    val bottomClearance = when {
        showTabs && hasMiniPlayer -> AppDimens.FloatingBottomClearance
        showTabs -> AppDimens.FloatingNavOnlyClearance
        hasMiniPlayer -> AppDimens.FloatingNavOnlyClearance
        else -> AppDimens.ScreenPadding
    }
    val selectedIndex = tabItems.indexOfFirst { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }.coerceAtLeast(0)

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        LocalHazeState provides hazeState,
        LocalBottomContentPadding provides bottomClearance,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
            ) {
                val ambientState by remember(playerViewModel) {
                    playerViewModel.playbackState
                        .map { Triple(it.isPlaying, it.track?.id, it.track?.uri) }
                        .distinctUntilChanged()
                }.collectAsStateWithLifecycle(initialValue = Triple(false, null, null))
                BreathingAmbientBackground(
                    isPlaying = ambientState.first,
                    trackId = ambientState.second,
                    trackUri = ambientState.third,
                    modifier = Modifier.fillMaxSize(),
                )
                NavHost(
                    navController = navController,
                    startDestination = AppRoute.HOME,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    composable(AppRoute.HOME) { HomeScreen(navController, onPlayWithNotificationPermission = requestPlaybackPermission) }
                    composable(AppRoute.PLAYLISTS) {
                        PlaylistsScreen(
                            onOpenPlaylist = { playlistId -> navController.navigate(AppRoute.playlistDetail(playlistId)) },
                        )
                    }
                    composable(
                        route = AppRoute.PLAYLIST_DETAIL,
                        arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                    ) { entry ->
                        val rawId = entry.arguments?.getString("playlistId").orEmpty()
                        val decodedId = URLDecoder.decode(rawId, StandardCharsets.UTF_8.name())
                        PlaylistDetailScreen(
                            playlistId = decodedId,
                            onBack = { navController.popBackStack() },
                            onPlayWithNotificationPermission = requestPlaybackPermission,
                        )
                    }
                    composable(AppRoute.SEARCH) {
                        val activeTrackState by remember(playerViewModel) {
                            playerViewModel.playbackState.map { it.track?.id to it.isPlaying }.distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = null to false)
                        SearchScreen(
                            activeTrackId = activeTrackState.first,
                            isPlaying = activeTrackState.second,
                            onPlayWithNotificationPermission = requestPlaybackPermission,
                        )
                    }
                    composable(AppRoute.LIBRARY) {
                        val activeTrackState by remember(playerViewModel) {
                            playerViewModel.playbackState.map { it.track?.id to it.isPlaying }.distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = null to false)
                        LibraryScreen(
                            activeTrackId = activeTrackState.first,
                            isPlaying = activeTrackState.second,
                            onPlayWithNotificationPermission = requestPlaybackPermission,
                        )
                    }
                    composable(AppRoute.SETTINGS) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenSleepTimer = { navController.navigate(AppRoute.SLEEP_TIMER) },
                        )
                    }
                    composable(AppRoute.SLEEP_TIMER) {
                        SleepTimerScreen(onBack = { navController.popBackStack() })
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0f),
                            ),
                        ),
                    )
                    .statusBarsPadding()
                    .height(AppDimens.TopScrimExtraHeight),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(AppDimens.BottomScrimHeight)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = AppDimens.FloatingNavBottomPadding),
            ) {
                MiniPlayerHost(
                    playerViewModel = playerViewModel,
                    visible = !isPlayerOpen,
                    onOpen = { isPlayerOpen = true },
                    modifier = Modifier.padding(
                        bottom = if (showTabs) AppDimens.FloatingMiniPlayerGap else AppDimens.ElementSpacing,
                    ),
                )
                if (showTabs) {
                    LiquidGlassBottomBar(
                        tabs = tabItems,
                        selectedIndex = selectedIndex,
                        onSelectTab = { index ->
                            val tab = tabItems[index]
                            navController.navigate(tab.route, navOptions {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            })
                        },
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }.coerceAtLeast(1f)
                val playerOffsetY = remember { Animatable(screenHeightPx) }
                val coroutineScope = rememberCoroutineScope()
                LaunchedEffect(isPlayerOpen, screenHeightPx) {
                    if (isPlayerOpen) {
                        playerOffsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                    } else if (playerOffsetY.value < screenHeightPx) {
                        playerOffsetY.animateTo(screenHeightPx, tween(240, easing = FastOutSlowInEasing))
                    }
                }
                if (isPlayerOpen || playerOffsetY.value < screenHeightPx - 1f) {
                    BackHandler(enabled = isPlayerOpen) { isPlayerOpen = false }
                    val bgState by remember(playerViewModel) {
                        playerViewModel.playbackState
                            .map { Triple(it.isPlaying, it.track?.id, it.track?.uri) }
                            .distinctUntilChanged()
                    }.collectAsStateWithLifecycle(initialValue = Triple(false, null, null))
                    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
                    val sleepSeconds by settingsViewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationY = playerOffsetY.value },
                    ) {
                        BreathingAmbientBackground(
                            isPlaying = bgState.first,
                            trackId = bgState.second,
                            trackUri = bgState.third,
                            modifier = Modifier.fillMaxSize(),
                        )
                        NowPlayingScreen(
                            state = playbackState,
                            viewModel = playerViewModel,
                            sleepTimerRemainingSeconds = sleepSeconds,
                            onBack = { isPlayerOpen = false },
                            onSleepTimer = {
                                isPlayerOpen = false
                                navController.navigate(AppRoute.SLEEP_TIMER)
                            },
                            onSettings = {
                                isPlayerOpen = false
                                navController.navigate(AppRoute.SETTINGS)
                            },
                            onVerticalDrag = { delta ->
                                val next = (playerOffsetY.value + delta).coerceAtLeast(0f)
                                coroutineScope.launch { playerOffsetY.snapTo(next) }
                            },
                            onVerticalDragEnd = { thresholdPx ->
                                if (playerOffsetY.value > thresholdPx) {
                                    isPlayerOpen = false
                                } else {
                                    coroutineScope.launch {
                                        playerOffsetY.animateTo(
                                            0f,
                                            spring(dampingRatio = 0.8f, stiffness = 420f),
                                        )
                                    }
                                }
                            },
                            onVerticalDragCancel = {
                                coroutineScope.launch {
                                    playerOffsetY.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.8f, stiffness = 420f),
                                    )
                                }
                            },
                            currentDragOffsetPx = { playerOffsetY.value },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerHost(
    playerViewModel: PlayerViewModel,
    visible: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    if (playbackState.track != null) {
        MiniPlayer(
            state = playbackState,
            viewModel = playerViewModel,
            onOpen = onOpen,
            modifier = modifier,
        )
    }
}
