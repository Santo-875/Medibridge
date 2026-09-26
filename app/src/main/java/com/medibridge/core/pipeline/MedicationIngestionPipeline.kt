package com.medibridge.core.pipeline

import android.content.Context
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.fromEntity
import com.medibridge.core.model.toEntity
import com.medibridge.moduleB_safety.data.SafetyRepository
import com.medibridge.moduleB_safety.logic.AiSafetyResponse
import com.medibridge.moduleB_safety.logic.MedicationNormalizer
import com.medibridge.moduleB_safety.logic.SafetyEngine
import com.medibridge.moduleB_safety.logic.SafetyEvaluationResult
import com.medibridge.moduleB_safety.logic.SafetyVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MedicationIngestionPipeline — Shared pipeline for both voice recordings (HomeScreen)
 * and uploaded/scanned prescriptions & bills (ScannerScreen).
 *
 * Sequence:
 * 1. Normalize candidate medications using MedicationNormalizer
 * 2. Dedupe/arrange against existing Room database records
 * 3. Run SafetyEngine.evaluateSafety against active regimen
 * 4. Populate conflicts, crossVerified, and reviewRecommended flags
 * 5. Persist to shared AppDatabase and log safety evaluation entries
 */
object MedicationIngestionPipeline {

    data class PipelineResultItem(
        val medication: MedicationObject,
        val evaluation: SafetyEvaluationResult
    )

    /**
     * Executes normalization, deduplication, and safety evaluation for a batch of candidate medications.
     */
    suspend fun evaluatePipeline(
        context: Context,
        candidates: List<MedicationObject>
    ): List<PipelineResultItem> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val existingEntities = db.medicationDao().getAllMedicationsDirect()
        val currentHistory = existingEntities.map { it.fromEntity() }.toMutableList()

        val results = mutableListOf<PipelineResultItem>()

        for (candidate in candidates) {
            // 1. Normalization
            val normalizedBase = MedicationNormalizer.extractBaseName(candidate.name)
            val displayName = if (normalizedBase.isNotEmpty() && !candidate.name.contains(normalizedBase, ignoreCase = true)) {
                "$normalizedBase (${candidate.name})".trim()
            } else {
                candidate.name.trim()
            }

            val normalizedCandidate = candidate.copy(
                name = displayName
            )

            // 2 & 3. Run SafetyEngine against current history (including previously evaluated in this batch)
            val evaluation = SafetyEngine.evaluateSafety(normalizedCandidate, currentHistory)

            val finalizedMed = evaluation.updatedMedication
            results.add(PipelineResultItem(finalizedMed, evaluation))

            // Add to running history so intra-batch conflicts (e.g. Lisinopril + Amlodipine in same prescription) are caught
            currentHistory.add(finalizedMed)
        }

        results
    }

    /**
     * Saves evaluated medications and their safety evaluation audits to the central Room AppDatabase.
     */
    suspend fun commitPipeline(
        context: Context,
        pipelineItems: List<PipelineResultItem>
    ) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val safetyRepo = SafetyRepository.getInstance(context)
        val allHistory = db.medicationDao().getAllMedicationsDirect().map { it.fromEntity() }

        for (item in pipelineItems) {
            // Upsert medication to Room
            db.medicationDao().upsertMedication(item.medication.toEntity())

            // Persist safety check record if conflicts exist or unverified
            if (item.evaluation.conflicts.isNotEmpty() || !item.evaluation.crossVerified) {
                val severity = when (item.evaluation.verdict) {
                    SafetyVerdict.INTERACTION, SafetyVerdict.DUPLICATE -> "HIGH"
                    SafetyVerdict.OVERLAP, SafetyVerdict.UNVERIFIED -> "MODERATE"
                    SafetyVerdict.SAFE -> "LOW"
                }
                val aiResponse = AiSafetyResponse(
                    flagged = item.evaluation.reviewRecommended,
                    severity = severity,
                    title = item.evaluation.headline,
                    message = item.evaluation.summary,
                    recommendation = if (item.evaluation.reviewRecommended) {
                        "Consult clinical pharmacist or prescribing physician regarding co-administration."
                    } else {
                        "Take medication as scheduled."
                    }
                )
                try {
                    safetyRepo.persistSafetyEvaluation(
                        evaluation = item.evaluation,
                        aiResponse = aiResponse,
                        candidateMed = item.medication,
                        history = allHistory
                    )
                } catch (e: Exception) {
                    // Safe handling for audit logging
                }
            }
        }
    }
}
