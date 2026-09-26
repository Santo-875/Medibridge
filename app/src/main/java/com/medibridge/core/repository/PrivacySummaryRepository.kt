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
 *
 * Tailored for clinical patient Mr Tan Ah Kow (NRIC: S1111111X).
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

        buildString {
            appendLine("=== PATIENT CLINICAL DOSSIER ===")
            appendLine("Patient Name: Mr Tan Ah Kow (Male, 55 years old)")
            appendLine("NRIC/FIN: S1111111X | Status: Divorced, Unemployed (Former cleaner)")
            appendLine("Primary Caretaker: Mr Tan Ah Beng (Son, Co-resident)")
            appendLine("Attending Physician: Dr Tan Ah Moi (MCR: 333333, Blackacre Hospital)")
            appendLine("Clinical Diagnoses: 1. Dementia (Moderate-Severe) 2. Ischemic Stroke 3. Hypertension, Cardiomyopathy, Chronic Renal Disease")
            appendLine("Mental Capacity Assessment: Lacks capacity in Personal Welfare & Property/Affairs (Mental Capacity Act)")
            appendLine()

            appendLine("=== ACTIVE MEDICATION REGIMEN (${meds.size} Prescriptions) ===")
            if (meds.isEmpty()) {
                appendLine("No active prescriptions recorded in database.")
            } else {
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
            }

            val severeFlags = safetyChecks.filter { it.findingType != "SAFE" }
            if (severeFlags.isNotEmpty()) {
                appendLine("\n=== SAFETY & DRUG INTERACTION AUDIT ===")
                severeFlags.forEach { flag ->
                    appendLine("• ${flag.medicineA} + ${flag.medicineB} [${flag.severity}]: ${flag.result}")
                    if (flag.aiRecommendation.isNotBlank()) {
                        appendLine("  Recommendation: ${flag.aiRecommendation}")
                    }
                }
            }

            if (recordings.isNotEmpty()) {
                val latest = recordings.first()
                appendLine("\n=== LATEST CLINICAL CONSULTATION AUDIO NOTE ===")
                appendLine("Recorded: ${dateFormatter.format(Date(latest.timestamp))}")
                appendLine("Transcript & Summary:")
                appendLine(latest.summary.ifBlank { latest.transcript })
            }
        }.trim()
    }

    /**
     * Pharmacy view: active medications, dosage, last refill/visit advice.
     */
    suspend fun getPharmacySummary(): String = withContext(Dispatchers.IO) {
        val meds = db.medicationDao().getAllMedicationsDirect().map { it.fromEntity() }

        buildString {
            appendLine("=== PHARMACY DISPENSING & VERIFICATION STATUS ===")
            appendLine("Patient: Mr Tan Ah Kow (NRIC: S1111111X, Age 55)")
            appendLine("Prescribing Doctor: Dr Tan Ah Moi (MCR: 333333, Blackacre Hospital)")
            appendLine("Authorized Collector: Mr Tan Ah Beng (Son / Primary Caretaker)")
            appendLine("Renal Caution: Chronic renal disease & cardiomyopathy on record.")
            appendLine()

            if (meds.isEmpty()) {
                appendLine("No active prescriptions currently on file for dispensing.")
            } else {
                appendLine("Active Medications to Dispense:")
                meds.forEach { med ->
                    appendLine("• Medicine: ${med.name}")
                    appendLine("  Dispensed Dosage: ${med.dose} (${med.strength})")
                    appendLine("  Instructions: ${med.timing} · ${med.frequency}")
                    appendLine("  Course Duration: ${med.duration}")
                    val verifiedStatus = if (med.verifiedByUser) "Verified by Caretaker" else "Requires Pharmacist Cross-Check"
                    appendLine("  Status: $verifiedStatus")
                }
            }

            appendLine("\nPharmacist Advice:")
            appendLine("• Co-administration of Lisinopril 10mg + Amlodipine 5mg requires monitoring for postural hypotension.")
            appendLine("• Donepezil 5mg requires supervision by son Ah Beng due to patient's cognitive memory impairment.")
            appendLine("• Next Refill Due: Schedule refill 5 days before 90-day course completion.")
        }.trim()
    }

    /**
     * Caretaker view: schedule adherence + emergency/caretaker-phone info.
     */
    suspend fun getCaretakerSummary(): String = withContext(Dispatchers.IO) {
        val meds = db.medicationDao().getAllMedicationsDirect().map { it.fromEntity() }

        buildString {
            appendLine("=== CARETAKER SUPPORT & ADHERENCE DASHBOARD ===")
            appendLine("Patient: Mr Tan Ah Kow (Age 55) | Caretaker: Mr Tan Ah Beng (Son)")
            appendLine("Caregiver Priority: High — Patient is incontinent, needs assistance with bathing & toilet. Feeds himself.")
            appendLine("Mental Capacity Notice: Inability to retain dates/places/arithmetic. Full supervision required for medication administration.")
            appendLine("Emergency Caretaker Phone: +65 9123 4567")
            appendLine("Attending Clinic: Dr Tan Ah Moi, Blackacre Hospital (Tel: +65 6789 0101)")
            appendLine()

            appendLine("=== MEDICATION SCHEDULE & DOSING CHECKLIST ===")
            if (meds.isEmpty()) {
                appendLine("No scheduled doses active.")
            } else {
                meds.forEach { med ->
                    val adherenceHistory = med.adherence
                    val missed = adherenceHistory.count { it.status.equals("missed", ignoreCase = true) }
                    val taken = adherenceHistory.count { it.status.equals("taken", ignoreCase = true) }
                    val adherenceBadge = if (missed > 0) "⚠️ $missed missed dose(s)" else "✓ $taken dose(s) taken"
                    appendLine("• ${med.name} (${med.strength}): $adherenceBadge")
                    if (med.schedule.isNotEmpty()) {
                        appendLine("  Times: ${med.schedule.joinToString(", ") { "${it.slot} (${it.time})" }}")
                    }
                }
            }

            appendLine("\nCaretaker Escalation Protocol:")
            appendLine("• If morning or evening dose is missed by > 60 minutes, automated phone call escalation will trigger.")
            appendLine("• In case of severe confusion, limb weakness, or difficulty swallowing, contact Blackacre Emergency immediately.")
        }.trim()
    }
}
