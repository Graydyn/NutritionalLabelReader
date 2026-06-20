package com.graydyn.tracker.data.db

/** Per-day calorie sum returned by [DiaryEntryDao.getDailyCalorieTotals]. */
data class DailyCalorieTotal(
    val date: String,   // "yyyy-MM-dd"
    val total: Int
)
