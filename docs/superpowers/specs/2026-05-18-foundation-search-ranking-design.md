# Foundation + User-Added Search Ranking

Date: 2026-05-18

## Problem

`nutrition_all.csv` grew from a curated set into a ~469k-row merge of USDA FoodData Central (foundation, SR Legacy, survey/FNDDS, and branded). `FoodDao.search` does a plain `LIKE '%query%' ORDER BY name ASC LIMIT 50`, so common queries like "chicken" surface obscure branded rows ahead of the curated foundation foods, and any food the user has added by hand gets buried the same way.

We want two groups of foods to surface first in search:

1. Foods that came from the USDA Foundation Foods subset (the 364-row `nutrition_foundation.csv` that was one input to the merged `nutrition_all.csv`).
2. Foods added by the user at runtime, whether through the "Create new food" dialog or auto-created from a nutrition-label scan.

## Goals

- Preferred-vs-rest tiering in search results: foundation OR user-added rows always rank above everything else for any query.
- Within each tier, smarter ranking by where the query appears in the name: prefix > word-start > contains, then alphabetical.
- A repeatable script for regenerating `nutrition_all.csv` with the new column, so the foundation tag stays in sync if the source CSVs are refreshed.
- Existing dev installs migrate cleanly. The app is pre-release; wiping local data is acceptable.

## Non-goals

