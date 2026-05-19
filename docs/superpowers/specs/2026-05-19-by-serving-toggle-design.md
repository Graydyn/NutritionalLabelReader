# By-Serving Toggle for Scanned and Manually-Entered Foods

## Problem

The nutritional label scanner currently treats scanned values as **per item**, but nutrition labels are written **per serving**, and a serving may be a fraction of an item, a multiple of items, or a weight that has no direct item count. Users have no way to express "1 serving = 2 cookies = 28 g" in either `ScannedFoodDialog` or `CreateFoodDialog`, so the data they save is wrong from the moment they hit Save.

The fix is to introduce serving as a first-class storage mode rather than forcing the user to mentally convert at entry time.

## Goals

1. Add a third unit type, `SERVING`, alongside `GRAM` and `ITEM`, available in both `ScannedFoodDialog` and `CreateFoodDialog`.
2. Let the user optionally declare "1 serving = X g" and/or "1 serving = Y items" so the app can convert per-serving values to per-100g or per-item on demand.
3. When the user toggles away from "by serving" with the relevant serving-definition field filled, prefill the per-weight or per-item macros with the converted values.
4. When the user toggles away from "by serving" with the required serving-definition field blank, show a validation dialog explaining what is needed and abort the toggle.
5. Persist serving foods end-to-end: search results, diary entries, and saved meals all understand a SERVING-mode food and can log it as a number of servings.

## Non-goals

- Existing CSV-seeded foods stay GRAM; no retroactive conversion.
- Existing GRAM/ITEM foods in the user's database are unaffected; the migration only adds nullable columns.
- A food's `unitType` is fixed at create time. There is no "log this GRAM food as servings" feature.
- `Goals` and `MacroProgressBar` are unchanged; they consume the denormalized `calories`/`protein`/`fat`/`carbs` snapshot already stored on each `DiaryEntry`.

## Data model

### `FoodUnitType`

```kotlin
enum class FoodUnitType { GRAM, ITEM, SERVING }
```

### `Food`

Six new nullable columns added by Room migration 4 → 5 (additive `ALTER TABLE`):

| Column | Type | Meaning |
|---|---|---|
| `caloriesPerServing` | `Float?` | Required when `unitType = SERVING`. |
| `proteinPerServing` | `Float?` | Optional when `unitType = SERVING`. |
| `fatPerServing` | `Float?` | Optional when `unitType = SERVING`. |
| `carbsPerServing` | `Float?` | Optional when `unitType = SERVING`. |
| `gramsPerServing` | `Float?` | Optional metadata: "1 serving = X g". Used for SERVING → GRAM conversion. |
| `itemsPerServing` | `Float?` | Optional metadata: "1 serving = Y items". Used for SERVING → ITEM conversion. |

A `SERVING` food populates the four per-serving macro columns, optionally one or both of the serving-definition columns, and leaves all per-100g and per-item columns null. GRAM and ITEM foods leave all six new columns null.

The `Food.calories` / `Food.protein` / `Food.fat` / `Food.carbs` extension properties get a `SERVING` branch returning the per-serving values.

### `DiaryEntry`

One new nullable column added by the same migration:

| Column | Type | Meaning |
|---|---|---|
| `servings` | `Float?` | Populated when `unitType = SERVING`; null otherwise. Mirrors `grams` (GRAM) and `count` (ITEM). |

The denormalized `calories` / `protein` / `fat` / `carbs` snapshot continues to store the total at log time (`caloriesPerServing * servings`, etc.).

### `SavedMealItem`

Same `servings: Float?` column added by the same migration.

### Migration 4 → 5

Additive `ALTER TABLE` statements. No data is deleted or rewritten. Old rows have `null` in the new columns, which matches their `unitType` (none of them are SERVING).

