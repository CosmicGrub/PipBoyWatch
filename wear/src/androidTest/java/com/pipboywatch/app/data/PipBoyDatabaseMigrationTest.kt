package com.pipboywatch.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pipboywatch.app.data.migrations.MIGRATION_1_2
import com.pipboywatch.app.data.migrations.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

/**
 * The project's first androidTest, and the actual point of System 04:
 * fallbackToDestructiveMigration was an accepted "no real data to
 * preserve yet" tradeoff right up until real Quests, Notes, and Runs
 * started accumulating on the physical watch this session. These tests
 * assert real rows survive both migrations, against the real historical
 * schemas — see Migrations.kt's doc comment for how those schemas were
 * obtained (generated from the actual historical commits, not guessed).
 */
@RunWith(AndroidJUnit4::class)
class PipBoyDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PipBoyDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesExistingRowAndCreatesTheThreeNewTables() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO inv_items (label, sortOrder, isSystemLinked, isChecked) VALUES ('Phone', 0, 1, 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        val cursor = db.query("SELECT label FROM inv_items")
        assertTrue("the pre-existing inv_items row must survive the migration", cursor.moveToFirst())
        assertEquals("Phone", cursor.getString(0))
        cursor.close()

        // The three new tables actually exist and are queryable — not
        // just present in the schema, genuinely selectable.
        db.query("SELECT * FROM quests").close()
        db.query("SELECT * FROM holotapes").close()
        db.query("SELECT * FROM notes").close()
    }

    @Test
    fun migrate2To3_preservesRowsInAllFourExistingTablesAndCreatesRuns() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL("INSERT INTO inv_items (label, sortOrder, isSystemLinked, isChecked) VALUES ('Radio', 1, 0, 1)")
            execSQL("INSERT INTO quests (text, isDone, createdAt) VALUES ('Find the water chip', 0, 1000)")
            execSQL("INSERT INTO holotapes (appLabel, title, text, postedAt) VALUES ('Messages', 'Mom', 'Call back', 2000)")
            execSQL("INSERT INTO notes (text, receivedAt, source) VALUES ('Buy RadAway', 3000, 'watch')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        assertEquals(1, rowCount(db, "inv_items"))
        assertEquals(1, rowCount(db, "quests"))
        assertEquals(1, rowCount(db, "holotapes"))
        assertEquals(1, rowCount(db, "notes"))
        // runs is new in this version — exists, queryable, empty.
        assertEquals(0, rowCount(db, "runs"))
    }

    @Test
    fun migrateFrom1AllTheWayTo3_survivesBothStepsAppliedInSequence() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO inv_items (label, sortOrder, isSystemLinked, isChecked) VALUES ('Watch', 0, 1, 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        val cursor = db.query("SELECT label FROM inv_items")
        assertTrue(cursor.moveToFirst())
        assertEquals("Watch", cursor.getString(0))
        cursor.close()
        db.query("SELECT * FROM runs").close()
    }

    private fun rowCount(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int {
        val cursor = db.query("SELECT COUNT(*) FROM $table")
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count
    }
}
