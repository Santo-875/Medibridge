package com.medibridge.moduleB_safety.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO interface for querying and inserting safety evaluation records.
 */
@Dao
interface SafetyCheckDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSafetyCheck(safetyCheck: SafetyCheckEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(safetyChecks: List<SafetyCheckEntity>)

    @Query("SELECT * FROM safety_checks ORDER BY createdAt DESC")
    fun getAllSafetyChecks(): Flow<List<SafetyCheckEntity>>

    @Query("SELECT * FROM safety_checks ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentSafetyChecks(limit: Int = 30): Flow<List<SafetyCheckEntity>>

    @Query("SELECT * FROM safety_checks ORDER BY createdAt DESC")
    suspend fun getAllSafetyChecksDirect(): List<SafetyCheckEntity>

    @Query("SELECT * FROM safety_checks WHERE id = :id")
    suspend fun getSafetyCheckById(id: String): SafetyCheckEntity?

    @Query("SELECT * FROM safety_checks WHERE synced = 0")
    suspend fun getUnsyncedSafetyChecks(): List<SafetyCheckEntity>

    @Query("UPDATE safety_checks SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM safety_checks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM safety_checks")
    suspend fun deleteAll()
}
