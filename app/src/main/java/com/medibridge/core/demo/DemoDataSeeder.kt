package com.medibridge.core.demo

import android.content.Context
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.db.RecordingEntity
import com.medibridge.core.model.AdherenceRecord
import com.medibridge.core.model.MedicationConflict
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.ScheduleSlot
import com.medibridge.core.model.toEntity
import com.medibridge.moduleB_safety.data.SafetyCheckEntity
import java.time.LocalDate
import java.util.UUID

/**
 * DemoDataSeeder — Seeds clinical patient profiles & pitch demo scenarios for MediBridge.
 *
 * Primary Patient: Mr Tan Ah Kow (Age 55, NRIC: S1111111X)
 * Attending Doctor: Dr Tan Ah Moi (MCR: 333333, Blackacre Hospital)
 * Caretaker: Mr Tan Ah Beng (Son, +65 9123 4567)
 * Diagnoses: 1. Dementia 2. Stroke 3. Hypertension & Cardiomyopathy
 */
object DemoDataSeeder {

    private fun todayIso(): String = LocalDate.now().toString()

    suspend fun seedAll(context: Context) {
        clearAll(context)
        val db = AppDatabase.getInstance(context)
        seedPatientMrTanAhKow(db)
    }

    suspend fun clearAll(context: Context) {
        val db = AppDatabase.getInstance(context)
        db.medicationDao().deleteAll()
        db.safetyCheckDao().deleteAll()
        db.recordingDao().deleteAll()
        db.patientSummaryDao().deleteAll()
    }

    suspend fun loadScenario(context: Context, scenarioIndex: Int) {
        val db = AppDatabase.getInstance(context)
        val medDao = db.medicationDao()
        val safetyDao = db.safetyCheckDao()

        clearAll(context)

        when (scenarioIndex) {
            1 -> seedPatientMrTanAhKow(db) // Primary Case: Mr Tan Ah Kow
            2 -> seedScenario2_Hypertension(medDao, safetyDao)
            3 -> seedScenario3_ElderlyCaretaker(medDao)
            4 -> { /* No Scenario: Starts empty, live scans/notes populate it cleanly */ }
        }
    }

