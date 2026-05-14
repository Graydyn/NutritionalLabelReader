package com.graydyn.tracker.data.db

import androidx.room.TypeConverter
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SourceType

class Converters {
    @TypeConverter
    fun mealTypeToString(value: MealType): String = value.name

    @TypeConverter
    fun stringToMealType(value: String): MealType = MealType.valueOf(value)

    @TypeConverter
    fun sourceTypeToString(value: SourceType): String = value.name

    @TypeConverter
    fun stringToSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun foodUnitTypeToString(value: FoodUnitType): String = value.name

    @TypeConverter
    fun stringToFoodUnitType(value: String): FoodUnitType = FoodUnitType.valueOf(value)
}
