# Foundation + User-Added Search Ranking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `FoodDao.search` rank foundation foods and user-added foods above the rest, with prefix > word-start > contains ordering inside each tier.

**Architecture:** Add two booleans (`foundational`, `userAdded`) to the `foods` table. A new Python script tags rows in `nutrition_all.csv` whose name appears in `nutrition_foundation.csv`; the seeder reads the new column. Both runtime insertion paths (`createFood`, `logScannedFood`) mark new rows `userAdded = true`. A v3→v4 migration wipes `foods`, `diary_entries`, `saved_meals`, and `saved_meal_items` so dangling `foodId` references cannot survive into the new schema. The DAO query is rewritten with a two-tier `CASE` plus a match-position `CASE`.

**Tech Stack:** Kotlin, Room (Android), Python 3 (CSV regen script), JUnit + Room MigrationTestHelper for instrumented tests.

**Source spec:** `docs/superpowers/specs/2026-05-18-foundation-search-ranking-design.md`

**Connected device or emulator required** for all instrumented (`androidTest`) tasks.

---

## File map

**Created:**
- `scripts/build_nutrition_all.py`
- `tracker/src/test/java/com/graydyn/tracker/data/seed/CsvSeederTest.kt`
- `tracker/src/androidTest/java/com/graydyn/tracker/data/db/FoodDaoSearchTest.kt`
- `tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration3To4Test.kt`

**Modified:**
- `tracker/src/main/assets/nutrition_all.csv` (regenerated with a 6th column)
- `tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt`
- `tracker/src/main/java/com/graydyn/tracker/data/seed/CsvSeeder.kt`
- `tracker/src/main/java/com/graydyn/tracker/data/db/FoodDao.kt`
- `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt`
- `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`
- `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt`

---

### Task 1: Add `foundational` and `userAdded` fields to the Food entity

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt`

This task only changes the entity. The DB version is still 3, so a build would now fail Room's schema validation — that's expected. Task 2 fixes it. Do not run the app between tasks 1 and 2.

- [ ] **Step 1: Edit Food.kt**

Replace the entire contents of `tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt` with:

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

- [ ] **Step 2: Do not commit yet**

Task 2 includes the matching schema bump and migration. Leave this change uncommitted in the working tree until Task 2 commits them together.

---

### Task 2: Bump DB version to 4, add MIGRATION_3_4, add migration test

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt`
- Create: `tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration3To4Test.kt`

- [ ] **Step 1: Write the failing migration test**

Create `tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration3To4Test.kt`:

```kotlin
package com.graydyn.tracker.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {

    private val testDbName = "tracker-migration-3-4-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrackerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate3To4_wipesFoodsDiaryAndSavedMeals_andAddsNewColumns() {
        helper.createDatabase(testDbName, 3).use { v3 ->
            v3.execSQL(
                """
                INSERT INTO foods (name, unitType, caloriesPer100g, proteinPer100g, fatPer100g, carbsPer100g, caloriesPerItem, proteinPerItem, fatPerItem, carbsPerItem)
                VALUES ('Oats', 'GRAM', 379.0, 13.0, 7.0, 67.0, NULL, NULL, NULL, NULL)
                """.trimIndent()
            )
            v3.execSQL(
                """
                INSERT INTO diary_entries (date, mealType, label, sourceType, foodId, unitType, grams, count, calories, protein, fat, carbs)
                VALUES ('2026-05-14', 'BREAKFAST', 'Oats', 'DATABASE', 1, 'GRAM', 50.0, NULL, 190, 6.5, 3.5, 33.5)
                """.trimIndent()
            )
            v3.execSQL(
                """
                INSERT INTO saved_meals (id, name, createdAt) VALUES (1, 'My Oats Meal', 0)
                """.trimIndent()
            )
            v3.execSQL(
                """
                INSERT INTO saved_meal_items (savedMealId, position, label, foodId, unitType, grams, count, calories, protein, fat, carbs)
                VALUES (1, 0, 'Oats', 1, 'GRAM', 50.0, NULL, 190, 6.5, 3.5, 33.5)
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            testDbName,
            4,
            true,
            TrackerDatabase.MIGRATION_3_4
        )

        migrated.query("SELECT COUNT(*) FROM foods").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM diary_entries").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM saved_meal_items").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM saved_meals").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }

        // New columns exist with the expected defaults.
        migrated.query("PRAGMA table_info(foods)").use { c ->
            val cols = mutableListOf<Triple<String, String, String?>>()
            while (c.moveToNext()) {
                val name = c.getString(c.getColumnIndexOrThrow("name"))
                val type = c.getString(c.getColumnIndexOrThrow("type"))
                val dflt = c.getString(c.getColumnIndexOrThrow("dflt_value"))
                cols.add(Triple(name, type, dflt))
            }
            val foundational = cols.firstOrNull { it.first == "foundational" }
            val userAdded = cols.firstOrNull { it.first == "userAdded" }
            assertTrue("foundational column missing", foundational != null)
            assertTrue("userAdded column missing", userAdded != null)
            assertEquals("INTEGER", foundational!!.second)
            assertEquals("INTEGER", userAdded!!.second)
            assertEquals("0", foundational.third)
            assertEquals("0", userAdded.third)
        }
    }
}
```

