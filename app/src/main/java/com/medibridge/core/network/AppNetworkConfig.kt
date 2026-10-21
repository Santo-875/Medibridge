package com.medibridge.core.network

import com.medibridge.BuildConfig

object AppNetworkConfig {

    var manualOverrideUrl: String = ""

    val effectiveBaseUrl: String
        get() {
            val url = if (manualOverrideUrl.isNotBlank()) {
                manualOverrideUrl.trim()
            } else {
                BuildConfig.BACKEND_BASE_URL.trim()
            }
            return if (url.endsWith("/")) url else "$url/"
        }

    val apiKey: String
        get() = BuildConfig.API_KEY
}
