package com.medibridge.moduleB_safety.logic

/**
 * Clean abstraction for AI-assisted safety analysis and explanation.
 */
interface AiSafetyAnalyzer {
    /**
     * Contextualizes and explains structured findings from SafetyEngine into
     * patient-friendly natural language and polite safety flags.
     */
    suspend fun analyzeSafety(input: AiSafetyComparisonInput): AiSafetyResponse
}
