package com.medibridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.medibridge.core.theme.MediBridgeTheme
import com.medibridge.moduleD_shell.ui.MainShell
import com.medibridge.moduleD_shell.viewmodel.SettingsViewModel

/**
 * MainActivity — the single Activity entry point for MediBridge.
 *
 * Responsibilities:
 *   1. Enable edge-to-edge display.
 *   2. Observe [SettingsViewModel.isDarkMode] to control [MediBridgeTheme].
 *   3. Host the [MainShell] composable (bottom nav + NavGraph).
 *
 * Each module composable is reached via Navigation Compose — this Activity
 * never needs to be modified when new modules are added.
 */
class MainActivity : ComponentActivity() {

    // SettingsViewModel survives configuration changes (rotation, etc.)
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.medibridge.core.network.BackendClient.init(this)

        val initialRoute = intent?.getStringExtra("EXTRA_NAV_ROUTE")

        setContent {
            val isDarkMode by settingsViewModel.isDarkMode.collectAsState()

            // MediBridgeTheme wraps the entire app — all composables inherit the
            // active color scheme. Switching dark mode re-composes the whole tree.
            MediBridgeTheme(darkTheme = isDarkMode) {
                MainShell(
                    settingsViewModel = settingsViewModel,
                    initialRoute = initialRoute
                )
            }
        }
    }
}
