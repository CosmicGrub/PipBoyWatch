package com.pipboywatch.app.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real migrations reconstructed from this project's actual commit history
 * rather than hand-guessed: each of the three versions ever declared by
 * PipBoyDatabase added whole new tables and never once changed an
 * existing entity's columns after introduction (confirmed by checking
 * every entity file's own git log — each has exactly one commit, its
 * introduction). That made every migration here a pure ADD TABLE, with
 * no ALTER/data-copy logic needed.
 *
 * The CREATE TABLE SQL below is copied verbatim from the real schema
 * exports in wear/schemas/ (the `${TABLE_NAME}` placeholder replaced with
 * the literal table name) — not retyped by hand from the entity classes —
 * so it's byte-for-byte what Room itself considers correct for each
 * version. Those schema JSONs for versions 1 and 2 were themselves
 * generated for real, from the actual historical commits (via a scratch
 * git worktree with exportSchema temporarily flipped on), not
 * hand-authored — exportSchema was false for both at the time, so this
 * was the only way to get real ground truth instead of a guess.
 */

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quests` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`text` TEXT NOT NULL, `isDone` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `holotapes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`appLabel` TEXT NOT NULL, `title` TEXT NOT NULL, `text` TEXT NOT NULL, `postedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`text` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, `source` TEXT NOT NULL)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `runs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `distanceMeters` REAL NOT NULL, " +
                "`elevationGainMeters` REAL NOT NULL, `avgHeartRateBpm` REAL, `routePointsJson` TEXT NOT NULL)"
        )
    }
}

/** Every migration this database currently knows how to run in order —
 * PipBoyDatabase.getInstance() passes this straight to .addMigrations().
 * fallbackToDestructiveMigration remains wired alongside this only as the
 * terminal fallback for a jump this list can't cover (e.g. an install
 * from before version 1 existed at all, if that's even reachable) — the
 * steady-state path for every future version bump is a new Migration
 * added here, not a silent data wipe. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
