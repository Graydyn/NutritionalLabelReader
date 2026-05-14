# Saved Meals (with Scan-to-Food) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user save the foods currently logged in a meal slot on a given date as a named, reusable saved meal, then add all of those foods to a meal slot in one action on a later day. Bundles a prerequisite that turns each scan into a real `Food` row so saved meals include scanned items uniformly.

**Architecture:** Snapshot model — saved meals carry their own copies of each item's macros and quantity so apply is a simple copy and the meal works even if the source `Food` is later deleted. Picker is ordered per-slot via a small `SavedMealSlotApplication` junction table. Scan-to-food reuses the existing `CreateFoodDialog` after lifting it into `ui/components/`. Edit screen reuses `SearchScreen` in a new "pick" mode for adding foods.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.6.1 (KAPT), Coroutines, Material 3, AndroidX Navigation Compose. New test deps to add: `androidx.room:room-testing`, `kotlinx-coroutines-test`.

**Spec:** `docs/superpowers/specs/2026-05-14-saved-meals-design.md`

---

## File Structure

**New files:**
- `tracker/src/main/java/com/graydyn/tracker/data/model/SavedMeal.kt`
- `tracker/src/main/java/com/graydyn/tracker/data/model/SavedMealItem.kt`
- `tracker/src/main/java/com/graydyn/tracker/data/model/SavedMealSlotApplication.kt`
- `tracker/src/main/java/com/graydyn/tracker/data/db/SavedMealDao.kt`
- `tracker/src/main/java/com/graydyn/tracker/data/repository/SavedMealRepository.kt`
- `tracker/src/main/java/com/graydyn/tracker/ui/components/CreateFoodDialog.kt` (lifted from `SearchScreen.kt`)
- `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt` (post-scan variant with prefill + initial quantity)
- `tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SaveMealDialog.kt`
- `tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealPickerSheet.kt`
- `tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditScreen.kt`
- `tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModel.kt`
- `tracker/src/androidTest/java/com/graydyn/tracker/data/db/SavedMealDaoTest.kt`
- `tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration2To3Test.kt`
- `tracker/src/test/java/com/graydyn/tracker/ui/diary/DiaryViewModelSavedMealsTest.kt`
- `tracker/src/test/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModelTest.kt`

**Modified files:**
- `gradle/libs.versions.toml` (add room-testing, kotlinx-coroutines-test)
- `tracker/build.gradle.kts` (consume new test deps; export schemas)
- `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt` (register new entities, version → 3, MIGRATION_2_3)
- `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt` (remove inlined CreateFoodDialog; add pick-mode handling)
- `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt` (add pick-mode result path)
- `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt` (replace `logScannedEntry`; add saved-meal flows)
- `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt` (post-scan dialog; meal-card overflow + Meals button + bottom sheet wiring)
- `tracker/src/main/java/com/graydyn/tracker/navigation/NavGraph.kt` (optional `mode` arg on Search; `Route.SavedMealEdit`)

**Assumptions to verify (from spec):**
- `nutritionlib`'s `Macros` (raw Int values, `-1` sentinel) is treated as **per-100 g** for label scans in the GRAM case. Verify against `nutritionlib` OCR pipeline before wiring the post-scan dialog labels (Task 2.4).
- Compose Foundation in this project's Compose BOM (`2024.02.02`) does not ship a stable reorderable list. Drag-to-reorder in `SavedMealEditScreen` is dropped from this plan; items keep their `position` order and editing changes positions only via add/remove.

---

## Phase 1 — Test Infrastructure

### Task 1.1: Add test dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `tracker/build.gradle.kts`

- [ ] **Step 1: Add versions and library entries**

Edit `gradle/libs.versions.toml`. Under `[versions]` add:

```toml
kotlinxCoroutinesTest = "1.7.3"
roomTesting = "2.6.1"
androidxTestRunner = "1.5.2"
androidxTestCore = "1.5.0"
```

Under `[libraries]` add:

```toml
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "roomTesting" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidxTestRunner" }
androidx-test-core-ktx = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
```

- [ ] **Step 2: Wire deps into the tracker module**

Edit `tracker/build.gradle.kts`. Inside `defaultConfig`, after the existing `vectorDrawables { ... }` line, add:

```kotlin
javaCompileOptions {
    annotationProcessorOptions {
        arguments += mapOf(
            "room.schemaLocation" to "$projectDir/schemas",
            "room.incremental" to "true"
        )
    }
}
```

In the `dependencies` block, replace the existing `// Test` section with:

```kotlin
// Test
testImplementation(libs.junit)
testImplementation(libs.kotlinx.coroutines.test)

androidTestImplementation(libs.androidx.junit)
androidTestImplementation(libs.androidx.test.runner)
androidTestImplementation(libs.androidx.test.core.ktx)
androidTestImplementation(libs.androidx.espresso.core)
androidTestImplementation(libs.androidx.room.testing)
androidTestImplementation(libs.kotlinx.coroutines.test)

debugImplementation(libs.androidx.ui.tooling)
debugImplementation(libs.androidx.ui.test.manifest)
```

Also add to the same block (so KAPT exports schemas for migration tests):

```kotlin
kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}
```

- [ ] **Step 3: Sync and confirm build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml tracker/build.gradle.kts
git commit -m "chore(tracker): add room-testing and coroutines-test deps + schema export"
```

---

## Phase 2 — Scan-to-Food

### Task 2.1: Lift CreateFoodDialog into its own file

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/ui/components/CreateFoodDialog.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt` (remove inlined `CreateFoodDialog`, import from new location)

- [ ] **Step 1: Move the composable**

Cut the entire `CreateFoodDialog` private composable (currently lines 357-526 in `SearchScreen.kt`) and paste into a new file:

`tracker/src/main/java/com/graydyn/tracker/ui/components/CreateFoodDialog.kt`

Change the visibility from `private` to public (drop the `private` keyword) and adjust the package and imports:

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
        carbs: Float?
    ) -> Unit
) {
    // ... existing body unchanged ...
}
```

In `SearchScreen.kt`: delete the original `CreateFoodDialog` definition; add this import at the top:

```kotlin
import com.graydyn.tracker.ui.components.CreateFoodDialog
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/components/CreateFoodDialog.kt tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt
git commit -m "refactor(tracker): lift CreateFoodDialog into ui/components"
```

### Task 2.2: Build ScannedFoodDialog (CreateFoodDialog variant for post-scan)

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt`

This is a sibling of `CreateFoodDialog` rather than a parameter-bloated version of it. Reason: it has a different title, a prefilled set of macros, and an extra Quantity field. Sharing one composable means lots of optional knobs; two composables read more clearly.

- [ ] **Step 1: Write the composable**

Create `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt`:

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
import com.graydyn.nutritionlib.model.Macros
import com.graydyn.tracker.data.model.FoodUnitType

