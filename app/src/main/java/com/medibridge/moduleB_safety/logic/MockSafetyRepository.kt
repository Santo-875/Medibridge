package com.medibridge.moduleB_safety.logic

import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.ScheduleSlot

/**
 * Demo scenarios to test and present all requirements of Module B.
 */
data class SafetyDemoScenario(
    val id: String,
    val title: String,
    val subtitle: String,
    val candidateMedication: MedicationObject
)

/**
 * Dedicated mock medication repository for Module B.
 *
 * Provides a realistic 6-10 medication history and predefined demo cases
 * covering Safe, Duplicate, Overlap, Interaction, and Unknown drugs.
 */
object MockSafetyRepository {

    /**
     * Baseline active medication history (7 items).
     */
    val baselineHistory: List<MedicationObject> = listOf(
        MedicationObject(
            id = "hist-001",
            name = "Metformin",
            strength = "500mg",
            dose = "1 tablet",
            frequency = "Twice daily",
            timing = "After meals",
            duration = "90 days",
            confidence = 0.98f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = listOf(
                ScheduleSlot("08:00", "Morning", true),
                ScheduleSlot("20:00", "Night", true)
            ),
            adherence = emptyList(),
            summary = "Oral antidiabetic medication for blood sugar control.",
            sideEffects = listOf("Mild nausea", "Stomach upset"),
            visibleTo = listOf("patient", "doctor")
        ),
        MedicationObject(
            id = "hist-002",
            name = "Amlodipine",
            strength = "5mg",
            dose = "1 tablet",
            frequency = "Once daily",
            timing = "Morning",
            duration = "Ongoing",
            confidence = 0.95f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = listOf(ScheduleSlot("09:00", "Morning", false)),
            adherence = emptyList(),
            summary = "Calcium channel blocker for blood pressure.",
            sideEffects = listOf("Ankle swelling", "Flushing"),
            visibleTo = listOf("patient", "doctor")
        ),
        MedicationObject(
            id = "hist-003",
            name = "Lisinopril",
            strength = "10mg",
            dose = "1 tablet",
            frequency = "Once daily",
            timing = "Evening",
            duration = "Ongoing",
            confidence = 0.92f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = listOf(ScheduleSlot("20:00", "Night", false)),
            adherence = emptyList(),
            summary = "ACE inhibitor for blood pressure management.",
            sideEffects = listOf("Dry cough", "Dizziness"),
            visibleTo = listOf("patient", "doctor")
        ),
        MedicationObject(
            id = "hist-004",
            name = "Vitamin D3",
            strength = "1000 IU",
            dose = "1 softgel",
            frequency = "Once daily",
            timing = "After breakfast",
            duration = "Ongoing",
            confidence = 0.99f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = listOf(ScheduleSlot("09:00", "Morning", true)),
            adherence = emptyList(),
            summary = "Nutritional supplement supporting bone and immune health.",
            sideEffects = listOf("None noted at therapeutic dose"),
            visibleTo = listOf("patient")
        ),
        MedicationObject(
            id = "hist-005",
            name = "Warfarin",
            strength = "2.5mg",
            dose = "1 tablet",
            frequency = "Once daily",
            timing = "Night",
            duration = "Ongoing",
            confidence = 0.96f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = listOf(ScheduleSlot("21:00", "Night", false)),
            adherence = emptyList(),
            summary = "Anticoagulant blood thinner to prevent clot formation.",
            sideEffects = listOf("Bleeding tendency", "Bruising"),
            visibleTo = listOf("patient", "doctor")
        ),
        MedicationObject(
            id = "hist-006",
            name = "Omeprazole",
            strength = "20mg",
            dose = "1 capsule",
            frequency = "Once daily",
            timing = "Before breakfast",
            duration = "30 days",
            confidence = 0.94f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = listOf(ScheduleSlot("07:30", "Morning", false)),
            adherence = emptyList(),
            summary = "Proton pump inhibitor reducing stomach acid.",
            sideEffects = listOf("Headache", "Abdominal discomfort"),
            visibleTo = listOf("patient", "doctor")
        ),
        MedicationObject(
            id = "hist-007",
            name = "Metoprolol",
            strength = "25mg",
            dose = "1 tablet",
            frequency = "Twice daily",
            timing = "With meals",
            duration = "Ongoing",
            confidence = 0.91f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = true,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = listOf(
                ScheduleSlot("08:30", "Morning", true),
                ScheduleSlot("20:30", "Night", true)
            ),
            adherence = emptyList(),
            summary = "Cardioselective beta-blocker for heart rate control.",
            sideEffects = listOf("Fatigue", "Cold extremities"),
            visibleTo = listOf("patient", "doctor")
        )
    )

