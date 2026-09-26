package com.medibridge.moduleD_shell.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.medibridge.moduleD_shell.navigation.BottomNavItem
import com.medibridge.moduleD_shell.navigation.MediBridgeNavGraph
import com.medibridge.moduleD_shell.navigation.Screen
import com.medibridge.moduleD_shell.viewmodel.SettingsViewModel

/**
 * MainShell — the root composable that wraps:
 *   • Bottom navigation bar (Home / Reminders / Settings)
 *   • The full NavGraph
 *
 * Called from MainActivity after MediBridgeTheme wraps everything.
 *
 * @param settingsViewModel  Passed from MainActivity so the theme is controlled
 *                           at the Activity level (above the NavGraph).
 */
@Composable
fun MainShell(
    settingsViewModel: SettingsViewModel,
    initialRoute: String? = null
) {
    val navController = rememberNavController()
    LaunchedEffect(initialRoute) {
        if (!initialRoute.isNullOrBlank()) {
            navController.navigate(initialRoute)
        }
    }
    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentDest   = navBackStack?.destination

    // Screens where the bottom nav should be hidden (full-screen flows)
    val hideBottomNavRoutes = setOf(Screen.Scanner.route, Screen.Chatbot.route)
    val showBottomNav       = currentDest?.route !in hideBottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    BottomNavItem.all.forEach { item ->
                        val isSelected = currentDest?.hierarchy?.any {
                            it.route == item.screen.route
                        } == true

                        NavigationBarItem(
                            selected = isSelected,
                            onClick  = {
                                navController.navigate(item.screen.route) {
                                    // Avoid stacking the same destination
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon = {
                                val icon: ImageVector = when (item) {
                                    BottomNavItem.Home      -> Icons.Filled.Home
                                    BottomNavItem.Reminders -> Icons.Filled.Notifications
                                    BottomNavItem.Settings  -> Icons.Filled.Settings
                                }
                                Icon(icon, contentDescription = item.label)
                            },
                            label = {
                                Text(item.label, fontWeight = FontWeight.Medium)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = MaterialTheme.colorScheme.primary,
                                selectedTextColor   = MaterialTheme.colorScheme.primary,
                                indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            MediBridgeNavGraph(
                navController    = navController,
                settingsViewModel = settingsViewModel
            )
        }
    }
}