```sql
ALTER TABLE foods ADD COLUMN caloriesPerServing REAL;
ALTER TABLE foods ADD COLUMN proteinPerServing REAL;
ALTER TABLE foods ADD COLUMN fatPerServing REAL;
ALTER TABLE foods ADD COLUMN carbsPerServing REAL;
ALTER TABLE foods ADD COLUMN gramsPerServing REAL;
ALTER TABLE foods ADD COLUMN itemsPerServing REAL;

ALTER TABLE diary_entries ADD COLUMN servings REAL;
ALTER TABLE saved_meal_items ADD COLUMN servings REAL;
```

## Dialog behavior

Both `ScannedFoodDialog` and `CreateFoodDialog` get a three-way radio: **By weight** · **By item** · **By serving**.

### Dialog state

- Macro fields (`calories`, `protein`, `fat`, `carbs`) are unit-dependent. Their labels change with the active mode, and their values are cleared on switch except where prefill applies.
- `gramsPerServing` and `itemsPerServing` are independent state, only visible when mode = SERVING, but their values persist across mode switches within the dialog session. A user who enters "1 serving = 28 g", switches to By weight, then switches back to By serving still sees "28" in the weight-per-serving field.

### Fields shown in SERVING mode

Top to bottom:
- Name
- Calories per serving (required)
- Protein per serving (optional)
- Fat per serving (optional)
- Carbs per serving (optional)
- Weight per serving (g) — optional
- Items per serving — optional
- Servings to log now (default `1`) — `ScannedFoodDialog` only

### Switch rules (`selectUnit(next)`)

| From | To | Behavior |
|---|---|---|
| SERVING | GRAM | If `gramsPerServing` is blank, show the validation dialog and abort the switch. Otherwise compute `cal_per_100g = cal_per_serving / gramsPerServing * 100` (same formula for each filled macro), prefill the macro fields with those values, and switch the mode. |
| SERVING | ITEM | If `itemsPerServing` is blank, show the validation dialog and abort. Otherwise compute `cal_per_item = cal_per_serving / itemsPerServing` (same formula for each filled macro), prefill, switch. |
| GRAM ↔ ITEM | — | Existing behavior: clear macros, switch. No conversion data is available. |
| GRAM/ITEM → SERVING | — | Clear macros, switch. `gramsPerServing` and `itemsPerServing` retain whatever value they held in this dialog session (blank by default). |
| Any → same | — | No-op. |

### Validation dialog

A simple `AlertDialog`:
- Title: "Missing serving information"
- Body: "To switch to 'by weight', enter the weight per serving so we can convert the values." (or the items variant)
- Single "OK" button that dismisses

The mode toggle stays on SERVING.

### Save validation in SERVING mode

`Save & log` (scanner) / `Save` (manual) is enabled when:
- `name` is non-blank, AND
- `caloriesPerServing` is a number ≥ 0, AND
- (scanner only) `servingsToLogNow` is a number > 0

`gramsPerServing` and `itemsPerServing` are NOT required to save in SERVING mode. A SERVING food may be saved with neither serving-definition field populated; it will then be log-able only as servings, never convertible to grams or items.

## ViewModel and downstream wiring

### Dialog `onSave` callback signatures

Both `ScannedFoodDialog` and `CreateFoodDialog` `onSave` callbacks gain two new parameters: `gramsPerServing: Float?` and `itemsPerServing: Float?`, both null unless `unitType == SERVING`. The existing `calories` / `protein` / `fat` / `carbs` parameters retain their position; their meaning is determined by `unitType` (per-100g, per-item, or per-serving).

`ScannedFoodDialog`'s existing `quantity: Float` parameter keeps its meaning: grams in GRAM mode, item count in ITEM mode, servings in SERVING mode. `CreateFoodDialog` has no quantity parameter and does not gain one.

### `DiaryViewModel.logScannedFood`

Add a `SERVING` branch alongside the existing `GRAM` and `ITEM` whens:

