# By-Serving Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `SERVING` as a first-class `FoodUnitType` alongside `GRAM` and `ITEM`, with optional grams-per-serving / items-per-serving metadata so `ScannedFoodDialog` and `CreateFoodDialog` can convert per-serving values on demand and the diary can log entries as a number of servings.

**Architecture:** Additive Room migration v4 → v5 adds six nullable columns to `foods` (the four per-serving macros plus `gramsPerServing` and `itemsPerServing`) and one nullable `servings` column to each of `diary_entries` and `saved_meal_items`. The `FoodUnitType` enum gains a `SERVING` case; every exhaustive `when (unitType)` site grows a third branch. Both dialogs become tri-modal: the `By serving` radio shows the per-serving macro fields plus the two serving-definition fields, and switching to `By weight` or `By item` either converts and prefills (when the relevant serving-definition field is filled) or pops a validation dialog (when it is not). Conversion math is extracted into a small pure-Kotlin file so it can be unit-tested without Compose infrastructure.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room (schema migration v4 → v5), Kotlin coroutines.

**Spec:** `docs/superpowers/specs/2026-05-19-by-serving-toggle-design.md`

**Working module:** `tracker/`

---

## File Structure

**Create**
- `tracker/src/main/java/com/graydyn/tracker/ui/components/ServingConversion.kt` — pure top-level helpers `perServingToPer100g(perServing: Float?, gramsPerServing: Float): Float?` and `perServingToPerItem(perServing: Float?, itemsPerServing: Float): Float?`. Returning `null` when input is null, returning `0f` when input is `0f`, dividing otherwise.
- `tracker/src/test/java/com/graydyn/tracker/ui/components/ServingConversionTest.kt` — plain JUnit tests for the above.
- `tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration4To5Test.kt` — Room migration test mirroring `Migration2To3Test`.

**Modify**
- `tracker/src/main/java/com/graydyn/tracker/data/model/FoodUnitType.kt` — add `SERVING`.
- `tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt` — add six nullable per-serving columns; add `SERVING` branch to each of the four extension properties.
- `tracker/src/main/java/com/graydyn/tracker/data/model/DiaryEntry.kt` — add nullable `servings: Float?` column.
- `tracker/src/main/java/com/graydyn/tracker/data/model/SavedMealItem.kt` — add nullable `servings: Float?` column.
- `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt` — bump `version = 4` to `version = 5`; add `MIGRATION_4_5`; register it in `getInstance`.
- `tracker/src/main/java/com/graydyn/tracker/data/seed/CsvSeeder.kt` — pass nulls for the six new `Food` columns.
- `tracker/src/main/java/com/graydyn/tracker/data/repository/SavedMealRepository.kt` — thread `servings` through `saveFromDiaryEntries` and `applyToSlot`.
- `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt` — add `gramsPerServing` / `itemsPerServing` parameters to `logScannedFood`; add `SERVING` branch.
- `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt` — update `ScannedFoodDialog` caller for new `onSave` shape; add `SERVING` branch to the entry subtitle.
- `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt` — add `gramsPerServing` / `itemsPerServing` to `createFood`; add `SERVING` branches to `createFood` and `logEntry`.
- `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt` — update `CreateFoodDialog` caller; add `SERVING` branches to the per-unit caption and the quantity-field label.
- `tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModel.kt` — add `SERVING` branches to `applyQuantityChange` and `addPickedFood`.
- `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt` — three-way radio, per-serving fields, switch logic with validation dialog, conversion-on-switch, updated `onSave` signature.
- `tracker/src/main/java/com/graydyn/tracker/ui/components/CreateFoodDialog.kt` — same set of changes minus the quantity field.

**Test additions in existing files**
- `tracker/src/androidTest/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModelTest.kt` (extend with serving cases).

**Note on testing scope**

The spec listed Compose UI tests for the two dialogs and ViewModel-level tests for `DiaryViewModel` and `SearchViewModel`. The `:tracker` module has no Compose UI test infrastructure today, and `DiaryViewModel` / `SearchViewModel` construct their dependencies from `TrackerDatabase.getInstance(application)` internally — there is no seam for injecting an in-memory database without a wider refactor. This plan therefore covers the SERVING paths with:

1. **`ServingConversion` unit tests** (Task 7) — the only non-trivial math (per-serving → per-100g and per-serving → per-item).
2. **Room migration test** (Task 4) — verifies the schema bump preserves data and adds the eight new nullable columns.
3. **`SavedMealEditViewModel` tests** (Task 6) — the one VM with an injectable repo; exercises the SERVING branch in both `addPickedFood` and `applyQuantityChange`.
4. **Manual verification** (Task 10) — end-to-end coverage of scan → save → search → diary subtitle, plus the validation-dialog UX.

Adding test seams for the other two view models is intentionally deferred; the SERVING branches in those files are structurally identical to the existing GRAM/ITEM branches.

---

## Build and test commands

Throughout this plan:

- Build: `./gradlew :tracker:assembleDebug`
- JVM unit tests: `./gradlew :tracker:testDebugUnitTest`
- Instrumented (Room migration + ViewModel): `./gradlew :tracker:connectedDebugAndroidTest` — requires an emulator or device.

When a step says "Run tests," prefer the JVM tests where possible; instrumented tests are only required after migration / ViewModel changes.

---

