# Per-Item Foods Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a food declare whether it is measured by weight (per 100 g) or counted as items, with the choice baked into the food at creation time. Logging a per-item food asks for a count instead of grams; the diary shows "Apple · ×2".

**Architecture:** Add a `FoodUnitType` enum (`GRAM` / `ITEM`) as a discriminator on both `Food` and `DiaryEntry`. `Food` gains four parallel `*PerItem` columns alongside the existing `*Per100g` columns; `DiaryEntry` gains a `count` column alongside the existing `grams`. A real Room `Migration(1, 2)` adds the columns and defaults all existing rows to `GRAM`. The Create Food dialog grows a radio toggle whose selection flips the macro field labels; `SearchViewModel` branches `createFood` and `logEntry` on the chosen / selected unit type; `FoodResultCard` and `DiaryEntryRow` flip their cosmetic strings on the food/entry's `unitType`.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room (schema migration v1 → v2), Kotlin coroutines.

**Spec:** `docs/superpowers/specs/2026-05-14-per-item-foods-design.md`

**Working module:** `tracker/`

---

## File Structure

**Create**
- `tracker/src/main/java/com/graydyn/tracker/data/model/FoodUnitType.kt` — the `GRAM` / `ITEM` enum.

**Modify**
- `tracker/src/main/java/com/graydyn/tracker/data/db/Converters.kt` — add `FoodUnitType` ↔ `String` converter pair.
- `tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt` — add `unitType` + four `*PerItem` columns; add convenience extension props for unit-aware macro reads.
- `tracker/src/main/java/com/graydyn/tracker/data/model/DiaryEntry.kt` — add `unitType` + `count` columns.
- `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt` — bump version 1 → 2 and add `MIGRATION_1_2`.
- `tracker/src/main/java/com/graydyn/tracker/data/seed/CsvSeeder.kt` — pass `unitType = GRAM` and `null` for the four `*PerItem` parameters when constructing seeded foods.
- `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt` — rename `_grams` → `_quantity` (and accessors); change `createFood` signature to take `unitType`; branch the `Food`/`DiaryEntry` construction on unit type.
- `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt` — flip the quantity field label ("Grams" / "Items"), flip the "per 100g" caption, use the new convenience extension props for the macro chips, add the unit-type radio group to `CreateFoodDialog` and update its `onSave` signature.
- `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt` — branch the `DiaryEntryRow` suffix on `entry.unitType`; add `formatCount` helper.

**Note on testing**

The `:tracker` module has no test infrastructure (no instrumentation tests, no Robolectric, no Compose UI tests). Per the spec, automated tests are out of scope. Verification for each task is "the project builds; affected screens render and behave per the manual test plan." The build command is the same in every task:

```bash
./gradlew :tracker:assembleDebug
```

After Task 8, install the app and walk the manual test plan at the bottom of this document. **Migration verification matters here** — if you have a previous install of the app, do NOT uninstall it before running this plan; the migration is part of what you are testing.

---

### Task 1: Add `FoodUnitType` enum and Converter

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/data/model/FoodUnitType.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/db/Converters.kt`

- [ ] **Step 1: Create the `FoodUnitType` enum**

Create the new file with this exact content:

```kotlin
package com.graydyn.tracker.data.model

enum class FoodUnitType { GRAM, ITEM }
```

- [ ] **Step 2: Add the converter pair**

Open `tracker/src/main/java/com/graydyn/tracker/data/db/Converters.kt`. The current file is:

```kotlin
package com.graydyn.tracker.data.db

import androidx.room.TypeConverter
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SourceType

class Converters {
    @TypeConverter
    fun mealTypeToString(value: MealType): String = value.name

    @TypeConverter
    fun stringToMealType(value: String): MealType = MealType.valueOf(value)

    @TypeConverter
    fun sourceTypeToString(value: SourceType): String = value.name

    @TypeConverter
    fun stringToSourceType(value: String): SourceType = SourceType.valueOf(value)
}
```

Replace it with this exact content (adds the `FoodUnitType` import and two converter functions, matching the existing `xxxToString` / `stringToXxx` pattern):

```kotlin
package com.graydyn.tracker.data.db

import androidx.room.TypeConverter
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SourceType

class Converters {
    @TypeConverter
    fun mealTypeToString(value: MealType): String = value.name

