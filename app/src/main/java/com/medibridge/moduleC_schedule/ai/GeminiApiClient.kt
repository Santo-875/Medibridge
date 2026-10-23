package com.medibridge.moduleC_schedule.ai

import com.google.gson.annotations.SerializedName

data class GeminiRequest(
    @SerializedName("contents") val contents: List<GeminiContent>,
    @SerializedName("generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    @SerializedName("parts") val parts: List<GeminiPart>
)

data class GeminiPart(
    @SerializedName("text") val text: String
)

data class GeminiGenerationConfig(
    @SerializedName("temperature") val temperature: Float = 0.2f,
    @SerializedName("responseMimeType") val responseMimeType: String? = null
)

data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    @SerializedName("content") val content: GeminiContent?
)

interface GeminiService {
    suspend fun generateContent(
        model: String = "gemini-3.6-flash",
        apiKey: String = "",
        request: GeminiRequest
    ): GeminiResponse
}

object GeminiApiClient {

    val service: GeminiService = object : GeminiService {
        override suspend fun generateContent(
            model: String,
            apiKey: String,
            request: GeminiRequest
        ): GeminiResponse {
            throw UnsupportedOperationException("Direct Gemini calls from app are disabled.")
        }
    }

    fun buildTextRequest(prompt: String, jsonOutput: Boolean = true): GeminiRequest {
        return GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            generationConfig = if (jsonOutput) {
                GeminiGenerationConfig(temperature = 0.1f, responseMimeType = "application/json")
            } else {
                GeminiGenerationConfig(temperature = 0.2f)
            }
        )
    }
}
