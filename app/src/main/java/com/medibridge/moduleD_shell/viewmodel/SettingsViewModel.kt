package com.medibridge.moduleD_shell.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medibridge.BuildConfig
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.db.RecordingEntity
import com.medibridge.core.demo.DemoDataSeeder
import com.medibridge.core.network.BackendClient
import com.medibridge.moduleC_schedule.tts.ReminderLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SettingsViewModel — manages app-wide UI state, backend URL configuration,
 * TTS speech language, recordings, and pitch demo data loading.
 */
class SettingsViewModel : ViewModel() {

    // Dark mode state
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Reminder Voice TTS Language (English / Tamil)
    private val _ttsLanguage = MutableStateFlow(ReminderLanguage.ENGLISH)
    val ttsLanguage: StateFlow<ReminderLanguage> = _ttsLanguage.asStateFlow()

    // Backend Base URL (defaults to BackendClient.getBaseUrl())
    private val _backendBaseUrl = MutableStateFlow(com.medibridge.core.network.BackendClient.getBaseUrl())
    val backendBaseUrl: StateFlow<String> = _backendBaseUrl.asStateFlow()

    // Caretaker emergency escalation phone number
    private val _caretakerPhone = MutableStateFlow("+1 555-0199")
    val caretakerPhone: StateFlow<String> = _caretakerPhone.asStateFlow()

    // Snooze / Reminder alert repeat interval in minutes
    private val _snoozeIntervalMinutes = MutableStateFlow(15)
    val snoozeIntervalMinutes: StateFlow<Int> = _snoozeIntervalMinutes.asStateFlow()

    // Active demo scenario: 1=Diabetes, 2=Hypertension, 3=Caretaker, 4=No Scenario
    private val _activeScenario = MutableStateFlow(1)
    val activeScenario: StateFlow<Int> = _activeScenario.asStateFlow()

    private val _demoStatus = MutableStateFlow<String?>(null)
    val demoStatus: StateFlow<String?> = _demoStatus.asStateFlow()

    // Gemini API Key management & AI status
    private val _geminiApiKey = MutableStateFlow(com.medibridge.moduleC_schedule.ai.GeminiConfigProvider.getApiKey() ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _aiStatus = MutableStateFlow(com.medibridge.moduleC_schedule.ai.GeminiConfigProvider.getStatusLabel())
    val aiStatus: StateFlow<String> = _aiStatus.asStateFlow()

    fun initGeminiKey(context: Context) {
        val prefs = context.getSharedPreferences("medibridge_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key", null)
        if (!savedKey.isNullOrBlank()) {
            com.medibridge.moduleC_schedule.ai.GeminiConfigProvider.setApiKey(savedKey)
            _geminiApiKey.value = savedKey
        } else {
            _geminiApiKey.value = com.medibridge.moduleC_schedule.ai.GeminiConfigProvider.getApiKey() ?: ""
        }
        _aiStatus.value = com.medibridge.moduleC_schedule.ai.GeminiConfigProvider.getStatusLabel()
    }

    fun updateGeminiApiKey(key: String, context: Context) {
        val trimmed = key.trim()
        com.medibridge.moduleC_schedule.ai.GeminiConfigProvider.setApiKey(trimmed)
        _geminiApiKey.value = trimmed
        _aiStatus.value = com.medibridge.moduleC_schedule.ai.GeminiConfigProvider.getStatusLabel()
        val prefs = context.getSharedPreferences("medibridge_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", trimmed).apply()
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
    }

    fun setTtsLanguage(language: ReminderLanguage) {
        _ttsLanguage.value = language
    }

    fun updateCaretakerPhone(phone: String) {
        _caretakerPhone.value = phone.trim()
    }

    fun setSnoozeIntervalMinutes(minutes: Int) {
        _snoozeIntervalMinutes.value = minutes
    }

    fun updateBackendBaseUrl(newUrl: String, context: Context? = null) {
        val trimmed = newUrl.trim()
        if (trimmed.isNotEmpty()) {
            _backendBaseUrl.value = trimmed
            BackendClient.updateBaseUrl(trimmed, context)
        }
    }

    fun observeRecordings(context: Context): Flow<List<RecordingEntity>> {
        return AppDatabase.getInstance(context).recordingDao().getAllRecordings()
    }

    fun selectScenario(context: Context, index: Int) {
        _activeScenario.value = index
        viewModelScope.launch {
            when (index) {
                1 -> {
                    _demoStatus.value = "Loading Scenario 1: Mr Tan Ah Kow (Clinical Dossier)..."
                    DemoDataSeeder.loadScenario(context, 1)
                    _demoStatus.value = "Scenario 1: Mr Tan Ah Kow loaded (Dementia & Stroke Regimen)!"
                }
                2 -> {
                    _demoStatus.value = "Loading Scenario 2: Hypertension (Dual Therapy Conflict)..."
                    DemoDataSeeder.loadScenario(context, 2)
                    _demoStatus.value = "Scenario 2: Hypertension loaded with interaction alert!"
                }
                3 -> {
                    _demoStatus.value = "Loading Scenario 3: Elderly Caretaker (Calcium Missed Dose)..."
                    DemoDataSeeder.loadScenario(context, 3)
                    _demoStatus.value = "Scenario 3: Caretaker escalation armed!"
                }
                4 -> {
                    _demoStatus.value = "Clearing DB for 'No Scenario' mode..."
                    DemoDataSeeder.loadScenario(context, 4)
                    _demoStatus.value = "No Scenario: Database is empty. Ready for live scanning & voice!"
                }
            }
        }
    }

    fun loadAllDemoScenarios(context: Context) {
        viewModelScope.launch {
            _demoStatus.value = "Loading all 3 demo scenarios..."
            DemoDataSeeder.seedAll(context)
            _demoStatus.value = "Loaded: Diabetes, Hypertension, and Elderly Caretaker!"
        }
    }
}
