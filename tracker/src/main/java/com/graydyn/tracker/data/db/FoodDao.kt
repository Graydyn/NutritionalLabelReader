package com.graydyn.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.graydyn.tracker.data.model.Food

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(foods: List<Food>)

    /**
     * Inserts in chunks inside a single transaction. Without an explicit
     * transaction each insert commits on its own, which on a fresh install
     * turns the ~half-million-row seed into hundreds of thousands of fsyncs.
     * Chunking keeps SQLite's bound-argument count within limits.
     */
    @Transaction
    suspend fun insertAllBatched(foods: List<Food>) {
        foods.chunked(SEED_INSERT_CHUNK_SIZE).forEach { insertAll(it) }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(food: Food): Long

    @Query(
        """
        SELECT * FROM foods
        WHERE name LIKE '%' || :query || '%'
        ORDER BY
          CASE WHEN lastAmount IS NOT NULL THEN 0 ELSE 1 END,
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

    @Query("UPDATE foods SET lastAmount = :amount WHERE id = :id")
    suspend fun updateLastAmount(id: Long, amount: Float)

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    companion object {
        /**
         * Rows per insert statement during seeding. Each [Food] binds ~20
         * columns; staying well under SQLite's 999-variable limit (≈49 rows)
         * with margin avoids "too many SQL variables".
         */
        private const val SEED_INSERT_CHUNK_SIZE = 40
    }
}
