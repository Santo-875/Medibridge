package com.medibridge.moduleD_shell.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medibridge.BuildConfig
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.demo.DemoDataSeeder
import com.medibridge.core.network.BackendClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SettingsViewModel — manages app-wide UI state, backend URL configuration,
 * and pitch demo data loading.
 */
class SettingsViewModel : ViewModel() {

    // Dark mode state
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Backend Base URL (defaults to BuildConfig.BACKEND_BASE_URL)
    private val _backendBaseUrl = MutableStateFlow(BuildConfig.BACKEND_BASE_URL)
    val backendBaseUrl: StateFlow<String> = _backendBaseUrl.asStateFlow()

    private val _demoStatus = MutableStateFlow<String?>(null)
    val demoStatus: StateFlow<String?> = _demoStatus.asStateFlow()

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
    }

    fun updateBackendBaseUrl(newUrl: String) {
        val trimmed = newUrl.trim()
        if (trimmed.isNotEmpty()) {
            _backendBaseUrl.value = trimmed
            BackendClient.updateBaseUrl(trimmed)
        }
    }

    fun loadAllDemoScenarios(context: Context) {
        viewModelScope.launch {
            _demoStatus.value = "Loading all 3 demo scenarios..."
            DemoDataSeeder.seedAll(context)
            _demoStatus.value = "Loaded: Diabetes, Hypertension, and Elderly Caretaker!"
        }
    }

    fun loadScenario1(context: Context) {
        viewModelScope.launch {
            _demoStatus.value = "Loading Scenario 1: Diabetes (Metformin)..."
            val db = AppDatabase.getInstance(context)
            DemoDataSeeder.seedScenario1_Diabetes(db.medicationDao())
            _demoStatus.value = "Scenario 1 loaded: Metformin 500mg active!"
        }
    }

    fun loadScenario2(context: Context) {
        viewModelScope.launch {
            _demoStatus.value = "Loading Scenario 2: Hypertension (Lisinopril + Amlodipine)..."
            val db = AppDatabase.getInstance(context)
            DemoDataSeeder.seedScenario2_Hypertension(db.medicationDao(), db.safetyCheckDao())
            _demoStatus.value = "Scenario 2 loaded: Interaction flag in Safety Dashboard!"
        }
    }

    fun loadScenario3(context: Context) {
        viewModelScope.launch {
            _demoStatus.value = "Loading Scenario 3: Elderly Caretaker (Calcium Missed Dose)..."
            val db = AppDatabase.getInstance(context)
            DemoDataSeeder.seedScenario3_ElderlyCaretaker(db.medicationDao())
            _demoStatus.value = "Scenario 3 loaded: Caretaker Call Escalation armed!"
        }
    }

    fun clearAllData(context: Context) {
        viewModelScope.launch {
            val db = AppDatabase.getInstance(context)
            db.medicationDao().deleteAll()
            db.safetyCheckDao().deleteAll()
            _demoStatus.value = "Database cleared."
        }
    }
}
