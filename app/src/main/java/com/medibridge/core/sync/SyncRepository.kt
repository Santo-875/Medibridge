package com.medibridge.core.sync

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.app.NotificationCompat
import com.medibridge.MainActivity
import com.medibridge.core.db.*
import com.medibridge.core.network.*
import com.medibridge.core.session.UserSessionManager
import com.medibridge.moduleB_safety.data.SafetyCheckEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.OffsetDateTime

enum class SyncState {
    IDLE,
    SYNCING,
    OFFLINE
}

class SyncRepository private constructor(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val sessionManager = UserSessionManager.getInstance(context)
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var debounceJob: Job? = null

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun scheduleSyncDebounced(delayMs: Long = 1500L) {
        debounceJob?.cancel()
        debounceJob = repositoryScope.launch {
            delay(delayMs)
            syncNow()
        }
    }

    suspend fun syncNow(): Boolean = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            _syncState.value = SyncState.OFFLINE
            return@withContext false
        }

        _syncState.value = SyncState.SYNCING
        val api = BackendClient.getService()

        try {
            sessionManager.ensureInitialized()

            // ── 1. Push Local Unsynced Changes ───────────────────────────────
            val unsyncedProfiles = db.profileDao().getUnsyncedProfiles()
            val unsyncedMeds = db.medicationDao().getUnsyncedMedications()
            val unsyncedTimes = db.doseTimeDao().getUnsyncedDoseTimes()
            val unsyncedLogs = db.doseLogDao().getUnsyncedDoseLogs()
            val unsyncedLinks = db.careLinkDao().getUnsyncedCareLinks()
            val unsyncedRecordings = db.recordingDao().getUnsyncedRecordings()
            val unsyncedSafety = db.safetyCheckDao().getUnsyncedSafetyChecks()
            val unsyncedNotifs = db.notificationDao().getUnsyncedNotifications()

            val hasChanges = unsyncedProfiles.isNotEmpty() || unsyncedMeds.isNotEmpty() ||
                    unsyncedTimes.isNotEmpty() || unsyncedLogs.isNotEmpty() ||
                    unsyncedLinks.isNotEmpty() || unsyncedRecordings.isNotEmpty() ||
                    unsyncedSafety.isNotEmpty() || unsyncedNotifs.isNotEmpty()

            if (hasChanges) {
                val pushPayload = SyncPushRequest(
                    profiles = unsyncedProfiles.map {
                        ProfileDto(
                            id = it.id,
                            ownerUserId = it.ownerUserId,
                            name = it.name,
                            avatar = it.avatar,
                            createdAt = toIso(it.createdAt),
                            updatedAt = toIso(it.updatedAt),
                            deletedAt = toIso(it.deletedAt)
                        )
                    },
                    medications = unsyncedMeds.map {
                        MedicationDto(
                            id = it.id,
                            profileId = it.profileId,
                            name = it.name,
                            dosage = it.dose,
                            strength = it.strength,
                            timing = it.timing,
                            foodRule = "Take with food",
                            durationDays = 30,
                            source = it.sourceType,
                            rxcui = null,
                            createdAt = toIso(it.updatedAt),
                            updatedAt = toIso(it.updatedAt),
                            deletedAt = toIso(it.deletedAt)
                        )
                    },
                    doseTimes = unsyncedTimes.map {
                        DoseTimeDto(
                            id = it.id,
                            medicationId = it.medicationId,
                            profileId = it.profileId,
                            time = it.time,
                            slot = it.slot,
                            createdAt = toIso(it.createdAt),
                            updatedAt = toIso(it.updatedAt),
                            deletedAt = toIso(it.deletedAt)
                        )
                    },
                    doseLogs = unsyncedLogs.map {
                        DoseLogDto(
                            id = it.id,
                            profileId = it.profileId,
                            medicationId = it.medicationId,
                            doseTimeId = it.doseTimeId,
                            scheduledTime = it.scheduledTime,
                            status = it.status,
                            takenAt = toIso(it.takenAt),
                            snoozeUntil = toIso(it.snoozeUntil),
                            createdAt = toIso(it.createdAt),
                            updatedAt = toIso(it.updatedAt),
                            deletedAt = toIso(it.deletedAt)
                        )
                    },
                    careLinks = unsyncedLinks.map {
                        CareLinkDto(
                            id = it.id,
                            profileId = it.profileId,
                            caretakerUserId = it.caretakerUserId,
                            permission = it.permission,
                            status = it.status,
                            inviteCode = it.inviteCode,
                            createdAt = toIso(it.createdAt),
                            updatedAt = toIso(it.updatedAt),
                            deletedAt = toIso(it.deletedAt)
                        )
                    },
                    recordings = unsyncedRecordings.map {
                        RecordingDto(
                            id = it.uuid,
                            profileId = it.profileId,
                            filePath = it.filePath,
                            transcriptTamil = null,
                            transcriptEnglish = it.transcript,
                            summaryJson = it.summary,
                            createdAt = toIso(it.timestamp),
                            updatedAt = toIso(it.updatedAt),
                            deletedAt = toIso(it.deletedAt)
                        )
                    },
                    safetyChecks = unsyncedSafety.map {
                        SafetyCheckDto(
                            id = it.id,
                            profileId = it.profileId,
                            medicationId = null,
                            conflictType = it.findingType,
                            severity = it.severity,
                            details = it.aiMessage,
                            actionRequired = it.reviewRecommended,
                            createdAt = toIso(it.createdAt),
                            updatedAt = toIso(it.updatedAt),
                            deletedAt = toIso(it.deletedAt)
                        )
                    },
                    notifications = unsyncedNotifs.map {
                        NotificationDto(
                            id = it.uuid,
                            profileId = it.profileId,
                            type = it.type,
                            message = it.message,
                            isRead = it.isRead,
                            createdAt = toIso(it.timestamp),
                            updatedAt = toIso(it.updatedAt),
                            deletedAt = toIso(it.deletedAt)
                        )
                    }
                )

                api.syncPush(pushPayload)

                // Mark local rows as synced
                unsyncedProfiles.forEach { db.profileDao().markSynced(it.id) }
                unsyncedMeds.forEach { db.medicationDao().markSynced(it.id) }
                unsyncedTimes.forEach { db.doseTimeDao().markSynced(it.id) }
                unsyncedLogs.forEach { db.doseLogDao().markSynced(it.id) }
                unsyncedLinks.forEach { db.careLinkDao().markSynced(it.id) }
                unsyncedRecordings.forEach { db.recordingDao().markSynced(it.uuid) }
                unsyncedSafety.forEach { db.safetyCheckDao().markSynced(it.id) }
                unsyncedNotifs.forEach { db.notificationDao().markSynced(it.uuid) }
            }

            // ── 2. Pull Remote Changes ────────────────────────────────────────
            val lastSync = sessionManager.getLastSyncTime()
            val pullResult = api.syncPull(since = lastSync)

            // Merge Profiles
            for (p in pullResult.profiles) {
                val existing = db.profileDao().getProfileById(p.id)
                val remoteUpdated = parseIso(p.updatedAt)
                if (existing == null || remoteUpdated >= existing.updatedAt) {
                    db.profileDao().insertProfile(
                        ProfileEntity(
                            id = p.id,
                            ownerUserId = p.ownerUserId,
                            name = p.name,
                            avatar = p.avatar,
                            createdAt = parseIso(p.createdAt),
                            updatedAt = remoteUpdated,
                            deletedAt = parseIsoNullable(p.deletedAt),
                            synced = true
                        )
                    )
                }
            }

            // Merge Medications
            for (m in pullResult.medications) {
                val existing = db.medicationDao().getMedicationById(m.id)
                val remoteUpdated = parseIso(m.updatedAt)
                if (existing == null || remoteUpdated >= existing.updatedAt) {
                    val entity = existing?.copy(
                        profileId = m.profileId,
                        name = m.name,
                        dose = m.dosage ?: existing.dose,
                        strength = m.strength ?: existing.strength,
                        timing = m.timing ?: existing.timing,
                        sourceType = m.source ?: existing.sourceType,
                        updatedAt = remoteUpdated,
                        deletedAt = parseIsoNullable(m.deletedAt),
                        synced = true
                    ) ?: MedicationEntity(
                        id = m.id,
                        name = m.name,
                        strength = m.strength ?: "",
                        dose = m.dosage ?: "1 unit",
                        frequency = "Daily",
                        timing = m.timing ?: "Daily",
                        duration = "${m.durationDays ?: 30} days",
                        confidence = 0.95f,
                        needsVerification = false,
                        verifiedByUser = true,
                        crossVerified = true,
                        conflicts = "[]",
                        reviewRecommended = false,
                        schedule = "[]",
                        adherence = "[]",
                        summary = "${m.name} (${m.dosage ?: "1 unit"}). Take as directed.",
                        sideEffects = "[]",
                        visibleTo = "[\"patient\"]",
                        imageUrl = null,
                        sourceType = m.source ?: "sync",
                        consultationNotes = "",
                        billImageUrl = null,
                        caretakerPhone = null,
                        callReminderStatus = "not_set",
                        privacySummary = "{}",
                        profileId = m.profileId,
                        updatedAt = remoteUpdated,
                        deletedAt = parseIsoNullable(m.deletedAt),
                        synced = true
                    )
                    db.medicationDao().upsertMedication(entity)
                }
            }

            // Merge DoseTimes
            for (t in pullResult.doseTimes) {
                val existing = db.doseTimeDao().getDoseTimeById(t.id)
                val remoteUpdated = parseIso(t.updatedAt)
                if (existing == null || remoteUpdated >= existing.updatedAt) {
                    db.doseTimeDao().insertDoseTime(
                        DoseTimeEntity(
                            id = t.id,
                            medicationId = t.medicationId,
                            profileId = t.profileId,
                            time = t.time,
                            slot = t.slot,
                            createdAt = parseIso(t.createdAt),
                            updatedAt = remoteUpdated,
                            deletedAt = parseIsoNullable(t.deletedAt),
                            synced = true
                        )
                    )
                }
            }

            // Merge DoseLogs
            for (l in pullResult.doseLogs) {
                val existing = db.doseLogDao().getDoseLogById(l.id)
                val remoteUpdated = parseIso(l.updatedAt)
                if (existing == null || remoteUpdated >= existing.updatedAt) {
                    db.doseLogDao().insertDoseLog(
                        DoseLogEntity(
                            id = l.id,
                            profileId = l.profileId,
                            medicationId = l.medicationId,
                            doseTimeId = l.doseTimeId,
                            scheduledTime = l.scheduledTime,
                            status = l.status,
                            takenAt = parseIsoNullable(l.takenAt),
                            snoozeUntil = parseIsoNullable(l.snoozeUntil),
                            createdAt = parseIso(l.createdAt),
                            updatedAt = remoteUpdated,
                            deletedAt = parseIsoNullable(l.deletedAt),
                            synced = true
                        )
                    )
                }
            }

            // Merge CareLinks
            for (cl in pullResult.careLinks) {
                val existing = db.careLinkDao().getCareLinkById(cl.id)
                val remoteUpdated = parseIso(cl.updatedAt)
                if (existing == null || remoteUpdated >= existing.updatedAt) {
                    db.careLinkDao().insertCareLink(
                        CareLinkEntity(
                            id = cl.id,
                            profileId = cl.profileId,
                            caretakerUserId = cl.caretakerUserId,
                            permission = cl.permission,
                            status = cl.status,
                            inviteCode = cl.inviteCode ?: "",
                            createdAt = parseIso(cl.createdAt),
                            updatedAt = remoteUpdated,
                            deletedAt = parseIsoNullable(cl.deletedAt),
                            synced = true
                        )
                    )
                }
            }

            // Merge Recordings
            for (r in pullResult.recordings) {
                val existing = db.recordingDao().getRecordingByUuid(r.id)
                val remoteUpdated = parseIso(r.updatedAt)
                if (existing == null || remoteUpdated >= existing.updatedAt) {
                    val entity = existing?.copy(
                        profileId = r.profileId,
                        filePath = r.filePath ?: existing.filePath,
                        transcript = r.transcriptEnglish ?: existing.transcript,
                        summary = r.summaryJson ?: existing.summary,
                        updatedAt = remoteUpdated,
                        deletedAt = parseIsoNullable(r.deletedAt),
                        synced = true
                    ) ?: RecordingEntity(
                        uuid = r.id,
                        profileId = r.profileId,
                        timestamp = parseIso(r.createdAt),
                        filePath = r.filePath ?: "",
                        transcript = r.transcriptEnglish ?: "",
                        summary = r.summaryJson ?: "",
                        status = "transcribed",
                        updatedAt = remoteUpdated,
                        deletedAt = parseIsoNullable(r.deletedAt),
                        synced = true
                    )
                    db.recordingDao().insertRecording(entity)
                }
            }

            // Merge Safety Checks
            for (s in pullResult.safetyChecks) {
                val existing = db.safetyCheckDao().getSafetyCheckById(s.id)
                val remoteUpdated = parseIso(s.updatedAt)
                if (existing == null || remoteUpdated >= existing.updatedAt) {
                    db.safetyCheckDao().insertSafetyCheck(
                        SafetyCheckEntity(
                            id = s.id,
                            profileId = s.profileId,
                            medicineA = s.details ?: "Medication",
                            medicineB = "",
                            medicineAStrength = "",
                            medicineBStrength = "",
                            findingType = s.conflictType,
                            result = s.severity,
                            severity = s.severity,
                            aiTitle = s.conflictType,
                            aiMessage = s.details ?: "",
                            aiRecommendation = "",
                            crossVerified = true,
                            reviewRecommended = s.actionRequired,
                            createdAt = parseIso(s.createdAt),
                            updatedAt = remoteUpdated,
                            deletedAt = parseIsoNullable(s.deletedAt),
                            synced = true
                        )
                    )
                }
            }

            // Merge Notifications
            for (n in pullResult.notifications) {
                val existing = db.notificationDao().getNotificationByUuid(n.id)
                val remoteUpdated = parseIso(n.updatedAt)
                if (existing == null || remoteUpdated >= existing.updatedAt) {
                    val entity = existing?.copy(
                        profileId = n.profileId,
                        type = n.type,
                        message = n.message,
                        isRead = n.isRead,
                        updatedAt = remoteUpdated,
                        deletedAt = parseIsoNullable(n.deletedAt),
                        synced = true
                    ) ?: NotificationEntity(
                        uuid = n.id,
                        profileId = n.profileId,
                        type = n.type,
                        message = n.message,
                        timestamp = parseIso(n.createdAt),
                        isRead = n.isRead,
                        updatedAt = remoteUpdated,
                        deletedAt = parseIsoNullable(n.deletedAt),
                        synced = true
                    )
                    db.notificationDao().insertNotification(entity)
                    if (existing == null && !n.isRead && n.type == "MISSED_DOSE") {
                        showLocalMissedDoseNotification(n.message, n.id)
                    }
                }
            }

            if (!pullResult.serverTime.isNullOrBlank()) {
                sessionManager.setLastSyncTime(pullResult.serverTime)
            }

            _syncState.value = SyncState.IDLE
            true
        } catch (e: Exception) {
            _syncState.value = SyncState.OFFLINE
            false
        }
    }

    private fun toIso(millis: Long?): String? {
        if (millis == null || millis <= 0L) return null
        return try {
            Instant.ofEpochMilli(millis).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseIso(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(iso).toInstant().toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    private fun parseIsoNullable(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(iso).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun showLocalMissedDoseNotification(message: String, notifId: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(context, "medication_reminders")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Missed Dose Alert")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
            nm.notify(notifId.hashCode(), builder.build())
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SyncRepository? = null

        fun getInstance(context: Context): SyncRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = SyncRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
