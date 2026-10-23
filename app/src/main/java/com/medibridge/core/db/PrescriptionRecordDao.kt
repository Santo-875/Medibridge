package com.medibridge.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrescriptionRecordDao {

    @Query("SELECT * FROM prescription_records WHERE deletedAt IS NULL ORDER BY scanTimestamp DESC")
    fun getAllPrescriptions(): Flow<List<PrescriptionRecordEntity>>

    @Query("SELECT * FROM prescription_records WHERE deletedAt IS NULL ORDER BY scanTimestamp DESC")
    suspend fun getAllPrescriptionsDirect(): List<PrescriptionRecordEntity>

    @Query("SELECT * FROM prescription_records WHERE id = :id AND deletedAt IS NULL")
    suspend fun getPrescriptionById(id: Long): PrescriptionRecordEntity?

    @Query("SELECT * FROM prescription_records WHERE uuid = :uuid LIMIT 1")
    suspend fun getPrescriptionByUuid(uuid: String): PrescriptionRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescription(prescription: PrescriptionRecordEntity): Long

    @Query("UPDATE prescription_records SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteById(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM prescription_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM prescription_records WHERE synced = 0")
    suspend fun getUnsyncedPrescriptions(): List<PrescriptionRecordEntity>

    @Query("UPDATE prescription_records SET synced = 1 WHERE uuid = :uuid")
    suspend fun markSynced(uuid: String)
}
