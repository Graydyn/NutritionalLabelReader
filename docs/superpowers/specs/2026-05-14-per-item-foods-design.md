# Per-Item Foods — Design

Date: 2026-05-14
Module: `:tracker`
Touches: `data/model/Food.kt`, `data/model/DiaryEntry.kt`, `data/db/TrackerDatabase.kt`, `data/db/Converters.kt`, `data/seed/CsvSeeder.kt`, `ui/search/SearchViewModel.kt`, `ui/search/SearchScreen.kt`, `ui/diary/DiaryScreen.kt`

## Goal

Let a food declare whether it is measured by weight or counted as items. A per-gram food (e.g., chicken breast) continues to be logged as `100g`. A per-item food (e.g., apple) is logged as a count: `1`, `2`, `0.5`. The choice is a permanent property of the food, set when the food is created.

## Motivation

Today every food in the tracker is per-100g. The `2026-05-13-create-new-food-design.md` spec called per-serving input out as a non-goal. Real diet logging needs both: weight for cuts of meat and bulk ingredients, count for discrete units (eggs, apples, slices). This spec adds the per-item path without disturbing the per-gram path.

## Decisions (from brainstorming)

- Unit type is a property of the food, not a per-log choice. A food is either GRAM or ITEM, permanently.
- Per-item foods store macros for one item. Logged quantity is a count; macros at log time are `macroPerItem * count`.
- All existing foods (the seeded CSV, ~363 rows) stay GRAM.
- Create-food dialog selects unit via radio buttons.
- Logging a per-item food uses the same single text field as today, with the label flipped from "Grams" to "Items" and a decimal keyboard.
- Diary row shows per-item entries as `"Apple  ·  ×2"` (compact, no pluralization).
- DiaryEntry stores both `unitType` and a new `count` column so the row is self-describing and doesn't need to look up its food.
- Schema migration is a real `Migration(1, 2)`, not destructive fallback, so any locally logged entries survive.

## Data model

### `FoodUnitType` enum

New file `data/model/FoodUnitType.kt`:

```kotlin
package com.graydyn.tracker.data.model

enum class FoodUnitType { GRAM, ITEM }
```

### `Food` entity

```kotlin
@Entity(tableName = "foods")
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unitType: FoodUnitType,
    val caloriesPer100g: Float?,   // populated when unitType = GRAM
    val proteinPer100g: Float?,
    val fatPer100g: Float?,
    val carbsPer100g: Float?,
    val caloriesPerItem: Float?,   // populated when unitType = ITEM
    val proteinPerItem: Float?,
    val fatPerItem: Float?,
    val carbsPerItem: Float?
)
```

**Invariant** (enforced by the create-food flow, not by the DB): when `unitType = GRAM`, the four `*PerItem` columns are null; when `unitType = ITEM`, the four `*Per100g` columns are null. No CHECK constraint — Room's first-class story for that is awkward and the invariant only needs to be maintained at one writer (the Create Food path) plus the CSV seeder.

### `DiaryEntry` entity

```kotlin
@Entity(
    tableName = "diary_entries",
    indices = [Index(value = ["date"])]
)
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val mealType: MealType,
    val label: String,
    val sourceType: SourceType,
    val foodId: Long?,
    val unitType: FoodUnitType,
    val grams: Float?,    // populated when unitType = GRAM
    val count: Float?,    // populated when unitType = ITEM
    val calories: Int?,
    val protein: Float?,
    val fat: Float?,
    val carbs: Float?
)
```

Per-gram entries set `grams`, leave `count` null. Per-item entries set `count`, leave `grams` null. `unitType` is the discriminator the UI reads.

### Converters

Add `FoodUnitType ↔ String` to `data/db/Converters.kt`, matching the existing `MealType` / `SourceType` pair pattern (`xxxToString` / `stringToXxx`):

```kotlin
@TypeConverter
fun foodUnitTypeToString(value: FoodUnitType): String = value.name

@TypeConverter
fun stringToFoodUnitType(value: String): FoodUnitType = FoodUnitType.valueOf(value)
```

### Scanned entries

The OCR scan flow in `DiaryScreen` produces ad-hoc entries from a nutrition label. Nutrition labels are weight-based, so scanned entries write `unitType = FoodUnitType.GRAM` and `count = null`. Out of scope to change.

## Migration

