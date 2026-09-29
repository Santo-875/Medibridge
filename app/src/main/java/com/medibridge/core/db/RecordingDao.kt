package com.medibridge.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for voice recordings and clinical consultation transcripts.
 */
@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    suspend fun getAllRecordingsDirect(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE status = :status ORDER BY timestamp DESC")
    fun getRecordingsByStatus(status: String): Flow<List<RecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Query("UPDATE recordings SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE recordings SET transcript = :transcript, summary = :summary, status = :status WHERE id = :id")
    suspend fun updateTranscript(id: Long, transcript: String, summary: String, status: String)

    @Query("DELETE FROM recordings")
    suspend fun deleteAll()
}
