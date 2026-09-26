package com.medibridge.moduleB_safety.logic

import com.medibridge.core.model.MedicationConflict
import com.medibridge.core.model.MedicationObject

/**
 * High-level verdict describing the outcome of Module B safety analysis.
 */
enum class SafetyVerdict {
    SAFE,
    DUPLICATE,
    OVERLAP,
    INTERACTION,
    UNVERIFIED
}

/**
 * Result package produced by [SafetyEngine.evaluateSafety].
 */
data class SafetyEvaluationResult(
    /** The updated MedicationObject with crossVerified, conflicts, and reviewRecommended populated. */
    val updatedMedication: MedicationObject,
    /** Primary verdict for display badges and headers. */
    val verdict: SafetyVerdict,
    /** Clean patient/clinician friendly headline. */
    val headline: String,
    /** Plain-language summary of safety evaluation. */
    val summary: String,
    /** List of all detected conflicts. */
    val conflicts: List<MedicationConflict>,
    /** True if recognized in the local knowledge base. */
    val crossVerified: Boolean,
    /** True if clinical/pharmacist review is recommended. */
    val reviewRecommended: Boolean
)

/**
 * Deterministic Safety Engine for MediBridge Module B.
 *
 * Evaluates:
 * 1. Cross-verification against configured recognized medication knowledge base
 * 2. Duplicate detection against patient medication history
 * 3. Therapeutic class / active ingredient overlap
 * 4. Configured order-independent drug-drug interactions
 */
object SafetyEngine {

    const val CONFLICT_TYPE_DUPLICATE = "duplicate"
    const val CONFLICT_TYPE_OVERLAP = "overlap"
    const val CONFLICT_TYPE_INTERACTION = "interaction"
    const val CONFLICT_TYPE_VERIFICATION = "verification_warning"

    /**
     * Evaluates safety for [newMedication] against [history].
     *
     * Returns a [SafetyEvaluationResult] and an updated copy of [MedicationObject]
     * with Module B fields populated according to the shared schema contract.
     */
    fun evaluateSafety(
        newMedication: MedicationObject,
        history: List<MedicationObject>
    ): SafetyEvaluationResult {
        val conflicts = mutableListOf<MedicationConflict>()

        // 1. Cross-verification against local knowledge base
        val isRecognized = SafetyRules.isRecognized(newMedication.name)
        val crossVerified = isRecognized

        if (!isRecognized) {
            conflicts.add(
                MedicationConflict(
                    withMedId = "",
                    type = CONFLICT_TYPE_VERIFICATION,
                    detail = "Medication '${newMedication.name}' could not be verified against the configured safety knowledge base."
                )
            )
        }

        val normNew = MedicationNormalizer.normalize(newMedication.name)
        val baseNew = MedicationNormalizer.extractBaseName(newMedication.name)
        val newTherapeuticClass = SafetyRules.getTherapeuticClass(newMedication.name)

        // 2. Compare against medication history
        for (existing in history) {
            // Skip comparing with identical record ID if already in history
            if (existing.id == newMedication.id) continue

            val normExisting = MedicationNormalizer.normalize(existing.name)
            val baseExisting = MedicationNormalizer.extractBaseName(existing.name)
            var hasDuplicateForThisMed = false

            // A. Duplicate detection
            if (normNew == normExisting || (baseNew.isNotEmpty() && baseNew == baseExisting)) {
                conflicts.add(
                    MedicationConflict(
                        withMedId = existing.id,
                        type = CONFLICT_TYPE_DUPLICATE,
                        detail = "Duplicate medication detected. Patient is already prescribed ${existing.name} (${existing.strength})."
                    )
                )
                hasDuplicateForThisMed = true
            }

            // B. Therapeutic overlap detection (only if not already an exact duplicate)
            if (!hasDuplicateForThisMed && newTherapeuticClass != null) {
                val existingClass = SafetyRules.getTherapeuticClass(existing.name)
                if (existingClass != null && existingClass.equals(newTherapeuticClass, ignoreCase = true)) {
                    conflicts.add(
                        MedicationConflict(
                            withMedId = existing.id,
                            type = CONFLICT_TYPE_OVERLAP,
                            detail = "Potential therapeutic overlap detected: Both ${newMedication.name} and ${existing.name} belong to '$newTherapeuticClass'."
                        )
                    )
                }
            }

            // C. Interaction detection (order-independent rule lookup)
            val interaction = SafetyRules.findInteraction(newMedication.name, existing.name)
            if (interaction != null) {
                conflicts.add(
                    MedicationConflict(
                        withMedId = existing.id,
                        type = CONFLICT_TYPE_INTERACTION,
                        detail = "${interaction.detail} (Co-administered with ${existing.name})"
                    )
                )
            }
        }

        // 3. Determine review recommendation
        val reviewRecommended = conflicts.isNotEmpty() || !crossVerified

        // 4. Derive primary verdict and summaries
        val verdict: SafetyVerdict
        val headline: String
        val summary: String

        when {
            !crossVerified -> {
                verdict = SafetyVerdict.UNVERIFIED
                headline = "Verification Required"
                summary = "Medication could not be verified against the configured local knowledge base. Clinical review is required."
            }
            conflicts.any { it.type == CONFLICT_TYPE_DUPLICATE } -> {
                verdict = SafetyVerdict.DUPLICATE
                headline = "Duplicate Medication Detected"
                summary = "This medication or an equivalent active ingredient is already active in the patient's schedule."
            }
            conflicts.any { it.type == CONFLICT_TYPE_INTERACTION } -> {
                verdict = SafetyVerdict.INTERACTION
                headline = "Potential Interaction Detected"
                summary = "A known medication interaction was identified with one or more existing prescriptions."
            }
            conflicts.any { it.type == CONFLICT_TYPE_OVERLAP } -> {
                verdict = SafetyVerdict.OVERLAP
                headline = "Therapeutic Overlap Detected"
                summary = "This medication shares a pharmacological class or purpose with an existing prescription."
            }
            else -> {
                verdict = SafetyVerdict.SAFE
                headline = "No Known Conflict Detected"
                summary = "Cross-verification complete against configured rules and active medication history."
            }
        }

        // 5. Update MedicationObject safety fields
        val updatedMedication = newMedication.copy(
            crossVerified = crossVerified,
            conflicts = conflicts,
            reviewRecommended = reviewRecommended
        )

        return SafetyEvaluationResult(
            updatedMedication = updatedMedication,
            verdict = verdict,
            headline = headline,
            summary = summary,
            conflicts = conflicts,
            crossVerified = crossVerified,
            reviewRecommended = reviewRecommended
        )
    }

