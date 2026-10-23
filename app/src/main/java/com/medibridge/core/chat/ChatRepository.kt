package com.medibridge.core.chat

import android.content.Context
import android.util.Log
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.model.fromEntity
import com.medibridge.moduleC_schedule.ai.GeminiApiClient
import com.medibridge.moduleC_schedule.ai.GeminiConfigProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * ChatRepository — RAG chatbot engine for MediBridge (Module D).
 *
 * On each message:
 * 1. Queries local Room DB (medications, safety history, patient summaries).
 * 2. Matches keywords and builds a grounded clinical context block.
 * 3. Calls Gemini API using the GeminiApiClient pattern.
 * 4. Gracefully falls back to structured offline clinical reasoning if Gemini is unavailable.
 */
class ChatRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val medicationDao = db.medicationDao()
    private val safetyCheckDao = db.safetyCheckDao()
    private val patientSummaryDao = db.patientSummaryDao()

    companion object {
        private const val TAG = "ChatRepository"
    }

    suspend fun sendMessage(query: String): String = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext "Please ask a question about your medications or schedule."

        // 1. Retrieve all patient context from Room DB
        val medications = try {
            medicationDao.getAllMedications().first().map { it.fromEntity() }
        } catch (e: Exception) {
            emptyList()
        }

        val safetyChecks = try {
            safetyCheckDao.getAllSafetyChecksDirect()
        } catch (e: Exception) {
            emptyList()
        }

        val patientSummaries = try {
            patientSummaryDao.getAllSummariesDirect()
        } catch (e: Exception) {
            emptyList()
        }

        val recordings = try {
            db.recordingDao().getAllRecordingsDirect()
        } catch (e: Exception) {
            emptyList()
        }

        // 2. Build structured RAG context block
        val contextBlock = buildString {
            appendLine("=== VERIFIED PATIENT MEDICATIONS ===")
            if (medications.isEmpty()) {
                appendLine("No active medications found in patient database.")
            } else {
                medications.forEach { med ->
                    appendLine("• Medicine: ${med.name}")
                    appendLine("  Strength: ${med.strength} | Dose: ${med.dose} | Frequency: ${med.frequency}")
                    appendLine("  Timing: ${med.timing} | Duration: ${med.duration}")
                    appendLine("  Confidence: ${(med.confidence * 100).toInt()}% | Needs Verification: ${med.needsVerification}")
                    if (med.conflicts.isNotEmpty()) {
                        appendLine("  Conflicts Detected: ${med.conflicts.joinToString("; ") { "${it.type}: ${it.detail}" }}")
                    }
                    if (med.consultationNotes.isNotBlank()) {
                        appendLine("  Doctor/Consultation Notes: ${med.consultationNotes}")
                    }
                    if (med.schedule.isNotEmpty()) {
                        appendLine("  Schedule: ${med.schedule.joinToString(", ") { "${it.slot} at ${it.time} (with food: ${it.withFood})" }}")
                    }
                    appendLine()
                }
            }

            if (safetyChecks.isNotEmpty()) {
                appendLine("=== SAFETY AUDIT & INTERACTION CHECKS ===")
                safetyChecks.take(5).forEach { check ->
                    appendLine("• Check: ${check.medicineA} + ${check.medicineB} -> Severity: ${check.severity} (${check.result})")
                    appendLine("  Detail: ${check.aiTitle} - ${check.aiMessage}")
                    if (check.aiRecommendation.isNotBlank()) {
                        appendLine("  Recommendation: ${check.aiRecommendation}")
                    }
                }
                appendLine()
            }

            if (recordings.isNotEmpty()) {
                appendLine("=== PAST CONSULTATION RECORDINGS ===")
                recordings.take(3).forEach { rec ->
                    appendLine("• Audio Summary: ${rec.summary}")
                    if (rec.transcript.isNotBlank()) {
                        appendLine("  Transcript Preview: ${rec.transcript.take(150)}...")
                    }
                }
                appendLine()
            }

            if (patientSummaries.isNotEmpty()) {
                appendLine("=== CLINICAL SUMMARY ===")
                patientSummaries.forEach { ps ->
                    appendLine(ps.patientSummary)
                }
            }
        }

        // 3. Try Gemini API call if key configured
        val apiKey = GeminiConfigProvider.getApiKey()
        if (!apiKey.isNullOrBlank() && apiKey != "your_gemini_api_key_here") {
            try {
                val prompt = """
                    You are Medi, an intelligent, empathetic medical assistant inside the MediBridge health app.
                    Answer the patient's question accurately, concisely, and with warmth, using ONLY the verified medical context below.
                    
                    Structure your response strictly using this clean format:
                    1. A concise, empathetic 1-2 sentence direct answer.
                    2. **Schedule & Instructions**: Clear bullet points (•) listing specific medicines, doses, food timing, or steps.
                    3. **Precautions & Safety**: Clear bullet points (•) highlighting warnings, side effects, missed dose guidance, or food/drug interactions (if relevant).
                    4. **Clinical Advice**: A brief 1-sentence reminder on when to contact their doctor.

                    Formatting Rules:
                    - Use bold titles with clean headers (e.g., **Schedule & Instructions**, **Precautions & Safety**).
                    - Use bullet points (•) for each distinct point.
                    - Keep sentences crisp, readable, and patient-friendly.

                    --- VERIFIED PATIENT CONTEXT ---
                    $contextBlock

                    --- PATIENT QUESTION ---
                    $trimmedQuery
                """.trimIndent()

                val request = GeminiApiClient.buildTextRequest(prompt, jsonOutput = false)
                val response = GeminiApiClient.service.generateContent(
                    model = "gemini-flash-latest",
                    apiKey = apiKey,
                    request = request
                )

                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!replyText.isNullOrBlank()) {
                    Log.i(TAG, "[CHAT_PATH: GEMINI_SUCCESS] Successfully generated clinical response via Gemini Flash")
                    return@withContext replyText.trim()
                } else {
                    Log.w(TAG, "[CHAT_PATH: GEMINI_EMPTY] Gemini returned empty response text, falling back to local RAG")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[CHAT_PATH: GEMINI_FAILED] Gemini call failed: ${e.message}, falling back to local RAG", e)
            }
        } else {
            Log.i(TAG, "[CHAT_PATH: LOCAL_RAG_NO_KEY] No valid Gemini API key present, using local RAG engine")
        }

        // 4. Local RAG Fallback: Keyword & Entity matching over Room DB
        Log.i(TAG, "[CHAT_PATH: LOCAL_RAG_FALLBACK] Serving answer via local clinical rule matching for: $trimmedQuery")
        val lowerQuery = trimmedQuery.lowercase()
        return@withContext buildLocalRagResponse(lowerQuery, medications, contextBlock)
    }

    /**
     * Summarizes the chat message history + relevant DB rows for the session.
     */
    suspend fun summarizeSession(chatHistory: List<String>): String = withContext(Dispatchers.IO) {
        val medications = try {
            medicationDao.getAllMedications().first().map { it.fromEntity() }
        } catch (e: Exception) {
            emptyList()
        }

        val safetyChecks = try {
            safetyCheckDao.getAllSafetyChecksDirect()
        } catch (e: Exception) {
            emptyList()
        }

        val historyText = chatHistory.joinToString("\n")

        val apiKey = GeminiConfigProvider.getApiKey()
        if (!apiKey.isNullOrBlank() && apiKey != "your_gemini_api_key_here") {
            try {
                val prompt = """
                    You are Medi, the clinical AI assistant for MediBridge.
                    Summarize this consultation chat session into a structured, patient-friendly summary.
                    
                    --- RELEVANT CLINICAL DATA ---
                    Medications: ${medications.joinToString { "${it.name} (${it.dose}, ${it.frequency})" }}
                    Safety Flags: ${safetyChecks.filter { it.findingType != "SAFE" }.joinToString { "${it.medicineA}+${it.medicineB}: ${it.result}" }}
                    
                    --- CONVERSATION TRANSCRIPT ---
                    $historyText
                    
                    Provide the summary in 3 concise sections with clear bold headers and bullets:
                    1. **Discussion Overview** (topics reviewed)
                    2. **Medication Schedule & Instructions** (medicines and timings)
                    3. **Precautions & Next Steps** (warnings and doctor follow-up)
                """.trimIndent()

                val request = GeminiApiClient.buildTextRequest(prompt, jsonOutput = false)
                val response = GeminiApiClient.service.generateContent(
                    model = "gemini-flash-latest",
                    apiKey = apiKey,
                    request = request
                )

                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!replyText.isNullOrBlank()) {
                    return@withContext "**Consultation Session Summary**:\n\n${replyText.trim()}"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini summarization failed: ${e.message}, falling back to local summary")
            }
        }

        // Local summarization fallback
        val medSummary = if (medications.isNotEmpty()) {
            medications.joinToString("\n") { "• **${it.name}**: ${it.dose} (${it.timing})" }
        } else {
            "• No active medications registered."
        }

        val conflictSummary = safetyChecks.filter { it.findingType != "SAFE" }
            .take(2)
            .joinToString("\n") { "• [Alert] ${it.medicineA} + ${it.medicineB}: ${it.aiMessage}" }
            .ifBlank { "• No critical medication interaction warnings identified." }

        """
        **Session Clinical Summary**
        
        **1. Discussion Overview**
        • Reviewed ${medications.size} active medication regimen(s) and answered patient inquiries.
        
        **2. Active Regimen & Schedule**
        $medSummary
        
        **3. Precautions & Actions**
        $conflictSummary
        • Consult your healthcare provider before adjusting any dosages.
        """.trimIndent()
    }

    private fun buildLocalRagResponse(
        query: String,
        medications: List<com.medibridge.core.model.MedicationObject>,
        contextBlock: String
    ): String {
        // Match specific medications mentioned in the query
        val matchedMed = medications.firstOrNull { med ->
            val nameParts = med.name.lowercase().split(" ")
            nameParts.any { it.length > 2 && query.contains(it) }
        }

        return when {
            matchedMed != null -> {
                val timing = if (matchedMed.timing.isNotBlank()) matchedMed.timing else "as instructed on prescription"
                val dose = if (matchedMed.dose.isNotBlank()) matchedMed.dose else matchedMed.strength
                val scheduleDesc = if (matchedMed.schedule.isNotEmpty()) {
                    matchedMed.schedule.joinToString(", ") { "${it.slot} at ${it.time} (${if (it.withFood) "with food" else "without food"})" }
                } else {
                    matchedMed.frequency
                }

                """
                Here are the clinical details for **${matchedMed.name}**:

                **Dosage & Administration**
                • **Dose / Strength**: $dose (${matchedMed.strength})
                • **Schedule**: $scheduleDesc
                • **Instructions**: $timing

                **Precautions & Safety**
                • Always take with a full glass of water.
                • Take strictly according to your prescribed schedule.
                • If you experience unusual discomfort, dizziness, or allergic reactions, notify your doctor promptly.

                **Clinical Advice**
                • Do not adjust or stop your dosage without consulting your prescribing physician.
                """.trimIndent()
            }

            query.contains("missed") || query.contains("forgot") -> {
                """
                Guidance for a missed medication dose:

                **Immediate Steps**
                • **Take the missed dose as soon as you remember**, unless your next scheduled dose is coming up soon.
                • **Never double up**: Do not take two doses at once to make up for a missed dose.

                **Precautions & Safety**
                • If it is almost time for your next scheduled dose, skip the missed one and continue your normal schedule.
                • Check your MediBridge schedule to verify your upcoming dose times.

                **Clinical Advice**
                • Reach out to your pharmacist or healthcare provider if you have missed more than one consecutive dose.
                """.trimIndent()
            }

            query.contains("side effect") || query.contains("reaction") -> {
                if (medications.isEmpty()) {
                    """
                    **Side Effects Guidance**
                    • You have no active medications currently registered in your profile.
                    • If you've been prescribed medicine, tap **Scan Prescription** to log your medications.
                    """.trimIndent()
                } else {
                    buildString {
                        appendLine("General side effect precautions for your active prescription(s):\n")
                        appendLine("**Active Medications**")
                        medications.forEach { med ->
                            appendLine("• **${med.name}** (${med.dose}): Monitor for mild GI upset, dizziness, or headache when beginning therapy.")
                        }
                        appendLine("\n**Important Safety Advice**")
                        appendLine("• Seek emergency medical attention if you experience severe symptoms like difficulty breathing, chest pain, or facial swelling.")
                        appendLine("• For mild, ongoing side effects, discuss alternative options with your prescribing doctor.")
                    }.trimEnd()
                }
            }

            query.contains("schedule") || query.contains("when") || query.contains("time") || query.contains("dose") -> {
                if (medications.isEmpty()) {
                    """
                    **Medication Schedule**
                    • No active medications registered yet in your MediBridge profile.
                    • Tap the **Scan Prescription** button or add a medication to configure your schedule.
                    """.trimIndent()
                } else {
                    buildString {
                        appendLine("Here is your current daily medication schedule:\n")
                        appendLine("**Active Regimen**")
                        medications.forEach { med ->
                            val sched = if (med.schedule.isNotEmpty()) {
                                med.schedule.joinToString(", ") { "${it.slot} (${it.time})" }
                            } else {
                                "${med.frequency} · ${med.timing}"
                            }
                            appendLine("• **${med.name}**: $sched ($med.dose)")
                        }
                        appendLine("\n**Clinical Advice**")
                        appendLine("• Take your doses at consistent times each day for maximum therapeutic effect.")
                    }.trimEnd()
                }
            }

            query.contains("interaction") || query.contains("conflict") || query.contains("safe") -> {
                if (medications.size < 2) {
                    """
                    **Medication Safety Check**
                    • You have ${medications.size} active medication registered.
                    • Drug-drug interaction audits require 2 or more concurrent medications.
                    • Tap **Medication Safety** from the menu to run a full clinical interaction audit.
                    """.trimIndent()
                } else {
                    buildString {
                        appendLine("Safety summary for your concurrent medications (${medications.joinToString { it.name }}):\n")
                        appendLine("• Automated clinical safety checks run continuously on your active medications.")
                        appendLine("• Navigate to the **Medication Safety** screen to view full interaction ratings and details.")
                        appendLine("\n**Clinical Advice**")
                        appendLine("• Always inform your physician and pharmacist about all prescription and OTC medications you take.")
                    }.trimEnd()
                }
            }

            else -> {
                if (medications.isNotEmpty()) {
                    val firstMed = medications.first().name
                    """
                    I've reviewed your active MediBridge health record:

                    **Summary**
                    • You have **${medications.size} active medication(s)** registered: ${medications.joinToString { it.name }}.

                    **Questions You Can Ask**
                    • *"When should I take $firstMed?"*
                    • *"What are the common side effects of $firstMed?"*
                    • *"What should I do if I missed a dose?"*
                    • *"Show my complete daily schedule."*
                    """.trimIndent()
                } else {
                    """
                    Welcome to Medi AI!

                    **Summary**
                    • You have no active medications registered yet in your profile.

                    **Getting Started**
                    • Tap **Scan Prescription** to import your medications from a prescription slip.
                    • Or tap **+** on the Medicines screen to add your medications manually.
                    • Ask me questions anytime about dose timing, missed doses, or side effects!
                    """.trimIndent()
                }
            }
        }
    }
}
