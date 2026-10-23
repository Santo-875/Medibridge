package com.medibridge.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "prescription_records")
data class PrescriptionRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val scanTimestamp: Long = System.currentTimeMillis(),
    val doctorName: String = "",
    val clinicName: String = "",
    val prescriptionDate: String = "",
    val imagePath: String = "",
    val rawOcrText: String = "",
    val remarks: String = "",
    val deliveryType: String = "",
    val medicationsJson: String = "[]",
    val profileId: String = "default_me",
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val synced: Boolean = false
)