    @TypeConverter
    fun stringToMealType(value: String): MealType = MealType.valueOf(value)

    @TypeConverter
    fun sourceTypeToString(value: SourceType): String = value.name

    @TypeConverter
    fun stringToSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun foodUnitTypeToString(value: FoodUnitType): String = value.name

    @TypeConverter
    fun stringToFoodUnitType(value: String): FoodUnitType = FoodUnitType.valueOf(value)
}
```

- [ ] **Step 3: Build**

Run:

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. The enum and converter are pure additions; no callers are affected yet.

- [ ] **Step 4: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/model/FoodUnitType.kt tracker/src/main/java/com/graydyn/tracker/data/db/Converters.kt
git commit -m "feat(tracker): add FoodUnitType enum and Room converter"
```

---

### Task 2: Add per-item columns and convenience extensions to `Food`; fix all callers

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/seed/CsvSeeder.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`

This task changes the `Food` constructor signature and ripples through every site that builds a `Food`. To keep the build green, all call sites are updated in this same task.

- [ ] **Step 1: Update the `Food` entity**

Open `tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt`. Replace the entire file with:

```kotlin
package com.graydyn.tracker.data.model

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
    val carbsPerItem: Float?
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

- [ ] **Step 2: Update `CsvSeeder` to construct GRAM foods**

Open `tracker/src/main/java/com/graydyn/tracker/data/seed/CsvSeeder.kt`. Find the `Food(...)` constructor at the bottom of `parseLine` (currently around line 54). It looks like this:

```kotlin
        return Food(
            name = name.trim(),
            caloriesPer100g = parseNullableFloat(parts[0]),
            proteinPer100g = parseNullableFloat(parts[1]),
            fatPer100g = parseNullableFloat(parts[2]),
            carbsPer100g = parseNullableFloat(parts[3])
        )
```

Replace it with (adds the `unitType` and four null `*PerItem` parameters; also need to import `FoodUnitType`):

```kotlin
        return Food(
            name = name.trim(),
            unitType = FoodUnitType.GRAM,
            caloriesPer100g = parseNullableFloat(parts[0]),
            proteinPer100g = parseNullableFloat(parts[1]),
            fatPer100g = parseNullableFloat(parts[2]),
            carbsPer100g = parseNullableFloat(parts[3]),
            caloriesPerItem = null,
            proteinPerItem = null,
            fatPerItem = null,
            carbsPerItem = null
        )
```

Then add the import at the top of the file. Find the existing imports:

```kotlin
import com.graydyn.tracker.data.db.FoodDao
import com.graydyn.tracker.data.model.Food
```

Replace with:

```kotlin
import com.graydyn.tracker.data.db.FoodDao
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.FoodUnitType
```

- [ ] **Step 3: Update the existing `createFood` in `SearchViewModel` to pass GRAM and null per-item values**

This is a temporary patch to keep the build green. Task 5 rewrites this method to branch on unit type. For now we just keep it constructing GRAM foods.

Open `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`. Find the `Food(...)` construction inside `createFood` (currently around line 100):

```kotlin
            val food = Food(
                name = name.trim(),
                caloriesPer100g = calories,
                proteinPer100g = protein,
                fatPer100g = fat,
                carbsPer100g = carbs
            )
```

Replace with:

```kotlin
            val food = Food(
                name = name.trim(),
                unitType = FoodUnitType.GRAM,
                caloriesPer100g = calories,
                proteinPer100g = protein,
                fatPer100g = fat,
                carbsPer100g = carbs,
                caloriesPerItem = null,
                proteinPerItem = null,
                fatPerItem = null,
                carbsPerItem = null
            )
```

Then add `FoodUnitType` to the imports near the top of the file. Find:

```kotlin
import com.graydyn.tracker.data.model.Food
```

Add immediately below:

```kotlin
import com.graydyn.tracker.data.model.FoodUnitType
```

- [ ] **Step 4: Build**

Run:

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. (If the build fails because Room complains about the schema, that is expected at runtime, not compile time. We handle the migration in Task 4.)

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt tracker/src/main/java/com/graydyn/tracker/data/seed/CsvSeeder.kt tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt
git commit -m "feat(tracker): extend Food with per-item macros and unitType"
```

---

### Task 3: Add `unitType` and `count` to `DiaryEntry`; fix `logEntry`

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/model/DiaryEntry.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt` (if it constructs DiaryEntry — verify in Step 0)
- Modify: any other file that constructs a `DiaryEntry` (verify in Step 0)

