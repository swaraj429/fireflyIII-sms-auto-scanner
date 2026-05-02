package com.swaraj429.firefly3smsscanner.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedAccount::class,
        CachedCategory::class,
        CachedTag::class,
        CachedBudget::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FireflyDatabase : RoomDatabase() {

    abstract fun fireflyDao(): FireflyDao

    companion object {
        @Volatile
        private var INSTANCE: FireflyDatabase? = null

        fun getDatabase(context: Context): FireflyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FireflyDatabase::class.java,
                    "firefly_metadata_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
