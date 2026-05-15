package com.graydyn.tracker.ui.diary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SourceType
import com.graydyn.tracker.data.repository.SavedMealRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryViewModelSaveMealTest {

    private lateinit var db: TrackerDatabase
    private lateinit var repo: SavedMealRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TrackerDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = SavedMealRepository(db, db.savedMealDao(), db.diaryEntryDao())
    }

    @After
    fun teardown() { db.close() }

    private fun entry(label: String, calories: Int) = DiaryEntry(
        date = "2026-05-14",
        mealType = MealType.BREAKFAST,
        label = label,
        sourceType = SourceType.DATABASE,
        foodId = 1L,
        unitType = FoodUnitType.GRAM,
        grams = 100f, count = null,
        calories = calories, protein = 5f, fat = 2f, carbs = 10f
    )

    @Test
    fun saveFromDiaryEntries_writesMealItemsAndInitialSlotApplication() = runTest {
        val id = repo.saveFromDiaryEntries(
            name = "My breakfast",
            sourceMealType = MealType.BREAKFAST,
            entries = listOf(entry("Oats", 200), entry("Berries", 80)),
            nowMillis = 1_000L
        )

        val items = db.savedMealDao().getItems(id)
        assertEquals(2, items.size)
        assertEquals(listOf("Oats", "Berries"), items.map { it.label })
        assertEquals(listOf(0, 1), items.map { it.position })

        val summaries = db.savedMealDao().observeSummariesForSlot(MealType.BREAKFAST).first()
        assertEquals(1, summaries.size)
        assertEquals("My breakfast", summaries[0].name)
        assertEquals(2, summaries[0].itemCount)
        assertEquals(280, summaries[0].totalCalories)
        assertEquals(1_000L, summaries[0].lastAppliedAt)
    }
}