- [ ] **Step 0: Find every `DiaryEntry(` construction site**

Run:

```bash
grep -rn "DiaryEntry(" tracker/src/main/java
```

Expected: at least one hit in `SearchViewModel.kt` (the `logEntry` method) and one in `DiaryViewModel.kt` (the OCR scan path — `logScannedEntry`). Update **every** construction site in this task; otherwise the build will fail.

- [ ] **Step 1: Update the `DiaryEntry` entity**

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
    val calories: Int?,
    val protein: Float?,
    val fat: Float?,
    val carbs: Float?
)
```

- [ ] **Step 2: Patch `logEntry` in `SearchViewModel` to compile**

Task 5 will rewrite this method to branch on unit type. For now just add the new fields with the GRAM defaults to keep the build green.

Open `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`. Find the `DiaryEntry(...)` construction inside `logEntry` (currently around line 121):

```kotlin
        val entry = DiaryEntry(
            date = date,
            mealType = mealType,
            label = food.name,
            sourceType = SourceType.DATABASE,
            foodId = food.id,
            grams = grams,
            calories = food.caloriesPer100g?.let { (it * grams / 100f).toInt() },
            protein = food.proteinPer100g?.let { it * grams / 100f },
            fat = food.fatPer100g?.let { it * grams / 100f },
            carbs = food.carbsPer100g?.let { it * grams / 100f }
        )
```

Replace with:

```kotlin
        val entry = DiaryEntry(
            date = date,
            mealType = mealType,
            label = food.name,
            sourceType = SourceType.DATABASE,
            foodId = food.id,
            unitType = FoodUnitType.GRAM,
            grams = grams,
            count = null,
            calories = food.caloriesPer100g?.let { (it * grams / 100f).toInt() },
            protein = food.proteinPer100g?.let { it * grams / 100f },
            fat = food.fatPer100g?.let { it * grams / 100f },
            carbs = food.carbsPer100g?.let { it * grams / 100f }
        )
