package com.pipboywatch.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A note, either typed directly on-watch or (from Phase 7 onward) relayed
 * from the phone's Share-sheet receiver over the Wear Data Layer.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val receivedAt: Long,
    val source: String // "watch" or "phone"
)
