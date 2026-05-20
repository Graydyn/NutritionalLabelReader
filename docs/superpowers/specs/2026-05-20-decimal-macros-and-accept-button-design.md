# Decimal Macros + Accept Button — Design

Status: draft
Date: 2026-05-20
Modules: `nutritionlib` (primary), `tracker` (downstream Float coercion ripple)

## Goal

1. **Decimal macros bug fix:** Detect and store macro values like `Fat 0.4g`. Today the integer-only regex `\d+` matches the leading `0` and is then rejected by the "no gram unit immediately after number" guard, leaving fat unset. Real low-fat labels routinely show sub-1g fat values; the scanner must capture them.
2. **Accept button:** Add an OK ("Accept") button to the scan screen so the user can finalize a partial scan and fill in any undetected fields by hand in `ScannedFoodDialog`. Useful when one macro is impossible to OCR (glare, label damage, font weight) and waiting for the four-macro completion gate is futile.

## Non-goals

- No change to the contamination guards (`fibre`, `saturated`, `omega`, etc.) in `detectMacro`.
- No new Material theming or styling of the scan screen beyond the button itself.
- No "are you sure?" confirmation when Accept is pressed with all-sentinel macros — the resulting blank dialog is self-explanatory.
- No persistence of decimal precision beyond one decimal place in display. Float storage is full-precision; the *display* helper rounds for readability.
- No change to `Macros.gramsPerServing` — it remains `Int` (serving sizes on labels are always whole grams).

## Architecture

Two changes ship together because they overlap on the same files:

```
┌───────────────────────────────────────────────────────────────────────┐
│  Macros.kt                                                            │
│   calories: Int → Float    -1   → -1f                                 │
│   fat:      Int → Float    -1   → -1f                                 │
│   protein:  Int → Float    -1   → -1f                                 │
│   carbs:    Int → Float    -1   → -1f                                 │
│   gramsPerServing: Int        (unchanged)                             │
│   isComplete()                (body uses != -1f, otherwise identical) │
└─────────────┬─────────────────────────────────────────────────────────┘
              │
              ▼ Float ripples to:
┌───────────────────────────────────────────────────────────────────────┐
│  TextBlocksInterpreter.kt                                             │
│   regex \d+ → \d+(?:\.\d+)?                                           │
│   parsing toIntOrNull() → toFloatOrNull()                             │
│   isPlausible ranges: Float                                           │
│   MacroDetection(value = Float)                                       │
│                                                                       │
│  NutritionReaderActivity.kt                                           │
│   bind(value: Int) → bind(value: Float)                               │
│   isCalorieConsistent: Float math (Math.abs → kotlin.math.abs)        │
│   formatMacro(Float): String                                          │
│   acceptButton.setOnClickListener { returnResult(macros) }   [NEW]    │
│                                                                       │
│  OcrPassData.kt                                                       │
│   MacroDetection.value: Int → Float                                   │
│                                                                       │
│  OcrPassLogger.kt                                                     │
│   accumulatedMacros JSON puts: Float values pass through JSONObject   │
│   (Number overload, no JSON shape change beyond decimal points)       │
│                                                                       │
│  activity_nutrition_reader.xml                                        │
│   <Button android:id="@+id/acceptButton" .../>             [NEW]      │
└─────────────┬─────────────────────────────────────────────────────────┘
              │
              ▼ tracker side
┌───────────────────────────────────────────────────────────────────────┐
│  DiaryViewModel.logScannedEntry                                       │
│   protein = if (macros.protein != -1f) macros.protein else null       │
│   (no more .toFloat() calls — the field is already Float)             │
│                                                                       │
│  ScannedFoodDialog.kt                                                 │
│   seed(value: Int): String           — kept, used by gramsPerServing  │
│   seed(value: Float): String          — NEW, used by macros           │
│   seed(-1f) → ""                                                      │
│   seed(157.0f) → "157"                                                │
│   seed(0.4f)  → "0.4"                                                 │
└───────────────────────────────────────────────────────────────────────┘
```

## Decimal handling details

### Regex change

`TextBlocksInterpreter.readTextLines` currently runs:

```kotlin
val match = Regex("""\d+""").find(lineNoPercent, keywordEnd) ?: continue
val number = match.value.toIntOrNull() ?: continue
```

Becomes:

```kotlin
val match = Regex("""\d+(?:\.\d+)?""").find(lineNoPercent, keywordEnd) ?: continue
val number = match.value.toFloatOrNull() ?: continue
```

### Unit-check guard fixes itself

The existing guard rejects rows where the character immediately after the matched number is not `g`/`9`. With the old regex matching only `"0"` from `"0.4g"`, the next char was `"."` → rejected. With the new regex matching `"0.4"`, the next char is `"g"` → accepted. No change needed to the guard logic itself.

### Plausibility ranges

```kotlin
private fun isPlausible(macro: String, value: Float): Boolean = when (macro) {
    "calories" -> value in 0f..5000f
    "fat", "carbs", "protein" -> value in 0f..200f
    else -> true
}
```

