package com.graydyn.tracker.data.seed

import android.content.Context
import android.util.Log
import com.graydyn.tracker.data.db.FoodDao
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.FoodUnitType

object CsvSeeder {
    private const val TAG = "CsvSeeder"

    suspend fun seed(context: Context, dao: FoodDao) {
        val foods = mutableListOf<Food>()
        context.assets.open("nutrition_all.csv").bufferedReader().use { reader ->
            val header = reader.readLine() ?: return
            val hasFoundational = headerHasFoundationalColumn(header)
            reader.forEachLine { line ->
                parseLine(line, hasFoundational)?.let { foods.add(it) }
            }
        }
        dao.insertAll(foods)
        Log.d(TAG, "Seeded ${foods.size} foods")
    }

    internal fun headerHasFoundationalColumn(header: String): Boolean {
        val cols = header.split(",").map { it.trim().lowercase() }
        return cols.lastOrNull() == "foundational"
    }

    /**
     * Parses a CSV line that may have a quoted food name containing commas.
     * Five-column format:  "name",calories,protein,fat,carbs
     * Six-column format:   "name",calories,protein,fat,carbs,foundational  (0 or 1)
     */
    internal fun parseLine(line: String, hasFoundationalColumn: Boolean): Food? {
        if (line.isBlank()) return null

        val name: String
        val rest: String

        if (line.startsWith("\"")) {
            val closeQuote = line.indexOf('"', 1)
            if (closeQuote == -1) return null
            name = line.substring(1, closeQuote)
            rest = if (closeQuote + 1 < line.length && line[closeQuote + 1] == ',') {
                line.substring(closeQuote + 2)
            } else {
                ""
            }
        } else {
            val firstComma = line.indexOf(',')
            if (firstComma == -1) return null
            name = line.substring(0, firstComma)
            rest = line.substring(firstComma + 1)
        }

        val parts = rest.split(",")
        val minCols = if (hasFoundationalColumn) 5 else 4
        if (parts.size < minCols) return null

        val foundational = if (hasFoundationalColumn) {
            parts[4].trim() == "1"
        } else {
            false
        }

        return Food(
            name = name.trim(),
            unitType = FoodUnitType.GRAM,
            caloriesPer100g = parseNullableFloat(parts[0]),
            proteinPer100g = parseNullableFloat(parts[1]),
            fatPer100g = parseNullableFloat(parts[2]),
            carbsPer100g = parseNullableFloat(parts[3]),
            caloriesPerItem = null,
            proteinPerItem = null,
            fatPerItem = null,
            carbsPerItem = null,
            foundational = foundational,
            userAdded = false,
        )
    }

    private fun parseNullableFloat(s: String): Float? {
        val v = s.trim().toFloatOrNull() ?: return null
        return if (v < 0f) null else v
    }
}