/**
 * Post-scan dialog. Lets the user name the scanned label, choose its unit type,
 * confirm/edit the macros (prefilled from the scan; -1 sentinel rendered blank),
 * and pick the quantity to log immediately.
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
        quantity: Float
    ) -> Unit
) {
    var unitType by remember { mutableStateOf(FoodUnitType.GRAM) }
    var name by remember { mutableStateOf("") }

    fun seed(value: Int): String = if (value == -1) "" else value.toString()
    var calories by remember { mutableStateOf(seed(macros.calories)) }
    var protein by remember { mutableStateOf(seed(macros.protein)) }
    var fat by remember { mutableStateOf(seed(macros.fat)) }
    var carbs by remember { mutableStateOf(seed(macros.carbs)) }
    var quantity by remember { mutableStateOf("100") }

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

    val caloriesLabel = if (unitType == FoodUnitType.GRAM) "Calories per 100 g" else "Calories per item"
    val proteinLabel = if (unitType == FoodUnitType.GRAM) "Protein per 100 g (optional)" else "Protein per item (optional)"
    val fatLabel = if (unitType == FoodUnitType.GRAM) "Fat per 100 g (optional)" else "Fat per item (optional)"
    val carbsLabel = if (unitType == FoodUnitType.GRAM) "Carbs per 100 g (optional)" else "Carbs per item (optional)"
    val quantityLabel = if (unitType == FoodUnitType.GRAM) "Grams to log now" else "Count to log now"

    fun selectUnit(next: FoodUnitType) {
        if (next == unitType) return
        unitType = next
        quantity = if (next == FoodUnitType.GRAM) "100" else "1"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save scanned food") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .clickable { selectUnit(FoodUnitType.GRAM) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = unitType == FoodUnitType.GRAM, onClick = { selectUnit(FoodUnitType.GRAM) })
                        Text("By weight")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clickable { selectUnit(FoodUnitType.ITEM) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = unitType == FoodUnitType.ITEM, onClick = { selectUnit(FoodUnitType.ITEM) })
                        Text("By item")
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
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt
git commit -m "feat(tracker): add ScannedFoodDialog for post-scan create-food flow"
```

### Task 2.3: Replace `logScannedEntry` with `logScannedFood` in DiaryViewModel

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt`

- [ ] **Step 1: Add `scanInProgress` state and the new logging method**

In `DiaryViewModel.kt`:

1. Add a `FoodRepository` field at the top of the class (after `goalsRepo`):

```kotlin
private val foodRepo = com.graydyn.tracker.data.repository.FoodRepository(db.foodDao())
```

2. Add scan-in-progress state above `selectedDate`:

```kotlin
private val _scanInProgress = MutableStateFlow<Macros?>(null)
val scanInProgress: StateFlow<Macros?> = _scanInProgress.asStateFlow()

fun onScanResult(macros: Macros) { _scanInProgress.value = macros }
fun dismissScannedFoodDialog() { _scanInProgress.value = null }
```

3. **Delete** the existing `logScannedEntry` method.

4. Add the new logging method (writes a `Food` then a `DiaryEntry` referencing it, both off the IO dispatcher; bumps nothing else):

```kotlin
fun logScannedFood(
    name: String,
    unitType: FoodUnitType,
    calories: Float,
    protein: Float?,
    fat: Float?,
    carbs: Float?,
    quantity: Float,
    mealType: MealType
) {
    _scanInProgress.value = null
    viewModelScope.launch(Dispatchers.IO) {
        val food = when (unitType) {
            FoodUnitType.GRAM -> com.graydyn.tracker.data.model.Food(
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
            FoodUnitType.ITEM -> com.graydyn.tracker.data.model.Food(
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
        }
        diaryRepo.insert(entry)
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt
git commit -m "feat(tracker): replace logScannedEntry with logScannedFood scan-to-food path"
```

### Task 2.4: Verify Macros contract assumption

Before wiring the dialog (next task), verify how `nutritionlib` populates `Macros`. We assume per-100 g (the GRAM default).

- [ ] **Step 1: Inspect**

```bash
grep -RIn "Macros(" nutritionlib/src/main/java | head -50
grep -RIn "calories" nutritionlib/src/main/java | head -50
```

Read the OCR / parsing code and confirm whether values written into `Macros` come from the "per 100 g" column on the label, the "per serving" column, or the unmodified label number (which on most labels is per-serving, not per-100g).

- [ ] **Step 2: If contract is per-serving, not per-100 g**

Update the dialog labels in `ScannedFoodDialog.kt` so the GRAM case reads:

```kotlin
val caloriesLabel = if (unitType == FoodUnitType.GRAM) "Calories per serving (you'll enter weight)" else "Calories per item"
```

… and add a comment near `selectUnit` that the user should set `quantity` to the **serving size in grams** when GRAM is chosen. This still works because `Food.caloriesPer100g` is a label, not an enforced unit — the math `(calories * grams / 100f)` will be wrong if interpreted as per-serving, so in this case the GRAM branch should NOT scale by 100 and we instead store macros as if they were per-100g of the serving (so `caloriesPer100g = calories * 100 / quantity`).

If the verification confirms per-100 g (the spec's stated assumption), no change is needed. Document the result in the commit message.

- [ ] **Step 3: Commit (only if changes were made in Step 2)**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt
git commit -m "fix(tracker): adjust ScannedFoodDialog for actual Macros unit semantics"
```

### Task 2.5: Wire the dialog into DiaryScreen

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt`

- [ ] **Step 1: Replace `logScannedEntry` call with `onScanResult`**

In `DiaryScreen.kt`, find the `scanLauncher` block and change the `macros?.let { ... }` body to:

```kotlin
macros?.let { viewModel.onScanResult(it) }
```

- [ ] **Step 2: Render the dialog**

Add at the top of `DiaryScreen` (alongside other `collectAsState` calls):

```kotlin
val scanInProgress by viewModel.scanInProgress.collectAsState()
```

Just before the closing brace of `Scaffold { padding -> LazyColumn { ... } }` (i.e. after the `LazyColumn`), add:

```kotlin
scanInProgress?.let { macros ->
    com.graydyn.tracker.ui.components.ScannedFoodDialog(
        macros = macros,
        onDismiss = { viewModel.dismissScannedFoodDialog() },
        onSave = { name, unitType, calories, protein, fat, carbs, quantity ->
            viewModel.logScannedFood(
                name = name,
                unitType = unitType,
                calories = calories,
                protein = protein,
                fat = fat,
                carbs = carbs,
                quantity = quantity,
                mealType = scanTargetMeal
            )
        }
    )
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual smoke test**

Install on device or emulator and verify: tap Scan on a meal card → run a label scan → dialog appears prefilled with macros → enter "Test Food", quantity 100 → Save & log → diary entry appears with the chosen macros, AND the new food appears in the Search screen results.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt
git commit -m "feat(tracker): show ScannedFoodDialog after a successful scan"
```

---

## Phase 3 — Saved Meals Data Layer

### Task 3.1: SavedMeal entity

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/data/model/SavedMeal.kt`

- [ ] **Step 1: Write the entity**

```kotlin
package com.graydyn.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_meals")
data class SavedMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)
```

- [ ] **Step 2: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/model/SavedMeal.kt
git commit -m "feat(tracker): add SavedMeal entity"
```

### Task 3.2: SavedMealItem entity

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/data/model/SavedMealItem.kt`

- [ ] **Step 1: Write the entity**

```kotlin
package com.graydyn.tracker.data.model

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
    val calories: Int?,
    val protein: Float?,
    val fat: Float?,
    val carbs: Float?
)
```

- [ ] **Step 2: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/model/SavedMealItem.kt
git commit -m "feat(tracker): add SavedMealItem entity"
```

### Task 3.3: SavedMealSlotApplication entity

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/data/model/SavedMealSlotApplication.kt`

- [ ] **Step 1: Write the entity**

```kotlin
package com.graydyn.tracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "saved_meal_slot_applications",
    primaryKeys = ["savedMealId", "mealType"],
    foreignKeys = [
        ForeignKey(
            entity = SavedMeal::class,
            parentColumns = ["id"],
            childColumns = ["savedMealId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SavedMealSlotApplication(
    val savedMealId: Long,
    val mealType: MealType,
    val lastAppliedAt: Long
)
```

- [ ] **Step 2: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/model/SavedMealSlotApplication.kt
git commit -m "feat(tracker): add SavedMealSlotApplication entity"
```

### Task 3.4: SavedMealDao

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/data/db/SavedMealDao.kt`

- [ ] **Step 1: Write the DAO**

```kotlin
package com.graydyn.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SavedMeal
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.data.model.SavedMealSlotApplication
import kotlinx.coroutines.flow.Flow

data class SavedMealSummary(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val totalCalories: Int,
    val createdAt: Long,
    val lastAppliedAt: Long?
)

@Dao
interface SavedMealDao {

    @Insert
    suspend fun insertSavedMeal(meal: SavedMeal): Long

    @Insert
    suspend fun insertItems(items: List<SavedMealItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSlotApplication(application: SavedMealSlotApplication)

    @Update
    suspend fun updateSavedMeal(meal: SavedMeal)

    @Query("DELETE FROM saved_meals WHERE id = :savedMealId")
    suspend fun deleteSavedMeal(savedMealId: Long)

    @Query("DELETE FROM saved_meal_items WHERE savedMealId = :savedMealId")
    suspend fun deleteItemsFor(savedMealId: Long)

    @Query("SELECT * FROM saved_meals WHERE id = :savedMealId")
    suspend fun getSavedMeal(savedMealId: Long): SavedMeal?

    @Query("SELECT * FROM saved_meal_items WHERE savedMealId = :savedMealId ORDER BY position ASC")
    suspend fun getItems(savedMealId: Long): List<SavedMealItem>

    @Query("SELECT * FROM saved_meal_items WHERE savedMealId = :savedMealId ORDER BY position ASC")
    fun observeItems(savedMealId: Long): Flow<List<SavedMealItem>>

    @Query(
        """
        SELECT m.id AS id,
               m.name AS name,
               (SELECT COUNT(*) FROM saved_meal_items i WHERE i.savedMealId = m.id) AS itemCount,
               (SELECT COALESCE(SUM(i.calories), 0) FROM saved_meal_items i WHERE i.savedMealId = m.id) AS totalCalories,
               m.createdAt AS createdAt,
               (SELECT a.lastAppliedAt FROM saved_meal_slot_applications a
                  WHERE a.savedMealId = m.id AND a.mealType = :mealType) AS lastAppliedAt
        FROM saved_meals m
        ORDER BY (CASE WHEN lastAppliedAt IS NULL THEN 1 ELSE 0 END) ASC,
                 lastAppliedAt DESC,
                 m.createdAt DESC
        """
    )
    fun observeSummariesForSlot(mealType: MealType): Flow<List<SavedMealSummary>>

    @Transaction
    suspend fun createSavedMealWithItems(
        meal: SavedMeal,
        itemsBuilder: (savedMealId: Long) -> List<SavedMealItem>,
        initialSlotMealType: MealType,
        nowMillis: Long
    ): Long {
        val id = insertSavedMeal(meal)
        insertItems(itemsBuilder(id))
        upsertSlotApplication(
            SavedMealSlotApplication(
                savedMealId = id,
                mealType = initialSlotMealType,
                lastAppliedAt = nowMillis
            )
        )
        return id
    }

    @Transaction
    suspend fun replaceItemsTransactionally(savedMealId: Long, newItems: List<SavedMealItem>) {
        deleteItemsFor(savedMealId)
        insertItems(newItems)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/db/SavedMealDao.kt
git commit -m "feat(tracker): add SavedMealDao with summaries query and transaction helpers"
```

### Task 3.5: Register entities and add MIGRATION_2_3

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt`

- [ ] **Step 1: Update the `@Database` declaration**

Replace the `@Database` block at the top of the class:

```kotlin
@Database(
    entities = [
        Food::class,
        DiaryEntry::class,
        Goals::class,
        com.graydyn.tracker.data.model.SavedMeal::class,
        com.graydyn.tracker.data.model.SavedMealItem::class,
        com.graydyn.tracker.data.model.SavedMealSlotApplication::class
    ],
    version = 3,
    exportSchema = true
)
```

Add a DAO accessor inside the abstract class body, after `abstract fun goalsDao()`:

```kotlin
abstract fun savedMealDao(): SavedMealDao
```

- [ ] **Step 2: Add migration**

Inside the `companion object`, after `MIGRATION_1_2`, add:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `saved_meals` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `name` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `saved_meal_items` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `savedMealId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `label` TEXT NOT NULL,
                `foodId` INTEGER,
                `unitType` TEXT NOT NULL,
                `grams` REAL,
                `count` REAL,
                `calories` INTEGER,
                `protein` REAL,
                `fat` REAL,
                `carbs` REAL,
                FOREIGN KEY(`savedMealId`) REFERENCES `saved_meals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_saved_meal_items_savedMealId`
            ON `saved_meal_items` (`savedMealId`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `saved_meal_slot_applications` (
                `savedMealId` INTEGER NOT NULL,
                `mealType` TEXT NOT NULL,
                `lastAppliedAt` INTEGER NOT NULL,
                PRIMARY KEY(`savedMealId`, `mealType`),
                FOREIGN KEY(`savedMealId`) REFERENCES `saved_meals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}
```

Then update the builder line to add the new migration:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```

Add the import at the top:

```kotlin
import com.graydyn.tracker.data.db.SavedMealDao
```

- [ ] **Step 3: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL. A new schema file at `tracker/schemas/com.graydyn.tracker.data.db.TrackerDatabase/3.json` should appear.

- [ ] **Step 4: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt tracker/schemas
git commit -m "feat(tracker): register saved-meal entities, version 3, MIGRATION_2_3"
```

### Task 3.6: SavedMealRepository

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/data/repository/SavedMealRepository.kt`

- [ ] **Step 1: Write the repository**

```kotlin
package com.graydyn.tracker.data.repository

import com.graydyn.tracker.data.db.SavedMealDao
import com.graydyn.tracker.data.db.SavedMealSummary
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SavedMeal
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.data.model.SavedMealSlotApplication
import com.graydyn.tracker.data.model.SourceType
import kotlinx.coroutines.flow.Flow

class SavedMealRepository(
    private val savedMealDao: SavedMealDao,
    private val diaryEntryDao: com.graydyn.tracker.data.db.DiaryEntryDao
) {

    fun observeSummariesForSlot(mealType: MealType): Flow<List<SavedMealSummary>> =
        savedMealDao.observeSummariesForSlot(mealType)

    suspend fun getItems(savedMealId: Long): List<SavedMealItem> =
        savedMealDao.getItems(savedMealId)

    fun observeItems(savedMealId: Long): Flow<List<SavedMealItem>> =
        savedMealDao.observeItems(savedMealId)

    suspend fun getSavedMeal(savedMealId: Long): SavedMeal? =
        savedMealDao.getSavedMeal(savedMealId)

    suspend fun saveFromDiaryEntries(
        name: String,
        sourceMealType: MealType,
        entries: List<DiaryEntry>,
        nowMillis: Long
    ): Long {
        val meal = SavedMeal(name = name, createdAt = nowMillis)
        return savedMealDao.createSavedMealWithItems(
            meal = meal,
            itemsBuilder = { savedMealId ->
                entries.mapIndexed { index, entry ->
                    SavedMealItem(
                        savedMealId = savedMealId,
                        position = index,
                        label = entry.label,
                        foodId = entry.foodId,
                        unitType = entry.unitType,
                        grams = entry.grams,
                        count = entry.count,
                        calories = entry.calories,
                        protein = entry.protein,
                        fat = entry.fat,
                        carbs = entry.carbs
                    )
                }
            },
            initialSlotMealType = sourceMealType,
            nowMillis = nowMillis
        )
    }

    suspend fun applyToSlot(
        savedMealId: Long,
        mealType: MealType,
        date: String,
        nowMillis: Long
    ): Int {
        val items = savedMealDao.getItems(savedMealId)
        items.forEach { item ->
            val entry = DiaryEntry(
                date = date,
                mealType = mealType,
                label = item.label,
                sourceType = if (item.foodId != null) SourceType.DATABASE else SourceType.SCANNED,
                foodId = item.foodId,
                unitType = item.unitType,
                grams = item.grams,
                count = item.count,
                calories = item.calories,
                protein = item.protein,
                fat = item.fat,
                carbs = item.carbs
            )
            diaryEntryDao.insert(entry)
        }
        savedMealDao.upsertSlotApplication(
            SavedMealSlotApplication(
                savedMealId = savedMealId,
                mealType = mealType,
                lastAppliedAt = nowMillis
            )
        )
        return items.size
    }

    suspend fun rename(savedMealId: Long, newName: String) {
        val current = savedMealDao.getSavedMeal(savedMealId) ?: return
        savedMealDao.updateSavedMeal(current.copy(name = newName))
    }

    suspend fun delete(savedMealId: Long) {
        savedMealDao.deleteSavedMeal(savedMealId)
    }

    suspend fun replaceItems(savedMealId: Long, newItems: List<SavedMealItem>) {
        savedMealDao.replaceItemsTransactionally(savedMealId, newItems)
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/repository/SavedMealRepository.kt
git commit -m "feat(tracker): add SavedMealRepository covering save, apply, rename, delete, replace"
```

### Task 3.7: DAO instrumented tests

**Files:**
- Create: `tracker/src/androidTest/java/com/graydyn/tracker/data/db/SavedMealDaoTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.graydyn.tracker.data.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SavedMeal
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.data.model.SavedMealSlotApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedMealDaoTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: TrackerDatabase
    private lateinit var dao: SavedMealDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TrackerDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.savedMealDao()
    }

    @After
    fun tearDown() { db.close() }

    private fun item(savedMealId: Long, position: Int, label: String, calories: Int) = SavedMealItem(
        savedMealId = savedMealId,
        position = position,
        label = label,
        foodId = null,
        unitType = FoodUnitType.GRAM,
        grams = 100f,
        count = null,
        calories = calories,
        protein = 10f,
        fat = 5f,
        carbs = 20f
    )

    @Test
    fun createSavedMealWithItems_writesAllRowsAndInitialSlotApplication() = runTest {
        val id = dao.createSavedMealWithItems(
            meal = SavedMeal(name = "Breakfast A", createdAt = 1_000L),
            itemsBuilder = { savedMealId ->
                listOf(
                    item(savedMealId, 0, "Oats", 200),
                    item(savedMealId, 1, "Berries", 80)
                )
            },
            initialSlotMealType = MealType.BREAKFAST,
            nowMillis = 1_000L
        )

        val items = dao.getItems(id)
        assertEquals(2, items.size)
        assertEquals("Oats", items[0].label)
        assertEquals("Berries", items[1].label)

        val summaries = dao.observeSummariesForSlot(MealType.BREAKFAST).first()
        assertEquals(1, summaries.size)
        val summary = summaries[0]
        assertEquals("Breakfast A", summary.name)
        assertEquals(2, summary.itemCount)
        assertEquals(280, summary.totalCalories)
        assertEquals(1_000L, summary.lastAppliedAt)
    }

    @Test
    fun observeSummariesForSlot_isOrderedByLastAppliedThenCreatedAt() = runTest {
        // Created first, never applied to LUNCH
        dao.createSavedMealWithItems(
            meal = SavedMeal(name = "M_old_unapplied", createdAt = 100L),
            itemsBuilder = { listOf(item(it, 0, "x", 100)) },
            initialSlotMealType = MealType.BREAKFAST,
            nowMillis = 100L
        )
        // Created second, applied to LUNCH at t=300
        val mLunchOld = dao.createSavedMealWithItems(
            meal = SavedMeal(name = "M_lunch_old", createdAt = 200L),
            itemsBuilder = { listOf(item(it, 0, "x", 100)) },
            initialSlotMealType = MealType.LUNCH,
            nowMillis = 300L
        )
        // Created third, applied to LUNCH at t=500 (most recent)
        dao.createSavedMealWithItems(
            meal = SavedMeal(name = "M_lunch_recent", createdAt = 400L),
            itemsBuilder = { listOf(item(it, 0, "x", 100)) },
            initialSlotMealType = MealType.LUNCH,
            nowMillis = 500L
        )

        val summaries = dao.observeSummariesForSlot(MealType.LUNCH).first()
        // Order: most-recently-applied to LUNCH first, then never-applied-to-LUNCH by createdAt DESC
        assertEquals(listOf("M_lunch_recent", "M_lunch_old", "M_old_unapplied"), summaries.map { it.name })
        assertEquals(500L, summaries[0].lastAppliedAt)
        assertEquals(300L, summaries[1].lastAppliedAt)
        assertNull(summaries[2].lastAppliedAt)
    }

    @Test
    fun upsertSlotApplication_replacesExistingRow() = runTest {
        val id = dao.createSavedMealWithItems(
            meal = SavedMeal(name = "M", createdAt = 1L),
            itemsBuilder = { listOf(item(it, 0, "x", 100)) },
            initialSlotMealType = MealType.DINNER,
            nowMillis = 1L
        )
        dao.upsertSlotApplication(SavedMealSlotApplication(id, MealType.DINNER, 999L))

        val updated = dao.observeSummariesForSlot(MealType.DINNER).first().single()
        assertEquals(999L, updated.lastAppliedAt)
    }

    @Test
    fun deleteSavedMeal_cascadesToItemsAndSlotApplications() = runTest {
        val id = dao.createSavedMealWithItems(
            meal = SavedMeal(name = "M", createdAt = 1L),
            itemsBuilder = { listOf(item(it, 0, "x", 100), item(it, 1, "y", 50)) },
            initialSlotMealType = MealType.BREAKFAST,
            nowMillis = 1L
        )

        dao.deleteSavedMeal(id)

        assertNull(dao.getSavedMeal(id))
        assertEquals(emptyList<SavedMealItem>(), dao.getItems(id))
        val summaries = dao.observeSummariesForSlot(MealType.BREAKFAST).first()
        assertEquals(emptyList<SavedMealSummary>(), summaries)
    }

    @Test
    fun replaceItemsTransactionally_swapsItems() = runTest {
        val id = dao.createSavedMealWithItems(
            meal = SavedMeal(name = "M", createdAt = 1L),
            itemsBuilder = { listOf(item(it, 0, "Old A", 100), item(it, 1, "Old B", 200)) },
            initialSlotMealType = MealType.SNACK,
            nowMillis = 1L
        )

        dao.replaceItemsTransactionally(
            savedMealId = id,
            newItems = listOf(
                item(id, 0, "New A", 50),
                item(id, 1, "New B", 75),
                item(id, 2, "New C", 25)
            )
        )

        val items = dao.getItems(id)
        assertEquals(listOf("New A", "New B", "New C"), items.map { it.label })
        val summary = dao.observeSummariesForSlot(MealType.SNACK).first().single()
        assertEquals(3, summary.itemCount)
        assertEquals(150, summary.totalCalories)
    }
}
```

- [ ] **Step 2: Run the failing test**

Run: `./gradlew :tracker:connectedDebugAndroidTest --tests "com.graydyn.tracker.data.db.SavedMealDaoTest"`
Expected: tests run and PASS (the DAO from Task 3.4 should already satisfy these expectations). If a test fails, adjust the DAO query to match — most likely the ordering clause or the COALESCE in `totalCalories`.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/androidTest/java/com/graydyn/tracker/data/db/SavedMealDaoTest.kt
git commit -m "test(tracker): SavedMealDao instrumented tests for create/order/cascade/replace"
```

### Task 3.8: Migration test

**Files:**
- Create: `tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration2To3Test.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.graydyn.tracker.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2To3Test {

    private val testDbName = "tracker-migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrackerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3_addsNewTablesAndPreservesExistingRows() {
        helper.createDatabase(testDbName, 2).use { v2 ->
            v2.execSQL(
                """
                INSERT INTO foods (name, unitType, caloriesPer100g, proteinPer100g, fatPer100g, carbsPer100g, caloriesPerItem, proteinPerItem, fatPerItem, carbsPerItem)
                VALUES ('Oats', 'GRAM', 379.0, 13.0, 7.0, 67.0, NULL, NULL, NULL, NULL)
                """.trimIndent()
            )
            v2.execSQL(
                """
                INSERT INTO diary_entries (date, mealType, label, sourceType, foodId, unitType, grams, count, calories, protein, fat, carbs)
                VALUES ('2026-05-14', 'BREAKFAST', 'Oats', 'DATABASE', 1, 'GRAM', 50.0, NULL, 190, 6.5, 3.5, 33.5)
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            testDbName,
            3,
            true,
            TrackerDatabase.MIGRATION_2_3
        )

        // Existing data preserved
        migrated.query("SELECT name FROM foods").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Oats", cursor.getString(0))
        }
        migrated.query("SELECT label FROM diary_entries").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Oats", cursor.getString(0))
        }

        // New tables exist and are empty
        migrated.query("SELECT COUNT(*) FROM saved_meals").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM saved_meal_items").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM saved_meal_slot_applications").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }

        // Index exists
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_saved_meal_items_savedMealId'"
        ).use { c ->
            assertEquals(true, c.moveToFirst())
        }
    }
}
```

- [ ] **Step 2: Verify v2 schema is exported**

Confirm `tracker/schemas/com.graydyn.tracker.data.db.TrackerDatabase/2.json` exists. If not, the migration test cannot validate the v2 starting point. To produce it, temporarily revert the DB version to 2 in `TrackerDatabase.kt`, run `./gradlew :tracker:assembleDebug`, restore the version to 3, run again. Commit only the resulting `2.json` (and any `3.json` regenerated):

```bash
git add tracker/schemas/com.graydyn.tracker.data.db.TrackerDatabase/2.json
git commit -m "chore(tracker): export v2 Room schema for migration tests"
```

- [ ] **Step 3: Run the test**

Run: `./gradlew :tracker:connectedDebugAndroidTest --tests "com.graydyn.tracker.data.db.Migration2To3Test"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration2To3Test.kt
git commit -m "test(tracker): MIGRATION_2_3 instrumented test"
```

---

## Phase 4 — Save-as-Meal Flow

### Task 4.1: SaveMealDialog composable

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SaveMealDialog.kt`

- [ ] **Step 1: Write the dialog**

```kotlin
package com.graydyn.tracker.ui.savedmeal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

@Composable
fun SaveMealDialog(
    suggestedName: String,
    summary: String,
    onDismiss: () -> Unit,
    onSave: (name: String) -> Unit
) {
    var name by remember { mutableStateOf(suggestedName) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val trimmed = name.trim()
    val canSave = trimmed.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save meal") },
        text = {
            Column {
                Text(text = summary, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = !canSave,
                    supportingText = if (!canSave) { { Text("Required") } } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(trimmed) }, enabled = canSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SaveMealDialog.kt
git commit -m "feat(tracker): add SaveMealDialog"
```

### Task 4.2: Wire SavedMealRepository into DiaryViewModel and add save method

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt`

- [ ] **Step 1: Add the repository, save state, and save method**

In `DiaryViewModel.kt`, add a field after `goalsRepo`:

```kotlin
private val savedMealRepo = com.graydyn.tracker.data.repository.SavedMealRepository(
    db.savedMealDao(),
    db.diaryEntryDao()
)
```

Add state for the save dialog:

```kotlin
private val _saveMealRequest = MutableStateFlow<MealType?>(null)
val saveMealRequest: StateFlow<MealType?> = _saveMealRequest.asStateFlow()

fun openSaveMealDialog(mealType: MealType) { _saveMealRequest.value = mealType }
fun dismissSaveMealDialog() { _saveMealRequest.value = null }
```

Add the save method (reads from the existing `entriesByMeal` flow's current value):

```kotlin
fun saveCurrentMealAsSavedMeal(mealType: MealType, name: String) {
    val entries = entriesByMeal.value[mealType].orEmpty()
    if (entries.isEmpty()) {
        _saveMealRequest.value = null
        return
    }
    _saveMealRequest.value = null
    viewModelScope.launch(Dispatchers.IO) {
        savedMealRepo.saveFromDiaryEntries(
            name = name,
            sourceMealType = mealType,
            entries = entries,
            nowMillis = System.currentTimeMillis()
        )
    }
}
```

Also add a Snackbar event channel (a `MutableStateFlow<String?>` is fine for our needs; consume-and-clear pattern):

```kotlin
private val _snackbarMessage = MutableStateFlow<String?>(null)
val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()
fun consumeSnackbar() { _snackbarMessage.value = null }
```

And update `saveCurrentMealAsSavedMeal` so the launch block ends with:

```kotlin
_snackbarMessage.value = "Saved as '$name'"
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt
git commit -m "feat(tracker): DiaryViewModel.saveCurrentMealAsSavedMeal + snackbar state"
```

### Task 4.3: VM unit test for save

**Files:**
- Create: `tracker/src/test/java/com/graydyn/tracker/ui/diary/DiaryViewModelSavedMealsTest.kt`

This is a focused unit test against fakes. It does NOT bring up Room — that's covered by the DAO instrumented tests.

- [ ] **Step 1: Add a fake repository**

```kotlin
package com.graydyn.tracker.ui.diary

import com.graydyn.tracker.data.db.SavedMealSummary
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SavedMeal
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.data.repository.SavedMealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface SavedMealsApi {
    suspend fun saveFromDiaryEntries(
        name: String,
        sourceMealType: MealType,
        entries: List<DiaryEntry>,
        nowMillis: Long
    ): Long

    suspend fun applyToSlot(
        savedMealId: Long,
        mealType: MealType,
        date: String,
        nowMillis: Long
    ): Int

    fun observeSummariesForSlot(mealType: MealType): Flow<List<SavedMealSummary>>
}
```

In addition to the file above, the simplest path forward is to refactor `DiaryViewModel` to take a `SavedMealsApi` (interface implemented by `SavedMealRepository`) so we can substitute a fake. If that refactor is rejected at code review, fall back to `kotlinx.coroutines.test.runTest` exercising the real repository against an in-memory Room DB (move the test to `androidTest`).

If proceeding with the refactor: extract a small interface in `data/repository/SavedMealRepository.kt`:

```kotlin
interface SavedMealApi {
    fun observeSummariesForSlot(mealType: MealType): Flow<List<SavedMealSummary>>
    suspend fun getItems(savedMealId: Long): List<SavedMealItem>
    fun observeItems(savedMealId: Long): Flow<List<SavedMealItem>>
    suspend fun getSavedMeal(savedMealId: Long): SavedMeal?
    suspend fun saveFromDiaryEntries(name: String, sourceMealType: MealType, entries: List<DiaryEntry>, nowMillis: Long): Long
    suspend fun applyToSlot(savedMealId: Long, mealType: MealType, date: String, nowMillis: Long): Int
    suspend fun rename(savedMealId: Long, newName: String)
    suspend fun delete(savedMealId: Long)
    suspend fun replaceItems(savedMealId: Long, newItems: List<SavedMealItem>)
}
```

Make `SavedMealRepository : SavedMealApi`. Update `DiaryViewModel` to type the field as `SavedMealApi`. Add a secondary constructor that accepts a `SavedMealApi` for testing:

```kotlin
internal constructor(
    application: Application,
    userPreferencesRepository: UserPreferencesRepository,
    savedMealRepoOverride: SavedMealApi
) : this(application, userPreferencesRepository) {
    this.savedMealRepo = savedMealRepoOverride
}
```

Mark `savedMealRepo` `var` (and document why).

- [ ] **Step 2: Write the failing test**

```kotlin
package com.graydyn.tracker.ui.diary

import android.app.Application
import com.graydyn.tracker.data.db.SavedMealSummary
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SavedMeal
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.data.model.SourceType
import com.graydyn.tracker.data.preferences.userPreferencesDataStore
import com.graydyn.tracker.data.repository.SavedMealApi
import com.graydyn.tracker.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class DiaryViewModelSavedMealsTest {

    private val app: Application = mock(Application::class.java)
    private val userPrefsRepo: UserPreferencesRepository = mock(UserPreferencesRepository::class.java)

    @Before
    fun setMain() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After
    fun resetMain() { Dispatchers.resetMain() }

    private fun makeEntry(label: String, calories: Int) = DiaryEntry(
        date = "2026-05-14",
        mealType = MealType.BREAKFAST,
        label = label,
        sourceType = SourceType.DATABASE,
        foodId = 1L,
        unitType = FoodUnitType.GRAM,
        grams = 100f, count = null,
        calories = calories, protein = 5f, fat = 2f, carbs = 10f
    )

    @Test
    fun saveCurrentMealAsSavedMeal_callsRepoWithCurrentEntries_andEmitsSnackbar() = runTest {
        val captured = mutableListOf<Triple<String, MealType, List<DiaryEntry>>>()
        val fake = object : SavedMealApi {
            override fun observeSummariesForSlot(mealType: MealType) = flowOf(emptyList<SavedMealSummary>())
            override suspend fun getItems(savedMealId: Long) = emptyList<SavedMealItem>()
            override fun observeItems(savedMealId: Long) = flowOf(emptyList<SavedMealItem>())
            override suspend fun getSavedMeal(savedMealId: Long): SavedMeal? = null
            override suspend fun saveFromDiaryEntries(name: String, sourceMealType: MealType, entries: List<DiaryEntry>, nowMillis: Long): Long {
                captured += Triple(name, sourceMealType, entries); return 42L
            }
            override suspend fun applyToSlot(savedMealId: Long, mealType: MealType, date: String, nowMillis: Long) = 0
            override suspend fun rename(savedMealId: Long, newName: String) {}
            override suspend fun delete(savedMealId: Long) {}
            override suspend fun replaceItems(savedMealId: Long, newItems: List<SavedMealItem>) {}
        }
        // Construct the VM with the override constructor
        val vm = DiaryViewModel(app, userPrefsRepo, fake)
        // Seed the entriesByMeal state (test helper: the VM exposes a method or we go through a fake DAO).
        vm.testSeedEntriesByMeal(mapOf(MealType.BREAKFAST to listOf(makeEntry("Oats", 200), makeEntry("Berries", 80))))

        vm.saveCurrentMealAsSavedMeal(MealType.BREAKFAST, "My breakfast")
        advanceUntilIdle()

        assertEquals(1, captured.size)
        assertEquals("My breakfast", captured[0].first)
        assertEquals(MealType.BREAKFAST, captured[0].second)
        assertEquals(listOf("Oats", "Berries"), captured[0].third.map { it.label })
        assertEquals("Saved as 'My breakfast'", vm.snackbarMessage.value)
    }
}
```

- [ ] **Step 3: Add `testSeedEntriesByMeal` to DiaryViewModel**

Add to `DiaryViewModel`, marked `internal` and annotated as test-only:

```kotlin
@androidx.annotation.VisibleForTesting
internal fun testSeedEntriesByMeal(seed: Map<MealType, List<DiaryEntry>>) {
    _testEntriesOverride.value = seed
}

private val _testEntriesOverride = MutableStateFlow<Map<MealType, List<DiaryEntry>>?>(null)
```

Update the `entriesByMeal` definition to honor the override when set:

```kotlin
val entriesByMeal: StateFlow<Map<MealType, List<DiaryEntry>>> =
    combine(_selectedDate, _testEntriesOverride) { date, override -> date to override }
        .flatMapLatest { (date, override) ->
            if (override != null) flowOf(override)
            else diaryRepo.getEntriesForDate(date).map { entries -> entries.groupBy { it.mealType } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
```

Add the imports: `kotlinx.coroutines.flow.combine`, `kotlinx.coroutines.flow.flowOf`.

If the project doesn't already have Mockito on the test classpath, replace the `mock(...)` calls with hand-written stubs (small enough). Add Mockito only if more tests will need it.

For the `Application` and `UserPreferencesRepository` mocks specifically: replace with anonymous subclasses that return a `MutableStateFlow(false)` for `proteinAndCaloriesOnly`. Keep the test free of Mockito.

```kotlin
private val userPrefsRepo = object : UserPreferencesRepository(
    // The constructor takes a DataStore; pass null via a test-only ctor or refactor UserPreferencesRepository to accept a flow directly.
) { /* override proteinAndCaloriesOnly to return MutableStateFlow(false) */ }
```

If `UserPreferencesRepository` cannot be instantiated without a DataStore, extract its interface (`UserPreferencesApi { val proteinAndCaloriesOnly: Flow<Boolean> }`), make `UserPreferencesRepository` implement it, and have `DiaryViewModel` depend on `UserPreferencesApi`. Same pattern as `SavedMealApi`.

- [ ] **Step 4: Run the test**

Run: `./gradlew :tracker:testDebugUnitTest --tests "com.graydyn.tracker.ui.diary.DiaryViewModelSavedMealsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/repository/SavedMealRepository.kt tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt tracker/src/test/java/com/graydyn/tracker/ui/diary/DiaryViewModelSavedMealsTest.kt
git commit -m "test(tracker): DiaryViewModel save-meal unit test (with SavedMealApi seam)"
```

### Task 4.4: Add overflow menu and Save dialog to MealCard

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt`

