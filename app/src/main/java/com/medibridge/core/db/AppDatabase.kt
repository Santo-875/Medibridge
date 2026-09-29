package com.medibridge.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import com.medibridge.moduleB_safety.data.SafetyCheckDao
import com.medibridge.moduleB_safety.data.SafetyCheckEntity

/**
 * MediBridge Room Database — single source of truth for local persistence.
 *
 * DB VERSION HISTORY:
 *   v1 — Initial schema: medications table (hackathon shell)
 *   v3 — PatientSummaryEntity added (Module C)
 *   v4 — SafetyCheckEntity added (Module B migration)
 *   v5 — RecordingEntity added (Module D voice recordings)
 *   v6 — NotificationEntity added (alerts tray: wrong-time, missed-dose, safety flags)
 *         RecordingEntity.status field added
 *
 * HOW TO MIGRATE:
 *   1. Bump [version] below.
 *   2. Create a Migration object: val MIGRATION_1_2 = object : Migration(1, 2) { ... }
 *   3. Add it to .addMigrations(MIGRATION_1_2) in [getInstance].
 *
 * ADDING ENTITIES:
 *   Add new @Entity classes to the [entities] array and bump the version.
 */
@Database(
    entities = [
        MedicationEntity::class,
        PatientSummaryEntity::class,
        SafetyCheckEntity::class,
        RecordingEntity::class,
        NotificationEntity::class
    ],
    version = 6,
    exportSchema = false  // Set to true + provide schemaDirectory for production
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun patientSummaryDao(): PatientSummaryDao
    abstract fun safetyCheckDao(): SafetyCheckDao
    abstract fun recordingDao(): RecordingDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Singleton accessor — always use this to get the DB instance.
         * Thread-safe via double-checked locking.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medibridge_database"
                )
                    // Add migrations here as schema evolves
                    // .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration() // Remove for production!
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
