package com.medibridge.core.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseTimeDao {
    @Query("SELECT * FROM dose_times WHERE profileId = :profileId AND deletedAt IS NULL")
    fun getDoseTimesForProfile(profileId: String): Flow<List<DoseTimeEntity>>

    @Query("SELECT * FROM dose_times WHERE medicationId = :medicationId AND deletedAt IS NULL")
    fun getDoseTimesForMedication(medicationId: String): Flow<List<DoseTimeEntity>>

    @Query("SELECT * FROM dose_times WHERE id = :id LIMIT 1")
    suspend fun getDoseTimeById(id: String): DoseTimeEntity?

    @Query("SELECT * FROM dose_times WHERE synced = 0")
    suspend fun getUnsyncedDoseTimes(): List<DoseTimeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseTime(doseTime: DoseTimeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseTimes(doseTimes: List<DoseTimeEntity>)

    @Query("UPDATE dose_times SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
