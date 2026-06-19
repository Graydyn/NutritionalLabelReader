# Last Logged Amount — Design

**Date:** 2026-06-19

## Goal

When a user logs a food from the Add Food search screen, remember the quantity
they entered. The next time they select that food on the search screen, prefill
the quantity field with that last amount. The prefilled value is editable — the
user can accept it or change it before tapping Add.

This removes the most common piece of repeated typing: people tend to log the
same food in the same amount (e.g. always 50g of oats, always 1 banana).

## Scope decisions

- **Single last amount per food.** One number stored on the food, in the food's
  own unit (grams / items / servings, determined by `unitType`). No per-meal-type
  history. Each food has exactly one `unitType`, so the stored amount is always
  unambiguous.
- **Search-screen logging only.** `lastAmount` is updated only when a food is
  logged via `SearchViewModel.logEntry()` (the deliberate single-food log).
  Saved-meal application and copy-meal do **not** touch it — those are bulk
  operations whose quantities are incidental and would pollute the value.
- **Database foods only.** Scanned (non-database) foods have no row to store the
  value on; they are unaffected.

## Data model

Add one nullable column to the `Food` entity (`foods` table):

```kotlin
val lastAmount: Float? = null
```

- Holds the quantity in the food's own unit.
- Nullable. Foods that have never been logged from the search screen keep
  `lastAmount = null`, which produces an empty quantity field (today's behavior).

## Migration

Bump the database version `5 → 6` in `TrackerDatabase` and add `MIGRATION_5_6`:

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE foods ADD COLUMN lastAmount REAL")
    }
}
```

Register it in the `addMigrations(...)` call alongside the existing migrations.

## Write path

In `SearchViewModel.logEntry(date, mealType)`, after the `DiaryEntry` is built
and the qty validated, persist the amount back to the food in the same IO
coroutine that inserts the diary entry:

```kotlin
viewModelScope.launch(Dispatchers.IO) {
    diaryRepo.insert(entry)
    foodRepo.updateLastAmount(food.id, qty)
}
```

This requires:

- **`FoodDao.updateLastAmount`** — a targeted update (cheaper than a full-row
  `@Update`, and avoids stale-row clobbering):
  ```kotlin
  @Query("UPDATE foods SET lastAmount = :amount WHERE id = :id")
  suspend fun updateLastAmount(id: Long, amount: Float)
  ```
- **`FoodRepository.updateLastAmount`** — passthrough:
  ```kotlin
  suspend fun updateLastAmount(id: Long, amount: Float) =
      dao.updateLastAmount(id, amount)
  ```

`qty` is already validated to be `> 0f` earlier in `logEntry`, so only valid
amounts are ever stored.

## Read path

In `SearchViewModel.onSelectFood(food)`, replace the unconditional
`_quantity.value = ""` with a prefill from `lastAmount`:

```kotlin
fun onSelectFood(food: Food) {
    _selectedFood.value = food
    _quantity.value = food.lastAmount
        ?.takeIf { it > 0f }
        ?.let { formatAmount(it) }
        ?: ""
}
```

`formatAmount` strips a trailing `.0` so whole numbers display the way a user
would have typed them, while decimals are preserved:

- `100.0f` → `"100"`
- `1.5f`   → `"1.5"`

Implementation:

```kotlin
private fun formatAmount(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString()
    else value.toString()
```

No UI change is required. `SearchScreen` already passes
`quantity = if (food.id == selectedFood?.id) quantity else ""` into the result
card, and the card's quantity `OutlinedTextField` renders whatever string the
view model exposes. A non-empty prefill simply shows up in that field.

## Testing (TDD)

Write tests before implementation.

1. **`Migration5To6Test`** (androidTest) — mirrors `Migration4To5Test`:
   - Create a v5 database, insert a food row.
   - Run `MIGRATION_5_6` via `runMigrationsAndValidate`.
   - Assert the existing row is preserved and the new `lastAmount` column exists
     and is null on the old row.

2. **`FoodDao` androidTest** — `updateLastAmount` writes the value:
   - Insert a food, call `updateLastAmount(id, 50f)`, assert `getById(id)?.lastAmount == 50f`.

3. **`SearchViewModel` test:**
   - After `logEntry`, the logged food's persisted `lastAmount` equals the entered
     quantity.
   - `onSelectFood` on a food with `lastAmount = 100f` sets `quantity` to `"100"`;
     with `lastAmount = 1.5f` sets it to `"1.5"`; with `lastAmount = null` sets it
     to `""`.

## Out of scope (YAGNI)

- Per-meal-type last amounts.
- Updating `lastAmount` from saved-meal application or copy-meal.
- Any handling for scanned (non-database) foods.
