# Statistics Screen

**Date:** 2026-06-20
**Status:** Approved

## Goal

A Statistics screen with two stacked line charts for a selected month:

1. **Calories** — daily calories logged vs. the calorie goal (a flat reference line).
2. **Weight** — daily weight (carried forward).

Both charts share an identical day-of-month X axis (days `1..daysInMonth`) so the
user can read straight down and compare weight against calorie-goal adherence.

## Decisions (from brainstorming)

- **Goal line:** the app stores only the *current* calorie goal (single mutable
  row, no history). The goal line uses the current goal across all months. No
  goal-history table is added.
- **Aggregation:** one point per **day**, within a single selected month. The
  user pages between months. (Not month-level averaging.)
- **Missing days:** the calorie line **breaks (gaps)** on days with nothing
  logged. The weight line **carries forward** the most recent prior weight, so it
  is continuous — matching how weight already works in the diary.
- **Charting:** hand-rolled Compose `Canvas`. No new charting dependency.
- **Entry & navigation:** a chart icon in the Food Diary top bar (next to the
  existing Settings gear) opens the screen; a month selector with `‹ ›` arrows
  pages between months, defaulting to the current month.

## Navigation

- Add `Route.Stats : Route("stats")` to `navigation/NavGraph.kt` and a
  `composable(Route.Stats.path) { StatsScreen(navController) }` entry.
- In `DiaryScreen`'s `TopAppBar` `actions`, add an `IconButton` with
  `Icons.Default.ShowChart` (contentDescription "Statistics") **before** the
  existing Settings `IconButton`, navigating to `Route.Stats.path`.

## Data Layer

### DiaryEntryDao — per-day calorie sums for a date range

```kotlin
data class DailyCalorieTotal(val date: String, val total: Int)

@Query(
    "SELECT date AS date, SUM(calories) AS total FROM diary_entries " +
    "WHERE date BETWEEN :start AND :end GROUP BY date"
)
fun getDailyCalorieTotals(start: String, end: String): Flow<List<DailyCalorieTotal>>
```

`start`/`end` are inclusive `yyyy-MM-dd` strings (first and last day of the
month). Days with no entries are simply absent from the result (→ gap in the
calorie line). `SUM(calories)` ignores NULL calories per SQL semantics.

### WeightEntryDao — weights within a range + carry-forward seed

Add a range query; reuse the existing carry-forward query
`observeEffectiveWeight(date)` to seed the value at the month start.

```kotlin
@Query("SELECT * FROM weight_entries WHERE date BETWEEN :start AND :end ORDER BY date ASC")
fun getWeightsInRange(start: String, end: String): Flow<List<WeightEntry>>
```

(`observeEffectiveWeight` already exists from the current-weight feature and
returns the most recent row at or before a date.)

### GoalsRepository

Reuse `getGoals(): Flow<Goals?>` for the current calorie goal.

## StatsMath (pure logic — JVM-unit-testable)

A standalone object `ui/stats/StatsMath.kt` holding the testable logic. No
Android/Compose imports so it runs under `tracker/src/test`.

```kotlin
data class YRange(val min: Float, val max: Float)

object StatsMath {

    /** Day-of-month (1-based) for a "yyyy-MM-dd" string. */
    fun dayOfMonth(date: String): Int  // parses the dd field

    /**
     * Bucket per-day calorie totals into day-of-month -> calories for the given
     * month. Input dates outside the month are ignored.
     */
    fun calorieSeries(totals: List<DailyCalorieTotal>): Map<Int, Int>

    /**
     * Build a carried-forward weight series across every day 1..daysInMonth.
     * - seed = most recent weight at or before the month start (or null).
     * - inMonth = weight rows within the month (day-of-month -> lbs).
     * Each day uses the latest known weight at or before it. Days before any
     * known weight (null seed and no earlier in-month row) are absent.
     */
    fun weightSeries(
        daysInMonth: Int,
        seedLbs: Float?,
        inMonth: Map<Int, Float>
    ): Map<Int, Float>

    /**
     * Y range for a chart. Includes every series value and (if non-null) the
     * goal line. Pads by [padFraction] of the span. If all values are equal (or
     * a single value), falls back to value ± [flatPad] so the line isn't drawn
     * on the axis edge. Returns YRange(0f, 1f) if there are no values at all.
     */
    fun yRange(
        values: List<Float>,
        goal: Float?,
        padFraction: Float = 0.1f,
        flatPad: Float = 1f
    ): YRange
}
```

## ViewModel (`StatsViewModel`)

`AndroidViewModel`, constructed via a `Factory` like `DiaryViewModel`.

- State: `selectedMonth: StateFlow<YearMonthKey>` where `YearMonthKey(year, month)`
  (month 1-12). Defaults to the current month (computed once at init using the
  same `SimpleDateFormat`/`Calendar` approach as `DiaryViewModel`).
- Helpers: derive `monthStart` ("yyyy-MM-01"), `monthEnd` ("yyyy-MM-{lastDay}"),
  and `daysInMonth` from the selected month.
- `prevMonth()` / `nextMonth()` step the selected month by one calendar month.
- Combine the sources into:

