package com.medibridge.moduleC_schedule.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.medibridge.core.db.AppDatabase
import com.medibridge.moduleC_schedule.reminder.ReminderManager
import com.medibridge.moduleC_schedule.repository.ScheduleRepository
import java.util.concurrent.TimeUnit

/**
 * MissedDoseWorker — periodic background worker for automatic missed dose evaluation.
 *
 * Runs every 30 minutes in the background to check whether any scheduled medication doses
 * have passed their grace period without being marked "taken", flips their status to "missed",
 * and registers an in-app alert notification.
 */
class MissedDoseWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Executing periodic MissedDoseWorker run...")
            val db = AppDatabase.getInstance(applicationContext)
            val repo = ScheduleRepository(
                medicationDao = db.medicationDao(),
                reminderManager = ReminderManager(applicationContext),
                notificationDao = db.notificationDao()
            )
            val count = repo.checkAndMarkMissedDoses()
            Log.i(TAG, "MissedDoseWorker completed. Detected and updated $count missed dose(s).")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "MissedDoseWorker failed during execution", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MissedDoseWorker"
        const val WORK_NAME = "periodic_missed_dose_check"

        /**
         * Enqueues the 30-minute periodic worker using WorkManager.
         */
        fun enqueuePeriodicWork(context: Context) {
            try {
                val workRequest = PeriodicWorkRequestBuilder<MissedDoseWorker>(
                    repeatInterval = 30,
                    repeatIntervalTimeUnit = TimeUnit.MINUTES
                ).build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
                Log.i(TAG, "Periodic missed dose check scheduled via WorkManager (30m interval)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue MissedDoseWorker", e)
            }
        }
    }
}
