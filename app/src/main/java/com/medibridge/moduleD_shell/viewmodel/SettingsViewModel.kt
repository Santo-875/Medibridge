package com.medibridge.moduleD_shell.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SettingsViewModel — manages app-wide UI state for the Settings screen.
 *
 * Currently handles:
 *   - Dark mode toggle (in-memory; add DataStore for persistence post-hackathon)
 *
 * HOW TO PERSIST (post-hackathon):
 *   Replace _isDarkMode StateFlow with a DataStore<Preferences> backed flow.
 *   See: https://developer.android.com/topic/libraries/architecture/datastore
 */
class SettingsViewModel : ViewModel() {

    // In-memory dark mode state — survives configuration changes, not process death
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    /** Toggle between light and dark theme. Called from SettingsScreen switch. */
    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    /** Explicitly set the theme (e.g., from a "system default" option). */
    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
    }
}
