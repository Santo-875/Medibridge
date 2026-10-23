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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // SettingsViewModel survives configuration changes (rotation, etc.)
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.medibridge.core.network.BackendClient.init(this)

        com.medibridge.core.sync.SyncWorker.enqueuePeriodicWork(this)
        lifecycleScope.launch {
            val tokenMgr = com.medibridge.core.auth.AuthTokenManager.getInstance(this@MainActivity)
            if (tokenMgr.hasToken()) {
                val session = com.medibridge.core.session.UserSessionManager.getInstance(this@MainActivity)
                session.ensureInitialized()
                val uid = session.getOrCreateUserId()
                if (uid.isNotBlank()) {
                    try {
                        com.medibridge.core.network.BackendClient.getService().registerDevice(
                            com.medibridge.core.network.DeviceRegisterRequest(
                                fcmToken = "device_${uid.take(8)}",
                                platform = "android"
                            )
                        )
                    } catch (_: Exception) {}
                    com.medibridge.core.sync.SyncRepository.getInstance(this@MainActivity).syncNow()
                }
            }
        }


        // Enqueue periodic missed-dose worker via WorkManager
        com.medibridge.moduleC_schedule.work.MissedDoseWorker.enqueuePeriodicWork(this)

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