```

- [ ] **Step 3: Patch every other `DiaryEntry(` site found in Step 0**

For each site grep found (besides the one in Step 2), add the two new parameters:
- `unitType = FoodUnitType.GRAM`
- `count = null`

Match the placement used in Step 2: `unitType` after `foodId`, and `count` immediately after `grams`. If the file does not already import `FoodUnitType`, add:

```kotlin
import com.graydyn.tracker.data.model.FoodUnitType
```

next to the existing `import com.graydyn.tracker.data.model.DiaryEntry` line.

The OCR scan path in `DiaryViewModel.logScannedEntry` is the most likely site here. Scanned entries are weight-based (per the spec), so `unitType = FoodUnitType.GRAM` and `count = null` is the correct semantic, not just a compile fix.

- [ ] **Step 4: Build**

Run:

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If you see an "unresolved reference" or "no value passed for parameter" error, you missed a `DiaryEntry(` site in Step 3 — re-run the grep and update it.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/model/DiaryEntry.kt tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt
git commit -m "feat(tracker): extend DiaryEntry with unitType and count"
```

(Add any other files you patched in Step 3 to the `git add` list.)

---

### Task 4: Add `Migration(1, 2)` and bump database version

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt`

- [ ] **Step 1: Add the migration and wire it in**

Open `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt`. The current file is:

```kotlin
package com.graydyn.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.Goals

@Database(
    entities = [Food::class, DiaryEntry::class, Goals::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun diaryEntryDao(): DiaryEntryDao
    abstract fun goalsDao(): GoalsDao

    companion object {
        @Volatile private var INSTANCE: TrackerDatabase? = null

        fun getInstance(context: Context): TrackerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackerDatabase::class.java,
                    "tracker.db"
                ).build().also { INSTANCE = it }
            }
    }
}
```

Replace the entire file with:

```kotlin
package com.graydyn.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.Goals

@Database(
    entities = [Food::class, DiaryEntry::class, Goals::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun diaryEntryDao(): DiaryEntryDao
    abstract fun goalsDao(): GoalsDao

    companion object {
        @Volatile private var INSTANCE: TrackerDatabase? = null

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

        fun getInstance(context: Context): TrackerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackerDatabase::class.java,
                    "tracker.db"
                ).addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
```

- [ ] **Step 2: Build**

Run:

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt
git commit -m "feat(tracker): add Room migration v1->v2 for per-item columns"
```

---

### Task 5: Branch `SearchViewModel` per unit type and rename `_grams` → `_quantity`

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt`

This task changes both the public ViewModel API (`grams` → `quantity`, `onGramsChange` → `onQuantityChange`, `createFood` signature) and the call sites in `SearchScreen` that consume those names. Both files must be updated together to keep the build green. Cosmetic UI flips on the screen (label text, caption text) come in Task 7; this task only handles the rename plumbing and the data branching.

- [ ] **Step 1: Update `SearchViewModel`**

Open `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`. There are several edits to this file. The cleanest path is to replace the whole file. Replace with:

```kotlin
package com.graydyn.tracker.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.graydyn.tracker.TrackerApplication
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SourceType
import com.graydyn.tracker.data.repository.DiaryRepository
import com.graydyn.tracker.data.repository.FoodRepository
import com.graydyn.tracker.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    application: Application,
    userPreferencesRepository: UserPreferencesRepository
) : AndroidViewModel(application) {

    private val db = TrackerDatabase.getInstance(application)
    private val foodRepo = FoodRepository(db.foodDao())
    private val diaryRepo = DiaryRepository(db.diaryEntryDao())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val searchResults: StateFlow<List<Food>> =
        _query
            .debounce(300)
            .mapLatest { q ->
                if (q.isBlank()) emptyList()
                else foodRepo.search(q)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedFood = MutableStateFlow<Food?>(null)
    val selectedFood: StateFlow<Food?> = _selectedFood.asStateFlow()

    private val _quantity = MutableStateFlow("")
    val quantity: StateFlow<String> = _quantity.asStateFlow()

    val proteinOnly: StateFlow<Boolean> =
        userPreferencesRepository.proteinAndCaloriesOnly
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    fun onQueryChange(q: String) { _query.value = q }

    fun onSelectFood(food: Food) {
        _selectedFood.value = food
        _quantity.value = ""
    }

    fun onQuantityChange(q: String) { _quantity.value = q }

    fun clearSelection() { _selectedFood.value = null }

    fun openCreateDialog() { _showCreateDialog.value = true }

    fun dismissCreateDialog() { _showCreateDialog.value = false }

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
                    caloriesPerItem = null,
                    proteinPerItem = null,
                    fatPerItem = null,
                    carbsPerItem = null
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

    /** Returns true on success; false if input is invalid. */
    fun logEntry(date: String, mealType: MealType): Boolean {
        val food = _selectedFood.value ?: return false
        val qty = _quantity.value.toFloatOrNull()?.takeIf { it > 0f } ?: return false

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
        }
        viewModelScope.launch(Dispatchers.IO) { diaryRepo.insert(entry) }
        return true
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                val trackerApp = app as TrackerApplication
                return SearchViewModel(app, trackerApp.userPreferencesRepository) as T
            }
        }
    }
}
```

- [ ] **Step 2: Update `SearchScreen` rename plumbing only**

Open `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt`. There are exactly four references to the old names. Make these changes; do NOT change any visible label strings or caption strings yet — those are Task 7.

**(a)** Near the top of `SearchScreen`, find:

```kotlin
    val grams by viewModel.grams.collectAsState()
```

Replace with:

```kotlin
    val quantity by viewModel.quantity.collectAsState()
```

**(b)** A few lines down, find the `FoodResultCard` invocation:

```kotlin
                        FoodResultCard(
                            food = food,
                            isSelected = food.id == selectedFood?.id,
                            grams = if (food.id == selectedFood?.id) grams else "",
                            proteinOnly = proteinOnly,
                            onSelect = { viewModel.onSelectFood(food) },
                            onGramsChange = { viewModel.onGramsChange(it) },
                            onAdd = {
                                if (viewModel.logEntry(date, mealType)) {
                                    navController.popBackStack()
                                }
                            }
                        )
```

Replace with:

```kotlin
                        FoodResultCard(
                            food = food,
                            isSelected = food.id == selectedFood?.id,
                            quantity = if (food.id == selectedFood?.id) quantity else "",
                            proteinOnly = proteinOnly,
                            onSelect = { viewModel.onSelectFood(food) },
                            onQuantityChange = { viewModel.onQuantityChange(it) },
                            onAdd = {
                                if (viewModel.logEntry(date, mealType)) {
                                    navController.popBackStack()
                                }
                            }
                        )
