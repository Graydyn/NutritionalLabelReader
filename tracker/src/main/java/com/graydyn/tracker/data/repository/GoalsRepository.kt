package com.graydyn.tracker.data.repository

import com.graydyn.tracker.data.db.GoalsDao
import com.graydyn.tracker.data.model.Goals
import kotlinx.coroutines.flow.Flow

class GoalsRepository(
    private val goalsDao: GoalsDao
) {
    fun getGoals(): Flow<Goals?> = goalsDao.getGoals()

    suspend fun upsert(goals: Goals) = goalsDao.upsert(goals)
}
