package com.medibridge.moduleD_shell.navigation

/**
 * Sealed class defining all navigation routes in the MediBridge app.
 *
 * HOW TO ADD A MODULE SCREEN:
 *   1. Add a new object/class here in the correct Module block.
 *   2. Add a composable() entry in MediBridgeNavGraph.kt.
 *   3. (Optional) Add a bottom nav entry in BottomNavItem.kt if it's a top-level tab.
 *
 * ROUTE NAMING CONVENTION: lowercase_snake_case
 */
sealed class Screen(val route: String) {

    // ── Module D: Shell (always present) ─────────────────────────────────────
    object Intro       : Screen("intro")         // Launch motion graphics intro screen
    object Home        : Screen("home")
    object Scanner     : Screen("scanner")       // Dedicated Scan tab
    object Recording   : Screen("recording")     // Dedicated Record tab
    object Chatbot     : Screen("chatbot")       // Dedicated Medi AI tab
    object Reminders   : Screen("reminders")     // Accessible from Home or schedule details
    object Settings    : Screen("settings")      // Clean Settings tab

    // ── Module B: Safety ─────────────────────────────────────────────────────
    object SafetyDashboard : Screen("safety_dashboard")

    // ── Module C: Schedule ─────────────────────────────────────────────────────
    /** Add/Edit a medication. Optional param ?medId=<id> for edit mode. */
    object AddEditMedication : Screen("add_edit_medication")
}

/** Items shown in the bottom navigation bar (top-level destinations only). */
sealed class BottomNavItem(
    val screen: Screen,
    val label: String,
    val iconName: String
) {
    object Home      : BottomNavItem(Screen.Home,      "Home",      "Home")
    object Scanner   : BottomNavItem(Screen.Scanner,   "Scan",      "DocumentScanner")
    object Recording : BottomNavItem(Screen.Recording, "Record",    "Mic")
    object Chatbot   : BottomNavItem(Screen.Chatbot,   "Medi AI",   "AutoAwesome")
    object Settings  : BottomNavItem(Screen.Settings,  "Settings",  "Settings")

    companion object {
        val all = listOf(Home, Scanner, Recording, Chatbot, Settings)
    }
}
