# Current Weight on the Diary Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-day current-weight field to the Food Diary screen that carries forward from the most recent prior entry until the user updates it.

**Architecture:** A new single-column-keyed Room table `weight_entries` (one row per day a weight was recorded). The "effective weight" for any date is resolved by a carry-forward query (`date <= :date ORDER BY date DESC LIMIT 1`). A `WeightRepository` wraps the DAO; `DiaryViewModel` exposes `effectiveWeight: StateFlow<Float?>` and `setWeight(lbs)`. The `SummaryCard` in `DiaryScreen` gains a tappable weight row that opens a `WeightDialog`.

**Tech Stack:** Kotlin, Room (with kapt + exported schemas), Jetpack Compose Material3, Kotlin coroutines/Flow. Tests are AndroidJUnit4 instrumented tests under `tracker/src/androidTest` using in-memory Room and `MigrationTestHelper`.

## Global Constraints

- Room schema is **exported** to `tracker/schemas/com.graydyn.tracker.data.db.TrackerDatabase/` (one JSON per version). Bumping the DB version generates `7.json` at build time; commit it.
- All DB-backed and ViewModel tests are **instrumented** (`tracker/src/androidTest/...`, `@RunWith(AndroidJUnit4::class)`), run on a device/emulator via `./gradlew :tracker:connectedDebugAndroidTest`. Follow the existing in-memory Room pattern (`Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()`).
- `MigrationTestHelper` is constructed with `emptyList()` for auto-migrations and `FrameworkSQLiteOpenHelperFactory()`, validating against exported schemas.
- Weight unit is **pounds (lbs)**, decimals allowed. Format with the existing `formatAmount(value: Float)` in `tracker/src/main/java/com/graydyn/tracker/ui/components/ServingConversion.kt` (trims trailing zeros: `182.0f` -> `"182"`, `182.4f` -> `"182.4"`).
- Migration `MIGRATION_6_7` is purely additive — it must not touch existing data.

---

### Task 1: WeightEntry entity, DAO, and migration to DB version 7

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/data/model/WeightEntry.kt`
- Create: `tracker/src/main/java/com/graydyn/tracker/data/db/WeightEntryDao.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt`
- Test: `tracker/src/androidTest/java/com/graydyn/tracker/data/db/WeightEntryDaoTest.kt`
- Test: `tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration6To7Test.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `data class WeightEntry(val date: String, val weightLbs: Float)` with `@PrimaryKey val date: String`, table name `weight_entries`.
  - `WeightEntryDao.observeEffectiveWeight(date: String): Flow<WeightEntry?>`
  - `WeightEntryDao.upsert(entry: WeightEntry)` (suspend, REPLACE on conflict)
  - `TrackerDatabase.weightEntryDao(): WeightEntryDao`
  - `TrackerDatabase.MIGRATION_6_7`
  - DB version is now `7`.

- [ ] **Step 1: Create the entity**

Create `tracker/src/main/java/com/graydyn/tracker/data/model/WeightEntry.kt`:

```kotlin
package com.graydyn.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per day the user actually recorded a weight (pounds). The effective
 * weight for any date carries forward from the most recent row at or before it.
 */
@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey val date: String,   // "yyyy-MM-dd"
    val weightLbs: Float
)
```

- [ ] **Step 2: Create the DAO**

Create `tracker/src/main/java/com/graydyn/tracker/data/db/WeightEntryDao.kt`:

```kotlin
package com.graydyn.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.graydyn.tracker.data.model.WeightEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightEntryDao {

    /** Carry-forward: most recent weight at or before the given date, or null. */
    @Query("SELECT * FROM weight_entries WHERE date <= :date ORDER BY date DESC LIMIT 1")
    fun observeEffectiveWeight(date: String): Flow<WeightEntry?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntry)
}
```

- [ ] **Step 3: Wire the entity, DAO accessor, migration, and version bump into TrackerDatabase**

In `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt`:

Add the import near the other model imports:

```kotlin
import com.graydyn.tracker.data.model.WeightEntry
```

