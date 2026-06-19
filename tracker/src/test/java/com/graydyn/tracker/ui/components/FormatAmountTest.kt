package com.graydyn.tracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatAmountTest {

    @Test
    fun wholeNumber_dropsTrailingDecimal() {
        assertEquals("100", formatAmount(100.0f))
    }

    @Test
    fun decimal_isPreserved() {
        assertEquals("1.5", formatAmount(1.5f))
    }

    @Test
    fun smallWholeNumber_formatsCleanly() {
        assertEquals("1", formatAmount(1.0f))
    }
}
