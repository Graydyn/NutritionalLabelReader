package com.graydyn.tracker.ui.stats

import com.graydyn.tracker.data.db.DailyCalorieTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsMathTest {

    @Test
    fun dayOfMonth_parsesDayField() {
        assertEquals(1, StatsMath.dayOfMonth("2026-06-01"))
        assertEquals(20, StatsMath.dayOfMonth("2026-06-20"))
        assertEquals(31, StatsMath.dayOfMonth("2026-01-31"))
    }

    @Test
    fun calorieSeries_bucketsByDayOfMonth() {
        val totals = listOf(
            DailyCalorieTotal("2026-06-01", 1800),
            DailyCalorieTotal("2026-06-03", 2200)
        )
        val series = StatsMath.calorieSeries(totals)
        assertEquals(mapOf(1 to 1800, 3 to 2200), series)
    }

    @Test
    fun weightSeries_carriesForwardFromSeed() {
        // Seed 180 before the month; updates on day 10 and day 20.
        val series = StatsMath.weightSeries(
            daysInMonth = 30,
            seedLbs = 180f,
            inMonth = mapOf(10 to 178f, 20 to 176f)
        )
        assertEquals(180f, series[1])
        assertEquals(180f, series[9])
        assertEquals(178f, series[10])
        assertEquals(178f, series[19])
        assertEquals(176f, series[20])
        assertEquals(176f, series[30])
        assertEquals(30, series.size)
    }

    @Test
    fun weightSeries_nullSeed_daysBeforeFirstWeightAbsent() {
        val series = StatsMath.weightSeries(
            daysInMonth = 30,
            seedLbs = null,
            inMonth = mapOf(5 to 175f)
        )
        assertFalse(series.containsKey(1))
        assertFalse(series.containsKey(4))
        assertEquals(175f, series[5])
        assertEquals(175f, series[30])
    }

    @Test
    fun weightSeries_emptyAndNullSeed_isEmpty() {
        val series = StatsMath.weightSeries(30, null, emptyMap())
        assertTrue(series.isEmpty())
    }

    @Test
    fun yRange_includesGoalAndPads() {
        // values 1800..2000, goal 2200 -> range must cover 1800 and 2200, padded.
        val r = StatsMath.yRange(listOf(1800f, 2000f), goal = 2200f, padFraction = 0.1f)
        assertTrue("min below data", r.min < 1800f)
        assertTrue("max above goal", r.max > 2200f)
    }

    @Test
    fun yRange_allEqual_usesFlatPad() {
        val r = StatsMath.yRange(listOf(180f, 180f), goal = null, flatPad = 1f)
        assertEquals(179f, r.min, 0.001f)
        assertEquals(181f, r.max, 0.001f)
    }

    @Test
    fun yRange_empty_returnsUnit() {
        val r = StatsMath.yRange(emptyList(), goal = null)
        assertEquals(0f, r.min, 0.001f)
        assertEquals(1f, r.max, 0.001f)
    }
}
