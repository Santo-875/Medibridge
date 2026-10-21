package com.medibridge.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "care_links")
data class CareLinkEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val caretakerUserId: String? = null,
    val permission: String = "view", // alerts, view, manage
    val status: String = "pending",   // pending, active, revoked
    val inviteCode: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val synced: Boolean = false
)
