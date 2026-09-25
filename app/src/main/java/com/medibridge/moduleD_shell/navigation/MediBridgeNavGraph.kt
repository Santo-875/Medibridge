package com.medibridge.moduleD_shell.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.medibridge.moduleB_safety.ui.SafetyScreen
import com.medibridge.moduleD_shell.ui.ChatbotScreen
import com.medibridge.moduleD_shell.ui.HomeScreen
import com.medibridge.moduleD_shell.ui.RemindersScreen
import com.medibridge.moduleD_shell.ui.ScannerScreen
import com.medibridge.moduleD_shell.ui.SettingsScreen
import com.medibridge.moduleD_shell.viewmodel.SettingsViewModel

/**
 * Central navigation graph for the MediBridge app.
 *
 * All 4 module screens are registered here.
 * Start destination is [Screen.Home].
 *
 * TO ADD A NEW MODULE SCREEN:
 *   1. Add a route to [Screen].
 *   2. Import the composable from the module package.
 *   3. Add a composable() block below.
 *
 * @param navController  The single NavHostController managing back-stack.
 * @param settingsViewModel  Passed down for theme toggling — no need to hoist higher.
 */
@Composable
fun MediBridgeNavGraph(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Home.route
    ) {

        // ── Module D: Home ────────────────────────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onScannerClick = { navController.navigate(Screen.Scanner.route) },
                onChatbotClick = { navController.navigate(Screen.Chatbot.route) },
                onReminderClick = { navController.navigate(Screen.Reminders.route) },
                onSafetyClick = { navController.navigate(Screen.SafetyDashboard.route) }
            )
        }

        // ── Module D: Reminders ───────────────────────────────────────────────
        composable(Screen.Reminders.route) {
            RemindersScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ── Module D: Settings ────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(viewModel = settingsViewModel)
        }

        // ── Module A: Scanner (stub) ──────────────────────────────────────────
        composable(Screen.Scanner.route) {
            ScannerScreen(
                onBack = { navController.popBackStack() }
                // TODO: Module A wires OCR camera + AI extraction here
            )
        }

        // ── Module D: Chatbot (Medi) ──────────────────────────────────────────
        composable(Screen.Chatbot.route) {
            ChatbotScreen(
                onBack = { navController.popBackStack() }
                // TODO: Module D wires AI chat API (Retrofit) here
            )
        }

        // ── Module B: Safety Dashboard ───────────────────────────────────────
        composable(Screen.SafetyDashboard.route) {
            SafetyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ── Future Module C screens — add composable() blocks here ────────────
        // composable(Screen.ScheduleCalendar.route) { ScheduleCalendarScreen(...) }
    }
}
