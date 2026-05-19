# Grams Per Serving OCR Detection — Design

Status: draft
Date: 2026-05-19
Modules: `nutritionlib` (primary), `tracker` (one prefill change)

## Goal

When the OCR scanner reads a nutrition label, opportunistically capture the per-serving size in grams from common "Per X (Yg)" / "Pour X (Yg)" label phrasing and prefill the existing `gramsPerServing` field in the tracker's `ScannedFoodDialog`. The user keeps full editing control; OCR is best-effort.

## Non-goals

- Not a required field. `Macros.isComplete()` (in either default or protein-only mode) does not look at it.
- Not a perfect detector. Many real labels list serving size in formats this design intentionally ignores (`Serving size: 30g`, `Per 100g`, ounces). Broadening detection is left to a future iteration if real-world misses warrant it.
- No new UI surface. No fifth status indicator on the scan screen, no "auto-filled" badge in the dialog. The user sees the value pre-typed and either accepts it or overtypes it.
- No persistence layer change. The tracker already stores `gramsPerServing` on saved foods; only the dialog's initial state changes.

## Architecture

A new opportunistic field on `Macros` accumulates the value across OCR frames, matching the existing pattern used for the four macros (and for fat/carbs in protein-only mode). A second detector inside `TextBlocksInterpreter.readTextLines` scans the same row list for the serving-size pattern. Nothing about scan completion changes.

```
camera frame → TextBlocksInterpreter.read()
                  └─ readTextLines()
                       ├─ macro detector (existing)
                       └─ serving-size detector (NEW)
                              └─ macros.gramsPerServing = ...
                                      │
                                      ▼
                       Activity returns Macros via Intent
                                      │
                                      ▼
                ScannedFoodDialog seeds gramsPerServing text field
```

## Data model

`nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt`:

```kotlin
data class Macros(
    var calories: Int,
    var fat: Int,
    var protein: Int,
    var carbs: Int,
    var gramsPerServing: Int
) : Serializable {

    constructor() : this(-1, -1, -1, -1, -1)

    fun isComplete(proteinOnly: Boolean = false): Boolean {
        if (proteinOnly) return calories != -1 && protein != -1
        return calories != -1 && fat != -1 && protein != -1 && carbs != -1
    }
}
```

- `gramsPerServing` follows the same `-1 = not found` convention as the other fields.
- `isComplete()` is unchanged — the new field is never required.
- `Serializable` wire format gains one field; only same-process callers serialize this object (through `Intent.putExtra`), so the format extension is safe.

The constructor signature change is breaking. Three call sites construct `Macros` with positional arguments today:

1. `NutritionReaderActivity.kt` — `private var macros = Macros()` (no-arg, still compiles).
2. `NutritionReaderActivity.kt` — `macros = Macros()` (reset on failed consistency check, no-arg).
3. `MacrosTest.kt` — uses named arguments (`Macros(calories = 200, fat = 5, protein = 12, carbs = 25)`), which become missing-argument errors for the new field.

All `MacrosTest.kt` constructors need a new `gramsPerServing = -1` argument added. No production call sites construct `Macros` with positional or partial args.

## Detection logic

Inside `TextBlocksInterpreter.readTextLines`, after the existing macro detection loop (or inside the same loop — see the implementation note below), each row is also passed through the serving-size detector:

```kotlin
private val SERVING_REGEX = Regex(
    """\b(per|pour)\b\s+.*?\(\s*(\d+(?:\.\d+)?)\s*g\s*\)""",
    RegexOption.IGNORE_CASE
)
```

For each row:

1. If `macros.gramsPerServing != -1`, skip (already captured in a prior frame).
2. Run `SERVING_REGEX.find(line)`. If no match, skip.
3. Parse the captured number as `Double` and convert to `Int` with `Double.roundToInt()` (half-up rounding from Kotlin stdlib).
4. Plausibility filter: must be in `1..2000`. Reject otherwise (silently, like the macro `isPlausible` check).
5. Assign to `macros.gramsPerServing` and record a `MacroDetection` entry for `OcrPassData` logging.

The MacroDetection model currently keys on `macro: String`. The new detector uses `"gramsPerServing"` as its key string.

### Why this regex

