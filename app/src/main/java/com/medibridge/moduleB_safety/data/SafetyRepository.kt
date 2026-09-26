package com.medibridge.moduleB_safety.data

import android.content.Context
import com.medibridge.core.model.MedicationObject
import com.medibridge.moduleB_safety.logic.AiSafetyResponse
import com.medibridge.moduleB_safety.logic.SafetyEvaluationResult
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository interface and implementation managing Module B persistence.
 */
class SafetyRepository(
    private val dao: SafetyCheckDao
) {

    fun getSafetyHistory(): Flow<List<SafetyCheckEntity>> {
        return dao.getAllSafetyChecks()
    }

    suspend fun getSafetyHistoryDirect(): List<SafetyCheckEntity> {
        return dao.getAllSafetyChecksDirect()
    }

    suspend fun persistSafetyEvaluation(
        evaluation: SafetyEvaluationResult,
        aiResponse: AiSafetyResponse,
        candidateMed: MedicationObject,
        history: List<MedicationObject>
    ): List<SafetyCheckEntity> {
        val records = mutableListOf<SafetyCheckEntity>()

        if (evaluation.conflicts.isEmpty()) {
            val record = SafetyCheckEntity(
                id = UUID.randomUUID().toString(),
                medicineA = candidateMed.name,
                medicineB = "None (Full Regimen)",
                medicineAStrength = candidateMed.strength,
                medicineBStrength = "-",
                findingType = "SAFE",
                result = "No known conflicts detected in configured rules.",
                severity = aiResponse.severity,
                aiTitle = aiResponse.title,
                aiMessage = aiResponse.message,
                aiRecommendation = aiResponse.recommendation,
                crossVerified = evaluation.crossVerified,
                reviewRecommended = evaluation.reviewRecommended,
                createdAt = System.currentTimeMillis()
            )
            records.add(record)
        } else {
            for (conflict in evaluation.conflicts) {
                val conflictingMed = history.find { it.id == conflict.withMedId }
                val record = SafetyCheckEntity(
                    id = UUID.randomUUID().toString(),
                    medicineA = candidateMed.name,
                    medicineB = conflictingMed?.name ?: "Knowledge Base",
                    medicineAStrength = candidateMed.strength,
                    medicineBStrength = conflictingMed?.strength ?: "-",
                    findingType = conflict.type.uppercase(),
                    result = conflict.detail,
                    severity = aiResponse.severity,
                    aiTitle = aiResponse.title,
                    aiMessage = aiResponse.message,
                    aiRecommendation = aiResponse.recommendation,
                    crossVerified = evaluation.crossVerified,
                    reviewRecommended = evaluation.reviewRecommended,
                    createdAt = System.currentTimeMillis()
                )
                records.add(record)
            }
        }

        dao.insertAll(records)
        return records
    }

    suspend fun clearHistory() {
        dao.deleteAll()
    }

    companion object {
        @Volatile
        private var INSTANCE: SafetyRepository? = null

        fun getInstance(context: Context): SafetyRepository {
            return INSTANCE ?: synchronized(this) {
                val db = SafetyDatabase.getInstance(context)
                val repo = SafetyRepository(db.safetyCheckDao())
                INSTANCE = repo
                repo
            }
        }
    }
}
