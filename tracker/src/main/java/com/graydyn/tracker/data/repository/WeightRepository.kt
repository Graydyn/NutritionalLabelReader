package com.graydyn.tracker.data.repository

import com.graydyn.tracker.data.db.WeightEntryDao
import com.graydyn.tracker.data.model.WeightEntry
import kotlinx.coroutines.flow.Flow

class WeightRepository(
    private val dao: WeightEntryDao
) {
    fun observeEffectiveWeight(date: String): Flow<WeightEntry?> =
        dao.observeEffectiveWeight(date)

    suspend fun setWeight(date: String, weightLbs: Float) =
        dao.upsert(WeightEntry(date, weightLbs))
}
