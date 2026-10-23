package com.medibridge.core.chat

import android.content.Context
import android.util.Log
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.model.fromEntity
import com.medibridge.core.network.BackendClient
import com.medibridge.core.network.ChatApiRequest
import com.medibridge.core.network.ChatCitationDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class ChatResponseResult(
    val reply: String,
    val source: String? = null,
    val citations: List<ChatCitationDto> = emptyList(),
    val safetyDisclaimer: String? = null,
    val isError: Boolean = false
)

class ChatRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val medicationDao = db.medicationDao()
    private val safetyCheckDao = db.safetyCheckDao()
    private val patientSummaryDao = db.patientSummaryDao()

    companion object {
        private const val TAG = "ChatRepository"
    }

    suspend fun sendMessage(query: String): String = sendGroundedMessage(query).reply

    suspend fun sendGroundedMessage(query: String): ChatResponseResult = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return@withContext ChatResponseResult(
                reply = "Please ask a question about your medications or schedule."
            )
        }

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

        try {
            val backendService = BackendClient.getService()
            val backendResp = backendService.chatWithAi(
                ChatApiRequest(
                    message = trimmedQuery,
                    patientContext = contextBlock
                )
            )
            if (backendResp.reply.isNotBlank()) {
                Log.i(TAG, "[CHAT_PATH: BACKEND_AI_SUCCESS] Sourced via ${backendResp.source}")
                return@withContext ChatResponseResult(
                    reply = backendResp.reply.trim(),
                    source = backendResp.source,
                    citations = backendResp.citations,
                    safetyDisclaimer = backendResp.safetyDisclaimer,
                    isError = false
                )
            } else {
                return@withContext ChatResponseResult(
                    reply = "I received an empty response from Medi AI. Please try rephrasing your question.",
                    isError = true
                )
            }
        } catch (backendErr: Exception) {
            Log.e(TAG, "[CHAT_PATH: BACKEND_AI_ERROR] Backend chat failed: ${backendErr.message}", backendErr)
            val activeUrl = BackendClient.getBaseUrl()
            return@withContext ChatResponseResult(
                reply = "⚠️ Unable to connect to Medi AI server (${backendErr.localizedMessage ?: backendErr.message ?: "Connection error"}).\n\nActive Server URL: $activeUrl\n\nPlease verify that your server/ngrok tunnel is running or update the URL in Settings.",
                isError = true
            )
        }
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

        try {
            val backendService = BackendClient.getService()
            val backendResp = backendService.chatWithAi(
                ChatApiRequest(
                    message = "Summarize this consultation chat session into 3 clear sections: 1. Discussion Overview, 2. Medication Schedule & Instructions, 3. Precautions & Next Steps.\n\nConversation Transcript:\n$historyText",
                    patientContext = "Medications: ${medications.joinToString { "${it.name} (${it.dose}, ${it.frequency})" }}"
                )
            )
            if (backendResp.reply.isNotBlank()) {
                return@withContext "**Consultation Session Summary**:\n\n${backendResp.reply.trim()}"
            } else {
                return@withContext "⚠️ Unable to generate consultation summary: Medi AI returned an empty response. Please try again."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backend summarization failed: ${e.message}", e)
            val activeUrl = BackendClient.getBaseUrl()
            return@withContext "⚠️ Unable to generate consultation summary (${e.localizedMessage ?: e.message ?: "Connection error"}).\n\nActive Server URL: $activeUrl\n\nPlease ensure your backend server is reachable."
        }
    }

}

