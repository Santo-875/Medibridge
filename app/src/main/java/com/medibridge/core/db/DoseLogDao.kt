package com.medibridge.core.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs WHERE profileId = :profileId AND deletedAt IS NULL ORDER BY scheduledTime ASC")
    fun getDoseLogsForProfile(profileId: String): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE id = :id LIMIT 1")
    suspend fun getDoseLogById(id: String): DoseLogEntity?

    @Query("SELECT * FROM dose_logs WHERE synced = 0")
    suspend fun getUnsyncedDoseLogs(): List<DoseLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseLog(log: DoseLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseLogs(logs: List<DoseLogEntity>)

    @Query("UPDATE dose_logs SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE dose_logs SET status = :status, takenAt = :takenAt, updatedAt = :updatedAt, synced = 0 WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, takenAt: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE dose_logs SET status = 'SNOOZED', snoozeUntil = :snoozeUntil, updatedAt = :updatedAt, synced = 0 WHERE id = :id")
    suspend fun snoozeLog(id: String, snoozeUntil: Long, updatedAt: Long = System.currentTimeMillis())
}
