package com.medibridge.core.demo

import android.content.Context
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.model.AdherenceRecord
import com.medibridge.core.model.MedicationConflict
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.ScheduleSlot
import com.medibridge.core.model.toEntity
import com.medibridge.moduleB_safety.data.SafetyCheckEntity
import java.time.LocalDate
import java.util.UUID

/**
 * DemoDataSeeder — Seeds the 3 pitch demo scenarios for MediBridge:
 *
 * 1. Diabetes — Metformin:
 *    OCR bill-scan flow, plain daily reminder, high confidence, no conflicts.
 *
 * 2. Hypertension — Lisinopril + Amlodipine:
 *    Voice-note consultation flow, Module B flags a real drug interaction conflict card.
 *
 * 3. Elderly / Caretaker — Calcium Supplement:
 *    Scheduled caretaker reminder; a missed dose triggers caretaker call escalation + TTS reminder.
 */
object DemoDataSeeder {

    private fun todayIso(): String = LocalDate.now().toString()

    suspend fun seedAll(context: Context) {
        clearAll(context)
        val db = AppDatabase.getInstance(context)
        val medDao = db.medicationDao()
        val safetyDao = db.safetyCheckDao()

        seedScenario1_Diabetes(medDao)
        seedScenario2_Hypertension(medDao, safetyDao)
        seedScenario3_ElderlyCaretaker(medDao)
    }

    suspend fun clearAll(context: Context) {
        val db = AppDatabase.getInstance(context)
        db.medicationDao().deleteAll()
        db.safetyCheckDao().deleteAll()
        db.patientSummaryDao().deleteAll()
    }

    suspend fun loadScenario(context: Context, scenarioIndex: Int) {
        val db = AppDatabase.getInstance(context)
        val medDao = db.medicationDao()
        val safetyDao = db.safetyCheckDao()

        clearAll(context)

        when (scenarioIndex) {
            1 -> seedScenario1_Diabetes(medDao)
            2 -> seedScenario2_Hypertension(medDao, safetyDao)
            3 -> seedScenario3_ElderlyCaretaker(medDao)
            4 -> { /* No Scenario: Starts empty, live scans/notes populate it cleanly */ }
        }
    }

    /**
     * Scenario 1: Diabetes — Metformin 500mg
     */
    suspend fun seedScenario1_Diabetes(medDao: com.medibridge.core.db.MedicationDao) {
        val metformin = MedicationObject(
            id = "demo_metformin_500",
            name = "Metformin HCl",
            strength = "500mg",
            dose = "1 tablet",
            frequency = "Once daily (Morning)",
            timing = "Take with breakfast",
            duration = "Ongoing",
            confidence = 0.96f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = listOf(
                ScheduleSlot(time = "08:00", slot = "Morning", withFood = true)
            ),
            adherence = listOf(
                AdherenceRecord(date = todayIso(), status = "taken", slotTime = "08:00")
            ),
            summary = "Oral biguanide used to manage high blood sugar levels in type 2 diabetes mellitus.",
            sideEffects = listOf("Mild nausea", "Stomach upset (reduced when taken with meals)"),
            visibleTo = listOf("patient", "caregiver", "doctor"),
            sourceType = "bill",
            consultationNotes = "Prescribed following HbA1c screening: 7.2%."
        )

        medDao.upsertMedication(metformin.toEntity())
    }

