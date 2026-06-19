# Last Logged Amount Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remember the quantity entered when a food is logged from the Add Food search screen, and prefill it the next time that food is selected.

**Architecture:** Add a nullable `lastAmount` column to the `foods` table (Room migration v5→6). The search-screen log path (`SearchViewModel.logEntry`) writes the entered quantity back to the food via a targeted DAO update. `SearchViewModel.onSelectFood` reads it back and prefills the editable quantity field, formatted by a pure helper.

**Tech Stack:** Kotlin, Room, Jetpack Compose, Kotlin coroutines. JVM unit tests (JUnit) in `src/test`.

## Global Constraints

- The `tracker` module owns all code here. Package root: `com.graydyn.tracker`.
- `Food` macro/amount numerics are `Float?`; SQLite column type is `REAL`.
- DB migrations are registered in `TrackerDatabase.getInstance` via `addMigrations(...)`.
- Only the search-screen log path updates `lastAmount`. Saved-meal and copy-meal paths are untouched.
- Run JVM tests with `./gradlew :tracker:testDebugUnitTest`. Build with `./gradlew :tracker:assembleDebug`.
- **Instrumented tests are out of scope for this plan** — the migration and DAO behavior will be verified manually by the user on a device. Do not write `androidTest` files.

---

### Task 1: Add `lastAmount` column and migration

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt`

**Interfaces:**
- Produces: `Food.lastAmount: Float?` (defaults to `null`); `TrackerDatabase.MIGRATION_5_6`; database version is now `6`.

- [ ] **Step 1: Add the `lastAmount` field to `Food`**

In `tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt`, add the field to the `Food` data class after `itemsPerServing`:

```kotlin
    val gramsPerServing: Float? = null,
    val itemsPerServing: Float? = null,
    val lastAmount: Float? = null,
    @ColumnInfo(defaultValue = "0") val foundational: Boolean = false,
    @ColumnInfo(defaultValue = "0") val userAdded: Boolean = false,
```

- [ ] **Step 2: Add the migration and bump the version**

In `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt`:

Change the version in the `@Database` annotation:

```kotlin
    version = 6,
```

Add the migration object inside `companion object`, after `MIGRATION_4_5`:

```kotlin
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN lastAmount REAL")
            }
        }
```

Register it in `getInstance`:

```kotlin
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
```

- [ ] **Step 3: Build to verify it compiles and the schema is valid**

Run: `./gradlew :tracker:assembleDebug`
Expected: BUILD SUCCESSFUL. (Room validates the entity ↔ schema at compile time, so a version/column mismatch fails here.)

- [ ] **Step 4: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt \
        tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt
git commit -m "feat(tracker): add foods.lastAmount column and MIGRATION_5_6"
```

---

### Task 2: DAO + repository support for writing `lastAmount`

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/db/FoodDao.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/repository/FoodRepository.kt`

**Interfaces:**
- Consumes: `Food.lastAmount` (Task 1).
- Produces:
  - `FoodDao.updateLastAmount(id: Long, amount: Float)` (suspend)
  - `FoodRepository.updateLastAmount(id: Long, amount: Float)` (suspend)

- [ ] **Step 1: Add `updateLastAmount` to `FoodDao`**

In `tracker/src/main/java/com/graydyn/tracker/data/db/FoodDao.kt`, add after the `getById` query:

```kotlin
    @Query("UPDATE foods SET lastAmount = :amount WHERE id = :id")
    suspend fun updateLastAmount(id: Long, amount: Float)
```

- [ ] **Step 2: Add the repository passthrough**

In `tracker/src/main/java/com/graydyn/tracker/data/repository/FoodRepository.kt`, add after `add`:

```kotlin
    suspend fun updateLastAmount(id: Long, amount: Float) =
        dao.updateLastAmount(id, amount)
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :tracker:assembleDebug`
Expected: BUILD SUCCESSFUL. (Room validates the `@Query` SQL against the schema at compile time.)

- [ ] **Step 4: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/db/FoodDao.kt \
        tracker/src/main/java/com/graydyn/tracker/data/repository/FoodRepository.kt
git commit -m "feat(tracker): add FoodDao.updateLastAmount and repository passthrough"
```

---

