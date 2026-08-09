package com.pipboywatch.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [InvItemEntity::class], version = 1, exportSchema = false)
abstract class PipBoyDatabase : RoomDatabase() {
    abstract fun invItemDao(): InvItemDao

    companion object {
        @Volatile private var instance: PipBoyDatabase? = null

        fun getInstance(context: Context): PipBoyDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PipBoyDatabase::class.java,
                    "pipboy.db"
                ).build().also { instance = it }
            }
        }
    }
}
