package com.medibridge.core.pipeline

import android.content.Context
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.db.PrescriptionRecordEntity
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

object MedicationIngestionPipeline {

    data class PipelineResultItem(
        val medication: MedicationObject,
        val evaluation: SafetyEvaluationResult,
        val reconciliation: ReconciledMedication? = null,
        val printedName: String = ""
    )

    suspend fun evaluatePipeline(
        context: Context,
        candidates: List<MedicationObject>
    ): List<PipelineResultItem> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val existingEntities = db.medicationDao().getAllMedicationsDirect()
        val currentHistory = existingEntities.map { it.fromEntity() }.toMutableList()

        val reconciliationList = MedicationReconciliationEngine.reconcile(candidates, currentHistory)
        val results = mutableListOf<PipelineResultItem>()

        for ((index, candidate) in candidates.withIndex()) {
            val normalizedBase = MedicationNormalizer.extractBaseName(candidate.name)
            val printed = candidate.name.trim()
            val normalizedTitle = if (normalizedBase.isNotEmpty()) {
                if (candidate.strength.isNotBlank() && !normalizedBase.contains(candidate.strength, ignoreCase = true)) {
                    "$normalizedBase ${candidate.strength}".trim()
                } else {
                    normalizedBase
                }
            } else {
                printed
            }

            val normalizedCandidate = candidate.copy(name = normalizedTitle)
            val evaluation = SafetyEngine.evaluateSafety(normalizedCandidate, currentHistory)
            val finalizedMed = evaluation.updatedMedication
            val recon = reconciliationList.getOrNull(index)

            results.add(PipelineResultItem(finalizedMed, evaluation, recon, printed))
            currentHistory.add(finalizedMed)
        }

        results
    }

    suspend fun commitPipeline(
        context: Context,
        pipelineItems: List<PipelineResultItem>,
        prescriptionRecord: PrescriptionRecordEntity? = null
    ) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val safetyRepo = SafetyRepository.getInstance(context)
        val allHistory = db.medicationDao().getAllMedicationsDirect().map { it.fromEntity() }

        if (prescriptionRecord != null) {
            try {
                db.prescriptionRecordDao().insertPrescription(prescriptionRecord)
            } catch (_: Exception) {}
        }

        for (item in pipelineItems) {
            db.medicationDao().upsertMedication(item.medication.toEntity())

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
                } catch (_: Exception) {}
            }
        }
    }
}