    /**
     * Scenario 2: Hypertension — Lisinopril 10mg + Amlodipine 5mg
     */
    suspend fun seedScenario2_Hypertension(
        medDao: com.medibridge.core.db.MedicationDao,
        safetyDao: com.medibridge.moduleB_safety.data.SafetyCheckDao
    ) {
        val lisinoprilId = "demo_lisinopril_10"
        val amlodipineId = "demo_amlodipine_5"

        val lisinopril = MedicationObject(
            id = lisinoprilId,
            name = "Lisinopril",
            strength = "10mg",
            dose = "1 tablet",
            frequency = "Once daily (Morning)",
            timing = "After breakfast",
            duration = "30 days",
            confidence = 0.94f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = listOf(
                MedicationConflict(
                    withMedId = amlodipineId,
                    type = "interaction",
                    detail = "Additive hypotensive effect with Amlodipine. Monitor blood pressure for dizziness."
                )
            ),
            reviewRecommended = true,
            schedule = listOf(
                ScheduleSlot(time = "08:30", slot = "Morning", withFood = true)
            ),
            adherence = listOf(
                AdherenceRecord(date = todayIso(), status = "pending", slotTime = "08:30")
            ),
            summary = "ACE inhibitor prescribed for blood pressure reduction and cardiovascular protection.",
            sideEffects = listOf("Dry cough", "Mild dizziness when standing quickly"),
            visibleTo = listOf("patient", "caregiver", "doctor"),
            sourceType = "voice",
            consultationNotes = "Consultation voice summary: Patient BP 148/92 mmHg. Dual therapy initiated."
        )

        val amlodipine = MedicationObject(
            id = amlodipineId,
            name = "Amlodipine Besylate",
            strength = "5mg",
            dose = "1 tablet",
            frequency = "Once daily (Night)",
            timing = "At bedtime",
            duration = "30 days",
            confidence = 0.92f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = listOf(
                MedicationConflict(
                    withMedId = lisinoprilId,
                    type = "interaction",
                    detail = "Combined ACE inhibitor and calcium channel blocker therapy requires BP monitoring."
                )
            ),
            reviewRecommended = true,
            schedule = listOf(
                ScheduleSlot(time = "21:00", slot = "Night", withFood = false)
            ),
            adherence = listOf(
                AdherenceRecord(date = todayIso(), status = "pending", slotTime = "21:00")
            ),
            summary = "Dihydropyridine calcium channel blocker that relaxes arterial smooth muscle.",
            sideEffects = listOf("Mild peripheral ankle edema", "Flushing"),
            visibleTo = listOf("patient", "caregiver", "doctor"),
            sourceType = "voice",
            consultationNotes = "Evening dosing to achieve stable 24h ambulatory BP."
        )

        medDao.upsertMedication(lisinopril.toEntity())
        medDao.upsertMedication(amlodipine.toEntity())

        // Insert real Module B safety evaluation record into Room table
        val safetyCheck = SafetyCheckEntity(
            id = UUID.randomUUID().toString(),
            medicineA = "Lisinopril",
            medicineB = "Amlodipine Besylate",
            medicineAStrength = "10mg",
            medicineBStrength = "5mg",
            findingType = "DRUG_INTERACTION",
            result = "MODERATE_INTERACTION",
            severity = "MODERATE",
            aiTitle = "Additive Antihypertensive Effect",
            aiMessage = "Dual antihypertensive combination: additive blood pressure reduction. Patient should stand up slowly and report orthostatic lightheadedness.",
            aiRecommendation = "Monitor blood pressure weekly; report dizziness on standing.",
            crossVerified = true,
            reviewRecommended = true,
            createdAt = System.currentTimeMillis()
        )
        safetyDao.insertSafetyCheck(safetyCheck)
    }

    /**
     * Scenario 3: Elderly / Caretaker — Calcium Supplement with Call Escalation
     */
    suspend fun seedScenario3_ElderlyCaretaker(medDao: com.medibridge.core.db.MedicationDao) {
        val calcium = MedicationObject(
            id = "demo_calcium_vitd",
            name = "Calcium 500mg + Vitamin D3",
            strength = "500mg / 400 IU",
            dose = "1 tablet",
            frequency = "Once daily (Afternoon)",
            timing = "After lunch with a full glass of water",
            duration = "60 days",
            confidence = 0.98f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = listOf(
                ScheduleSlot(time = "13:00", slot = "Afternoon", withFood = true)
            ),
            adherence = listOf(
                AdherenceRecord(date = todayIso(), status = "missed", slotTime = "13:00")
            ),
            summary = "Essential mineral and vitamin supplement for bone density maintenance in elderly care.",
            sideEffects = listOf("Mild constipation if fluid intake is low"),
            visibleTo = listOf("patient", "caregiver", "doctor"),
            sourceType = "bill",
            caretakerPhone = "+1 555-0199",
            callReminderStatus = "scheduled",
            consultationNotes = "Elderly care protocol: Auto-call caretaker if dose is unconfirmed."
        )

        medDao.upsertMedication(calcium.toEntity())
    }
}
