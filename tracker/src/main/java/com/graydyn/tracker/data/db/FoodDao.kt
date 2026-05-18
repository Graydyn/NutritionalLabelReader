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

    @Query(
        """
        SELECT * FROM foods
        WHERE name LIKE '%' || :query || '%'
        ORDER BY
          CASE WHEN foundational = 1 OR userAdded = 1 THEN 0 ELSE 1 END,
          CASE
            WHEN name LIKE :query || '%' THEN 0
            WHEN name LIKE '% ' || :query || '%' THEN 1
            ELSE 2
          END,
          name COLLATE NOCASE ASC
        LIMIT 50
        """
    )
    suspend fun search(query: String): List<Food>

    @Query("SELECT * FROM foods WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Food?

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int
}