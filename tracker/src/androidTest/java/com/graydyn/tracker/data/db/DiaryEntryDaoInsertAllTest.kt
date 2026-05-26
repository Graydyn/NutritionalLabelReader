package com.graydyn.tracker.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryEntryDaoInsertAllTest {

    private lateinit var db: TrackerDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TrackerDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() { db.close() }

    private fun entry(label: String, date: String, mealType: MealType, calories: Int) = DiaryEntry(
        date = date,
        mealType = mealType,
        label = label,
        sourceType = SourceType.DATABASE,
        foodId = 7L,
        unitType = FoodUnitType.GRAM,
        grams = 100f, count = null,
        calories = calories, protein = 5f, fat = 2f, carbs = 10f
    )

    @Test
    fun insertAll_writesAllEntries() = runTest {
        val dao = db.diaryEntryDao()
        dao.insertAll(
            listOf(
                entry("a", "2026-05-27", MealType.LUNCH, 100),
                entry("b", "2026-05-27", MealType.LUNCH, 200),
                entry("c", "2026-05-27", MealType.LUNCH, 300)
            )
        )

        val rows = dao.getEntriesForDate("2026-05-27").first()
        assertEquals(3, rows.size)
        assertEquals(setOf("a", "b", "c"), rows.map { it.label }.toSet())
        assertEquals(setOf(MealType.LUNCH), rows.map { it.mealType }.toSet())
        assertEquals(600, rows.sumOf { it.calories ?: 0 })
    }

    @Test
    fun insertAll_doesNotAffectOtherDates() = runTest {
        val dao = db.diaryEntryDao()
        dao.insert(entry("source-1", "2026-05-26", MealType.BREAKFAST, 200))
        dao.insert(entry("source-2", "2026-05-26", MealType.BREAKFAST, 150))

        dao.insertAll(
            listOf(
                entry("source-1", "2026-05-27", MealType.LUNCH, 200),
                entry("source-2", "2026-05-27", MealType.LUNCH, 150)
            )
        )

        val sourceRows = dao.getEntriesForDate("2026-05-26").first()
        assertEquals(2, sourceRows.size)
        val targetRows = dao.getEntriesForDate("2026-05-27").first()
        assertEquals(2, targetRows.size)
    }
}
