package com.medibridge.moduleC_schedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.model.MedicationObject
import com.medibridge.moduleC_schedule.ai.PatientSummaryResult
import com.medibridge.moduleC_schedule.ai.PatientSummaryService
import com.medibridge.moduleC_schedule.ai.PrivacySummaryService
import com.medibridge.moduleC_schedule.ai.SideEffectInfo
import com.medibridge.moduleC_schedule.ai.SideEffectService
import com.medibridge.moduleC_schedule.reminder.ReminderManager
import com.medibridge.moduleC_schedule.repository.ScheduleRepository
import com.medibridge.moduleD_shell.ui.ReminderItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ScheduleViewModel — Bridge between Module C business logic and Module D UI.
 *
 * Module D can instantiate this ViewModel or call its functions directly:
 *   - [reminders] StateFlow exposes dynamic list of [ReminderItem]
 *   - [markTaken] marks an occurrence as taken
 *   - [snooze] reschedules an occurrence
 *   - [generatePatientSummary] calls Gemini for patient summary and side effects
 */
class ScheduleViewModel(
    application: Application,
    private val repository: ScheduleRepository,
    private val patientSummaryService: PatientSummaryService,
    private val sideEffectService: SideEffectService,
    private val privacySummaryService: PrivacySummaryService
) : AndroidViewModel(application) {

    // Secondary constructor using Application context for default Room database instantiation
    constructor(application: Application) : this(
        application = application,
        repository = ScheduleRepository(
            medicationDao = AppDatabase.getInstance(application).medicationDao(),
            reminderManager = ReminderManager(application)
        ),
        patientSummaryService = PatientSummaryService(
            patientSummaryDao = AppDatabase.getInstance(application).patientSummaryDao()
        ),
        sideEffectService = SideEffectService(),
        privacySummaryService = PrivacySummaryService()
    )

    val reminders: StateFlow<List<ReminderItem>> = repository.observeTodayReminders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _summaryState = MutableStateFlow<PatientSummaryResult?>(null)
    val summaryState: StateFlow<PatientSummaryResult?> = _summaryState.asStateFlow()

    private val _sideEffectsState = MutableStateFlow<SideEffectInfo?>(null)
    val sideEffectsState: StateFlow<SideEffectInfo?> = _sideEffectsState.asStateFlow()

    private val _privacySummaryState = MutableStateFlow<Map<String, String>>(emptyMap())
    val privacySummaryState: StateFlow<Map<String, String>> = _privacySummaryState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Marks a reminder item as taken by its occurrence ID.
     * ID format: "${medicationId}_${slotTime}_${date}"
     */
    fun markTaken(reminderId: String) {
        viewModelScope.launch {
            val parts = reminderId.split("_")
            if (parts.size >= 3) {
                val medicationId = parts[0]
                val slotTime = parts[1]
                val date = parts.subList(2, parts.size).joinToString("_")
                repository.markTaken(medicationId, slotTime, date)
            }
        }
    }

    /**
     * Snoozes a reminder item by its occurrence ID.
     */
    fun snooze(reminderId: String, delayMinutes: Int = 15) {
        viewModelScope.launch {
            val parts = reminderId.split("_")
            if (parts.size >= 3) {
                val medicationId = parts[0]
                val slotTime = parts[1]
                val date = parts.subList(2, parts.size).joinToString("_")
                repository.snooze(
                    medicationId = medicationId,
                    slotTime = slotTime,
                    delayMinutes = delayMinutes,
                    date = date
                )
            }
        }
    }

    /**
     * Generates and stores a patient summary + side effects using Gemini AI.
     */
    fun generatePatientSummary(medication: MedicationObject) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = patientSummaryService.generateAndSavePatientSummary(medication)
                _summaryState.value = result
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Fetches structured side effects for a medication using Gemini AI.
     */
    fun fetchSideEffects(medicineName: String, strength: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val info = sideEffectService.fetchSideEffects(medicineName, strength)
                _sideEffectsState.value = info
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Generates role-based privacy summary content for doctor, caretaker, and pharmacy.
     */
    fun generatePrivacySummary(medication: MedicationObject) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val summaries = privacySummaryService.generatePrivacySummary(medication)
                _privacySummaryState.value = summaries
            } finally {
                _isLoading.value = false
            }
        }
    }
}