    /**
     * Primary Patient Dossier: Mr Tan Ah Kow (Age 55, NRIC: S1111111X)
     * Seeds full multi-drug regimen, bilingual doctor consultation audio note,
     * safety cross-checks, and caretaker escalation settings.
     */
    suspend fun seedPatientMrTanAhKow(db: AppDatabase) {
        val medDao = db.medicationDao()
        val safetyDao = db.safetyCheckDao()
        val recDao = db.recordingDao()

        val caretakerContact = "+65 9123 4567"

        // 1. Lisinopril 10mg (Morning) — Hypertension & Renal protection
        val lisinopril = MedicationObject(
            id = "tan_lisinopril_10",
            name = "Lisinopril",
            strength = "10mg",
            dose = "1 tablet",
            frequency = "Once daily (Morning)",
            timing = "After breakfast with water",
            duration = "90 days",
            confidence = 0.98f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = listOf(
                MedicationConflict(
                    withMedId = "tan_amlodipine_5",
                    type = "INTERACTION",
                    detail = "Additive hypotensive effect when combined with Amlodipine. Patient should rise slowly from seated position."
                )
            ),
            reviewRecommended = true,
            schedule = listOf(ScheduleSlot(time = "08:00", slot = "Morning", withFood = true)),
            adherence = listOf(
                AdherenceRecord(date = todayIso(), status = "taken", slotTime = "08:00")
            ),
            summary = "Tamil: காலையில் உணவுக்குப் பின் 1 மாத்திரை | English: Blood pressure medication. Take 1 tablet every morning with breakfast.",
            sideEffects = listOf("Occasional dry cough", "Mild dizziness when standing"),
            visibleTo = listOf("patient", "caregiver", "doctor", "pharmacy"),
            sourceType = "voice",
            caretakerPhone = caretakerContact,
            callReminderStatus = "verified",
            consultationNotes = "Prescribed by Dr Tan Ah Moi for BP control (Target < 130/80)."
        )

        // 2. Amlodipine 5mg (Bedtime) — Calcium channel blocker for 24h BP coverage
        val amlodipine = MedicationObject(
            id = "tan_amlodipine_5",
            name = "Amlodipine Besylate",
            strength = "5mg",
            dose = "1 tablet",
            frequency = "Once daily (Bedtime)",
            timing = "Take at bedtime",
            duration = "90 days",
            confidence = 0.97f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = listOf(
                MedicationConflict(
                    withMedId = "tan_lisinopril_10",
                    type = "INTERACTION",
                    detail = "Dual antihypertensive combination with Lisinopril. Monitor blood pressure log."
                )
            ),
            reviewRecommended = true,
            schedule = listOf(ScheduleSlot(time = "21:00", slot = "Night", withFood = false)),
            adherence = listOf(
                AdherenceRecord(date = todayIso(), status = "pending", slotTime = "21:00")
            ),
            summary = "Tamil: இரவில் தூங்கும் முன் 1 மாத்திரை | English: Antihypertensive calcium channel blocker. Take 1 tablet at night.",
            sideEffects = listOf("Mild ankle swelling", "Flushing"),
            visibleTo = listOf("patient", "caregiver", "doctor", "pharmacy"),
            sourceType = "voice",
            caretakerPhone = caretakerContact,
            callReminderStatus = "verified",
            consultationNotes = "Evening dosing to achieve stable 24h ambulatory BP."
        )

        // 3. Donepezil 5mg (Bedtime) — Dementia cognitive symptom support
        val donepezil = MedicationObject(
            id = "tan_donepezil_5",
            name = "Donepezil HCl",
            strength = "5mg",
            dose = "1 tablet",
            frequency = "Once daily (Bedtime)",
            timing = "Take before sleep with assistance",
            duration = "90 days",
            confidence = 0.95f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = listOf(ScheduleSlot(time = "21:30", slot = "Night", withFood = false)),
            adherence = listOf(
                AdherenceRecord(date = todayIso(), status = "pending", slotTime = "21:30")
            ),
            summary = "Tamil: இரவில் டோனெபெசில் நினைவாற்றல் மாத்திரை | English: Cholinesterase inhibitor for dementia cognitive support. Requires son Ah Beng's administration.",
            sideEffects = listOf("Vivid dreams", "Nausea"),
            visibleTo = listOf("patient", "caregiver", "doctor"),
            sourceType = "prescription",
            caretakerPhone = caretakerContact,
            callReminderStatus = "verified",
            consultationNotes = "Mental Capacity Act assessment: Patient lacks capacity. Caretaker Ah Beng manages dosing."
        )

        // 4. Aspirin 75mg (Lunch) — Antiplatelet for stroke secondary prevention
        val aspirin = MedicationObject(
            id = "tan_aspirin_75",
            name = "Aspirin (Cardiprin)",
            strength = "75mg",
            dose = "1 tablet",
            frequency = "Once daily (Lunch)",
            timing = "Take after lunch",
            duration = "Ongoing",
            confidence = 0.99f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = listOf(ScheduleSlot(time = "13:00", slot = "Afternoon", withFood = true)),
            adherence = listOf(
                AdherenceRecord(date = todayIso(), status = "taken", slotTime = "13:00")
            ),
            summary = "Tamil: மதிய உணவுக்குப் பின் அஸ்பிரின் | English: Antiplatelet agent for secondary stroke prevention. Take with lunch.",
            sideEffects = listOf("Mild gastric irritation"),
            visibleTo = listOf("patient", "caregiver", "doctor", "pharmacy"),
            sourceType = "bill",
            caretakerPhone = caretakerContact,
            callReminderStatus = "verified",
            consultationNotes = "Post-stroke management following 2005/2010 episodes."
        )

        // 5. Atorvastatin 20mg (Bedtime) — Hyperlipidemia & vascular protection
        val atorvastatin = MedicationObject(
            id = "tan_atorvastatin_20",
            name = "Atorvastatin Calcium",
            strength = "20mg",
            dose = "1 tablet",
            frequency = "Once daily (Night)",
            timing = "Take at bedtime",
            duration = "90 days",
            confidence = 0.98f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = listOf(ScheduleSlot(time = "22:00", slot = "Night", withFood = false)),
            adherence = listOf(
                AdherenceRecord(date = todayIso(), status = "pending", slotTime = "22:00")
            ),
            summary = "Tamil: இரவில் கொலஸ்ட்ரால் மாத்திரை | English: Lipid-lowering statin for hyperlipidemia and stroke prevention.",
            sideEffects = listOf("Mild muscle ache"),
            visibleTo = listOf("patient", "caregiver", "doctor", "pharmacy"),
            sourceType = "prescription",
            caretakerPhone = caretakerContact,
            callReminderStatus = "verified",
            consultationNotes = "Target LDL-C < 1.8 mmol/L post ischemic stroke."
        )

        // Upsert all 5 medications to Room database
        medDao.upsertMedication(lisinopril.toEntity())
        medDao.upsertMedication(amlodipine.toEntity())
        medDao.upsertMedication(donepezil.toEntity())
        medDao.upsertMedication(aspirin.toEntity())
        medDao.upsertMedication(atorvastatin.toEntity())

        // Seed Safety Check record
        val safetyCheck = SafetyCheckEntity(
            id = UUID.randomUUID().toString(),
            medicineA = "Lisinopril 10mg",
            medicineB = "Amlodipine Besylate 5mg",
            medicineAStrength = "10mg",
            medicineBStrength = "5mg",
            findingType = "INTERACTION",
            result = "MODERATE_INTERACTION",
            severity = "MODERATE",
            aiTitle = "Additive Antihypertensive Effect",
            aiMessage = "Dual antihypertensive combination: additive blood pressure reduction. Patient should stand up slowly and report orthostatic lightheadedness.",
            aiRecommendation = "Monitor blood pressure weekly; caretaker to assist when patient stands up from bed.",
            crossVerified = true,
            reviewRecommended = true,
            createdAt = System.currentTimeMillis()
        )
        safetyDao.insertSafetyCheck(safetyCheck)

        // Seed Consultation Audio Recording (Bilingual Tamil & English)
        val recording = RecordingEntity(
            timestamp = System.currentTimeMillis() - 3600000L, // 1 hour ago
            filePath = "/data/user/0/com.medibridge/cache/consultation_tan_ah_kow.m4a",
            transcript = "Tamil:\nமருத்துவர் டான் ஆ மோய்: வணக்கம் திரு. டான் ஆ கோவ். உங்கள் இரத்த அழுத்தம் 148/92 ஆக உள்ளது. உங்களுக்கு லிசினோபிரில் (Lisinopril) 10mg காலையிலும், அம்லோடிபைன் (Amlodipine) 5mg இரவிலும் பரிந்துரைக்கிறேன். நினைவாற்றல் குறைபாட்டிற்கு டோனெபெசில் (Donepezil) 5mg மற்றும் ரத்த உறைவு தடுப்பிற்கு அஸ்பிரின் (Aspirin) 75mg தொடரவும். உங்கள் மகன் ஆ பெங் உங்களுக்கு மருந்துகளை சரியாக கொடுக்க வேண்டும்.\n\nEnglish:\nDr. Tan Ah Moi: Hello Mr. Tan Ah Kow. Your blood pressure is 148/92. I am prescribing Lisinopril 10mg in the morning and Amlodipine 5mg at bedtime. Continue Donepezil 5mg for dementia cognitive support and Aspirin 75mg for stroke secondary prevention. Your son Ah Beng will assist in administering your daily medications.",
            summary = "Tamil: மருத்துவர் டான் ஆ மோய் ஆலோசனை | English: Dr. Tan Ah Moi consultation. Dual antihypertensive therapy initiated + Dementia (Donepezil) and stroke secondary prevention (Aspirin). Caretaker Ah Beng managing medication administration."
        )
        recDao.insertRecording(recording)
    }