- Full-text search (FTS5). Plain `LIKE` with the existing 300 ms debounce in `SearchViewModel` is fast enough for a 469k-row local SQLite table on-device; we will not introduce FTS as part of this change.
- Preserving any existing user-added foods, diary entries, or saved meals across the migration.
- Distinguishing SR Legacy vs survey vs branded inside the "rest" tier.
- Re-ranking results that come back from anything other than `FoodDao.search` (e.g., the meal-picker's own queries are unchanged).

## Data model

Two new columns on the `foods` table:

| column | type | default | meaning |
|---|---|---|---|
| `foundational` | `INTEGER NOT NULL` | `0` | `1` if the row's name was present in `nutrition_foundation.csv` at seed time, else `0`. Set by the seeder; never written at runtime. |
| `userAdded` | `INTEGER NOT NULL` | `0` | `1` for any row inserted via `SearchViewModel.createFood` or `DiaryViewModel.logScannedFood`. Default `0` for everything the seeder loads. |

Both are modeled in Kotlin as `Boolean` on the `Food` entity:

```kotlin
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
```

The `@ColumnInfo(defaultValue = "0")` annotations are required so Room's generated schema matches the `DEFAULT 0` clauses used in the migration's `CREATE TABLE`; without them, schema validation on app startup fails with a schema-mismatch error.

Rationale for two booleans rather than a single `FoodOrigin` enum:

- The user request specifically asked for a `foundational` column. A boolean maps directly.
- Overlap is allowed and meaningful: a user can manually create a food whose name happens to be in the foundation set, and both bits should stay independently true.
- Search only needs a single OR of the two bits; the query stays simple.

## CSV regeneration

New script: `scripts/build_nutrition_all.py`.

Inputs (read-only, kept where they live on the user's machine):

- `~/Downloads/nutrition_foundation.csv` (header: `name,calories_kcal,protein_g,fat_g,carbs_g`, 364 rows).
- `tracker/src/main/assets/nutrition_all.csv` (current merged CSV, same five columns).

Output:

- `tracker/src/main/assets/nutrition_all.csv` overwritten in place, with a new header `name,calories_kcal,protein_g,fat_g,carbs_g,foundational` and a sixth cell on every data row that is `1` if the row's `name` (after `strip()` and `casefold()`) is in the foundation set, else `0`.

Matching is on the exact normalized name. USDA names are highly structured (`"Chicken, broilers or fryers, breast, meat only, raw"`), so a name-equality match is reliable. No fuzzy matching. If two rows in `nutrition_all.csv` happen to share a name (e.g., a foundation entry whose name is also present in another source), both rows get `foundational = 1`; that is the desired behavior.

The script is independent of `scripts/clean_nutrition_csv.py`. That script's job is the Atwater kcal cleanup; mixing the two would conflate responsibilities. If the Atwater cleanup needs to run after a rebuild, it is run separately and adapted to preserve the new column (out of scope here unless we see it break).

The script is invoked manually, and the regenerated `nutrition_all.csv` is committed to the repo.

## Seeder change

`CsvSeeder.parseLine` currently expects exactly five columns and rejects anything else. Change it to:

- Read the header on entry and detect whether a `foundational` column is present (last column, name `foundational`).
- When present, parse the sixth cell as `0`/`1` and pass `foundational = (cell == "1")` into the `Food` constructor.
- When absent (older snapshot, tests, etc.), pass `foundational = false`. This keeps the seeder forward- and backward-compatible.

`userAdded` is always `false` at seed time.

## Insertion paths set `userAdded = true`

Two call sites today construct a `Food` and insert it:

1. `SearchViewModel.createFood` (the "Create new food" dialog).
2. `DiaryViewModel.logScannedFood` (auto-creation from a nutrition-label scan).

Both construct their `Food(...)` with `userAdded = true`. No other code path inserts into `foods` (verified by grep for `foodRepo.add` / `foodDao.insert`).

## Search query

Replace `FoodDao.search` with the two-tier + match-position ordering:

```kotlin
@Query("""
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
""")
suspend fun search(query: String): List<Food>
```

Behavior:

- Tier 0 (preferred) = `foundational = 1 OR userAdded = 1`; tier 1 = everything else.
- Within a tier, rows whose `name` starts with the query rank above rows where the query starts a later word (boundary = single ASCII space, matching USDA's `", "` delimiter convention), which rank above rows where the query only appears mid-word.
- Final sort is `name COLLATE NOCASE`, so `"chicken"` and `"Chicken"` interleave correctly.
- LIKE in SQLite is case-insensitive for ASCII by default, so the predicate stays case-insensitive without explicit `LOWER()`.
- The `LIMIT 50` is unchanged.

The 300 ms debounce in `SearchViewModel.searchResults` already throttles per-keystroke load.

`FoodRepository.search` and `SearchViewModel` need no API changes.

## Migration

Bump `TrackerDatabase` version from `3` to `4` and add `MIGRATION_3_4` that wipes anything referencing `foodId`:

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // saved_meal_slot_applications cascades from saved_meals (FK ON DELETE CASCADE).
        db.execSQL("DELETE FROM saved_meal_items")
        db.execSQL("DELETE FROM saved_meals")
        db.execSQL("DELETE FROM diary_entries")
        db.execSQL("DROP TABLE foods")
        db.execSQL("""
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
        """.trimIndent())
    }
}
```

The seeder runs on next launch in the same place it does today (the "is `foods` empty?" check) and refills the table from the new CSV.

Why wipe rather than `ALTER TABLE foods ADD COLUMN foundational INTEGER NOT NULL DEFAULT 0` + a name-based backfill pass:

- The user accepted wipe-and-reseed. There is no production data to preserve.
- Backfilling foundational by re-reading the foundation CSV at migration time duplicates the seeder's parsing and adds a slow one-time scan over hundreds of thousands of rows during DB upgrade.
- Wipe avoids dangling `foodId` references in `diary_entries` and `saved_meal_items`.

## Testing

Unit/instrumented coverage to add (or extend) alongside this change:

- `FoodDaoTest`: insert foods spanning all four cells of the (`foundational`, `userAdded`) matrix plus rows with the query as prefix / word-start / contains. Assert the result ordering matches the spec for a query that hits all of them.
- `CsvSeederTest`: seed from a fixture CSV with and without the `foundational` column; confirm `Food.foundational` is set correctly and `userAdded` is always `false`.
- `SearchViewModelTest` (if it exists): confirm `createFood` produces a `Food` with `userAdded = true`.
- A `Migration3To4Test` in `tracker/src/androidTest/` mirroring the existing `Migration2To3Test` style: seed v3 with one food, one diary entry, one saved meal + item; run migration; confirm `foods`/`diary_entries`/`saved_meal_items`/`saved_meals` are all empty and the new `foods` schema has the two new columns.

## Risks and open follow-ups

- USDA name format drift: if a future foundation CSV uses subtly different punctuation/capitalization than the merged `nutrition_all.csv` for the same food, the equality match will miss. Mitigation: the script logs an unmatched-foundation-names count so we can spot drift early. (Numeric audit only — no names dumped.)
- Word-start boundary is single ASCII space. Names like `"Beef,chicken"` (no space after comma) would not match the word-start tier; they would fall through to the contains tier. Acceptable: USDA's actual convention is `", "`, and the contains tier still surfaces them.
- The merged `nutrition_all.csv` will grow by one byte per row plus header (~470 KB added on a ~21 MB file). Negligible.