- [ ] **Step 2: Run the test, confirm it fails**

Run from repo root:

```bash
./gradlew :tracker:connectedAndroidTest --tests "com.graydyn.tracker.data.db.Migration3To4Test"
```

Expected: FAIL. Possible failure modes (any of these is acceptable): "unknown migration", "no migration found for 3 -> 4", or a schema-validation error about the `foundational` / `userAdded` columns. Both indicate the migration code does not yet exist.

- [ ] **Step 3: Add MIGRATION_3_4 and bump version**

Edit `tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt`.

Change the `@Database(... version = 3, ...)` line to `version = 4`.

Inside the `companion object`, after `MIGRATION_2_3`, add:

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // saved_meal_slot_applications cascades from saved_meals (FK ON DELETE CASCADE)
        // but cascade only fires on row delete, not bulk DELETE FROM, so clear it explicitly.
        db.execSQL("DELETE FROM saved_meal_slot_applications")
        db.execSQL("DELETE FROM saved_meal_items")
        db.execSQL("DELETE FROM saved_meals")
        db.execSQL("DELETE FROM diary_entries")
        db.execSQL("DROP TABLE foods")
        db.execSQL(
            """
            CREATE TABLE foods (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                unitType TEXT NOT NULL,
                caloriesPer100g REAL,
                proteinPer100g REAL,
                fatPer100g REAL,
                carbsPer100g REAL,
                caloriesPerItem REAL,
                proteinPerItem REAL,
                fatPerItem REAL,
                carbsPerItem REAL,
                foundational INTEGER NOT NULL DEFAULT 0,
                userAdded INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}
```

In the `getInstance` builder, change `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` to `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`.

- [ ] **Step 4: Run the migration test, confirm it passes**

```bash
./gradlew :tracker:connectedAndroidTest --tests "com.graydyn.tracker.data.db.Migration3To4Test"
```

Expected: PASS.

- [ ] **Step 5: Confirm the full test suite still compiles**

```bash
./gradlew :tracker:assembleDebug
```

Expected: BUILD SUCCESSFUL. Room's schema validation now passes because version 4's expected schema (from the entity, including `@ColumnInfo(defaultValue = "0")`) matches the migration's `CREATE TABLE`.

- [ ] **Step 6: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/model/Food.kt \
        tracker/src/main/java/com/graydyn/tracker/data/db/TrackerDatabase.kt \
        tracker/src/androidTest/java/com/graydyn/tracker/data/db/Migration3To4Test.kt \
        tracker/schemas
git commit -m "feat(tracker): add foundational/userAdded columns + MIGRATION_3_4"
```

(`tracker/schemas` is regenerated by Room's annotation processor at build time. Stage whatever new file(s) appear there for version 4.)

---

### Task 3: Rewrite FoodDao.search with two-tier + match-position ordering

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/db/FoodDao.kt`
- Create: `tracker/src/androidTest/java/com/graydyn/tracker/data/db/FoodDaoSearchTest.kt`

- [ ] **Step 1: Write the failing DAO search test**

Create `tracker/src/androidTest/java/com/graydyn/tracker/data/db/FoodDaoSearchTest.kt`:

```kotlin
package com.graydyn.tracker.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.FoodUnitType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodDaoSearchTest {

    private lateinit var db: TrackerDatabase
    private lateinit var dao: FoodDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TrackerDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.foodDao()
    }

    @After
    fun tearDown() { db.close() }

    private fun mk(
        name: String,
        foundational: Boolean = false,
        userAdded: Boolean = false,
    ) = Food(
        name = name,
        unitType = FoodUnitType.GRAM,
        caloriesPer100g = 100f,
        proteinPer100g = 10f,
        fatPer100g = 1f,
        carbsPer100g = 10f,
        caloriesPerItem = null,
        proteinPerItem = null,
        fatPerItem = null,
        carbsPerItem = null,
        foundational = foundational,
        userAdded = userAdded,
    )

    @Test
    fun search_preferredTierBeatsRest_andPrefixBeatsWordStartBeatsContains() = runBlocking {
        dao.insertAll(listOf(
            // Preferred tier (foundational OR userAdded)
            mk("Chicken, breast, raw", foundational = true),           // prefix, preferred
            mk("Pan-seared chicken thigh", userAdded = true),          // word-start, preferred
            mk("Bachickenized stew", foundational = true),             // contains-only, preferred (no space before "chicken")
            // Rest tier
            mk("Chicken nuggets, frozen"),                              // prefix, rest
            mk("Sandwich, chicken filet"),                              // word-start, rest (", chicken" -> " chicken" pattern hits)
            mk("Beans with chicken bits"),                              // word-start, rest (" chicken" after "with")
            mk("Bachicken brand cereal"),                               // contains-only, rest
            // Should NOT appear at all
            mk("Beef stew"),
        ))

        val results = dao.search("chicken").map { it.name }

        assertEquals(
            listOf(
                // Preferred tier first, in match-position order then name-alpha
                "Chicken, breast, raw",            // preferred, prefix
                "Pan-seared chicken thigh",        // preferred, word-start
                "Bachickenized stew",              // preferred, contains-only
                // Then the rest tier in the same sub-order
                "Chicken nuggets, frozen",         // rest, prefix
                "Beans with chicken bits",         // rest, word-start (alpha before "Sandwich, ...")
                "Sandwich, chicken filet",         // rest, word-start
                "Bachicken brand cereal",          // rest, contains-only
            ),
            results,
        )
    }

    @Test
    fun search_isCaseInsensitive() = runBlocking {
        dao.insertAll(listOf(
            mk("chicken curry"),
            mk("CHICKEN soup"),
            mk("Chicken stock"),
        ))

        val results = dao.search("Chicken").map { it.name }

        // All three should be returned (case-insensitive LIKE),
        // all in the rest tier, all prefix matches, alpha order.
        assertEquals(listOf("chicken curry", "CHICKEN soup", "Chicken stock"), results)
    }

    @Test
    fun search_returnsEmptyWhenNoMatch() = runBlocking {
        dao.insertAll(listOf(mk("Apples"), mk("Bananas")))
        assertEquals(emptyList<Food>(), dao.search("zzz"))
    }
}
```

- [ ] **Step 2: Run the test, confirm it fails**

```bash
./gradlew :tracker:connectedAndroidTest --tests "com.graydyn.tracker.data.db.FoodDaoSearchTest"
```

Expected: FAIL on the ordering assertion in `search_preferredTierBeatsRest_...` because the current DAO sorts only by name.

- [ ] **Step 3: Update FoodDao.search**

Replace the entire contents of `tracker/src/main/java/com/graydyn/tracker/data/db/FoodDao.kt` with:

```kotlin
package com.graydyn.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.graydyn.tracker.data.model.Food

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(foods: List<Food>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(food: Food): Long

    @Query(
        """
        SELECT * FROM foods
        WHERE name LIKE '%' || :query || '%'
        ORDER BY
          CASE WHEN foundational = 1 OR userAdded = 1 THEN 0 ELSE 1 END,
          CASE
            WHEN name LIKE :query || '%' THEN 0
            WHEN name LIKE '% ' || :query || '%' THEN 1
            ELSE 2
          END,
          name COLLATE NOCASE ASC
        LIMIT 50
        """
    )
    suspend fun search(query: String): List<Food>

    @Query("SELECT * FROM foods WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Food?

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int
}
```

- [ ] **Step 4: Run the test, confirm it passes**

```bash
./gradlew :tracker:connectedAndroidTest --tests "com.graydyn.tracker.data.db.FoodDaoSearchTest"
```

Expected: PASS on all three test methods.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/db/FoodDao.kt \
        tracker/src/androidTest/java/com/graydyn/tracker/data/db/FoodDaoSearchTest.kt
git commit -m "feat(tracker): rank foundational/userAdded foods first in search"
```

---

### Task 4: Extend CsvSeeder to parse the optional `foundational` column

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/data/seed/CsvSeeder.kt`
- Create: `tracker/src/test/java/com/graydyn/tracker/data/seed/CsvSeederTest.kt`

This step makes the seeder backward-compatible: it still parses the old 5-column format and now also reads a 6th `foundational` column when the header advertises it. We extract a pure-Kotlin `parseLine` so it can be tested off-device.

- [ ] **Step 1: Write the failing parser test**

Create `tracker/src/test/java/com/graydyn/tracker/data/seed/CsvSeederTest.kt`:

```kotlin
package com.graydyn.tracker.data.seed

import com.graydyn.tracker.data.model.FoodUnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvSeederTest {

    @Test
    fun parseLine_legacyFiveColumnRow_returnsFoodWithFoundationalFalse() {
        val food = CsvSeeder.parseLine(
            line = "\"Hummus, commercial\",229,7.35,17.1,14.9",
            hasFoundationalColumn = false,
        )!!

        assertEquals("Hummus, commercial", food.name)
        assertEquals(FoodUnitType.GRAM, food.unitType)
        assertEquals(229f, food.caloriesPer100g)
        assertEquals(7.35f, food.proteinPer100g)
        assertEquals(17.1f, food.fatPer100g)
        assertEquals(14.9f, food.carbsPer100g)
        assertNull(food.caloriesPerItem)
        assertFalse(food.foundational)
        assertFalse(food.userAdded)
    }

    @Test
    fun parseLine_sixColumnFoundationalOne_marksFoundational() {
        val food = CsvSeeder.parseLine(
            line = "\"Chicken, breast, raw\",165,31,3.6,0,1",
            hasFoundationalColumn = true,
        )!!

        assertEquals("Chicken, breast, raw", food.name)
        assertTrue(food.foundational)
        assertFalse(food.userAdded)
    }

    @Test
    fun parseLine_sixColumnFoundationalZero_doesNotMarkFoundational() {
        val food = CsvSeeder.parseLine(
            line = "\"Some branded item\",100,5,5,5,0",
            hasFoundationalColumn = true,
        )!!

        assertFalse(food.foundational)
    }

    @Test
    fun parseLine_unquotedName_works() {
        val food = CsvSeeder.parseLine(
            line = "Apples,52,0.3,0.2,14",
            hasFoundationalColumn = false,
        )!!
        assertEquals("Apples", food.name)
        assertEquals(52f, food.caloriesPer100g)
    }

    @Test
    fun parseLine_blankLine_returnsNull() {
        assertNull(CsvSeeder.parseLine(line = "", hasFoundationalColumn = false))
    }

    @Test
    fun parseLine_tooFewColumns_returnsNull() {
        assertNull(CsvSeeder.parseLine(line = "Apples,52,0.3", hasFoundationalColumn = false))
    }

    @Test
    fun headerHasFoundationalColumn_detectsBothFormats() {
        assertTrue(
            CsvSeeder.headerHasFoundationalColumn("name,calories_kcal,protein_g,fat_g,carbs_g,foundational")
        )
        assertFalse(
            CsvSeeder.headerHasFoundationalColumn("name,calories_kcal,protein_g,fat_g,carbs_g")
        )
    }
}
```

- [ ] **Step 2: Run the test, confirm it fails**

```bash
./gradlew :tracker:testDebugUnitTest --tests "com.graydyn.tracker.data.seed.CsvSeederTest"
```

Expected: FAIL with "unresolved reference: parseLine" / "unresolved reference: headerHasFoundationalColumn" (currently `private`, no `hasFoundationalColumn` parameter, no header helper).

- [ ] **Step 3: Refactor CsvSeeder**

Replace the entire contents of `tracker/src/main/java/com/graydyn/tracker/data/seed/CsvSeeder.kt` with:

```kotlin
package com.graydyn.tracker.data.seed

import android.content.Context
import android.util.Log
import com.graydyn.tracker.data.db.FoodDao
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.FoodUnitType

object CsvSeeder {
    private const val TAG = "CsvSeeder"

    suspend fun seed(context: Context, dao: FoodDao) {
        val foods = mutableListOf<Food>()
        context.assets.open("nutrition_all.csv").bufferedReader().use { reader ->
            val header = reader.readLine() ?: return
            val hasFoundational = headerHasFoundationalColumn(header)
            reader.forEachLine { line ->
                parseLine(line, hasFoundational)?.let { foods.add(it) }
            }
        }
        dao.insertAll(foods)
        Log.d(TAG, "Seeded ${foods.size} foods")
    }

    internal fun headerHasFoundationalColumn(header: String): Boolean {
        val cols = header.split(",").map { it.trim().lowercase() }
        return cols.lastOrNull() == "foundational"
    }

    /**
     * Parses a CSV line that may have a quoted food name containing commas.
     * Five-column format:  "name",calories,protein,fat,carbs
     * Six-column format:   "name",calories,protein,fat,carbs,foundational  (0 or 1)
     */
    internal fun parseLine(line: String, hasFoundationalColumn: Boolean): Food? {
        if (line.isBlank()) return null

        val name: String
        val rest: String

        if (line.startsWith("\"")) {
            val closeQuote = line.indexOf('"', 1)
            if (closeQuote == -1) return null
            name = line.substring(1, closeQuote)
            rest = if (closeQuote + 1 < line.length && line[closeQuote + 1] == ',') {
                line.substring(closeQuote + 2)
            } else {
                ""
            }
        } else {
            val firstComma = line.indexOf(',')
            if (firstComma == -1) return null
            name = line.substring(0, firstComma)
            rest = line.substring(firstComma + 1)
        }

        val parts = rest.split(",")
        val minCols = if (hasFoundationalColumn) 5 else 4
        if (parts.size < minCols) return null

        val foundational = if (hasFoundationalColumn) {
            parts[4].trim() == "1"
        } else {
            false
        }

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
            carbsPerItem = null,
            foundational = foundational,
            userAdded = false,
        )
    }

    private fun parseNullableFloat(s: String): Float? {
        val v = s.trim().toFloatOrNull() ?: return null
        return if (v < 0f) null else v
    }
}
```

- [ ] **Step 4: Run the test, confirm it passes**

```bash
./gradlew :tracker:testDebugUnitTest --tests "com.graydyn.tracker.data.seed.CsvSeederTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/data/seed/CsvSeeder.kt \
        tracker/src/test/java/com/graydyn/tracker/data/seed/CsvSeederTest.kt
git commit -m "feat(tracker): CsvSeeder reads optional foundational column"
```

---

### Task 5: Build the CSV regeneration script

**Files:**
- Create: `scripts/build_nutrition_all.py`

The script reads `~/Downloads/nutrition_foundation.csv`, then rewrites `tracker/src/main/assets/nutrition_all.csv` in place adding a sixth `foundational` column. It is run manually and the regenerated CSV is committed.

- [ ] **Step 1: Create the script**

Create `scripts/build_nutrition_all.py`:

```python
#!/usr/bin/env python3
"""
Tag rows in tracker/src/main/assets/nutrition_all.csv with a foundational flag.

Reads the curated foundation list from ~/Downloads/nutrition_foundation.csv,
normalizes names (strip + casefold), then rewrites nutrition_all.csv in place
adding a sixth column 'foundational' with value '1' if the row's name is in
the foundation set, else '0'.

If nutrition_all.csv already has the 'foundational' column, it is replaced
based on the current foundation set (idempotent).

Inputs (read-only):
  ~/Downloads/nutrition_foundation.csv     (header: name,calories_kcal,protein_g,fat_g,carbs_g)
  tracker/src/main/assets/nutrition_all.csv

Output:
  tracker/src/main/assets/nutrition_all.csv (overwritten in place; header gains 'foundational')
"""

from __future__ import annotations

import csv
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
FOUNDATION_SRC = Path.home() / "Downloads" / "nutrition_foundation.csv"
ALL_CSV = REPO_ROOT / "tracker" / "src" / "main" / "assets" / "nutrition_all.csv"

BASE_HEADER = ["name", "calories_kcal", "protein_g", "fat_g", "carbs_g"]
NEW_HEADER = BASE_HEADER + ["foundational"]


def load_foundation_names(path: Path) -> set[str]:
    with path.open("r", newline="", encoding="utf-8") as fin:
        reader = csv.reader(fin)
        header = next(reader)
        if header != BASE_HEADER:
            print(
                f"ERROR: unexpected foundation header {header!r}, expected {BASE_HEADER!r}",
                file=sys.stderr,
            )
            sys.exit(1)
        return {row[0].strip().casefold() for row in reader if row}


def main() -> int:
    if not FOUNDATION_SRC.exists():
        print(f"ERROR: foundation CSV not found: {FOUNDATION_SRC}", file=sys.stderr)
        return 1
    if not ALL_CSV.exists():
        print(f"ERROR: nutrition_all.csv not found: {ALL_CSV}", file=sys.stderr)
        return 1

    foundation_names = load_foundation_names(FOUNDATION_SRC)
    print(f"Loaded {len(foundation_names)} foundation names from {FOUNDATION_SRC}")

    with ALL_CSV.open("r", newline="", encoding="utf-8") as fin:
        reader = csv.reader(fin)
        header = next(reader)

        if header == BASE_HEADER:
            has_existing_flag = False
        elif header == NEW_HEADER:
            has_existing_flag = True
        else:
            print(
                f"ERROR: unexpected header in nutrition_all.csv: {header!r}\n"
                f"  expected {BASE_HEADER!r} or {NEW_HEADER!r}",
                file=sys.stderr,
            )
            return 1

        rows = list(reader)

    matched = 0
    out_rows: list[list[str]] = []
    for row in rows:
        if len(row) < 5:
            out_rows.append(row)
            continue
        name = row[0].strip().casefold()
        is_foundational = name in foundation_names
        if is_foundational:
            matched += 1
        if has_existing_flag and len(row) >= 6:
            new_row = row[:5] + ["1" if is_foundational else "0"]
        else:
            new_row = row[:5] + ["1" if is_foundational else "0"]
        out_rows.append(new_row)

    with ALL_CSV.open("w", newline="", encoding="utf-8") as fout:
        writer = csv.writer(fout, quoting=csv.QUOTE_MINIMAL)
        writer.writerow(NEW_HEADER)
        writer.writerows(out_rows)

    unmatched = len(foundation_names) - matched if matched <= len(foundation_names) else 0
    # `matched` counts rows in all CSV that match a foundation name. A foundation
    # name may correspond to multiple rows; treat that as expected.
    print(f"Wrote {len(out_rows)} rows to {ALL_CSV.relative_to(REPO_ROOT)}")
    print(f"Marked foundational=1 on {matched} rows")
    if matched < len(foundation_names):
        print(
            f"WARN: {len(foundation_names) - matched} foundation names had no "
            f"matching row in nutrition_all.csv (possible name drift)"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: Make the script executable**

```bash
chmod +x scripts/build_nutrition_all.py
```

- [ ] **Step 3: Run the script**

```bash
python3 scripts/build_nutrition_all.py
```

Expected: prints "Loaded 363 foundation names from ..." (the CSV has 364 rows, 363 data rows + header) and "Marked foundational=1 on N rows" where N is at least 300. If a `WARN:` line appears with more than ~10 unmatched names, stop and investigate name drift before committing.

- [ ] **Step 4: Sanity-check the regenerated CSV**

```bash
head -1 tracker/src/main/assets/nutrition_all.csv
awk -F',' 'NR>1 && $NF=="1"' tracker/src/main/assets/nutrition_all.csv | wc -l
```

Expected: header ends with `,foundational`; the count matches the "Marked foundational=1 on N rows" line from step 3.

- [ ] **Step 5: Commit**

```bash
git add scripts/build_nutrition_all.py tracker/src/main/assets/nutrition_all.csv
git commit -m "feat(tracker): regen nutrition_all.csv with foundational column"
```

---

### Task 6: Mark runtime-inserted foods as `userAdded = true`

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt`

- [ ] **Step 1: Update SearchViewModel.createFood**

In `tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt`, in the `createFood` function, change both `Food(...)` constructors to pass `userAdded = true` as the last argument.

The GRAM branch becomes:

```kotlin
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
```

The ITEM branch becomes:

```kotlin
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
```

(`foundational` keeps its default `false`.)

- [ ] **Step 2: Update DiaryViewModel.logScannedFood**

In `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt`, in the `logScannedFood` function, do the same: add `userAdded = true` as the last argument to both `Food(...)` constructors inside the `when (unitType)` block (lines ~193-218).

- [ ] **Step 3: Build to confirm both call sites compile**

```bash
./gradlew :tracker:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/search/SearchViewModel.kt \
        tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt
git commit -m "feat(tracker): mark created and scanned foods as userAdded"
```

---

### Task 7: End-to-end verification on device

**Files:** none modified.

- [ ] **Step 1: Install on the test device with the old DB present**

If you already have the app installed on the connected device with the v3 DB, leave it. Otherwise install once at v3 first (skip this if not applicable).

```bash
./gradlew :tracker:installDebug
```

- [ ] **Step 2: Launch the app and watch logs**

```bash
adb logcat -c
adb shell am start -n com.graydyn.tracker/.MainActivity
adb logcat -s CsvSeeder:D *:S
```

Expected within ~10 seconds of launch: `D/CsvSeeder: Seeded N foods` where N is approximately the number of data rows in the regenerated CSV (~469k). The seeder runs because MIGRATION_3_4 dropped `foods`, leaving `dao.count() == 0` on first launch after upgrade. Stop the logcat tail with Ctrl-C once you see the line.

- [ ] **Step 3: Manual smoke test in the UI**

In the app:
1. Tap a meal slot, then the search field. Type "chicken".
2. Confirm the first several results are USDA Foundation-style names ("Chicken, broilers or fryers, ...", "Chicken, breast, raw", etc.) rather than branded products ("KFC Original Recipe Chicken Sandwich").
3. Tap "Create new food" and create a food named "ZZZ Test Item" with arbitrary macros. Save it.
4. Clear the search and type "zzz". Confirm "ZZZ Test Item" appears at the top of the list (preferred tier, prefix match).
5. Type a partial name like "appl" and confirm prefix matches like "Apples, raw" sort above word-start matches like "Pie, apple".

Expected: all four behaviors hold. If any fails, capture the search results and stop — the DAO query or seeder output likely needs investigation before declaring this done.

- [ ] **Step 4: No commit needed**

This task is verification only.

---

## Self-review notes

- Every spec section is covered: data model (Task 1), CSV regeneration (Task 5), seeder change (Task 4), search query (Task 3), insertion paths (Task 6), migration (Task 2), testing (Tasks 2, 3, 4), manual verification (Task 7).
- The `foundational` column default (`DEFAULT 0`) in the migration's `CREATE TABLE` is matched by `@ColumnInfo(defaultValue = "0")` on the entity, so Room's schema validation will pass.
- The `MIGRATION_3_4` deletes `saved_meal_slot_applications` explicitly because SQLite cascade deletes only fire on row deletes, not on bulk `DELETE FROM` (verified by SQLite documentation; safer to clear explicitly).
- The new DAO query relies on SQLite's ASCII-only case-insensitive LIKE. All test data uses ASCII, so this is fine.
- The CSV regeneration script is idempotent: running it twice produces the same output.