package com.medibridge.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "dose_times")
data class DoseTimeEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val medicationId: String,
    val profileId: String = "default_me",
    val time: String, // HH:MM
    val slot: String = "MORNING",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val synced: Boolean = false
)
