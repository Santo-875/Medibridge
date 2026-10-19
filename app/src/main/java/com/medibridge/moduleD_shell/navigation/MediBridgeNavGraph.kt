package com.medibridge.moduleD_shell.navigation

import androidx.compose.animation.*
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.medibridge.moduleB_safety.ui.SafetyScreen
import com.medibridge.moduleD_shell.ui.*
import com.medibridge.moduleD_shell.viewmodel.SettingsViewModel

@Composable
fun MediBridgeNavGraph(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    onOpenDrawer: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Intro.route,
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(220)) },
        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
        popExitTransition = { fadeOut(animationSpec = tween(220)) }
    ) {
        // Intro Screen
        composable(Screen.Intro.route) {
            IntroVideoScreen(
                onIntroComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Intro.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // Top-Level Tab: Home
        composable(Screen.Home.route) {
            HomeScreen(
                onScannerClick = { navController.navigate(Screen.Scanner.route) },
                onChatbotClick = { navController.navigate(Screen.Chatbot.route) },
                onReminderClick = { navController.navigate(Screen.Reminders.route) },
                onSafetyClick = { navController.navigate(Screen.SafetyDashboard.route) },
                onRecordClick = { navController.navigate(Screen.Recording.route) },
                onOpenDrawer = onOpenDrawer
            )
        }

        // Top-Level Tab: Medicines
        composable(Screen.Medicines.route) {
            MedicinesScreen(
                onMedicationClick = { medId ->
                    navController.navigate(Screen.AddEditMedication.route)
                },
                onRemindersClick = { navController.navigate(Screen.Reminders.route) },
                onAddClick = { navController.navigate(Screen.Scanner.route) }
            )
        }

        // Top-Level Tab: Medi AI
        composable(Screen.Chatbot.route) {
            ChatbotScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Top-Level Tab: Profile / Settings
        composable(Screen.Profile.route) {
            SettingsScreen(viewModel = settingsViewModel)
        }

        // Detail Slide Screen: Reminders
        composable(
            route = Screen.Reminders.route,
            enterTransition = { slideIntoContainer(SlideDirection.Start, tween(280)) },
            exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(280)) },
            popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(280)) },
            popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(280)) }
        ) {
            RemindersScreen(
                onBack = { navController.popBackStack() },
                onAddMedication = { navController.navigate(Screen.AddEditMedication.route) }
            )
        }

        // Detail Slide Screen: Scanner
        composable(
            route = Screen.Scanner.route,
            enterTransition = { slideIntoContainer(SlideDirection.Start, tween(280)) },
            exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(280)) },
            popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(280)) },
            popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(280)) }
        ) {
            ScannerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Detail Slide Screen: Recording
        composable(
            route = Screen.Recording.route,
            enterTransition = { slideIntoContainer(SlideDirection.Start, tween(280)) },
            exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(280)) },
            popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(280)) },
            popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(280)) }
        ) {
            RecordingScreen(
                onBack = { navController.popBackStack() },
                onNavigateToReminders = {
                    navController.navigate(Screen.Reminders.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        // Detail Slide Screen: Safety Dashboard
        composable(
            route = Screen.SafetyDashboard.route,
            enterTransition = { slideIntoContainer(SlideDirection.Start, tween(280)) },
            exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(280)) },
            popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(280)) },
            popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(280)) }
        ) {
            SafetyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Detail Slide Screen: Add / Edit Medication
        composable(
            route = Screen.AddEditMedication.route,
            enterTransition = { slideIntoContainer(SlideDirection.Start, tween(280)) },
            exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(280)) },
            popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(280)) },
            popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(280)) }
        ) {
            AddEditMedicationScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
