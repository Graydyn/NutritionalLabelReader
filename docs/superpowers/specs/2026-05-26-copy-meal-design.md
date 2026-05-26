# Copy Meal To Date + Slot

Date: 2026-05-26
Module: `:tracker`

## Goal

Let a user duplicate everything logged in one meal slot on one date into another (date, meal slot) pair in a single action. Entry point is a new "Copy to..." item in the existing `MoreVert` overflow menu on a populated `MealCard`.

## Non-goals

- Moving entries (copy only; source is never modified).
- Multi-target copy (one date + one slot per invocation).
- Selecting a subset of entries to copy (always all entries in the source slot).
- Per-entry edits at copy time (tweak the new diary entries afterward).
- Replacing the target slot's existing entries (always append).
- Saving the copy as a reusable saved meal as a side effect.

## User-visible behavior

### Entry point

The existing `MoreVert` overflow icon on populated meal cards (rendered when `entries.isNotEmpty() && onSaveAsMeal != null`) gains a second `DropdownMenuItem` below "Save as meal":

- **Save as meal** (existing)
- **Copy to...** (new)

The menu item is hidden along with the overflow icon when the slot is empty.

### Dialog

Tapping "Copy to..." opens an `AlertDialog` titled **"Copy to..."**.

Content, top to bottom:

