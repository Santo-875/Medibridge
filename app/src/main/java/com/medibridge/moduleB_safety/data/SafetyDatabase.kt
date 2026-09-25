package com.medibridge.moduleB_safety.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Dedicated local Room database for Module B safety check persistence and history.
 */
@Database(
    entities = [SafetyCheckEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SafetyDatabase : RoomDatabase() {

    abstract fun safetyCheckDao(): SafetyCheckDao

    companion object {
        @Volatile
        private var INSTANCE: SafetyDatabase? = null

        fun getInstance(context: Context): SafetyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SafetyDatabase::class.java,
                    "medibridge_safety_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
