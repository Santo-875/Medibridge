package com.medibridge.moduleB_safety.ui

import androidx.lifecycle.ViewModel
import com.medibridge.core.model.MedicationObject
import com.medibridge.moduleB_safety.logic.MockSafetyRepository
import com.medibridge.moduleB_safety.logic.SafetyDemoScenario
import com.medibridge.moduleB_safety.logic.SafetyEngine
import com.medibridge.moduleB_safety.logic.SafetyEvaluationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SafetyUiState(
    val scenarios: List<SafetyDemoScenario> = MockSafetyRepository.demoScenarios,
    val selectedScenarioIndex: Int = 0,
    val activeHistory: List<MedicationObject> = MockSafetyRepository.baselineHistory,
    val currentMedication: MedicationObject = MockSafetyRepository.demoScenarios[0].candidateMedication,
    val evaluationResult: SafetyEvaluationResult = SafetyEngine.evaluateSafety(
        MockSafetyRepository.demoScenarios[0].candidateMedication,
        MockSafetyRepository.baselineHistory
    )
)

class SafetyViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SafetyUiState())
    val uiState: StateFlow<SafetyUiState> = _uiState.asStateFlow()

    fun selectScenario(index: Int) {
        if (index !in _uiState.value.scenarios.indices) return
        val scenario = _uiState.value.scenarios[index]
        val result = SafetyEngine.evaluateSafety(scenario.candidateMedication, _uiState.value.activeHistory)

        _uiState.update { current ->
            current.copy(
                selectedScenarioIndex = index,
                currentMedication = scenario.candidateMedication,
                evaluationResult = result
            )
        }
    }

    fun runEvaluationFor(medication: MedicationObject) {
        val result = SafetyEngine.evaluateSafety(medication, _uiState.value.activeHistory)
        _uiState.update { current ->
            current.copy(
                currentMedication = medication,
                evaluationResult = result
            )
        }
    }
}
