package com.graydyn.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per day the user actually recorded a weight (pounds). The effective
 * weight for any date carries forward from the most recent row at or before it.
 */
@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey val date: String,   // "yyyy-MM-dd"
    val weightLbs: Float
)
