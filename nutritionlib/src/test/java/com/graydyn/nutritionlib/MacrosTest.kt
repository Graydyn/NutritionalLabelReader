package com.graydyn.nutritionlib

import com.graydyn.nutritionlib.model.Macros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacrosTest {

    @Test
    fun isComplete_default_requiresAllFourMacros() {
        val allFour = Macros(calories = 200, fat = 5, protein = 12, carbs = 25, gramsPerServing = -1)
        val missingCarbs = Macros(calories = 200, fat = 5, protein = 12, carbs = -1, gramsPerServing = -1)

        assertTrue(allFour.isComplete())
        assertFalse(missingCarbs.isComplete())
    }

    @Test
    fun isComplete_proteinOnlyTrue_ignoresFatAndCarbs() {
        val caloriesAndProteinOnly = Macros(calories = 200, fat = -1, protein = 12, carbs = -1, gramsPerServing = -1)
        val missingProtein = Macros(calories = 200, fat = -1, protein = -1, carbs = -1, gramsPerServing = -1)
        val missingCalories = Macros(calories = -1, fat = -1, protein = 12, carbs = -1, gramsPerServing = -1)

        assertTrue(caloriesAndProteinOnly.isComplete(proteinOnly = true))
        assertFalse(missingProtein.isComplete(proteinOnly = true))
        assertFalse(missingCalories.isComplete(proteinOnly = true))
    }

    @Test
    fun isComplete_proteinOnlyFalse_matchesDefault() {
        val twoOfFour = Macros(calories = 200, fat = -1, protein = 12, carbs = -1, gramsPerServing = -1)
        assertFalse(twoOfFour.isComplete(proteinOnly = false))
    }

    @Test
    fun noArgConstructor_setsGramsPerServingToMinusOne() {
        val empty = Macros()
        assertEquals(-1, empty.gramsPerServing)
    }

    @Test
    fun isComplete_ignoresGramsPerServing_inBothModes() {
        val withServingButMissingCarbs = Macros(calories = 200, fat = 5, protein = 12, carbs = -1, gramsPerServing = 30)
        assertFalse(withServingButMissingCarbs.isComplete())

        val proteinOnlyComplete = Macros(calories = 200, fat = -1, protein = 12, carbs = -1, gramsPerServing = 30)
        assertTrue(proteinOnlyComplete.isComplete(proteinOnly = true))

        val proteinOnlyMissingProtein = Macros(calories = 200, fat = -1, protein = -1, carbs = -1, gramsPerServing = 30)
        assertFalse(proteinOnlyMissingProtein.isComplete(proteinOnly = true))
    }
}
