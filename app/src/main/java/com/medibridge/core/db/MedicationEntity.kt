package com.medibridge.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room DB Entity mirroring [com.medibridge.core.model.MedicationObject].
 *
 * Complex fields (conflicts, schedule, adherence, sideEffects, visibleTo) are stored
 * as JSON strings and unpacked by the toEntity()/fromEntity() extension functions
 * in MedicationObject.kt.
 *
 * DB VERSION: 1  (increment in AppDatabase when schema changes, add a Migration)
 */
@Entity(tableName = "medications")
data class MedicationEntity(

    @PrimaryKey
    val id: String,

    val name: String,
    val strength: String,
    val dose: String,
    val frequency: String,
    val timing: String,
    val duration: String,

    // AI confidence (0.0 – 1.0) from Module A
    val confidence: Float,

    val needsVerification: Boolean,
    val verifiedByUser: Boolean,

    // Module B safety cross-check flag
    val crossVerified: Boolean,

    /**
     * JSON array of {withMedId, type, detail} objects.
     * Deserialize with Gson in MedicationObject.fromEntity().
     */
    val conflicts: String,

    val reviewRecommended: Boolean,

    /**
     * JSON array of {time, slot, withFood} objects — set by Module C.
     */
    val schedule: String,

    /**
     * JSON array of {date, status} objects — adherence log.
     */
    val adherence: String,

    val summary: String,

    /** JSON array of side-effect strings. */
    val sideEffects: String,

    /**
     * JSON array of role strings e.g. ["patient", "doctor"].
     * Reserved for future role-based access control.
     */
    val visibleTo: String,

    /** Optional prescription image URL/path (set by Module A). */
    val imageUrl: String? = null
)
