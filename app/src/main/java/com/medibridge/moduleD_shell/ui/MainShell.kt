package com.medibridge.moduleD_shell.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.medibridge.moduleD_shell.navigation.BottomNavItem
import com.medibridge.moduleD_shell.navigation.MediBridgeNavGraph
import com.medibridge.moduleD_shell.navigation.Screen
import com.medibridge.moduleD_shell.ui.components.ActionTile
import com.medibridge.moduleD_shell.ui.components.CenterAddButton
import com.medibridge.moduleD_shell.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    settingsViewModel: SettingsViewModel,
    initialRoute: String? = null
) {
    val navController = rememberNavController()
    var showAddSheet by remember { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(initialRoute) {
        if (!initialRoute.isNullOrBlank()) {
            navController.navigate(initialRoute)
        }
    }

    LaunchedEffect(Unit) {
        com.medibridge.core.auth.AuthEvents.unauthorized.collect {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val navBackStack by navController.currentBackStackEntryAsState()
    val currentDest = navBackStack?.destination

    val hideBottomNavRoutes = setOf(
        Screen.Intro.route,
        Screen.Login.route,
        Screen.SignUp.route,
        Screen.Onboarding.route,
        Screen.Scanner.route,
        Screen.Recording.route,
        Screen.AddEditMedication.route,
        Screen.SafetyDashboard.route,
        Screen.ConsultationRecordings.route,
        Screen.PrescriptionArchive.route
    )
    val showBottomNav = currentDest?.route !in hideBottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        // Left items: Home, Medicines
                        BottomNavItem.leftItems.forEach { item ->
                            val isSelected = currentDest?.hierarchy?.any {
                                it.route == item.screen.route
                            } == true

                            val icon = when (item) {
                                BottomNavItem.Home -> Icons.Default.Home
                                BottomNavItem.Medicines -> Icons.Default.Medication
                                else -> Icons.Default.Home
                            }

                            NavBarItemView(
                                item = item,
                                icon = icon,
                                isSelected = isSelected,
                                onClick = {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        // Center 56dp Teal Add Button
                        CenterAddButton(
                            isOpen = showAddSheet,
                            onClick = { showAddSheet = !showAddSheet }
                        )

                        // Right items: Medi AI, Profile
                        BottomNavItem.rightItems.forEach { item ->
                            val isSelected = currentDest?.hierarchy?.any {
                                it.route == item.screen.route
                            } == true

                            val icon = when (item) {
                                BottomNavItem.Chatbot -> Icons.Default.AutoAwesome
                                BottomNavItem.Profile -> Icons.Default.Person
                                else -> Icons.Default.Person
                            }

                            NavBarItemView(
                                item = item,
                                icon = icon,
                                isSelected = isSelected,
                                onClick = {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MediBridgeNavGraph(
                navController = navController,
                settingsViewModel = settingsViewModel
            )
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = addSheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add Medicine",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose your preferred method to import prescription details.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                ActionTile(
                    title = "Scan prescription",
                    subtitle = "Scan pill bottle label, paper prescription, or bill",
                    icon = Icons.Default.DocumentScanner,
                    onClick = {
                        showAddSheet = false
                        navController.navigate(Screen.Scanner.route)
                    }
                )

                ActionTile(
                    title = "Speak it",
                    subtitle = "Dictate doctor instructions in your spoken language",
                    icon = Icons.Default.Mic,
                    badgeText = "Voice AI",
                    onClick = {
                        showAddSheet = false
                        navController.navigate(Screen.Recording.route)
                    }
                )
            }
        }
    }
}

@Composable
private fun NavBarItemView(
    item: BottomNavItem,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "navScale"
    )

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val color = if (isSelected) activeColor else inactiveColor

    Column(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = item.label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = color,
            maxLines = 1
        )
    }
}
