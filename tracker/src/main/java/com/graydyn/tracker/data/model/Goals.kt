package com.graydyn.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table holding the user's daily macro goals. The row is always [SINGLETON_ID];
 * upserts replace it so there is only ever one set of goals.
 */
@Entity(tableName = "goals")
data class Goals(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val caloriesGoal: Int,
    val proteinGoal: Int,
    val fatGoal: Int,
    val carbsGoal: Int
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