### Task 1: Add `servings` column to `SavedMealItem` and thread through the repository

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/model/SavedMealItem.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/repository/SavedMealRepository.kt`
- Modify: `tracker/src/androidTest/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModelTest.kt`

This task is purely additive — adds a nullable column, propagates it through the two repository methods that construct `SavedMealItem` and `DiaryEntry`. No behavior change; `null` everywhere.

- [ ] **Step 1: Add `servings` to `SavedMealItem`**

Open `tracker/src/main/java/com/graydyn/tracker/data/model/SavedMealItem.kt`. Replace the entire file with:

```kotlin
package com.graydyn.tracker.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_meal_items",
    foreignKeys = [
        ForeignKey(
            entity = SavedMeal::class,
            parentColumns = ["id"],
            childColumns = ["savedMealId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["savedMealId"])]
)
data class SavedMealItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedMealId: Long,
    val position: Int,
    val label: String,
    val foodId: Long?,
    val unitType: FoodUnitType,
    val grams: Float?,
    val count: Float?,
    @ColumnInfo(name = "servings") val servings: Float? = null,
    val calories: Int?,
    val protein: Float?,
    val fat: Float?,
    val carbs: Float?
)
```

Note: the explicit `@ColumnInfo(name = "servings")` is documentation; Room would default to the property name. The default value lets existing `SavedMealItem(...)` callers continue to work without naming every argument.

- [ ] **Step 2: Thread `servings` through `SavedMealRepository`**

Open `tracker/src/main/java/com/graydyn/tracker/data/repository/SavedMealRepository.kt`. Two functions need updating:

In `saveFromDiaryEntries`, find the `SavedMealItem(...)` constructor (currently lines 45-57). Replace with:

```kotlin
                    SavedMealItem(
                        savedMealId = savedMealId,
                        position = index,
                        label = entry.label,
                        foodId = entry.foodId,
                        unitType = entry.unitType,
                        grams = entry.grams,
                        count = entry.count,
                        servings = entry.servings,
                        calories = entry.calories,
                        protein = entry.protein,
                        fat = entry.fat,
                        carbs = entry.carbs
                    )
```

(This references `entry.servings`, which doesn't exist yet — that's added in Task 2. To keep the build green for now, instead use `servings = null` and we will return to fix this in Task 2's step that updates the repository.)

Actually do this: leave the line as `servings = null` for now. We will change it to `entry.servings` in Task 2 once `DiaryEntry.servings` exists.

```kotlin
                    SavedMealItem(
                        savedMealId = savedMealId,
                        position = index,
                        label = entry.label,
                        foodId = entry.foodId,
                        unitType = entry.unitType,
                        grams = entry.grams,
                        count = entry.count,
                        servings = null,
                        calories = entry.calories,
                        protein = entry.protein,
                        fat = entry.fat,
                        carbs = entry.carbs
                    )
```

In `applyToSlot`, find the `DiaryEntry(...)` constructor (currently lines 73-86). Leave it unchanged for now (DiaryEntry has no `servings` yet). We will return in Task 2 to thread `servings = item.servings` through.

- [ ] **Step 3: Update existing test's `gramEntry` helper to keep building**

Open `tracker/src/androidTest/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModelTest.kt`. The test does not need to change yet because the new `SavedMealItem.servings` has a default value of `null` and existing tests construct `DiaryEntry` (not `SavedMealItem`). Verify the file is unchanged.

- [ ] **Step 4: Build**

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Room will fail at runtime if you try to open an existing v4 DB because the schema no longer matches, but compilation succeeds (we'll add the migration in Task 4).

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/model/SavedMealItem.kt tracker/src/main/java/com/graydyn/tracker/data/repository/SavedMealRepository.kt
git commit -m "feat(tracker): add nullable servings column to SavedMealItem"
```

---

### Task 2: Add `servings` column to `DiaryEntry` and propagate

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/model/DiaryEntry.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/repository/SavedMealRepository.kt`

- [ ] **Step 1: Add `servings` to `DiaryEntry`**

Open `tracker/src/main/java/com/graydyn/tracker/data/model/DiaryEntry.kt`. Replace the entire file with:

```kotlin
package com.graydyn.tracker.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }
enum class SourceType { DATABASE, SCANNED }

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
    val grams: Float?,
    val count: Float?,
    val servings: Float? = null,
    val calories: Int?,
    val protein: Float?,
    val fat: Float?,
    val carbs: Float?
)
```

The default `null` keeps existing callers compiling.

- [ ] **Step 2: Thread `servings` through `SavedMealRepository`**

Open `tracker/src/main/java/com/graydyn/tracker/data/repository/SavedMealRepository.kt`.

In `saveFromDiaryEntries`, replace `servings = null,` with `servings = entry.servings,`.

In `applyToSlot`, find the `DiaryEntry(...)` constructor and add a `servings = item.servings,` line right after `count = item.count,`:

```kotlin
            val entry = DiaryEntry(
                date = date,
                mealType = mealType,
                label = item.label,
                sourceType = if (item.foodId != null) SourceType.DATABASE else SourceType.SCANNED,
                foodId = item.foodId,
                unitType = item.unitType,
                grams = item.grams,
                count = item.count,
                servings = item.servings,
                calories = item.calories,
                protein = item.protein,
                fat = item.fat,
                carbs = item.carbs
            )
```

- [ ] **Step 3: Build**

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/model/DiaryEntry.kt tracker/src/main/java/com/graydyn/tracker/data/repository/SavedMealRepository.kt
git commit -m "feat(tracker): add nullable servings column to DiaryEntry"
```

---

### Task 3: Add per-serving columns to `Food`

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt`

`Food` gets six nullable columns. Because all six have `= null` defaults, no `Food(...)` caller needs to change. The extension properties get a `SERVING` branch in Task 5 once the enum case exists.

- [ ] **Step 1: Add the six new columns**

Open `tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt`. Replace the entire file with:

```kotlin
package com.graydyn.tracker.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "foods")
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unitType: FoodUnitType,
    val caloriesPer100g: Float?,
    val proteinPer100g: Float?,
    val fatPer100g: Float?,
    val carbsPer100g: Float?,
    val caloriesPerItem: Float?,
    val proteinPerItem: Float?,
    val fatPerItem: Float?,
    val carbsPerItem: Float?,
    val caloriesPerServing: Float? = null,
    val proteinPerServing: Float? = null,
    val fatPerServing: Float? = null,
    val carbsPerServing: Float? = null,
    val gramsPerServing: Float? = null,
    val itemsPerServing: Float? = null,
    @ColumnInfo(defaultValue = "0") val foundational: Boolean = false,
    @ColumnInfo(defaultValue = "0") val userAdded: Boolean = false,
)

val Food.calories: Float?
    get() = if (unitType == FoodUnitType.ITEM) caloriesPerItem else caloriesPer100g

val Food.protein: Float?
    get() = if (unitType == FoodUnitType.ITEM) proteinPerItem else proteinPer100g

val Food.fat: Float?
    get() = if (unitType == FoodUnitType.ITEM) fatPerItem else fatPer100g

val Food.carbs: Float?
    get() = if (unitType == FoodUnitType.ITEM) carbsPerItem else carbsPer100g
```

The extension properties intentionally still only branch on `ITEM` vs. else; they will be expanded in Task 5 when the `SERVING` enum case is added.

- [ ] **Step 2: Build**

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt
git commit -m "feat(tracker): add nullable per-serving columns to Food"
```

---

### Task 4: Bump DB version to 5 and add `MIGRATION_4_5`

**Files:**
- Create: `tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration4To5Test.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt`

TDD-style: write the migration test first, watch it fail because the migration isn't registered yet, then write the migration.

- [ ] **Step 1: Write the failing migration test**

Create `tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration4To5Test.kt`:

```kotlin
package com.graydyn.tracker.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {

    private val testDbName = "tracker-migration-4to5-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrackerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate4To5_addsNewColumnsAndPreservesExistingRows() {
        helper.createDatabase(testDbName, 4).use { v4 ->
            v4.execSQL(
                """
                INSERT INTO foods (name, unitType, caloriesPer100g, proteinPer100g, fatPer100g, carbsPer100g, caloriesPerItem, proteinPerItem, fatPerItem, carbsPerItem, foundational, userAdded)
                VALUES ('Oats', 'GRAM', 379.0, 13.0, 7.0, 67.0, NULL, NULL, NULL, NULL, 1, 0)
                """.trimIndent()
            )
            v4.execSQL(
                """
                INSERT INTO diary_entries (date, mealType, label, sourceType, foodId, unitType, grams, count, calories, protein, fat, carbs)
                VALUES ('2026-05-19', 'BREAKFAST', 'Oats', 'DATABASE', 1, 'GRAM', 50.0, NULL, 190, 6.5, 3.5, 33.5)
                """.trimIndent()
            )
            v4.execSQL(
                """
                INSERT INTO saved_meals (name, createdAt) VALUES ('M', 1000)
                """.trimIndent()
            )
            v4.execSQL(
                """
                INSERT INTO saved_meal_items (savedMealId, position, label, foodId, unitType, grams, count, calories, protein, fat, carbs)
                VALUES (1, 0, 'Oats', 1, 'GRAM', 50.0, NULL, 190, 6.5, 3.5, 33.5)
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            testDbName,
            5,
            true,
            TrackerDatabase.MIGRATION_4_5
        )

        // Existing data preserved
        migrated.query("SELECT name FROM foods").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals("Oats", c.getString(0))
        }

        // New foods columns exist and are null on old rows
        migrated.query(
            "SELECT caloriesPerServing, proteinPerServing, fatPerServing, carbsPerServing, gramsPerServing, itemsPerServing FROM foods"
        ).use { c ->
            assertEquals(true, c.moveToFirst())
            for (i in 0..5) {
                assertEquals("column $i should be null on old row", true, c.isNull(i))
            }
        }

        // diary_entries gains servings
        migrated.query("SELECT servings FROM diary_entries").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals(true, c.isNull(0))
        }

        // saved_meal_items gains servings
        migrated.query("SELECT servings FROM saved_meal_items").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals(true, c.isNull(0))
        }
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

```bash
./gradlew :tracker:connectedDebugAndroidTest --tests "com.graydyn.tracker.data.db.Migration4To5Test"
```

Expected: failure. Either compilation error (because `MIGRATION_4_5` doesn't exist) or runtime failure ("no such column"). If the test reports `TrackerDatabase.MIGRATION_4_5` is unresolved, that is the expected failure.

- [ ] **Step 3: Bump DB version and write `MIGRATION_4_5`**

Open `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt`. Change `version = 4` to `version = 5` in the `@Database` annotation. Inside the `companion object`, after `MIGRATION_3_4`, add:

```kotlin
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN caloriesPerServing REAL")
                db.execSQL("ALTER TABLE foods ADD COLUMN proteinPerServing REAL")
                db.execSQL("ALTER TABLE foods ADD COLUMN fatPerServing REAL")
                db.execSQL("ALTER TABLE foods ADD COLUMN carbsPerServing REAL")
                db.execSQL("ALTER TABLE foods ADD COLUMN gramsPerServing REAL")
                db.execSQL("ALTER TABLE foods ADD COLUMN itemsPerServing REAL")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN servings REAL")
                db.execSQL("ALTER TABLE saved_meal_items ADD COLUMN servings REAL")
            }
        }