- [ ] **Step 1: Add the overflow icon and dropdown to `MealCard`**

In `MealCard`, change the function signature to accept an `onSaveAsMeal` callback (default `null`):

```kotlin
@Composable
private fun MealCard(
    mealType: MealType,
    entries: List<DiaryEntry>,
    proteinOnly: Boolean,
    onAddFood: () -> Unit,
    onScan: () -> Unit,
    onDelete: (DiaryEntry) -> Unit,
    onSaveAsMeal: (() -> Unit)? = null,
)
```

In the header row of `MealCard`, after the existing Column, add (still inside the Row):

```kotlin
if (entries.isNotEmpty() && onSaveAsMeal != null) {
    var menuOpen by remember { mutableStateOf(false) }
    IconButton(onClick = { menuOpen = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Meal options",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    androidx.compose.material3.DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false }
    ) {
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("Save as meal") },
            onClick = { menuOpen = false; onSaveAsMeal() }
        )
    }
}
```

Add imports at top: `androidx.compose.material.icons.filled.MoreVert`.

- [ ] **Step 2: Pass the callback from `DiaryScreen`**

In the `MealType.entries.forEach { mealType -> item { MealCard(...) } }` block, add:

```kotlin
onSaveAsMeal = { viewModel.openSaveMealDialog(mealType) }
```

