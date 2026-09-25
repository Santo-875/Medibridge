package com.medibridge.moduleC_schedule.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Structured side-effect information model.
 */
data class SideEffectInfo(
    @SerializedName("medicine") val medicine: String,
    @SerializedName("commonSideEffects") val commonSideEffects: List<String>,
    @SerializedName("seriousSideEffects") val seriousSideEffects: List<String>,
    @SerializedName("importantNotes") val importantNotes: List<String>,
    @SerializedName("disclaimer") val disclaimer: String? = "For informational purposes only. Consult a healthcare professional for medical advice."
) {
    val effectiveDisclaimer: String
        get() = disclaimer ?: "For informational purposes only. Consult a healthcare professional for medical advice."
}

/**
 * SideEffectService — Module C AI retrieval of structured medication side-effect information.
 */
class SideEffectService(
    private val geminiService: GeminiService = GeminiApiClient.service,
    private val gson: Gson = Gson()
) {

    companion object {
        private const val TAG = "SideEffectService"
    }

    /**
     * Fetches structured side-effect information for a verified medication.
     *
     * @param medicineName Verified medication name (e.g. "Paracetamol", "Metformin").
     * @param strength Medication strength (e.g. "500mg").
     * @return [SideEffectInfo] parsed from Gemini or safe fallback structure.
     */
    suspend fun fetchSideEffects(medicineName: String, strength: String): SideEffectInfo = withContext(Dispatchers.IO) {
        val cleanName = medicineName.trim()
        val cleanStrength = strength.trim()

        if (cleanName.isBlank()) {
            return@withContext buildFallback(
                medicineName = "Unknown",
                note = "Cannot retrieve side-effects for unverified or blank medication name."
            )
        }

        val apiKey = GeminiConfigProvider.getApiKey()
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "Gemini API key is not configured. Returning safe informational fallback.")
            return@withContext buildFallback(
                medicineName = cleanName,
                note = "AI service key not configured. Consult prescribing doctor or pharmacist for side effects."
            )
        }

        val prompt = """
            You are a clinical pharmacology reference assistant.
            Provide structured side-effect information for the medication:
            Medicine: $cleanName
            Strength: $cleanStrength

            CRITICAL SAFETY INSTRUCTIONS:
            1. For informational purposes only. Do NOT prescribe, alter dosage, or recommend stopping medication.
            2. Clearly separate common, mild side effects from serious/rare side effects requiring medical attention.
            3. Return ONLY a valid JSON object matching this schema:
            {
              "medicine": "$cleanName",
              "commonSideEffects": ["side effect 1", "side effect 2"],
              "seriousSideEffects": ["serious side effect 1", "serious side effect 2"],
              "importantNotes": ["key precaution 1", "key precaution 2"]
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
                val parsed = parseJsonResponse(text, cleanName)
                if (parsed != null) {
                    return@withContext parsed
                }
            }

            Log.w(TAG, "Received empty or unparseable response from Gemini for $cleanName")
            return@withContext buildFallback(
                medicineName = cleanName,
                note = "Could not parse side effect data. Consult your pharmacist."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch side effects from Gemini for $cleanName: ${e.message}")
            return@withContext buildFallback(
                medicineName = cleanName,
                note = "Network or service unavailable. Check your internet connection."
            )
        }
    }

    private fun parseJsonResponse(rawText: String, fallbackMedicine: String): SideEffectInfo? {
        return try {
            // Strip markdown backticks if returned (e.g. ```json ... ```)
            val cleanJson = rawText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val info = gson.fromJson(cleanJson, SideEffectInfo::class.java)
            if (info != null && (info.commonSideEffects.isNotEmpty() || info.seriousSideEffects.isNotEmpty())) {
                info
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "JSON parsing error: ${e.message}")
            null
        }
    }

    private fun buildFallback(medicineName: String, note: String): SideEffectInfo {
        return SideEffectInfo(
            medicine = medicineName,
            commonSideEffects = emptyList(),
            seriousSideEffects = emptyList(),
            importantNotes = listOf(note),
            disclaimer = "For informational purposes. Follow your prescribed instructions and consult a healthcare professional."
        )
    }
}