```

In `getInstance`, change the `addMigrations` call to include the new migration:

```kotlin
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
```

- [ ] **Step 4: Run the migration test and verify it passes**

```bash
./gradlew :tracker:connectedDebugAndroidTest --tests "com.graydyn.tracker.data.db.Migration4To5Test"
```

Expected: `BUILD SUCCESSFUL`, 1 test passes. Room will also write a new schema JSON to `tracker/schemas/com.graydyn.tracker.data.db.TrackerDatabase/5.json` during the compile.

- [ ] **Step 5: Confirm the generated schema**

```bash
ls tracker/schemas/com.graydyn.tracker.data.db.TrackerDatabase/
```

Expected: `2.json  3.json  4.json  5.json`.

- [ ] **Step 6: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration4To5Test.kt tracker/schemas/com.graydyn.tracker.data.db.TrackerDatabase/5.json
git commit -m "feat(tracker): add MIGRATION_4_5 for per-serving columns"
```

---

### Task 5: Add `SERVING` to `FoodUnitType` and update all exhaustive `when` sites

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/model/FoodUnitType.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModel.kt`

Adding `SERVING` to the enum will break every `when (unitType) { GRAM -> ...; ITEM -> ... }` switch in the codebase. This single task adds the enum case and updates every such site so the build stays green. The new dialog signatures (with `gramsPerServing`/`itemsPerServing` parameters) are also added here; callers pass `null` until Tasks 8 and 9 wire the dialog values through.

- [ ] **Step 1: Add `SERVING` to `FoodUnitType`**

Open `tracker/src/main/java/com/graydyn/tracker/data/model/FoodUnitType.kt`. Replace with:

```kotlin
package com.graydyn.tracker.data.model

enum class FoodUnitType { GRAM, ITEM, SERVING }
```

- [ ] **Step 2: Update `Food` extension properties**

Open `tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt`. Replace the four extension properties at the bottom of the file with these `when`-based versions:

```kotlin
val Food.calories: Float?
    get() = when (unitType) {
        FoodUnitType.GRAM -> caloriesPer100g
        FoodUnitType.ITEM -> caloriesPerItem
        FoodUnitType.SERVING -> caloriesPerServing
    }

val Food.protein: Float?
    get() = when (unitType) {
        FoodUnitType.GRAM -> proteinPer100g
        FoodUnitType.ITEM -> proteinPerItem
        FoodUnitType.SERVING -> proteinPerServing
    }

val Food.fat: Float?
    get() = when (unitType) {
        FoodUnitType.GRAM -> fatPer100g
        FoodUnitType.ITEM -> fatPerItem
        FoodUnitType.SERVING -> fatPerServing
    }

val Food.carbs: Float?
    get() = when (unitType) {
        FoodUnitType.GRAM -> carbsPer100g
        FoodUnitType.ITEM -> carbsPerItem
        FoodUnitType.SERVING -> carbsPerServing
    }
```

- [ ] **Step 3: Update `DiaryViewModel.logScannedFood` signature and add `SERVING` branch**

Open `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt`. Find the `logScannedFood` function (currently lines 181-256). Replace its signature and body with:

```kotlin
    fun logScannedFood(
        name: String,
        unitType: FoodUnitType,
        calories: Float,
        protein: Float?,
        fat: Float?,
        carbs: Float?,
        gramsPerServing: Float?,
        itemsPerServing: Float?,
        quantity: Float,
        mealType: MealType
    ) {
        _scanInProgress.value = null
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
            val food = when (unitType) {
                FoodUnitType.GRAM -> Food(
                    name = name.trim(),
                    unitType = FoodUnitType.GRAM,
                    caloriesPer100g = calories,
                    proteinPer100g = protein,
                    fatPer100g = fat,
                    carbsPer100g = carbs,
                    caloriesPerItem = null,
                    proteinPerItem = null,
                    fatPerItem = null,
                    carbsPerItem = null,
                    userAdded = true,
                )
                FoodUnitType.ITEM -> Food(
                    name = name.trim(),
                    unitType = FoodUnitType.ITEM,
                    caloriesPer100g = null,
                    proteinPer100g = null,
                    fatPer100g = null,
                    carbsPer100g = null,
                    caloriesPerItem = calories,
                    proteinPerItem = protein,
                    fatPerItem = fat,
                    carbsPerItem = carbs,
                    userAdded = true,
                )
                FoodUnitType.SERVING -> Food(
                    name = name.trim(),
                    unitType = FoodUnitType.SERVING,
                    caloriesPer100g = null,
                    proteinPer100g = null,
                    fatPer100g = null,
                    carbsPer100g = null,
                    caloriesPerItem = null,
                    proteinPerItem = null,
                    fatPerItem = null,
                    carbsPerItem = null,
                    caloriesPerServing = calories,
                    proteinPerServing = protein,
                    fatPerServing = fat,
                    carbsPerServing = carbs,
                    gramsPerServing = gramsPerServing,
                    itemsPerServing = itemsPerServing,
                    userAdded = true,
                )
            }
            val foodId = foodRepo.add(food)
            val entry = when (unitType) {
                FoodUnitType.GRAM -> DiaryEntry(
                    date = _selectedDate.value,
                    mealType = mealType,
                    label = food.name,
                    sourceType = SourceType.DATABASE,
                    foodId = foodId,
                    unitType = FoodUnitType.GRAM,
                    grams = quantity,
                    count = null,
                    calories = food.caloriesPer100g?.let { (it * quantity / 100f).toInt() },
                    protein = food.proteinPer100g?.let { it * quantity / 100f },
                    fat = food.fatPer100g?.let { it * quantity / 100f },
                    carbs = food.carbsPer100g?.let { it * quantity / 100f }
                )
                FoodUnitType.ITEM -> DiaryEntry(
                    date = _selectedDate.value,
                    mealType = mealType,
                    label = food.name,
                    sourceType = SourceType.DATABASE,
                    foodId = foodId,
                    unitType = FoodUnitType.ITEM,
                    grams = null,
                    count = quantity,
                    calories = food.caloriesPerItem?.let { (it * quantity).toInt() },
                    protein = food.proteinPerItem?.let { it * quantity },
                    fat = food.fatPerItem?.let { it * quantity },
                    carbs = food.carbsPerItem?.let { it * quantity }
                )
                FoodUnitType.SERVING -> DiaryEntry(
                    date = _selectedDate.value,
                    mealType = mealType,
                    label = food.name,
                    sourceType = SourceType.DATABASE,
                    foodId = foodId,
                    unitType = FoodUnitType.SERVING,
                    grams = null,
                    count = null,
                    servings = quantity,
                    calories = food.caloriesPerServing?.let { (it * quantity).toInt() },
                    protein = food.proteinPerServing?.let { it * quantity },
                    fat = food.fatPerServing?.let { it * quantity },
                    carbs = food.carbsPerServing?.let { it * quantity }
                )
            }
            diaryRepo.insert(entry)
            }
        }
    }
