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
        val crossVerified = isRecognized && !newMedication.needsVerification

        if (!isRecognized || newMedication.needsVerification) {
            conflicts.add(
                MedicationConflict(
                    withMedId = "",
                    type = CONFLICT_TYPE_VERIFICATION,
                    detail = if (!isRecognized) {
                        "Medication '${newMedication.name}' could not be verified against the configured safety knowledge base."
                    } else {
                        "Medication '${newMedication.name}' flagged for clinical verification."
                    },
                    finding = "Formula requires verification (${newMedication.name})",
                    explanation = if (!isRecognized) {
                        "Medication '${newMedication.name}' could not be matched with high certainty in the local safety knowledge base."
                    } else {
                        "Medication '${newMedication.name}' flagged for clinical verification."
                    },
                    severity = "CAUTION",
                    source = "MediBridge Prescription Safety Protocol",
                    nextStep = "Check prescription photo to verify medicine name and strength spelling."
                )
            )
        }

        val normNew = MedicationNormalizer.normalize(newMedication.name)
        val baseNew = MedicationNormalizer.extractBaseName(newMedication.name)
        val canonNew = MedicationNormalizer.canonicalGeneric(newMedication.name)
        val newTherapeuticClass = SafetyRules.getTherapeuticClass(newMedication.name)

        for (existing in history) {
            if (existing.id == newMedication.id) continue

            val normExisting = MedicationNormalizer.normalize(existing.name)
            val baseExisting = MedicationNormalizer.extractBaseName(existing.name)
            val canonExisting = MedicationNormalizer.canonicalGeneric(existing.name)
            var hasDuplicateForThisMed = false

            if (normNew == normExisting || (baseNew.isNotEmpty() && baseNew == baseExisting) || (canonNew.isNotEmpty() && canonNew == canonExisting)) {
                val detailText = "Duplicate medication detected. Patient is already prescribed ${existing.name} (${existing.strength})."
                conflicts.add(
                    MedicationConflict(
                        withMedId = existing.id,
                        type = CONFLICT_TYPE_DUPLICATE,
                        detail = detailText,
                        finding = "Duplicate therapy: ${newMedication.name} matches active ${existing.name} ($canonNew)",
                        explanation = "Patient is currently prescribed ${existing.name} (${existing.strength}). Co-prescribing another medication containing $canonNew leads to accidental double dosing.",
                        severity = "HIGH",
                        source = "WHO Patient Safety Curriculum - Dual Therapy Prevention",
                        nextStep = "Ask your doctor if ${newMedication.name} replaces ${existing.name} or if one should be discontinued."
                    )
                )
                hasDuplicateForThisMed = true
            }

            if (!hasDuplicateForThisMed && newTherapeuticClass != null) {
                val existingClass = SafetyRules.getTherapeuticClass(existing.name)
                if (existingClass != null && existingClass.equals(newTherapeuticClass, ignoreCase = true)) {
                    val detailText = "Potential therapeutic overlap detected: Both ${newMedication.name} and ${existing.name} belong to '$newTherapeuticClass'."
                    conflicts.add(
                        MedicationConflict(
                            withMedId = existing.id,
                            type = CONFLICT_TYPE_OVERLAP,
                            detail = detailText,
                            finding = "Therapeutic class overlap ($newTherapeuticClass)",
                            explanation = "Both ${newMedication.name} and ${existing.name} belong to '$newTherapeuticClass'. Co-administration may increase cumulative side effects without added therapeutic efficacy.",
                            severity = "MODERATE",
                            source = "Clinical Pharmacology Class Overlap Guidance",
                            nextStep = "Discuss with doctor to ensure simultaneous usage in this drug class was intentional."
                        )
                    )
                }
            }

            val interaction = SafetyRules.findInteraction(newMedication.name, existing.name)
            if (interaction != null) {
                conflicts.add(
                    MedicationConflict(
                        withMedId = existing.id,
                        type = CONFLICT_TYPE_INTERACTION,
                        detail = "${interaction.detail} (Co-administered with ${existing.name})",
                        finding = "Drug-drug interaction between ${newMedication.name} and ${existing.name}",
                        explanation = interaction.explanation,
                        severity = interaction.severity,
                        source = interaction.source,
                        nextStep = interaction.nextStep
                    )
                )
            }
        }

        val reviewRecommended = conflicts.isNotEmpty() || !crossVerified

        val verdict: SafetyVerdict
        val headline: String
        val summary: String

        val hasInteraction = conflicts.any { it.type == CONFLICT_TYPE_INTERACTION }
        val hasDuplicate = conflicts.any { it.type == CONFLICT_TYPE_DUPLICATE }
        val hasOverlap = conflicts.any { it.type == CONFLICT_TYPE_OVERLAP }

        when {
            hasInteraction -> {
                verdict = SafetyVerdict.INTERACTION
                headline = if (!crossVerified) "Interaction Detected & Verification Required" else "Potential Interaction Detected"
                summary = if (!crossVerified) {
                    "A known medication interaction was identified with active prescriptions, and medication requires clinical verification."
                } else {
                    "A known medication interaction was identified with one or more existing prescriptions."
                }
            }
            hasDuplicate -> {
                verdict = SafetyVerdict.DUPLICATE
                headline = if (!crossVerified) "Duplicate Detected & Verification Required" else "Duplicate Medication Detected"
                summary = "This medication or an equivalent active ingredient is already active in the patient's schedule."
            }
            hasOverlap -> {
                verdict = SafetyVerdict.OVERLAP
                headline = if (!crossVerified) "Overlap Detected & Verification Required" else "Therapeutic Overlap Detected"
                summary = "This medication shares a pharmacological class or purpose with an existing prescription."
            }
            !crossVerified -> {
                verdict = SafetyVerdict.UNVERIFIED
                headline = "Verification Required"
                summary = "Medication could not be verified against the configured local knowledge base. Clinical review is required."
            }
            else -> {
                verdict = SafetyVerdict.SAFE
                headline = "No issues detected by the checks available in this app"
                summary = "Checked against drug-drug interactions, duplicate therapies, food/meal timing, and active regimen history."
            }
        }

        val realConflicts = conflicts.filter { it.type != CONFLICT_TYPE_VERIFICATION }

        val updatedMedication = newMedication.copy(
            crossVerified = crossVerified,
            conflicts = realConflicts,
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
