package com.graydyn.tracker.ui.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.withTransaction
import com.graydyn.nutritionlib.model.Macros
import com.graydyn.tracker.TrackerApplication
import com.graydyn.tracker.data.db.SavedMealSummary
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.Goals
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.data.model.SourceType
import com.graydyn.tracker.data.repository.DiaryRepository
import com.graydyn.tracker.data.repository.FoodRepository
import com.graydyn.tracker.data.repository.GoalsRepository
import com.graydyn.tracker.data.repository.SavedMealRepository
import com.graydyn.tracker.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MacroTotals(
    val calories: Int = 0,
    val protein: Float = 0f,
    val fat: Float = 0f,
    val carbs: Float = 0f
)

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModel(
    application: Application,
    userPreferencesRepository: UserPreferencesRepository
) : AndroidViewModel(application) {

    private val db = TrackerDatabase.getInstance(application)
    private val diaryRepo = DiaryRepository(db.diaryEntryDao())
    private val goalsRepo = GoalsRepository(db.goalsDao())
    private val foodRepo = FoodRepository(db.foodDao())
    private val savedMealRepo = SavedMealRepository(
        database = db,
        savedMealDao = db.savedMealDao(),
        diaryEntryDao = db.diaryEntryDao()
    )

    private val _saveMealRequest = MutableStateFlow<MealType?>(null)
    val saveMealRequest: StateFlow<MealType?> = _saveMealRequest.asStateFlow()

    fun openSaveMealDialog(mealType: MealType) { _saveMealRequest.value = mealType }
    fun dismissSaveMealDialog() { _saveMealRequest.value = null }

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()
    fun consumeSnackbar() { _snackbarMessage.value = null }

    fun saveCurrentMealAsSavedMeal(mealType: MealType, name: String) {
        val entries = entriesByMeal.value[mealType].orEmpty()
        if (entries.isEmpty()) {
            _saveMealRequest.value = null
            return
        }
        _saveMealRequest.value = null
        viewModelScope.launch(Dispatchers.IO) {
            savedMealRepo.saveFromDiaryEntries(
                name = name,
                sourceMealType = mealType,
                entries = entries,
                nowMillis = System.currentTimeMillis()
            )
            _snackbarMessage.value = "Saved as '$name'"
        }
    }

    private val _pickerOpenForSlot = MutableStateFlow<MealType?>(null)
    val pickerOpenForSlot: StateFlow<MealType?> = _pickerOpenForSlot.asStateFlow()

    fun openSavedMealPicker(mealType: MealType) { _pickerOpenForSlot.value = mealType }
    fun dismissSavedMealPicker() { _pickerOpenForSlot.value = null }

    val pickerSummaries: StateFlow<List<SavedMealSummary>> =
        _pickerOpenForSlot
            .flatMapLatest { meal ->
                if (meal == null) flowOf(emptyList())
                else savedMealRepo.observeSummariesForSlot(meal)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _expandedSavedMealItems = MutableStateFlow<Map<Long, List<SavedMealItem>>>(emptyMap())
    val expandedSavedMealItems: StateFlow<Map<Long, List<SavedMealItem>>> = _expandedSavedMealItems.asStateFlow()

    fun expandSavedMeal(savedMealId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = savedMealRepo.getItems(savedMealId)
            _expandedSavedMealItems.update { it + (savedMealId to items) }
        }
    }

    fun collapseSavedMeal(savedMealId: Long) {
        _expandedSavedMealItems.update { it - savedMealId }
    }

    fun applySavedMeal(savedMealId: Long, mealType: MealType) {
        val date = _selectedDate.value
        viewModelScope.launch(Dispatchers.IO) {
            val n = savedMealRepo.applyToSlot(
                savedMealId = savedMealId,
                mealType = mealType,
                date = date,
                nowMillis = System.currentTimeMillis()
            )
            val mealLabel = mealType.name.lowercase().replaceFirstChar { it.uppercase() }
            _snackbarMessage.value = "Added $n items to $mealLabel"
        }
        _pickerOpenForSlot.value = null
        _expandedSavedMealItems.value = emptyMap()
    }

    fun renameSavedMeal(savedMealId: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) { savedMealRepo.rename(savedMealId, newName) }
    }

    fun deleteSavedMeal(savedMealId: Long) {
        viewModelScope.launch(Dispatchers.IO) { savedMealRepo.delete(savedMealId) }
    }

    private val _scanInProgress = MutableStateFlow<Macros?>(null)
    val scanInProgress: StateFlow<Macros?> = _scanInProgress.asStateFlow()

    fun onScanResult(macros: Macros) { _scanInProgress.value = macros }
    fun dismissScannedFoodDialog() { _scanInProgress.value = null }

    private val _selectedDate = MutableStateFlow(todayString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val entriesByMeal: StateFlow<Map<MealType, List<DiaryEntry>>> =
        _selectedDate
            .flatMapLatest { date -> diaryRepo.getEntriesForDate(date) }
            .map { entries -> entries.groupBy { it.mealType } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val dailyTotals: StateFlow<MacroTotals> =
        entriesByMeal
            .map { grouped ->
                val all = grouped.values.flatten()
                MacroTotals(
                    calories = all.sumOf { it.calories ?: 0 },
                    protein = all.sumOf { (it.protein ?: 0f).toDouble() }.toFloat(),
                    fat = all.sumOf { (it.fat ?: 0f).toDouble() }.toFloat(),
                    carbs = all.sumOf { (it.carbs ?: 0f).toDouble() }.toFloat()
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MacroTotals())

    val goals: StateFlow<Goals?> =
        goalsRepo.getGoals()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val proteinOnly: StateFlow<Boolean> =
        userPreferencesRepository.proteinAndCaloriesOnly
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun logScannedFood(
        name: String,
        unitType: FoodUnitType,
        calories: Float,
        protein: Float?,
        fat: Float?,
        carbs: Float?,
        quantity: Float,
        mealType: MealType
    ) {
        _scanInProgress.value = null
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
            val food = when (unitType) {
                FoodUnitType.GRAM -> Food(
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
                FoodUnitType.ITEM -> Food(
                    name = name.trim(),
                    unitType = FoodUnitType.ITEM,
                    caloriesPer100g = null,
                    proteinPer100g = null,
                    fatPer100g = null,
                    carbsPer100g = null,
                    caloriesPerItem = calories,
                    proteinPerItem = protein,
                    fatPerItem = fat,
                    carbsPerItem = carbs
                )
            }
            val foodId = foodRepo.add(food)
            val entry = when (unitType) {
                FoodUnitType.GRAM -> DiaryEntry(
                    date = _selectedDate.value,
                    mealType = mealType,
                    label = food.name,
                    sourceType = SourceType.DATABASE,
                    foodId = foodId,
                    unitType = FoodUnitType.GRAM,
                    grams = quantity,
                    count = null,
                    calories = food.caloriesPer100g?.let { (it * quantity / 100f).toInt() },
                    protein = food.proteinPer100g?.let { it * quantity / 100f },
                    fat = food.fatPer100g?.let { it * quantity / 100f },
                    carbs = food.carbsPer100g?.let { it * quantity / 100f }
                )
                FoodUnitType.ITEM -> DiaryEntry(
                    date = _selectedDate.value,
                    mealType = mealType,
                    label = food.name,
                    sourceType = SourceType.DATABASE,
                    foodId = foodId,
                    unitType = FoodUnitType.ITEM,
                    grams = null,
                    count = quantity,
                    calories = food.caloriesPerItem?.let { (it * quantity).toInt() },
                    protein = food.proteinPerItem?.let { it * quantity },
                    fat = food.fatPerItem?.let { it * quantity },
                    carbs = food.carbsPerItem?.let { it * quantity }
                )
            }
            diaryRepo.insert(entry)
            }
        }
    }

    fun deleteEntry(entry: DiaryEntry) {
        viewModelScope.launch(Dispatchers.IO) { diaryRepo.delete(entry) }
    }

    fun navigateDate(daysOffset: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val currentDate = sdf.parse(_selectedDate.value) ?: return
        val calendar = java.util.Calendar.getInstance()
        calendar.time = currentDate
        calendar.add(java.util.Calendar.DAY_OF_YEAR, daysOffset)
        _selectedDate.value = sdf.format(calendar.time)
    }

    fun goToToday() {
        _selectedDate.value = todayString()
    }

    companion object {
        fun todayString(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                val trackerApp = app as TrackerApplication
                return DiaryViewModel(app, trackerApp.userPreferencesRepository) as T
            }
        }
    }
}