The lower bound stays at `0f`. A label showing `0g` for fat is a real value, distinct from "not yet detected" (`-1f`).

### Calorie consistency check

```kotlin
private fun isCalorieConsistent(macros: Macros): Boolean {
    val expected = macros.fat * 9 + macros.carbs * 4 + macros.protein * 4
    val allowance = macros.calories * 0.20f
    val passes = kotlin.math.abs(macros.calories - expected) <= allowance
    if (!passes) {
        Log.d(TAG, "Calorie check failed: detected=${macros.calories}, " +
                "calculated=$expected (fat=${macros.fat}*9 + carbs=${macros.carbs}*4 + protein=${macros.protein}*4)")
    }
    return passes
}
```

The math works on Float without coercions. The `0.20` literal becomes `0.20f` for type consistency. `Math.abs` is swapped for `kotlin.math.abs` to take the Float overload cleanly.

### Status display formatting

A small helper renders Float macros without trailing `.0`:

```kotlin
private fun formatMacro(value: Float): String {
    return if (value == value.toInt().toFloat()) value.toInt().toString()
    else "%.1f".format(value)
}
```

Used by the `bind()` function inside `updateProgressUI`:

```kotlin
fun bind(view: TextView, label: String, value: Float) {
    if (value == -1f) {
        view.text = "○  $label"
        view.setTextColor(Color.parseColor("#80FFFFFF"))
    } else {
        view.text = "✓  $label: ${formatMacro(value)}"
        view.setTextColor(Color.parseColor("#FF4CAF50"))
    }
}
```

The tracker side reuses the same idea via `seed(Float)`:

```kotlin
fun seed(value: Float): String {
    if (value == -1f) return ""
    return if (value == value.toInt().toFloat()) value.toInt().toString()
    else "%.1f".format(value)
}
```

## Accept button details

### Layout addition

In `activity_nutrition_reader.xml`, inside `statusPanel` and below `statusMessage`:

```xml
        <Button
            android:id="@+id/acceptButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="Accept"
            android:textAllCaps="false"
            android:backgroundTint="#4CAF50" />
```

Green to match the "macro detected" check-mark color. `textAllCaps="false"` keeps the label readable (`Accept` rather than `ACCEPT`).

### Activity wiring

In `NutritionReaderActivity.onCreate`, after the existing `proteinOnly`/visibility block:

```kotlin
        viewBinding.acceptButton.setOnClickListener {
            returnResult(macros)
        }
```

This reuses the existing `returnResult` function (which finalizes the Intent extra and finishes the activity). No new validation path; we deliberately skip `isCalorieConsistent` because the user has explicitly opted in to the partial result and the dialog gives them a chance to edit anything that looks wrong.

### Edge case: pressed before any frame is OCR'd

`macros` starts as `Macros()` with all-sentinel values. The activity returns immediately; `DiaryViewModel.onScanResult` opens `ScannedFoodDialog` with every macro field blank (`seed(-1f) → ""`). The user types in everything by hand. No crash, no special-case needed.

### Edge case: pressed after partial detection

`macros` has some fields populated, others still `-1f`. The detected fields show as pre-filled in the dialog; the undetected fields stay blank. The user fills in the gaps.

## Tracker integration

### `DiaryViewModel.logScannedEntry`

Today:

```kotlin
calories = if (macros.calories != -1) macros.calories else null,
protein = if (macros.protein != -1) macros.protein.toFloat() else null,
fat = if (macros.fat != -1) macros.fat.toFloat() else null,
carbs = if (macros.carbs != -1) macros.carbs.toFloat() else null
```

After:

```kotlin
calories = if (macros.calories != -1f) macros.calories.toInt() else null,
protein = if (macros.protein != -1f) macros.protein else null,
fat = if (macros.fat != -1f) macros.fat else null,
carbs = if (macros.carbs != -1f) macros.carbs else null
```

The `DiaryEntry.calories` field is `Int?` (read from existing schema, see `tracker/.../model/DiaryEntry.kt`). Calorie labels are always whole numbers, so `.toInt()` is safe and lossless in practice. The other three macros are `Float?` in `DiaryEntry`, so the cast disappears.

### `ScannedFoodDialog`

The dialog has a `seed(value: Int)` helper used today for all four macros plus `gramsPerServing`. The change:

- Add an overload `seed(value: Float)` for the four macro fields.
- Keep `seed(value: Int)` for `gramsPerServing`.

```kotlin
fun seed(value: Int): String = if (value == -1) "" else value.toString()
fun seed(value: Float): String {
    if (value == -1f) return ""
    return if (value == value.toInt().toFloat()) value.toInt().toString()
    else "%.1f".format(value)
}
```

The four `var calories = remember { mutableStateOf(seed(macros.calories)) }` etc. lines pick up the Float overload automatically because `macros.calories` is now Float. The `var gramsPerServing = remember { mutableStateOf(seed(macros.gramsPerServing)) }` line still resolves to the Int overload.

