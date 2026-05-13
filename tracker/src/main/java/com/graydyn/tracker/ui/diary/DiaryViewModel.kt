package com.graydyn.tracker.ui.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.graydyn.nutritionlib.model.Macros
import com.graydyn.tracker.TrackerApplication
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.Goals
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SourceType
import com.graydyn.tracker.data.repository.DiaryRepository
import com.graydyn.tracker.data.repository.GoalsRepository
import com.graydyn.tracker.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun logScannedEntry(macros: Macros, mealType: MealType) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = DiaryEntry(
                date = _selectedDate.value,
                mealType = mealType,
                label = "Scanned label",
                sourceType = SourceType.SCANNED,
                foodId = null,
                grams = null,
                calories = if (macros.calories != -1) macros.calories else null,
                protein = if (macros.protein != -1) macros.protein.toFloat() else null,
                fat = if (macros.fat != -1) macros.fat.toFloat() else null,
                carbs = if (macros.carbs != -1) macros.carbs.toFloat() else null
            )
            diaryRepo.insert(entry)
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
