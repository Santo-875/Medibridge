package com.medibridge.moduleB_safety.logic

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.medibridge.moduleB_safety.logic.fallback.FallbackSafetyAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Gemini-backed implementation of [AiSafetyAnalyzer].
 *
 * Implements a safe fallback: if the API key is a placeholder ("YOUR_GEMINI_API_KEY_HERE"),
 * missing, or if any network/parsing failure occurs, it returns polite, deterministic
 * local explanations from [FallbackSafetyAnalyzer] without crashing.
 */
class GeminiSafetyAnalyzer(
    private val customApiKey: String? = null,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : AiSafetyAnalyzer {

    // Secondary constructor for backward compatibility
    constructor(apiKey: String) : this(customApiKey = apiKey)

    private val effectiveApiKey: String
        get() = customApiKey?.takeIf { it.isNotBlank() } ?: AiConfig.GEMINI_API_KEY

    private val gson = Gson()

    private fun logDebug(tag: String, msg: String) {
        try { android.util.Log.d(tag, msg) } catch (_: Throwable) {}
    }

    private fun logError(tag: String, msg: String, tr: Throwable?) {
        try { android.util.Log.e(tag, msg, tr) } catch (_: Throwable) {}
    }

    override suspend fun analyzeSafety(input: AiSafetyComparisonInput): AiSafetyResponse {
        val key = effectiveApiKey
        // If placeholder or missing, immediately return robust local fallback
        if (key.isBlank() || key == "YOUR_GEMINI_API_KEY_HERE") {
            logDebug("GeminiSafetyAnalyzer", "No valid Gemini API key configured, using local fallback")
            return FallbackSafetyAnalyzer.generatePoliteExplanation(input)
        }

        return withContext(Dispatchers.IO) {
            try {
                callGeminiApi(input, key)
            } catch (e: Exception) {
                // Safe graceful degradation: NEVER crash on network or API failures
                logError("GeminiSafetyAnalyzer", "Gemini API call failed, falling back to local explanation", e)
                FallbackSafetyAnalyzer.generatePoliteExplanation(input)
            }
        }
    }

    private fun callGeminiApi(input: AiSafetyComparisonInput, key: String): AiSafetyResponse {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${AiConfig.MODEL_NAME}:generateContent?key=$key"

        val promptText = buildString {
            appendLine("You are a clinical communication assistant in MediBridge AI.")
            appendLine("The factual safety engine has detected the following structured medication safety findings:")
            appendLine(gson.toJson(input))
            appendLine("Task:")
            appendLine("Provide a polite, professional, and patient-understandable explanation.")
            appendLine("Do NOT prescribe, change dosage, or diagnose. Do NOT invent new findings.")
            appendLine("Return ONLY valid JSON matching this schema:")
            appendLine("{\"flagged\": boolean, \"severity\": \"LOW\"|\"MODERATE\"|\"HIGH\", \"title\": string, \"message\": string, \"recommendation\": string}")
        }

        val requestJson = JsonObject().apply {
            val contents = com.google.gson.JsonArray().apply {
                val contentObj = JsonObject().apply {
                    val parts = com.google.gson.JsonArray().apply {
                        val partObj = JsonObject().apply {
                            addProperty("text", promptText)
                        }
                        add(partObj)
                    }
                    add("parts", parts)
                }
                add(contentObj)
            }
            add("contents", contents)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            return FallbackSafetyAnalyzer.generatePoliteExplanation(input)
        }

        val responseBody = response.body?.string() ?: return FallbackSafetyAnalyzer.generatePoliteExplanation(input)
        return parseGeminiResponse(responseBody, input)
    }

    private fun parseGeminiResponse(responseBody: String, input: AiSafetyComparisonInput): AiSafetyResponse {
        return try {
            val root = gson.fromJson(responseBody, JsonObject::class.java)
            val text = root.getAsJsonArray("candidates")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?.get(0)?.asJsonObject
                ?.get("text")?.asString ?: return FallbackSafetyAnalyzer.generatePoliteExplanation(input)

            // Extract JSON substring if wrapped in markdown code fence
            val cleanJson = text
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val parsed = gson.fromJson(cleanJson, JsonObject::class.java)
            AiSafetyResponse(
                flagged = parsed.get("flagged")?.asBoolean ?: input.findings.isNotEmpty(),
                severity = parsed.get("severity")?.asString ?: "MODERATE",
                title = parsed.get("title")?.asString ?: "Medication safety review",
                message = parsed.get("message")?.asString ?: "Please discuss these medications with your pharmacist.",
                recommendation = parsed.get("recommendation")?.asString ?: "Professional review recommended.",
                isFallback = false
            )
        } catch (e: Exception) {
            FallbackSafetyAnalyzer.generatePoliteExplanation(input)
        }
    }
}
