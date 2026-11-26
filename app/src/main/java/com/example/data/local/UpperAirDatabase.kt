package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SoundingSnapshotEntity::class, SyncLogEntity::class],
    version = 3,
    exportSchema = false
)
abstract class UpperAirDatabase : RoomDatabase() {
    abstract fun upperAirDao(): UpperAirDao

    companion object {
        @Volatile
        private var INSTANCE: UpperAirDatabase? = null

        fun getInstance(context: Context): UpperAirDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UpperAirDatabase::class.java,
                    "upperair_soundings.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