- `\b(per|pour)\b` — anchors the match as a whole word so `"performance"`, `"supermarket"`, `"pourquoi"` don't trigger.
- Case-insensitive — labels appear in title case, all caps, or sentence case unpredictably.
- `.*?` non-greedy — when a line has multiple parentheticals, we want the first one after the anchor.
- `(\d+(?:\.\d+)?)\s*g` — accepts integer or decimal grams with optional whitespace before `g`. The single-letter `g` is what nutrition labels use; we are not trying to match `gram`, `grams`, `grammes`.
- The trailing `\s*\)` requires the closing paren. This is the key constraint that distinguishes the serving-size phrase from other "per ... g" text on the label.

### Examples

| Line | Matches? | Captured value |
|---|---|---|
| `Per serving (30g)` | yes | 30 |
| `Per 1 cookie (15 g)` | yes | 15 |
| `Pour 1 portion (28g)` | yes | 28 |
| `PER 2 TBSP (32 g)` | yes | 32 |
| `Per 100g of product (28g)` | yes | 28 (first paren after `per`) |
| `Serving size: 30g` | no | — (no parenthesis, no anchor word) |
| `Per 100g` | no | — (no parenthesis) |
| `Per 1 oz (1/8 cup)` | no | — (no `g` inside paren) |
| `Performance per cycle (15 ops)` | no | — (`g` missing) |

## Activity behavior

`NutritionReaderActivity.kt` requires **no changes** beyond the implicit `Macros` constructor signature change (no-arg constructor still works). The new field rides along on the `Macros` instance through the Intent result.

## Tracker integration

`tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt:60` changes one line.

Before:

```kotlin
var gramsPerServing by remember { mutableStateOf("") }
```

After:

```kotlin
var gramsPerServing by remember { mutableStateOf(seed(macros.gramsPerServing)) }
```

The `seed()` helper at line 55 already maps `-1 → ""` for ints, so this reuses the established pattern. When OCR fails to detect serving size, the field starts blank exactly as before. When detection succeeds, the user sees the value pre-typed and can edit or accept.

No change to the dialog's signature, save callback, or downstream save path. Once typed (or pre-typed) and saved, the value flows through the existing tracker plumbing.

## Testing

`nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt` is updated for the new constructor parameter and the default value:

- Existing tests get a `gramsPerServing = -1` added to each `Macros(...)` literal.
- A new test confirms the no-arg constructor sets `gramsPerServing` to `-1`.
- A new test confirms `isComplete()` ignores `gramsPerServing` in both modes (i.e., a `Macros` instance with `gramsPerServing = 30` but missing some macros is still not complete).

A new test file `nutritionlib/src/test/java/com/graydyn/nutritionlib/TextBlocksInterpreterTest.kt` covers the regex against representative strings. The existing interpreter has no tests; this is the right time to start a unit-test suite for it because the new detector is pure string work with no Android dependencies. Test cases mirror the Examples table above plus a few invariants:

- A row that has already populated `gramsPerServing` in a prior call is not overwritten.
- A row whose captured value is implausible (e.g., `Per serving (3000g)`) does not populate the field.
- A row with a decimal value (`Per serving (27.5g)`) populates as `28` via `Double.roundToInt()`.
- Detection runs independently of macro detection: a row that triggers serving-size detection but not macro detection still yields the serving-size value, and vice versa.

The full TextBlocksInterpreter is not covered by these tests; we add only the new detector and the invariants directly exercised by this feature, to avoid retrofit testing as scope creep.

## Files touched

**Modify in `nutritionlib`:**
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt` — add `gramsPerServing` field, update no-arg constructor.
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/TextBlocksInterpreter.kt` — add `SERVING_REGEX` companion val and the detection pass inside `readTextLines`.
- `nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt` — update existing constructors, add field-default test.

**Create in `nutritionlib`:**
- `nutritionlib/src/test/java/com/graydyn/nutritionlib/TextBlocksInterpreterTest.kt` — regex behavior tests.

**Modify in `tracker`:**
- `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt` — one-line `gramsPerServing` initial-state change.

**Unchanged:**
- `NutritionReaderActivity.kt`, the scan UI layout, the protein-only Intent extra plumbing, all DataStore/ViewModel/Repository code in the tracker.

## Out of scope (explicit)

- No detection of non-parenthetical patterns (`Serving size: 30g`, `Per 100g`).
- No imperial-to-metric conversion (`Per serving (1 oz)`).
- No multi-language support beyond English and French.
- No fractional gram storage on `Macros` (rounded to nearest int).
- No persistence change. The saved food's `gramsPerServing` is whatever the user accepted in the dialog.
- No UI cue distinguishing OCR-prefilled values from user-typed values. We trust the user to glance at the field before saving.
