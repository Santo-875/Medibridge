package com.medibridge.moduleB_safety.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room Entity representing a persistent record of a completed safety evaluation.
 */
@Entity(tableName = "safety_checks")
data class SafetyCheckEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val medicineA: String,
    val medicineB: String,
    val medicineAStrength: String,
    val medicineBStrength: String,
    val findingType: String,
    val result: String,
    val severity: String,
    val aiTitle: String,
    val aiMessage: String,
    val aiRecommendation: String,
    val crossVerified: Boolean,
    val reviewRecommended: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)
