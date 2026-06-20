package com.graydyn.tracker.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.graydyn.tracker.TrackerApplication
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.repository.DiaryRepository
import com.graydyn.tracker.data.repository.GoalsRepository
import com.graydyn.tracker.data.repository.WeightRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MonthStats(
    val year: Int,
    val month: Int,                 // 1-12
    val daysInMonth: Int,
    val calorieGoal: Int?,
    val dailyCalories: Map<Int, Int>,
    val dailyWeight: Map<Int, Float>
)

private data class YearMonthKey(val year: Int, val month: Int) // month 1-12

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TrackerDatabase.getInstance(application)
    private val diaryRepo = DiaryRepository(db.diaryEntryDao())
    private val weightRepo = WeightRepository(db.weightEntryDao())
    private val goalsRepo = GoalsRepository(db.goalsDao())

    private val _selected = MutableStateFlow(currentMonth())

    private fun pad2(n: Int) = n.toString().padStart(2, '0')

    private fun daysIn(year: Int, month: Int): Int {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun monthStart(k: YearMonthKey) = "${k.year}-${pad2(k.month)}-01"
    private fun monthEnd(k: YearMonthKey) = "${k.year}-${pad2(k.month)}-${pad2(daysIn(k.year, k.month))}"

    val selectedLabel: StateFlow<String> =
        _selected
            .map { k ->
                val cal = Calendar.getInstance().apply { clear(); set(k.year, k.month - 1, 1) }
                SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val monthStats: StateFlow<MonthStats> =
        _selected
            .flatMapLatest { k ->
                val start = monthStart(k)
                val end = monthEnd(k)
                val days = daysIn(k.year, k.month)
                combine(
                    diaryRepo.getDailyCalorieTotals(start, end),
                    weightRepo.getWeightsInRange(start, end),
                    weightRepo.observeEffectiveWeight(start),
                    goalsRepo.getGoals()
                ) { calorieTotals, weights, seed, goals ->
                    val inMonth = weights.associate { StatsMath.dayOfMonth(it.date) to it.weightLbs }
                    MonthStats(
                        year = k.year,
                        month = k.month,
                        daysInMonth = days,
                        calorieGoal = goals?.caloriesGoal,
                        dailyCalories = StatsMath.calorieSeries(calorieTotals),
                        dailyWeight = StatsMath.weightSeries(days, seed?.weightLbs, inMonth)
                    )
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                MonthStats(currentMonth().year, currentMonth().month, 30, null, emptyMap(), emptyMap())
            )

    fun prevMonth() = step(-1)
    fun nextMonth() = step(1)

    private fun step(delta: Int) {
        val k = _selected.value
        val cal = Calendar.getInstance().apply { clear(); set(k.year, k.month - 1, 1) }
        cal.add(Calendar.MONTH, delta)
        _selected.value = YearMonthKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    companion object {
        private fun currentMonth(): YearMonthKey {
            val cal = Calendar.getInstance()
            return YearMonthKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                app as TrackerApplication
                return StatsViewModel(app) as T
            }
        }
    }
}