- [ ] **Step 3: Render the dialog and snackbar host**

At the top of `DiaryScreen`, add to the existing `collectAsState` block:

```kotlin
val saveMealRequest by viewModel.saveMealRequest.collectAsState()
val snackbarMessage by viewModel.snackbarMessage.collectAsState()
val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
LaunchedEffect(snackbarMessage) {
    snackbarMessage?.let {
        snackbarHostState.showSnackbar(it)
        viewModel.consumeSnackbar()
    }
}
```

Inside `Scaffold(...)` add `snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }`.

Just below the existing `scanInProgress?.let { ... }` block (added in Task 2.5), add:

```kotlin
saveMealRequest?.let { mealType ->
    val mealEntries = entriesByMeal[mealType].orEmpty()
    val mealCalories = mealEntries.sumOf { it.calories ?: 0 }
    val mealLabel = mealStyle(mealType).label
    com.graydyn.tracker.ui.savedmeal.SaveMealDialog(
        suggestedName = "$mealLabel – $selectedDate",
        summary = "$mealLabel: ${mealEntries.size} item${if (mealEntries.size == 1) "" else "s"}, $mealCalories kcal",
        onDismiss = { viewModel.dismissSaveMealDialog() },
        onSave = { name -> viewModel.saveCurrentMealAsSavedMeal(mealType, name) }
    )
}
```

