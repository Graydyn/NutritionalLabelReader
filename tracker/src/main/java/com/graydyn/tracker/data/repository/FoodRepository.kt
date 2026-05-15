package com.graydyn.tracker.data.repository

import com.graydyn.tracker.data.db.FoodDao
import com.graydyn.tracker.data.model.Food

class FoodRepository(private val dao: FoodDao) {
    suspend fun search(query: String): List<Food> = dao.search(query)

    suspend fun getById(id: Long): Food? = dao.getById(id)

    suspend fun add(food: Food): Long = dao.insert(food)
}
