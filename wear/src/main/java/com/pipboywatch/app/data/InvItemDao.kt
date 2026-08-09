package com.pipboywatch.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InvItemDao {
    @Query("SELECT * FROM inv_items ORDER BY sortOrder")
    fun observeAll(): Flow<List<InvItemEntity>>

    @Query("SELECT COUNT(*) FROM inv_items")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InvItemEntity>)

    @Query("UPDATE inv_items SET isChecked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("UPDATE inv_items SET isChecked = 0")
    suspend fun uncheckAll()

    @Query("SELECT * FROM inv_items WHERE isSystemLinked = 1 LIMIT 1")
    suspend fun getSystemLinkedItem(): InvItemEntity?
}
