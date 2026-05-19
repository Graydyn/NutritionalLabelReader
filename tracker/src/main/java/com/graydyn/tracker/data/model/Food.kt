package com.graydyn.tracker.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "foods")
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unitType: FoodUnitType,
    val caloriesPer100g: Float?,
    val proteinPer100g: Float?,
    val fatPer100g: Float?,
    val carbsPer100g: Float?,
    val caloriesPerItem: Float?,
    val proteinPerItem: Float?,
    val fatPerItem: Float?,
    val carbsPerItem: Float?,
    val caloriesPerServing: Float? = null,
    val proteinPerServing: Float? = null,
    val fatPerServing: Float? = null,
    val carbsPerServing: Float? = null,
    val gramsPerServing: Float? = null,
    val itemsPerServing: Float? = null,
    @ColumnInfo(defaultValue = "0") val foundational: Boolean = false,
    @ColumnInfo(defaultValue = "0") val userAdded: Boolean = false,
)

val Food.calories: Float?
    get() = when (unitType) {
        FoodUnitType.GRAM -> caloriesPer100g
        FoodUnitType.ITEM -> caloriesPerItem
        FoodUnitType.SERVING -> caloriesPerServing
    }

val Food.protein: Float?
    get() = when (unitType) {
        FoodUnitType.GRAM -> proteinPer100g
        FoodUnitType.ITEM -> proteinPerItem
        FoodUnitType.SERVING -> proteinPerServing
    }

val Food.fat: Float?
    get() = when (unitType) {
        FoodUnitType.GRAM -> fatPer100g
        FoodUnitType.ITEM -> fatPerItem
        FoodUnitType.SERVING -> fatPerServing
    }

val Food.carbs: Float?
    get() = when (unitType) {
        FoodUnitType.GRAM -> carbsPer100g
        FoodUnitType.ITEM -> carbsPerItem
        FoodUnitType.SERVING -> carbsPerServing
    }