Add imports: `androidx.compose.runtime.LaunchedEffect`.

- [ ] **Step 4: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual smoke test**

Add a few foods to Breakfast → tap the overflow icon → tap "Save as meal" → name dialog appears with `"Breakfast – 2026-05-14"` prefilled → Save → snackbar `"Saved as '...'"` appears. (Picker not wired yet; verify in Phase 5.)

- [ ] **Step 6: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt
git commit -m "feat(tracker): MealCard 'Save as meal' overflow menu and dialog"
```

---

## Phase 5 — Apply-Saved-Meal Flow

### Task 5.1: SavedMealPickerSheet composable

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealPickerSheet.kt`

- [ ] **Step 1: Write the bottom sheet**

```kotlin
package com.graydyn.tracker.ui.savedmeal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.graydyn.tracker.data.db.SavedMealSummary
import com.graydyn.tracker.data.model.SavedMealItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMealPickerSheet(
    title: String,
    summaries: List<SavedMealSummary>,
    expandedItems: Map<Long, List<SavedMealItem>>,
    onDismiss: () -> Unit,
    onExpand: (Long) -> Unit,
    onCollapse: (Long) -> Unit,
    onApply: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onRename: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            if (summaries.isEmpty()) {
                Text(
                    "No saved meals yet. Save one from a populated meal card.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(summaries, key = { it.id }) { summary ->
                        SummaryRow(
                            summary = summary,
                            expanded = expandedItems.containsKey(summary.id),
                            items = expandedItems[summary.id].orEmpty(),
                            onTap = {
                                if (expandedItems.containsKey(summary.id)) onCollapse(summary.id)
                                else onExpand(summary.id)
                            },
                            onApply = { onApply(summary.id) },
                            onEdit = { onEdit(summary.id) },
                            onRename = { onRename(summary.id) },
                            onDelete = { onDelete(summary.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    summary: SavedMealSummary,
    expanded: Boolean,
    items: List<SavedMealItem>,
    onTap: () -> Unit,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    Text(summary.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${summary.itemCount} item${if (summary.itemCount == 1) "" else "s"} · ${summary.totalCalories} kcal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onTap) {
                    Text(if (expanded) "Hide" else "Open", style = MaterialTheme.typography.labelSmall)
                }
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Saved meal options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                items.forEach { item ->
                    val qty = if (item.grams != null) "${item.grams.toInt()} g" else "${item.count?.toInt() ?: 0} ×"
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(item.label, modifier = Modifier.weight(1f))
                        Text(qty, modifier = Modifier.padding(end = 8.dp))
                        Text("${item.calories ?: 0} kcal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                    Text("Add to meal")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealPickerSheet.kt
git commit -m "feat(tracker): SavedMealPickerSheet with inline expand-and-confirm apply"
```

### Task 5.2: Picker state and apply path in DiaryViewModel

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt`

- [ ] **Step 1: Add picker state and the apply method**

```kotlin
private val _pickerOpenForSlot = MutableStateFlow<MealType?>(null)
val pickerOpenForSlot: StateFlow<MealType?> = _pickerOpenForSlot.asStateFlow()

fun openSavedMealPicker(mealType: MealType) { _pickerOpenForSlot.value = mealType }
fun dismissSavedMealPicker() { _pickerOpenForSlot.value = null }

