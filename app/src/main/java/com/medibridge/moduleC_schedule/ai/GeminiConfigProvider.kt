package com.medibridge.moduleC_schedule.ai

import java.io.File

/**
 * GeminiConfigProvider — Secure API key resolver for Module C.
 *
 * Security rules:
 *  - NEVER hardcode API keys in source code.
 *  - NEVER print API keys in log statements.
 *  - Reads from Environment variables, System properties, local .env file, or runtime setter.
 */
object GeminiConfigProvider {

    private var runtimeApiKey: String? = null

    /**
     * Programmatically sets the Gemini API key at runtime if needed.
     */
    fun setApiKey(key: String) {
        runtimeApiKey = key.trim()
    }

    /**
     * Resolves the Gemini API key securely.
     *
     * Order of resolution:
     * 1. Runtime override
     * 2. System environment variable (GEMINI_API_KEY)
     * 3. System property (gemini.api.key or GEMINI_API_KEY)
     * 4. Local .env file if running in local/test environment
     */
    fun getApiKey(): String? {
        // 1. Runtime override
        runtimeApiKey?.let { if (it.isNotBlank()) return it }

        // 2. System environment variable
        val envKey = System.getenv("GEMINI_API_KEY")
        if (!envKey.isNullOrBlank()) return envKey.trim()

        // 3. System property
        val propKey = System.getProperty("GEMINI_API_KEY") ?: System.getProperty("gemini.api.key")
        if (!propKey.isNullOrBlank()) return propKey.trim()

        // 4. Read from .env file if present in working directory (local testing/dev)
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
}
