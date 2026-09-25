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
    object Home        : Screen("home")
    object Reminders   : Screen("reminders")
    object Settings    : Screen("settings")
    object Chatbot     : Screen("chatbot")       // Opens from Home FAB
    object Scanner     : Screen("scanner")       // Opens from Home top-bar icon

    // ── Module A: Prescription (add more as Module A expands) ─────────────────
    // object PrescriptionDetail : Screen("prescription_detail/{medId}") {
    //     fun createRoute(medId: String) = "prescription_detail/$medId"
    // }

    // ── Module B: Safety ─────────────────────────────────────────────────────
    object SafetyDashboard : Screen("safety_dashboard")

    // ── Module C: Schedule (add schedule calendar etc.) ───────────────────────
    // object ScheduleCalendar : Screen("schedule_calendar")
}

/** Items shown in the bottom navigation bar (top-level destinations only). */
sealed class BottomNavItem(
    val screen: Screen,
    val label: String,
    // Icon resource names — using Material Icons Extended (referenced in composable)
    val iconName: String
) {
    object Home      : BottomNavItem(Screen.Home,      "Home",      "Home")
    object Reminders : BottomNavItem(Screen.Reminders, "Reminders", "Notifications")
    object Settings  : BottomNavItem(Screen.Settings,  "Settings",  "Settings")

    companion object {
        val all = listOf(Home, Reminders, Settings)
    }
}
