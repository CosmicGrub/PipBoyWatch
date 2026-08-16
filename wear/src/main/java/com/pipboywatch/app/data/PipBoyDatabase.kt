package com.pipboywatch.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        InvItemEntity::class,
        QuestEntity::class,
        HolotapeEntity::class,
        NoteEntity::class,
        RunEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PipBoyDatabase : RoomDatabase() {
    abstract fun invItemDao(): InvItemDao
    abstract fun questDao(): QuestDao
    abstract fun holotapeDao(): HolotapeDao
    abstract fun noteDao(): NoteDao
    abstract fun runDao(): RunDao

    companion object {
        @Volatile private var instance: PipBoyDatabase? = null

        fun getInstance(context: Context): PipBoyDatabase {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    // Loads SQLCipher's native libs — cheap to call more
                    // than once, so no need for a custom Application class
                    // just to do it exactly once at process start.
                    SQLiteDatabase.loadLibs(appContext)
                    deletePlaintextDbIfPresent(appContext)
                    val passphrase = DatabasePassphrase.getOrCreate(appContext)

                    Room.databaseBuilder(appContext, PipBoyDatabase::class.java, "pipboy.db")
                        // GPS routes, heart rate, notes, and mirrored
                        // notification content all live in here — encrypt
                        // the file at rest with a Keystore-wrapped
                        // passphrase (see DatabasePassphrase) rather than
                        // leaving it as plain SQLite on disk.
                        .openHelperFactory(SupportFactory(passphrase))
                        // Pre-release app, no real user data to preserve yet —
                        // acceptable now, revisit with real migrations before
                        // this ever ships with data worth keeping.
                        .fallbackToDestructiveMigration(dropAllTables = true)
                        .build()
                        .also { instance = it }
                }
            }
        }

        /**
         * One-time migration guard: any install from before this change has
         * a plain (unencrypted) SQLite file at this path. Opening it with
         * SQLCipher's SupportFactory would fail outright — not a version
         * mismatch Room's own destructive-migration handling can catch —
         * so detect a plaintext file by its unencrypted magic header and
         * delete it before Room ever touches it. Matches this DB's existing
         * "pre-release, no real data to preserve yet" tradeoff.
         */
        private fun deletePlaintextDbIfPresent(context: Context) {
            val dbFile = context.getDatabasePath("pipboy.db")
            if (!dbFile.exists()) return
            val header = ByteArray(16)
            val isPlaintextSqlite = try {
                dbFile.inputStream().use { it.read(header) }
                String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
            } catch (e: Exception) {
                false
            }
            if (isPlaintextSqlite) {
                context.deleteDatabase("pipboy.db")
            }
        }
    }
}
