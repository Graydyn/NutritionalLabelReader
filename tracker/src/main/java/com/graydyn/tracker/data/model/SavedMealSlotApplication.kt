package com.graydyn.tracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "saved_meal_slot_applications",
    primaryKeys = ["savedMealId", "mealType"],
    foreignKeys = [
        ForeignKey(
            entity = SavedMeal::class,
            parentColumns = ["id"],
            childColumns = ["savedMealId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SavedMealSlotApplication(
    val savedMealId: Long,
    val mealType: MealType,
    val lastAppliedAt: Long
)
