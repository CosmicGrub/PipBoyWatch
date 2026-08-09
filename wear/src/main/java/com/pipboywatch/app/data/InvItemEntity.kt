package com.pipboywatch.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row of the INV checklist. The phone row (isSystemLinked = true) is
 * auto-managed from Bluetooth connection state and isn't user-tappable;
 * everything else is a plain tap-to-confirm item.
 */
@Entity(tableName = "inv_items")
data class InvItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val sortOrder: Int,
    val isSystemLinked: Boolean = false,
    val isChecked: Boolean = false
)
