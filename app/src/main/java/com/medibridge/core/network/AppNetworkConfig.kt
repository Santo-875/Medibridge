package com.medibridge.core.network

import com.medibridge.BuildConfig

/**
 * AppNetworkConfig
 *
 * Direct configuration for MediBridge Backend Connection.
 *
 * HOW TO SET YOUR LAPTOP IP ADDRESS:
 * Option A (Code): Paste your laptop IP into [MANUAL_LAPTOP_IP] below.
 *   Example: const val MANUAL_LAPTOP_IP = "http://192.168.1.50:8000/"
 *
 * Option B (.env): Edit BACKEND_URL in the project root .env file:
 *   BACKEND_URL=http://192.168.1.50:8000/
 *
 * When you assemble the APK, the app will automatically communicate with this server address.
 */
object AppNetworkConfig {

    /**
     * >>> PASTE YOUR LAPTOP IP ADDRESS HERE (e.g. "http://192.168.1.50:8000/") <<<
     * Leave as "" to automatically use the value from the .env file (BuildConfig.BACKEND_BASE_URL).
     */
    const val MANUAL_LAPTOP_IP: String = ""

    val effectiveBaseUrl: String
        get() {
            val url = if (MANUAL_LAPTOP_IP.isNotBlank()) {
                MANUAL_LAPTOP_IP.trim()
            } else {
                BuildConfig.BACKEND_BASE_URL.trim()
            }
            return if (url.endsWith("/")) url else "$url/"
        }
}