```

**(c)** In the `FoodResultCard` composable definition signature, find:

```kotlin
@Composable
private fun FoodResultCard(
    food: Food,
    isSelected: Boolean,
    grams: String,
    proteinOnly: Boolean,
    onSelect: () -> Unit,
    onGramsChange: (String) -> Unit,
    onAdd: () -> Unit
) {
```

Replace with:

```kotlin
@Composable
private fun FoodResultCard(
    food: Food,
    isSelected: Boolean,
    quantity: String,
    proteinOnly: Boolean,
    onSelect: () -> Unit,
    onQuantityChange: (String) -> Unit,
    onAdd: () -> Unit
) {
```

**(d)** Inside `FoodResultCard`, in the `AnimatedVisibility` block, find the `OutlinedTextField`:

```kotlin
                    OutlinedTextField(
                        value = grams,
                        onValueChange = onGramsChange,
                        label = { Text("Grams") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
```

Replace `value = grams,` with `value = quantity,` and `onValueChange = onGramsChange,` with `onValueChange = onQuantityChange,`. Leave `label = { Text("Grams") }` alone for now — Task 7 makes that conditional.

The block becomes:

```kotlin
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = onQuantityChange,
                        label = { Text("Grams") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
```

**(e)** Also in `SearchScreen`, find the `CreateFoodDialog` invocation near the bottom:

```kotlin
        if (showCreateDialog) {
            CreateFoodDialog(
                initialName = query,
                onDismiss = { viewModel.dismissCreateDialog() },
                onSave = { name, calories, protein, fat, carbs ->
                    viewModel.createFood(name, calories, protein, fat, carbs)
                }
            )
        }
```

Replace with (the dialog itself stays unchanged for now — Task 6 adds the unit-type radio; this is a minimal patch so the new `createFood` signature compiles, hardcoding GRAM for the moment):

```kotlin
        if (showCreateDialog) {
            CreateFoodDialog(
                initialName = query,
                onDismiss = { viewModel.dismissCreateDialog() },
                onSave = { name, calories, protein, fat, carbs ->
                    viewModel.createFood(name, FoodUnitType.GRAM, calories, protein, fat, carbs)
                }
            )
        }
```

Add the import to the top of the file. Find:

```kotlin
import com.graydyn.tracker.data.model.Food
```

Add immediately below:

```kotlin
import com.graydyn.tracker.data.model.FoodUnitType
```

- [ ] **Step 3: Build**

Run:

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt
git commit -m "feat(tracker): branch SearchViewModel per food unit type"
```

---

### Task 6: Add unit-type radio group to `CreateFoodDialog`

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt`

- [ ] **Step 1: Update the `CreateFoodDialog` composable**

Open `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt`. Find the existing `CreateFoodDialog` composable (currently around line 354 — the one whose signature is `private fun CreateFoodDialog(initialName: String, onDismiss: () -> Unit, onSave: (...) -> Unit)`).

Replace the entire `CreateFoodDialog` composable with:

```kotlin
@Composable
private fun CreateFoodDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        unitType: FoodUnitType,
        calories: Float,
        protein: Float?,
        fat: Float?,
        carbs: Float?
    ) -> Unit
) {
    var unitType by remember { mutableStateOf(FoodUnitType.GRAM) }
    var name by remember { mutableStateOf(initialName.trim()) }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }

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
    }
    val proteinLabel = when (unitType) {
        FoodUnitType.GRAM -> "Protein per 100 g (optional)"
        FoodUnitType.ITEM -> "Protein per item (optional)"
    }
    val fatLabel = when (unitType) {
        FoodUnitType.GRAM -> "Fat per 100 g (optional)"
        FoodUnitType.ITEM -> "Fat per item (optional)"
    }
    val carbsLabel = when (unitType) {
        FoodUnitType.GRAM -> "Carbs per 100 g (optional)"
        FoodUnitType.ITEM -> "Carbs per item (optional)"
    }

    fun selectUnit(next: FoodUnitType) {
        if (next == unitType) return
        unitType = next
        // Clear macro fields so leftover per-100g values aren't silently saved as per-item.
        calories = ""
        protein = ""
        fat = ""
        carbs = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New food") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectUnit(FoodUnitType.GRAM) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = unitType == FoodUnitType.GRAM,
                            onClick = { selectUnit(FoodUnitType.GRAM) }
                        )
                        Text("Measured by weight")
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectUnit(FoodUnitType.ITEM) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = unitType == FoodUnitType.ITEM,
                            onClick = { selectUnit(FoodUnitType.ITEM) }
                        )
                        Text("Counted as items")
                    }
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
                        carbs.trim().toFloatOrNull()
                    )
                },
                enabled = canSave
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 2: Add the missing import for `RadioButton`**

