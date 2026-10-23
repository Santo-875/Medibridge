package com.medibridge.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import com.medibridge.moduleB_safety.data.SafetyCheckDao
import com.medibridge.moduleB_safety.data.SafetyCheckEntity

/**
 * MediBridge Room Database — single source of truth for local persistence.
 *
 * DB VERSION HISTORY:
 *   v1 — Initial schema: medications table
 *   v3 — PatientSummaryEntity added (Module C)
 *   v4 — SafetyCheckEntity added (Module B migration)
 *   v5 — RecordingEntity added (Module D voice recordings)
 *   v6 — NotificationEntity added
 *   v7 — Added ProfileEntity, CareLinkEntity, DoseTimeEntity, DoseLogEntity,
 *        profileId, updatedAt, deletedAt, synced columns for offline sync.
 */
@Database(
    entities = [
        MedicationEntity::class,
        PatientSummaryEntity::class,
        SafetyCheckEntity::class,
        RecordingEntity::class,
        NotificationEntity::class,
        ProfileEntity::class,
        CareLinkEntity::class,
        DoseTimeEntity::class,
        DoseLogEntity::class,
        UserAccountEntity::class,
        PrescriptionRecordEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun patientSummaryDao(): PatientSummaryDao
    abstract fun safetyCheckDao(): SafetyCheckDao
    abstract fun recordingDao(): RecordingDao
    abstract fun notificationDao(): NotificationDao
    abstract fun profileDao(): ProfileDao
    abstract fun careLinkDao(): CareLinkDao
    abstract fun doseTimeDao(): DoseTimeDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun userAccountDao(): UserAccountDao
    abstract fun prescriptionRecordDao(): PrescriptionRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Alter medications
                db.execSQL("ALTER TABLE medications ADD COLUMN profileId TEXT NOT NULL DEFAULT 'default_me'")
                db.execSQL("ALTER TABLE medications ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE medications ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE medications ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")

                // 2. Alter safety_checks
                db.execSQL("ALTER TABLE safety_checks ADD COLUMN profileId TEXT NOT NULL DEFAULT 'default_me'")
                db.execSQL("ALTER TABLE safety_checks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE safety_checks ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE safety_checks ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")

                // 3. Alter recordings
                db.execSQL("ALTER TABLE recordings ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recordings ADD COLUMN profileId TEXT NOT NULL DEFAULT 'default_me'")
                db.execSQL("ALTER TABLE recordings ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recordings ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE recordings ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")

                // 4. Alter notifications
                db.execSQL("ALTER TABLE notifications ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notifications ADD COLUMN profileId TEXT NOT NULL DEFAULT 'default_me'")
                db.execSQL("ALTER TABLE notifications ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notifications ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE notifications ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")

                // 5. Create profiles
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `profiles` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `ownerUserId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `avatar` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `synced` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // 6. Create care_links
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `care_links` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `profileId` TEXT NOT NULL,
                        `caretakerUserId` TEXT,
                        `permission` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `inviteCode` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `synced` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // 7. Create dose_times
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dose_times` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `medicationId` TEXT NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `time` TEXT NOT NULL,
                        `slot` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `synced` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // 8. Create dose_logs
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dose_logs` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `profileId` TEXT NOT NULL,
                        `medicationId` TEXT NOT NULL,
                        `doseTimeId` TEXT,
                        `scheduledTime` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `takenAt` INTEGER,
                        `snoozeUntil` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `synced` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `local_users` (
                        `username` TEXT NOT NULL PRIMARY KEY,
                        `password` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `userId` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR REPLACE INTO `local_users` (`username`, `password`, `name`, `userId`)
                    VALUES ('admin', 'password123', 'Patient', 'user_local_admin_001')
                """.trimIndent())
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `prescription_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uuid` TEXT NOT NULL,
                        `scanTimestamp` INTEGER NOT NULL,
                        `doctorName` TEXT NOT NULL,
                        `clinicName` TEXT NOT NULL,
                        `prescriptionDate` TEXT NOT NULL,
                        `imagePath` TEXT NOT NULL,
                        `rawOcrText` TEXT NOT NULL,
                        `remarks` TEXT NOT NULL,
                        `deliveryType` TEXT NOT NULL,
                        `medicationsJson` TEXT NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `synced` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medibridge_database"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL("""
                                INSERT OR REPLACE INTO `local_users` (`username`, `password`, `name`, `userId`)
                                VALUES ('admin', 'password123', 'Patient', 'user_local_admin_001')
                            """.trimIndent())
                        }
                    })
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
