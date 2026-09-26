package com.medibridge.moduleB_safety

import com.medibridge.core.model.MedicationObject
import com.medibridge.moduleB_safety.logic.SafetyEngine
import com.medibridge.moduleB_safety.logic.SafetyEvaluationResult

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * MODULE B — Safety & Cross-Verification
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Provides deterministic cross-verification, duplicate therapy detection,
 * therapeutic overlap checking, and order-independent drug-drug interaction detection.
 *
 * Module B owns:
 * - crossVerified
 * - conflicts
 * - reviewRecommended
 */
object ModuleB {

    /**
     * Evaluates safety for [newMedication] against the patient's [history].
     * Returns a comprehensive [SafetyEvaluationResult].
     */
    fun evaluateSafety(
        newMedication: MedicationObject,
        history: List<MedicationObject>
    ): SafetyEvaluationResult {
        return SafetyEngine.evaluateSafety(newMedication, history)
    }

    /**
     * Cross-verifies [newMedication] against [history] and returns an updated copy
     * with crossVerified, conflicts, and reviewRecommended populated.
     */
    fun verifyAndApplySafetyFields(
        newMedication: MedicationObject,
        history: List<MedicationObject>
    ): MedicationObject {
        return SafetyEngine.evaluateSafety(newMedication, history).updatedMedication
    }

    /**
     * Evaluates safety deterministically and generates polite AI natural language
     * contextualization and safety flags.
     */
    suspend fun evaluateWithAi(
        newMedication: MedicationObject,
        history: List<MedicationObject>,
        aiAnalyzer: com.medibridge.moduleB_safety.logic.AiSafetyAnalyzer = com.medibridge.moduleB_safety.logic.GeminiSafetyAnalyzer()
    ): Pair<SafetyEvaluationResult, com.medibridge.moduleB_safety.logic.AiSafetyResponse> {
        val evaluation = SafetyEngine.evaluateSafety(newMedication, history)
        val aiInput = SafetyEngine.buildAiComparisonInput(newMedication, history, evaluation)
        val aiResponse = aiAnalyzer.analyzeSafety(aiInput)
        return Pair(evaluation, aiResponse)
    }
}
