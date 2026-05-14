package com.graydyn.tracker.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.graydyn.tracker.TrackerApplication
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SourceType
import com.graydyn.tracker.data.repository.DiaryRepository
import com.graydyn.tracker.data.repository.FoodRepository
import com.graydyn.tracker.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    application: Application,
    userPreferencesRepository: UserPreferencesRepository
) : AndroidViewModel(application) {

    private val db = TrackerDatabase.getInstance(application)
    private val foodRepo = FoodRepository(db.foodDao())
    private val diaryRepo = DiaryRepository(db.diaryEntryDao())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val searchResults: StateFlow<List<Food>> =
        _query
            .debounce(300)
            .mapLatest { q ->
                if (q.isBlank()) emptyList()
                else foodRepo.search(q)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedFood = MutableStateFlow<Food?>(null)
    val selectedFood: StateFlow<Food?> = _selectedFood.asStateFlow()

    private val _grams = MutableStateFlow("")
    val grams: StateFlow<String> = _grams.asStateFlow()

    val proteinOnly: StateFlow<Boolean> =
        userPreferencesRepository.proteinAndCaloriesOnly
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    fun onQueryChange(q: String) { _query.value = q }

    fun onSelectFood(food: Food) {
        _selectedFood.value = food
        _grams.value = ""
    }

    fun onGramsChange(g: String) { _grams.value = g }

    fun clearSelection() { _selectedFood.value = null }

    fun openCreateDialog() { _showCreateDialog.value = true }

    fun dismissCreateDialog() { _showCreateDialog.value = false }

    /**
     * Inserts a new food row, then auto-selects it on the screen so the user
     * can enter grams and log it. Sets _query to the new food's name so the
     * existing debounced search re-queries the DB and includes the new row.
     */
    fun createFood(
        name: String,
        calories: Float,
        protein: Float?,
        fat: Float?,
        carbs: Float?
    ) {
        // Close the dialog synchronously so a second tap cannot launch
        // a duplicate insert before the coroutine completes.
        _showCreateDialog.value = false
        viewModelScope.launch(Dispatchers.IO) {
            val food = Food(
                name = name.trim(),
                unitType = FoodUnitType.GRAM,
                caloriesPer100g = calories,
                proteinPer100g = protein,
                fatPer100g = fat,
                carbsPer100g = carbs,
                caloriesPerItem = null,
                proteinPerItem = null,
                fatPerItem = null,
                carbsPerItem = null
            )
            val id = foodRepo.add(food)
            val saved = food.copy(id = id)
            withContext(Dispatchers.Main) {
                _selectedFood.value = saved
                _grams.value = ""
                _query.value = saved.name
            }
        }
    }

    /** Returns true on success; false if input is invalid. */
    fun logEntry(date: String, mealType: MealType): Boolean {
        val food = _selectedFood.value ?: return false
        val grams = _grams.value.toFloatOrNull()?.takeIf { it > 0f } ?: return false
        val entry = DiaryEntry(
            date = date,
            mealType = mealType,
            label = food.name,
            sourceType = SourceType.DATABASE,
            foodId = food.id,
            unitType = FoodUnitType.GRAM,
            grams = grams,
            count = null,
            calories = food.caloriesPer100g?.let { (it * grams / 100f).toInt() },
            protein = food.proteinPer100g?.let { it * grams / 100f },
            fat = food.fatPer100g?.let { it * grams / 100f },
            carbs = food.carbsPer100g?.let { it * grams / 100f }
        )
        viewModelScope.launch(Dispatchers.IO) { diaryRepo.insert(entry) }
        return true
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                val trackerApp = app as TrackerApplication
                return SearchViewModel(app, trackerApp.userPreferencesRepository) as T
            }
        }
    }
}
