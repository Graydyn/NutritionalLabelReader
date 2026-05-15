package com.graydyn.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SavedMeal
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.data.model.SavedMealSlotApplication
import kotlinx.coroutines.flow.Flow

data class SavedMealSummary(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val totalCalories: Int,
    val createdAt: Long,
    val lastAppliedAt: Long?
)

@Dao
interface SavedMealDao {

    @Insert
    suspend fun insertSavedMeal(meal: SavedMeal): Long

    @Insert
    suspend fun insertItems(items: List<SavedMealItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSlotApplication(application: SavedMealSlotApplication)

    @Update
    suspend fun updateSavedMeal(meal: SavedMeal)

    @Query("DELETE FROM saved_meals WHERE id = :savedMealId")
    suspend fun deleteSavedMeal(savedMealId: Long)

    @Query("DELETE FROM saved_meal_items WHERE savedMealId = :savedMealId")
    suspend fun deleteItemsFor(savedMealId: Long)

    @Query("SELECT * FROM saved_meals WHERE id = :savedMealId")
    suspend fun getSavedMeal(savedMealId: Long): SavedMeal?

    @Query("SELECT * FROM saved_meal_items WHERE savedMealId = :savedMealId ORDER BY position ASC")
    suspend fun getItems(savedMealId: Long): List<SavedMealItem>

    @Query("SELECT * FROM saved_meal_items WHERE savedMealId = :savedMealId ORDER BY position ASC")
    fun observeItems(savedMealId: Long): Flow<List<SavedMealItem>>

    @Query(
        """
        SELECT m.id AS id,
               m.name AS name,
               (SELECT COUNT(*) FROM saved_meal_items i WHERE i.savedMealId = m.id) AS itemCount,
               (SELECT COALESCE(SUM(i.calories), 0) FROM saved_meal_items i WHERE i.savedMealId = m.id) AS totalCalories,
               m.createdAt AS createdAt,
               (SELECT a.lastAppliedAt FROM saved_meal_slot_applications a
                  WHERE a.savedMealId = m.id AND a.mealType = :mealType) AS lastAppliedAt
        FROM saved_meals m
        ORDER BY (CASE WHEN lastAppliedAt IS NULL THEN 1 ELSE 0 END) ASC,
                 lastAppliedAt DESC,
                 m.createdAt DESC
        """
    )
    fun observeSummariesForSlot(mealType: MealType): Flow<List<SavedMealSummary>>

    @Transaction
    suspend fun createSavedMealWithItems(
        meal: SavedMeal,
        itemsBuilder: (savedMealId: Long) -> List<SavedMealItem>,
        initialSlotMealType: MealType,
        nowMillis: Long
    ): Long {
        val id = insertSavedMeal(meal)
        insertItems(itemsBuilder(id))
        upsertSlotApplication(
            SavedMealSlotApplication(
                savedMealId = id,
                mealType = initialSlotMealType,
                lastAppliedAt = nowMillis
            )
        )
        return id
    }

    @Transaction
    suspend fun replaceItems(savedMealId: Long, newItems: List<SavedMealItem>) {
        deleteItemsFor(savedMealId)
        insertItems(newItems)
    }
}