## Testing

### `MacrosTest.kt`

Update every `Macros(...)` literal: `calories = 200` → `calories = 200f`, etc. Add one test asserting the new Float sentinel:

```kotlin
@Test
fun noArgConstructor_setsAllMacrosToMinusOneFloat() {
    val empty = Macros()
    assertEquals(-1f, empty.calories, 0f)
    assertEquals(-1f, empty.fat, 0f)
    assertEquals(-1f, empty.protein, 0f)
    assertEquals(-1f, empty.carbs, 0f)
    assertEquals(-1, empty.gramsPerServing)
}
```

(The `delta` argument is required on JUnit 4's `assertEquals(float, float, float)` because the no-delta Float overload is deprecated. A delta of `0f` enforces exact equality, which is what we want for sentinel comparisons.)

### `TextBlocksInterpreterTest.kt`

The existing 12 tests cover `detectGramsPerServing` only. Add a new section for macro detection — exposed via the existing `read(List<Text.TextBlock>, Macros)` is hard to test because it needs MLKit types. Instead, expose `readTextLines` as `internal` (it's currently `private`) for testability. The new tests feed plain `List<String>` rows and assert specific `Macros` field outcomes:

```kotlin
@Test
fun decimalFat_isCaptured() {
    val (macros, _) = TextBlocksInterpreter.readTextLines(listOf("Fat 0.4g"), Macros())
    assertEquals(0.4f, macros.fat, 0.001f)
}

@Test
fun integerFat_stillWorks() {
    val (macros, _) = TextBlocksInterpreter.readTextLines(listOf("Fat 5g"), Macros())
    assertEquals(5f, macros.fat, 0.001f)
}

@Test
fun caloriesWithoutGramUnit_stillWorks() {
    val (macros, _) = TextBlocksInterpreter.readTextLines(listOf("Calories 157"), Macros())
    assertEquals(157f, macros.calories, 0.001f)
}

@Test
fun decimalProteinAndCarbs_areCaptured() {
    val (macros, _) = TextBlocksInterpreter.readTextLines(
        listOf("Protein 2.5g", "Carbohydrate 13.7g"),
        Macros()
    )
    assertEquals(2.5f, macros.protein, 0.001f)
    assertEquals(13.7f, macros.carbs, 0.001f)
}

@Test
fun implausiblyLargeFat_isRejected() {
    val (macros, _) = TextBlocksInterpreter.readTextLines(listOf("Fat 249g"), Macros())
    assertEquals(-1f, macros.fat)
}
```

(Plus existing `detectGramsPerServing` tests, unchanged.)

The Accept button cannot be unit-tested without instrumentation infrastructure that doesn't exist in this repo. Manual verification only.

## Manual verification

After the feature ships:

1. Scan a label with `Fat 0.4g`. Confirm the status row shows `✓ Fat: 0.4`. Confirm `ScannedFoodDialog` prefills "0.4" in the Fat field.
2. Scan a label with integer-only macros. Confirm no display regression — e.g., `Fat 5g` shows as `✓ Fat: 5` (not `✓ Fat: 5.0`).
3. Hold the camera at a label with obscured carbs. Confirm calories/fat/protein update normally, carbs stays `○ Carbs`. Tap Accept. Confirm the dialog opens with calories/fat/protein pre-filled and carbs blank.
4. Open the scanner, immediately tap Accept without aiming. Confirm the dialog opens with all four macro fields blank.
5. Confirm the Accept button is visible against the camera preview (green on dark background should be obvious).

## Files touched

**Modify in `nutritionlib`:**
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt` — Int → Float.
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/OcrPassData.kt` — `MacroDetection.value: Int → Float`.
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/TextBlocksInterpreter.kt` — regex, parsing, isPlausible, expose `readTextLines` as `internal`.
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/NutritionReaderActivity.kt` — `bind()` Float signature, `formatMacro` helper, `isCalorieConsistent` Float math, Accept button click handler.
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/OcrPassLogger.kt` — verify JSON still serializes correctly (no source change expected; `JSONObject.put(String, Number)` overload accepts Float).
- `nutritionlib/src/main/res/layout/activity_nutrition_reader.xml` — add `acceptButton`.
- `nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt` — Float literals + new sentinel test.
- `nutritionlib/src/test/java/com/graydyn/nutritionlib/TextBlocksInterpreterTest.kt` — add decimal/integer macro detection tests.

**Modify in `tracker`:**
- `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryViewModel.kt` — `-1` → `-1f`, drop `.toFloat()`, add `.toInt()` for calories field.
- `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt` — add `seed(Float)` overload.

**Unchanged:**
- The protein-only Intent extra plumbing (no semantic change — `proteinOnly` still skips the consistency check; Accept now also skips it for partial results).
- DataStore / Room schema (`DiaryEntry.calories` is already `Int?`, the other macros already `Float?`).
- `ScannedFoodDialog`'s downstream save callback signature (Float comes out the other end either way).