    /**
     * 5 Demo Scenarios explicitly supporting the Module B specification:
     * 1. Safe medication
     * 2. Duplicate medication
     * 3. Overlap medication
     * 4. Interaction conflict
     * 5. Unknown / unverified medication
     */
    val demoScenarios: List<SafetyDemoScenario> = listOf(
        SafetyDemoScenario(
            id = "case-1",
            title = "Case 1: Safe",
            subtitle = "Atorvastatin (No conflicts)",
            candidateMedication = MedicationObject(
                id = "demo-001",
                name = "Atorvastatin",
                strength = "20mg",
                dose = "1 tablet",
                frequency = "Once daily",
                timing = "At bedtime",
                duration = "90 days",
                confidence = 0.95f,
                needsVerification = false,
                verifiedByUser = true,
                crossVerified = false,
                conflicts = emptyList(),
                reviewRecommended = false,
                schedule = listOf(ScheduleSlot("22:00", "Night", false)),
                adherence = emptyList(),
                summary = "Lipid-lowering statin for cholesterol control.",
                sideEffects = listOf("Mild muscle soreness", "Headache"),
                visibleTo = listOf("patient", "doctor")
            )
        ),
        SafetyDemoScenario(
            id = "case-2",
            title = "Case 2: Duplicate",
            subtitle = "Metformin (Already prescribed)",
            candidateMedication = MedicationObject(
                id = "demo-002",
                name = "Metformin",
                strength = "500mg",
                dose = "1 tablet",
                frequency = "Twice daily",
                timing = "After food",
                duration = "30 days",
                confidence = 0.97f,
                needsVerification = false,
                verifiedByUser = true,
                crossVerified = false,
                conflicts = emptyList(),
                reviewRecommended = false,
                schedule = listOf(ScheduleSlot("08:00", "Morning", true)),
                adherence = emptyList(),
                summary = "Blood sugar control. Redundant with existing prescription.",
                sideEffects = listOf("GI upset"),
                visibleTo = listOf("patient", "doctor")
            )
        ),
        SafetyDemoScenario(
            id = "case-3",
            title = "Case 3: Overlap",
            subtitle = "Enalapril (ACE inhibitor overlap)",
            candidateMedication = MedicationObject(
                id = "demo-003",
                name = "Enalapril",
                strength = "10mg",
                dose = "1 tablet",
                frequency = "Once daily",
                timing = "Morning",
                duration = "60 days",
                confidence = 0.93f,
                needsVerification = false,
                verifiedByUser = true,
                crossVerified = false,
                conflicts = emptyList(),
                reviewRecommended = false,
                schedule = listOf(ScheduleSlot("09:00", "Morning", false)),
                adherence = emptyList(),
                summary = "ACE inhibitor antihypertensive. Overlaps with Lisinopril.",
                sideEffects = listOf("Dizziness", "Hypotension"),
                visibleTo = listOf("patient", "doctor")
            )
        ),
        SafetyDemoScenario(
            id = "case-4",
            title = "Case 4: Interaction",
            subtitle = "Aspirin (Interacts with Warfarin)",
            candidateMedication = MedicationObject(
                id = "demo-004",
                name = "Aspirin",
                strength = "75mg",
                dose = "1 tablet",
                frequency = "Once daily",
                timing = "With lunch",
                duration = "Ongoing",
                confidence = 0.96f,
                needsVerification = false,
                verifiedByUser = true,
                crossVerified = false,
                conflicts = emptyList(),
                reviewRecommended = false,
                schedule = listOf(ScheduleSlot("13:00", "Afternoon", true)),
                adherence = emptyList(),
                summary = "Antiplatelet agent. High bleeding risk with Warfarin.",
                sideEffects = listOf("Gastrointestinal bleeding risk"),
                visibleTo = listOf("patient", "doctor")
            )
        ),
        SafetyDemoScenario(
            id = "case-5",
            title = "Case 5: Unknown",
            subtitle = "Xylozen-99 (Unverified drug)",
            candidateMedication = MedicationObject(
                id = "demo-005",
                name = "Xylozen-99",
                strength = "100mg",
                dose = "1 capsule",
                frequency = "Once daily",
                timing = "Morning",
                duration = "14 days",
                confidence = 0.60f,
                needsVerification = true,
                verifiedByUser = false,
                crossVerified = false,
                conflicts = emptyList(),
                reviewRecommended = false,
                schedule = listOf(ScheduleSlot("08:00", "Morning", false)),
                adherence = emptyList(),
                summary = "Unrecognized drug formula requiring verification.",
                sideEffects = listOf("Unknown"),
                visibleTo = listOf("patient")
            )
        )
    )
}