### Task 3: `formatAmount` display helper (pure, JVM-tested)

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/components/ServingConversion.kt`
- Test: `tracker/src/test/java/com/graydyn/tracker/ui/components/FormatAmountTest.kt`

**Interfaces:**
- Produces: top-level `fun formatAmount(value: Float): String` in package `com.graydyn.tracker.ui.components`.

Rationale: `ServingConversion.kt` already hosts small pure top-level helpers for this screen and is covered by a JVM unit test (`ServingConversionTest`). Keeping `formatAmount` pure and here lets us unit-test the formatting rules on the JVM without Android.

- [ ] **Step 1: Write the failing test**

Create `tracker/src/test/java/com/graydyn/tracker/ui/components/FormatAmountTest.kt`:

```kotlin
package com.graydyn.tracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatAmountTest {

    @Test
    fun wholeNumber_dropsTrailingDecimal() {
        assertEquals("100", formatAmount(100.0f))
    }

    @Test
    fun decimal_isPreserved() {
        assertEquals("1.5", formatAmount(1.5f))
    }

    @Test
    fun smallWholeNumber_formatsCleanly() {
        assertEquals("1", formatAmount(1.0f))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :tracker:testDebugUnitTest --tests "com.graydyn.tracker.ui.components.FormatAmountTest"`
Expected: FAIL — `formatAmount` is unresolved (won't compile).

- [ ] **Step 3: Implement `formatAmount`**

In `tracker/src/main/java/com/graydyn/tracker/ui/components/ServingConversion.kt`, add at the end of the file:

```kotlin
/** Renders an amount the way a user would have typed it: drops a trailing ".0". */
fun formatAmount(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString()
    else value.toString()
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :tracker:testDebugUnitTest --tests "com.graydyn.tracker.ui.components.FormatAmountTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/components/ServingConversion.kt \
        tracker/src/test/java/com/graydyn/tracker/ui/components/FormatAmountTest.kt
git commit -m "feat(tracker): add formatAmount display helper"
```

---

### Task 4: Wire write + read into `SearchViewModel`

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`

**Interfaces:**
- Consumes: `FoodRepository.updateLastAmount` (Task 2), `formatAmount` (Task 3), `Food.lastAmount` (Task 1).
- Produces: no new public surface; behavior change only — `onSelectFood` prefills `quantity`; `logEntry` persists `lastAmount`.

This task has no isolated automated test: `SearchViewModel` is an `AndroidViewModel` that builds its own `TrackerDatabase` singleton and dispatches DB work on `Dispatchers.IO` inside `viewModelScope`, so it is not deterministically unit-testable without refactoring its construction — out of scope here. The persistence and formatting logic it calls are covered by the `formatAmount` JVM test (Task 3) and the user's manual device testing. Verify this task by reading the diff and by the manual check in Step 4.

- [ ] **Step 1: Prefill quantity in `onSelectFood`**

In `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`, add the import near the other imports:

```kotlin
import com.graydyn.tracker.ui.components.formatAmount
```

Replace the existing `onSelectFood`:

```kotlin
    fun onSelectFood(food: Food) {
        _selectedFood.value = food
        _quantity.value = ""
    }
```

with:

```kotlin
    fun onSelectFood(food: Food) {
        _selectedFood.value = food
        _quantity.value = food.lastAmount
            ?.takeIf { it > 0f }
            ?.let { formatAmount(it) }
            ?: ""
    }
```

- [ ] **Step 2: Persist `lastAmount` in `logEntry`**

In the same file, in `logEntry`, replace the insert launch at the end of the function:

```kotlin
        viewModelScope.launch(Dispatchers.IO) { diaryRepo.insert(entry) }
        return true
```

with:

```kotlin
        viewModelScope.launch(Dispatchers.IO) {
            diaryRepo.insert(entry)
            foodRepo.updateLastAmount(food.id, qty)
        }
        return true
```

(`qty` is already validated `> 0f` earlier in the function, and `food.id` is the database id of the selected food.)

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :tracker:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification (user)**

On an emulator/device:
1. Add Food → search a food → select it → enter `50` → Add.
2. Add Food → search the same food → select it.
3. Expected: the quantity field is prefilled with `50` and is editable.
4. Select a food never logged this way → expected: quantity field is empty.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt
git commit -m "feat(tracker): prefill and persist last logged amount in search"
```

---

## Self-Review

**Spec coverage:**
- Data model (`lastAmount: Float?`) → Task 1, Step 1. ✓
- Migration v5→6 → Task 1, Step 2. ✓
- `FoodDao.updateLastAmount` + repository passthrough → Task 2. ✓
- Write path in `logEntry` → Task 4, Step 2. ✓
- Read/prefill path in `onSelectFood` + `formatAmount` rules (`100.0`→`100`, `1.5`→`1.5`) → Task 3 + Task 4, Step 1. ✓
- No UI change required → Task 4 touches only the view model. ✓
- Tests: per user direction, instrumented (`androidTest`) tests for the migration and DAO are skipped and verified manually; only the pure `formatAmount` JVM test is written (Task 3). ✓
- Out-of-scope items (per-meal-type, saved-meal/copy-meal, scanned foods) → not implemented. ✓

**Placeholder scan:** No TBD/TODO; every code step contains complete code. ✓

**Type consistency:** `updateLastAmount(id: Long, amount: Float)` identical across DAO (Task 2), repository (Task 2), and call site (Task 4). `formatAmount(value: Float): String` identical between Task 3 definition and Task 4 use. `Food.lastAmount: Float?` consistent across Tasks 1, 2, 4. ✓
