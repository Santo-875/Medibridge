package com.medibridge.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * NotificationEntity — persists in-app alert notifications.
 *
 * type: "WRONG_TIME" | "MISSED_DOSE" | "SAFETY_INTERACTION" | "SAFETY_DUPLICATE" | "GENERAL"
 *
 * These are stored so notifications are still visible after the system dismisses them,
 * and are surfaced in the notification bell tray on the home screen.
 */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Alert type — controls the icon and colour shown in the tray. */
    val type: String,
    /** Short human-readable message for the tray. */
    val message: String,
    /** Optional related medication name for linking. */
    val medicationName: String = "",
    /** Unix epoch millis of when the alert was generated. */
    val timestamp: Long,
    /** false = unread (shows as bold / badge incremented), true = read. */
    val isRead: Boolean = false
)
