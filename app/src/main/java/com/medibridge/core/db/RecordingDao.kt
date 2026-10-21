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

    @Query("SELECT * FROM recordings WHERE uuid = :uuid LIMIT 1")
    suspend fun getRecordingByUuid(uuid: String): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE synced = 0")
    suspend fun getUnsyncedRecordings(): List<RecordingEntity>

    @Query("UPDATE recordings SET synced = 1 WHERE uuid = :uuid")
    suspend fun markSynced(uuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordings(recordings: List<RecordingEntity>)

    @Query("DELETE FROM recordings")
    suspend fun deleteAll()

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)
}
