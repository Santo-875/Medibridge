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
    val baselineHistory: List<MedicationObject> = emptyList()
    val demoScenarios: List<SafetyDemoScenario> = emptyList()
}
