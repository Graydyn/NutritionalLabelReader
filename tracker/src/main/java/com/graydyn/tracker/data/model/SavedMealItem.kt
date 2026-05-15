package com.graydyn.tracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_meal_items",
    foreignKeys = [
        ForeignKey(
            entity = SavedMeal::class,
            parentColumns = ["id"],
            childColumns = ["savedMealId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["savedMealId"])]
)
data class SavedMealItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedMealId: Long,
    val position: Int,
    val label: String,
    val foodId: Long?,
    val unitType: FoodUnitType,
    val grams: Float?,
    val count: Float?,
    val calories: Int?,
    val protein: Float?,
    val fat: Float?,
    val carbs: Float?
)
