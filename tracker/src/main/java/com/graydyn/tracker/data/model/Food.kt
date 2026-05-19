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
    get() = if (unitType == FoodUnitType.ITEM) caloriesPerItem else caloriesPer100g

val Food.protein: Float?
    get() = if (unitType == FoodUnitType.ITEM) proteinPerItem else proteinPer100g

val Food.fat: Float?
    get() = if (unitType == FoodUnitType.ITEM) fatPerItem else fatPer100g

val Food.carbs: Float?
    get() = if (unitType == FoodUnitType.ITEM) carbsPerItem else carbsPer100g