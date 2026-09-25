package com.medibridge.moduleC_schedule.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Supported reminder speech languages.
 */
enum class ReminderLanguage {
    ENGLISH,
    TAMIL
}

/**
 * ReminderTtsHelper — Dynamic voice reminder assistant for Module C.
 *
 * Supports English and Tamil voice announcements using on-device Android TextToSpeech.
 * Gracefully falls back to English if Tamil voice data is not installed on the user's device.
 */
class ReminderTtsHelper(private val context: Context) {

    companion object {
        private const val TAG = "ReminderTtsHelper"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
            } else {
                Log.w(TAG, "TextToSpeech initialization failed with status: $status")
            }
        }
    }

    /**
     * Builds reminder speech text dynamically for the given medication and dose.
     * Preserves the medicine name while localizing dosage/action text.
     */
    fun buildReminderText(medicineName: String, dose: String, language: ReminderLanguage): String {
        return when (language) {
            ReminderLanguage.ENGLISH -> {
                "$medicineName. $dose"
            }
            ReminderLanguage.TAMIL -> {
                val tamilDose = translateDoseToTamil(dose)
                "$medicineName. $tamilDose"
            }
        }
    }

    /**
     * Translates common dosage expressions into Tamil.
     */
    fun translateDoseToTamil(dose: String): String {
        val clean = dose.trim().lowercase()
        return when {
            clean.contains("1 tablet") || clean == "1 tab" -> "ஒரு மாத்திரை"
            clean.contains("2 tablets") || clean == "2 tabs" -> "இரண்டு மாத்திரைகள்"
            clean.contains("3 tablets") || clean == "3 tabs" -> "மூன்று மாத்திரைகள்"
            clean.contains("1/2 tablet") || clean.contains("half tablet") -> "அரை மாத்திரை"
            clean.contains("1 capsule") || clean == "1 cap" -> "ஒரு காப்ஸ்யூல்"
            clean.contains("2 capsules") || clean == "2 caps" -> "இரண்டு காப்ஸ்யூல்கள்"
            clean.contains("5 ml") || clean == "5ml" -> "ஐந்து மில்லி"
            clean.contains("10 ml") || clean == "10ml" -> "பத்து மில்லி"
            clean.contains("15 ml") || clean == "15ml" -> "பதினைந்து மில்லி"
            clean.contains("1 drop") -> "ஒரு சொட்டு"
            clean.contains("2 drops") -> "இரண்டு சொட்டுகள்"
            clean.contains("after food") || clean.contains("after meals") -> "உணவுக்குப் பின் $dose"
            clean.contains("before food") || clean.contains("before meals") -> "உணவுக்கு முன் $dose"
            else -> dose // Default back to original dose string safely
        }
    }

    /**
     * Speaks the reminder aloud in the specified language, falling back to English if Tamil is unavailable.
     */
    fun speakReminder(medicineName: String, dose: String, language: ReminderLanguage = ReminderLanguage.ENGLISH) {
        val engine = tts ?: return
        if (!isInitialized) {
            Log.w(TAG, "TTS not yet initialized, skipping speech")
            return
        }

        val textToSpeak: String
        val targetLocale: Locale

        if (language == ReminderLanguage.TAMIL) {
            val tamilLocale = Locale("ta", "IN")
            val result = engine.isLanguageAvailable(tamilLocale)
            if (result >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = tamilLocale
                targetLocale = tamilLocale
                textToSpeak = buildReminderText(medicineName, dose, ReminderLanguage.TAMIL)
            } else {
                Log.i(TAG, "Tamil TTS voice unavailable on device, falling back to English.")
                engine.language = Locale.ENGLISH
                targetLocale = Locale.ENGLISH
                textToSpeak = buildReminderText(medicineName, dose, ReminderLanguage.ENGLISH)
            }
        } else {
            engine.language = Locale.ENGLISH
            targetLocale = Locale.ENGLISH
            textToSpeak = buildReminderText(medicineName, dose, ReminderLanguage.ENGLISH)
        }

        try {
            engine.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "medibridge_reminder_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to speak reminder", e)
        }
    }

    /**
     * Cleans up TTS resources when no longer needed.
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }
}