DB version 1 → 2. Real `Migration(1, 2)`, no destructive fallback.

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE foods ADD COLUMN unitType TEXT NOT NULL DEFAULT 'GRAM'")
        db.execSQL("ALTER TABLE foods ADD COLUMN caloriesPerItem REAL")
        db.execSQL("ALTER TABLE foods ADD COLUMN proteinPerItem REAL")
        db.execSQL("ALTER TABLE foods ADD COLUMN fatPerItem REAL")
        db.execSQL("ALTER TABLE foods ADD COLUMN carbsPerItem REAL")

        db.execSQL("ALTER TABLE diary_entries ADD COLUMN unitType TEXT NOT NULL DEFAULT 'GRAM'")
        db.execSQL("ALTER TABLE diary_entries ADD COLUMN count REAL")
    }
}
```

Wired in `TrackerDatabase.getInstance` via `.addMigrations(MIGRATION_1_2)` on the `Room.databaseBuilder` chain. `@Database(... version = 2 ...)`.

Notes:
- The `DEFAULT 'GRAM'` on the new NOT NULL column is what allows `ALTER TABLE` to succeed on existing rows. Future inserts go through Room and supply `unitType` explicitly; the column default is a one-time migration scaffold.
- `TEXT` is what Room emits for enum columns via the `Converters`.
- No data backfill needed: existing per-gram rows already have correct per-100g values, and the per-item columns being null is exactly what the GRAM invariant requires.

## CsvSeeder

`CsvSeeder.parseLine` constructs `Food(...)`. The constructor call now needs `unitType = FoodUnitType.GRAM` and `null` for the four `*PerItem` parameters. CSV format itself is unchanged; the seed continues to produce per-gram foods only.

## Create Food dialog (`SearchScreen.kt`)

The existing `CreateFoodDialog` composable gains a unit-type radio group at the top.

### Layout, top to bottom

1. **Unit-type radio group** — a `Row` of two `RadioButton`s with labels:
   - `Measured by weight` (default selected on open)
   - `Counted as items`
2. **Name** — unchanged (required, default keyboard, pre-filled from query).
3. **Calories** — required, decimal keyboard. Label flips:
   - GRAM → `"Calories per 100 g"`
   - ITEM → `"Calories per item"`
4. **Protein** — optional, decimal keyboard. Label flips:
   - GRAM → `"Protein per 100 g (optional)"`
   - ITEM → `"Protein per item (optional)"`
5. **Fat** — same pattern.
6. **Carbs** — same pattern.

Footer buttons (`Cancel`, `Save`) unchanged.

### State

Local to the dialog composable, alongside the existing `name`/`calories`/`protein`/`fat`/`carbs` strings:

```kotlin
var unitType by remember { mutableStateOf(FoodUnitType.GRAM) }
```

When the user toggles `unitType`, the four macro string fields reset to `""`. This prevents a "300 per 100 g" value from being silently re-saved as "300 per item". The Name field is preserved across the toggle.

### Validation

Identical to today's rules, regardless of unit:

- Save enabled iff: name (after trim) is non-blank AND calories parses as a non-negative `Float`.
- Inline captions:
  - Name blank → `"Required"`
  - Calories blank → `"Required"`
  - Calories non-numeric → `"Must be a number"`
  - Calories negative → `"Must be 0 or greater"`
  - Optional macros never show a caption (non-numeric input is silently coerced to null, matching `CsvSeeder.parseNullableFloat`).

### Save callback signature

```kotlin
onSave: (
    name: String,
    unitType: FoodUnitType,
    calories: Float,
    protein: Float?,
    fat: Float?,
    carbs: Float?
) -> Unit
```

## SearchViewModel

### Renames

`_grams: MutableStateFlow<String>` → `_quantity: MutableStateFlow<String>`. Public `grams: StateFlow<String>` → `quantity: StateFlow<String>`. `onGramsChange` → `onQuantityChange`. The string holds either grams or count depending on the selected food's `unitType`.

### `createFood` — branch on unit type

```kotlin
fun createFood(
    name: String,
    unitType: FoodUnitType,
    calories: Float,
    protein: Float?,
    fat: Float?,
    carbs: Float?
) {
    _showCreateDialog.value = false
    viewModelScope.launch(Dispatchers.IO) {
        val food = when (unitType) {
            FoodUnitType.GRAM -> Food(
                name = name.trim(),
                unitType = FoodUnitType.GRAM,
                caloriesPer100g = calories,
                proteinPer100g = protein,
                fatPer100g = fat,
                carbsPer100g = carbs,
                caloriesPerItem = null, proteinPerItem = null,
                fatPerItem = null, carbsPerItem = null
            )
            FoodUnitType.ITEM -> Food(
                name = name.trim(),
                unitType = FoodUnitType.ITEM,
                caloriesPer100g = null, proteinPer100g = null,
                fatPer100g = null, carbsPer100g = null,
                caloriesPerItem = calories,
                proteinPerItem = protein,
                fatPerItem = fat,
                carbsPerItem = carbs
            )
        }
        val id = foodRepo.add(food)
        val saved = food.copy(id = id)
        withContext(Dispatchers.Main) {
            _selectedFood.value = saved
            _quantity.value = ""
            _query.value = saved.name
        }
    }
}
```

### `logEntry` — branch on the selected food's unit type

```kotlin
fun logEntry(date: String, mealType: MealType): Boolean {
    val food = _selectedFood.value ?: return false
    val qty = _quantity.value.toFloatOrNull()?.takeIf { it > 0f } ?: return false

    val entry = when (food.unitType) {
        FoodUnitType.GRAM -> DiaryEntry(
            date = date, mealType = mealType, label = food.name,
            sourceType = SourceType.DATABASE, foodId = food.id,
            unitType = FoodUnitType.GRAM,
            grams = qty, count = null,
            calories = food.caloriesPer100g?.let { (it * qty / 100f).toInt() },
            protein  = food.proteinPer100g?.let  { it * qty / 100f },
            fat      = food.fatPer100g?.let      { it * qty / 100f },
            carbs    = food.carbsPer100g?.let    { it * qty / 100f }
        )
        FoodUnitType.ITEM -> DiaryEntry(
            date = date, mealType = mealType, label = food.name,
            sourceType = SourceType.DATABASE, foodId = food.id,
            unitType = FoodUnitType.ITEM,
            grams = null, count = qty,
            calories = food.caloriesPerItem?.let { (it * qty).toInt() },
            protein  = food.proteinPerItem?.let  { it * qty },
            fat      = food.fatPerItem?.let      { it * qty },
            carbs    = food.carbsPerItem?.let    { it * qty }
        )
    }
    viewModelScope.launch(Dispatchers.IO) { diaryRepo.insert(entry) }
    return true
}
```

## SearchScreen — `FoodResultCard`

Two cosmetic flips driven by `food.unitType`:

- **"per 100g" caption** under the macro chips becomes `"per 100g"` for GRAM, `"per item"` for ITEM.
- **Quantity input label** in the `AnimatedVisibility` block becomes `"Grams"` for GRAM, `"Items"` for ITEM. Decimal keyboard either way (so 0.5 apples is valid).

The "Add" button and its enable logic are unchanged — `logEntry` already returns false for invalid input.

The `MacroChip` row (calories/protein/fat/carbs values) renders whichever side of the food's macro pair is non-null. Convenient helpers on `Food`:

```kotlin
val Food.calories: Float? get() = if (unitType == FoodUnitType.ITEM) caloriesPerItem else caloriesPer100g
val Food.protein:  Float? get() = if (unitType == FoodUnitType.ITEM) proteinPerItem  else proteinPer100g
val Food.fat:      Float? get() = if (unitType == FoodUnitType.ITEM) fatPerItem      else fatPer100g
val Food.carbs:    Float? get() = if (unitType == FoodUnitType.ITEM) carbsPerItem    else carbsPer100g
```

These are the fields the chips render. Defined in `data/model/Food.kt` next to the `Food` class.

## DiaryScreen — `DiaryEntryRow`

The suffix after the food label flips on `entry.unitType`:

```kotlin
Text(
    text = buildString {
        append(entry.label)
        if (entry.sourceType == SourceType.DATABASE) {
            when (entry.unitType) {
                FoodUnitType.GRAM -> entry.grams?.let { append("  ·  ${it.toInt()}g") }
                FoodUnitType.ITEM -> entry.count?.let { append("  ·  ×${formatCount(it)}") }
            }
        }
    },
    ...
)
```

`formatCount` strips a trailing `.0` so `1.0 → "1"`, `0.5 → "0.5"`:

```kotlin
private fun formatCount(c: Float): String =
    if (c == c.toInt().toFloat()) c.toInt().toString() else "%g".format(c)
