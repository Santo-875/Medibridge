package com.medibridge.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "dose_logs")
data class DoseLogEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val profileId: String = "default_me",
    val medicationId: String,
    val doseTimeId: String? = null,
    val scheduledTime: String,
    val status: String = "PENDING", // PENDING, TAKEN, MISSED, SNOOZED
    val takenAt: Long? = null,
    val snoozeUntil: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val synced: Boolean = false
)
