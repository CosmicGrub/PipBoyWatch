package com.pipboywatch.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestDao {
    @Query("SELECT * FROM quests ORDER BY isDone ASC, createdAt DESC")
    fun observeAll(): Flow<List<QuestEntity>>

    @Insert
    suspend fun insert(quest: QuestEntity)

    @Query("UPDATE quests SET isDone = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Delete
    suspend fun delete(quest: QuestEntity)
}
