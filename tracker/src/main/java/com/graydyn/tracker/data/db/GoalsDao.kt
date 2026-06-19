package com.graydyn.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.graydyn.tracker.data.model.Goals
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalsDao {

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    fun getGoals(id: Int = Goals.SINGLETON_ID): Flow<Goals?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goals: Goals)
}