    /**
     * Scenario 2: Hypertension — Dual Antihypertensive Therapy
     */
    suspend fun seedScenario2_Hypertension(
        medDao: com.medibridge.core.db.MedicationDao,
        safetyDao: com.medibridge.moduleB_safety.data.SafetyCheckDao
    ) {
        val lisinopril = MedicationObject(
            id = "demo_lisinopril_10",
            name = "Lisinopril",
            strength = "10mg",
            dose = "1 tablet",
            frequency = "Once daily (Morning)",
            timing = "After breakfast",
            duration = "30 days",
            confidence = 0.95f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = listOf(
                MedicationConflict(
                    withMedId = "demo_amlodipine_5",
                    type = "INTERACTION",
                    detail = "Additive hypotensive effect when co-prescribed with Amlodipine."
                )
            ),
            reviewRecommended = true,
            schedule = listOf(ScheduleSlot(time = "08:00", slot = "Morning", withFood = true)),
            adherence = listOf(
                AdherenceRecord(date = todayIso(), status = "taken", slotTime = "08:00")
            ),
            summary = "ACE inhibitor prescribed for blood pressure regulation.",
            sideEffects = listOf("Occasional dry cough"),
            visibleTo = listOf("patient", "doctor", "pharmacy"),
            sourceType = "voice",
            caretakerPhone = "+65 9123 4567",
            callReminderStatus = "verified",
            consultationNotes = "Morning dosing initiated by clinical consultation."
        )

        val amlodipine = MedicationObject(
            id = "demo_amlodipine_5",
            name = "Amlodipine Besylate",
            strength = "5mg",
            dose = "1 tablet",
            frequency = "Once daily (Bedtime)",
            timing = "Take before sleep",
            duration = "30 days",
            confidence = 0.94f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = listOf(
                MedicationConflict(
                    withMedId = "demo_lisinopril_10",
                    type = "INTERACTION",
                    detail = "Additive blood pressure reduction; monitor for postural dizziness."
                )
            ),
            reviewRecommended = true,
            schedule = listOf(ScheduleSlot(time = "21:00", slot = "Night", withFood = false)),
            adherence = listOf(
                AdherenceRecord(date = todayIso(), status = "pending", slotTime = "21:00")
            ),
            summary = "Calcium channel blocker for 24-hour hypertension management.",
            sideEffects = listOf("Mild ankle edema", "Flushing"),
            visibleTo = listOf("patient", "doctor", "pharmacy"),
            sourceType = "voice",
            caretakerPhone = "+65 9123 4567",
            callReminderStatus = "verified",
            consultationNotes = "Evening dosing to achieve stable 24h ambulatory BP."
        )

        medDao.upsertMedication(lisinopril.toEntity())
        medDao.upsertMedication(amlodipine.toEntity())

        val safetyCheck = SafetyCheckEntity(
            id = UUID.randomUUID().toString(),
            medicineA = "Lisinopril",
            medicineB = "Amlodipine Besylate",
            medicineAStrength = "10mg",
            medicineBStrength = "5mg",
            findingType = "INTERACTION",
            result = "MODERATE_INTERACTION",
            severity = "MODERATE",
            aiTitle = "Additive Antihypertensive Effect",
            aiMessage = "Dual antihypertensive combination: additive blood pressure reduction.",
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
            caretakerPhone = "+65 9123 4567",
            callReminderStatus = "scheduled",
            consultationNotes = "Elderly care protocol: Auto-call caretaker if dose is unconfirmed."
        )

        medDao.upsertMedication(calcium.toEntity())
    }
}
