package com.graydyn.tracker.ui.savedmeal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.SavedMeal
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.data.repository.FoodRepository
import com.graydyn.tracker.data.repository.SavedMealRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SavedMealEditViewModel(
    application: Application,
    private val savedMealId: Long,
    private val savedMealRepo: SavedMealRepository,
    private val foodLookup: suspend (Long) -> Food?
) : AndroidViewModel(application) {

    private val _meal = MutableStateFlow<SavedMeal?>(null)
    val meal: StateFlow<SavedMeal?> = _meal.asStateFlow()

    private val _items = MutableStateFlow<List<SavedMealItem>>(emptyList())
    val items: StateFlow<List<SavedMealItem>> = _items.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _meal.value = savedMealRepo.getSavedMeal(savedMealId)
            _items.value = savedMealRepo.getItems(savedMealId)
        }
    }

    fun rename(newName: String) {
        val current = _meal.value ?: return
        _meal.value = current.copy(name = newName)
    }

    fun deleteItem(item: SavedMealItem) {
        _items.value = _items.value.filterNot { it.id == item.id }
    }

    fun updateQuantity(itemId: Long, newQuantity: Float) {
        if (newQuantity <= 0f) return
        viewModelScope.launch(Dispatchers.IO) {
            val foodId = _items.value.firstOrNull { it.id == itemId }?.foodId
            val food = foodId?.let { foodLookup(it) }
            _items.update { current ->
                current.map { item ->
                    if (item.id != itemId) item else applyQuantityChange(item, newQuantity, food)
                }
            }
        }
    }

    private fun applyQuantityChange(item: SavedMealItem, newQty: Float, food: Food?): SavedMealItem {
        if (food != null) {
            return when (item.unitType) {
                FoodUnitType.GRAM -> item.copy(
                    grams = newQty,
                    calories = food.caloriesPer100g?.let { (it * newQty / 100f).toInt() },
                    protein = food.proteinPer100g?.let { it * newQty / 100f },
                    fat = food.fatPer100g?.let { it * newQty / 100f },
                    carbs = food.carbsPer100g?.let { it * newQty / 100f }
                )
                FoodUnitType.ITEM -> item.copy(
                    count = newQty,
                    calories = food.caloriesPerItem?.let { (it * newQty).toInt() },
                    protein = food.proteinPerItem?.let { it * newQty },
                    fat = food.fatPerItem?.let { it * newQty },
                    carbs = food.carbsPerItem?.let { it * newQty }
                )
                FoodUnitType.SERVING -> item.copy(
                    servings = newQty,
                    calories = food.caloriesPerServing?.let { (it * newQty).toInt() },
                    protein = food.proteinPerServing?.let { it * newQty },
                    fat = food.fatPerServing?.let { it * newQty },
                    carbs = food.carbsPerServing?.let { it * newQty }
                )
            }
        }
        // Orphan: scale snapshot proportionally
        val oldQty = (item.grams ?: item.count ?: item.servings ?: 0f).coerceAtLeast(0.001f)
        val ratio = newQty / oldQty
        return item.copy(
            grams = item.grams?.let { newQty },
            count = item.count?.let { newQty },
            servings = item.servings?.let { newQty },
            calories = item.calories?.let { (it * ratio).toInt() },
            protein = item.protein?.let { it * ratio },
            fat = item.fat?.let { it * ratio },
            carbs = item.carbs?.let { it * ratio }
        )
    }

    fun addPickedFood(food: Food, quantity: Float) {
        val unitType = food.unitType
        val newItem = when (unitType) {
            FoodUnitType.GRAM -> SavedMealItem(
                savedMealId = savedMealId,
                position = _items.value.size,
                label = food.name,
                foodId = food.id,
                unitType = FoodUnitType.GRAM,
                grams = quantity, count = null,
                calories = food.caloriesPer100g?.let { (it * quantity / 100f).toInt() },
                protein = food.proteinPer100g?.let { it * quantity / 100f },
                fat = food.fatPer100g?.let { it * quantity / 100f },
                carbs = food.carbsPer100g?.let { it * quantity / 100f }
            )
            FoodUnitType.ITEM -> SavedMealItem(
                savedMealId = savedMealId,
                position = _items.value.size,
                label = food.name,
                foodId = food.id,
                unitType = FoodUnitType.ITEM,
                grams = null, count = quantity,
                calories = food.caloriesPerItem?.let { (it * quantity).toInt() },
                protein = food.proteinPerItem?.let { it * quantity },
                fat = food.fatPerItem?.let { it * quantity },
                carbs = food.carbsPerItem?.let { it * quantity }
            )
            FoodUnitType.SERVING -> SavedMealItem(
                savedMealId = savedMealId,
                position = _items.value.size,
                label = food.name,
                foodId = food.id,
                unitType = FoodUnitType.SERVING,
                grams = null, count = null, servings = quantity,
                calories = food.caloriesPerServing?.let { (it * quantity).toInt() },
                protein = food.proteinPerServing?.let { it * quantity },
                fat = food.fatPerServing?.let { it * quantity },
                carbs = food.carbsPerServing?.let { it * quantity }
            )
        }
        _items.value = _items.value + newItem
    }

    fun handlePickedFoodById(foodId: Long, quantity: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val food = foodLookup(foodId) ?: return@launch
            addPickedFood(food, quantity)
        }
    }

    fun save() {
        val current = _meal.value ?: return
        val list = _items.value
        if (list.isEmpty()) {
            _saveError.value = "A meal must contain at least one food."
            return
        }
        _saveError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val renumbered = list.mapIndexed { index, item -> item.copy(position = index) }
            savedMealRepo.renameAndReplaceItems(current.id, current.name, renumbered)
            _saved.value = true
        }
    }

    companion object {
        fun factory(savedMealId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!! as Application
                val db = TrackerDatabase.getInstance(app)
                val repo = SavedMealRepository(db, db.savedMealDao(), db.diaryEntryDao())
                val foodRepo = FoodRepository(db.foodDao())
                return SavedMealEditViewModel(
                    application = app,
                    savedMealId = savedMealId,
                    savedMealRepo = repo,
                    foodLookup = { id -> foodRepo.getById(id) }
                ) as T
            }
        }
    }
}
