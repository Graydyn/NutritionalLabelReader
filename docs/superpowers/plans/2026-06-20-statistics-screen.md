# Statistics Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Statistics screen with two vertically stacked, X-axis-aligned line charts for a selected month — daily calories vs. the calorie goal, and daily weight — so the user can compare weight against calorie-goal adherence.

**Architecture:** New Room queries aggregate calories per day and fetch weights per month; a pure-logic `StatsMath` object turns rows into day-of-month series (calories gap on missing days, weight carried forward from a seed). `StatsViewModel` combines these with the current goal into a `MonthStats`. A hand-rolled Compose `Canvas` `LineChart` renders each series; `StatsScreen` stacks two charts sharing one `1..daysInMonth` X domain. Entry is a chart icon in the diary top bar.

**Tech Stack:** Kotlin, Room (kapt), Jetpack Compose Material3 + `Canvas`, Compose Navigation, kotlinx coroutines/Flow. Pure logic tested as JVM unit tests under `tracker/src/test`.

## Global Constraints

- **No new charting dependency** — charts are hand-rolled with Compose `Canvas`.
- **Goal line** uses the current calorie goal (single `Goals` row) for all months; no goal-history table.
- **Aggregation** is one point per **day** within one selected month; user pages between months.
- **Missing days:** calorie line **breaks (gaps)**; weight line **carries forward** the most recent prior weight.
- `StatsMath` must have **no Android/Compose imports** so it runs under `tracker/src/test` (pure JVM). Existing JVM tests use JUnit4 (`org.junit.Test`, `org.junit.Assert.*`).
- No DB schema change — these are read-only queries against existing tables (`diary_entries`, `weight_entries`, `goals`). DB version stays at 7.
- Follow existing patterns: `Route` sealed class in `NavGraph.kt`; ViewModel `Factory` via `CreationExtras` + `TrackerApplication`; date math with `SimpleDateFormat("yyyy-MM-dd", Locale.US)` + `Calendar`.
- Build with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (no JDK on PATH). JVM unit tests: `./gradlew :tracker:testDebugUnitTest`.

---

### Task 1: Data-layer queries and repository methods

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/db/DiaryEntryDao.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/db/WeightEntryDao.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/repository/DiaryRepository.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/repository/WeightRepository.kt`
- Create: `tracker/src/main/java/com/graydyn/tracker/data/db/DailyCalorieTotal.kt`

**Interfaces:**
- Consumes: existing `WeightEntry` model; existing `WeightEntryDao.observeEffectiveWeight(date)`.
- Produces:
  - `data class DailyCalorieTotal(val date: String, val total: Int)`
  - `DiaryEntryDao.getDailyCalorieTotals(start: String, end: String): Flow<List<DailyCalorieTotal>>`
  - `WeightEntryDao.getWeightsInRange(start: String, end: String): Flow<List<WeightEntry>>`
  - `DiaryRepository.getDailyCalorieTotals(start, end): Flow<List<DailyCalorieTotal>>`
  - `WeightRepository.getWeightsInRange(start, end): Flow<List<WeightEntry>>`
  - `WeightRepository.observeEffectiveWeight(date)` (already exists; used as the seed)

There are no JVM-testable units here (DAO queries are instrumented-only in this
project). This task is pure wiring verified by compilation; the query logic is
exercised indirectly by `StatsMath` tests in Task 2.

- [ ] **Step 1: Create the DailyCalorieTotal row class**

Create `tracker/src/main/java/com/graydyn/tracker/data/db/DailyCalorieTotal.kt`:

```kotlin
package com.graydyn.tracker.data.db

/** Per-day calorie sum returned by [DiaryEntryDao.getDailyCalorieTotals]. */
data class DailyCalorieTotal(
    val date: String,   // "yyyy-MM-dd"
    val total: Int
)
```

- [ ] **Step 2: Add the calorie-totals query to DiaryEntryDao**

In `tracker/src/main/java/com/graydyn/tracker/data/db/DiaryEntryDao.kt` add the
import and query (Room maps the `date`/`total` columns onto `DailyCalorieTotal`):

Add import:

```kotlin
import com.graydyn.tracker.data.db.DailyCalorieTotal
```

(Same package, so the import is optional; include only if the file references it
unqualified without resolving. If in same package, skip the import.)

Add inside the `@Dao interface DiaryEntryDao`:

```kotlin
    @Query(
        "SELECT date AS date, SUM(calories) AS total FROM diary_entries " +
            "WHERE date BETWEEN :start AND :end GROUP BY date"
    )
    fun getDailyCalorieTotals(start: String, end: String): Flow<List<DailyCalorieTotal>>
