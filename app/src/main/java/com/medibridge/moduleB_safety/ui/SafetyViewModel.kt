package com.medibridge.moduleB_safety.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.fromEntity
import com.medibridge.moduleB_safety.data.SafetyCheckEntity
import com.medibridge.moduleB_safety.data.SafetyRepository
import com.medibridge.moduleB_safety.logic.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SafetyUiState(
    val scenarios: List<SafetyDemoScenario> = emptyList(),
    val selectedScenarioIndex: Int = 0,
    val activeHistory: List<MedicationObject> = emptyList(),
    val currentMedication: MedicationObject? = null,
    val evaluationResult: SafetyEvaluationResult? = null,
    val aiResponse: AiSafetyResponse? = null,
    val isAiLoading: Boolean = false,
    val savedSafetyHistory: List<SafetyCheckEntity> = emptyList()
)

class SafetyViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val db = com.medibridge.core.db.AppDatabase.getInstance(application)
    private val medicationDao = db.medicationDao()
    private val safetyRepository: SafetyRepository = SafetyRepository.getInstance(application)
    private val aiAnalyzer: AiSafetyAnalyzer = GeminiSafetyAnalyzer()

    private val _uiState = MutableStateFlow(SafetyUiState())
    val uiState: StateFlow<SafetyUiState> = _uiState.asStateFlow()

    init {
        // 1. Observe persistent safety audit history from local Room database
        viewModelScope.launch {
            safetyRepository.getSafetyHistory().collect { historyList ->
                _uiState.update { it.copy(savedSafetyHistory = historyList) }
            }
        }

        // 2. Observe real active medications from Room database
        viewModelScope.launch {
            medicationDao.getAllMedications().collect { entities ->
                val realHistory = entities.map { it.fromEntity() }
                val currentMed = _uiState.value.currentMedication?.let { existing ->
                    realHistory.find { it.id == existing.id }
                } ?: realHistory.firstOrNull()

                val evalResult = if (currentMed != null) {
                    val otherMeds = realHistory.filter { it.id != currentMed.id }
                    SafetyEngine.evaluateSafety(currentMed, otherMeds)
                } else null

                _uiState.update { current ->
                    current.copy(
                        activeHistory = realHistory,
                        currentMedication = currentMed,
                        evaluationResult = evalResult
                    )
                }
                if (currentMed != null && evalResult != null) {
                    evaluateAndPersistCurrent()
                }
            }
        }
    }

    fun selectMedication(medication: MedicationObject) {
        val otherMeds = _uiState.value.activeHistory.filter { it.id != medication.id }
        val result = SafetyEngine.evaluateSafety(medication, otherMeds)
        _uiState.update { current ->
            current.copy(
                currentMedication = medication,
                evaluationResult = result,
                isAiLoading = true
            )
        }
        evaluateAndPersistCurrent()
    }

    fun selectScenario(index: Int) {
        // No-op for mock scenarios, real medications use selectMedication
    }

    fun runEvaluationFor(medication: MedicationObject) {
        val otherMeds = _uiState.value.activeHistory.filter { it.id != medication.id }
        val result = SafetyEngine.evaluateSafety(medication, otherMeds)
        _uiState.update { current ->
            current.copy(
                currentMedication = medication,
                evaluationResult = result,
                isAiLoading = true
            )
        }
        evaluateAndPersistCurrent()
    }

    private fun evaluateAndPersistCurrent() {
        val currentMed = _uiState.value.currentMedication ?: return
        val history = _uiState.value.activeHistory.filter { it.id != currentMed.id }
        val evaluation = _uiState.value.evaluationResult ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true) }
            val aiInput = SafetyEngine.buildAiComparisonInput(currentMed, history, evaluation)
            val aiResponse = aiAnalyzer.analyzeSafety(aiInput)
            try {
                safetyRepository.persistSafetyEvaluation(
                    evaluation = evaluation,
                    aiResponse = aiResponse,
                    candidateMed = currentMed,
                    history = history
                )
            } catch (e: Exception) {
                // Silently handle any DB storage exception
            }
            _uiState.update {
                it.copy(
                    aiResponse = aiResponse,
                    isAiLoading = false
                )
            }
        }
    }

    fun clearSafetyHistory() {
        viewModelScope.launch {
            safetyRepository.clearHistory()
        }
    }
}