Add `WeightEntry::class` to the `entities` list and change `version = 6` to `version = 7`:

```kotlin
@Database(
    entities = [
        Food::class,
        DiaryEntry::class,
        Goals::class,
        com.graydyn.tracker.data.model.SavedMeal::class,
        com.graydyn.tracker.data.model.SavedMealItem::class,
        com.graydyn.tracker.data.model.SavedMealSlotApplication::class,
        WeightEntry::class
    ],
    version = 7,
    exportSchema = true
)
```

Add the DAO accessor alongside the others:

```kotlin
    abstract fun weightEntryDao(): WeightEntryDao
```

Add the migration after `MIGRATION_5_6`:

```kotlin
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `weight_entries` (
                        `date` TEXT NOT NULL PRIMARY KEY,
                        `weightLbs` REAL NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
```

Register it in `addMigrations(...)`:

```kotlin
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
```

- [ ] **Step 4: Write the failing DAO test**

Create `tracker/src/androidTest/java/com/graydyn/tracker/data/db/WeightEntryDaoTest.kt`:

```kotlin
package com.graydyn.tracker.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.graydyn.tracker.data.model.WeightEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeightEntryDaoTest {

    private lateinit var db: TrackerDatabase
    private lateinit var dao: WeightEntryDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TrackerDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.weightEntryDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun emptyTable_returnsNull() = runTest {
        assertNull(dao.observeEffectiveWeight("2026-06-20").first())
    }

    @Test
    fun exactDay_returnsThatDaysWeight() = runTest {
        dao.upsert(WeightEntry("2026-06-20", 182.4f))
        assertEquals(182.4f, dao.observeEffectiveWeight("2026-06-20").first()!!.weightLbs)
    }

    @Test
    fun noRowForDate_carriesFromMostRecentPrior() = runTest {
        dao.upsert(WeightEntry("2026-06-18", 180f))
        dao.upsert(WeightEntry("2026-06-19", 181f))
        // 2026-06-21 has no row -> carries from 2026-06-19
        assertEquals(181f, dao.observeEffectiveWeight("2026-06-21").first()!!.weightLbs)
    }

    @Test
    fun futureRowsAreIgnored() = runTest {
        dao.upsert(WeightEntry("2026-06-25", 200f))
        // Looking at 2026-06-20, the only row is in the future -> null
        assertNull(dao.observeEffectiveWeight("2026-06-20").first())
    }

    @Test
    fun editingOldDay_affectsLaterRowlessDays_butNotLaterDaysWithOwnRow() = runTest {
        dao.upsert(WeightEntry("2026-06-10", 170f))
        dao.upsert(WeightEntry("2026-06-20", 175f))
        // Edit the old day
        dao.upsert(WeightEntry("2026-06-10", 168f))
        // A later day WITH its own row is unaffected
        assertEquals(175f, dao.observeEffectiveWeight("2026-06-20").first()!!.weightLbs)
        // A later day WITHOUT its own row (between the two edits) reflects the edit
        assertEquals(168f, dao.observeEffectiveWeight("2026-06-15").first()!!.weightLbs)
    }

    @Test
    fun upsertSameDay_replacesValue() = runTest {
        dao.upsert(WeightEntry("2026-06-20", 182f))
        dao.upsert(WeightEntry("2026-06-20", 183.5f))
        assertEquals(183.5f, dao.observeEffectiveWeight("2026-06-20").first()!!.weightLbs)
    }
}
```

- [ ] **Step 5: Write the failing migration test**

Create `tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration6To7Test.kt`:

