package com.graydyn.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.graydyn.tracker.data.model.Food

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(foods: List<Food>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(food: Food): Long

    @Query("SELECT * FROM foods WHERE name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT 50")
    suspend fun search(query: String): List<Food>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int
}