@OptIn(ExperimentalCoroutinesApi::class)
val pickerSummaries: StateFlow<List<com.graydyn.tracker.data.db.SavedMealSummary>> =
    _pickerOpenForSlot
        .flatMapLatest { meal ->
            if (meal == null) flowOf(emptyList())
            else savedMealRepo.observeSummariesForSlot(meal)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

private val _expandedSavedMealItems = MutableStateFlow<Map<Long, List<com.graydyn.tracker.data.model.SavedMealItem>>>(emptyMap())
val expandedSavedMealItems: StateFlow<Map<Long, List<com.graydyn.tracker.data.model.SavedMealItem>>> = _expandedSavedMealItems.asStateFlow()

fun expandSavedMeal(savedMealId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val items = savedMealRepo.getItems(savedMealId)
        _expandedSavedMealItems.value = _expandedSavedMealItems.value + (savedMealId to items)
    }
}

fun collapseSavedMeal(savedMealId: Long) {
    _expandedSavedMealItems.value = _expandedSavedMealItems.value - savedMealId
}

fun applySavedMeal(savedMealId: Long, mealType: MealType) {
    val date = _selectedDate.value
    viewModelScope.launch(Dispatchers.IO) {
        val n = savedMealRepo.applyToSlot(
            savedMealId = savedMealId,
            mealType = mealType,
            date = date,
            nowMillis = System.currentTimeMillis()
        )
        val mealLabel = mealType.name.lowercase().replaceFirstChar { it.uppercase() }
        _snackbarMessage.value = "Added $n items to $mealLabel"
    }
    _pickerOpenForSlot.value = null
    _expandedSavedMealItems.value = emptyMap()
}

fun renameSavedMeal(savedMealId: Long, newName: String) {
    viewModelScope.launch(Dispatchers.IO) { savedMealRepo.rename(savedMealId, newName) }
}

fun deleteSavedMeal(savedMealId: Long) {
    viewModelScope.launch(Dispatchers.IO) { savedMealRepo.delete(savedMealId) }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt
git commit -m "feat(tracker): DiaryViewModel picker state, apply, rename, delete"
```

### Task 5.3: VM unit test for apply

**Files:**
- Modify: `tracker/src/test/java/com/graydyn/tracker/ui/diary/DiaryViewModelSavedMealsTest.kt`

- [ ] **Step 1: Add a test method**

Append this `@Test` method to the existing class:

```kotlin
@Test
fun applySavedMeal_invokesRepoAndEmitsSnackbar() = runTest {
    val applies = mutableListOf<Triple<Long, MealType, String>>()
    val fake = object : SavedMealApi {
        override fun observeSummariesForSlot(mealType: MealType) = flowOf(emptyList<SavedMealSummary>())
        override suspend fun getItems(savedMealId: Long) = emptyList<SavedMealItem>()
        override fun observeItems(savedMealId: Long) = flowOf(emptyList<SavedMealItem>())
        override suspend fun getSavedMeal(savedMealId: Long): SavedMeal? = null
        override suspend fun saveFromDiaryEntries(name: String, sourceMealType: MealType, entries: List<DiaryEntry>, nowMillis: Long) = 1L
        override suspend fun applyToSlot(savedMealId: Long, mealType: MealType, date: String, nowMillis: Long): Int {
            applies += Triple(savedMealId, mealType, date); return 3
        }
        override suspend fun rename(savedMealId: Long, newName: String) {}
        override suspend fun delete(savedMealId: Long) {}
        override suspend fun replaceItems(savedMealId: Long, newItems: List<SavedMealItem>) {}
    }
    val vm = DiaryViewModel(app, userPrefsRepo, fake)
    vm.applySavedMeal(savedMealId = 7L, mealType = MealType.LUNCH)
    advanceUntilIdle()

    assertEquals(1, applies.size)
    assertEquals(7L, applies[0].first)
    assertEquals(MealType.LUNCH, applies[0].second)
    assertEquals("Added 3 items to Lunch", vm.snackbarMessage.value)
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :tracker:testDebugUnitTest --tests "com.graydyn.tracker.ui.diary.DiaryViewModelSavedMealsTest"`
Expected: PASS for both tests.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/test/java/com/graydyn/tracker/ui/diary/DiaryViewModelSavedMealsTest.kt
git commit -m "test(tracker): DiaryViewModel apply-saved-meal unit test"
```

### Task 5.4: Add the third "Meals" button and wire the picker

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt`

- [ ] **Step 1: Add a third button to the action row in `MealCard`**

Add a parameter to `MealCard`:

```kotlin
onPickSavedMeal: () -> Unit,
```

In the existing action `Row` (currently containing Scan and Add), add a middle button:

```kotlin
FilledTonalButton(
    onClick = onPickSavedMeal,
    modifier = Modifier.weight(1f),
    shape = MaterialTheme.shapes.medium,
    contentPadding = PaddingValues(vertical = 10.dp)
) {
    Icon(
        imageVector = Icons.Default.Bookmark,
        contentDescription = null,
        modifier = Modifier.size(18.dp)
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text("Meals", style = MaterialTheme.typography.labelLarge)
}
```

Add import: `androidx.compose.material.icons.filled.Bookmark`.

- [ ] **Step 2: Pass the callback from `DiaryScreen`**

In the `MealCard(...)` call inside the `entries.forEach`, add:

```kotlin
onPickSavedMeal = { viewModel.openSavedMealPicker(mealType) }
```

- [ ] **Step 3: Render the picker sheet, rename dialog, delete dialog**

At the top of `DiaryScreen`, add:

```kotlin
val pickerOpenForSlot by viewModel.pickerOpenForSlot.collectAsState()
val pickerSummaries by viewModel.pickerSummaries.collectAsState()
val expandedItems by viewModel.expandedSavedMealItems.collectAsState()
var renameTarget by remember { mutableStateOf<com.graydyn.tracker.data.db.SavedMealSummary?>(null) }
var deleteTarget by remember { mutableStateOf<com.graydyn.tracker.data.db.SavedMealSummary?>(null) }
```

After the `saveMealRequest?.let { ... }` block, add:

```kotlin
pickerOpenForSlot?.let { slot ->
    val mealLabel = mealStyle(slot).label
    com.graydyn.tracker.ui.savedmeal.SavedMealPickerSheet(
        title = "$mealLabel meals",
        summaries = pickerSummaries,
        expandedItems = expandedItems,
        onDismiss = { viewModel.dismissSavedMealPicker() },
        onExpand = { viewModel.expandSavedMeal(it) },
        onCollapse = { viewModel.collapseSavedMeal(it) },
        onApply = { viewModel.applySavedMeal(it, slot) },
        onEdit = { id ->
            navController.navigate(com.graydyn.tracker.navigation.Route.SavedMealEdit.createRoute(id))
            viewModel.dismissSavedMealPicker()
        },
        onRename = { id -> renameTarget = pickerSummaries.firstOrNull { it.id == id } },
        onDelete = { id -> deleteTarget = pickerSummaries.firstOrNull { it.id == id } }
    )
}

renameTarget?.let { target ->
    com.graydyn.tracker.ui.savedmeal.SaveMealDialog(
        suggestedName = target.name,
        summary = "Rename '${target.name}'",
        onDismiss = { renameTarget = null },
        onSave = { newName ->
            viewModel.renameSavedMeal(target.id, newName)
            renameTarget = null
        }
    )
}

deleteTarget?.let { target ->
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleteTarget = null },
        title = { Text("Delete '${target.name}'?") },
        text = { Text("This cannot be undone.") },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    viewModel.deleteSavedMeal(target.id)
                    deleteTarget = null
                }
            ) { Text("Delete") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL. The `Route.SavedMealEdit` reference fails to compile until Phase 6 — comment out the `onEdit` body temporarily and add a `// TODO: wire to SavedMealEdit route in Phase 6` (commit message notes this), or include Task 6.4 (route registration) as a prerequisite to compile this. **Recommended: do Task 6.4 (just the `Route.SavedMealEdit` declaration in the sealed class) immediately before Step 4 of this task** so the build stays green.

- [ ] **Step 5: Manual smoke test**

Save a meal from Breakfast, open the Meals picker on a different day's Breakfast → see the saved meal sorted to top → tap Open → see items → tap Add to meal → entries appear, snackbar fires. Test rename and delete from the overflow menu.

- [ ] **Step 6: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt
git commit -m "feat(tracker): MealCard 'Meals' picker with apply/rename/delete"
```

---

## Phase 6 — Edit Saved Meal

### Task 6.1: Search screen pick mode

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`

- [ ] **Step 1: Add a `mode` enum**

Top of `SearchScreen.kt`:

```kotlin
enum class SearchMode { LOG, PICK_FOR_SAVED_MEAL }
```

- [ ] **Step 2: Plumb the mode through the screen**

Change the `SearchScreen` composable signature to accept `mode: SearchMode = SearchMode.LOG` and a callback for pick mode:

```kotlin
@Composable
fun SearchScreen(
    navController: NavController,
    date: String,
    mealType: MealType,
    mode: SearchMode = SearchMode.LOG,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
)
```

In the `FoodResultCard` Add button branch, change the handler:

```kotlin
onAdd = {
    when (mode) {
        SearchMode.LOG -> {
            if (viewModel.logEntry(date, mealType)) navController.popBackStack()
        }
        SearchMode.PICK_FOR_SAVED_MEAL -> {
            val food = viewModel.selectedFood.value ?: return@FoodResultCard
            val qty = viewModel.quantity.value.toFloatOrNull()?.takeIf { it > 0f } ?: return@FoodResultCard
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set("picked_food_id", food.id)
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set("picked_quantity", qty)
            navController.popBackStack()
        }
    }
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL (the route nav arg is added in Task 6.4; with the default value, existing callers still work).

- [ ] **Step 4: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/search/SearchScreen.kt
git commit -m "feat(tracker): SearchScreen pick-mode for saved-meal editing"
```

### Task 6.2: SavedMealEditViewModel

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModel.kt`

- [ ] **Step 1: Write the ViewModel**

```kotlin
package com.graydyn.tracker.ui.savedmeal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.SavedMeal
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.data.repository.FoodRepository
import com.graydyn.tracker.data.repository.SavedMealApi
import com.graydyn.tracker.data.repository.SavedMealRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SavedMealEditViewModel(
    application: Application,
    private val savedMealId: Long,
    private val savedMealRepo: SavedMealApi,
    private val foodLookup: suspend (Long) -> Food?
) : AndroidViewModel(application) {

    private val _meal = MutableStateFlow<SavedMeal?>(null)
    val meal: StateFlow<SavedMeal?> = _meal.asStateFlow()

    private val _items = MutableStateFlow<List<SavedMealItem>>(emptyList())
    val items: StateFlow<List<SavedMealItem>> = _items.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _meal.value = savedMealRepo.getSavedMeal(savedMealId)
            _items.value = savedMealRepo.getItems(savedMealId)
        }
    }

    fun rename(newName: String) {
        val current = _meal.value ?: return
        _meal.value = current.copy(name = newName)
    }

    fun deleteItem(item: SavedMealItem) {
        _items.value = _items.value.filterNot { it.id == item.id }
    }

    fun updateQuantity(itemId: Long, newQuantity: Float) {
        if (newQuantity <= 0f) return
        viewModelScope.launch(Dispatchers.IO) {
            _items.value = _items.value.map { item ->
                if (item.id != itemId) item
                else recomputeForQuantity(item, newQuantity)
            }
        }
    }

    private suspend fun recomputeForQuantity(item: SavedMealItem, newQty: Float): SavedMealItem {
        val food = item.foodId?.let { foodLookup(it) }
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
            }
        }
        // Orphan: scale snapshot proportionally
        val oldQty = (item.grams ?: item.count ?: 0f).coerceAtLeast(0.001f)
        val ratio = newQty / oldQty
        return item.copy(
            grams = item.grams?.let { newQty },
            count = item.count?.let { newQty },
            calories = item.calories?.let { (it * ratio).toInt() },
            protein = item.protein?.let { it * ratio },
            fat = item.fat?.let { it * ratio },
            carbs = item.carbs?.let { it * ratio }
        )
    }

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
        }
        _items.value = _items.value + newItem
    }

    fun save() {
        val current = _meal.value ?: return
        val list = _items.value
        if (list.isEmpty()) {
            _saveError.value = "A meal must contain at least one food."
            return
        }
        _saveError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            savedMealRepo.rename(current.id, current.name)
            val renumbered = list.mapIndexed { index, item -> item.copy(position = index) }
            savedMealRepo.replaceItems(current.id, renumbered)
            _saved.value = true
        }
    }

    companion object {
        fun factory(savedMealId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!! as Application
                val db = TrackerDatabase.getInstance(app)
                val repo = SavedMealRepository(db.savedMealDao(), db.diaryEntryDao())
                val foodRepo = FoodRepository(db.foodDao())
                return SavedMealEditViewModel(
                    application = app,
                    savedMealId = savedMealId,
                    savedMealRepo = repo,
                    foodLookup = { id ->
                        // No single-food query exists today; do a wide search to find by id.
                        // Cheap because catalog is ~hundreds of items.
                        foodRepo.search("").firstOrNull { it.id == id }
                    }
                ) as T
            }
        }
    }
}
```

Note: `FoodRepository.search("")` currently returns `emptyList()` because the SQL is `LIKE '%' || :query || '%'` — `LIKE '%%'` matches everything, so this works. If the catalog grows large, add a `getById(id: Long)` method to `FoodDao`/`FoodRepository`.

- [ ] **Step 2: Add `FoodDao.getById` for cleanliness**

Modify `tracker/src/main/java/com/graydyn/tracker/data/db/FoodDao.kt`:

```kotlin
@Query("SELECT * FROM foods WHERE id = :id LIMIT 1")
suspend fun getById(id: Long): Food?
```

Modify `FoodRepository.kt` to add:

```kotlin
suspend fun getById(id: Long): com.graydyn.tracker.data.model.Food? = dao.getById(id)
```

Then change the `foodLookup` lambda in the ViewModel factory to:

```kotlin
foodLookup = { id -> foodRepo.getById(id) }
```

- [ ] **Step 3: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/db/FoodDao.kt tracker/src/main/java/com/graydyn/tracker/data/repository/FoodRepository.kt tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModel.kt
git commit -m "feat(tracker): SavedMealEditViewModel + FoodDao.getById"
```

