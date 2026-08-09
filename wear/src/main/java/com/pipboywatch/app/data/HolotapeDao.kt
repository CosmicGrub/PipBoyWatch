package com.pipboywatch.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HolotapeDao {
    @Query("SELECT * FROM holotapes ORDER BY postedAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<HolotapeEntity>>

    @Insert
    suspend fun insert(holotape: HolotapeEntity)

    // Keep the table from growing unbounded — called after each insert.
    @Query(
        "DELETE FROM holotapes WHERE id NOT IN " +
            "(SELECT id FROM holotapes ORDER BY postedAt DESC LIMIT 50)"
    )
    suspend fun trimOldEntries()
}
