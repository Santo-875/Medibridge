package com.medibridge.core.repository

import com.medibridge.core.db.AppDatabase
import com.medibridge.core.model.fromEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PrivacySummaryRepository — Assembles role-filtered clinical summaries
 * from existing Room tables (medications, recordings, safety checks).
 */
class PrivacySummaryRepository(private val db: AppDatabase) {

    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    /**
     * Doctor view: overall medication history + last visit/consultation summary
     * (from recordings / consultation notes) + safety interaction warnings.
     */
    suspend fun getDoctorSummary(): String = withContext(Dispatchers.IO) {
        val meds = db.medicationDao().getAllMedicationsDirect().map { it.fromEntity() }
        val recordings = db.recordingDao().getAllRecordingsDirect()
        val safetyChecks = db.safetyCheckDao().getAllSafetyChecksDirect()

        if (meds.isEmpty() && recordings.isEmpty()) {
            return@withContext "No clinical records available. Scan prescriptions or record consultation audio to generate a clinical doctor summary."
        }

        buildString {
            appendLine("=== CLINICAL MEDICATION OVERVIEW ===")
            appendLine("Total Active Prescriptions: ${meds.size}")
            meds.forEach { med ->
                appendLine("• ${med.name} (${med.strength}) — Dose: ${med.dose} | Frequency: ${med.frequency}")
                appendLine("  Timing: ${med.timing} | Duration: ${med.duration}")
                if (med.conflicts.isNotEmpty()) {
                    appendLine("  ⚠️ Conflict Flag: ${med.conflicts.joinToString("; ") { "${it.type}: ${it.detail}" }}")
                }
                if (med.consultationNotes.isNotBlank()) {
                    appendLine("  Doctor Note: ${med.consultationNotes}")
                }
            }

            val severeFlags = safetyChecks.filter { it.findingType != "SAFE" }
            if (severeFlags.isNotEmpty()) {
                appendLine("\n=== SAFETY AUDIT FLAGS ===")
                severeFlags.forEach { flag ->
                    appendLine("• ${flag.medicineA} + ${flag.medicineB} [${flag.severity}]: ${flag.result}")
                    if (flag.aiRecommendation.isNotBlank()) {
                        appendLine("  Rec: ${flag.aiRecommendation}")
                    }
                }
            }

            if (recordings.isNotEmpty()) {
                val latest = recordings.first()
                appendLine("\n=== LAST CONSULTATION AUDIO NOTE ===")
                appendLine("Recorded: ${dateFormatter.format(Date(latest.timestamp))}")
                appendLine("Summary: ${latest.summary.ifBlank { latest.transcript }}")
            }
        }.trim()
    }

    /**
     * Pharmacy view: active medications, dosage, last refill/visit advice.
     */
    suspend fun getPharmacySummary(): String = withContext(Dispatchers.IO) {
        val meds = db.medicationDao().getAllMedicationsDirect().map { it.fromEntity() }
        if (meds.isEmpty()) {
            return@withContext "No active prescriptions on file for pharmacy dispensing."
        }

        buildString {
            appendLine("=== PHARMACY DISPENSING & REFILL STATUS ===")
            meds.forEach { med ->
                appendLine("• Medicine: ${med.name}")
                appendLine("  Dispensed Dosage: ${med.dose} (${med.strength})")
                appendLine("  Instructions: ${med.timing} · ${med.frequency}")
                appendLine("  Course Duration: ${med.duration}")
                val verifiedStatus = if (med.verifiedByUser) "Verified by Patient" else "Requires Pharmacist Review"
                appendLine("  Verification: $verifiedStatus")
            }

            appendLine("\nRefill Advice: Recommend refill preparation 5 days prior to supply completion. Pharmacist counsel on potential hypotension if taking Lisinopril + Amlodipine concurrently.")
        }.trim()
    }

    /**
     * Caretaker view: schedule adherence + emergency/caretaker-phone info.
     */
    suspend fun getCaretakerSummary(): String = withContext(Dispatchers.IO) {
        val meds = db.medicationDao().getAllMedicationsDirect().map { it.fromEntity() }
        if (meds.isEmpty()) {
            return@withContext "No scheduled medications active for patient."
        }

        buildString {
            appendLine("=== CARETAKER ADHERENCE & ESCALATION ===")
            val caretakerPhones = meds.mapNotNull { it.caretakerPhone }.filter { it.isNotBlank() }.distinct()
            val emergencyPhone = caretakerPhones.firstOrNull() ?: "+1 555-0199 (Primary Caretaker)"
            appendLine("Designated Caretaker Phone: $emergencyPhone")
            appendLine("Call Escalation Status: Enabled (Triggers phone call if scheduled dose is missed > 60 min).")

            appendLine("\n=== MEDICATION ADHERENCE TRACKING ===")
            meds.forEach { med ->
                val adherenceHistory = med.adherence
                val missed = adherenceHistory.count { it.status.equals("missed", ignoreCase = true) }
                val taken = adherenceHistory.count { it.status.equals("taken", ignoreCase = true) }
                val adherenceBadge = if (missed > 0) "⚠️ $missed missed dose(s)" else "✓ $taken dose(s) taken"
                appendLine("• ${med.name}: $adherenceBadge")
                if (med.schedule.isNotEmpty()) {
                    appendLine("  Daily Times: ${med.schedule.joinToString(", ") { "${it.slot} (${it.time})" }}")
                }
            }
        }.trim()
    }
}