```kotlin
package com.graydyn.tracker.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration6To7Test {

    private val testDbName = "tracker-migration-6to7-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrackerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate6To7_createsWeightTableAndPreservesExistingData() {
        helper.createDatabase(testDbName, 6).use { v6 ->
            v6.execSQL(
                """
                INSERT INTO foods (name, unitType, caloriesPer100g, proteinPer100g, fatPer100g, carbsPer100g, caloriesPerItem, proteinPerItem, fatPerItem, carbsPerItem, foundational, userAdded)
                VALUES ('Oats', 'GRAM', 379.0, 13.0, 7.0, 67.0, NULL, NULL, NULL, NULL, 1, 0)
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            testDbName,
            7,
            true,
            TrackerDatabase.MIGRATION_6_7
        )

        // Existing data preserved
        migrated.query("SELECT name FROM foods").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals("Oats", c.getString(0))
        }

        // New table exists and is empty
        migrated.query("SELECT COUNT(*) FROM weight_entries").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }
}
```

- [ ] **Step 6: Build to generate the v7 schema, then run the tests**

First build the module so Room emits the `7.json` schema (the migration test validates against it):

Run: `./gradlew :tracker:assembleDebug`
Expected: BUILD SUCCESSFUL, and `tracker/schemas/com.graydyn.tracker.data.db.TrackerDatabase/7.json` now exists.

Then run the instrumented tests (device/emulator required):

Run: `./gradlew :tracker:connectedDebugAndroidTest --tests "com.graydyn.tracker.data.db.WeightEntryDaoTest" --tests "com.graydyn.tracker.data.db.Migration6To7Test"`
Expected: All tests PASS.

> If no emulator is available, document that these instrumented tests could not be run locally and must pass in CI; do not claim them passing without evidence.

- [ ] **Step 7: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/model/WeightEntry.kt \
        tracker/src/main/java/com/graydyn/tracker/data/db/WeightEntryDao.kt \
        tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt \
        tracker/src/androidTest/java/com/graydyn/tracker/data/db/WeightEntryDaoTest.kt \
        tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration6To7Test.kt \
        tracker/schemas/com.graydyn.tracker.data.db.TrackerDatabase/7.json
git commit -m "feat(tracker): add weight_entries table with carry-forward query"
```

---

### Task 2: WeightRepository

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/data/repository/WeightRepository.kt`
- Test: `tracker/src/androidTest/java/com/graydyn/tracker/data/repository/WeightRepositoryTest.kt`

**Interfaces:**
- Consumes: `WeightEntryDao`, `WeightEntry` (Task 1).
- Produces:
  - `WeightRepository(dao: WeightEntryDao)`
  - `WeightRepository.observeEffectiveWeight(date: String): Flow<WeightEntry?>`
  - `WeightRepository.setWeight(date: String, weightLbs: Float)` (suspend)

- [ ] **Step 1: Write the failing repository test**

Create `tracker/src/androidTest/java/com/graydyn/tracker/data/repository/WeightRepositoryTest.kt`:

```kotlin
package com.graydyn.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.graydyn.tracker.data.db.TrackerDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeightRepositoryTest {

    private lateinit var db: TrackerDatabase
    private lateinit var repo: WeightRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TrackerDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = WeightRepository(db.weightEntryDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun setWeight_thenObserve_returnsValueForThatDay() = runTest {
        repo.setWeight("2026-06-20", 182.4f)
        assertEquals(182.4f, repo.observeEffectiveWeight("2026-06-20").first()!!.weightLbs)
    }

    @Test
    fun observe_carriesForwardFromPriorDay() = runTest {
        repo.setWeight("2026-06-18", 180f)
        assertEquals(180f, repo.observeEffectiveWeight("2026-06-22").first()!!.weightLbs)
    }

    @Test
    fun observe_emptyReturnsNull() = runTest {
        assertNull(repo.observeEffectiveWeight("2026-06-20").first())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :tracker:connectedDebugAndroidTest --tests "com.graydyn.tracker.data.repository.WeightRepositoryTest"`
Expected: FAIL — unresolved reference `WeightRepository`.

- [ ] **Step 3: Implement the repository**

Create `tracker/src/main/java/com/graydyn/tracker/data/repository/WeightRepository.kt`:

