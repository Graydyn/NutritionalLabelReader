# Current Weight on the Diary Screen

**Date:** 2026-06-20
**Status:** Approved

## Goal

Show the user's current weight on the main logging screen (the Food Diary). The
weight is editable per day and **carries forward** from the most recent prior
entry, so it stays the same until the user updates it.

## Behavior

- Each day the user records a weight, that day stores its own value.
- A day with no recorded weight displays the most recent **prior** weight
  (carry-forward).
- Editing an old day's weight rewrites only that day's value. Later days that
  have their own recorded weight are unaffected. Later days *without* a recorded
  weight reflect whatever is the most recent value at or before their date — this
  is the "stays the same until updated" behavior.
- Unit is **pounds (lbs)**. Decimals allowed (e.g. `182.4`).
- Before any weight has ever been recorded, the row shows an `Add weight` prompt.
- Carried-forward values display plainly (no "estimated" styling) — they are
  treated as the current weight.

## Data Model

New Room entity / table `weight_entries`:

```kotlin
@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey val date: String,   // "yyyy-MM-dd"
    val weightLbs: Float
)
```

One row per day the user actually recorded a weight. `date` is the primary key,
so re-logging a day replaces that day's value (upsert via
`OnConflictStrategy.REPLACE`).

### Migration

Bump `TrackerDatabase` from version **6 to 7**. `MIGRATION_6_7` is purely
additive — it creates the table and touches no existing data:

```sql
CREATE TABLE IF NOT EXISTS `weight_entries` (
    `date` TEXT NOT NULL PRIMARY KEY,
    `weightLbs` REAL NOT NULL
)
```

Add `WeightEntry::class` to the `@Database` entities list and register
`MIGRATION_6_7` in `addMigrations(...)`. Room exports the new schema as
`tracker/schemas/.../7.json` at build time.

## DAO

`WeightEntryDao`:

```kotlin
@Dao
interface WeightEntryDao {
    // Carry-forward: most recent weight at or before the given date.
    @Query("SELECT * FROM weight_entries WHERE date <= :date ORDER BY date DESC LIMIT 1")
    fun observeEffectiveWeight(date: String): Flow<WeightEntry?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntry)
}
```

## Repository

`WeightRepository`:

```kotlin
class WeightRepository(private val dao: WeightEntryDao) {
    fun observeEffectiveWeight(date: String): Flow<WeightEntry?> =
        dao.observeEffectiveWeight(date)

    suspend fun setWeight(date: String, weightLbs: Float) =
        dao.upsert(WeightEntry(date, weightLbs))
}
```

## ViewModel (`DiaryViewModel`)

- Construct `WeightRepository(db.weightEntryDao())`.
- Expose effective weight for the selected date:

```kotlin
val effectiveWeight: StateFlow<Float?> =
    _selectedDate
        .flatMapLatest { date -> weightRepo.observeEffectiveWeight(date) }
        .map { it?.weightLbs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

- Action:

```kotlin
fun setWeight(lbs: Float) {
    val date = _selectedDate.value
    viewModelScope.launch(Dispatchers.IO) { weightRepo.setWeight(date, lbs) }
}
```

`null` from `effectiveWeight` means no weight has ever been recorded.

## UI (`DiaryScreen`)

In the existing "Today" `SummaryCard`, add a tappable weight row (after the
calorie header / progress bars area):

- Shows `Weight   182.4 lbs` when a value exists.
- Shows `Add weight` (subtle, `onSurfaceVariant`) when `effectiveWeight` is null.
- Tapping opens a `WeightDialog`.

`WeightDialog` (new small composable, e.g. in `ui/components/`):

- Decimal number `TextField`, pre-filled with the current effective value when
  one exists (empty otherwise).
- Save / Cancel buttons. Save parses the input to `Float`; ignore/disable Save on
  blank or unparseable input. On save, call `viewModel.setWeight(value)` and
  dismiss.

Wire dialog open/close with a `remember { mutableStateOf(false) }` in
`DiaryScreen` (consistent with existing dialog patterns like `renameTarget`),
passing `effectiveWeight` into `SummaryCard` and an `onEditWeight` callback up to
the screen.

Format: trim trailing zeros so whole numbers show as `182 lbs`, not `182.0 lbs`
(reuse the `formatCount` style already in `DiaryScreen.kt`).

## Testing

- **DAO / carry-forward query** (`WeightEntryDao`): exact-day hit returns that
  day; a date with no row carries from the most recent prior row; editing an old
  day's row changes the effective value for later rowless days but not for later
  days that have their own row; empty table returns null.
- **ViewModel**: `setWeight` writes a row for the current `selectedDate`;
  `effectiveWeight` reflects the carry-forward result and updates when the
  selected date changes.

## Out of Scope (YAGNI)

- No weight history chart / trends screen.
- No unit toggle (kg) or goal-weight tracking.
- No editing weight for a non-selected date except via navigating to that date.
