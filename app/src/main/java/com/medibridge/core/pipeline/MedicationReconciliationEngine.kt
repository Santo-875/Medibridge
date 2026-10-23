package com.medibridge.core.pipeline

import com.medibridge.core.model.MedicationObject
import com.medibridge.moduleB_safety.logic.MedicationNormalizer
import com.medibridge.moduleB_safety.logic.SafetyRules

enum class ReconciliationStatus {
    NEW,
    DOSE_CHANGED,
    DUPLICATE,
    CLASS_OVERLAP,
    UNCHANGED
}

data class ReconciledMedication(
    val candidate: MedicationObject,
    val status: ReconciliationStatus,
    val existingMatch: MedicationObject? = null,
    val changeSummary: String = "",
    val requiresVerification: Boolean = false
)

object MedicationReconciliationEngine {

    fun reconcile(
        candidates: List<MedicationObject>,
        activeRegimen: List<MedicationObject>
    ): List<ReconciledMedication> {
        return candidates.map { candidate ->
            val normCandidate = MedicationNormalizer.normalize(candidate.name)
            val baseCandidate = MedicationNormalizer.extractBaseName(candidate.name)
            val candidateClass = SafetyRules.getTherapeuticClass(candidate.name)

            val exactMatch = activeRegimen.firstOrNull { existing ->
                val normExisting = MedicationNormalizer.normalize(existing.name)
                normExisting == normCandidate || (baseCandidate.isNotEmpty() && baseCandidate == MedicationNormalizer.extractBaseName(existing.name))
            }

            if (exactMatch != null) {
                val strengthChanged = candidate.strength.isNotBlank() && exactMatch.strength.isNotBlank() &&
                        !candidate.strength.equals(exactMatch.strength, ignoreCase = true)
                val doseChanged = candidate.dose.isNotBlank() && exactMatch.dose.isNotBlank() &&
                        !candidate.dose.equals(exactMatch.dose, ignoreCase = true)
                val freqChanged = candidate.frequency.isNotBlank() && exactMatch.frequency.isNotBlank() &&
                        !candidate.frequency.equals(exactMatch.frequency, ignoreCase = true)

                if (strengthChanged || doseChanged || freqChanged) {
                    val changes = mutableListOf<String>()
                    if (strengthChanged) changes.add("Strength: ${exactMatch.strength} -> ${candidate.strength}")
                    if (doseChanged) changes.add("Dose: ${exactMatch.dose} -> ${candidate.dose}")
                    if (freqChanged) changes.add("Freq: ${exactMatch.frequency} -> ${candidate.frequency}")
                    ReconciledMedication(
                        candidate = candidate,
                        status = ReconciliationStatus.DOSE_CHANGED,
                        existingMatch = exactMatch,
                        changeSummary = changes.joinToString("; "),
                        requiresVerification = true
                    )
                } else {
                    ReconciledMedication(
                        candidate = candidate,
                        status = ReconciliationStatus.DUPLICATE,
                        existingMatch = exactMatch,
                        changeSummary = "Active in regimen (${exactMatch.strength}, ${exactMatch.frequency})",
                        requiresVerification = false
                    )
                }
            } else {
                val classMatch = activeRegimen.firstOrNull { existing ->
                    !candidateClass.isNullOrEmpty() && candidateClass == SafetyRules.getTherapeuticClass(existing.name)
                }
                if (classMatch != null) {
                    ReconciledMedication(
                        candidate = candidate,
                        status = ReconciliationStatus.CLASS_OVERLAP,
                        existingMatch = classMatch,
                        changeSummary = "Same class as active ${classMatch.name} ($candidateClass)",
                        requiresVerification = true
                    )
                } else {
                    ReconciledMedication(
                        candidate = candidate,
                        status = ReconciliationStatus.NEW,
                        changeSummary = "New medicine added to profile",
                        requiresVerification = candidate.confidence < 0.80f || candidate.needsVerification
                    )
                }
            }
        }
    }
}