`RadioButton` is in `androidx.compose.material3`. Find the `androidx.compose.material3` import block near the top of `SearchScreen.kt`. Add:

```kotlin
import androidx.compose.material3.RadioButton
```

(Alphabetical ordering matches the rest of the import block.)

- [ ] **Step 3: Update the `CreateFoodDialog` call site to pass the unit through**

Earlier in `SearchScreen` (the part you patched in Task 5 step 2(e)), find:

```kotlin
        if (showCreateDialog) {
            CreateFoodDialog(
                initialName = query,
                onDismiss = { viewModel.dismissCreateDialog() },
                onSave = { name, calories, protein, fat, carbs ->
                    viewModel.createFood(name, FoodUnitType.GRAM, calories, protein, fat, carbs)
                }
            )
        }
```

Replace with:

```kotlin
        if (showCreateDialog) {
            CreateFoodDialog(
                initialName = query,
                onDismiss = { viewModel.dismissCreateDialog() },
                onSave = { name, unitType, calories, protein, fat, carbs ->
                    viewModel.createFood(name, unitType, calories, protein, fat, carbs)
                }
            )
        }
```

- [ ] **Step 4: Build**

Run:

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt
git commit -m "feat(tracker): unit-type radio group in Create Food dialog"
```

---

### Task 7: `FoodResultCard` cosmetic flips

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt`

- [ ] **Step 1: Use unit-aware macro extensions for the chips**

Open `SearchScreen.kt` and find the `FoodResultCard` composable. Inside it, find the `Row` with the macro chips:

```kotlin
            Row(verticalAlignment = Alignment.CenterVertically) {
                MacroChip(
                    label = food.caloriesPer100g?.let { "${it.toInt()} kcal" } ?: "-- kcal",
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                MacroChip(
                    label = "P ${food.proteinPer100g?.let { "${it}g" } ?: "--"}",
                    color = MaterialTheme.colorScheme.secondary
                )
                if (!proteinOnly) {
                    Spacer(modifier = Modifier.width(6.dp))
                    MacroChip(
                        label = "F ${food.fatPer100g?.let { "${it}g" } ?: "--"}",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MacroChip(
                        label = "C ${food.carbsPer100g?.let { "${it}g" } ?: "--"}",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
```

Replace with (uses the convenience extension props on `Food` defined in Task 2 — `food.calories`, `food.protein`, `food.fat`, `food.carbs` — which return the per-100g value for GRAM foods and the per-item value for ITEM foods):

```kotlin
            Row(verticalAlignment = Alignment.CenterVertically) {
                MacroChip(
                    label = food.calories?.let { "${it.toInt()} kcal" } ?: "-- kcal",
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                MacroChip(
                    label = "P ${food.protein?.let { "${it}g" } ?: "--"}",
                    color = MaterialTheme.colorScheme.secondary
                )
                if (!proteinOnly) {
                    Spacer(modifier = Modifier.width(6.dp))
                    MacroChip(
                        label = "F ${food.fat?.let { "${it}g" } ?: "--"}",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MacroChip(
                        label = "C ${food.carbs?.let { "${it}g" } ?: "--"}",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
```

- [ ] **Step 2: Add the imports for the convenience extensions**

The extension props live in `com.graydyn.tracker.data.model`. Find the existing `import com.graydyn.tracker.data.model.Food` line and add four imports below it (one per extension):

```kotlin
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.calories
import com.graydyn.tracker.data.model.carbs
import com.graydyn.tracker.data.model.fat
import com.graydyn.tracker.data.model.protein
```

(Alphabetical order on the extension imports.)

- [ ] **Step 3: Flip the "per 100g" caption based on `food.unitType`**

Just below that `Row`, find:

