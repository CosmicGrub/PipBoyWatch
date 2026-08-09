package com.pipboywatch.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A captured notification, shown as a "holotape" log entry in DATA. */
@Entity(tableName = "holotapes")
data class HolotapeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long
)