```kotlin
package com.graydyn.tracker.data.repository

import com.graydyn.tracker.data.db.WeightEntryDao
import com.graydyn.tracker.data.model.WeightEntry
import kotlinx.coroutines.flow.Flow

class WeightRepository(
    private val dao: WeightEntryDao
) {
    fun observeEffectiveWeight(date: String): Flow<WeightEntry?> =
        dao.observeEffectiveWeight(date)

    suspend fun setWeight(date: String, weightLbs: Float) =
        dao.upsert(WeightEntry(date, weightLbs))
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :tracker:connectedDebugAndroidTest --tests "com.graydyn.tracker.data.repository.WeightRepositoryTest"`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/repository/WeightRepository.kt \
        tracker/src/androidTest/java/com/graydyn/tracker/data/repository/WeightRepositoryTest.kt
git commit -m "feat(tracker): add WeightRepository"
```

---

### Task 3: DiaryViewModel exposes effectiveWeight and setWeight

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt`
- Test: `tracker/src/androidTest/java/com/graydyn/tracker/ui/diary/DiaryViewModelWeightTest.kt`

**Interfaces:**
- Consumes: `WeightRepository` (Task 2), existing `_selectedDate: MutableStateFlow<String>` and `db` in `DiaryViewModel`.
- Produces:
  - `DiaryViewModel.effectiveWeight: StateFlow<Float?>`
  - `DiaryViewModel.setWeight(lbs: Float)`

- [ ] **Step 1: Write the failing ViewModel test**

The existing ViewModel tests (e.g. `DiaryViewModelSaveMealTest`) exercise repositories directly against an in-memory DB rather than constructing the full `DiaryViewModel` (which needs `Application` + DataStore). Follow that pattern: drive `WeightRepository` against the in-memory DB and assert the carry-forward StateFlow contract the ViewModel relies on. This keeps the test hermetic and matches the established style.

Create `tracker/src/androidTest/java/com/graydyn/tracker/ui/diary/DiaryViewModelWeightTest.kt`:

```kotlin
package com.graydyn.tracker.ui.diary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.repository.WeightRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryViewModelWeightTest {

    private lateinit var db: TrackerDatabase
    private lateinit var repo: WeightRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TrackerDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = WeightRepository(db.weightEntryDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun setWeight_writesRowForSelectedDate() = runTest {
        val selectedDate = "2026-06-20"
        repo.setWeight(selectedDate, 182.4f)
        assertEquals(182.4f, repo.observeEffectiveWeight(selectedDate).first()!!.weightLbs)
    }

    @Test
    fun effectiveWeight_followsSelectedDateAcrossDays() = runTest {
        repo.setWeight("2026-06-18", 180f)
        // selecting a later rowless day carries forward
        assertEquals(180f, repo.observeEffectiveWeight("2026-06-25").first()!!.weightLbs)
        // selecting an earlier day before any entry is null
        assertNull(repo.observeEffectiveWeight("2026-06-01").first())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :tracker:connectedDebugAndroidTest --tests "com.graydyn.tracker.ui.diary.DiaryViewModelWeightTest"`
Expected: FAIL — unresolved reference `WeightRepository` import compiles only after Task 2; if Task 2 is already merged this fails instead on the not-yet-added ViewModel members once Step 3 wiring is referenced. (The test itself uses only the repo, so it will PASS after Task 2 — its purpose is to lock the carry-forward contract. Proceed to Step 3 to add the ViewModel members the UI needs.)

- [ ] **Step 3: Add the repository, StateFlow, and action to DiaryViewModel**

In `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt`:

Add the import alongside the other repository imports:

```kotlin
import com.graydyn.tracker.data.repository.WeightRepository
```

Construct the repository next to the others (after the `savedMealRepo` block, around line 63):

```kotlin
    private val weightRepo = WeightRepository(db.weightEntryDao())
```

Add the StateFlow next to `proteinOnly` (after line 201). Place it below the existing flows so `_selectedDate` is already declared:

```kotlin
    val effectiveWeight: StateFlow<Float?> =
        _selectedDate
            .flatMapLatest { date -> weightRepo.observeEffectiveWeight(date) }
            .map { it?.weightLbs }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

Add the action near `deleteEntry` (around line 316):

```kotlin
    fun setWeight(lbs: Float) {
        val date = _selectedDate.value
        viewModelScope.launch(Dispatchers.IO) { weightRepo.setWeight(date, lbs) }
    }
