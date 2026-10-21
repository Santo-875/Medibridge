package com.medibridge.core.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE deletedAt IS NULL ORDER BY createdAt ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getProfileById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE synced = 0")
    suspend fun getUnsyncedProfiles(): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<ProfileEntity>)

    @Query("UPDATE profiles SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE profiles SET deletedAt = :deletedAt, updatedAt = :updatedAt, synced = 0 WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())
}
