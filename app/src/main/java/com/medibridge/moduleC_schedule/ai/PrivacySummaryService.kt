package com.medibridge.moduleC_schedule.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.medibridge.core.model.MedicationObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PrivacySummaryService — Generates role-tailored summaries using Gemini AI
 * for "doctor", "caretaker", and "pharmacy" roles.
 *
 * Content generation is owned by Module C; access control/visibility is handled by Module D.
 */
class PrivacySummaryService(
    private val geminiService: GeminiService = GeminiApiClient.service,
    private val gson: Gson = Gson()
) {

    companion object {
        private const val TAG = "PrivacySummaryService"
        const val ROLE_DOCTOR = "doctor"
        const val ROLE_CARETAKER = "caretaker"
        const val ROLE_PHARMACY = "pharmacy"
    }

    /**
     * Generates a Map of role -> tailored summary content.
     */
    suspend fun generatePrivacySummary(medication: MedicationObject): Map<String, String> = withContext(Dispatchers.IO) {
        val apiKey = GeminiConfigProvider.getApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext buildFallbackPrivacySummaries(medication)
        }

        val prompt = """
            You are a clinical privacy and data-sharing assistant.
            Generate tailored summary views for 3 different stakeholders for this medication:
            - Medicine: ${medication.name}
            - Strength: ${medication.strength}
            - Dose: ${medication.dose}
            - Frequency: ${medication.frequency}
            - Timing: ${medication.timing}
            - Duration: ${medication.duration}
            - Safety Review Recommended: ${medication.reviewRecommended}
            - Conflicts count: ${medication.conflicts.size}

            Generate structured JSON with exactly these 3 keys:
            {
              "doctor": "Clinical view: Full drug regimen details, conflicts, titration, and monitoring guidance.",
              "caretaker": "Caregiver view: Actionable daily administration schedule, reminders, missed-dose handling, and urgent warning signs.",
              "pharmacy": "Dispensing view: Drug identity, strength, package count, duration, and refill requirements."
            }
        """.trimIndent()

        try {
            val request = GeminiApiClient.buildTextRequest(prompt, jsonOutput = true)
            val response = geminiService.generateContent(
                model = "gemini-1.5-flash",
                apiKey = apiKey,
                request = request
            )

            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                val clean = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val mapType = object : TypeToken<Map<String, String>>() {}.type
                val map: Map<String, String>? = gson.fromJson(clean, mapType)
                if (map != null && map.containsKey(ROLE_DOCTOR) && map.containsKey(ROLE_CARETAKER)) {
                    return@withContext map
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini call failed for privacy summary: ${e.message}")
        }

        buildFallbackPrivacySummaries(medication)
    }

    private fun buildFallbackPrivacySummaries(medication: MedicationObject): Map<String, String> {
        val conflictsText = if (medication.conflicts.isNotEmpty()) " (${medication.conflicts.size} safety conflicts flagged)" else ""
        return mapOf(
            ROLE_DOCTOR to "Doctor view: ${medication.name} ${medication.strength}, ${medication.dose} ${medication.frequency}, ${medication.timing}, duration: ${medication.duration}$conflictsText.",
            ROLE_CARETAKER to "Caretaker view: Administer ${medication.name} (${medication.dose}) ${medication.timing}. Remind patient daily as scheduled.",
            ROLE_PHARMACY to "Pharmacy view: Dispense ${medication.name} ${medication.strength}, ${medication.frequency} for ${medication.duration}."
        )
    }
}
