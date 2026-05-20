package com.graydyn.nutritionlib

import com.graydyn.nutritionlib.model.Macros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextBlocksInterpreterTest {

    @Test
    fun englishParenthetical_isDetected() {
        assertEquals(30, TextBlocksInterpreter.detectGramsPerServing("Per serving (30g)"))
    }

    @Test
    fun englishWithSpaceBeforeG_isDetected() {
        assertEquals(15, TextBlocksInterpreter.detectGramsPerServing("Per 1 cookie (15 g)"))
    }

    @Test
    fun frenchParenthetical_isDetected() {
        assertEquals(28, TextBlocksInterpreter.detectGramsPerServing("Pour 1 portion (28g)"))
    }

    @Test
    fun upperCase_isDetected() {
        assertEquals(32, TextBlocksInterpreter.detectGramsPerServing("PER 2 TBSP (32 g)"))
    }

    @Test
    fun firstParenAfterAnchor_isCaptured() {
        // "(28g)" is the FIRST parenthetical after "Per"; "100g" appears earlier but
        // is not inside parens, so it is correctly skipped.
        assertEquals(28, TextBlocksInterpreter.detectGramsPerServing("Per 100g of product (28g)"))
    }

    @Test
    fun decimalGrams_areRoundedHalfUp() {
        assertEquals(28, TextBlocksInterpreter.detectGramsPerServing("Per serving (27.5g)"))
        assertEquals(27, TextBlocksInterpreter.detectGramsPerServing("Per serving (27.4g)"))
    }

    @Test
    fun missingParenthesis_returnsNull() {
        assertNull(TextBlocksInterpreter.detectGramsPerServing("Serving size: 30g"))
    }

    @Test
    fun missingGramsUnit_returnsNull() {
        assertNull(TextBlocksInterpreter.detectGramsPerServing("Per 100g"))
    }

    @Test
    fun nonGramUnitInsideParens_returnsNull() {
        assertNull(TextBlocksInterpreter.detectGramsPerServing("Per 1 oz (1/8 cup)"))
    }

    @Test
    fun anchorWordInsideOtherWord_doesNotMatch() {
        // "Performance" should NOT trigger the "per" anchor.
        assertNull(TextBlocksInterpreter.detectGramsPerServing("Performance metrics (15 ops)"))
    }

    @Test
    fun implausiblyLargeValue_returnsNull() {
        assertNull(TextBlocksInterpreter.detectGramsPerServing("Per serving (3000g)"))
    }

    @Test
    fun zeroGrams_returnsNull() {
        // 0g is implausible and almost certainly an OCR misread.
        assertNull(TextBlocksInterpreter.detectGramsPerServing("Per serving (0g)"))
    }

    @Test
    fun decimalFat_isCaptured() {
        val (macros, _) = TextBlocksInterpreter.readTextLines(listOf("Fat 0.4g"), Macros())
        assertEquals(0.4f, macros.fat, 0.001f)
    }

    @Test
    fun integerFat_stillWorks() {
        val (macros, _) = TextBlocksInterpreter.readTextLines(listOf("Fat 5g"), Macros())
        assertEquals(5f, macros.fat, 0.001f)
    }

    @Test
    fun caloriesWithoutGramUnit_stillWorks() {
        val (macros, _) = TextBlocksInterpreter.readTextLines(listOf("Calories 157"), Macros())
        assertEquals(157f, macros.calories, 0.001f)
    }

    @Test
    fun decimalProteinAndCarbs_areCaptured() {
        val (macros, _) = TextBlocksInterpreter.readTextLines(
            listOf("Protein 2.5g", "Carbohydrate 13.7g"),
            Macros()
        )
        assertEquals(2.5f, macros.protein, 0.001f)
        assertEquals(13.7f, macros.carbs, 0.001f)
    }

    @Test
    fun implausiblyLargeFat_isRejected() {
        val (macros, _) = TextBlocksInterpreter.readTextLines(listOf("Fat 249g"), Macros())
        assertEquals(-1f, macros.fat, 0f)
    }
}
