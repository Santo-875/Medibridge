package com.medibridge.moduleC_schedule

import android.content.Context
import com.medibridge.core.db.AppDatabase
import com.medibridge.moduleC_schedule.ai.PatientSummaryService
import com.medibridge.moduleC_schedule.ai.PrivacySummaryService
import com.medibridge.moduleC_schedule.ai.SideEffectService
import com.medibridge.moduleC_schedule.engine.ScheduleEngine
import com.medibridge.moduleC_schedule.reminder.ReminderManager
import com.medibridge.moduleC_schedule.repository.ScheduleRepository
import com.medibridge.moduleC_schedule.tts.ReminderTtsHelper

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * MODULE C — Schedule Engine, Adherence Tracker & Gemini AI Services
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Implemented components:
 *   1. [ScheduleEngine] — Dynamic prescription frequency & timing parser into [ScheduleSlot].
 *   2. [ReminderManager] — AlarmManager & NotificationManager reminder scheduling.
 *   3. [ScheduleRepository] — Adherence tracking (Taken / Snooze / Auto-Missed).
 *   4. [SideEffectService] — Gemini AI structured side-effect retriever.
 *   5. [PatientSummaryService] — Gemini AI patient summary generator & Room DB storage.
 *   6. [PrivacySummaryService] — Gemini AI role-based privacy content generator.
 *   7. [ReminderTtsHelper] — On-device English and Tamil voice reminders.
 *   8. [viewmodel.ScheduleViewModel] — Bridge for Module D RemindersScreen integration.
 */
class ModuleC(context: Context) {
    val database = AppDatabase.getInstance(context)
    val reminderManager = ReminderManager(context)
    val scheduleRepository = ScheduleRepository(database.medicationDao(), reminderManager)
    val sideEffectService = SideEffectService()
    val patientSummaryService = PatientSummaryService(database.patientSummaryDao(), sideEffectService)
    val privacySummaryService = PrivacySummaryService()
    val ttsHelper = ReminderTtsHelper(context)
}