    /**
     * Builds structured input for the AI safety analyzer from evaluation results.
     */
    fun buildAiComparisonInput(
        newMedication: MedicationObject,
        history: List<MedicationObject>,
        evaluation: SafetyEvaluationResult
    ): AiSafetyComparisonInput {
        val medA = MedicineInfo(newMedication.name, newMedication.strength, newMedication.dose)
        val findings = mutableListOf<SafetyFinding>()

        if (!evaluation.crossVerified) {
            findings.add(
                SafetyFinding(
                    medicineA = medA,
                    medicineB = null,
                    findingType = FindingType.UNVERIFIED,
                    severity = "MODERATE",
                    detectedBy = "SafetyEngine",
                    reason = "Unrecognized medication name in configured local rules",
                    crossVerified = false,
                    reviewRecommended = true
                )
            )
        }

        for (conflict in evaluation.conflicts) {
            val conflictingMed = history.find { it.id == conflict.withMedId }
            val medB = conflictingMed?.let { MedicineInfo(it.name, it.strength, it.dose) }
            val (findingType, severity) = when (conflict.type) {
                CONFLICT_TYPE_DUPLICATE -> FindingType.DUPLICATE to "HIGH"
                CONFLICT_TYPE_OVERLAP -> FindingType.OVERLAP to "MODERATE"
                CONFLICT_TYPE_INTERACTION -> FindingType.INTERACTION to "HIGH"
                else -> FindingType.UNVERIFIED to "MODERATE"
            }
            if (findingType != FindingType.UNVERIFIED || evaluation.crossVerified) {
                findings.add(
                    SafetyFinding(
                        medicineA = medA,
                        medicineB = medB,
                        findingType = findingType,
                        severity = severity,
                        detectedBy = "SafetyEngine",
                        reason = conflict.detail,
                        crossVerified = evaluation.crossVerified,
                        reviewRecommended = evaluation.reviewRecommended
                    )
                )
            }
        }

        if (findings.isEmpty()) {
            findings.add(
                SafetyFinding(
                    medicineA = medA,
                    medicineB = null,
                    findingType = FindingType.SAFE,
                    severity = "LOW",
                    detectedBy = "SafetyEngine",
                    reason = "No conflicts or overlaps detected in configured rules",
                    crossVerified = true,
                    reviewRecommended = false
                )
            )
        }

        return AiSafetyComparisonInput(
            candidateMedicine = medA,
            findings = findings,
            crossVerified = evaluation.crossVerified,
            reviewRecommended = evaluation.reviewRecommended
        )
    }
}
