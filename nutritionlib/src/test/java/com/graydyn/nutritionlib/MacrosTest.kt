package com.graydyn.nutritionlib

import com.graydyn.nutritionlib.model.Macros
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacrosTest {

    @Test
    fun isComplete_default_requiresAllFourMacros() {
        val allFour = Macros(calories = 200, fat = 5, protein = 12, carbs = 25)
        val missingCarbs = Macros(calories = 200, fat = 5, protein = 12, carbs = -1)

        assertTrue(allFour.isComplete())
        assertFalse(missingCarbs.isComplete())
    }

    @Test
    fun isComplete_proteinOnlyTrue_ignoresFatAndCarbs() {
        val caloriesAndProteinOnly = Macros(calories = 200, fat = -1, protein = 12, carbs = -1)
        val missingProtein = Macros(calories = 200, fat = -1, protein = -1, carbs = -1)
        val missingCalories = Macros(calories = -1, fat = -1, protein = 12, carbs = -1)

        assertTrue(caloriesAndProteinOnly.isComplete(proteinOnly = true))
        assertFalse(missingProtein.isComplete(proteinOnly = true))
        assertFalse(missingCalories.isComplete(proteinOnly = true))
    }

    @Test
    fun isComplete_proteinOnlyFalse_matchesDefault() {
        val twoOfFour = Macros(calories = 200, fat = -1, protein = 12, carbs = -1)
        assertFalse(twoOfFour.isComplete(proteinOnly = false))
    }
}
