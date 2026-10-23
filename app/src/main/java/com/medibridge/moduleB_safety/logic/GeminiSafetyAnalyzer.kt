package com.medibridge.moduleB_safety.logic

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.medibridge.core.network.BackendClient
import com.medibridge.core.network.BackendSafetyRequest
import com.medibridge.moduleB_safety.logic.fallback.FallbackSafetyAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiSafetyAnalyzer(
    private val customApiKey: String? = null
) : AiSafetyAnalyzer {

    constructor() : this(null)

    private val gson = Gson()

    override suspend fun analyzeSafety(input: AiSafetyComparisonInput): AiSafetyResponse {
        return withContext(Dispatchers.IO) {
            try {
                val inputJson = gson.toJson(input)
                val mapType = object : TypeToken<Map<String, Any?>>() {}.type
                val mapped: Map<String, Any?> = gson.fromJson(inputJson, mapType)

                @Suppress("UNCHECKED_CAST")
                val candidateMed = mapped["candidateMedicine"] as? Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val findingsList = (mapped["findings"] as? List<Map<String, Any?>>) ?: emptyList()

                val request = BackendSafetyRequest(
                    candidateMedicine = candidateMed,
                    findings = findingsList,
                    crossVerified = input.crossVerified,
                    reviewRecommended = input.reviewRecommended
                )

                val response = BackendClient.getService().analyzeSafety(request)
                AiSafetyResponse(
                    flagged = response.flagged,
                    severity = response.severity,
                    title = response.title.ifBlank { "Medication Safety Review" },
                    message = response.message.ifBlank { "Please review these medications with your doctor." },
                    recommendation = response.recommendation.ifBlank { "Professional review recommended." },
                    isFallback = false
                )
            } catch (_: Exception) {
                FallbackSafetyAnalyzer.generatePoliteExplanation(input)
            }
        }
    }
}
