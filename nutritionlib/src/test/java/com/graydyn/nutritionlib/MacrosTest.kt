package com.graydyn.nutritionlib

import com.graydyn.nutritionlib.model.Macros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacrosTest {

    @Test
    fun isComplete_default_requiresAllFourMacros() {
        val allFour = Macros(calories = 200f, fat = 5f, protein = 12f, carbs = 25f, gramsPerServing = -1)
        val missingCarbs = Macros(calories = 200f, fat = 5f, protein = 12f, carbs = -1f, gramsPerServing = -1)

        assertTrue(allFour.isComplete())
        assertFalse(missingCarbs.isComplete())
    }

    @Test
    fun isComplete_proteinOnlyTrue_ignoresFatAndCarbs() {
        val caloriesAndProteinOnly = Macros(calories = 200f, fat = -1f, protein = 12f, carbs = -1f, gramsPerServing = -1)
        val missingProtein = Macros(calories = 200f, fat = -1f, protein = -1f, carbs = -1f, gramsPerServing = -1)
        val missingCalories = Macros(calories = -1f, fat = -1f, protein = 12f, carbs = -1f, gramsPerServing = -1)

        assertTrue(caloriesAndProteinOnly.isComplete(proteinOnly = true))
        assertFalse(missingProtein.isComplete(proteinOnly = true))
        assertFalse(missingCalories.isComplete(proteinOnly = true))
    }

    @Test
    fun isComplete_proteinOnlyFalse_matchesDefault() {
        val twoOfFour = Macros(calories = 200f, fat = -1f, protein = 12f, carbs = -1f, gramsPerServing = -1)
        assertFalse(twoOfFour.isComplete(proteinOnly = false))
    }

    @Test
    fun noArgConstructor_setsGramsPerServingToMinusOne() {
        val empty = Macros()
        assertEquals(-1, empty.gramsPerServing)
    }

    @Test
    fun isComplete_ignoresGramsPerServing_inBothModes() {
        val withServingButMissingCarbs = Macros(calories = 200f, fat = 5f, protein = 12f, carbs = -1f, gramsPerServing = 30)
        assertFalse(withServingButMissingCarbs.isComplete())

        val proteinOnlyComplete = Macros(calories = 200f, fat = -1f, protein = 12f, carbs = -1f, gramsPerServing = 30)
        assertTrue(proteinOnlyComplete.isComplete(proteinOnly = true))

        val proteinOnlyMissingProtein = Macros(calories = 200f, fat = -1f, protein = -1f, carbs = -1f, gramsPerServing = 30)
        assertFalse(proteinOnlyMissingProtein.isComplete(proteinOnly = true))
    }

    @Test
    fun noArgConstructor_setsAllMacrosToMinusOneFloat() {
        val empty = Macros()
        assertEquals(-1f, empty.calories, 0f)
        assertEquals(-1f, empty.fat, 0f)
        assertEquals(-1f, empty.protein, 0f)
        assertEquals(-1f, empty.carbs, 0f)
        assertEquals(-1, empty.gramsPerServing)
    }
}
