package com.pipboywatch.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Query("SELECT * FROM runs ORDER BY startTime DESC")
    fun observeAll(): Flow<List<RunEntity>>

    @Insert
    suspend fun insert(run: RunEntity)
}