- Build a `Food` with `unitType = SERVING`, the four per-serving macro columns populated, `gramsPerServing` / `itemsPerServing` populated if provided, all per-100g and per-item columns null, `userAdded = true`.
- Build a `DiaryEntry` with `unitType = SERVING`, `grams = null`, `count = null`, `servings = quantity`, and the snapshot macros computed as `caloriesPerServing * quantity` (etc.).

### `SearchViewModel.createFood` and `SearchViewModel.logEntry`

Matching `SERVING` branches. `logEntry` reads `caloriesPerServing` etc. from the selected `Food` and writes a `DiaryEntry` with `servings = qty` and snapshot macros computed from per-serving values.

### `SavedMealEditViewModel.updateItemQuantity` and `SavedMealEditViewModel.addItem`

Matching `SERVING` branches that compute snapshot macros from per-serving values and write `servings` on the `SavedMealItem`.

### Display labels (existing call sites that switch on `unitType`)

| File | Line | Change |
|---|---|---|
| `SearchScreen.kt` | 306-308 | `FoodUnitType.SERVING -> "per serving"` |
| `SearchScreen.kt` | 330-332 | `FoodUnitType.SERVING -> "Servings"` (default value `1`) |
| `DiaryScreen.kt` | 607-609 | `FoodUnitType.SERVING -> entry.servings?.let { append("  ·  ${formatCount(it)} serving${if (it == 1f) "" else "s"}") }` |

## Testing

### Unit and Compose tests (new)

**`ScannedFoodDialogTest` / `CreateFoodDialogTest`** (Compose UI):
- SERVING mode renders the per-serving label, weight-per-serving, items-per-serving, and (scanner) servings-to-log fields.
- SERVING → GRAM with `gramsPerServing` blank shows the validation dialog; the toggle stays on SERVING.
- SERVING → GRAM with `gramsPerServing = 50`, `caloriesPerServing = 200` prefills the calories field with `400`.
- SERVING → ITEM with `itemsPerServing` blank shows the validation dialog.
- SERVING → ITEM with `itemsPerServing = 2`, `caloriesPerServing = 200` prefills the calories field with `100`.
- GRAM → SERVING → GRAM (with `gramsPerServing` previously filled) preserves the `gramsPerServing` value across the round trip.
- Save in SERVING mode is enabled when name + calories-per-serving + (scanner) quantity are valid, even with both serving-definition fields blank.
- `onSave` callback receives `unitType = SERVING` and the correct macro and serving-definition values.

**`DiaryViewModelTest.logScannedFood_serving_*`** — verifies the SERVING branch produces a `Food` with per-serving columns populated and per-100g / per-item columns null, and a `DiaryEntry` with `servings = quantity` and snapshot macros equal to `perServing * quantity`.

**`SearchViewModelTest`** — analogous SERVING branch coverage for `createFood` and `logEntry`.

**`SavedMealEditViewModelTest`** — SERVING branch for `updateItemQuantity` and `addItem`.

### Migration test

**`TrackerDatabaseMigrationTest.migrate4to5`** — opens a v4 DB with sample rows in `foods`, `diary_entries`, and `saved_meal_items`, runs the migration, and asserts:
- The eight new columns (six on `foods`, one on `diary_entries`, one on `saved_meal_items`) exist with type `REAL`.
- Existing rows have `null` in the new columns.
- Existing data in the unchanged columns is preserved byte-for-byte.

### Manual verification checklist

- Scan a label, choose By serving, fill calories + grams-per-serving, switch to By weight. Values prefill correctly.
- Same flow but leave grams-per-serving blank. Dialog appears, toggle stays on By serving.
- Save in By serving mode. Food appears in Search with the "per serving" label. Logging it from Search prompts for "Servings", defaulting to 1.
- Diary entry subtitle reads `... · 2 servings` (or `1 serving`).
- Create a SERVING food manually via `CreateFoodDialog`. Same downstream behavior as the scanned variant.
- Save a meal containing a SERVING entry; reopen via the saved-meal picker; the SERVING quantity round-trips correctly.