package com.medibridge.moduleB_safety.logic

/**
 * Finding classification detected by the deterministic SafetyEngine.
 */
enum class FindingType {
    DUPLICATE,
    OVERLAP,
    INTERACTION,
    UNVERIFIED,
    SAFE
}

/**
 * Compact representation of a medication involved in a safety check.
 */
data class MedicineInfo(
    val name: String,
    val strength: String,
    val dose: String = ""
)

/**
 * Structured finding produced by deterministic SafetyEngine and passed to AI.
 */
data class SafetyFinding(
    val medicineA: MedicineInfo,
    val medicineB: MedicineInfo? = null,
    val findingType: FindingType,
    val severity: String, // "LOW", "MODERATE", "HIGH"
    val detectedBy: String = "SafetyEngine",
    val reason: String,
    val crossVerified: Boolean,
    val reviewRecommended: Boolean
)

/**
 * Structured input provided to the AI Safety Analyzer.
 */
data class AiSafetyComparisonInput(
    val candidateMedicine: MedicineInfo,
    val findings: List<SafetyFinding>,
    val crossVerified: Boolean,
    val reviewRecommended: Boolean
) {
    val hasMultipleFindings: Boolean get() = findings.size > 1
}

/**
 * Structured response produced by the AI Safety Analyzer.
 */
data class AiSafetyResponse(
    val flagged: Boolean,
    val severity: String,
    val title: String,
    val message: String,
    val recommendation: String,
    val isFallback: Boolean = false
)
