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

import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import com.medibridge.core.auth.AuthTokenManager
import com.medibridge.moduleD_shell.ui.auth.LoginScreen
import com.medibridge.moduleD_shell.ui.auth.SignUpScreen
import com.medibridge.moduleD_shell.ui.auth.OnboardingScreen

@Composable
fun MediBridgeNavGraph(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val startDest = remember {
        if (AuthTokenManager.getInstance(context).hasToken()) Screen.Home.route else Screen.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = startDest,
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(220)) },
        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
        popExitTransition = { fadeOut(animationSpec = tween(220)) }
    ) {
        // Auth Screens
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToSignUp = {
                    navController.navigate(Screen.SignUp.route)
                },
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.SignUp.route) {
            SignUpScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                    }
                },
                onSignUpSuccess = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                settingsViewModel = settingsViewModel,
                onOnboardingComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // Intro / Splash Screen — direct immediate launch without video
        composable(Screen.Intro.route) {
            val target = if (AuthTokenManager.getInstance(context).hasToken()) Screen.Home.route else Screen.Login.route
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.navigate(target) {
                    popUpTo(Screen.Intro.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        // Top-Level Tab: Home
        composable(Screen.Home.route) {
            HomeScreen(
                onScannerClick = { navController.navigate(Screen.Scanner.route) },
                onChatbotClick = { navController.navigate(Screen.Chatbot.route) },
                onReminderClick = { navController.navigate(Screen.Reminders.route) },
                onSafetyClick = { navController.navigate(Screen.SafetyDashboard.route) },
                onRecordClick = { navController.navigate(Screen.Recording.route) },
                onRecordingsListClick = { navController.navigate(Screen.ConsultationRecordings.route) },
                onMedicinesClick = { navController.navigate(Screen.Medicines.route) },
                onSettingsClick = { navController.navigate(Screen.Profile.route) }
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
                },
                onNavigateToRecordingsList = {
                    navController.navigate(Screen.ConsultationRecordings.route)
                }
            )
        }

        // Detail Slide Screen: Consultation Recordings
        composable(
            route = Screen.ConsultationRecordings.route,
            enterTransition = { slideIntoContainer(SlideDirection.Start, tween(280)) },
            exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(280)) },
            popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(280)) },
            popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(280)) }
        ) {
            ConsultationRecordingsScreen(
                onBack = { navController.popBackStack() },
                onNewRecording = { navController.navigate(Screen.Recording.route) }
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
