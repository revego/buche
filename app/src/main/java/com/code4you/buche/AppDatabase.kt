package com.code4you.buche

import BucaDao
import BucaSegnalazione
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BucaSegnalazione::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bucaDao(): BucaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dissesti_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}