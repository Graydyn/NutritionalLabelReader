package com.graydyn.tracker.data.seed

import com.graydyn.tracker.data.model.FoodUnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvSeederTest {

    @Test
    fun parseLine_legacyFiveColumnRow_returnsFoodWithFoundationalFalse() {
        val food = CsvSeeder.parseLine(
            line = "\"Hummus, commercial\",229,7.35,17.1,14.9",
            hasFoundationalColumn = false,
        )!!

        assertEquals("Hummus, commercial", food.name)
        assertEquals(FoodUnitType.GRAM, food.unitType)
        assertEquals(229f, food.caloriesPer100g)
        assertEquals(7.35f, food.proteinPer100g)
        assertEquals(17.1f, food.fatPer100g)
        assertEquals(14.9f, food.carbsPer100g)
        assertNull(food.caloriesPerItem)
        assertFalse(food.foundational)
        assertFalse(food.userAdded)
    }

    @Test
    fun parseLine_sixColumnFoundationalOne_marksFoundational() {
        val food = CsvSeeder.parseLine(
            line = "\"Chicken, breast, raw\",165,31,3.6,0,1",
            hasFoundationalColumn = true,
        )!!

        assertEquals("Chicken, breast, raw", food.name)
        assertTrue(food.foundational)
        assertFalse(food.userAdded)
    }

    @Test
    fun parseLine_sixColumnFoundationalZero_doesNotMarkFoundational() {
        val food = CsvSeeder.parseLine(
            line = "\"Some branded item\",100,5,5,5,0",
            hasFoundationalColumn = true,
        )!!

        assertFalse(food.foundational)
    }

    @Test
    fun parseLine_unquotedName_works() {
        val food = CsvSeeder.parseLine(
            line = "Apples,52,0.3,0.2,14",
            hasFoundationalColumn = false,
        )!!
        assertEquals("Apples", food.name)
        assertEquals(52f, food.caloriesPer100g)
    }

    @Test
    fun parseLine_blankLine_returnsNull() {
        assertNull(CsvSeeder.parseLine(line = "", hasFoundationalColumn = false))
    }

    @Test
    fun parseLine_tooFewColumns_returnsNull() {
        assertNull(CsvSeeder.parseLine(line = "Apples,52,0.3", hasFoundationalColumn = false))
    }

    @Test
    fun headerHasFoundationalColumn_detectsBothFormats() {
        assertTrue(
            CsvSeeder.headerHasFoundationalColumn("name,calories_kcal,protein_g,fat_g,carbs_g,foundational")
        )
        assertFalse(
            CsvSeeder.headerHasFoundationalColumn("name,calories_kcal,protein_g,fat_g,carbs_g")
        )
    }
}