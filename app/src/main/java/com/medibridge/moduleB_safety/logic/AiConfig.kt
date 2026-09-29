package com.medibridge.moduleB_safety.logic

import com.medibridge.moduleC_schedule.ai.GeminiConfigProvider

/**
 * Configuration holder for AI safety services.
 * Delegates to GeminiConfigProvider so Module B uses the same unified API key as all other modules.
 */
object AiConfig {
    val GEMINI_API_KEY: String
        get() = GeminiConfigProvider.getApiKey() ?: ""

    const val MODEL_NAME: String = "gemini-1.5-flash"
}
