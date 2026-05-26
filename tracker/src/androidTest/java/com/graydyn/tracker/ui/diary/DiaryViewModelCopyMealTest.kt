package com.graydyn.tracker.ui.diary

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SourceType
import com.graydyn.tracker.data.repository.DiaryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryViewModelCopyMealTest {

    private lateinit var db: TrackerDatabase
    private lateinit var repo: DiaryRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TrackerDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = DiaryRepository(db.diaryEntryDao())
    }

    @After
    fun teardown() { db.close() }

    private fun sourceEntry(label: String, calories: Int) = DiaryEntry(
        date = "2026-05-26",
        mealType = MealType.BREAKFAST,
        label = label,
        sourceType = SourceType.DATABASE,
        foodId = 1L,
        unitType = FoodUnitType.GRAM,
        grams = 100f, count = null,
        calories = calories, protein = 5f, fat = 2f, carbs = 10f
    )

    @Test
    fun copyMealEntries_appendsAtTargetAndLeavesSourceUnchanged() = runTest {
        val sources = listOf(sourceEntry("Oats", 200), sourceEntry("Berries", 80))
        sources.forEach { repo.insert(it) }

        val readBackSources = repo.getEntriesForDate("2026-05-26").first()
        val copies = readBackSources.map {
            it.copy(id = 0, date = "2026-05-27", mealType = MealType.LUNCH)
        }
        db.withTransaction { repo.insertAll(copies) }

        val sourceRows = repo.getEntriesForDate("2026-05-26").first()
        assertEquals(2, sourceRows.size)
        assertEquals(setOf("Oats", "Berries"), sourceRows.map { it.label }.toSet())
        assertEquals(setOf(MealType.BREAKFAST), sourceRows.map { it.mealType }.toSet())

        val targetRows = repo.getEntriesForDate("2026-05-27").first()
        assertEquals(2, targetRows.size)
        assertEquals(setOf("Oats", "Berries"), targetRows.map { it.label }.toSet())
        assertEquals(setOf(MealType.LUNCH), targetRows.map { it.mealType }.toSet())
        assertEquals(280, targetRows.sumOf { it.calories ?: 0 })
        assertEquals(setOf(SourceType.DATABASE), targetRows.map { it.sourceType }.toSet())
    }

    @Test
    fun copyMealEntries_appendsToTargetSlotWithExistingEntries() = runTest {
        repo.insert(sourceEntry("source-A", 100))
        repo.insert(sourceEntry("source-B", 150))

        val existing = DiaryEntry(
            date = "2026-05-27",
            mealType = MealType.LUNCH,
            label = "existing",
            sourceType = SourceType.DATABASE,
            foodId = 2L,
            unitType = FoodUnitType.GRAM,
            grams = 50f, count = null,
            calories = 50, protein = 1f, fat = 0f, carbs = 5f
        )
        repo.insert(existing)

        val sources = repo.getEntriesForDate("2026-05-26").first()
        val copies = sources.map {
            it.copy(id = 0, date = "2026-05-27", mealType = MealType.LUNCH)
        }
        db.withTransaction { repo.insertAll(copies) }

        val lunchRows = repo.getEntriesForDate("2026-05-27").first()
        assertEquals(3, lunchRows.size)
        assertEquals(setOf("existing", "source-A", "source-B"), lunchRows.map { it.label }.toSet())
    }

    @Test
    fun copyMealEntries_selfCopyDuplicatesInPlace() = runTest {
        repo.insert(sourceEntry("Oats", 200))

        val sources = repo.getEntriesForDate("2026-05-26").first()
        val copies = sources.map {
            it.copy(id = 0, date = "2026-05-26", mealType = MealType.BREAKFAST)
        }
        db.withTransaction { repo.insertAll(copies) }

        val rows = repo.getEntriesForDate("2026-05-26").first()
        assertEquals(2, rows.size)
        assertEquals(listOf("Oats", "Oats"), rows.map { it.label })
    }
}
