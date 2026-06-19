# Prioritize previously-logged foods in search

**Date:** 2026-06-19

## Goal

When a user searches for a food, foods they have logged before should appear
above foods they have never logged. Example: searching "chicken" should surface
"chicken thigh" (logged before) above "chicken breast" (foundational, never
logged).

## Signal: "logged before"

A food counts as "logged before" if its `lastAmount` column is non-null. This
column is already written on every log via `FoodDao.updateLastAmount` (called
from `SearchViewModel.logEntry`). Using it requires no schema change and no join.

## Change

The only change is the `ORDER BY` clause of `FoodDao.search`. A new top-priority
sort key is added; all existing keys are preserved below it.

Sort priority, top to bottom:

1. **Logged before** — `lastAmount IS NOT NULL` (new)
2. **Foundational or user-added** — `foundational = 1 OR userAdded = 1` (existing)
3. **Name-match position** — prefix > word-start > substring (existing)
4. **Alphabetical** — `name COLLATE NOCASE ASC` (existing)

New query:

```sql
SELECT * FROM foods
WHERE name LIKE '%' || :query || '%'
ORDER BY
  CASE WHEN lastAmount IS NOT NULL THEN 0 ELSE 1 END,
  CASE WHEN foundational = 1 OR userAdded = 1 THEN 0 ELSE 1 END,
  CASE
    WHEN name LIKE :query || '%' THEN 0
    WHEN name LIKE '% ' || :query || '%' THEN 1
    ELSE 2
  END,
  name COLLATE NOCASE ASC
LIMIT 50
```

## Non-goals

- No frequency or recency ranking — all logged foods are treated equally and tie
  on key 1, falling back to the existing keys among themselves.
- No DB migration, no model changes, no ViewModel/UI changes.

## Testing

Add an instrumented `FoodDao` test under `androidTest` (matching the existing
`SavedMealDaoTest` in-memory-Room pattern). Seed foods with mixed `lastAmount`
and `foundational` states and assert that, for a shared query, a logged-before
plain food outranks a foundational-but-never-logged food.
