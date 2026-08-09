package com.pipboywatch.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        InvItemEntity::class,
        QuestEntity::class,
        HolotapeEntity::class,
        NoteEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PipBoyDatabase : RoomDatabase() {
    abstract fun invItemDao(): InvItemDao
    abstract fun questDao(): QuestDao
    abstract fun holotapeDao(): HolotapeDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile private var instance: PipBoyDatabase? = null

        fun getInstance(context: Context): PipBoyDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PipBoyDatabase::class.java,
                    "pipboy.db"
                )
                    // Pre-release app, no real user data to preserve yet —
                    // acceptable now, revisit with real migrations before
                    // this ever ships with data worth keeping.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