```

(`flatMapLatest`, `map`, `stateIn`, `SharingStarted`, `StateFlow`, `viewModelScope`, `Dispatchers`, `launch` are all already imported in this file.)

- [ ] **Step 4: Run the test and build to verify everything compiles and passes**

Run: `./gradlew :tracker:connectedDebugAndroidTest --tests "com.graydyn.tracker.ui.diary.DiaryViewModelWeightTest"`
Expected: All tests PASS and the module compiles with the new ViewModel members.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt \
        tracker/src/androidTest/java/com/graydyn/tracker/ui/diary/DiaryViewModelWeightTest.kt
git commit -m "feat(tracker): expose effectiveWeight and setWeight in DiaryViewModel"
```

---

### Task 4: WeightDialog composable

**Files:**
- Create: `tracker/src/main/java/com/graydyn/tracker/ui/components/WeightDialog.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks (pure UI).
- Produces:
  - `@Composable fun WeightDialog(currentLbs: Float?, onDismiss: () -> Unit, onSave: (Float) -> Unit)`

- [ ] **Step 1: Implement the dialog**

This is presentation-only and follows the existing `ScannedFoodDialog` patterns (AlertDialog, OutlinedTextField with `KeyboardType.Decimal`, FocusRequester). No unit test — Compose UI in this module is not unit-tested (only logic and DB layers are). Verification happens via the manual run in Task 5.

Create `tracker/src/main/java/com/graydyn/tracker/ui/components/WeightDialog.kt`:

```kotlin
package com.graydyn.tracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType

/**
 * Enter or update the current weight (pounds). Pre-fills with [currentLbs] when
 * a value already exists. Save is disabled until the input parses to a positive
 * number.
 */
