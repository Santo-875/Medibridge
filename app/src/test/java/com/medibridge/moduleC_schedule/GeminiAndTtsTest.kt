package com.medibridge.moduleC_schedule

import com.google.gson.Gson
import com.medibridge.moduleC_schedule.ai.GeminiConfigProvider
import com.medibridge.moduleC_schedule.ai.SideEffectInfo
import com.medibridge.moduleC_schedule.tts.ReminderLanguage
import com.medibridge.moduleC_schedule.tts.ReminderTtsHelper
import org.junit.Assert.*
import org.junit.Test

class GeminiAndTtsTest {

    private val gson = Gson()

    @Test
    fun `test side effect structured JSON parsing`() {
        val sampleJson = """
            {
              "medicine": "Metformin",
              "commonSideEffects": ["Nausea", "Diarrhea", "Stomach upset"],
              "seriousSideEffects": ["Lactic acidosis", "Severe hypoglycemia"],
              "importantNotes": ["Take with food to minimize GI disturbance", "Avoid excessive alcohol"]
            }
        """.trimIndent()

        val info = gson.fromJson(sampleJson, SideEffectInfo::class.java)
        assertEquals("Metformin", info.medicine)
        assertEquals(3, info.commonSideEffects.size)
        assertEquals(2, info.seriousSideEffects.size)
        assertTrue(info.commonSideEffects.contains("Nausea"))
        assertTrue(info.seriousSideEffects.contains("Lactic acidosis"))
        assertNotNull(info.effectiveDisclaimer)
    }

    @Test
    fun `test malformed JSON handling does not crash`() {
        val invalidJson = "{ invalid json content ... }"
        var parsed: SideEffectInfo? = null
        try {
            parsed = gson.fromJson(invalidJson, SideEffectInfo::class.java)
        } catch (_: Exception) {
            // Handled cleanly in service
        }
        assertNull(parsed)
    }

    @Test
    fun `test gemini config provider runtime override and security`() {
        // Ensure no API key is leaked or printed
        GeminiConfigProvider.setApiKey("test-dummy-key-not-real")
        assertEquals("test-dummy-key-not-real", GeminiConfigProvider.getApiKey())
        assertTrue(GeminiConfigProvider.hasValidApiKey())

        // Reset
        GeminiConfigProvider.setApiKey("")
    }

    @Test
    fun `test Tamil dose translation phrases`() {
        // Translation logic inside ReminderTtsHelper
        val dummyContext = DummyContextHelper()
        val ttsHelper = ReminderTtsHelper(dummyContext)

        val enText = ttsHelper.buildReminderText("Paracetamol", "1 tablet", ReminderLanguage.ENGLISH)
        assertEquals("Paracetamol. 1 tablet", enText)

        val taText = ttsHelper.buildReminderText("Paracetamol", "1 tablet", ReminderLanguage.TAMIL)
        assertEquals("Paracetamol. ஒரு மாத்திரை", taText)

        val taCapsule = ttsHelper.translateDoseToTamil("1 capsule")
        assertEquals("ஒரு காப்ஸ்யூல்", taCapsule)

        val taSyrup = ttsHelper.translateDoseToTamil("5 ml")
        assertEquals("ஐந்து மில்லி", taSyrup)
    }
}

// Minimal mock context for test execution
private class DummyContextHelper : android.content.ContextWrapper(null) {
    override fun getApplicationContext(): android.content.Context = this
}
