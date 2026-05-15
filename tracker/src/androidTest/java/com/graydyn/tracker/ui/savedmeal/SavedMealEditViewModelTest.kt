package com.graydyn.tracker.ui.savedmeal

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SourceType
import com.graydyn.tracker.data.repository.SavedMealRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedMealEditViewModelTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: TrackerDatabase
    private lateinit var repo: SavedMealRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(app, TrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SavedMealRepository(db, db.savedMealDao(), db.diaryEntryDao())
    }

    @After
    fun teardown() { db.close() }

    private fun gramEntry(label: String, calories: Int, foodId: Long? = null) = DiaryEntry(
        date = "2026-05-14",
        mealType = MealType.BREAKFAST,
        label = label,
        sourceType = if (foodId != null) SourceType.DATABASE else SourceType.SCANNED,
        foodId = foodId,
        unitType = FoodUnitType.GRAM,
        grams = 100f, count = null,
        calories = calories, protein = 5f, fat = 2f, carbs = 10f
    )

    @Test
    fun saveBlockedWhenItemsEmpty() = runTest {
        val mealId = repo.saveFromDiaryEntries(
            "M", MealType.BREAKFAST, listOf(gramEntry("x", 100, foodId = null)), 1L
        )
        val vm = SavedMealEditViewModel(app, mealId, repo) { id -> db.foodDao().getById(id) }
        // Wait for init load
        vm.items.firstOrNull { it.isNotEmpty() }
        vm.deleteItem(vm.items.value.single())
        vm.save()
        // wait for save error to populate
        val err = vm.saveError.firstOrNull { it != null }
        assertEquals("A meal must contain at least one food.", err)
    }

    @Test
    fun saveRenumbersPositions() = runTest {
        val mealId = repo.saveFromDiaryEntries(
            "Old",
            MealType.BREAKFAST,
            listOf(gramEntry("a", 100, foodId = null), gramEntry("b", 200, foodId = null), gramEntry("c", 300, foodId = null)),
            1L
        )
        val vm = SavedMealEditViewModel(app, mealId, repo) { id -> db.foodDao().getById(id) }
        vm.items.firstOrNull { it.size == 3 }
        // remove the middle item
        vm.deleteItem(vm.items.value[1])
        vm.rename("New name")
        vm.save()
        vm.saved.firstOrNull { it }
        val items = db.savedMealDao().getItems(mealId)
        assertEquals(listOf("a" to 0, "c" to 1), items.map { it.label to it.position })
        assertEquals("New name", db.savedMealDao().getSavedMeal(mealId)?.name)
    }

    @Test
    fun updateQuantityOrphanScalesProportionally() = runTest {
        val baseEntry = DiaryEntry(
            date = "2026-05-14", mealType = MealType.BREAKFAST,
            label = "x", sourceType = SourceType.SCANNED, foodId = null,
            unitType = FoodUnitType.GRAM, grams = 200f, count = null,
            calories = 100, protein = 5f, fat = 2f, carbs = 10f
        )
        val mealId = repo.saveFromDiaryEntries("M", MealType.BREAKFAST, listOf(baseEntry), 1L)
        val vm = SavedMealEditViewModel(app, mealId, repo) { _ -> null }
        val initial = vm.items.firstOrNull { it.isNotEmpty() } ?: emptyList()
        val itemId = initial.single().id
        vm.updateQuantity(itemId, 100f) // halve the quantity
        // Wait for the new value to settle
        val updated = vm.items.firstOrNull { list -> list.singleOrNull()?.calories == 50 }
        val resulting = updated!!.single()
        assertEquals(100f, resulting.grams!!, 0.001f)
        assertEquals(50, resulting.calories) // 100 -> halved
    }

    @Test
    fun updateQuantityWithLiveFoodRecomputesFromCatalog() = runTest {
        val food = Food(
            id = 0, name = "Live", unitType = FoodUnitType.GRAM,
            caloriesPer100g = 400f, proteinPer100g = 30f, fatPer100g = 10f, carbsPer100g = 50f,
            caloriesPerItem = null, proteinPerItem = null, fatPerItem = null, carbsPerItem = null
        )
        val foodId = db.foodDao().insert(food)
        val mealId = repo.saveFromDiaryEntries(
            "M", MealType.BREAKFAST,
            listOf(gramEntry("Live", 200, foodId = foodId)), 1L
        )
        val vm = SavedMealEditViewModel(app, mealId, repo) { id -> db.foodDao().getById(id) }
        val initial = vm.items.firstOrNull { it.isNotEmpty() } ?: emptyList()
        val itemId = initial.single().id
        vm.updateQuantity(itemId, 250f)
        val updated = vm.items.firstOrNull { list -> list.singleOrNull()?.calories == 1000 }
        val resulting = updated!!.single()
        assertEquals(250f, resulting.grams!!, 0.001f)
        assertEquals(1000, resulting.calories)
    }
}