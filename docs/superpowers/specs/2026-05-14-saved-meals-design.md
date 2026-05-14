# Saved Meals (with Scan-to-Food)

Date: 2026-05-14
Module: `:tracker`

## Goal

Let a user save the foods currently logged in a meal slot (Breakfast, Lunch, Dinner, or Snack) on a given date as a named, reusable "saved meal", then add all of those foods to a meal slot in one action on a later day. As a bundled prerequisite, change the scan flow so every successful scan produces a real `Food` row (not a free-floating diary entry), so saved meals can include scanned items uniformly.

UI uses the word **"Meal"** for this concept; in code we use the type name `SavedMeal` to avoid collision with the existing `MealType` enum (`BREAKFAST` / `LUNCH` / `DINNER` / `SNACK`).

## Non-goals

- Sharing or exporting saved meals.
- Suggesting saved meals automatically (by time of day, history, etc.).
- Per-item portion overrides at apply time (apply as-saved; tweak diary entries afterward).
- A separate "Manage meals" screen distinct from the picker (the picker handles management).
- Retroactively converting existing `SourceType.SCANNED` rows into `Food` rows. Old rows continue to render via their snapshotted macros.
- Compose UI tests for this feature; the module currently has no Compose UI test scaffolding and adding it is out of scope.

## User-visible behavior

### Scan-to-food (bundled prerequisite)

Today, tapping Scan on a meal card immediately writes a `DiaryEntry` with `sourceType = SCANNED`, `foodId = null`, `label = "Scanned label"`, no quantity, and the raw scanned macros.

After this change:

1. User taps Scan on a meal card. `NutritionReaderActivity` runs as today.
2. On `RESULT_OK`, the Diary screen opens a "Save scanned food" dialog: a variant of the existing `CreateFoodDialog` prefilled with the scanned macros, focus on the Name field with the keyboard up. The blank Name forces an explicit name.
3. The existing GRAM / ITEM radio group is shown. Macro field labels and prefilled values follow the selected unit type (per 100 g for GRAM; per item for ITEM). For the GRAM case, the prefilled values come straight from the scan; for ITEM the same numbers are reused as the user can adjust.
4. One new field below the macros: **Quantity** ("Grams" or "Count" depending on unit type), defaulting to `100` for GRAM and `1` for ITEM. This is the amount that will be logged right now.
5. **Save**: writes a `Food` row, then writes a `DiaryEntry` (`sourceType = DATABASE`, `foodId = <new>`) with macros computed for the chosen quantity using the same math used in `SearchViewModel.logEntry`. Both writes happen in one transaction.
6. **Cancel**: discards the scan; nothing written.

`SourceType.SCANNED` is no longer produced by new writes but stays in the enum so legacy rows render correctly. The existing branch in `DiaryEntryRow` that hides the quantity for SCANNED entries is preserved.

### Save as meal

1. On a `MealCard` whose entry list is non-empty, the header row gets a `MoreVert` overflow icon. The menu has one item: **Save as meal**. The icon and menu item are hidden when `entries.isEmpty()`.
2. Tapping it opens a "Save meal" `AlertDialog` containing:
   - A read-only summary of what will be saved, e.g. "Breakfast: 3 items, 540 kcal".
   - A Name text field (focused on open). Default suggestion: `"<MealLabel> – <date>"`, e.g. `"Breakfast – 2026-05-14"`. User may clear or edit it.
   - Save / Cancel buttons. Save is disabled while the trimmed name is empty.
3. **Save** writes (in one transaction):
   - A `SavedMeal` row.
   - One `SavedMealItem` per `DiaryEntry` in that slot, in display order, copying `label` / `foodId` / `unitType` / `grams` / `count` / `calories` / `protein` / `fat` / `carbs`.
   - A `SavedMealSlotApplication` row for the source `mealType` with `lastAppliedAt = now`, so the freshly saved meal sorts to the top of that slot's picker immediately.
4. A short Snackbar appears: `"Saved as '<name>'"`.

Duplicate names are allowed. The picker shows names verbatim.

### Apply a saved meal to a slot

