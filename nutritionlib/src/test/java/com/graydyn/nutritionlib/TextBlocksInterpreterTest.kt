package com.graydyn.nutritionlib

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
}