```kotlin
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "per 100g",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
```

Replace with:

```kotlin
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (food.unitType) {
                    FoodUnitType.GRAM -> "per 100g"
                    FoodUnitType.ITEM -> "per item"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
```

- [ ] **Step 4: Flip the quantity input label between "Grams" and "Items"**

Inside the `AnimatedVisibility` block in `FoodResultCard`, find the `OutlinedTextField` (the one you renamed in Task 5 step 2(d)). It currently has `label = { Text("Grams") }`. Replace that line:

```kotlin
                        label = { Text("Grams") },
```

with:

```kotlin
                        label = {
                            Text(
                                when (food.unitType) {
                                    FoodUnitType.GRAM -> "Grams"
                                    FoodUnitType.ITEM -> "Items"
                                }
                            )
                        },
```

- [ ] **Step 5: Build**

Run:

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt
git commit -m "feat(tracker): per-unit labels and macro chips in FoodResultCard"
```

---

### Task 8: `DiaryEntryRow` per-unit display

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt`

- [ ] **Step 1: Add the `formatCount` helper near the top of the file**

Open `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt`. Find the existing top-level `mealStyle` helper (around line 80). Immediately above it (after the `MealStyle` data class declaration), add:

```kotlin
private fun formatCount(c: Float): String =
    if (c == c.toInt().toFloat()) c.toInt().toString() else "%g".format(c)
```

- [ ] **Step 2: Branch the diary row suffix on `entry.unitType`**

Find the `DiaryEntryRow` composable. Inside it, find the first `Text` (the one that builds the label + grams suffix, currently around line 458):

```kotlin
            Text(
                text = buildString {
                    append(entry.label)
                    if (entry.sourceType == SourceType.DATABASE && entry.grams != null) {
                        append("  ·  ${entry.grams.toInt()}g")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
```

Replace with:

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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
```

- [ ] **Step 3: Add the `FoodUnitType` import**

Near the top of `DiaryScreen.kt`, find:

```kotlin
import com.graydyn.tracker.data.model.DiaryEntry
```

Add immediately below:

```kotlin
import com.graydyn.tracker.data.model.FoodUnitType
```

- [ ] **Step 4: Build**

Run:

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt
git commit -m "feat(tracker): per-unit display for diary entry rows"
```

---

## Manual verification

After Task 8, install the app (do **not** uninstall the previous version first — the migration is part of what you are testing) and walk through the cases below.

```bash
./gradlew :tracker:installDebug
```

### Migration

1. **Pre-existing install upgrades cleanly.** If you had a previous install with logged entries, open the app. The diary screen should load without crashing. Any previously logged entries should still appear with their original `Ng` suffix. If you see a Room "no migration found" exception in `adb logcat`, the migration is misconfigured.

2. **Fresh install.** If you don't have a previous install, `installDebug` produces a clean v2 database directly. The seeded foods should appear in search just like before.

### Per-gram path (unchanged behavior)

3. **Search and log a per-gram food.** Open Add Food on any meal. Search for an existing seeded food (e.g., "Chicken"). Tap the result. The card shows "per 100g" caption and a "Grams" input label. Type "100" and tap Add. The diary row appears as `<Food name>  ·  100g` with macros computed from per-100g values.

4. **Create a per-gram food.** On Add Food, tap "Create new food". The dialog opens with "Measured by weight" radio selected by default. The macro field labels read "Calories per 100 g", "Protein per 100 g (optional)", etc. Fill Name + Calories (e.g., "Test Gram Food", "150"), tap Save. The dialog closes; after debounce the new food appears auto-selected with "per 100g" caption and "Grams" input label. Log 50g. The diary row should compute calories as 75 (150 × 50 / 100) and read `Test Gram Food  ·  50g`.

### Per-item path (new behavior)

5. **Toggle clears macro fields.** Open Create new food. Type "100" into Calories. Tap "Counted as items". The Calories field should clear (to prevent "100 per 100g" being silently re-saved as "100 per item"). The label changes to "Calories per item". Tap "Measured by weight" again — Calories clears again, label reverts.

6. **Create a per-item food.** Open Create new food. Select "Counted as items". Fill Name "Test Apple", Calories "95", Protein "0.5", Fat "" (leave blank), Carbs "25". Tap Save. The dialog closes; after debounce "Test Apple" appears auto-selected. The card shows "per item" caption and an "Items" input label.