1. Every `MealCard` (empty or populated) gets a third button alongside Scan and Add: **Meals** with a `Bookmark` icon. The action row becomes three equal-weight buttons (Scan / Meals / Add).
2. Tapping it opens a `ModalBottomSheet` titled `"<MealLabel> meals"`:
   - A `LazyColumn` of saved meals filtered to those that have ever been applied to (or were created from) this `mealType`, ordered by `SavedMealSlotApplication.lastAppliedAt DESC`.
   - Below those, saved meals never applied to this slot, ordered by `SavedMeal.createdAt DESC`.
   - Each row: meal name, item count, total kcal computed from item snapshots, e.g. `"My usual breakfast — 3 items · 540 kcal"`.
   - A trailing overflow icon per row → **Edit / Rename / Delete** (see next section).
   - Empty state: `"No saved meals yet. Save one from a populated meal card."`
3. Tapping a row expands it inline to reveal the full item list (label + quantity + per-item kcal) and an `"Add to <MealLabel>"` button. This confirm step prevents accidental bulk inserts.
4. Tapping the confirm button:
   - Inserts one `DiaryEntry` per `SavedMealItem`, with `date = selectedDate`, `mealType = <this slot>`, and `sourceType = DATABASE` if the item's `foodId != null` else `SourceType.SCANNED`. Other fields are copied from the snapshot.
   - Upserts the `SavedMealSlotApplication` row for `(savedMealId, mealType)` with `lastAppliedAt = now`.
   - Closes the sheet. Snackbar: `"Added <N> items to <MealLabel>"`.

The sheet is opened in the context of `selectedDate`, so applies always go to that date. No date picker.

Each applied `DiaryEntry` is independent: deleting one or deleting the saved meal afterward does not affect the other.

### Edit, rename, delete

Entry points are the trailing overflow on each picker row.

**Rename** — small `AlertDialog`, name prefilled, Save / Cancel. Updates only `SavedMeal.name`.

**Delete** — confirmation `AlertDialog`: `"Delete '<name>'? This cannot be undone."` On confirm, deletes the `SavedMeal` row; cascade removes its `SavedMealItem` and `SavedMealSlotApplication` rows. The sheet refreshes via Flow.

**Edit (contents)** — opens a full-screen route `SavedMealEditScreen`:

- Top app bar: back arrow, meal name with a small Rename affordance.
- A `LazyColumn` of items. Each row shows label + quantity + kcal. Per row:
  - A drag handle on the left (reorder), persisted as `position` integers on save. *Optional polish; if non-trivial under Compose Foundation, the implementation plan may defer it. In the deferred case, items keep their original creation order.*
  - Tap-to-edit quantity (same keyboard pattern as `SearchScreen`).
  - Trailing delete icon to remove the item.
- Bottom **Add food** button: opens `SearchScreen` in pick mode and returns the chosen food + quantity to the edit screen rather than writing a `DiaryEntry`.
- App-bar Save and Discard. Save commits all buffered edits in one transaction (`SavedMealDao.replaceItems`). Discard pops back without changes.

**Edit semantics for quantity changes:**
- If the item's `foodId != null` and that `Food` still exists, recompute snapshot macros from `(food.macrosPer100g, grams)` or `(food.macrosPerItem, count)` — the same math used in `SearchViewModel.logEntry`.
- Otherwise (orphan item), scale the existing snapshot macros proportionally to the new quantity.

**Empty meal after edits:** Save is blocked with an inline error `"A meal must contain at least one food."` Discard still works.

### `SearchScreen` pick mode

Rather than fork the search screen, parameterize it:

- `Route.Search` already takes `date` and `mealType`. Add an optional third nav arg `mode` defaulting to `LOG`. When `mode = PICK_FOR_SAVED_MEAL`, the screen reuses `SearchViewModel` but the Add button calls a `onPickResult(food, quantity)` callback path instead of `viewModel.logEntry(...)`.
- Result is delivered to the previous back-stack entry's `SavedStateHandle` and the screen pops.

This keeps a single `SearchScreen` and a single `SearchViewModel` with one switch statement.

## Data model

Three new entities. `Food` and `DiaryEntry` are unchanged.

### `SavedMeal` (table `saved_meals`)