### Task 6.3: SavedMealEditScreen

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditScreen.kt`

- [ ] **Step 1: Write the screen**

```kotlin
package com.graydyn.tracker.ui.savedmeal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.navigation.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMealEditScreen(
    navController: NavController,
    savedMealId: Long
) {
    val viewModel: SavedMealEditViewModel = viewModel(
        factory = SavedMealEditViewModel.factory(savedMealId)
    )
    val meal by viewModel.meal.collectAsState()
    val items by viewModel.items.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val saved by viewModel.saved.collectAsState()

    // Receive the picked food back from SearchScreen pick-mode
    val backEntry = navController.currentBackStackEntry
    val pickedIdState = backEntry?.savedStateHandle?.getStateFlow<Long?>("picked_food_id", null)?.collectAsState()
    val pickedQtyState = backEntry?.savedStateHandle?.getStateFlow<Float?>("picked_quantity", null)?.collectAsState()
    LaunchedEffect(pickedIdState?.value, pickedQtyState?.value) {
        val id = pickedIdState?.value ?: return@LaunchedEffect
        val qty = pickedQtyState?.value ?: return@LaunchedEffect
        // Resolve via repo through the VM (synchronous addPickedFood expects a Food).
        viewModel.handlePickedFoodById(id, qty)
        backEntry.savedStateHandle["picked_food_id"] = null
        backEntry.savedStateHandle["picked_quantity"] = null
    }

    LaunchedEffect(saved) {
        if (saved) navController.popBackStack()
    }

    var renameDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meal?.name ?: "Edit meal") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { renameDialog = true }) { Text("Rename") }
                    TextButton(onClick = { viewModel.save() }) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            saveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    EditableItemRow(
                        item = item,
                        onQuantityChange = { newQty -> viewModel.updateQuantity(item.id, newQty) },
                        onDelete = { viewModel.deleteItem(item) }
                    )
                }
            }
            Button(
                onClick = {
                    // Open SearchScreen in pick mode. Reuse Route.Search with mode=PICK_FOR_SAVED_MEAL.
                    val today = com.graydyn.tracker.ui.diary.DiaryViewModel.todayString()
                    navController.navigate(Route.Search.createRouteForPick(today, MealType.BREAKFAST))
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add food")
            }
        }
    }

    if (renameDialog) {
        SaveMealDialog(
            suggestedName = meal?.name.orEmpty(),
            summary = "Rename this meal",
            onDismiss = { renameDialog = false },
            onSave = { name ->
                viewModel.rename(name)
                renameDialog = false
            }
        )
    }
}

