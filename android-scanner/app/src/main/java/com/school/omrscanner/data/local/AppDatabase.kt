package com.school.omrscanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local Room SQLite Database for offline-first OMR scanning.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
@Database(entities = [ScanEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "school_omr_scans.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
