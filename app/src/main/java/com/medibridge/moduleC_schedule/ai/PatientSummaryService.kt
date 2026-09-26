package com.medibridge.moduleC_schedule.ai

import android.util.Log
import com.google.gson.Gson
import com.medibridge.core.db.PatientSummaryDao
import com.medibridge.core.db.PatientSummaryEntity
import com.medibridge.core.model.MedicationObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Result data class for generated Patient Summary.
 */
data class PatientSummaryResult(
    val medicationId: String,
    val summary: String,
    val sideEffects: SideEffectInfo,
    val isAiGenerated: Boolean
)

/**
 * PatientSummaryService — Generates patient-friendly summaries using Gemini AI
 * and persists them into the existing Room database via [PatientSummaryDao].
 */
class PatientSummaryService(
    private val patientSummaryDao: PatientSummaryDao,
    private val sideEffectService: SideEffectService = SideEffectService(),
    private val geminiService: GeminiService = GeminiApiClient.service,
    private val gson: Gson = Gson()
) {

    companion object {
        private const val TAG = "PatientSummaryService"
    }

    /**
     * Generates a patient summary for a verified medication and persists it in Room DB.
     */
    suspend fun generateAndSavePatientSummary(medication: MedicationObject): PatientSummaryResult = withContext(Dispatchers.IO) {
        val sideEffects = sideEffectService.fetchSideEffects(medication.name, medication.strength)

        val apiKey = GeminiConfigProvider.getApiKey()
        val summaryText = if (!apiKey.isNullOrBlank()) {
            fetchSummaryFromGemini(medication, apiKey) ?: buildFallbackSummary(medication)
        } else {
            buildFallbackSummary(medication)
        }

        // Persist in patient_summaries table inside Room
        val entity = PatientSummaryEntity(
            id = UUID.randomUUID().toString(),
            medicationId = medication.id,
            patientSummary = summaryText,
            sideEffects = gson.toJson(sideEffects),
            generatedAt = System.currentTimeMillis()
        )

        try {
            patientSummaryDao.upsertSummary(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist patient summary into Room DB: ${e.message}")
        }

        PatientSummaryResult(
            medicationId = medication.id,
            summary = summaryText,
            sideEffects = sideEffects,
            isAiGenerated = !apiKey.isNullOrBlank()
        )
    }

    private suspend fun fetchSummaryFromGemini(medication: MedicationObject, apiKey: String): String? {
        val prompt = """
            You are a helpful, clear medical information communicator for patients.
            Write a clear, simple 2-3 sentence patient summary explaining how and when to take this prescribed medication:
            - Medicine: ${medication.name}
            - Strength: ${medication.strength}
            - Dose: ${medication.dose}
            - Frequency: ${medication.frequency}
            - Timing: ${medication.timing}
            - Duration: ${medication.duration}

            SAFETY RULES:
            1. Do NOT invent medical diagnoses or clinical history.
            2. Do NOT alter prescribed dosage or timing.
            3. Do NOT tell the patient to stop or start any other medication.
            4. Keep the tone warm, clear, and reassuring.
            5. Return ONLY the plain text summary, no markdown, no quotes.
        """.trimIndent()

        return try {
            val request = GeminiApiClient.buildTextRequest(prompt, jsonOutput = false)
            val response = geminiService.generateContent(
                model = "gemini-1.5-flash",
                apiKey = apiKey,
                request = request
            )
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Gemini call failed for patient summary: ${e.message}")
            null
        }
    }

    private fun buildFallbackSummary(medication: MedicationObject): String {
        val timingPhrase = if (medication.timing.isNotBlank()) " (${medication.timing})" else ""
        val durationPhrase = if (medication.duration.isNotBlank()) " for ${medication.duration}" else ""
        return "Take ${medication.dose} of ${medication.name} ${medication.strength} ${medication.frequency.lowercase()}$timingPhrase$durationPhrase. Follow your doctor's instructions."
    }
}