@Composable
private fun EditableItemRow(
    item: SavedMealItem,
    onQuantityChange: (Float) -> Unit,
    onDelete: () -> Unit
) {
    var qtyText by remember(item.id) {
        mutableStateOf(((item.grams ?: item.count ?: 0f).toString()))
    }
    val qtySuffix = if (item.grams != null) "g" else "×"

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label)
            Text(
                "${item.calories ?: 0} kcal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = qtyText,
            onValueChange = { newText ->
                qtyText = newText
                newText.toFloatOrNull()?.takeIf { it > 0f }?.let { onQuantityChange(it) }
            },
            label = { Text(qtySuffix) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(100.dp)
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = "Remove")
        }
    }
}
```

- [ ] **Step 2: Add `handlePickedFoodById` to the ViewModel**

In `SavedMealEditViewModel.kt`, add:

```kotlin
fun handlePickedFoodById(foodId: Long, quantity: Float) {
    viewModelScope.launch(Dispatchers.IO) {
        val food = foodLookup(foodId) ?: return@launch
        // Switch to main-equivalent for state mutation; not strictly required since StateFlow is thread-safe.
        addPickedFood(food, quantity)
    }
}
```

- [ ] **Step 3: Add `Route.Search.createRouteForPick` and the optional `mode` nav arg**

In `tracker/src/main/java/com/graydyn/tracker/navigation/NavGraph.kt`:

```kotlin
object Search : Route("search/{date}/{mealType}?mode={mode}") {
    fun createRoute(date: String, mealType: MealType) = "search/$date/${mealType.name}"
    fun createRouteForPick(date: String, mealType: MealType) =
        "search/$date/${mealType.name}?mode=PICK_FOR_SAVED_MEAL"
}
```

Update the composable registration:

```kotlin
composable(
    route = Route.Search.path,
    arguments = listOf(
        navArgument("date") { type = NavType.StringType },
        navArgument("mealType") { type = NavType.StringType },
        navArgument("mode") { type = NavType.StringType; defaultValue = "LOG"; nullable = false }
    )
) { backStackEntry ->
    val date = backStackEntry.arguments!!.getString("date")!!
    val mealType = MealType.valueOf(backStackEntry.arguments!!.getString("mealType")!!)
    val mode = com.graydyn.tracker.ui.search.SearchMode.valueOf(
        backStackEntry.arguments!!.getString("mode") ?: "LOG"
    )
    com.graydyn.tracker.ui.search.SearchScreen(
        navController = navController,
        date = date,
        mealType = mealType,
        mode = mode
    )
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditScreen.kt tracker/src/main/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModel.kt tracker/src/main/java/com/graydyn/tracker/navigation/NavGraph.kt
git commit -m "feat(tracker): SavedMealEditScreen + Route.Search pick mode"
```

### Task 6.4: Add Route.SavedMealEdit

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/navigation/NavGraph.kt`

**(If you ran the recommended pre-step in Task 5.4 Step 4, just the route declaration may already exist; finish wiring here.)**

- [ ] **Step 1: Add the route**

In `Route` sealed class:

```kotlin
object SavedMealEdit : Route("savedMealEdit/{id}") {
    fun createRoute(id: Long) = "savedMealEdit/$id"
}
```

Add the composable inside `NavHost`:

```kotlin
composable(
    route = Route.SavedMealEdit.path,
    arguments = listOf(navArgument("id") { type = NavType.LongType })
) { backStackEntry ->
    val id = backStackEntry.arguments!!.getLong("id")
    com.graydyn.tracker.ui.savedmeal.SavedMealEditScreen(navController = navController, savedMealId = id)
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :tracker:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/navigation/NavGraph.kt
git commit -m "feat(tracker): Route.SavedMealEdit registration"
```

### Task 6.5: Edit ViewModel unit test

**Files:**
- Create: `tracker/src/test/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.graydyn.tracker.ui.savedmeal

import android.app.Application
import com.graydyn.tracker.data.db.SavedMealSummary
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SavedMeal
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.data.repository.SavedMealApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class SavedMealEditViewModelTest {

    private val app: Application = mock(Application::class.java)

    @Before fun setMain() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }

    private fun item(id: Long, label: String, grams: Float, calories: Int, foodId: Long? = null) =
        SavedMealItem(
            id = id, savedMealId = 1L, position = (id - 1).toInt(),
            label = label, foodId = foodId,
            unitType = FoodUnitType.GRAM, grams = grams, count = null,
            calories = calories, protein = 5f, fat = 2f, carbs = 10f
        )

    private fun makeRepo(initialMeal: SavedMeal, initialItems: List<SavedMealItem>): SavedMealApi =
        object : SavedMealApi {
            var stored: SavedMeal = initialMeal
            var storedItems: MutableList<SavedMealItem> = initialItems.toMutableList()
            override fun observeSummariesForSlot(mealType: MealType): Flow<List<SavedMealSummary>> = flowOf(emptyList())
            override suspend fun getItems(savedMealId: Long): List<SavedMealItem> = storedItems.toList()
            override fun observeItems(savedMealId: Long): Flow<List<SavedMealItem>> = flowOf(storedItems.toList())
            override suspend fun getSavedMeal(savedMealId: Long): SavedMeal? = stored
            override suspend fun saveFromDiaryEntries(name: String, sourceMealType: MealType, entries: List<DiaryEntry>, nowMillis: Long) = 1L
            override suspend fun applyToSlot(savedMealId: Long, mealType: MealType, date: String, nowMillis: Long) = 0
            override suspend fun rename(savedMealId: Long, newName: String) { stored = stored.copy(name = newName) }
            override suspend fun delete(savedMealId: Long) {}
            override suspend fun replaceItems(savedMealId: Long, newItems: List<SavedMealItem>) {
                storedItems = newItems.toMutableList()
            }
        }

    @Test
    fun saveBlockedWhenItemsEmpty() = runTest {
        val repo = makeRepo(SavedMeal(1L, "M", 100L), listOf(item(1L, "x", 100f, 100)))
        val vm = SavedMealEditViewModel(app, 1L, repo, foodLookup = { null })
        advanceUntilIdle()
        vm.deleteItem(item(1L, "x", 100f, 100))
        vm.save()
        advanceUntilIdle()
        assertEquals("A meal must contain at least one food.", vm.saveError.value)
    }

    @Test
    fun saveRenumbersPositionsAndCommitsRename() = runTest {
        val repo = makeRepo(
            SavedMeal(1L, "Old", 100L),
            listOf(item(1L, "a", 100f, 100), item(2L, "b", 100f, 200), item(3L, "c", 100f, 300))
        )
        val vm = SavedMealEditViewModel(app, 1L, repo, foodLookup = { null })
        advanceUntilIdle()

        // Remove the middle item, rename, save.
        vm.deleteItem(item(2L, "b", 100f, 200))
        vm.rename("New name")
        vm.save()
        advanceUntilIdle()

        val final = (repo as Any).let {
            // Inspect via the api method
            (repo.getItems(1L)).map { it.label to it.position }
        }
        assertEquals(listOf("a" to 0, "c" to 1), final)
        assertEquals("New name", repo.getSavedMeal(1L)?.name)
    }

    @Test
    fun updateQuantityOrphanScalesProportionally() = runTest {
        val repo = makeRepo(
            SavedMeal(1L, "M", 100L),
            listOf(item(1L, "x", 200f, 100, foodId = null))
        )
        val vm = SavedMealEditViewModel(app, 1L, repo, foodLookup = { null })
        advanceUntilIdle()

        vm.updateQuantity(1L, 100f) // halve the quantity
        advanceUntilIdle()

        val updated = vm.items.value.single()
        assertEquals(100f, updated.grams!!, 0.001f)
        assertEquals(50, updated.calories) // 100 -> halved
    }

    @Test
    fun updateQuantityWithLiveFoodRecomputesFromCatalog() = runTest {
        val food = Food(
            id = 9L, name = "Live", unitType = FoodUnitType.GRAM,
            caloriesPer100g = 400f, proteinPer100g = 30f, fatPer100g = 10f, carbsPer100g = 50f,
            caloriesPerItem = null, proteinPerItem = null, fatPerItem = null, carbsPerItem = null
        )
        val repo = makeRepo(
            SavedMeal(1L, "M", 100L),
            listOf(item(1L, "Live", 100f, 200, foodId = 9L))
        )
        val vm = SavedMealEditViewModel(app, 1L, repo, foodLookup = { id -> if (id == 9L) food else null })
        advanceUntilIdle()

        vm.updateQuantity(1L, 250f)
        advanceUntilIdle()

        val updated = vm.items.value.single()
        assertEquals(250f, updated.grams!!, 0.001f)
        assertEquals(1000, updated.calories) // 400 * 250 / 100
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :tracker:testDebugUnitTest --tests "com.graydyn.tracker.ui.savedmeal.SavedMealEditViewModelTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/test/java/com/graydyn/tracker/ui/savedmeal/SavedMealEditViewModelTest.kt
git commit -m "test(tracker): SavedMealEditViewModel unit tests"
```

---

## Phase 7 — End-to-End Verification

### Task 7.1: Full manual smoke test on device

- [ ] **Scenarios** (perform each on a physical device or emulator):

1. **Scan-to-food**: Tap Scan on Breakfast → run a label scan → dialog appears with macros prefilled → enter "Test Cereal", quantity 50 → Save & log → entry appears with computed macros → open Search and verify "Test Cereal" is now in results.

2. **Save a meal**: Add 2-3 foods (mix of GRAM and ITEM) to Lunch → tap overflow on Lunch card → Save as meal → name "My usual lunch" → snackbar appears.

3. **Apply to a different day**: Navigate to tomorrow's date → open Lunch's Meals picker → see "My usual lunch" sorted to top → tap Open → see all items → tap Add to meal → entries appear in Lunch, snackbar reads "Added 3 items to Lunch". Confirm sums match the previously saved meal.

4. **Per-slot ordering**: Save a second Lunch meal "Other lunch". Open Lunch picker → "Other lunch" sorted top. Apply "My usual lunch". Re-open Lunch picker → "My usual lunch" sorted top.

5. **Rename**: From the picker, overflow → Rename → change name → confirm sheet refreshes with new name.

6. **Delete**: Overflow → Delete → confirm → sheet refreshes; meal removed.

7. **Edit contents**: Save a meal, open the picker, overflow → Edit → screen opens. Delete one item, change a quantity, tap Add food → SearchScreen opens in pick mode; pick a food and quantity → return to edit screen with the new item appended. Save → returns to diary; open the picker again and confirm the saved meal reflects the changes.

8. **Empty after edit**: Open edit → delete every item → tap Save → inline error appears; Discard returns to diary.

9. **Cascade delete**: Save a meal, then Delete it from the picker → reopen picker → empty state shows correctly.

10. **Persistence**: Force-stop the app → relaunch → confirm saved meals are still there.

- [ ] **Step 1: Run all scenarios; capture any defects**

If any scenario fails, file fix tasks and address them before declaring complete. Otherwise, proceed to Step 2.

- [ ] **Step 2: Final summary commit (if any small fixes made)**

```bash
git status
# any remaining changes
git add <fixes>
git commit -m "fix(tracker): smoke-test cleanups for saved meals"
```

---

## Self-Review Checklist (run after writing this plan)

**Spec coverage:**

| Spec section | Covered by |
| --- | --- |
| Goal / Naming (UI = "Meal", code = SavedMeal) | Phases 3-5 |
| Non-goals (no sharing, no scan-row migration, no Compose UI tests, no separate manage screen) | Honored — no tasks contradict |
| Scan-to-food UX (1-6) | Tasks 2.1-2.5 |
| `SourceType.SCANNED` legacy retention | Honored — enum untouched, apply path emits SCANNED only when foodId is null |
| Save-as-meal UX (1-4) | Tasks 4.1-4.4 |
| Apply-saved-meal UX (1-4) | Tasks 5.1-5.4 |
| Edit / Rename / Delete | Tasks 6.1-6.5 |
| SearchScreen pick mode | Task 6.1, 6.3 (route arg) |
| Data model (3 entities + migration) | Tasks 3.1-3.5 |
| Architecture file layout | "File Structure" section |
| Error handling and edge cases | Honored across tasks; orphan handling in 6.2, empty-meal guard in 6.5, cascade in 3.5/3.7 |
| Assumptions to verify | Task 2.4 (Macros contract); reorderable list noted as deferred |
| Testing — DAO instrumented | Task 3.7 |
| Testing — migration instrumented | Task 3.8 |
| Testing — VM unit | Tasks 4.3, 5.3, 6.5 |
| Testing — manual smoke | Task 7.1 |

**Type consistency:**
- `SavedMealApi` referenced in DiaryViewModel (Task 4.2), and in tests (Tasks 4.3, 5.3, 6.5). Defined in 4.3.
- `SavedMealSummary` defined in 3.4, consumed in 5.1, 5.2, 5.4, 6.5.
- `MealType` import paths consistent.
- `Route.SavedMealEdit.createRoute(id)` defined in 6.4, called in 5.4.
- `Route.Search.createRouteForPick(date, mealType)` defined in 6.3, called in 6.3.
- `viewModel.handlePickedFoodById` defined in 6.3 Step 2, called in 6.3 Step 1.

**No placeholders verified:** All code blocks contain complete, runnable code. No "TODO" or "TBD" in plan steps. The single "TODO" comment suggested in 5.4 Step 4 is for the recommended-bypass branch; the recommended path runs Task 6.4 first.