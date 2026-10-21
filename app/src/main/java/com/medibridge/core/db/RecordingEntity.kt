package com.medibridge.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * RecordingEntity — Local persistence for doctor consultation audio notes,
 * transcripts, and clinical summaries.
 *
 * status: "pending" | "transcribed" | "failed"
 */
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val filePath: String,
    val transcript: String = "",
    val summary: String = "",
    /** Upload/transcription status: "pending" | "transcribed" | "failed" */
    val status: String = "pending",
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val profileId: String = "default_me",
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val synced: Boolean = false
)