| column      | type   | notes                       |
| ----------- | ------ | --------------------------- |
| `id`        | Long   | PK, auto-generated          |
| `name`      | String | user-facing label           |
| `createdAt` | Long   | epoch millis at save time   |

### `SavedMealItem` (table `saved_meal_items`)

Foreign key on `savedMealId` → `saved_meals.id` `ON DELETE CASCADE`. Index on `savedMealId`.

| column        | type           | notes                                          |
| ------------- | -------------- | ---------------------------------------------- |
| `id`          | Long           | PK, auto-generated                             |
| `savedMealId` | Long           | FK                                             |
| `position`    | Int            | display order within the meal                  |
| `label`       | String         | snapshot of food name at save time             |
| `foodId`      | Long?          | reference to `Food.id`, kept for edit UX only  |
| `unitType`    | FoodUnitType   | GRAM or ITEM                                   |
| `grams`       | Float?         | set when `unitType = GRAM`                     |
| `count`       | Float?         | set when `unitType = ITEM`                     |
| `calories`    | Int?           | snapshot                                       |
| `protein`     | Float?         | snapshot                                       |
| `fat`         | Float?         | snapshot                                       |
| `carbs`       | Float?         | snapshot                                       |

Macros are not re-read from `Food` at apply time; the saved meal is fully self-describing. This mirrors how `DiaryEntry` already snapshots macros and keeps the apply path a simple copy.

### `SavedMealSlotApplication` (table `saved_meal_slot_applications`)

Composite primary key `(savedMealId, mealType)`. Foreign key on `savedMealId` → `saved_meals.id` `ON DELETE CASCADE`.

| column          | type     | notes                                          |
| --------------- | -------- | ---------------------------------------------- |
| `savedMealId`   | Long     | FK, part of PK                                 |
| `mealType`      | MealType | part of PK                                     |
| `lastAppliedAt` | Long     | epoch millis; bumped at create and at each apply |

At most four rows per saved meal (one per slot it has been used in).

### Migration `MIGRATION_2_3`

Adds the three tables and their indexes / foreign keys. No changes to `foods` or `diary_entries`. `TrackerDatabase.version` bumps `2 → 3`. The migration is registered alongside `MIGRATION_1_2` in `TrackerDatabase.getInstance`.

## Architecture

```
ui/diary/
  DiaryScreen.kt          (modified: meal card overflow menu, third "Meals" button, post-scan dialog hookup)
  DiaryViewModel.kt       (modified: stash scan macros, saveCurrentMealAsSavedMeal, applySavedMeal, savedMealsForSlot flows; logScannedEntry replaced by logScannedFood)

ui/components/
  CreateFoodDialog.kt     (lifted out of SearchScreen.kt; gains optional macro prefill + initial quantity field for the scan flow)

ui/savedmeal/             (new package)
  SaveMealDialog.kt
  SavedMealPickerSheet.kt
  SavedMealEditScreen.kt
  SavedMealEditViewModel.kt

ui/search/
  SearchScreen.kt         (modified: optional mode parameter; pick-mode result handoff)
  SearchViewModel.kt      (modified: mode-aware Add path)

navigation/
  NavGraph.kt             (modified: optional mode arg on Route.Search; new Route.SavedMealEdit)

data/model/
  SavedMeal.kt            (new entity)
  SavedMealItem.kt        (new entity)
  SavedMealSlotApplication.kt (new entity)

data/db/
  TrackerDatabase.kt      (modified: register new entities, version 3, MIGRATION_2_3)
  SavedMealDao.kt         (new)

data/repository/
  SavedMealRepository.kt  (new; wraps SavedMealDao)
```

### Key DAO methods (`SavedMealDao`)

- `@Transaction insertSavedMeal(meal, items, initialSlotApplication): Long` — atomic create.
- `getSummariesForSlot(mealType): Flow<List<SavedMealSummary>>` — joined query returning `id`, `name`, item count, kcal sum, `lastAppliedAt` for that slot (null if never applied to it). Ordered by `lastAppliedAt DESC NULLS LAST`, then `createdAt DESC`.
- `getItems(savedMealId): Flow<List<SavedMealItem>>`.
- `@Transaction applyToSlot(savedMealId, mealType, date, now): Int` — reads items, inserts diary entries, upserts the slot-application row, returns inserted count.
- `update(meal)` — for rename.
- `@Transaction replaceItems(savedMealId, newItems)` — delete-then-insert all items in one transaction.
- `delete(savedMealId)` — cascades.

