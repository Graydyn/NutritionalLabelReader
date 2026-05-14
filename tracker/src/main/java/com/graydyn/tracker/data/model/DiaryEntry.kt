package com.graydyn.tracker.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }
enum class SourceType { DATABASE, SCANNED }

@Entity(
    tableName = "diary_entries",
    indices = [Index(value = ["date"])]
)
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val mealType: MealType,
    val label: String,
    val sourceType: SourceType,
    val foodId: Long?,
    val unitType: FoodUnitType,
    val grams: Float?,
    val count: Float?,
    val calories: Int?,
    val protein: Float?,
    val fat: Float?,
    val carbs: Float?
)
