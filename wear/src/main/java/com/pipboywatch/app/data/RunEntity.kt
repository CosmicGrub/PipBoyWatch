package com.pipboywatch.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A completed run. [routePointsJson] holds the GPS track as a JSON array
 * of {lat,lng,alt} objects — unused by any screen in v1 (no live map
 * rendering yet), stored now so a v2 map view has real data to work with
 * without needing to redesign the schema later.
 */
@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val avgHeartRateBpm: Double?,
    val routePointsJson: String
)
