package com.medibridge.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * RecordingEntity — Local persistence for doctor consultation audio notes,
 * transcripts, and clinical summaries.
 */
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val filePath: String,
    val transcript: String,
    val summary: String
)
