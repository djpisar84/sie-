package com.dronehacks.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.dronehacks.app.model.ConnectionState
import com.dronehacks.app.ui.screens.*
import com.dronehacks.app.ui.theme.DroneOrange
import com.dronehacks.app.viewmodel.DroneViewModel

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Connect : Screen("connect", "Connect", Icons.Filled.Link, Icons.Outlined.Link)
    data object Mods : Screen("mods", "Modify", Icons.Filled.Tune, Icons.Outlined.Tune)
    data object Nfz : Screen("nfz", "NFZ", Icons.Filled.LocationOff, Icons.Outlined.LocationOff)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun DroneHacksNavigation() {
    val navController = rememberNavController()
    val viewModel: DroneViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()

    val bottomNavItems = listOf(Screen.Home, Screen.Connect, Screen.Mods, Screen.Nfz, Screen.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    val isLocked = (screen == Screen.Mods || screen == Screen.Nfz) &&
                            state.connectionState != ConnectionState.CONNECTED
                    NavigationBarItem(
                        icon = {
                            if (isLocked) {
                                Icon(Icons.Filled.Lock, contentDescription = screen.label)
                            } else {
                                Icon(
                                    if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.label
                                )
                            }
                        },
                        label = { Text(screen.label) },
                        selected = selected,
                        onClick = {
                            if (!isLocked) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DroneOrange,
                            selectedTextColor = DroneOrange,
                            indicatorColor = DroneOrange.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToConnect = { navController.navigate(Screen.Connect.route) },
                    onNavigateToMods = { navController.navigate(Screen.Mods.route) },
                    onNavigateToNfz = { navController.navigate(Screen.Nfz.route) }
                )
            }
            composable(Screen.Connect.route) {
                ConnectScreen(
                    viewModel = viewModel,
                    onConnected = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Connect.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Mods.route) {
                ModificationsScreen(viewModel = viewModel)
            }
            composable(Screen.Nfz.route) {
                NfzScreen(viewModel = viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
