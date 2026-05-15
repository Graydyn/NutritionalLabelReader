package com.graydyn.tracker.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2To3Test {

    private val testDbName = "tracker-migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrackerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3_addsNewTablesAndPreservesExistingRows() {
        helper.createDatabase(testDbName, 2).use { v2 ->
            v2.execSQL(
                """
                INSERT INTO foods (name, unitType, caloriesPer100g, proteinPer100g, fatPer100g, carbsPer100g, caloriesPerItem, proteinPerItem, fatPerItem, carbsPerItem)
                VALUES ('Oats', 'GRAM', 379.0, 13.0, 7.0, 67.0, NULL, NULL, NULL, NULL)
                """.trimIndent()
            )
            v2.execSQL(
                """
                INSERT INTO diary_entries (date, mealType, label, sourceType, foodId, unitType, grams, count, calories, protein, fat, carbs)
                VALUES ('2026-05-14', 'BREAKFAST', 'Oats', 'DATABASE', 1, 'GRAM', 50.0, NULL, 190, 6.5, 3.5, 33.5)
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            testDbName,
            3,
            true,
            TrackerDatabase.MIGRATION_2_3
        )

        // Existing data preserved
        migrated.query("SELECT name FROM foods").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Oats", cursor.getString(0))
        }
        migrated.query("SELECT label FROM diary_entries").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Oats", cursor.getString(0))
        }

        // New tables exist and are empty
        migrated.query("SELECT COUNT(*) FROM saved_meals").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM saved_meal_items").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM saved_meal_slot_applications").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }

        // Index exists
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_saved_meal_items_savedMealId'"
        ).use { c ->
            assertEquals(true, c.moveToFirst())
        }
    }
}