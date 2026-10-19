package com.medibridge.moduleD_shell.ui

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.medibridge.moduleD_shell.navigation.BottomNavItem
import com.medibridge.moduleD_shell.navigation.MediBridgeNavGraph
import com.medibridge.moduleD_shell.navigation.Screen
import com.medibridge.moduleD_shell.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * MainShell — Root layout wrapping:
 *   • ModalNavigationDrawer sidebar listing user account and all clinical features
 *   • Bottom navigation bar (Home, Scan, Record, Medi AI, Settings) with vibrant colorful icons & animations
 *   • Navigation graph hosting all screens
 */
@Composable
fun MainShell(
    settingsViewModel: SettingsViewModel,
    initialRoute: String? = null
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialRoute) {
        if (!initialRoute.isNullOrBlank()) {
            navController.navigate(initialRoute)
        }
    }
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentDest = navBackStack?.destination

    // Screens where the bottom nav should be hidden (full-screen sub-flows & intro)
    val hideBottomNavRoutes = setOf(Screen.Intro.route, Screen.AddEditMedication.route, Screen.SafetyDashboard.route)
    val showBottomNav = currentDest?.route !in hideBottomNavRoutes

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                DrawerSidebarContent(
                    navController = navController,
                    currentRoute = currentDest?.route,
                    onCloseDrawer = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                if (showBottomNav) {
                    // ── Taste-Skill Floating Island Bottom Bar ─────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 8.dp,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                                modifier = Modifier.height(68.dp)
                            ) {
                                BottomNavItem.all.forEach { item ->
                                    val isSelected = currentDest?.hierarchy?.any {
                                        it.route == item.screen.route
                                    } == true

                                    // Distinct vibrant color palette per feature
                                    val (activeColor, activeContainer) = when (item) {
                                        BottomNavItem.Home -> Color(0xFF1E88E5) to Color(0xFFE3F2FD)       // Medical Blue
                                        BottomNavItem.Scanner -> Color(0xFF8E24AA) to Color(0xFFF3E5F5)    // Vibrant Purple
                                        BottomNavItem.Recording -> Color(0xFFE53935) to Color(0xFFFFEBEE)  // Coral Red
                                        BottomNavItem.Chatbot -> Color(0xFF00897B) to Color(0xFFE0F2F1)    // Emerald Teal
                                        BottomNavItem.Settings -> Color(0xFF3949AB) to Color(0xFFE8EAF6)   // Slate Indigo
                                    }

                                    val scale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.15f else 1.0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        ),
                                        label = "bottomIconScale"
                                    )

                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            navController.navigate(item.screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            val iconVector: ImageVector = when (item) {
                                                BottomNavItem.Home -> Icons.Filled.Home
                                                BottomNavItem.Scanner -> Icons.Filled.DocumentScanner
                                                BottomNavItem.Recording -> Icons.Filled.Mic
                                                BottomNavItem.Chatbot -> Icons.Filled.AutoAwesome
                                                BottomNavItem.Settings -> Icons.Filled.Settings
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) activeContainer else Color.Transparent,
                                                modifier = Modifier.scale(scale)
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = iconVector,
                                                        contentDescription = item.label,
                                                        tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = item.label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = activeColor,
                                            selectedTextColor = activeColor,
                                            indicatorColor = Color.Transparent,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                MediBridgeNavGraph(
                    navController = navController,
                    settingsViewModel = settingsViewModel,
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }
        }
    }
}

/**
 * Sidebar drawer content displaying user account header, clinical services, and system options.
 */
@Composable
private fun DrawerSidebarContent(
    navController: NavHostController,
    currentRoute: String?,
    onCloseDrawer: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("medibridge_prefs", Context.MODE_PRIVATE) }
    val patientName = prefs.getString("patient_name", "My Health Profile") ?: "My Health Profile"
    val caretakerPhone = prefs.getString("caretaker_phone", "Not configured") ?: "Not configured"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header: Account & Profile Enclave (Double-Bezel Architecture) ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(22.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = patientName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "ACTIVE PATIENT ENCLAVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Emergency Contact: $caretakerPhone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Clinical Services Section ─────────────────────────────────────────
        Text(
            text = "CLINICAL SERVICES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        NavigationDrawerItem(
            label = { Text("Home Dashboard", fontWeight = FontWeight.SemiBold) },
            icon = { DrawerIconBadge(Icons.Filled.Home, Color(0xFF1E88E5), Color(0xFFE3F2FD)) },
            selected = currentRoute == Screen.Home.route,
            onClick = {
                onCloseDrawer()
                navController.navigate(Screen.Home.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            label = { Text("Scan Prescription & Bill", fontWeight = FontWeight.SemiBold) },
            icon = { DrawerIconBadge(Icons.Filled.DocumentScanner, Color(0xFF8E24AA), Color(0xFFF3E5F5)) },
            selected = currentRoute == Screen.Scanner.route,
            onClick = {
                onCloseDrawer()
                navController.navigate(Screen.Scanner.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            label = { Text("Consultation Audio Recorder", fontWeight = FontWeight.SemiBold) },
            icon = { DrawerIconBadge(Icons.Filled.Mic, Color(0xFFE53935), Color(0xFFFFEBEE)) },
            selected = currentRoute == Screen.Recording.route,
            onClick = {
                onCloseDrawer()
                navController.navigate(Screen.Recording.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            label = { Text("Medi AI Clinical Assistant", fontWeight = FontWeight.SemiBold) },
            icon = { DrawerIconBadge(Icons.Filled.AutoAwesome, Color(0xFF00897B), Color(0xFFE0F2F1)) },
            selected = currentRoute == Screen.Chatbot.route,
            onClick = {
                onCloseDrawer()
                navController.navigate(Screen.Chatbot.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            label = { Text("Medication Reminders", fontWeight = FontWeight.SemiBold) },
            icon = { DrawerIconBadge(Icons.Filled.NotificationsActive, Color(0xFFF57C00), Color(0xFFFFF3E0)) },
            selected = currentRoute == Screen.Reminders.route,
            onClick = {
                onCloseDrawer()
                navController.navigate(Screen.Reminders.route) {
                    launchSingleTop = true
                }
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            label = { Text("Drug Safety & Interactions", fontWeight = FontWeight.SemiBold) },
            icon = { DrawerIconBadge(Icons.Filled.Shield, Color(0xFF2E7D32), Color(0xFFE8F5E9)) },
            selected = currentRoute == Screen.SafetyDashboard.route,
            onClick = {
                onCloseDrawer()
                navController.navigate(Screen.SafetyDashboard.route) {
                    launchSingleTop = true
                }
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))

        // ── System & Management Section ───────────────────────────────────────
        Text(
            text = "SYSTEM & PREFERENCES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        NavigationDrawerItem(
            label = { Text("Settings & Storage", fontWeight = FontWeight.SemiBold) },
            icon = { DrawerIconBadge(Icons.Filled.Settings, Color(0xFF3949AB), Color(0xFFE8EAF6)) },
            selected = currentRoute == Screen.Settings.route,
            onClick = {
                onCloseDrawer()
                navController.navigate(Screen.Settings.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        Spacer(Modifier.weight(1f, fill = false))
        Spacer(Modifier.height(20.dp))

        // ── Footer ────────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "MediBridge v1.0",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Secure On-Device Clinical Enclave",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * Colorful rounded container badge for drawer navigation icons.
 */
@Composable
private fun DrawerIconBadge(
    icon: ImageVector,
    iconColor: Color,
    containerColor: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

