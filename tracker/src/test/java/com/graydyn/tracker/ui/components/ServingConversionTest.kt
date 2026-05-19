package com.graydyn.tracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServingConversionTest {

    @Test
    fun perServingToPer100g_null_returnsNull() {
        assertNull(perServingToPer100g(null, 50f))
    }

    @Test
    fun perServingToPer100g_zero_returnsZero() {
        assertEquals(0f, perServingToPer100g(0f, 50f)!!, 0.001f)
    }

    @Test
    fun perServingToPer100g_divides() {
        assertEquals(400f, perServingToPer100g(200f, 50f)!!, 0.001f)
    }

    @Test
    fun perServingToPerItem_null_returnsNull() {
        assertNull(perServingToPerItem(null, 2f))
    }

    @Test
    fun perServingToPerItem_zero_returnsZero() {
        assertEquals(0f, perServingToPerItem(0f, 2f)!!, 0.001f)
    }

    @Test
    fun perServingToPerItem_divides() {
        assertEquals(100f, perServingToPerItem(200f, 2f)!!, 0.001f)
    }
}
