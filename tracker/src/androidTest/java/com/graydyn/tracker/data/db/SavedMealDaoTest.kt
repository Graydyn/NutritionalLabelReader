package com.graydyn.tracker.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SavedMeal
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.data.model.SavedMealSlotApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedMealDaoTest {

    private lateinit var db: TrackerDatabase
    private lateinit var dao: SavedMealDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TrackerDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.savedMealDao()
    }

    @After
    fun tearDown() { db.close() }

    private fun item(savedMealId: Long, position: Int, label: String, calories: Int) = SavedMealItem(
        savedMealId = savedMealId,
        position = position,
        label = label,
        foodId = null,
        unitType = FoodUnitType.GRAM,
        grams = 100f,
        count = null,
        calories = calories,
        protein = 10f,
        fat = 5f,
        carbs = 20f
    )

    @Test
    fun createSavedMealWithItems_writesAllRowsAndInitialSlotApplication() = runTest {
        val id = dao.createSavedMealWithItems(
            meal = SavedMeal(name = "Breakfast A", createdAt = 1_000L),
            itemsBuilder = { savedMealId ->
                listOf(
                    item(savedMealId, 0, "Oats", 200),
                    item(savedMealId, 1, "Berries", 80)
                )
            },
            initialSlotMealType = MealType.BREAKFAST,
            nowMillis = 1_000L
        )

        val items = dao.getItems(id)
        assertEquals(2, items.size)
        assertEquals("Oats", items[0].label)
        assertEquals("Berries", items[1].label)

        val summaries = dao.observeSummariesForSlot(MealType.BREAKFAST).first()
        assertEquals(1, summaries.size)
        val summary = summaries[0]
        assertEquals("Breakfast A", summary.name)
        assertEquals(2, summary.itemCount)
        assertEquals(280, summary.totalCalories)
        assertEquals(1_000L, summary.lastAppliedAt)
    }

    @Test
    fun observeSummariesForSlot_isOrderedByLastAppliedThenCreatedAt() = runTest {
        // Created first, never applied to LUNCH
        dao.createSavedMealWithItems(
            meal = SavedMeal(name = "M_old_unapplied", createdAt = 100L),
            itemsBuilder = { listOf(item(it, 0, "x", 100)) },
            initialSlotMealType = MealType.BREAKFAST,
            nowMillis = 100L
        )
        // Created second, applied to LUNCH at t=300
        val mLunchOld = dao.createSavedMealWithItems(
            meal = SavedMeal(name = "M_lunch_old", createdAt = 200L),
            itemsBuilder = { listOf(item(it, 0, "x", 100)) },
            initialSlotMealType = MealType.LUNCH,
            nowMillis = 300L
        )
        // Created third, applied to LUNCH at t=500 (most recent)
        dao.createSavedMealWithItems(
            meal = SavedMeal(name = "M_lunch_recent", createdAt = 400L),
            itemsBuilder = { listOf(item(it, 0, "x", 100)) },
            initialSlotMealType = MealType.LUNCH,
            nowMillis = 500L
        )

        val summaries = dao.observeSummariesForSlot(MealType.LUNCH).first()
        // Order: most-recently-applied to LUNCH first, then never-applied-to-LUNCH by createdAt DESC
        assertEquals(listOf("M_lunch_recent", "M_lunch_old", "M_old_unapplied"), summaries.map { it.name })
        assertEquals(500L, summaries[0].lastAppliedAt)
        assertEquals(300L, summaries[1].lastAppliedAt)
        assertNull(summaries[2].lastAppliedAt)
    }

    @Test
    fun upsertSlotApplication_replacesExistingRow() = runTest {
        val id = dao.createSavedMealWithItems(
            meal = SavedMeal(name = "M", createdAt = 1L),
            itemsBuilder = { listOf(item(it, 0, "x", 100)) },
            initialSlotMealType = MealType.DINNER,
            nowMillis = 1L
        )
        dao.upsertSlotApplication(SavedMealSlotApplication(id, MealType.DINNER, 999L))

        val updated = dao.observeSummariesForSlot(MealType.DINNER).first().single()
        assertEquals(999L, updated.lastAppliedAt)
    }

    @Test
    fun deleteSavedMeal_cascadesToItemsAndSlotApplications() = runTest {
        val id = dao.createSavedMealWithItems(
            meal = SavedMeal(name = "M", createdAt = 1L),
            itemsBuilder = { listOf(item(it, 0, "x", 100), item(it, 1, "y", 50)) },
            initialSlotMealType = MealType.BREAKFAST,
            nowMillis = 1L
        )

        dao.deleteSavedMeal(id)

        assertNull(dao.getSavedMeal(id))
        assertEquals(emptyList<SavedMealItem>(), dao.getItems(id))
        val summaries = dao.observeSummariesForSlot(MealType.BREAKFAST).first()
        assertEquals(emptyList<SavedMealSummary>(), summaries)
    }

    @Test
    fun replaceItemsTransactionally_swapsItems() = runTest {
        val id = dao.createSavedMealWithItems(
            meal = SavedMeal(name = "M", createdAt = 1L),
            itemsBuilder = { listOf(item(it, 0, "Old A", 100), item(it, 1, "Old B", 200)) },
            initialSlotMealType = MealType.SNACK,
            nowMillis = 1L
        )

        dao.replaceItems(
            savedMealId = id,
            newItems = listOf(
                item(id, 0, "New A", 50),
                item(id, 1, "New B", 75),
                item(id, 2, "New C", 25)
            )
        )

        val items = dao.getItems(id)
        assertEquals(listOf("New A", "New B", "New C"), items.map { it.label })
        val summary = dao.observeSummariesForSlot(MealType.SNACK).first().single()
        assertEquals(3, summary.itemCount)
        assertEquals(150, summary.totalCalories)
    }
}