1. **From line** (read-only): `"<MealLabel> · <sourceDate> · <N> item<s>, <kcal> kcal"`, e.g. `"Breakfast · 2026-05-26 · 3 items, 540 kcal"`. Singular "item" when N is 1.
2. **Date stepper row**: an `IconButton` with `KeyboardArrowLeft` on the left, the target date centered as a `Text` (formatted `yyyy-MM-dd` to match the diary's existing date label), an `IconButton` with `KeyboardArrowRight` on the right. Each tap shifts by one day. No bounds on past or future.
3. **Meal chip row**: a `Row` of four `FilterChip`s in `MealType.entries` order — Breakfast, Lunch, Dinner, Snack. The selected chip uses the standard `FilterChip` selected state. Tapping a chip sets the target meal type.
4. **Buttons**: `TextButton` **Cancel** (dismiss only) and `TextButton` **Copy** (confirm). Copy is always enabled.

**Defaults on open:**
- Target date: source date + 1 day.
- Target meal type: same as source meal type.

The dialog owns its form state (`targetDate: String`, `targetMealType: MealType`) via `remember { mutableStateOf(...) }`. Process death closes the dialog; this matches how the existing `SaveMealDialog` behaves.

### Copy action

On Copy tap:

1. Dialog closes (ViewModel clears `copyRequest`).
2. ViewModel reads `entriesByMeal.value[sourceMealType].orEmpty()`. If empty, no-op (should not happen because the menu item is gated on a non-empty slot).
3. For each source entry, the ViewModel constructs a new `DiaryEntry` with `id = 0`, `date = targetDate`, `mealType = targetMealType`, and every other field (`label`, `sourceType`, `foodId`, `unitType`, `grams`, `count`, `servings`, `calories`, `protein`, `fat`, `carbs`) copied verbatim from the source row.
4. The copies are inserted in a single `db.withTransaction { diaryRepo.insertAll(copies) }`.
5. Snackbar: `"Copied <N> item<s> to <TargetMealLabel> on <targetDate>"`, e.g. `"Copied 3 items to Lunch on 2026-05-27"`. Singular "item" when N is 1.
6. The diary stays on the source date. If the source date happens to equal the target date, the existing `entriesByMeal` Flow refreshes the visible card automatically.

Self-copy is allowed (same date + same meal slot duplicates the entries in place). No special handling.

Append-only: existing entries in the target slot are untouched.

## Data model

No schema changes. No migration. `DiaryEntry` and its DAO are unchanged in shape; only one new DAO method is added.

## Architecture

```
ui/diary/
  DiaryScreen.kt          (modified: "Copy to..." menu item, CopyMealDialog hookup)
  DiaryViewModel.kt       (modified: copyRequest state, openCopyDialog/dismissCopyDialog, copyMeal())
  CopyMealDialog.kt       (new: stateless composable, owns target date and meal type form state)

data/db/
  DiaryEntryDao.kt        (modified: add insertAll(entries: List<DiaryEntry>))

data/repository/
  DiaryRepository.kt      (modified: add insertAll(entries: List<DiaryEntry>) passthrough)
```

### DAO addition

```kotlin
@Insert
suspend fun insertAll(entries: List<DiaryEntry>)
```

### Repository addition

```kotlin
suspend fun insertAll(entries: List<DiaryEntry>) = dao.insertAll(entries)
```

### ViewModel additions

- `private val _copyRequest = MutableStateFlow<MealType?>(null)` — the source slot when the dialog is open, `null` otherwise.
- `val copyRequest: StateFlow<MealType?> = _copyRequest.asStateFlow()`
- `fun openCopyDialog(mealType: MealType) { _copyRequest.value = mealType }`
- `fun dismissCopyDialog() { _copyRequest.value = null }`
- `fun copyMeal(sourceMealType: MealType, targetDate: String, targetMealType: MealType)`:
  - Reads `entriesByMeal.value[sourceMealType].orEmpty()`.
  - Returns early after clearing `_copyRequest` if the list is empty.
  - Builds copies with `id = 0`, new `date`/`mealType`, all other fields copied.
  - Launches `viewModelScope.launch(Dispatchers.IO) { db.withTransaction { diaryRepo.insertAll(copies) } }`.
  - On completion: sets `_snackbarMessage.value = "Copied <N> item<s> to <TargetMealLabel> on <targetDate>"`.

The `MealLabel` strings ("Breakfast", "Lunch", "Dinner", "Snack") are produced the same way the existing snackbar in `applySavedMeal` does it: `mealType.name.lowercase().replaceFirstChar { it.uppercase() }`. The dialog renders labels via the existing private `mealStyle(mealType).label` in `DiaryScreen.kt`.

### `MealCard` change

`MealCard` already accepts `onSaveAsMeal: (() -> Unit)?`. Add a parallel parameter `onCopyTo: (() -> Unit)? = null` and add a second `DropdownMenuItem` inside the existing `DropdownMenu` block. Both items dismiss the menu before invoking their callback, mirroring the current pattern.

### `DiaryScreen` wiring

In the `MealType.entries.forEach { mealType -> ... MealCard(...) }` block, add:

```kotlin
onCopyTo = { viewModel.openCopyDialog(mealType) }
```

Below the existing `saveMealRequest?.let { ... }` block, add:

```kotlin
copyRequest?.let { sourceMealType ->
    val mealEntries = entriesByMeal[sourceMealType].orEmpty()
    val mealCalories = mealEntries.sumOf { it.calories ?: 0 }
    val sourceLabel = mealStyle(sourceMealType).label
    CopyMealDialog(
        sourceLabel = sourceLabel,
        sourceDate = selectedDate,
        sourceItemCount = mealEntries.size,
        sourceCalories = mealCalories,
        initialTargetDate = nextDay(selectedDate),
        initialTargetMealType = sourceMealType,
        onDismiss = { viewModel.dismissCopyDialog() },
        onCopy = { targetDate, targetMealType ->
            viewModel.copyMeal(sourceMealType, targetDate, targetMealType)
        }
    )
}
```

`nextDay(dateString: String): String` is a small file-private helper in `DiaryScreen.kt` that adds one day using the same `SimpleDateFormat("yyyy-MM-dd", Locale.US)` already used by `DiaryViewModel.navigateDate`.

### `CopyMealDialog` shape

```kotlin
@Composable
fun CopyMealDialog(
    sourceLabel: String,
    sourceDate: String,
    sourceItemCount: Int,
    sourceCalories: Int,
    initialTargetDate: String,
    initialTargetMealType: MealType,
    onDismiss: () -> Unit,
    onCopy: (targetDate: String, targetMealType: MealType) -> Unit,
)
```

Stateless from the caller's perspective; internally holds `targetDate` and `targetMealType` via `rememberSaveable` so a configuration change does not reset the form. The `initial*` parameters are read only on first composition. The date stepper uses the same `SimpleDateFormat("yyyy-MM-dd", Locale.US)` + `Calendar.add(DAY_OF_YEAR, ±1)` pattern as `DiaryViewModel.navigateDate`; the math is small enough to inline in the dialog file rather than extract a shared helper.

## Error handling and edge cases

- **Empty source slot**: cannot happen because the overflow menu and menu item are hidden when `entries.isEmpty()`. ViewModel still no-ops defensively.
- **Self-copy (same date + same meal slot)**: allowed by design; entries are duplicated in place. The Copy button is always enabled.
- **Target date far in the past or future**: no bounds. Inserts succeed; the user can navigate to the target date to see the result.
- **Concurrent edits to the source slot between dialog open and Copy tap**: ViewModel reads `entriesByMeal.value` at copy time, so the copy reflects the live state, not a stale snapshot.
- **Large source slot**: `insertAll` runs inside a single transaction so the operation is atomic and fast.
- **Snackbar pluralization**: `"item"` vs `"items"` based on N, matching the existing meal-card subtitle pattern.

## Testing

Following the pattern of existing tests in this module (`tracker/src/androidTest/`):

- **DAO test** (`DiaryEntryDaoTest`, new or extended): insert a batch via `insertAll`, then read via `getEntriesForDate` for the target date and assert the right count, the right `mealType`, and that every field on each new row equals the source row except `id`, `date`, and `mealType`.
- **ViewModel test** (`DiaryViewModelCopyMealTest`, new, instrumented like the existing `DiaryViewModelSaveMealTest`): seed a source date+slot with multiple entries, call `copyMeal(...)`, and assert that the target date+slot now contains matching entries, the source is unchanged, and the snackbar message is set with the right count and label.
- **Manual smoke test**: open menu on Breakfast 2026-05-26 with 2 items, Copy to..., default target (next day, Breakfast), confirm; navigate to 2026-05-27 and verify the entries; repeat with a different target slot; repeat with self-copy (same date, same slot) and confirm both copies appear.

No Compose UI tests are added (consistent with the rest of `:tracker`).