```

Scanned entries (`SourceType.SCANNED`) keep today's behavior: no suffix.

The macros line below the label (`"$cal kcal · P …"`) is unchanged — it renders the precomputed totals stored on the entry.

## Daily totals

`DiaryViewModel.dailyTotals` sums the precomputed macros on each `DiaryEntry`. Those totals are computed at log time (in `logEntry`) and are unit-agnostic by the time they reach the totals math. No changes needed.

## Edge cases handled

- Toggling unit type in the create dialog clears macro fields → no accidental cross-unit save.
- Per-item foods with `count = 0.5`: log path multiplies cleanly; diary row shows `×0.5`.
- A food's `unitType` is permanent. Editing a food (which doesn't exist as a feature yet) is out of scope.
- Existing per-gram entries logged before this migration continue to render correctly: they have `unitType = GRAM` (set by the migration default) and a non-null `grams`.
- The OCR scan flow continues to write GRAM entries; the diary row renders them with the existing `${grams}g` suffix.
- Optional per-item macros left blank → saved as null, contribute null to the entry's macros, which sum as zero in `dailyTotals` (matching today's behavior for null per-100g macros).

## Non-goals

- Editing a food's unit type after creation.
- Mixed-unit foods (a single food usable as either weight or count).
- Deleting foods.
- A second seeded CSV of per-item foods.
- Changing how OCR-scanned entries are stored.
- Automated tests. The `:tracker` module has no test harness yet; we continue with manual verification.
