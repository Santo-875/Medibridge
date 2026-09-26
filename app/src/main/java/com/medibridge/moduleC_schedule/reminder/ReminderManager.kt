package com.medibridge.moduleC_schedule.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.medibridge.MainActivity
import com.medibridge.moduleC_schedule.engine.ScheduleEngine
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * ReminderManager — Responsible for scheduling, canceling, and displaying medication alarms.
 */
class ReminderManager(private val context: Context) {

    companion object {
        private const val TAG = "ReminderManager"
        const val CHANNEL_ID = "medication_reminders"
        const val CHANNEL_NAME = "Medication Reminders"

        const val EXTRA_OCCURRENCE_ID = "extra_occurrence_id"
        const val EXTRA_MEDICATION_ID = "extra_medication_id"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_DOSE = "extra_dose"
        const val EXTRA_SLOT_TIME = "extra_slot_time"
        const val EXTRA_DATE = "extra_date"

        const val ACTION_REMINDER_ALARM = "com.medibridge.ACTION_REMINDER_ALARM"
        const val ACTION_MARK_TAKEN = "com.medibridge.ACTION_MARK_TAKEN"
        const val ACTION_SNOOZE = "com.medibridge.ACTION_SNOOZE"

        /**
         * Creates a deterministic, stable identifier for a medication dose occurrence.
         */
        fun buildOccurrenceId(medicationId: String, slotTime: String, date: String): String {
            return "${medicationId}_${slotTime}_${date}"
        }

        /**
         * Generates a stable integer request code from the occurrence ID.
         */
        fun buildRequestCode(occurrenceId: String): Int {
            return occurrenceId.hashCode() and 0x7FFFFFFF
        }
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications reminding patients to take scheduled medications"
                enableVibration(true)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Schedules an alarm for a specific medication dose occurrence.
     */
    fun scheduleReminder(
        medicationId: String,
        medicineName: String,
        dose: String,
        slotTime: String,
        date: String,
        duration: String = "Ongoing",
        startDate: String = date
    ): Boolean {
        // Duration check: do not schedule alarms if medication is outside its active duration
        if (!ScheduleEngine.isMedicationActive(startDate, date, duration)) {
            Log.i(TAG, "Skipping reminder scheduling: $medicineName is not active on $date ($duration)")
            return false
        }

        val occurrenceId = buildOccurrenceId(medicationId, slotTime, date)
        val triggerEpochMs = calculateTriggerTimeMs(date, slotTime)

        // If time already passed, don't set an alarm in the past
        if (triggerEpochMs <= System.currentTimeMillis()) {
            return false
        }

        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = ACTION_REMINDER_ALARM
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_DOSE, dose)
            putExtra(EXTRA_SLOT_TIME, slotTime)
            putExtra(EXTRA_DATE, date)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            buildRequestCode(occurrenceId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
            } else {
                alarmManager?.setExact(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
            }
            Log.d(TAG, "Scheduled reminder for $medicineName ($occurrenceId) at $date $slotTime")
            return true
        } catch (e: SecurityException) {
            // If exact alarm permission is missing on Android 12+, fall back to standard alarm
            try {
                alarmManager?.set(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
                return true
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to schedule alarm for $occurrenceId", ex)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "AlarmManager error for $occurrenceId", e)
            return false
        }
    }

    /**
     * Reschedules an alarm for a snoozed medication dose occurrence.
     */
    fun scheduleSnooze(
        medicationId: String,
        medicineName: String,
        dose: String,
        slotTime: String,
        delayMinutes: Int = 15,
        date: String
    ): Boolean {
        val occurrenceId = buildOccurrenceId(medicationId, slotTime, date)
        val triggerEpochMs = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)

        // Cancel any active notification while snoozed
        cancelNotification(occurrenceId)

        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = ACTION_REMINDER_ALARM
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_DOSE, dose)
            putExtra(EXTRA_SLOT_TIME, slotTime)
            putExtra(EXTRA_DATE, date)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            buildRequestCode(occurrenceId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
            } else {
                alarmManager?.setExact(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
            }
            Log.d(TAG, "Snoozed $medicineName ($occurrenceId) for $delayMinutes minutes")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set snooze alarm for $occurrenceId", e)
            return false
        }
    }

    /**
     * Cancels any scheduled alarm for the given occurrence.
     */
    fun cancelReminder(medicationId: String, slotTime: String, date: String) {
        val occurrenceId = buildOccurrenceId(medicationId, slotTime, date)
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = ACTION_REMINDER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            buildRequestCode(occurrenceId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        cancelNotification(occurrenceId)
    }

    /**
     * Posts a notification for the scheduled medication reminder.
     */
    fun showNotification(
        occurrenceId: String,
        medicationId: String,
        medicineName: String,
        dose: String,
        slotTime: String,
        date: String
    ) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra("EXTRA_NAV_ROUTE", "reminders")
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            buildRequestCode(occurrenceId),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Taken Action
        val takenIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ACTION_MARK_TAKEN
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_SLOT_TIME, slotTime)
            putExtra(EXTRA_DATE, date)
        }
        val takenPendingIntent = PendingIntent.getBroadcast(
            context,
            buildRequestCode(occurrenceId) + 1,
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze Action (15 min)
        val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_DOSE, dose)
            putExtra(EXTRA_SLOT_TIME, slotTime)
            putExtra(EXTRA_DATE, date)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            buildRequestCode(occurrenceId) + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time for your medication")
            .setContentText("$medicineName - $dose")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Time to take your scheduled dose: $medicineName ($dose) at $slotTime."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "Taken", takenPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Snooze (15m)", snoozePendingIntent)
            .build()

        notificationManager?.notify(buildRequestCode(occurrenceId), notification)
    }

    /**
     * Cancels an active system notification for the given occurrence.
     */
    fun cancelNotification(occurrenceId: String) {
        notificationManager?.cancel(buildRequestCode(occurrenceId))
    }

    /**
     * Call reminder escalation: triggers Intent(ACTION_CALL) to caretakerPhone
     * and speaks the reminder text on-device via ReminderTtsHelper.
     * Note: In-call audio injection requires carrier/VoIP infrastructure; this prototype
     * initiates the call and plays the local TTS announcement on the initiating device.
     */
    fun triggerCaretakerCallEscalation(
        medicationId: String,
        medicineName: String,
        caretakerPhone: String,
        dose: String
    ) {
        try {
            val ttsHelper = com.medibridge.moduleC_schedule.tts.ReminderTtsHelper(context)
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = android.net.Uri.parse("tel:$caretakerPhone")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                ttsHelper.speakReminder(
                    medicineName = "MediBridge Caretaker Escalation. Patient missed dose for $medicineName",
                    dose = dose
                )
            }, 1000)
            Log.i(TAG, "Triggered caretaker call escalation to $caretakerPhone for $medicineName")
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_CALL failed (permission may not be granted): ${e.message}, falling back to dialer")
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = android.net.Uri.parse("tel:$caretakerPhone")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Dialer fallback failed: ${e2.message}")
            }
        }
    }

    private fun calculateTriggerTimeMs(dateStr: String, timeStr: String): Long {
        return try {
            val date = LocalDate.parse(dateStr)
            val parts = timeStr.split(":")
            val hour = parts[0].toInt()
            val minute = if (parts.size > 1) parts[1].toInt() else 0
            val time = LocalTime.of(hour, minute)
            val dateTime = date.atTime(time)
            dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
