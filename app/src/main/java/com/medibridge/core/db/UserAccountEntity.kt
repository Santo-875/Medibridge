package com.medibridge.core.db

import androidx.room.*

@Entity(tableName = "local_users")
data class UserAccountEntity(
    @PrimaryKey
    val username: String,
    val password: String,
    val name: String,
    val userId: String
)

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM local_users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun findByUsername(username: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserAccountEntity)

    @Query("SELECT COUNT(*) FROM local_users")
    suspend fun getUserCount(): Int
}
