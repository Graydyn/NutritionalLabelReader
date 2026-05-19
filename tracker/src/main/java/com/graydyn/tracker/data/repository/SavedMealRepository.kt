package com.graydyn.tracker.data.repository

import androidx.room.withTransaction
import com.graydyn.tracker.data.db.DiaryEntryDao
import com.graydyn.tracker.data.db.SavedMealDao
import com.graydyn.tracker.data.db.SavedMealSummary
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SavedMeal
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.data.model.SavedMealSlotApplication
import com.graydyn.tracker.data.model.SourceType
import kotlinx.coroutines.flow.Flow

class SavedMealRepository(
    private val database: TrackerDatabase,
    private val savedMealDao: SavedMealDao,
    private val diaryEntryDao: DiaryEntryDao
) {

    fun observeSummariesForSlot(mealType: MealType): Flow<List<SavedMealSummary>> =
        savedMealDao.observeSummariesForSlot(mealType)

    suspend fun getItems(savedMealId: Long): List<SavedMealItem> =
        savedMealDao.getItems(savedMealId)

    fun observeItems(savedMealId: Long): Flow<List<SavedMealItem>> =
        savedMealDao.observeItems(savedMealId)

    suspend fun getSavedMeal(savedMealId: Long): SavedMeal? =
        savedMealDao.getSavedMeal(savedMealId)

    suspend fun saveFromDiaryEntries(
        name: String,
        sourceMealType: MealType,
        entries: List<DiaryEntry>,
        nowMillis: Long
    ): Long {
        val meal = SavedMeal(name = name, createdAt = nowMillis)
        return savedMealDao.createSavedMealWithItems(
            meal = meal,
            itemsBuilder = { savedMealId ->
                entries.mapIndexed { index, entry ->
                    SavedMealItem(
                        savedMealId = savedMealId,
                        position = index,
                        label = entry.label,
                        foodId = entry.foodId,
                        unitType = entry.unitType,
                        grams = entry.grams,
                        count = entry.count,
                        servings = null,
                        calories = entry.calories,
                        protein = entry.protein,
                        fat = entry.fat,
                        carbs = entry.carbs
                    )
                }
            },
            initialSlotMealType = sourceMealType,
            nowMillis = nowMillis
        )
    }

    suspend fun applyToSlot(
        savedMealId: Long,
        mealType: MealType,
        date: String,
        nowMillis: Long
    ): Int = database.withTransaction {
        val items = savedMealDao.getItems(savedMealId)
        items.forEach { item ->
            val entry = DiaryEntry(
                date = date,
                mealType = mealType,
                label = item.label,
                sourceType = if (item.foodId != null) SourceType.DATABASE else SourceType.SCANNED,
                foodId = item.foodId,
                unitType = item.unitType,
                grams = item.grams,
                count = item.count,
                calories = item.calories,
                protein = item.protein,
                fat = item.fat,
                carbs = item.carbs
            )
            diaryEntryDao.insert(entry)
        }
        savedMealDao.upsertSlotApplication(
            SavedMealSlotApplication(
                savedMealId = savedMealId,
                mealType = mealType,
                lastAppliedAt = nowMillis
            )
        )
        items.size
    }

    suspend fun rename(savedMealId: Long, newName: String) {
        val current = savedMealDao.getSavedMeal(savedMealId) ?: return
        savedMealDao.updateSavedMeal(current.copy(name = newName))
    }

    suspend fun delete(savedMealId: Long) {
        savedMealDao.deleteSavedMeal(savedMealId)
    }

    suspend fun replaceItems(savedMealId: Long, newItems: List<SavedMealItem>) {
        savedMealDao.replaceItems(savedMealId, newItems)
    }

    suspend fun renameAndReplaceItems(
        savedMealId: Long,
        newName: String,
        newItems: List<SavedMealItem>
    ) = database.withTransaction {
        val current = savedMealDao.getSavedMeal(savedMealId) ?: return@withTransaction
        savedMealDao.updateSavedMeal(current.copy(name = newName))
        savedMealDao.replaceItems(savedMealId, newItems)
    }
}
