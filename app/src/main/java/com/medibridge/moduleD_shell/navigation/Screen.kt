package com.medibridge.moduleD_shell.navigation

/**
 * Sealed class defining all navigation routes in the MediBridge app.
 */
sealed class Screen(val route: String) {
    object Intro             : Screen("intro")
    object Login             : Screen("login")
    object SignUp            : Screen("signup")
    object Onboarding        : Screen("onboarding")
    object Home              : Screen("home")
    object Medicines         : Screen("medicines")
    object Scanner           : Screen("scanner")
    object Recording         : Screen("recording")
    object Chatbot           : Screen("chatbot")
    object Reminders         : Screen("reminders")
    object Profile           : Screen("profile")
    val Settings: Screen get() = Profile
    object SafetyDashboard   : Screen("safety_dashboard")
    object AddEditMedication : Screen("add_edit_medication")
    object ConsultationRecordings : Screen("consultation_recordings")
    object PrescriptionArchive : Screen("prescription_archive")
}

sealed class BottomNavItem(
    val screen: Screen,
    val label: String
) {
    object Home      : BottomNavItem(Screen.Home,      "Home")
    object Medicines : BottomNavItem(Screen.Medicines, "Medicines")
    object Chatbot   : BottomNavItem(Screen.Chatbot,   "Medi AI")
    object Profile   : BottomNavItem(Screen.Profile,   "Profile")

    companion object {
        val leftItems = listOf(Home, Medicines)
        val rightItems = listOf(Chatbot, Profile)
        val all = listOf(Home, Medicines, Chatbot, Profile)
    }
}