7. **Log a single item.** With Test Apple selected, type "1" into Items. Tap Add. The diary row should read `Test Apple  ·  ×1`, calories 95, P 1g (rounded display from 0.5), F --, C 25g.

8. **Log a fractional item.** Open Add Food again, search "Test Apple", select it, type "0.5" into Items, tap Add. The diary row should read `Test Apple  ·  ×0.5`, calories 47 (95 × 0.5 = 47.5, truncated by `toInt`), P 0g, F --, C 13g.

9. **Log multiple items.** Search "Test Apple", select, type "2" into Items, tap Add. The diary row should read `Test Apple  ·  ×2`, calories 190, P 1g, F --, C 50g.

10. **Daily totals.** The Today card on the diary screen should show the sum of all calories from all logged entries (per-gram + per-item alike). Verify by adding up the per-row calorie values you just logged.

### Validation parity

11. **Per-item Save validation.** Open Create new food, select "Counted as items", leave Name blank — Save disabled, Name caption "Required". Fill Name, leave Calories blank — Save disabled, Calories caption "Required". Type "abc" into Calories — caption "Must be a number". Type "-3" — caption "Must be 0 or greater". Type "50" — caption disappears, Save enabled.

12. **Per-item optional macros tolerate garbage.** Open Create new food, select Counted as items, fill Name + Calories, type "abc" into Protein. Save remains enabled (no caption). Tap Save. The new food is saved with `proteinPerItem = null`. (Confirm via Android Studio's Database Inspector if you want to see the row.)

### Scan flow regression check

13. **OCR scan still works.** On the diary screen, tap "Scan" on any meal. Capture a nutrition label. The resulting entry should appear in the diary with no unit suffix (its `sourceType` is `SCANNED`, so the per-unit suffix block doesn't run). Macros should be present.

If any case fails, do NOT mark the plan complete — file the deviation against the spec and adjust before merging.

---

## Self-review notes (for the plan author, not the executor)

- **Spec coverage:**
  - `FoodUnitType` enum + Converter — Task 1.
  - `Food` per-item columns + extension props — Task 2.
  - `DiaryEntry` `unitType` + `count` — Task 3.
  - `Migration(1, 2)` + version bump — Task 4.
  - `CsvSeeder` keeps producing GRAM foods — Task 2 step 2.
  - Scanned entries write GRAM — Task 3 step 3 (covers `DiaryViewModel.logScannedEntry`).
  - SearchViewModel `createFood` branch — Task 5.
  - SearchViewModel `logEntry` branch — Task 5.
  - `_grams` → `_quantity` rename — Task 5.
  - Radio group + label flips in CreateFoodDialog — Task 6.
  - Toggle clears macro fields — Task 6 (the `selectUnit` helper).
  - FoodResultCard "per X" caption + "Grams"/"Items" label flip — Task 7.
  - FoodResultCard chips use unit-aware reads — Task 7.
  - DiaryEntryRow `×N` suffix + `formatCount` — Task 8.
  - Daily totals unchanged — verified by absence of any change in `DiaryViewModel.dailyTotals`.

- **Type consistency:** `FoodUnitType` lives in `com.graydyn.tracker.data.model.FoodUnitType` throughout (Task 1 creates it; Tasks 2, 3, 5, 6, 7, 8 import it from there). `createFood(name, unitType, calories, protein, fat, carbs)` signature in Task 5 matches the call in Task 6 step 3 and the dialog's `onSave` shape in Task 6 step 1. `_quantity` / `quantity` / `onQuantityChange` names are consistent across Task 5 and the references kept in Task 7 (which only flips labels, not names).

- **Cross-task ordering:** Task 2 must come before Task 7 (extensions used in chips). Task 3 must come before Task 4 (migration assumes the new entity shape). Task 4 must come before Task 5 (the VM writes through Room and the migration must exist before the runtime hits it). Task 5 must come before Task 6 (`createFood` signature change is visible to the dialog's `onSave`). Task 7 and Task 8 are independent and could be swapped.

- **Build green between tasks:** Each task ends with `./gradlew :tracker:assembleDebug` succeeding. Task 2 + Task 3 use minimal patches in their downstream callers (passing GRAM and nulls) so the build stays green even though the per-unit branching doesn't land until Task 5.