```

- [ ] **Step 4: Update `DiaryScreen` caller**

Open `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt`. Find the `ScannedFoodDialog` invocation (currently lines 237-252). Replace its `onSave` lambda with:

```kotlin
                onSave = { name, unitType, calories, protein, fat, carbs, gramsPerServing, itemsPerServing, quantity ->
                    viewModel.logScannedFood(
                        name = name,
                        unitType = unitType,
                        calories = calories,
                        protein = protein,
                        fat = fat,
                        carbs = carbs,
                        gramsPerServing = gramsPerServing,
                        itemsPerServing = itemsPerServing,
                        quantity = quantity,
                        mealType = scanTargetMeal
                    )
                }
```

(This expects `ScannedFoodDialog` to expose the wider `onSave` shape; the dialog file is updated in Task 8. For now this is "ahead of" the dialog and the build will break; we will fix it temporarily by also adding the two parameters to the `ScannedFoodDialog` signature with default `null`s in this same task — see Step 7.)

- [ ] **Step 5: Add `SERVING` branch to entry subtitle**

In the same file, find the entry-subtitle `when (entry.unitType)` (currently lines 607-610). Replace with:

```kotlin
                        when (entry.unitType) {
                            FoodUnitType.GRAM -> entry.grams?.let { append("  ·  ${it.toInt()}g") }
                            FoodUnitType.ITEM -> entry.count?.let { append("  ·  ×${formatCount(it)}") }
                            FoodUnitType.SERVING -> entry.servings?.let {
                                append("  ·  ${formatCount(it)} serving${if (it == 1f) "" else "s"}")
                            }
                        }
