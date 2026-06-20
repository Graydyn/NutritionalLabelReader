package com.graydyn.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.graydyn.tracker.data.model.WeightEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightEntryDao {

    /** Carry-forward: most recent weight at or before the given date, or null. */
    @Query("SELECT * FROM weight_entries WHERE date <= :date ORDER BY date DESC LIMIT 1")
    fun observeEffectiveWeight(date: String): Flow<WeightEntry?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntry)
}
