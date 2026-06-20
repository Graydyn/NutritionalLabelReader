package com.graydyn.tracker.ui.stats

import com.graydyn.tracker.data.db.DailyCalorieTotal

data class YRange(val min: Float, val max: Float)

/**
 * Pure (Android-free) chart math so it is unit-testable on the JVM.
 */
object StatsMath {

    /** Day-of-month (1-based) for a "yyyy-MM-dd" string. */
    fun dayOfMonth(date: String): Int =
        date.substringAfterLast('-').toInt()

    /** Bucket per-day calorie totals into day-of-month -> calories. */
    fun calorieSeries(totals: List<DailyCalorieTotal>): Map<Int, Int> =
        totals.associate { dayOfMonth(it.date) to it.total }

    /**
     * Carried-forward weight across every day 1..[daysInMonth].
     * Each day uses the latest known weight at or before it, starting from
     * [seedLbs] (most recent weight at or before the month start, or null).
     * Days before any known weight are absent.
     */
    fun weightSeries(
        daysInMonth: Int,
        seedLbs: Float?,
        inMonth: Map<Int, Float>
    ): Map<Int, Float> {
        val out = LinkedHashMap<Int, Float>()
        var current: Float? = seedLbs
        for (day in 1..daysInMonth) {
            inMonth[day]?.let { current = it }
            current?.let { out[day] = it }
        }
        return out
    }

    /**
     * Y range covering all [values] and (if non-null) [goal], padded by
     * [padFraction] of the span. All-equal/single value falls back to
     * value ± [flatPad]. No values at all -> YRange(0f, 1f).
     */
    fun yRange(
        values: List<Float>,
        goal: Float?,
        padFraction: Float = 0.1f,
        flatPad: Float = 1f
    ): YRange {
        val all = if (goal != null) values + goal else values
        if (all.isEmpty()) return YRange(0f, 1f)
        val lo = all.min()
        val hi = all.max()
        if (lo == hi) return YRange(lo - flatPad, hi + flatPad)
        val pad = (hi - lo) * padFraction
        return YRange(lo - pad, hi + pad)
    }
}
