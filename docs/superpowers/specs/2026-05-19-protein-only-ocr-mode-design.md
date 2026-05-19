# Protein-Only OCR Mode — Design

Status: draft
Date: 2026-05-19
Modules: `nutritionlib` (primary), `tracker` (call site)

## Goal

When the user has the existing "Only track protein and calories" preference enabled, the OCR scanner should complete as soon as calories and protein are detected, rather than waiting for fat and carbs. This makes scans noticeably faster for users who don't care about the hidden macros.

## Non-goals

- No change to the `TextBlocksInterpreter` parsing logic. Fat and carbs are still captured opportunistically and pass through to the returned `Macros`.
- No change to the persistence layer. `DiaryEntry` continues to store all four macro fields when the scanner happens to populate them.
- No change to the existing protein-only tracker preference (DataStore key, repository, ViewModel wiring). That feature ships separately under `2026-05-13-protein-calories-only-design.md`.
- No new dependency between `nutritionlib` and the tracker's preferences layer. `nutritionlib` remains self-contained.

## Architecture

A new `Intent` extra controls per-launch scanner behavior:

- Key: `NutritionReaderActivity.EXTRA_PROTEIN_ONLY` (String constant, value `"com.graydyn.nutritionlib.extra.PROTEIN_ONLY"` — namespaced to avoid collisions if this library is consumed by another app).
- Type: `Boolean`. Absent or `false` preserves current behavior.

The tracker reads its existing `proteinOnly` `StateFlow` on `DiaryViewModel` and writes the extra into the scan `Intent`. The activity reads the extra in `onCreate`, stores it in a field, and uses it in three places.

```
tracker DiaryScreen ── reads proteinOnly StateFlow
        │
        └── Intent(EXTRA_PROTEIN_ONLY = proteinOnly) ──► NutritionReaderActivity
                                                               │
                                                               ├── Macros.isComplete(proteinOnly)
                                                               ├── isCalorieConsistent  (skipped if proteinOnly)
                                                               └── statusFat/statusCarbs visibility
```

## Behavior in `nutritionlib` when `proteinOnly = true`

### Completion gate

`Macros.isComplete()` becomes `isComplete(proteinOnly: Boolean = false)`:

```kotlin
fun isComplete(proteinOnly: Boolean = false): Boolean {
    if (proteinOnly) return calories != -1 && protein != -1
    return calories != -1 && fat != -1 && protein != -1 && carbs != -1
}
```

The default parameter preserves all existing callers.

### Calorie consistency check

`isCalorieConsistent()` requires fat, carbs, and protein to validate the calorie total. In protein-only mode it is bypassed:

```kotlin
if (macros.isComplete(proteinOnly)) {
    if (proteinOnly || isCalorieConsistent(macros)) {
        returnResult(macros)
    } else {
        showValidationMessage("Validation failed, rescanning...")
        macros = Macros()
    }
}
```

Trade-off: a misread calories or protein value will no longer be caught by cross-checking. Acceptable because no meaningful two-macro consistency check exists, and a partial check would produce false rejections.

### Status UI

In `onCreate`, after `setContentView`, if `proteinOnly` is true:

```kotlin
viewBinding.statusFat.visibility = View.GONE
viewBinding.statusCarbs.visibility = View.GONE
```

The `bind(viewBinding.statusFat, ...)` and `bind(viewBinding.statusCarbs, ...)` calls in `updateProgressUI` continue to execute and set text — this is harmless on a `GONE` view, and avoids branching the bind loop.

### Parsing pipeline

`TextBlocksInterpreter.read()` is **unchanged**. Fat and carbs lines are still detected and written into the returned `Macros`. The tracker maps any non-`-1` values into `DiaryEntry.fat` / `DiaryEntry.carbs`, where they remain available if the user later toggles protein-only off.

## Behavior in `tracker`

### `DiaryScreen.kt`

Inside the `DiaryScreen` composable, collect the existing flow:

```kotlin
val proteinOnly by viewModel.proteinOnly.collectAsState()
```

(Already added by the prior protein-only spec; reuse it.)

In `launchScan`, build the intent with the extra:

```kotlin
val intent = Intent(context, NutritionReaderActivity::class.java).apply {
    putExtra(NutritionReaderActivity.EXTRA_PROTEIN_ONLY, proteinOnly)
}
scanLauncher.launch(intent)
```

No changes to `DiaryViewModel.onScanResult` or `logScannedEntry`. The returned `Macros` may contain real fat/carbs values; those are stored as before. UI suppression of fat/carbs in the tracker is already handled by the existing protein-only feature.

## Default and reversibility

- The extra defaults to `false` if absent. Any caller that doesn't set it (other apps consuming `nutritionlib`, tests) gets the existing four-macro behavior.
- The mode is read once per scan launch, not observed. Toggling the tracker preference mid-scan has no effect on the in-flight scan; the next launch picks it up. This matches the user's mental model: each scan is a discrete operation.
- No persisted state in `nutritionlib`. Removing the extra restores prior behavior with no migration.

## Files touched

**Modify in `nutritionlib`:**
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt` — add `proteinOnly` parameter to `isComplete`.
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/NutritionReaderActivity.kt` — read `EXTRA_PROTEIN_ONLY`, gate completion and validation, hide fat/carbs status views, add companion constant.

**Modify in `tracker`:**
- `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt` — set `EXTRA_PROTEIN_ONLY` on the scan Intent.

**Unchanged:**
- `nutritionlib/.../TextBlocksInterpreter.kt`
- `nutritionlib/src/main/res/layout/activity_nutrition_reader.xml`
- All tracker DataStore plumbing, ViewModels, and UI for the protein-only toggle (`2026-05-13-protein-calories-only-design.md` covers those).

## Testing

The `nutritionlib` and `tracker` modules have no existing UI test infrastructure. Verification is manual:

1. With the tracker's "Only track protein and calories" toggle OFF, scan a label. Confirm scan still waits for all four macros and the calorie consistency check still runs.
2. Toggle ON in Goals. Scan a label. Confirm:
   - Only Calories and Protein status indicators are visible during scan.
   - The activity returns as soon as both are detected (fat and carbs status lines never appear).
   - The validation "rescanning" message does not appear.
3. After a protein-only scan, open the resulting diary entry's underlying row (via a quick SQL check or by toggling protein-only off in Goals): confirm fat and carbs columns may contain real values if they happened to be visible to OCR, or be null otherwise.

A small unit test for `Macros.isComplete(proteinOnly = true)` is worth adding to `nutritionlib/src/test/` since it's pure logic:

```kotlin
@Test fun `isComplete proteinOnly true ignores fat and carbs`() {
    val m = Macros(calories = 200, fat = -1, protein = 12, carbs = -1)
    assertTrue(m.isComplete(proteinOnly = true))
    assertFalse(m.isComplete(proteinOnly = false))
}
```

## Out of scope (explicit)

- No removal of fat/carbs columns from `DiaryEntry` or `Food`.
- No change to `TextBlocksInterpreter`'s detection logic or contamination rules.
- No change to the `isCalorieConsistent` algorithm for the normal (four-macro) path.
- No alternative consistency check for protein-only mode.
- No persistence of `proteinOnly` inside `nutritionlib`. The mode lives only in the launching Intent.
