package com.medibridge.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for in-app alert notifications (wrong-time dose, missed dose, safety flags).
 */
@Dao
interface NotificationDao {

    /** Observe all notifications, newest first — for the tray UI. */
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    /** Count of unread notifications — drives the badge counter. */
    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    /** Insert a new alert notification. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity): Long

    /** Mark a single notification as read. */
    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    /** Mark all notifications as read (e.g. when user opens tray). */
    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllRead()

    /** Delete all notifications. */
    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    /**
     * Returns the most-recent unread alert for a specific medication name.
     * Used by ReminderCard to show per-card WRONG_TIME / MISSED_DOSE badges.
     */
    @Query(
        "SELECT * FROM notifications WHERE medicationName = :medName " +
        "AND type IN ('WRONG_TIME', 'MISSED_DOSE') " +
        "ORDER BY timestamp DESC LIMIT 1"
    )
    suspend fun getLatestAlertForMed(medName: String): NotificationEntity?

    /**
     * Observe all active (unread) alerts for a specific medication name as a Flow.
     */
    @Query(
        "SELECT * FROM notifications WHERE medicationName = :medName " +
        "AND type IN ('WRONG_TIME', 'MISSED_DOSE') AND isRead = 0 " +
        "ORDER BY timestamp DESC"
    )
    fun observeActiveAlertsForMed(medName: String): Flow<List<NotificationEntity>>
}
