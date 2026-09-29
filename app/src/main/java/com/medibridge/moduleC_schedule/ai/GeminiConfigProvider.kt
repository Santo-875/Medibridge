package com.medibridge.moduleC_schedule.ai

import com.medibridge.BuildConfig
import java.io.File

/**
 * GeminiConfigProvider — Secure API key resolver for all modules.
 *
 * Priority order (first non-blank wins):
 *  1. Runtime override (set via Settings screen at runtime)
 *  2. BuildConfig.GEMINI_API_KEY — baked from .env at build time (PRIMARY on-device path)
 *  3. System environment variable / System property (for JVM unit tests)
 *  4. Local .env file in working dir (for JVM unit tests only)
 *
 * Security rules:
 *  - NEVER hardcode API keys in source code.
 *  - NEVER print the full API key in log statements.
 */
object GeminiConfigProvider {

    private var runtimeApiKey: String? = null

    /**
     * Programmatically sets the Gemini API key at runtime.
     * Called from Settings screen when the user enters/overrides the key.
     * Persisted via SharedPreferences by the Settings layer — not here.
     */
    fun setApiKey(key: String) {
        runtimeApiKey = key.trim()
    }

    /**
     * Resolves the Gemini API key.
     *
     * Order of resolution:
     * 1. Runtime override (Settings screen)
     * 2. BuildConfig.GEMINI_API_KEY (baked from .env at build time — works on-device)
     * 3. System environment variable (GEMINI_API_KEY)
     * 4. System property (gemini.api.key or GEMINI_API_KEY)
     * 5. Local .env file if running in local/test environment
     */
    fun getApiKey(): String? {
        // 1. Runtime override (e.g. user typed it in Settings)
        runtimeApiKey?.let { if (it.isNotBlank() && it != "your_gemini_api_key_here") return it }

        // 2. BuildConfig — baked from .env at build time (PRIMARY on-device path)
        try {
            val buildKey = BuildConfig.GEMINI_API_KEY
            if (buildKey.isNotBlank() && buildKey != "your_gemini_api_key_here") {
                return buildKey
            }
        } catch (_: Exception) {
            // BuildConfig not available in pure JVM unit tests — fall through
        }

        // 3. System environment variable
        val envKey = System.getenv("GEMINI_API_KEY")
        if (!envKey.isNullOrBlank()) return envKey.trim()

        // 4. System property
        val propKey = System.getProperty("GEMINI_API_KEY") ?: System.getProperty("gemini.api.key")
        if (!propKey.isNullOrBlank()) return propKey.trim()

        // 5. Read from .env file if present in working directory (local testing/dev)
        try {
            val envFile = File(".env")
            if (envFile.exists() && envFile.canRead()) {
                val lines = envFile.readLines()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("GEMINI_API_KEY=") && !trimmed.startsWith("#")) {
                        val key = trimmed.substringAfter("GEMINI_API_KEY=").trim().trim('"', '\'')
                        if (key.isNotBlank() && key != "your_gemini_api_key_here") {
                            return key
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Silently ignore file read failures; never leak details
        }

        return null
    }

    /**
     * Returns true if a valid Gemini API key is configured.
     */
    fun hasValidApiKey(): Boolean {
        val key = getApiKey()
        return !key.isNullOrBlank() && key != "your_gemini_api_key_here"
    }

    /**
     * Returns a human-readable status string for display in Settings / Chatbot.
     * Values: "Connected", "Not Configured", "Fallback Mode"
     */
    fun getStatusLabel(): String {
        return if (hasValidApiKey()) "AI: Connected" else "AI: Not Configured — Fallback Mode"
    }
}
