package com.xelilovth.nota.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.xelilovth.nota.ui.components.PlaceholderScreen
import com.xelilovth.nota.presentation.library.LibraryScreen

private data class TabItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val tabItems = listOf(
    TabItem(AppRoute.HOME, "Home", Icons.Outlined.Home),
    TabItem(AppRoute.LIBRARY, "Library", Icons.Outlined.LibraryMusic),
    TabItem(AppRoute.SEARCH, "Search", Icons.Outlined.Search),
    TabItem(AppRoute.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun NotaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppRoute.HOME) {
                PlaceholderScreen("Home", "Your music experience starts here.")
            }
            composable(AppRoute.LIBRARY) {
                LibraryScreen()
            }
            composable(AppRoute.SEARCH) {
                PlaceholderScreen("Search", "Find music in your collection.")
            }
            composable(AppRoute.SETTINGS) {
                PlaceholderScreen("Settings", "App preferences will live here.")
            }
        }
    }
}
