package com.medibridge.core.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the medications table.
 *
 * All 4 modules interact with the DB through this interface:
 *   - Module A  → insertMedication / upsertMedication
 *   - Module B  → getMedicationById, getAllMedications, upsertMedication (update conflicts)
 *   - Module C  → upsertMedication (write schedule), getMedicationsWithSchedule
 *   - Module D  → getAllMedications (home screen), updateAdherence
 *
 * Add new query methods below as modules evolve.
 */
@Dao
interface MedicationDao {

    /** Observe all medications — Flow auto-updates UI when DB changes. */
    @Query("SELECT * FROM medications ORDER BY name ASC")
    fun getAllMedications(): Flow<List<MedicationEntity>>

    /** Fetch all medications directly (suspend function). */
    @Query("SELECT * FROM medications ORDER BY name ASC")
    suspend fun getAllMedicationsDirect(): List<MedicationEntity>

    /** Fetch a single medication by its ID. */
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationById(id: String): MedicationEntity?

    /** Updates adherence JSON column directly. */
    @Query("UPDATE medications SET adherence = :adherenceJson WHERE id = :id")
    suspend fun updateAdherence(id: String, adherenceJson: String)

    /**
     * Insert or replace a medication.
     * Use this from Module A when a new prescription is scanned.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedication(medication: MedicationEntity)

    /**
     * Bulk insert — used for seeding or batch import from Module A.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(medications: List<MedicationEntity>)

    /** Delete a specific medication. */
    @Delete
    suspend fun deleteMedication(medication: MedicationEntity)

    /** Delete all medications (used in testing / reset flow). */
    @Query("DELETE FROM medications")
    suspend fun deleteAll()

    /**
     * Medications flagged for user verification — used by Module A post-OCR.
     * TODO: Module A wires this to show verification prompts.
     */
    @Query("SELECT * FROM medications WHERE needsVerification = 1")
    fun getMedicationsNeedingVerification(): Flow<List<MedicationEntity>>

    /**
     * Medications with potential conflicts — used by Module B.
     * TODO: Module B wires this to the safety dashboard.
     */
    @Query("SELECT * FROM medications WHERE conflicts != '[]' AND conflicts != ''")
    fun getMedicationsWithConflicts(): Flow<List<MedicationEntity>>
}
