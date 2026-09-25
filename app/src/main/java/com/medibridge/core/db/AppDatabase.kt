package com.medibridge.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * MediBridge Room Database — single source of truth for local persistence.
 *
 * DB VERSION HISTORY:
 *   v1 — Initial schema: medications table (hackathon shell)
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
    entities = [MedicationEntity::class, PatientSummaryEntity::class],
    version = 3,
    exportSchema = false  // Set to true + provide schemaDirectory for production
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun patientSummaryDao(): PatientSummaryDao

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
