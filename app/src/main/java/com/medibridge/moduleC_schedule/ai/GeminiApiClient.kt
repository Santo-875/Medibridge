package com.medibridge.moduleC_schedule.ai

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ── Gemini API Data Transfer Objects ──────────────────────────────────────────

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

// ── Retrofit Interface ───────────────────────────────────────────────────────

interface GeminiService {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String = "gemini-1.5-flash",
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// ── Client Factory ──────────────────────────────────────────────────────────

object GeminiApiClient {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    // Never log headers or query params that might contain the API key
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val service: GeminiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(GeminiService::class.java)
    }

    /**
     * Helper to create a GeminiRequest with single prompt text.
     */
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
