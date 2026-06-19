package com.graydyn.tracker.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.FoodUnitType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodDaoSearchTest {

    private lateinit var db: TrackerDatabase
    private lateinit var dao: FoodDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TrackerDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.foodDao()
    }

    @After
    fun tearDown() { db.close() }

    private fun food(
        name: String,
        lastAmount: Float? = null,
        foundational: Boolean = false,
        userAdded: Boolean = false,
    ) = Food(
        name = name,
        unitType = FoodUnitType.GRAM,
        caloriesPer100g = 100f,
        proteinPer100g = 10f,
        fatPer100g = 5f,
        carbsPer100g = 20f,
        caloriesPerItem = null,
        proteinPerItem = null,
        fatPerItem = null,
        carbsPerItem = null,
        lastAmount = lastAmount,
        foundational = foundational,
        userAdded = userAdded,
    )

    @Test
    fun search_prioritizesLoggedBeforeOverFoundationalNeverLogged() = runTest {
        dao.insert(food("chicken breast", foundational = true))      // never logged
        dao.insert(food("chicken thigh", lastAmount = 150f))         // logged before, plain

        val names = dao.search("chicken").map { it.name }

        assertEquals(listOf("chicken thigh", "chicken breast"), names)
    }
}
