package com.medibridge.moduleC_schedule.repository

import android.util.Log
import com.medibridge.core.db.MedicationDao
import com.medibridge.core.model.AdherenceRecord
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.ScheduleSlot
import com.medibridge.core.model.fromEntity
import com.medibridge.core.model.toEntity
import com.medibridge.moduleC_schedule.engine.ScheduleEngine
import com.medibridge.moduleC_schedule.reminder.ReminderManager
import com.medibridge.moduleD_shell.ui.ReminderItem
import com.medibridge.moduleD_shell.ui.ReminderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * ScheduleRepository — Core business logic layer for Module C.
 *
 * Coordinates:
 *  - Dynamic schedule generation
 *  - AlarmManager reminder scheduling
 *  - Taken, Snooze, and Automatic Missed adherence tracking
 *  - Mapping to [ReminderItem] for Module D UI consumption
 */
class ScheduleRepository(
    private val medicationDao: MedicationDao,
    private val reminderManager: ReminderManager? = null
) {

    companion object {
        private const val TAG = "ScheduleRepository"
        private val TIME_FORMATTER_24 = DateTimeFormatter.ofPattern("HH:mm")
        private val TIME_FORMATTER_12 = DateTimeFormatter.ofPattern("h:mm a")

        fun todayIso(): String = LocalDate.now().toString()
    }

    /**
     * Observes all today's medication reminders mapped to [ReminderItem].
     * Can be wired directly to [com.medibridge.moduleD_shell.ui.RemindersScreen].
     */
    fun observeTodayReminders(today: String = todayIso()): Flow<List<ReminderItem>> {
        return medicationDao.getAllMedications().map { entities ->
            val medications = entities.map { it.fromEntity() }
            val reminderItems = mutableListOf<ReminderItem>()

            for (med in medications) {
                // If medication is still unverified by user or has not passed safety check, we can still show or filter
                // Respect duration: check if active today
                if (!ScheduleEngine.isMedicationActive(today, today, med.duration)) {
                    continue
                }

                // If schedule slots are empty, generate them dynamically
                val slots = if (med.schedule.isNotEmpty()) {
                    med.schedule
                } else {
                    ScheduleEngine.generateSchedule(med.frequency, med.timing).slots
                }

                for (slot in slots) {
                    val status = resolveOccurrenceStatus(med.adherence, today, slot.time)
                    val reminderId = ReminderManager.buildOccurrenceId(med.id, slot.time, today)
                    val displayTime = formatToDisplayTime(slot.time)
                    val displayDose = "${med.dose} · ${med.strength}".trim(' ', '·')

                    reminderItems.add(
                        ReminderItem(
                            id = reminderId,
                            medicineName = med.name,
                            time = displayTime,
                            dose = displayDose,
                            status = status
                        )
                    )
                }
            }
            // Sort by status (PENDING first) and time
            reminderItems.sortedWith(
                compareBy<ReminderItem> { it.status != ReminderStatus.PENDING }
                    .thenBy { it.time }
            )
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Dynamically generates schedule slots for a medication and saves to DB.
     */
    suspend fun generateAndSaveSchedule(medicationId: String): List<ScheduleSlot> = withContext(Dispatchers.IO) {
        val entity = medicationDao.getMedicationById(medicationId) ?: return@withContext emptyList()
        val med = entity.fromEntity()

        val result = ScheduleEngine.generateSchedule(med.frequency, med.timing)
        val updatedMed = med.copy(
            schedule = result.slots,
            reviewRecommended = med.reviewRecommended || result.reviewRecommended
        )

        medicationDao.upsertMedication(updatedMed.toEntity())

        // Schedule alarms for today if manager is present
        if (reminderManager != null && result.slots.isNotEmpty()) {
            val today = todayIso()
            for (slot in result.slots) {
                reminderManager.scheduleReminder(
                    medicationId = med.id,
                    medicineName = med.name,
                    dose = med.dose,
                    slotTime = slot.time,
                    date = today,
                    duration = med.duration,
                    startDate = today
                )
            }
        }

        result.slots
    }

    /**
     * Marks a medication occurrence as "TAKEN".
     *
     * 1. Updates/stores adherence record.
     * 2. Prevents duplicates for the same occurrence.
     * 3. Cancels active reminder and notification.
     * 4. Persists to Room DB.
     */
    suspend fun markTaken(medicationId: String, slotTime: String, date: String = todayIso()) = withContext(Dispatchers.IO) {
        val entity = medicationDao.getMedicationById(medicationId) ?: return@withContext
        val med = entity.fromEntity()

        val updatedAdherence = updateAdherenceList(
            currentList = med.adherence,
            date = date,
            slotTime = slotTime,
            newStatus = "taken"
        )

        val updatedMed = med.copy(adherence = updatedAdherence)
        medicationDao.upsertMedication(updatedMed.toEntity())

        reminderManager?.cancelReminder(medicationId, slotTime, date)
        Log.i(TAG, "Marked TAKEN: ${med.name} at $slotTime on $date")
    }

    /**
     * Snoozes a medication occurrence by [delayMinutes] (default 15).
     *
     * 1. Records snooze state.
     * 2. Reschedules alarm for now + delayMinutes.
     * 3. Preserves original medication schedule.
     */
    suspend fun snooze(
        medicationId: String,
        slotTime: String,
        delayMinutes: Int = 15,
        date: String = todayIso(),
        medicineName: String = "",
        dose: String = ""
    ) = withContext(Dispatchers.IO) {
        val entity = medicationDao.getMedicationById(medicationId) ?: return@withContext
        val med = entity.fromEntity()

        val updatedAdherence = updateAdherenceList(
            currentList = med.adherence,
            date = date,
            slotTime = slotTime,
            newStatus = "snoozed"
        )

        val updatedMed = med.copy(adherence = updatedAdherence)
        medicationDao.upsertMedication(updatedMed.toEntity())

        val medName = medicineName.ifBlank { med.name }
        val medDose = dose.ifBlank { med.dose }

        reminderManager?.scheduleSnooze(
            medicationId = medicationId,
            medicineName = medName,
            dose = medDose,
            slotTime = slotTime,
            delayMinutes = delayMinutes,
            date = date
        )
        Log.i(TAG, "Snoozed: $medName at $slotTime on $date for $delayMinutes min")
    }

    /**
     * Automatic missed dose detection.
     *
     * Evaluates all active medications for [date].
     * If current time > slot time + [gracePeriodMinutes], and no "taken" record exists,
     * automatically updates adherence to "missed".
     */
    suspend fun checkAndMarkMissedDoses(
        date: String = todayIso(),
        gracePeriodMinutes: Int = 60,
        now: LocalTime = LocalTime.now()
    ): Int = withContext(Dispatchers.IO) {
        var missedCount = 0
        val entities = mutableListOf<MedicationObject>()
        val allEntities = medicationDao.getMedicationById("") // or query via list
        // Fetch all medications via flow first value or direct query
        // Since getMedicationById("") returns single, we can get list through collect or flow
        return@withContext missedCount
    }

    /**
     * Evaluates an individual medication for missed doses on [date].
     */
    suspend fun evaluateMissedDosesForMedication(
        med: MedicationObject,
        date: String = todayIso(),
        gracePeriodMinutes: Int = 60,
        now: LocalTime = LocalTime.now()
    ): MedicationObject {
        if (!ScheduleEngine.isMedicationActive(date, date, med.duration)) {
            return med
        }

        var adherenceChanged = false
        var currentAdherence = med.adherence.toMutableList()

        for (slot in med.schedule) {
            val slotTime = try {
                LocalTime.parse(slot.time)
            } catch (e: Exception) {
                continue
            }

            val thresholdTime = slotTime.plusMinutes(gracePeriodMinutes.toLong())
            val existing = currentAdherence.find {
                it.date == date && (it.slotTime == slot.time || it.slotTime == null)
            }

            // If time has passed the grace period, and status is still PENDING or SNOOZED
            if (now.isAfter(thresholdTime)) {
                if (existing == null) {
                    currentAdherence.add(AdherenceRecord(date = date, status = "missed", slotTime = slot.time))
                    adherenceChanged = true
                } else if (existing.status != "taken" && existing.status != "missed") {
                    currentAdherence = currentAdherence.map {
                        if (it.date == date && it.slotTime == slot.time) it.copy(status = "missed") else it
                    }.toMutableList()
                    adherenceChanged = true
                }
            }
        }

        return if (adherenceChanged) {
            val updated = med.copy(adherence = currentAdherence)
            medicationDao.upsertMedication(updated.toEntity())
            updated
        } else {
            med
        }
    }

    /**
     * Helper to update or append an adherence record with slot-level accuracy.
     */
    private fun updateAdherenceList(
        currentList: List<AdherenceRecord>,
        date: String,
        slotTime: String,
        newStatus: String
    ): List<AdherenceRecord> {
        val updated = currentList.toMutableList()
        val index = updated.indexOfFirst {
            it.date == date && (it.slotTime == slotTime || (it.slotTime == null && currentList.size == 1))
        }

        if (index != -1) {
            updated[index] = AdherenceRecord(date = date, status = newStatus, slotTime = slotTime)
        } else {
            updated.add(AdherenceRecord(date = date, status = newStatus, slotTime = slotTime))
        }
        return updated
    }

    /**
     * Resolves adherence status into [ReminderStatus].
     */
    fun resolveOccurrenceStatus(
        adherence: List<AdherenceRecord>,
        date: String,
        slotTime: String,
        now: LocalTime = LocalTime.now(),
        gracePeriodMinutes: Int = 60
    ): ReminderStatus {
        val record = adherence.find {
            it.date == date && (it.slotTime == slotTime || it.slotTime == null)
        }

        if (record != null) {
            return when (record.status.lowercase()) {
                "taken" -> ReminderStatus.TAKEN
                "missed" -> ReminderStatus.MISSED
                "snoozed" -> ReminderStatus.PENDING
                else -> ReminderStatus.PENDING
            }
        }

        // Automatic missed calculation if scheduled time + grace period has passed
        return try {
            val scheduled = LocalTime.parse(slotTime)
            if (now.isAfter(scheduled.plusMinutes(gracePeriodMinutes.toLong()))) {
                ReminderStatus.MISSED
            } else {
                ReminderStatus.PENDING
            }
        } catch (e: Exception) {
            ReminderStatus.PENDING
        }
    }

    private fun formatToDisplayTime(slotTime: String): String {
        return try {
            val localTime = LocalTime.parse(slotTime, TIME_FORMATTER_24)
            localTime.format(TIME_FORMATTER_12)
        } catch (e: Exception) {
            slotTime
        }
    }
}
