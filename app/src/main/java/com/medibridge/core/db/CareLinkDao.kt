package com.medibridge.core.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CareLinkDao {
    @Query("SELECT * FROM care_links WHERE deletedAt IS NULL")
    fun getAllCareLinks(): Flow<List<CareLinkEntity>>

    @Query("SELECT * FROM care_links WHERE profileId = :profileId AND deletedAt IS NULL")
    fun getCareLinksForProfile(profileId: String): Flow<List<CareLinkEntity>>

    @Query("SELECT * FROM care_links WHERE id = :id LIMIT 1")
    suspend fun getCareLinkById(id: String): CareLinkEntity?

    @Query("SELECT * FROM care_links WHERE synced = 0")
    suspend fun getUnsyncedCareLinks(): List<CareLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCareLink(link: CareLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCareLinks(links: List<CareLinkEntity>)

    @Query("UPDATE care_links SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE care_links SET status = 'revoked', deletedAt = :deletedAt, updatedAt = :updatedAt, synced = 0 WHERE id = :id")
    suspend fun revokeCareLink(id: String, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())
}