@Composable
fun WeightDialog(
    currentLbs: Float?,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit
) {
    var text by remember {
        mutableStateOf(currentLbs?.let { formatAmount(it) } ?: "")
    }
    val parsed = text.trim().toFloatOrNull()
    val isValid = parsed != null && parsed > 0f

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weight") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Weight (lbs)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onSave(it) } },
                enabled = isValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :tracker:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/components/WeightDialog.kt
git commit -m "feat(tracker): add WeightDialog composable"
```

---

### Task 5: Wire the weight row into the diary SummaryCard

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt`

**Interfaces:**
- Consumes: `DiaryViewModel.effectiveWeight` and `DiaryViewModel.setWeight` (Task 3), `WeightDialog` (Task 4), `formatAmount` (existing, in `ui/components/ServingConversion.kt`).
- Produces: user-visible weight row + edit dialog on the diary screen.

- [ ] **Step 1: Add imports**

In `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt` add, with the existing component imports (near the `ScannedFoodDialog` / `SaveMealDialog` imports around lines 83-85):

```kotlin
import com.graydyn.tracker.ui.components.WeightDialog
import com.graydyn.tracker.ui.components.formatAmount
```

And with the foundation layout imports (near `clickable` usage is not yet imported, so add):

```kotlin
import androidx.compose.foundation.clickable
```

- [ ] **Step 2: Collect the weight state and add dialog state in `DiaryScreen`**

Inside `DiaryScreen`, with the other `collectAsState()` calls (after line 134):

```kotlin
    val effectiveWeight by viewModel.effectiveWeight.collectAsState()
```

With the other `remember { mutableStateOf(...) }` dialog-target declarations (near `renameTarget`, line 135):

```kotlin
    var weightDialogOpen by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Pass weight into `SummaryCard` and open the dialog on tap**

Change the `SummaryCard(...)` call (lines 222-234) to also pass the weight and an edit callback:

```kotlin
            item {
                SummaryCard(
                    calorieGoal = goals?.caloriesGoal ?: 2000,
                    proteinGoal = goals?.proteinGoal ?: 150,
                    fatGoal = goals?.fatGoal ?: 65,
                    carbsGoal = goals?.carbsGoal ?: 250,
                    calories = dailyTotals.calories,
                    protein = dailyTotals.protein,
                    fat = dailyTotals.fat,
                    carbs = dailyTotals.carbs,
                    proteinOnly = proteinOnly,
                    weightLbs = effectiveWeight,
                    onEditWeight = { weightDialogOpen = true }
                )
            }
```

- [ ] **Step 4: Render the dialog**

Add near the other dialog blocks inside the `Scaffold` content (e.g. after the `deleteTarget?.let { ... }` block, before the closing brace of the `Scaffold` lambda around line 348):

```kotlin
        if (weightDialogOpen) {
            WeightDialog(
                currentLbs = effectiveWeight,
                onDismiss = { weightDialogOpen = false },
                onSave = { lbs ->
                    viewModel.setWeight(lbs)
                    weightDialogOpen = false
                }
            )
        }
```

- [ ] **Step 5: Add the `weightLbs` / `onEditWeight` params and the weight row to `SummaryCard`**

Change the `SummaryCard` signature (lines 410-420) to add two parameters at the end:

```kotlin
@Composable
private fun SummaryCard(
    calorieGoal: Int,
    proteinGoal: Int,
    fatGoal: Int,
    carbsGoal: Int,
    calories: Int,
    protein: Float,
    fat: Float,
    carbs: Float,
    proteinOnly: Boolean,
    weightLbs: Float?,
    onEditWeight: () -> Unit
) {
```

Then add the weight row at the end of the card's inner `Column`, immediately after the closing brace of the `if (!proteinOnly) { ... }` block and before the `Column`'s closing brace (around line 477-478):

```kotlin
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEditWeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weight",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = weightLbs?.let { "${formatAmount(it)} lbs" } ?: "Add weight",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (weightLbs != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
```

(`Row`, `Spacer`, `Text`, `Alignment`, `MaterialTheme`, `Modifier`, `fillMaxWidth`, `height`, `weight`, `dp` are all already imported in this file.)

- [ ] **Step 6: Build and run the app to verify the feature works end to end**

Run: `./gradlew :tracker:assembleDebug`
Expected: BUILD SUCCESSFUL.

Then launch the app (emulator/device) and verify, on the Food Diary screen:
1. The summary card shows a "Weight  Add weight" row before any weight is recorded.
2. Tapping it opens the dialog; entering `182.4` and Save shows "Weight  182.4 lbs".
3. Navigating to the next day (no entry) still shows "182.4 lbs" (carry-forward).
4. Updating it on a later day changes that day forward, while the earlier day keeps its value.
5. Entering a whole number like `180` shows "180 lbs" (no trailing `.0`).

> If no emulator is available, document that the manual run could not be performed locally; do not claim the UI verified without evidence.

- [ ] **Step 7: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt
git commit -m "feat(tracker): show current weight on the diary summary card"
```

---

## Self-Review Notes

- **Spec coverage:** per-day storage + carry-forward (Task 1 DAO query + tests); lbs unit & decimals (Tasks 4/5, `formatAmount`); summary-card placement (Task 5); tap-to-edit dialog (Task 4); "Add weight" empty prompt (Task 5); migration additive & version 7 (Task 1); carry-forward edit semantics (Task 1 test `editingOldDay_...`); testing of DAO + ViewModel contract (Tasks 1-3). All spec sections map to a task.
- **Out of scope** items (history chart, kg toggle, goal weight) are not implemented — matches spec YAGNI.
- **Type consistency:** `observeEffectiveWeight(date): Flow<WeightEntry?>`, `setWeight(date, weightLbs)` / `setWeight(lbs)` (VM overload), `effectiveWeight: StateFlow<Float?>`, `WeightDialog(currentLbs, onDismiss, onSave)`, `SummaryCard(..., weightLbs, onEditWeight)`, `formatAmount(Float)` — all consistent across tasks.
- **No placeholders:** every code step shows full code; every test step shows full test bodies.
