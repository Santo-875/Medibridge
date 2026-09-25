package com.medibridge.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the patient_summaries table.
 *
 * Used by Module C to persist and retrieve Gemini-generated patient summaries
 * and side effects.
 */
@Dao
interface PatientSummaryDao {

    @Query("SELECT * FROM patient_summaries WHERE medicationId = :medicationId LIMIT 1")
    suspend fun getSummaryByMedicationId(medicationId: String): PatientSummaryEntity?

    @Query("SELECT * FROM patient_summaries WHERE medicationId = :medicationId LIMIT 1")
    fun observeSummaryByMedicationId(medicationId: String): Flow<PatientSummaryEntity?>

    @Query("SELECT * FROM patient_summaries ORDER BY generatedAt DESC")
    fun getAllSummaries(): Flow<List<PatientSummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: PatientSummaryEntity)

    @Query("DELETE FROM patient_summaries WHERE medicationId = :medicationId")
    suspend fun deleteByMedicationId(medicationId: String)

    @Query("DELETE FROM patient_summaries")
    suspend fun deleteAll()
}
