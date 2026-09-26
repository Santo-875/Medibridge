package com.medibridge.moduleB_safety.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medibridge.core.model.MedicationObject
import com.medibridge.moduleB_safety.data.SafetyCheckEntity
import com.medibridge.moduleB_safety.data.SafetyRepository
import com.medibridge.moduleB_safety.logic.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SafetyUiState(
    val scenarios: List<SafetyDemoScenario> = MockSafetyRepository.demoScenarios,
    val selectedScenarioIndex: Int = 0,
    val activeHistory: List<MedicationObject> = MockSafetyRepository.baselineHistory,
    val currentMedication: MedicationObject = MockSafetyRepository.demoScenarios[0].candidateMedication,
    val evaluationResult: SafetyEvaluationResult = SafetyEngine.evaluateSafety(
        MockSafetyRepository.demoScenarios[0].candidateMedication,
        MockSafetyRepository.baselineHistory
    ),
    val aiResponse: AiSafetyResponse? = null,
    val isAiLoading: Boolean = false,
    val savedSafetyHistory: List<SafetyCheckEntity> = emptyList()
)

class SafetyViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val safetyRepository: SafetyRepository = SafetyRepository.getInstance(application)
    private val aiAnalyzer: AiSafetyAnalyzer = GeminiSafetyAnalyzer()

    private val _uiState = MutableStateFlow(SafetyUiState())
    val uiState: StateFlow<SafetyUiState> = _uiState.asStateFlow()

    init {
        // 1. Observe persistent history from local Room database
        viewModelScope.launch {
            safetyRepository.getSafetyHistory().collect { historyList ->
                _uiState.update { it.copy(savedSafetyHistory = historyList) }
            }
        }

        // 2. Run initial scenario evaluation, generate AI explanation & persist
        evaluateAndPersistCurrent()
    }

    fun selectScenario(index: Int) {
        if (index !in _uiState.value.scenarios.indices) return
        val scenario = _uiState.value.scenarios[index]
        val result = SafetyEngine.evaluateSafety(scenario.candidateMedication, _uiState.value.activeHistory)

        _uiState.update { current ->
            current.copy(
                selectedScenarioIndex = index,
                currentMedication = scenario.candidateMedication,
                evaluationResult = result,
                isAiLoading = true
            )
        }

        evaluateAndPersistCurrent()
    }

    fun runEvaluationFor(medication: MedicationObject) {
        val result = SafetyEngine.evaluateSafety(medication, _uiState.value.activeHistory)
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
        val currentMed = _uiState.value.currentMedication
        val history = _uiState.value.activeHistory
        val evaluation = _uiState.value.evaluationResult

        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true) }

            // 1. Construct structured input for AI
            val aiInput = SafetyEngine.buildAiComparisonInput(currentMed, history, evaluation)

            // 2. Generate polite AI contextualization (uses Gemini or robust local fallback)
            val aiResponse = aiAnalyzer.analyzeSafety(aiInput)

            // 3. Persist record to Room database
            try {
                safetyRepository.persistSafetyEvaluation(
                    evaluation = evaluation,
                    aiResponse = aiResponse,
                    candidateMed = currentMed,
                    history = history
                )
            } catch (e: Exception) {
                // Silently handle any DB storage exception during demo
            }

            // 4. Update UI state
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
