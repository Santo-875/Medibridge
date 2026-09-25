package com.medibridge.moduleC_schedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.medibridge.core.db.AppDatabase
import com.medibridge.moduleC_schedule.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * MedicationReminderReceiver — Triggered by AlarmManager when a scheduled reminder is due.
 */
class MedicationReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MedicationReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getStringExtra(ReminderManager.EXTRA_OCCURRENCE_ID) ?: return
        val medicationId = intent.getStringExtra(ReminderManager.EXTRA_MEDICATION_ID) ?: return
        val medicineName = intent.getStringExtra(ReminderManager.EXTRA_MEDICINE_NAME) ?: "Medication"
        val dose = intent.getStringExtra(ReminderManager.EXTRA_DOSE) ?: ""
        val slotTime = intent.getStringExtra(ReminderManager.EXTRA_SLOT_TIME) ?: ""
        val date = intent.getStringExtra(ReminderManager.EXTRA_DATE) ?: ""

        Log.d(TAG, "Alarm triggered for occurrence: $occurrenceId ($medicineName)")

        val reminderManager = ReminderManager(context)
        reminderManager.showNotification(
            occurrenceId = occurrenceId,
            medicationId = medicationId,
            medicineName = medicineName,
            dose = dose,
            slotTime = slotTime,
            date = date
        )
    }
}

/**
 * ReminderActionReceiver — Handles direct actions ("Taken", "Snooze") from the notification.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val occurrenceId = intent.getStringExtra(ReminderManager.EXTRA_OCCURRENCE_ID) ?: return
        val medicationId = intent.getStringExtra(ReminderManager.EXTRA_MEDICATION_ID) ?: return
        val slotTime = intent.getStringExtra(ReminderManager.EXTRA_SLOT_TIME) ?: ""
        val date = intent.getStringExtra(ReminderManager.EXTRA_DATE) ?: ""

        val reminderManager = ReminderManager(context)
        val db = AppDatabase.getInstance(context)
        val repository = ScheduleRepository(db.medicationDao(), reminderManager)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ReminderManager.ACTION_MARK_TAKEN -> {
                        Log.d(TAG, "Notification action: Taken for $occurrenceId")
                        repository.markTaken(medicationId, slotTime, date)
                    }
                    ReminderManager.ACTION_SNOOZE -> {
                        val medicineName = intent.getStringExtra(ReminderManager.EXTRA_MEDICINE_NAME) ?: "Medication"
                        val dose = intent.getStringExtra(ReminderManager.EXTRA_DOSE) ?: ""
                        Log.d(TAG, "Notification action: Snooze for $occurrenceId")
                        repository.snooze(
                            medicationId = medicationId,
                            slotTime = slotTime,
                            delayMinutes = 15,
                            date = date,
                            medicineName = medicineName,
                            dose = dose
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle notification action $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
