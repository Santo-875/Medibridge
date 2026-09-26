package com.medibridge.moduleC_schedule

import com.medibridge.core.model.AdherenceRecord
import com.medibridge.moduleC_schedule.engine.ScheduleEngine
import com.medibridge.moduleC_schedule.repository.ScheduleRepository
import com.medibridge.moduleD_shell.ui.ReminderStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleEngineTest {

    // ── Frequency & Timing Tests ──────────────────────────────────────────────

    @Test
    fun `test once daily schedule generation`() {
        val result = ScheduleEngine.generateSchedule("Once daily", "In the morning")
        assertFalse("Review should not be recommended for once daily", result.reviewRecommended)
        assertEquals(1, result.slots.size)
        assertEquals("08:00", result.slots[0].time)
        assertEquals("Morning", result.slots[0].slot)
    }

    @Test
    fun `test once daily at bedtime`() {
        val result = ScheduleEngine.generateSchedule("Once daily", "Before bed")
        assertFalse(result.reviewRecommended)
        assertEquals(1, result.slots.size)
        assertEquals("22:00", result.slots[0].time)
        assertEquals("Night", result.slots[0].slot)
        assertFalse(result.slots[0].withFood)
    }

    @Test
    fun `test twice daily schedule generation`() {
        val result = ScheduleEngine.generateSchedule("Twice daily", "After breakfast and after dinner")
        assertFalse(result.reviewRecommended)
        assertEquals(2, result.slots.size)
        assertEquals("08:30", result.slots[0].time)
        assertEquals("Morning", result.slots[0].slot)
        assertTrue("After breakfast requires food", result.slots[0].withFood)

        assertEquals("20:30", result.slots[1].time)
        assertEquals("Night", result.slots[1].slot)
        assertTrue("After dinner requires food", result.slots[1].withFood)
    }

    @Test
    fun `test three times daily schedule generation`() {
        val result = ScheduleEngine.generateSchedule("Three times daily", "After meals")
        assertFalse(result.reviewRecommended)
        assertEquals(3, result.slots.size)
        assertTrue(result.slots.all { it.withFood })
        assertEquals("Morning", result.slots[0].slot)
        assertEquals("Afternoon", result.slots[1].slot)
        assertEquals("Night", result.slots[2].slot)
    }

    @Test
    fun `test four times daily schedule generation`() {
        val result = ScheduleEngine.generateSchedule("4 times daily", "With meals")
        assertFalse(result.reviewRecommended)
        assertEquals(4, result.slots.size)
        assertEquals(listOf("Morning", "Afternoon", "Evening", "Night"), result.slots.map { it.slot })
    }

    @Test
    fun `test every 8 hours interval`() {
        val result = ScheduleEngine.generateSchedule("Every 8 hours", "With water")
        assertFalse(result.reviewRecommended)
        assertEquals(3, result.slots.size)
        assertEquals("00:00", result.slots[0].time)
        assertEquals("08:00", result.slots[1].time)
        assertEquals("16:00", result.slots[2].time)
    }

    @Test
    fun `test explicit morning and night combination`() {
        val result = ScheduleEngine.generateSchedule("Morning and night", "Take with water")
        assertFalse(result.reviewRecommended)
        assertEquals(2, result.slots.size)
        assertEquals("08:00", result.slots[0].time)
        assertEquals("20:00", result.slots[1].time)
    }

    @Test
    fun `test before breakfast on empty stomach`() {
        val result = ScheduleEngine.generateSchedule("Once daily", "Before breakfast on an empty stomach")
        assertFalse(result.reviewRecommended)
        assertEquals(1, result.slots.size)
        assertEquals("07:30", result.slots[0].time)
        assertFalse("Empty stomach should have withFood=false", result.slots[0].withFood)
    }

    @Test
    fun `test ambiguous unknown instruction recommends review and does not invent times`() {
        val result = ScheduleEngine.generateSchedule("As needed when symptoms occur", "PRN")
        assertTrue("Ambiguous instructions must recommend review", result.reviewRecommended)
        assertTrue("Must not invent slots for ambiguous directives", result.slots.isEmpty())
        assertNotNull(result.note)
    }

    // ── Duration Tests ────────────────────────────────────────────────────────

    @Test
    fun `test finite duration parsing`() {
        assertEquals(5, ScheduleEngine.parseDurationDays("5 days"))
        assertEquals(7, ScheduleEngine.parseDurationDays("7 days"))
        assertEquals(7, ScheduleEngine.parseDurationDays("1 week"))
        assertEquals(14, ScheduleEngine.parseDurationDays("2 weeks"))
        assertEquals(30, ScheduleEngine.parseDurationDays("30 days"))
        assertEquals(30, ScheduleEngine.parseDurationDays("1 month"))
        assertEquals(90, ScheduleEngine.parseDurationDays("3 months"))
    }

    @Test
    fun `test ongoing medication duration`() {
        assertNull(ScheduleEngine.parseDurationDays("Ongoing"))
        assertNull(ScheduleEngine.parseDurationDays("Continuous"))
        assertNull(ScheduleEngine.parseDurationDays("Chronic"))
    }

    @Test
    fun `test isMedicationActive with finite and ongoing duration`() {
        val startDate = "2026-09-20"

        // Within 7 days
        assertTrue(ScheduleEngine.isMedicationActive(startDate, "2026-09-24", "7 days"))
        // After 7 days expired
        assertFalse(ScheduleEngine.isMedicationActive(startDate, "2026-09-28", "7 days"))

        // Ongoing is always active
        assertTrue(ScheduleEngine.isMedicationActive(startDate, "2026-12-31", "Ongoing"))
    }

    // ── Adherence Status Tests ────────────────────────────────────────────────

    @Test
    fun `test taken adherence status resolution`() {
        val adherence = listOf(
            AdherenceRecord(date = "2026-09-25", status = "taken", slotTime = "08:00")
        )
        val repo = ScheduleRepository(DummyMedicationDao())
        val status = repo.resolveOccurrenceStatus(
            adherence = adherence,
            date = "2026-09-25",
            slotTime = "08:00",
            now = LocalTime.of(12, 0)
        )
        assertEquals(ReminderStatus.TAKEN, status)
    }

    @Test
    fun `test snoozed adherence status resolves to pending`() {
        val adherence = listOf(
            AdherenceRecord(date = "2026-09-25", status = "snoozed", slotTime = "08:00")
        )
        val repo = ScheduleRepository(DummyMedicationDao())
        val status = repo.resolveOccurrenceStatus(
            adherence = adherence,
            date = "2026-09-25",
            slotTime = "08:00",
            now = LocalTime.of(8, 10)
        )
        assertEquals(ReminderStatus.PENDING, status)
    }

    @Test
    fun `test automatic missed detection when grace period elapsed`() {
        val adherence = emptyList<AdherenceRecord>()
        val repo = ScheduleRepository(DummyMedicationDao())

        // Scheduled at 08:00, current time is 09:30 (exceeds 60m grace period)
        val status = repo.resolveOccurrenceStatus(
            adherence = adherence,
            date = "2026-09-25",
            slotTime = "08:00",
            now = LocalTime.of(9, 30),
            gracePeriodMinutes = 60
        )
        assertEquals(ReminderStatus.MISSED, status)
    }

    @Test
    fun `test pending status before scheduled time plus grace period`() {
        val adherence = emptyList<AdherenceRecord>()
        val repo = ScheduleRepository(DummyMedicationDao())

        // Scheduled at 08:00, current time is 08:15 (within grace period)
        val status = repo.resolveOccurrenceStatus(
            adherence = adherence,
            date = "2026-09-25",
            slotTime = "08:00",
            now = LocalTime.of(8, 15),
            gracePeriodMinutes = 60
        )
        assertEquals(ReminderStatus.PENDING, status)
    }
}

// Dummy DAO implementation for unit testing ScheduleRepository adherence logic
private class DummyMedicationDao : com.medibridge.core.db.MedicationDao {
    override fun getAllMedications() = kotlinx.coroutines.flow.flowOf(emptyList<com.medibridge.core.db.MedicationEntity>())
    override suspend fun getMedicationById(id: String) = null
    override suspend fun upsertMedication(medication: com.medibridge.core.db.MedicationEntity) {}
    override suspend fun upsertAll(medications: List<com.medibridge.core.db.MedicationEntity>) {}
    override suspend fun deleteMedication(medication: com.medibridge.core.db.MedicationEntity) {}
    override suspend fun deleteAll() {}
    override fun getMedicationsNeedingVerification() = kotlinx.coroutines.flow.flowOf(emptyList<com.medibridge.core.db.MedicationEntity>())
    override fun getMedicationsWithConflicts() = kotlinx.coroutines.flow.flowOf(emptyList<com.medibridge.core.db.MedicationEntity>())
}
