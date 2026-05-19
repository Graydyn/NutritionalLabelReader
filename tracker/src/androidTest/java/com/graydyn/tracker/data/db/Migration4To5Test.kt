package com.graydyn.tracker.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {

    private val testDbName = "tracker-migration-4to5-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrackerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate4To5_addsNewColumnsAndPreservesExistingRows() {
        helper.createDatabase(testDbName, 4).use { v4 ->
            v4.execSQL(
                """
                INSERT INTO foods (name, unitType, caloriesPer100g, proteinPer100g, fatPer100g, carbsPer100g, caloriesPerItem, proteinPerItem, fatPerItem, carbsPerItem, foundational, userAdded)
                VALUES ('Oats', 'GRAM', 379.0, 13.0, 7.0, 67.0, NULL, NULL, NULL, NULL, 1, 0)
                """.trimIndent()
            )
            v4.execSQL(
                """
                INSERT INTO diary_entries (date, mealType, label, sourceType, foodId, unitType, grams, count, calories, protein, fat, carbs)
                VALUES ('2026-05-19', 'BREAKFAST', 'Oats', 'DATABASE', 1, 'GRAM', 50.0, NULL, 190, 6.5, 3.5, 33.5)
                """.trimIndent()
            )
            v4.execSQL(
                """
                INSERT INTO saved_meals (name, createdAt) VALUES ('M', 1000)
                """.trimIndent()
            )
            v4.execSQL(
                """
                INSERT INTO saved_meal_items (savedMealId, position, label, foodId, unitType, grams, count, calories, protein, fat, carbs)
                VALUES (1, 0, 'Oats', 1, 'GRAM', 50.0, NULL, 190, 6.5, 3.5, 33.5)
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            testDbName,
            5,
            true,
            TrackerDatabase.MIGRATION_4_5
        )

        // Existing data preserved
        migrated.query("SELECT name FROM foods").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals("Oats", c.getString(0))
        }

        // New foods columns exist and are null on old rows
        migrated.query(
            "SELECT caloriesPerServing, proteinPerServing, fatPerServing, carbsPerServing, gramsPerServing, itemsPerServing FROM foods"
        ).use { c ->
            assertEquals(true, c.moveToFirst())
            for (i in 0..5) {
                assertEquals("column $i should be null on old row", true, c.isNull(i))
            }
        }

        // diary_entries gains servings
        migrated.query("SELECT servings FROM diary_entries").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals(true, c.isNull(0))
        }

        // saved_meal_items gains servings
        migrated.query("SELECT servings FROM saved_meal_items").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals(true, c.isNull(0))
        }
    }
}