### ViewModel additions

- `DiaryViewModel.scanInProgress: StateFlow<Macros?>` — set on scan return, cleared on dialog dismiss/save. Survives configuration change because it lives in the ViewModel.
- `DiaryViewModel.logScannedFood(name, unitType, macros, quantity, mealType)` — creates the Food + DiaryEntry in one transaction.
- `DiaryViewModel.saveCurrentMealAsSavedMeal(mealType, name)` — reads `entriesByMeal.value[mealType]`, calls repository.
- `DiaryViewModel.savedMealsForSlot(mealType): StateFlow<List<SavedMealSummary>>`.
- `DiaryViewModel.applySavedMeal(savedMealId, mealType)`.

## Error handling and edge cases

- **Empty meal save** is prevented by hiding the overflow item when the slot has no entries.
- **Empty meal after edits**: Save in `SavedMealEditScreen` is blocked with an inline error.
- **Apply with zero items** cannot happen by construction; no runtime guard.
- **Underlying `Food` deleted**: apply still works because macros are snapshotted. The new `DiaryEntry` is written with a stale `foodId` (no FK on `DiaryEntry.foodId` exists today), and rendering uses the row's own macro fields. Edit screen handles orphan items via proportional scaling.
- **Migration safety**: `MIGRATION_2_3` only adds tables. `fallbackToDestructiveMigration()` is not added.
- **Cascade**: `SavedMealItem` and `SavedMealSlotApplication` cascade on `SavedMeal` delete.
- **Naming collisions** are allowed.
- **Scan dialog cancellation**: discards macros; no `Food` and no `DiaryEntry` written.
- **Process death during scan dialog**: the in-flight `Macros` is held in `DiaryViewModel`, restored after recreation. (If implementation finds this to require non-trivial Parcelable / serialization plumbing on `Macros`, the fallback is to clear on process death and require re-scan; documented in the implementation plan.)
- **Picker bottom sheet during data change**: the list is a `Flow`; renames, deletes, applies update the sheet live without manual refresh.
- **Repeated taps on the apply confirm button**: the sheet closes synchronously on confirm. The apply transaction is atomic; in the worst case a duplicate batch is inserted (user can delete). No additional debounce needed.

## Assumptions to verify during implementation

- `nutritionlib`'s `Macros` represents per-100 g values for a label scan. The scan-to-food prefill assumes this for the GRAM case. To verify by reading the `nutritionlib` `Macros` contract before wiring the dialog. If the contract is "as-printed on the label" (which may be per-serving), the dialog handling needs an explicit interpretation step.
- `kotlinx-coroutines-test` and `androidx.room:room-testing` are not currently on the `:tracker` classpath. The implementation plan adds them under `tracker/build.gradle.kts` if missing.
- Compose Foundation reorderable list support in this project's Compose version. If not available without a third-party dep, drag-to-reorder in `SavedMealEditScreen` is dropped from the initial implementation; items keep creation order.

## Testing

The `:tracker` module currently has no `src/test` or `src/androidTest` directories. This change adds the minimum needed to cover the feature:

- **DAO instrumented tests** (`tracker/src/androidTest/`): one class for `SavedMealDao` covering create, summaries query, apply transaction, cascade delete, and slot-application upsert. Uses an in-memory Room DB.
- **Migration test** (instrumented): seed a v2 DB with a `Food` and a `DiaryEntry`, run migration to v3, assert the new tables exist and existing rows are intact.
- **ViewModel unit tests** (`tracker/src/test/`): `DiaryViewModel.saveCurrentMealAsSavedMeal`, `DiaryViewModel.applySavedMeal`, and `SavedMealEditViewModel` save semantics. Fake repository implementations.
- **Manual smoke test** on device for UX paths: scan → save food, save as meal, apply meal, edit meal, rename, delete, cascade behavior, sort order in the picker.

No Compose UI tests are added.