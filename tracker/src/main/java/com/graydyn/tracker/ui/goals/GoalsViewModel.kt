package com.graydyn.tracker.ui.goals

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.graydyn.tracker.TrackerApplication
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.model.Goals
import com.graydyn.tracker.data.repository.GoalsRepository
import com.graydyn.tracker.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GoalsViewModel(
    application: Application,
    private val userPreferencesRepository: UserPreferencesRepository
) : AndroidViewModel(application) {

    private val repo = GoalsRepository(TrackerDatabase.getInstance(application).goalsDao())

    val goals: StateFlow<Goals?> =
        repo.getGoals()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val proteinOnly: StateFlow<Boolean> =
        userPreferencesRepository.proteinAndCaloriesOnly
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    var caloriesInput by mutableStateOf("")
    var proteinInput by mutableStateOf("")
    var fatInput by mutableStateOf("")
    var carbsInput by mutableStateOf("")

    fun loadIntoForm(goals: Goals) {
        caloriesInput = goals.caloriesGoal.toString()
        proteinInput = goals.proteinGoal.toString()
        fatInput = goals.fatGoal.toString()
        carbsInput = goals.carbsGoal.toString()
    }

    fun setProteinOnly(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setProteinAndCaloriesOnly(enabled)
        }
    }

    fun save() {
        val cal = caloriesInput.toIntOrNull() ?: return
        val prot = proteinInput.toIntOrNull() ?: return
        val current = goals.value
        val fat = if (proteinOnly.value) {
            fatInput.toIntOrNull() ?: current?.fatGoal ?: 0
        } else {
            fatInput.toIntOrNull() ?: return
        }
        val carbs = if (proteinOnly.value) {
            carbsInput.toIntOrNull() ?: current?.carbsGoal ?: 0
        } else {
            carbsInput.toIntOrNull() ?: return
        }
        val g = Goals(
            caloriesGoal = cal,
            proteinGoal = prot,
            fatGoal = fat,
            carbsGoal = carbs
        )
        viewModelScope.launch(Dispatchers.IO) { repo.upsert(g) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                val trackerApp = app as TrackerApplication
                return GoalsViewModel(app, trackerApp.userPreferencesRepository) as T
            }
        }
    }
}