```kotlin
data class MonthStats(
    val year: Int,
    val month: Int,            // 1-12
    val daysInMonth: Int,
    val calorieGoal: Int?,     // null if no goals row yet
    val dailyCalories: Map<Int, Int>,   // day-of-month -> calories (gaps absent)
    val dailyWeight: Map<Int, Float>    // day-of-month -> lbs (carry-forward filled)
)

val monthStats: StateFlow<MonthStats>
```

Built with `flatMapLatest` on `selectedMonth`. Inside, for the resolved
`start`/`end`/`daysInMonth`, `combine` four flows (Kotlin's `combine` supports up
to 5):
`diaryRepo.getDailyCalorieTotals(start, end)`,
`weightRepo.getWeightsInRange(start, end)`,
`weightRepo.observeEffectiveWeight(start)` (the carry-forward seed — most recent
weight at or before the month start, may be null),
`goalsRepo.getGoals()`.
The combine lambda maps raw rows through `StatsMath.calorieSeries` and
`StatsMath.weightSeries(daysInMonth, seed?.weightLbs, inMonthByDay)` and assembles
`MonthStats`.

A repository method `WeightRepository.getWeightsInRange(start, end)` wraps the new
DAO query; `DiaryRepository` gets `getDailyCalorieTotals(start, end)`.

## UI

### LineChart composable (`ui/stats/LineChart.kt`)

Reusable, drawn with Compose `Canvas`.

```kotlin
data class ChartSeries(
    val points: Map<Int, Float>,   // x (day-of-month) -> y
    val color: Color,
    val breakOnGaps: Boolean       // true = calories (skip missing), false = weight
)

@Composable
fun LineChart(
    series: List<ChartSeries>,
    xDomainMax: Int,               // daysInMonth; x domain is 1..xDomainMax
    yRange: YRange,
    referenceLine: Float? = null,  // goal; drawn dashed if non-null
    referenceColor: Color = ...,
    yLabel: (Float) -> String,     // Y tick formatter
    modifier: Modifier = Modifier
)
```

Drawing:
- Fixed left gutter for Y tick labels and bottom gutter for X labels; identical
  plot insets in both charts so day *k* maps to the same x-pixel in each (shared
  axis alignment).
- Horizontal gridlines at evenly spaced Y ticks with labels via `yLabel`.
- Each series → a `Path`; for `breakOnGaps`, start a new sub-path (`moveTo`)
  after any absent day so the line breaks. Draw a small dot per point. A series
  with a single point draws just the dot.
- `referenceLine` drawn as a dashed horizontal line (the goal).
- X position: `x = leftGutter + (day - 1) / (xDomainMax - 1) * plotWidth`
  (guard `xDomainMax == 1`).

### StatsScreen (`ui/stats/StatsScreen.kt`)

Top to bottom:
- `TopAppBar(title = "Statistics")` with a back navigation icon.
- Month selector row (`‹ June 2026 ›`), modeled on the diary `DateSelector`,
  calling `viewModel.prevMonth()` / `nextMonth()`.
- **Calories** `Card`: title + legend ("Calories" solid, "Goal" dashed), then
  `LineChart` with the calorie series (`breakOnGaps = true`) and
  `referenceLine = calorieGoal`. `yRange` from `StatsMath.yRange(calorieValues,
  goal)`; `yLabel` = integer kcal.
- **Weight** `Card`: title + "lbs" hint, then `LineChart` with the weight series
  (`breakOnGaps = false`), no reference line. `yRange` from
  `StatsMath.yRange(weightValues, null)`; `yLabel` = `formatAmount`.
- Shared X-axis day labels render inside `LineChart`'s bottom gutter (e.g. 1, 5,
  10, 15, 20, 25, and last day), so both charts align under the same ticks.
- **Empty state:** if both `dailyCalories` and `dailyWeight` are empty, show a
  centered "No data for this month" message in place of the charts.

## Error Handling & Edge Cases

- Empty maps → gaps (calories) / absent series (weight); no crashes.
- `calorieGoal == null` (no goals row) → no reference line drawn.
- Single data point → dot only.
- All-equal Y values / single value → `StatsMath.yRange` flat-pad fallback.
- `daysInMonth` correct for 28/29/30/31 via `Calendar.getActualMaximum`.

## Testing

JVM unit tests (`tracker/src/test/.../ui/stats/StatsMathTest.kt`), matching the
existing `FormatAmountTest` style:

- `dayOfMonth` parses the day field correctly.
- `calorieSeries` buckets totals by day-of-month.
- `weightSeries`: carry-forward from a seed; mid-month updates; days before any
  known weight (null seed) absent; full-month fill when seed present.
- `yRange`: includes goal in range; padding applied; all-equal fallback;
  empty → `YRange(0f, 1f)`.

The Canvas drawing and DAO queries are not unit-tested (consistent with the rest
of the tracker UI / the diary feature).

## Out of Scope (YAGNI)

- Goal history (per the decision above).
- Month-level averaging, multi-month/year overview, macro charts.
- Zoom/pan, tap-to-inspect tooltips, swipe-between-months gestures.
- Exporting or sharing charts.
