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
class DiaryViewModelApplyMealTest {

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

    private fun entry(label: String, calories: Int, mealType: MealType = MealType.BREAKFAST) = DiaryEntry(
        date = "2026-05-14",
        mealType = mealType,
        label = label,
        sourceType = SourceType.DATABASE,
        foodId = 1L,
        unitType = FoodUnitType.GRAM,
        grams = 100f, count = null,
        calories = calories, protein = 5f, fat = 2f, carbs = 10f
    )

    @Test
    fun applyToSlot_writesDiaryEntriesAndUpsertsSlotApplication() = runTest {
        val mealId = repo.saveFromDiaryEntries(
            name = "M1",
            sourceMealType = MealType.BREAKFAST,
            entries = listOf(entry("a", 100), entry("b", 200), entry("c", 300)),
            nowMillis = 1L
        )

        val n = repo.applyToSlot(
            savedMealId = mealId,
            mealType = MealType.LUNCH,
            date = "2026-05-15",
            nowMillis = 999L
        )

        assertEquals(3, n)

        // 3 diary entries inserted with the correct date and meal slot
        val lunchEntries = db.diaryEntryDao().getEntriesForDate("2026-05-15").first()
        assertEquals(3, lunchEntries.size)
        assertEquals(setOf("a", "b", "c"), lunchEntries.map { it.label }.toSet())
        assertEquals(setOf(MealType.LUNCH), lunchEntries.map { it.mealType }.toSet())
        assertEquals(setOf(SourceType.DATABASE), lunchEntries.map { it.sourceType }.toSet())

        // Picker for LUNCH now shows the meal at top with lastAppliedAt=999
        val lunchSummary = db.savedMealDao().observeSummariesForSlot(MealType.LUNCH).first().single()
        assertEquals("M1", lunchSummary.name)
        assertEquals(999L, lunchSummary.lastAppliedAt)
    }
}