```

- [ ] **Step 3: Add the range query to WeightEntryDao**

In `tracker/src/main/java/com/graydyn/tracker/data/db/WeightEntryDao.kt`, add
inside the interface:

```kotlin
    @Query("SELECT * FROM weight_entries WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    fun getWeightsInRange(start: String, end: String): Flow<List<WeightEntry>>
```

- [ ] **Step 4: Expose both via repositories**

In `tracker/src/main/java/com/graydyn/tracker/data/repository/DiaryRepository.kt`
add the import and method:

```kotlin
import com.graydyn.tracker.data.db.DailyCalorieTotal
```

```kotlin
    fun getDailyCalorieTotals(start: String, end: String): Flow<List<DailyCalorieTotal>> =
        dao.getDailyCalorieTotals(start, end)
```

In `tracker/src/main/java/com/graydyn/tracker/data/repository/WeightRepository.kt`
add the method (imports for `WeightEntry`/`Flow` already present):

```kotlin
    fun getWeightsInRange(start: String, end: String): Flow<List<WeightEntry>> =
        dao.getWeightsInRange(start, end)
```

- [ ] **Step 5: Verify it compiles**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :tracker:assembleDebug`
Expected: BUILD SUCCESSFUL (Room validates the new queries at compile time).

- [ ] **Step 6: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/db/DailyCalorieTotal.kt \
        tracker/src/main/java/com/graydyn/tracker/data/db/DiaryEntryDao.kt \
        tracker/src/main/java/com/graydyn/tracker/data/db/WeightEntryDao.kt \
        tracker/src/main/java/com/graydyn/tracker/data/repository/DiaryRepository.kt \
        tracker/src/main/java/com/graydyn/tracker/data/repository/WeightRepository.kt
git commit -m "feat(tracker): add monthly calorie-total and weight-range queries"
```

---

### Task 2: StatsMath pure logic (TDD)

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/ui/stats/StatsMath.kt`
- Test: `tracker/src/test/java/com/graydyn/tracker/ui/stats/StatsMathTest.kt`

**Interfaces:**
- Consumes: `DailyCalorieTotal` (Task 1).
- Produces:
  - `data class YRange(val min: Float, val max: Float)`
  - `StatsMath.dayOfMonth(date: String): Int`
  - `StatsMath.calorieSeries(totals: List<DailyCalorieTotal>): Map<Int, Int>`
  - `StatsMath.weightSeries(daysInMonth: Int, seedLbs: Float?, inMonth: Map<Int, Float>): Map<Int, Float>`
  - `StatsMath.yRange(values: List<Float>, goal: Float?, padFraction: Float = 0.1f, flatPad: Float = 1f): YRange`

- [ ] **Step 1: Write the failing tests**

Create `tracker/src/test/java/com/graydyn/tracker/ui/stats/StatsMathTest.kt`:

```kotlin
package com.graydyn.tracker.ui.stats

import com.graydyn.tracker.data.db.DailyCalorieTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsMathTest {

    @Test
    fun dayOfMonth_parsesDayField() {
        assertEquals(1, StatsMath.dayOfMonth("2026-06-01"))
        assertEquals(20, StatsMath.dayOfMonth("2026-06-20"))
        assertEquals(31, StatsMath.dayOfMonth("2026-01-31"))
    }

    @Test
    fun calorieSeries_bucketsByDayOfMonth() {
        val totals = listOf(
            DailyCalorieTotal("2026-06-01", 1800),
            DailyCalorieTotal("2026-06-03", 2200)
        )
        val series = StatsMath.calorieSeries(totals)
        assertEquals(mapOf(1 to 1800, 3 to 2200), series)
    }

    @Test
    fun weightSeries_carriesForwardFromSeed() {
        // Seed 180 before the month; updates on day 10 and day 20.
        val series = StatsMath.weightSeries(
            daysInMonth = 30,
            seedLbs = 180f,
            inMonth = mapOf(10 to 178f, 20 to 176f)
        )
        assertEquals(180f, series[1])
        assertEquals(180f, series[9])
        assertEquals(178f, series[10])
        assertEquals(178f, series[19])
        assertEquals(176f, series[20])
        assertEquals(176f, series[30])
        assertEquals(30, series.size)
    }

    @Test
    fun weightSeries_nullSeed_daysBeforeFirstWeightAbsent() {
        val series = StatsMath.weightSeries(
            daysInMonth = 30,
            seedLbs = null,
            inMonth = mapOf(5 to 175f)
        )
        assertFalse(series.containsKey(1))
        assertFalse(series.containsKey(4))
        assertEquals(175f, series[5])
        assertEquals(175f, series[30])
    }

    @Test
    fun weightSeries_emptyAndNullSeed_isEmpty() {
        val series = StatsMath.weightSeries(30, null, emptyMap())
        assertTrue(series.isEmpty())
    }

    @Test
    fun yRange_includesGoalAndPads() {
        // values 1800..2000, goal 2200 -> range must cover 1800 and 2200, padded.
        val r = StatsMath.yRange(listOf(1800f, 2000f), goal = 2200f, padFraction = 0.1f)
        assertTrue("min below data", r.min < 1800f)
        assertTrue("max above goal", r.max > 2200f)
    }

    @Test
    fun yRange_allEqual_usesFlatPad() {
        val r = StatsMath.yRange(listOf(180f, 180f), goal = null, flatPad = 1f)
        assertEquals(179f, r.min, 0.001f)
        assertEquals(181f, r.max, 0.001f)
    }

    @Test
    fun yRange_empty_returnsUnit() {
        val r = StatsMath.yRange(emptyList(), goal = null)
        assertEquals(0f, r.min, 0.001f)
        assertEquals(1f, r.max, 0.001f)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :tracker:testDebugUnitTest --tests "com.graydyn.tracker.ui.stats.StatsMathTest"`
Expected: FAIL — unresolved reference `StatsMath` / `YRange`.

- [ ] **Step 3: Implement StatsMath**

Create `tracker/src/main/java/com/graydyn/tracker/ui/stats/StatsMath.kt`:

```kotlin
package com.graydyn.tracker.ui.stats

import com.graydyn.tracker.data.db.DailyCalorieTotal

data class YRange(val min: Float, val max: Float)

/**
 * Pure (Android-free) chart math so it is unit-testable on the JVM.
 */
object StatsMath {

    /** Day-of-month (1-based) for a "yyyy-MM-dd" string. */
    fun dayOfMonth(date: String): Int =
        date.substringAfterLast('-').toInt()

    /** Bucket per-day calorie totals into day-of-month -> calories. */
    fun calorieSeries(totals: List<DailyCalorieTotal>): Map<Int, Int> =
        totals.associate { dayOfMonth(it.date) to it.total }

    /**
     * Carried-forward weight across every day 1..[daysInMonth].
     * Each day uses the latest known weight at or before it, starting from
     * [seedLbs] (most recent weight at or before the month start, or null).
     * Days before any known weight are absent.
     */
    fun weightSeries(
        daysInMonth: Int,
        seedLbs: Float?,
        inMonth: Map<Int, Float>
    ): Map<Int, Float> {
        val out = LinkedHashMap<Int, Float>()
        var current: Float? = seedLbs
        for (day in 1..daysInMonth) {
            inMonth[day]?.let { current = it }
            current?.let { out[day] = it }
        }
        return out
    }

    /**
     * Y range covering all [values] and (if non-null) [goal], padded by
     * [padFraction] of the span. All-equal/single value falls back to
     * value ± [flatPad]. No values at all -> YRange(0f, 1f).
     */
    fun yRange(
        values: List<Float>,
        goal: Float?,
        padFraction: Float = 0.1f,
        flatPad: Float = 1f
    ): YRange {
        val all = if (goal != null) values + goal else values
        if (all.isEmpty()) return YRange(0f, 1f)
        val lo = all.min()
        val hi = all.max()
        if (lo == hi) return YRange(lo - flatPad, hi + flatPad)
        val pad = (hi - lo) * padFraction
        return YRange(lo - pad, hi + pad)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :tracker:testDebugUnitTest --tests "com.graydyn.tracker.ui.stats.StatsMathTest"`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/stats/StatsMath.kt \
        tracker/src/test/java/com/graydyn/tracker/ui/stats/StatsMathTest.kt
git commit -m "feat(tracker): add StatsMath series and y-range logic with tests"
```

---

### Task 3: StatsViewModel

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/ui/stats/StatsViewModel.kt`

**Interfaces:**
- Consumes: `DiaryRepository.getDailyCalorieTotals`, `WeightRepository.getWeightsInRange`, `WeightRepository.observeEffectiveWeight`, `GoalsRepository.getGoals` (Task 1 + existing); `StatsMath.*` (Task 2); `TrackerDatabase.getInstance`, `TrackerApplication`.
- Produces:
  - `data class MonthStats(year, month, daysInMonth, calorieGoal, dailyCalories, dailyWeight)`
  - `StatsViewModel.monthStats: StateFlow<MonthStats>`
  - `StatsViewModel.selectedLabel: StateFlow<String>` (e.g. "June 2026")
  - `StatsViewModel.prevMonth()` / `nextMonth()`
  - `StatsViewModel.Factory`

No JVM unit test for the ViewModel (it constructs Room via `getInstance`; the
project tests such VMs only as instrumented tests, which are out of scope here).
The testable logic lives in `StatsMath` (Task 2). Verified by compilation +
manual run in Task 5.

- [ ] **Step 1: Implement the ViewModel**

Create `tracker/src/main/java/com/graydyn/tracker/ui/stats/StatsViewModel.kt`:

```kotlin
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
```

- [ ] **Step 2: Verify it compiles**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :tracker:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/stats/StatsViewModel.kt
git commit -m "feat(tracker): add StatsViewModel combining calories, weight, and goal"
```

---

### Task 4: LineChart Canvas composable

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/ui/stats/LineChart.kt`

**Interfaces:**
- Consumes: `YRange` (Task 2).
- Produces:
  - `data class ChartSeries(points: Map<Int, Float>, color: Color, breakOnGaps: Boolean)`
  - `@Composable fun LineChart(series, xDomainMax, yRange, referenceLine, referenceColor, yLabel, modifier)`

This is presentation-only (Compose `Canvas`); no unit test, consistent with the
rest of the tracker UI. Verified by compilation + the manual run in Task 5.

- [ ] **Step 1: Implement the chart**

Create `tracker/src/main/java/com/graydyn/tracker/ui/stats/LineChart.kt`:

```kotlin
package com.graydyn.tracker.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChartSeries(
    val points: Map<Int, Float>,   // x (day-of-month) -> y
    val color: Color,
    val breakOnGaps: Boolean       // true = calories (skip missing), false = weight
)

private const val LEFT_GUTTER = 96f   // px for Y labels
private const val BOTTOM_GUTTER = 40f // px for X labels
private const val TOP_PAD = 16f
private const val RIGHT_PAD = 16f
private const val Y_TICKS = 4

@Composable
fun LineChart(
    series: List<ChartSeries>,
    xDomainMax: Int,
    yRange: YRange,
    referenceLine: Float? = null,
    referenceColor: Color = Color(0xFF9E9E9E),
    yLabel: (Float) -> String,
    gridColor: Color = Color(0x33000000),
    axisTextColor: Color = Color(0xFF666666),
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val plotLeft = LEFT_GUTTER
        val plotTop = TOP_PAD
        val plotRight = size.width - RIGHT_PAD
        val plotBottom = size.height - BOTTOM_GUTTER
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

        val span = (yRange.max - yRange.min).takeIf { it != 0f } ?: 1f

        fun xFor(day: Int): Float =
            if (xDomainMax <= 1) plotLeft
            else plotLeft + (day - 1).toFloat() / (xDomainMax - 1).toFloat() * plotWidth

        fun yFor(value: Float): Float =
            plotBottom - ((value - yRange.min) / span) * plotHeight

        // Gridlines + Y tick labels
        for (i in 0..Y_TICKS) {
            val v = yRange.min + span * i / Y_TICKS
            val y = yFor(v)
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(plotLeft, y),
                end = androidx.compose.ui.geometry.Offset(plotRight, y), strokeWidth = 1f)
            val label = measurer.measure(yLabel(v), TextStyle(color = axisTextColor, fontSize = 10.sp))
            drawText(label, topLeft = androidx.compose.ui.geometry.Offset(0f, y - label.size.height / 2f))
        }

        // X tick labels: 1, 5, 10, ... and last day
        val xTicks = buildList {
            add(1)
            var d = 5
            while (d < xDomainMax) { add(d); d += 5 }
            add(xDomainMax)
        }.distinct()
        for (day in xTicks) {
            val label = measurer.measure(day.toString(), TextStyle(color = axisTextColor, fontSize = 10.sp))
            drawText(label, topLeft = androidx.compose.ui.geometry.Offset(
                xFor(day) - label.size.width / 2f, plotBottom + 6f))
        }

        // Reference (goal) line, dashed
        referenceLine?.let { ref ->
            if (ref in yRange.min..yRange.max) {
                val y = yFor(ref)
                drawLine(
                    referenceColor,
                    start = androidx.compose.ui.geometry.Offset(plotLeft, y),
                    end = androidx.compose.ui.geometry.Offset(plotRight, y),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                )
            }
        }

        // Series
        series.forEach { s -> drawSeries(s, xDomainMax, ::xFor, ::yFor) }
    }
}

private fun DrawScope.drawSeries(
    s: ChartSeries,
    xDomainMax: Int,
    xFor: (Int) -> Float,
    yFor: (Float) -> Float
) {
    val path = Path()
    var penDown = false
    for (day in 1..xDomainMax) {
        val v = s.points[day]
        if (v == null) {
            if (s.breakOnGaps) penDown = false
            continue
        }
        val px = xFor(day)
        val py = yFor(v)
        if (!penDown) { path.moveTo(px, py); penDown = true } else { path.lineTo(px, py) }
        drawCircle(s.color, radius = 3f, center = androidx.compose.ui.geometry.Offset(px, py))
    }
    drawPath(path, color = s.color, style = Stroke(width = 3f))
}
```

- [ ] **Step 2: Verify it compiles**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :tracker:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/stats/LineChart.kt
git commit -m "feat(tracker): add hand-rolled Canvas LineChart"
```

---

### Task 5: StatsScreen + navigation wiring

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/ui/stats/StatsScreen.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/navigation/NavGraph.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt`

**Interfaces:**
- Consumes: `StatsViewModel` + `MonthStats` (Task 3), `LineChart` + `ChartSeries` (Task 4), `StatsMath.yRange` (Task 2), existing `formatAmount` (`ui/components/ServingConversion.kt`), theme colors `MacroCalories`/`MacroProtein` (`ui/theme`).
- Produces: `@Composable fun StatsScreen(navController: NavController, ...)`; `Route.Stats`; a stats icon in the diary top bar.

- [ ] **Step 1: Add the Stats route**

In `tracker/src/main/java/com/graydyn/tracker/navigation/NavGraph.kt`:

Add to the `Route` sealed class:

```kotlin
    object Stats : Route("stats")
```

Add the import:

```kotlin
import com.graydyn.tracker.ui.stats.StatsScreen
```

Add inside `NavHost { ... }`:

```kotlin
        composable(Route.Stats.path) {
            StatsScreen(navController = navController)
        }
```

- [ ] **Step 2: Add the entry icon to the diary top bar**

In `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt`, add the
import:

```kotlin
import androidx.compose.material.icons.filled.ShowChart
```

In the `TopAppBar`'s `actions = { ... }` block, add a stats `IconButton`
**before** the existing Settings `IconButton`:

```kotlin
                    IconButton(onClick = { navController.navigate(Route.Stats.path) }) {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = "Statistics",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
```

- [ ] **Step 3: Implement StatsScreen**

Create `tracker/src/main/java/com/graydyn/tracker/ui/stats/StatsScreen.kt`:

```kotlin
package com.graydyn.tracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.graydyn.tracker.ui.components.formatAmount
import com.graydyn.tracker.ui.theme.MacroCalories
import com.graydyn.tracker.ui.theme.MacroProtein

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
) {
    val stats by viewModel.monthStats.collectAsState()
    val label by viewModel.selectedLabel.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Statistics", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MonthSelector(
                label = label,
                onPrevious = { viewModel.prevMonth() },
                onNext = { viewModel.nextMonth() }
            )

            val hasData = stats.dailyCalories.isNotEmpty() || stats.dailyWeight.isNotEmpty()
            if (!hasData) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No data for this month",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            // Calories chart
            ChartCard(title = "Calories") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendSwatch(MacroCalories); Text("  Calories", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(16.dp))
                    LegendSwatch(Color(0xFF9E9E9E)); Text("  Goal", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                val calValues = stats.dailyCalories.values.map { it.toFloat() }
                LineChart(
                    series = listOf(
                        ChartSeries(
                            points = stats.dailyCalories.mapValues { it.value.toFloat() },
                            color = MacroCalories,
                            breakOnGaps = true
                        )
                    ),
                    xDomainMax = stats.daysInMonth,
                    yRange = StatsMath.yRange(calValues, stats.calorieGoal?.toFloat()),
                    referenceLine = stats.calorieGoal?.toFloat(),
                    yLabel = { "${it.toInt()}" }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Weight chart
            ChartCard(title = "Weight (lbs)") {
                val wValues = stats.dailyWeight.values.toList()
                LineChart(
                    series = listOf(
                        ChartSeries(
                            points = stats.dailyWeight,
                            color = MacroProtein,
                            breakOnGaps = false
                        )
                    ),
                    xDomainMax = stats.daysInMonth,
                    yRange = StatsMath.yRange(wValues, null),
                    referenceLine = null,
                    yLabel = { formatAmount(it) }
                )
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color) {
    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MonthSelector(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month",
                tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(label, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground)
        IconButton(
            onClick = onNext,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month",
                tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}
```

- [ ] **Step 4: Build and run to verify end to end**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :tracker:assembleDebug`
Expected: BUILD SUCCESSFUL.

Then launch the app (emulator/device) and verify:
1. The Food Diary top bar shows a chart icon; tapping it opens Statistics.
2. The current month is shown; `‹ ›` arrows change the month and label.
3. The Calories chart shows logged days as a line with gaps on unlogged days, plus a dashed goal line.
4. The Weight chart shows a continuous carried-forward weight line.
5. Reading vertically, day *k* lines up between the two charts.
6. A month with no data shows "No data for this month".

> If no emulator is available, document that the manual run could not be performed locally; do not claim the UI verified without evidence.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/stats/StatsScreen.kt \
        tracker/src/main/java/com/graydyn/tracker/navigation/NavGraph.kt \
        tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt
git commit -m "feat(tracker): add Statistics screen with aligned calorie and weight charts"
```

---

## Self-Review Notes

- **Spec coverage:** navigation + diary icon (Task 5); current-goal reference line (Tasks 3/5); per-day aggregation paged by month (Tasks 1/3); calorie gaps + weight carry-forward (Task 2 `StatsMath`, Tasks 4/5 rendering); hand-rolled Canvas, no dependency (Task 4); month selector with arrows (Task 5); shared `1..daysInMonth` X domain via identical plot insets (Task 4); empty-month state (Task 5); StatsMath unit tests (Task 2); edge cases — null goal, single point, all-equal Y (`yRange` flat-pad), 28-31 day months (`getActualMaximum`). All spec sections map to a task.
- **Out of scope** (goal history, monthly averaging, zoom/tooltips, swipe, export) — not implemented, per spec YAGNI.
- **Type consistency:** `DailyCalorieTotal(date, total)`, `getDailyCalorieTotals(start, end)`, `getWeightsInRange(start, end)`, `StatsMath.{dayOfMonth, calorieSeries, weightSeries, yRange}`, `YRange(min, max)`, `MonthStats(...)`, `ChartSeries(points, color, breakOnGaps)`, `LineChart(series, xDomainMax, yRange, referenceLine, referenceColor, yLabel, ...)`, `Route.Stats` — consistent across all tasks. `weightSeries` is called with `seed?.weightLbs` matching its `seedLbs: Float?` param.
- **No placeholders:** every code step shows complete code; the one unit-test task (Task 2) shows full test bodies.