```

- [ ] **Step 6: Update `SearchViewModel.createFood` signature and add `SERVING` branches**

Open `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`. Find `createFood` (currently lines 82-128). Replace its signature and `food` construction with:

```kotlin
    fun createFood(
        name: String,
        unitType: FoodUnitType,
        calories: Float,
        protein: Float?,
        fat: Float?,
        carbs: Float?,
        gramsPerServing: Float?,
        itemsPerServing: Float?
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
                    caloriesPerItem = null,
                    proteinPerItem = null,
                    fatPerItem = null,
                    carbsPerItem = null,
                    userAdded = true,
                )
                FoodUnitType.ITEM -> Food(
                    name = name.trim(),
                    unitType = FoodUnitType.ITEM,
                    caloriesPer100g = null,
                    proteinPer100g = null,
                    fatPer100g = null,
                    carbsPer100g = null,
                    caloriesPerItem = calories,
                    proteinPerItem = protein,
                    fatPerItem = fat,
                    carbsPerItem = carbs,
                    userAdded = true,
                )
                FoodUnitType.SERVING -> Food(
                    name = name.trim(),
                    unitType = FoodUnitType.SERVING,
                    caloriesPer100g = null,
                    proteinPer100g = null,
                    fatPer100g = null,
                    carbsPer100g = null,
                    caloriesPerItem = null,
                    proteinPerItem = null,
                    fatPerItem = null,
                    carbsPerItem = null,
                    caloriesPerServing = calories,
                    proteinPerServing = protein,
                    fatPerServing = fat,
                    carbsPerServing = carbs,
                    gramsPerServing = gramsPerServing,
                    itemsPerServing = itemsPerServing,
                    userAdded = true,
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

Find `logEntry` (currently lines 131-167). Replace the `when (food.unitType)` block with:

```kotlin
        val entry = when (food.unitType) {
            FoodUnitType.GRAM -> DiaryEntry(
                date = date,
                mealType = mealType,
                label = food.name,
                sourceType = SourceType.DATABASE,
                foodId = food.id,
                unitType = FoodUnitType.GRAM,
                grams = qty,
                count = null,
                calories = food.caloriesPer100g?.let { (it * qty / 100f).toInt() },
                protein = food.proteinPer100g?.let { it * qty / 100f },
                fat = food.fatPer100g?.let { it * qty / 100f },
                carbs = food.carbsPer100g?.let { it * qty / 100f }
            )
            FoodUnitType.ITEM -> DiaryEntry(
                date = date,
                mealType = mealType,
                label = food.name,
                sourceType = SourceType.DATABASE,
                foodId = food.id,
                unitType = FoodUnitType.ITEM,
                grams = null,
                count = qty,
                calories = food.caloriesPerItem?.let { (it * qty).toInt() },
                protein = food.proteinPerItem?.let { it * qty },
                fat = food.fatPerItem?.let { it * qty },
                carbs = food.carbsPerItem?.let { it * qty }
            )
            FoodUnitType.SERVING -> DiaryEntry(
                date = date,
                mealType = mealType,
                label = food.name,
                sourceType = SourceType.DATABASE,
                foodId = food.id,
                unitType = FoodUnitType.SERVING,
                grams = null,
                count = null,
                servings = qty,
                calories = food.caloriesPerServing?.let { (it * qty).toInt() },
                protein = food.proteinPerServing?.let { it * qty },
                fat = food.fatPerServing?.let { it * qty },
                carbs = food.carbsPerServing?.let { it * qty }
            )
        }
```

- [ ] **Step 7: Update `SearchScreen` caller and labels**

Open `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt`. Find the `CreateFoodDialog` invocation (currently lines 198-205). Replace with:

```kotlin
        if (showCreateDialog) {
            CreateFoodDialog(
                initialName = query,
                onDismiss = { viewModel.dismissCreateDialog() },
                onSave = { name, unitType, calories, protein, fat, carbs, gramsPerServing, itemsPerServing ->
                    viewModel.createFood(name, unitType, calories, protein, fat, carbs, gramsPerServing, itemsPerServing)
                }
            )
        }
```

Find the per-unit caption (currently lines 306-309). Replace with:

```kotlin
            Text(
                text = when (food.unitType) {
                    FoodUnitType.GRAM -> "per 100g"
                    FoodUnitType.ITEM -> "per item"
                    FoodUnitType.SERVING -> "per serving"
                },
```

Find the quantity-field label (currently lines 330-334). Replace with:

```kotlin
                                when (food.unitType) {
                                    FoodUnitType.GRAM -> "Grams"
                                    FoodUnitType.ITEM -> "Items"
                                    FoodUnitType.SERVING -> "Servings"
                                }
```

- [ ] **Step 8: Update `SavedMealEditViewModel`**

Open `tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModel.kt`. Replace `applyQuantityChange` with:

```kotlin
    private fun applyQuantityChange(item: SavedMealItem, newQty: Float, food: Food?): SavedMealItem {
        if (food != null) {
            return when (item.unitType) {
                FoodUnitType.GRAM -> item.copy(
                    grams = newQty,
                    calories = food.caloriesPer100g?.let { (it * newQty / 100f).toInt() },
                    protein = food.proteinPer100g?.let { it * newQty / 100f },
                    fat = food.fatPer100g?.let { it * newQty / 100f },
                    carbs = food.carbsPer100g?.let { it * newQty / 100f }
                )
                FoodUnitType.ITEM -> item.copy(
                    count = newQty,
                    calories = food.caloriesPerItem?.let { (it * newQty).toInt() },
                    protein = food.proteinPerItem?.let { it * newQty },
                    fat = food.fatPerItem?.let { it * newQty },
                    carbs = food.carbsPerItem?.let { it * newQty }
                )
                FoodUnitType.SERVING -> item.copy(
                    servings = newQty,
                    calories = food.caloriesPerServing?.let { (it * newQty).toInt() },
                    protein = food.proteinPerServing?.let { it * newQty },
                    fat = food.fatPerServing?.let { it * newQty },
                    carbs = food.carbsPerServing?.let { it * newQty }
                )
            }
        }
        // Orphan: scale snapshot proportionally
        val oldQty = (item.grams ?: item.count ?: item.servings ?: 0f).coerceAtLeast(0.001f)
        val ratio = newQty / oldQty
        return item.copy(
            grams = item.grams?.let { newQty },
            count = item.count?.let { newQty },
            servings = item.servings?.let { newQty },
            calories = item.calories?.let { (it * ratio).toInt() },
            protein = item.protein?.let { it * ratio },
            fat = item.fat?.let { it * ratio },
            carbs = item.carbs?.let { it * ratio }
        )
    }
```

Replace `addPickedFood` with:

```kotlin
    fun addPickedFood(food: Food, quantity: Float) {
        val unitType = food.unitType
        val newItem = when (unitType) {
            FoodUnitType.GRAM -> SavedMealItem(
                savedMealId = savedMealId,
                position = _items.value.size,
                label = food.name,
                foodId = food.id,
                unitType = FoodUnitType.GRAM,
                grams = quantity, count = null,
                calories = food.caloriesPer100g?.let { (it * quantity / 100f).toInt() },
                protein = food.proteinPer100g?.let { it * quantity / 100f },
                fat = food.fatPer100g?.let { it * quantity / 100f },
                carbs = food.carbsPer100g?.let { it * quantity / 100f }
            )
            FoodUnitType.ITEM -> SavedMealItem(
                savedMealId = savedMealId,
                position = _items.value.size,
                label = food.name,
                foodId = food.id,
                unitType = FoodUnitType.ITEM,
                grams = null, count = quantity,
                calories = food.caloriesPerItem?.let { (it * quantity).toInt() },
                protein = food.proteinPerItem?.let { it * quantity },
                fat = food.fatPerItem?.let { it * quantity },
                carbs = food.carbsPerItem?.let { it * quantity }
            )
            FoodUnitType.SERVING -> SavedMealItem(
                savedMealId = savedMealId,
                position = _items.value.size,
                label = food.name,
                foodId = food.id,
                unitType = FoodUnitType.SERVING,
                grams = null, count = null, servings = quantity,
                calories = food.caloriesPerServing?.let { (it * quantity).toInt() },
                protein = food.proteinPerServing?.let { it * quantity },
                fat = food.fatPerServing?.let { it * quantity },
                carbs = food.carbsPerServing?.let { it * quantity }
            )
        }
        _items.value = _items.value + newItem
    }
```

- [ ] **Step 9: Temporarily widen dialog signatures so the build stays green**

The two `DiaryScreen` and `SearchScreen` callers above already pass the new `gramsPerServing` / `itemsPerServing` parameters to the dialog `onSave` callbacks. The dialog files themselves don't accept those parameters yet — Tasks 8 and 9 do the real dialog work. To keep the build compiling, **temporarily** widen the two dialog `onSave` signatures now so the callers match.

Open `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt`. Change the `onSave` parameter signature (currently lines 43-51) to:

```kotlin
    onSave: (
        name: String,
        unitType: FoodUnitType,
        calories: Float,
        protein: Float?,
        fat: Float?,
        carbs: Float?,
        gramsPerServing: Float?,
        itemsPerServing: Float?,
        quantity: Float
    ) -> Unit
```

Find the `onSave(...)` invocation inside the confirmButton (currently lines 191-199). Replace with:

```kotlin
                    onSave(
                        trimmedName,
                        unitType,
                        parsedCalories!!,
                        protein.trim().toFloatOrNull(),
                        fat.trim().toFloatOrNull(),
                        carbs.trim().toFloatOrNull(),
                        null,
                        null,
                        parsedQuantity!!
                    )
```

Open `tracker/src/main/java/com/graydyn/tracker/ui/components/CreateFoodDialog.kt`. Change the `onSave` parameter signature (currently lines 33-40) to:

```kotlin
    onSave: (
        name: String,
        unitType: FoodUnitType,
        calories: Float,
        protein: Float?,
        fat: Float?,
        carbs: Float?,
        gramsPerServing: Float?,
        itemsPerServing: Float?
    ) -> Unit
```

Find the `onSave(...)` invocation inside the confirmButton (currently lines 183-190). Replace with:

```kotlin
                    onSave(
                        trimmedName,
                        unitType,
                        parsedCalories!!,
                        protein.trim().toFloatOrNull(),
                        fat.trim().toFloatOrNull(),
                        carbs.trim().toFloatOrNull(),
                        null,
                        null
                    )
```

- [ ] **Step 10: Build**

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. No `when` switch on `FoodUnitType` should produce a non-exhaustive warning.

- [ ] **Step 11: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/model/FoodUnitType.kt tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModel.kt tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt tracker/src/main/java/com/graydyn/tracker/ui/components/CreateFoodDialog.kt
git commit -m "feat(tracker): add SERVING FoodUnitType and propagate through callers"
```

---

### Task 6: ViewModel SERVING-branch tests

**Files:**
- Modify: `tracker/src/androidTest/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModelTest.kt`

**Scope note.** `DiaryViewModel` and `SearchViewModel` construct their dependencies from `TrackerDatabase.getInstance(application)` internally with no seam for injecting an in-memory database. Adding such a seam is out of scope for this feature. The SERVING branches in those two view models follow the exact same `when (unitType) { GRAM -> ...; ITEM -> ... }` shape as their GRAM and ITEM branches, which are exercised by the existing app on every save. SERVING-branch correctness is covered by:
1. The `ServingConversion` unit tests (Task 7) — the only non-trivial math.
2. The manual verification walkthrough (Task 10) — end-to-end coverage of save → search → diary subtitle.
3. The `SavedMealEditViewModel` tests extended below — the one ViewModel that does take its repo as a constructor parameter, exercising the SERVING branch in `addPickedFood` and `applyQuantityChange`.

- [ ] **Step 1: Extend `SavedMealEditViewModelTest`**

Open `tracker/src/androidTest/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModelTest.kt`. Add at the bottom of the class, just before the closing brace:

```kotlin
    @Test
    fun addPickedFood_serving_writesServingItem() = runTest {
        val food = Food(
            id = 0, name = "ServBar", unitType = FoodUnitType.SERVING,
            caloriesPer100g = null, proteinPer100g = null, fatPer100g = null, carbsPer100g = null,
            caloriesPerItem = null, proteinPerItem = null, fatPerItem = null, carbsPerItem = null,
            caloriesPerServing = 250f, proteinPerServing = 10f, fatPerServing = 12f, carbsPerServing = 30f,
            gramsPerServing = 50f, itemsPerServing = null
        )
        val foodId = db.foodDao().insert(food)
        val mealId = repo.saveFromDiaryEntries(
            "M", MealType.BREAKFAST, listOf(gramEntry("seed", 100, foodId = null)), 1L
        )
        val vm = SavedMealEditViewModel(app, mealId, repo) { id -> db.foodDao().getById(id) }
        vm.items.firstOrNull { it.isNotEmpty() }
        vm.addPickedFood(food.copy(id = foodId), 2f)
        val updated = vm.items.firstOrNull { it.any { item -> item.label == "ServBar" } }!!
        val serving = updated.single { it.label == "ServBar" }
        assertEquals(FoodUnitType.SERVING, serving.unitType)
        assertEquals(2f, serving.servings!!, 0.001f)
        assertEquals(500, serving.calories)
        // grams/count are null for SERVING
        assertNull(serving.grams)
        assertNull(serving.count)
    }

    @Test
    fun updateQuantity_serving_recomputesFromCatalog() = runTest {
        val food = Food(
            id = 0, name = "ServBar", unitType = FoodUnitType.SERVING,
            caloriesPer100g = null, proteinPer100g = null, fatPer100g = null, carbsPer100g = null,
            caloriesPerItem = null, proteinPerItem = null, fatPerItem = null, carbsPerItem = null,
            caloriesPerServing = 250f, proteinPerServing = 10f, fatPerServing = 12f, carbsPerServing = 30f,
            gramsPerServing = 50f, itemsPerServing = null
        )
        val foodId = db.foodDao().insert(food)
        // Build a SavedMealItem directly via the repository's pathway.
        val seedEntry = DiaryEntry(
            date = "2026-05-19", mealType = MealType.BREAKFAST,
            label = "ServBar", sourceType = SourceType.DATABASE, foodId = foodId,
            unitType = FoodUnitType.SERVING, grams = null, count = null, servings = 1f,
            calories = 250, protein = 10f, fat = 12f, carbs = 30f
        )
        val mealId = repo.saveFromDiaryEntries("M", MealType.BREAKFAST, listOf(seedEntry), 1L)
        val vm = SavedMealEditViewModel(app, mealId, repo) { id -> db.foodDao().getById(id) }
        val initial = vm.items.firstOrNull { it.isNotEmpty() }!!
        val itemId = initial.single().id
        vm.updateQuantity(itemId, 3f)
        val updated = vm.items.firstOrNull { list -> list.singleOrNull()?.calories == 750 }!!
        val resulting = updated.single()
        assertEquals(3f, resulting.servings!!, 0.001f)
        assertEquals(750, resulting.calories)
    }
```

Add the corresponding imports at the top if missing: `import org.junit.Assert.assertNull`.

- [ ] **Step 2: Run the full SavedMeal test**

```bash
./gradlew :tracker:connectedDebugAndroidTest --tests "com.graydyn.tracker.ui.savedmeal.SavedMealEditViewModelTest"
```

Expected: all tests PASS, including the two new SERVING cases.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/androidTest/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModelTest.kt
git commit -m "test(tracker): SERVING branch coverage in SavedMealEditViewModel"
```

---

### Task 7: Extract and unit-test per-serving conversion helpers

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/ui/components/ServingConversion.kt`
- Create: `tracker/src/test/java/com/graydyn/tracker/ui/components/ServingConversionTest.kt`

The dialog conversion math (per-serving → per-100g, per-serving → per-item) is the most error-prone part of the feature. Extract it as pure functions so it can be unit-tested with plain JUnit.

- [ ] **Step 1: Write the failing unit test**

Create `tracker/src/test/java/com/graydyn/tracker/ui/components/ServingConversionTest.kt`:

```kotlin
package com.graydyn.tracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServingConversionTest {

    @Test
    fun perServingToPer100g_null_returnsNull() {
        assertNull(perServingToPer100g(null, 50f))
    }

    @Test
    fun perServingToPer100g_zero_returnsZero() {
        assertEquals(0f, perServingToPer100g(0f, 50f)!!, 0.001f)
    }

    @Test
    fun perServingToPer100g_divides() {
        // 200 kcal / 50 g serving => 400 kcal / 100 g
        assertEquals(400f, perServingToPer100g(200f, 50f)!!, 0.001f)
    }

    @Test
    fun perServingToPerItem_null_returnsNull() {
        assertNull(perServingToPerItem(null, 2f))
    }

    @Test
    fun perServingToPerItem_zero_returnsZero() {
        assertEquals(0f, perServingToPerItem(0f, 2f)!!, 0.001f)
    }

    @Test
    fun perServingToPerItem_divides() {
        // 200 kcal / 2 items per serving => 100 kcal / item
        assertEquals(100f, perServingToPerItem(200f, 2f)!!, 0.001f)
    }
}
```

- [ ] **Step 2: Run the test and verify it fails to compile**

```bash
./gradlew :tracker:testDebugUnitTest --tests "com.graydyn.tracker.ui.components.ServingConversionTest"
```

Expected: compilation error — `perServingToPer100g` and `perServingToPerItem` are not defined.

- [ ] **Step 3: Implement the helpers**

Create `tracker/src/main/java/com/graydyn/tracker/ui/components/ServingConversion.kt`:

```kotlin
package com.graydyn.tracker.ui.components

/**
 * Convert a per-serving macro to its per-100g equivalent.
 * Returns null when input is null. Returns 0f when input is 0f.
 * Caller is responsible for ensuring gramsPerServing > 0 (the dialog
 * gates the conversion behind a non-null, non-blank gramsPerServing).
 */
fun perServingToPer100g(perServing: Float?, gramsPerServing: Float): Float? =
    perServing?.let { it / gramsPerServing * 100f }

/**
 * Convert a per-serving macro to its per-item equivalent.
 * Returns null when input is null. Returns 0f when input is 0f.
 * Caller is responsible for ensuring itemsPerServing > 0.
 */
fun perServingToPerItem(perServing: Float?, itemsPerServing: Float): Float? =
    perServing?.let { it / itemsPerServing }
```

- [ ] **Step 4: Run the test and verify it passes**

```bash
./gradlew :tracker:testDebugUnitTest --tests "com.graydyn.tracker.ui.components.ServingConversionTest"
```

Expected: 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/components/ServingConversion.kt tracker/src/test/java/com/graydyn/tracker/ui/components/ServingConversionTest.kt
git commit -m "feat(tracker): add ServingConversion helpers with unit tests"
```

---

### Task 8: `ScannedFoodDialog` — three-way radio with SERVING mode

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt`

This task replaces the whole dialog body with the tri-modal version: three radio buttons, dynamic macro labels, per-serving definition fields, switch logic with validation dialog and conversion-on-switch, and the new `onSave` shape (which Task 5 already widened to accept `gramsPerServing` / `itemsPerServing`).

- [ ] **Step 1: Replace the dialog implementation**

Open `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt`. Replace the entire file with:

```kotlin
package com.graydyn.tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.graydyn.nutritionlib.model.Macros
import com.graydyn.tracker.data.model.FoodUnitType

/**
 * Post-scan dialog. Lets the user name the scanned label, choose its unit type
 * (by weight, by item, or by serving), confirm/edit macros, optionally define
 * the serving (in grams and/or items) so the dialog can convert between modes,
 * and pick the quantity to log immediately.
 *
 * Scanned macros are seeded as per-serving values (default mode is SERVING)
 * because nutrition labels are written per serving.
 */
@Composable
fun ScannedFoodDialog(
    macros: Macros,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        unitType: FoodUnitType,
        calories: Float,
        protein: Float?,
        fat: Float?,
        carbs: Float?,
        gramsPerServing: Float?,
        itemsPerServing: Float?,
        quantity: Float
    ) -> Unit
) {
    var unitType by remember { mutableStateOf(FoodUnitType.SERVING) }
    var name by remember { mutableStateOf("") }

    fun seed(value: Int): String = if (value == -1) "" else value.toString()
    var calories by remember { mutableStateOf(seed(macros.calories)) }
    var protein by remember { mutableStateOf(seed(macros.protein)) }
    var fat by remember { mutableStateOf(seed(macros.fat)) }
    var carbs by remember { mutableStateOf(seed(macros.carbs)) }
    var gramsPerServing by remember { mutableStateOf("") }
    var itemsPerServing by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var missingFieldMessage by remember { mutableStateOf<String?>(null) }

    val nameFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocusRequester.requestFocus() }

    val trimmedName = name.trim()
    val nameBlank = trimmedName.isEmpty()
    val parsedCalories: Float? = calories.trim().toFloatOrNull()
    val caloriesBlank = calories.isBlank()
    val caloriesNonNumeric = !caloriesBlank && parsedCalories == null
    val caloriesNegative = parsedCalories != null && parsedCalories < 0f
    val parsedQuantity: Float? = quantity.trim().toFloatOrNull()?.takeIf { it > 0f }
    val quantityInvalid = parsedQuantity == null

    val canSave = !nameBlank && parsedCalories != null && parsedCalories >= 0f && !quantityInvalid

    val caloriesLabel = when (unitType) {
        FoodUnitType.GRAM -> "Calories per 100 g"
        FoodUnitType.ITEM -> "Calories per item"
        FoodUnitType.SERVING -> "Calories per serving"
    }
    val proteinLabel = when (unitType) {
        FoodUnitType.GRAM -> "Protein per 100 g (optional)"
        FoodUnitType.ITEM -> "Protein per item (optional)"
        FoodUnitType.SERVING -> "Protein per serving (optional)"
    }
    val fatLabel = when (unitType) {
        FoodUnitType.GRAM -> "Fat per 100 g (optional)"
        FoodUnitType.ITEM -> "Fat per item (optional)"
        FoodUnitType.SERVING -> "Fat per serving (optional)"
    }
    val carbsLabel = when (unitType) {
        FoodUnitType.GRAM -> "Carbs per 100 g (optional)"
        FoodUnitType.ITEM -> "Carbs per item (optional)"
        FoodUnitType.SERVING -> "Carbs per serving (optional)"
    }
    val quantityLabel = when (unitType) {
        FoodUnitType.GRAM -> "Grams to log now"
        FoodUnitType.ITEM -> "Count to log now"
        FoodUnitType.SERVING -> "Servings to log now"
    }

    fun selectUnit(next: FoodUnitType) {
        if (next == unitType) return
        when {
            unitType == FoodUnitType.SERVING && next == FoodUnitType.GRAM -> {
                val gPerServing = gramsPerServing.trim().toFloatOrNull()?.takeIf { it > 0f }
                if (gPerServing == null) {
                    missingFieldMessage =
                        "To switch to 'by weight', enter the weight per serving so we can convert the values."
                    return
                }
                calories = perServingToPer100g(calories.trim().toFloatOrNull(), gPerServing)
                    ?.let { fmt(it) } ?: ""
                protein = perServingToPer100g(protein.trim().toFloatOrNull(), gPerServing)
                    ?.let { fmt(it) } ?: ""
                fat = perServingToPer100g(fat.trim().toFloatOrNull(), gPerServing)
                    ?.let { fmt(it) } ?: ""
                carbs = perServingToPer100g(carbs.trim().toFloatOrNull(), gPerServing)
                    ?.let { fmt(it) } ?: ""
                quantity = "100"
            }
            unitType == FoodUnitType.SERVING && next == FoodUnitType.ITEM -> {
                val iPerServing = itemsPerServing.trim().toFloatOrNull()?.takeIf { it > 0f }
                if (iPerServing == null) {
                    missingFieldMessage =
                        "To switch to 'by item', enter the items per serving so we can convert the values."
                    return
                }
                calories = perServingToPerItem(calories.trim().toFloatOrNull(), iPerServing)
                    ?.let { fmt(it) } ?: ""
                protein = perServingToPerItem(protein.trim().toFloatOrNull(), iPerServing)
                    ?.let { fmt(it) } ?: ""
                fat = perServingToPerItem(fat.trim().toFloatOrNull(), iPerServing)
                    ?.let { fmt(it) } ?: ""
                carbs = perServingToPerItem(carbs.trim().toFloatOrNull(), iPerServing)
                    ?.let { fmt(it) } ?: ""
                quantity = "1"
            }
            else -> {
                // GRAM <-> ITEM or anything -> SERVING: clear macros (no conversion possible).
                calories = ""
                protein = ""
                fat = ""
                carbs = ""
                quantity = if (next == FoodUnitType.GRAM) "100" else "1"
            }
        }
        unitType = next
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save scanned food") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnitRadio("By weight", unitType == FoodUnitType.GRAM) { selectUnit(FoodUnitType.GRAM) }
                    UnitRadio("By item", unitType == FoodUnitType.ITEM) { selectUnit(FoodUnitType.ITEM) }
                    UnitRadio("By serving", unitType == FoodUnitType.SERVING) { selectUnit(FoodUnitType.SERVING) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameBlank,
                    supportingText = if (nameBlank) { { Text("Required") } } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text(caloriesLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = caloriesBlank || caloriesNonNumeric || caloriesNegative,
                    supportingText = when {
                        caloriesBlank -> { { Text("Required") } }
                        caloriesNonNumeric -> { { Text("Must be a number") } }
                        caloriesNegative -> { { Text("Must be 0 or greater") } }
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = protein,
                    onValueChange = { protein = it },
                    label = { Text(proteinLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = { Text(fatLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = { Text(carbsLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (unitType == FoodUnitType.SERVING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gramsPerServing,
                        onValueChange = { gramsPerServing = it },
                        label = { Text("Weight per serving (g, optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = itemsPerServing,
                        onValueChange = { itemsPerServing = it },
                        label = { Text("Items per serving (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(quantityLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = quantityInvalid,
                    supportingText = if (quantityInvalid) { { Text("Must be greater than 0") } } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        trimmedName,
                        unitType,
                        parsedCalories!!,
                        protein.trim().toFloatOrNull(),
                        fat.trim().toFloatOrNull(),
                        carbs.trim().toFloatOrNull(),
                        if (unitType == FoodUnitType.SERVING) gramsPerServing.trim().toFloatOrNull() else null,
                        if (unitType == FoodUnitType.SERVING) itemsPerServing.trim().toFloatOrNull() else null,
                        parsedQuantity!!
                    )
                },
                enabled = canSave
            ) { Text("Save & log") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    missingFieldMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { missingFieldMessage = null },
            title = { Text("Missing serving information") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { missingFieldMessage = null }) { Text("OK") }
            }
        )
    }
}

@Composable
internal fun RowScope.UnitRadio(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .clickable { onSelect() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}

internal fun fmt(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')
```

- [ ] **Step 2: Build**

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt
git commit -m "feat(tracker): three-way unit toggle in ScannedFoodDialog"
```

---

### Task 9: `CreateFoodDialog` — three-way radio with SERVING mode

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/components/CreateFoodDialog.kt`

Same change as Task 8 but without the quantity field. The default mode here remains `GRAM` (matches the existing default for manual creation).

- [ ] **Step 1: Replace the dialog implementation**

Open `tracker/src/main/java/com/graydyn/tracker/ui/components/CreateFoodDialog.kt`. Replace the entire file with:

```kotlin
package com.graydyn.tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.graydyn.tracker.data.model.FoodUnitType

@Composable
fun CreateFoodDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        unitType: FoodUnitType,
        calories: Float,
        protein: Float?,
        fat: Float?,
        carbs: Float?,
        gramsPerServing: Float?,
        itemsPerServing: Float?
    ) -> Unit
) {
    var unitType by remember { mutableStateOf(FoodUnitType.GRAM) }
    var name by remember { mutableStateOf(initialName.trim()) }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var gramsPerServing by remember { mutableStateOf("") }
    var itemsPerServing by remember { mutableStateOf("") }
    var missingFieldMessage by remember { mutableStateOf<String?>(null) }

    val nameFocusRequester = remember { FocusRequester() }
    val nameInitiallyBlank = remember { initialName.isBlank() }
    LaunchedEffect(Unit) {
        if (nameInitiallyBlank) nameFocusRequester.requestFocus()
    }

    val trimmedName = name.trim()
    val nameBlank = trimmedName.isEmpty()

    val parsedCalories: Float? = calories.trim().toFloatOrNull()
    val caloriesBlank = calories.isBlank()
    val caloriesNonNumeric = !caloriesBlank && parsedCalories == null
    val caloriesNegative = parsedCalories != null && parsedCalories < 0f

    val canSave = !nameBlank && parsedCalories != null && parsedCalories >= 0f

    val caloriesLabel = when (unitType) {
        FoodUnitType.GRAM -> "Calories per 100 g"
        FoodUnitType.ITEM -> "Calories per item"
        FoodUnitType.SERVING -> "Calories per serving"
    }
    val proteinLabel = when (unitType) {
        FoodUnitType.GRAM -> "Protein per 100 g (optional)"
        FoodUnitType.ITEM -> "Protein per item (optional)"
        FoodUnitType.SERVING -> "Protein per serving (optional)"
    }
    val fatLabel = when (unitType) {
        FoodUnitType.GRAM -> "Fat per 100 g (optional)"
        FoodUnitType.ITEM -> "Fat per item (optional)"
        FoodUnitType.SERVING -> "Fat per serving (optional)"
    }
    val carbsLabel = when (unitType) {
        FoodUnitType.GRAM -> "Carbs per 100 g (optional)"
        FoodUnitType.ITEM -> "Carbs per item (optional)"
        FoodUnitType.SERVING -> "Carbs per serving (optional)"
    }

    fun selectUnit(next: FoodUnitType) {
        if (next == unitType) return
        when {
            unitType == FoodUnitType.SERVING && next == FoodUnitType.GRAM -> {
                val gPerServing = gramsPerServing.trim().toFloatOrNull()?.takeIf { it > 0f }
                if (gPerServing == null) {
                    missingFieldMessage =
                        "To switch to 'by weight', enter the weight per serving so we can convert the values."
                    return
                }
                calories = perServingToPer100g(calories.trim().toFloatOrNull(), gPerServing)
                    ?.let { fmt(it) } ?: ""
                protein = perServingToPer100g(protein.trim().toFloatOrNull(), gPerServing)
                    ?.let { fmt(it) } ?: ""
                fat = perServingToPer100g(fat.trim().toFloatOrNull(), gPerServing)
                    ?.let { fmt(it) } ?: ""
                carbs = perServingToPer100g(carbs.trim().toFloatOrNull(), gPerServing)
                    ?.let { fmt(it) } ?: ""
            }
            unitType == FoodUnitType.SERVING && next == FoodUnitType.ITEM -> {
                val iPerServing = itemsPerServing.trim().toFloatOrNull()?.takeIf { it > 0f }
                if (iPerServing == null) {
                    missingFieldMessage =
                        "To switch to 'by item', enter the items per serving so we can convert the values."
                    return
                }
                calories = perServingToPerItem(calories.trim().toFloatOrNull(), iPerServing)
                    ?.let { fmt(it) } ?: ""
                protein = perServingToPerItem(protein.trim().toFloatOrNull(), iPerServing)
                    ?.let { fmt(it) } ?: ""
                fat = perServingToPerItem(fat.trim().toFloatOrNull(), iPerServing)
                    ?.let { fmt(it) } ?: ""
                carbs = perServingToPerItem(carbs.trim().toFloatOrNull(), iPerServing)
                    ?.let { fmt(it) } ?: ""
            }
            else -> {
                calories = ""
                protein = ""
                fat = ""
                carbs = ""
            }
        }
        unitType = next
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New food") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnitRadio("By weight", unitType == FoodUnitType.GRAM) { selectUnit(FoodUnitType.GRAM) }
                    UnitRadio("By item", unitType == FoodUnitType.ITEM) { selectUnit(FoodUnitType.ITEM) }
                    UnitRadio("By serving", unitType == FoodUnitType.SERVING) { selectUnit(FoodUnitType.SERVING) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameBlank,
                    supportingText = if (nameBlank) { { Text("Required") } } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text(caloriesLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = caloriesBlank || caloriesNonNumeric || caloriesNegative,
                    supportingText = when {
                        caloriesBlank -> { { Text("Required") } }
                        caloriesNonNumeric -> { { Text("Must be a number") } }
                        caloriesNegative -> { { Text("Must be 0 or greater") } }
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = protein,
                    onValueChange = { protein = it },
                    label = { Text(proteinLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = { Text(fatLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = { Text(carbsLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (unitType == FoodUnitType.SERVING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gramsPerServing,
                        onValueChange = { gramsPerServing = it },
                        label = { Text("Weight per serving (g, optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = itemsPerServing,
                        onValueChange = { itemsPerServing = it },
                        label = { Text("Items per serving (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        trimmedName,
                        unitType,
                        parsedCalories!!,
                        protein.trim().toFloatOrNull(),
                        fat.trim().toFloatOrNull(),
                        carbs.trim().toFloatOrNull(),
                        if (unitType == FoodUnitType.SERVING) gramsPerServing.trim().toFloatOrNull() else null,
                        if (unitType == FoodUnitType.SERVING) itemsPerServing.trim().toFloatOrNull() else null
                    )
                },
                enabled = canSave
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    missingFieldMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { missingFieldMessage = null },
            title = { Text("Missing serving information") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { missingFieldMessage = null }) { Text("OK") }
            }
        )
    }
}
```

Note: this file uses the `UnitRadio` and `fmt` helpers defined in `ScannedFoodDialog.kt`. They are in the same package so no import is needed.

- [ ] **Step 2: Build**

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/components/CreateFoodDialog.kt
git commit -m "feat(tracker): three-way unit toggle in CreateFoodDialog"
```

---

### Task 10: Run all automated tests and manual verification

**Files:** none modified (verification only).

- [ ] **Step 1: Run all JVM unit tests**

```bash
./gradlew :tracker:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Run all instrumented tests**

```bash
./gradlew :tracker:connectedDebugAndroidTest
```

Expected: PASS. Migration test, all three ViewModel SERVING-branch tests, and prior tests should all be green.

- [ ] **Step 3: Manual verification — scanner SERVING happy path**

Install the debug APK on a device that already has the previous version installed (so the migration runs):

```bash
./gradlew :tracker:installDebug
```

In the app:
1. Open the diary, hit Scan on any meal, scan any label (or use the dev-mode override).
2. The dialog opens with **By serving** selected by default and macro fields labeled "per serving".
3. Fill in `Weight per serving (g) = 28`, leave items blank, ensure calories is present.
4. Tap **By weight**. The macro fields should switch to "per 100 g" labels, and the calories field should now show the converted value (e.g. calories were 200 → now 714, since `200 / 28 * 100`).
5. Tap **By serving** again. Macro fields go blank.
6. Tap **By item**. A dialog "Missing serving information" appears explaining the items-per-serving requirement. Tap OK; toggle stays on **By serving**.
7. Fill in `Items per serving = 2`. Tap **By item**. Macro fields show the per-item converted values.

- [ ] **Step 4: Manual verification — SERVING save & log**

1. Restart the scanner. Choose **By serving**, fill name "Test cookies", calories 200, optionally grams-per-serving = 28, optionally items-per-serving = 2, servings to log now = 1.5. Tap **Save & log**.
2. The diary should show "Test cookies · 1.5 servings" with 300 kcal for the snack.
3. Open Search, type "Test", select the saved food. The caption should read "per serving". The quantity field label should be "Servings", default 1. Add another serving to lunch; the entry should show "1 serving".

- [ ] **Step 5: Manual verification — manual create-food**

1. In Search, type a new name that doesn't match (e.g. "ManualServingTest"). Tap the "+ New food" button.
2. The Create Food dialog appears, default mode **By weight**. Tap **By serving**. The macro labels switch to "per serving", and the two serving-definition fields appear.
3. Fill in some values, save. The new food should appear in search results with "per serving" caption.

- [ ] **Step 6: Manual verification — saved meal round-trip**

1. With a SERVING entry in the diary, "Save meal" the day. Reopen via the saved-meal picker on another day. Apply.
2. The applied entry should be a SERVING entry with the same servings quantity. Diary subtitle should show "× N servings".

- [ ] **Step 7: Final commit only if any fixups were needed**

If steps 3-6 surfaced bugs, fix them and commit. Otherwise no commit needed; the feature is done.

---

## Manual test plan summary

- Default mode for scanned food is **By serving**.
- Default mode for manual food creation is **By weight** (unchanged).
- Switching SERVING → GRAM with `gramsPerServing` empty: validation dialog, toggle stays on SERVING.
- Switching SERVING → GRAM with `gramsPerServing` filled: macros convert and prefill, mode flips to GRAM.
- Switching SERVING → ITEM mirror behavior with `itemsPerServing`.
- Switching back to SERVING clears macros (per spec).
- SERVING food saved end-to-end: appears in Search with "per serving" caption, logs with "Servings" quantity field, diary entry shows "× N servings".
- Saved meals round-trip SERVING entries correctly.
- Existing GRAM and ITEM foods (CSV-seeded and previously-user-created) are unaffected by the migration; their per-serving columns are null and the dialog defaults / display unchanged.