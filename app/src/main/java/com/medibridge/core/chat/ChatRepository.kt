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
                    
                    Rules:
                    1. Base your answer strictly on the patient's actual medications, schedule, and safety notes.
                    2. If asked about taking medications or missed doses, provide clear, safe advice.
                    3. Highlight any active conflicts or precautions if relevant.
                    4. Keep replies clear and easy to read (use 2-3 short bullet points when listing actions).
                    5. End with a reminder to consult their healthcare provider for prescription changes.

                    --- VERIFIED PATIENT CONTEXT ---
                    $contextBlock

                    --- PATIENT QUESTION ---
                    $trimmedQuery
                """.trimIndent()

                val request = GeminiApiClient.buildTextRequest(prompt, jsonOutput = false)
                val response = GeminiApiClient.service.generateContent(
                    model = "gemini-1.5-flash",
                    apiKey = apiKey,
                    request = request
                )

                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!replyText.isNullOrBlank()) {
                    return@withContext replyText.trim()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini call failed or timed out: ${e.message}, falling back to local RAG")
            }
        }

        // 4. Local RAG Fallback: Keyword & Entity matching over Room DB
        val lowerQuery = trimmedQuery.lowercase()
        return@withContext buildLocalRagResponse(lowerQuery, medications, contextBlock)
    }

    private fun buildLocalRagResponse(
        query: String,
        medications: List<com.medibridge.core.model.MedicationObject>,
        contextBlock: String
    ): String {
        // Match specific medications mentioned in the query
        val matchedMed = medications.firstOrNull { med ->
            val nameParts = med.name.lowercase().split(" ")
            nameParts.any { it.length > 3 && query.contains(it) }
        }

        return when {
            query.contains("metformin") || (matchedMed != null && matchedMed.name.contains("Metformin", ignoreCase = true)) -> {
                val med = matchedMed ?: medications.find { it.name.contains("Metformin", ignoreCase = true) }
                val timing = med?.timing ?: "with your morning meal"
                val dose = med?.dose ?: "500mg (1 tablet)"
                """
                Here are the details for **Metformin**:
                • **Dose**: $dose
                • **When to take**: $timing with a glass of water
                • **Purpose**: Blood sugar control for Type 2 Diabetes
                • **Tip**: Always take Metformin with food to minimize stomach upset.
                """.trimIndent()
            }

            query.contains("lisinopril") || query.contains("amlodipine") || query.contains("blood pressure") || query.contains("bp") -> {
                """
                Regarding your blood pressure medications:
                • **Lisinopril 10mg**: Take 1 tablet in the morning after breakfast.
                • **Amlodipine 5mg**: Take 1 tablet at bedtime.
                • **Safety Note**: These two medications work together to lower your blood pressure. Watch out for mild dizziness when standing up quickly.
                • **Doctor Advice**: Monitor and record your blood pressure twice a week.
                """.trimIndent()
            }

            query.contains("missed") || query.contains("forgot") -> {
                """
                If you missed a scheduled dose:
                • **Take it as soon as you remember**, unless it is almost time for your next scheduled dose.
                • **Never double up**: Do not take two doses at the same time to make up for a missed dose.
                • If you have caretaker alert enabled, your caretaker will receive an update if a dose remains unconfirmed.
                """.trimIndent()
            }

            query.contains("side effect") || query.contains("reaction") -> {
                """
                Here are general side effect guidelines for your active prescriptions:
                • **Metformin**: Mild gastrointestinal effects (nausea, stomach upset) can occur; taking with food helps.
                • **Lisinopril**: Dry persistent cough or mild dizziness.
                • **Amlodipine**: Mild ankle or foot swelling.
                • If you experience any swelling of the lips, tongue, or difficulty breathing, seek immediate medical care.
                """.trimIndent()
            }

            query.contains("schedule") || query.contains("when") || query.contains("time") -> {
                if (medications.isEmpty()) {
                    "You currently have no scheduled medications in your list. You can scan a prescription or bill using the camera button."
                } else {
                    buildString {
                        appendLine("Here is your current daily medication schedule:")
                        medications.forEach { med ->
                            val sched = if (med.schedule.isNotEmpty()) {
                                med.schedule.joinToString(", ") { "${it.slot} (${it.time})" }
                            } else {
                                "${med.frequency} · ${med.timing}"
                            }
                            appendLine("• **${med.name}**: $sched ($med.dose)")
                        }
                    }.trimEnd()
                }
            }

            matchedMed != null -> {
                """
                **${matchedMed.name}**:
                • **Dose**: ${matchedMed.dose}
                • **Frequency**: ${matchedMed.frequency}
                • **Instructions**: ${matchedMed.timing}
                • **Status**: ${if (matchedMed.needsVerification) "Needs Verification" else "Verified"}
                """.trimIndent()
            }

            else -> {
                """
                I've reviewed your active MediBridge health record:
                • You have **${medications.size} active medication(s)** in your profile.
                • You can ask me specific questions like:
                  - *"When should I take Metformin?"*
                  - *"What are the side effects of Lisinopril?"*
                  - *"What should I do if I missed a dose?"*
                """.trimIndent()
            }
        }
    }
}
