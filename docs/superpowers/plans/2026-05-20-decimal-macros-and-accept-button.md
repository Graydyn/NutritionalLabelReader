# Decimal Macros + Accept Button Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** (1) Migrate four `Macros` fields from `Int` to `Float` so the OCR pipeline captures decimal values like `Fat 0.4g` instead of silently rejecting them; (2) add an "Accept" button to the scan screen so users can finalize a partial scan when one macro can't be OCR'd.

**Architecture:** The Float migration is one atomic change spanning `Macros`, `MacroDetection`, the parser regex/plausibility/sentinels, the activity's status display and consistency check, plus a tracker-side `seed(Float)` overload in `ScannedFoodDialog`. The Accept button is a thin addition: one `<Button>` in the activity layout plus a one-line click handler that reuses the existing `returnResult(macros)` path and deliberately skips the calorie-consistency check.

**Tech Stack:** Kotlin, Android `JSONObject` (handles Float via its `double` overload, no API change in `OcrPassLogger`), JUnit 4 (`testImplementation(libs.junit)` already wired in `:nutritionlib`), `kotlin.math.abs` for the Float `Math.abs` equivalent.

**Spec:** `docs/superpowers/specs/2026-05-20-decimal-macros-and-accept-button-design.md`.

**Working modules:** `nutritionlib/` (primary), `tracker/` (one overload added in `ScannedFoodDialog`).

---

## File Structure

**Modify in `nutritionlib`:**
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt` — `calories`/`fat`/`protein`/`carbs` become `Float` (sentinel `-1f`); `gramsPerServing` stays `Int`; `isComplete()` body uses `-1f`.
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/OcrPassData.kt` — `MacroDetection.value: Int → Float`.
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/TextBlocksInterpreter.kt` — macro number regex accepts decimals (`\d+(?:\.\d+)?`), parsing via `toFloatOrNull()`, `isPlausible` ranges become Float, the four "already found" sentinels compare against `-1f`, `MacroDetection(value = number)` ships Float, `readTextLines` visibility upgraded from `private` to `internal` so the same-module test source set can exercise it.
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/NutritionReaderActivity.kt` — `bind()` becomes `Float`, new `formatMacro(Float)` helper renders whole numbers without `.0`, `isCalorieConsistent` uses Float math (`0.20f` literal, `kotlin.math.abs`), and (Task 2) wires the new Accept button.
- `nutritionlib/src/main/res/layout/activity_nutrition_reader.xml` — (Task 2) `<Button android:id="@+id/acceptButton" …/>`.
- `nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt` — every literal switches to Float, plus a new `noArgConstructor_setsAllMacrosToMinusOneFloat` test.
- `nutritionlib/src/test/java/com/graydyn/nutritionlib/TextBlocksInterpreterTest.kt` — five new tests for the macro detector that the migration unlocks (decimal fat, integer fat regression, dimensionless calories, decimal protein + carbs, implausible large fat).

**Modify in `tracker`:**
- `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt` — add `seed(value: Float): String` overload next to the existing `seed(value: Int)`. The four lines `seed(macros.calories)` / `seed(macros.protein)` / `seed(macros.fat)` / `seed(macros.carbs)` automatically pick up the Float overload; `seed(macros.gramsPerServing)` keeps using the Int overload because `gramsPerServing` stays `Int`.

**Unchanged (verified by inspection):**
- `OcrPassLogger.kt` — `JSONObject.put(String, double)` accepts the now-Float values via Kotlin → Java widening; output JSON contains decimal points instead of integers, which is the desired behaviour.
- `tracker/.../ui/diary/DiaryViewModel.kt` — `onScanResult(macros: Macros)` stores the `Macros` opaquely; no field access in the tracker outside `ScannedFoodDialog`.
- `tracker/.../ui/diary/DiaryScreen.kt` — passes `scanInProgress` to the dialog by reference.

**Note on testing**

JUnit 4 is wired in `:nutritionlib` via `testImplementation(libs.junit)`. The existing `MacrosTest.kt` has 5 tests, `TextBlocksInterpreterTest.kt` has 12 (all on `detectGramsPerServing`). After this plan, `:nutritionlib` should have 6 + 17 = 23 unit tests. JUnit 4's `assertEquals(float expected, float actual)` is deprecated; use `assertEquals(float expected, float actual, float delta)` with `delta = 0f` for exact equality on the `-1f` sentinel and `delta = 0.001f` for decimal-value assertions. UI changes (Accept button, status formatting) cannot be unit tested without instrumentation infrastructure that doesn't exist in this repo; verification for those is `assembleDebug` plus manual exercise on a device.

---

### Task 1: Float migration with decimal-capture tests (TDD, single atomic commit)

This task migrates the four macro fields to `Float` across both modules in one coherent change. Intermediate compile states are broken — the implementer should perform every edit before re-running the build. TDD here means: write the new tests first (they fail to compile because production types and visibility don't match yet), then update production code, then watch the suite go green.

**Files:**
- Modify: `nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt`
- Modify: `nutritionlib/src/test/java/com/graydyn/nutritionlib/TextBlocksInterpreterTest.kt`
- Modify: `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt`
- Modify: `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/OcrPassData.kt`
- Modify: `nutritionlib/src/main/java/com/graydyn/nutritionlib/TextBlocksInterpreter.kt`
- Modify: `nutritionlib/src/main/java/com/graydyn/nutritionlib/NutritionReaderActivity.kt`
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt`

- [ ] **Step 1: Update `MacrosTest.kt` to expect Float fields**

Replace the entire contents of `nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt` with:

```kotlin
package com.graydyn.nutritionlib

import com.graydyn.nutritionlib.model.Macros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacrosTest {

    @Test
    fun isComplete_default_requiresAllFourMacros() {
        val allFour = Macros(calories = 200f, fat = 5f, protein = 12f, carbs = 25f, gramsPerServing = -1)
        val missingCarbs = Macros(calories = 200f, fat = 5f, protein = 12f, carbs = -1f, gramsPerServing = -1)

        assertTrue(allFour.isComplete())
        assertFalse(missingCarbs.isComplete())
    }

    @Test
    fun isComplete_proteinOnlyTrue_ignoresFatAndCarbs() {
        val caloriesAndProteinOnly = Macros(calories = 200f, fat = -1f, protein = 12f, carbs = -1f, gramsPerServing = -1)
        val missingProtein = Macros(calories = 200f, fat = -1f, protein = -1f, carbs = -1f, gramsPerServing = -1)
        val missingCalories = Macros(calories = -1f, fat = -1f, protein = 12f, carbs = -1f, gramsPerServing = -1)

        assertTrue(caloriesAndProteinOnly.isComplete(proteinOnly = true))
        assertFalse(missingProtein.isComplete(proteinOnly = true))
        assertFalse(missingCalories.isComplete(proteinOnly = true))
    }

    @Test
    fun isComplete_proteinOnlyFalse_matchesDefault() {
        val twoOfFour = Macros(calories = 200f, fat = -1f, protein = 12f, carbs = -1f, gramsPerServing = -1)
        assertFalse(twoOfFour.isComplete(proteinOnly = false))
    }

    @Test
    fun noArgConstructor_setsGramsPerServingToMinusOne() {
        val empty = Macros()
        assertEquals(-1, empty.gramsPerServing)
    }

    @Test
    fun isComplete_ignoresGramsPerServing_inBothModes() {
        val withServingButMissingCarbs = Macros(calories = 200f, fat = 5f, protein = 12f, carbs = -1f, gramsPerServing = 30)
        assertFalse(withServingButMissingCarbs.isComplete())

        val proteinOnlyComplete = Macros(calories = 200f, fat = -1f, protein = 12f, carbs = -1f, gramsPerServing = 30)
        assertTrue(proteinOnlyComplete.isComplete(proteinOnly = true))

        val proteinOnlyMissingProtein = Macros(calories = 200f, fat = -1f, protein = -1f, carbs = -1f, gramsPerServing = 30)
        assertFalse(proteinOnlyMissingProtein.isComplete(proteinOnly = true))
    }

    @Test
    fun noArgConstructor_setsAllMacrosToMinusOneFloat() {
        val empty = Macros()
        assertEquals(-1f, empty.calories, 0f)
        assertEquals(-1f, empty.fat, 0f)
        assertEquals(-1f, empty.protein, 0f)
        assertEquals(-1f, empty.carbs, 0f)
        assertEquals(-1, empty.gramsPerServing)
    }
}
```

(Every `200`/`5`/`12`/`25`/`-1` literal for a macro field now ends with `f`. `gramsPerServing` literals stay `Int`. The new `noArgConstructor_setsAllMacrosToMinusOneFloat` test locks the Float sentinel. `assertEquals(float, float, float)` with `delta = 0f` is JUnit 4's non-deprecated way to assert exact Float equality.)

- [ ] **Step 2: Update `TextBlocksInterpreterTest.kt` to add macro-detection tests**

Open `nutritionlib/src/test/java/com/graydyn/nutritionlib/TextBlocksInterpreterTest.kt`. The file currently has 12 tests on `detectGramsPerServing` and ends with the closing `}` of the class. Add a new import for `Macros` near the top alongside the existing imports:

```kotlin
import com.graydyn.nutritionlib.model.Macros
```

Then, immediately before the file's final closing `}`, add these five new test methods:

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
        assertEquals(-1f, macros.fat, 0f)
    }
```

These reach into `TextBlocksInterpreter.readTextLines`, which is currently `private`. The visibility change happens in Step 7.

- [ ] **Step 3: Run the tests and verify they fail to compile**

```bash
./gradlew :nutritionlib:testDebugUnitTest --tests "com.graydyn.nutritionlib.MacrosTest" --tests "com.graydyn.nutritionlib.TextBlocksInterpreterTest"
```

Expected: build fails. The errors should mention both `Type mismatch: inferred type is Float but Int was expected` (the new Float literals fail against the still-Int data class) and `Cannot access 'readTextLines': it is private in 'Companion'`. Both classes of failure confirm exactly the production changes we are about to make.

- [ ] **Step 4: Update `Macros.kt` (Int → Float on the four macro fields)**

Replace the entire contents of `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt` with:

```kotlin
package com.graydyn.nutritionlib.model

import java.io.Serializable

data class Macros(
    var calories: Float,
    var fat: Float,
    var protein: Float,
    var carbs: Float,
    var gramsPerServing: Int
) : Serializable {

    constructor() : this(-1f, -1f, -1f, -1f, -1)

    fun isComplete(proteinOnly: Boolean = false): Boolean {
        if (proteinOnly) return calories != -1f && protein != -1f
        return calories != -1f && fat != -1f && protein != -1f && carbs != -1f
    }
}
```

`gramsPerServing` stays `Int` because serving sizes on real labels are always whole grams. The Float fields keep the same `-1` sentinel idea, just with the `f` suffix.

- [ ] **Step 5: Update `OcrPassData.kt` (MacroDetection.value → Float)**

Replace the entire contents of `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/OcrPassData.kt` with:

```kotlin
package com.graydyn.nutritionlib.model

import android.graphics.Rect

data class RawOcrLine(val text: String, val boundingBox: Rect?)

data class MacroDetection(val macro: String, val value: Float, val fromLine: String)

data class OcrPassData(
    val rawLines: List<RawOcrLine>,       // all lines sorted top-to-bottom before row grouping
    val rowGroups: List<List<String>>,    // each spatial row as a list of element texts
    val rowTexts: List<String>,           // joined row strings fed to macro detection
    val detections: List<MacroDetection>, // macros newly found this frame (empty if none)
    val accumulatedMacros: Macros         // snapshot of Macros state after this frame
)
```

The previously-`detectGramsPerServing` callsite in `TextBlocksInterpreter` constructs `MacroDetection(value = <Int>)` where the gram value is rounded to `Int`; with `value` now Float we'll fix that call site in Step 6 by writing `value = value.toFloat()` at that one spot. The other macro `MacroDetection` callsite already passes a Float-typed local after Step 6.

- [ ] **Step 6: Update `TextBlocksInterpreter.kt` (regex, parsing, plausibility, sentinels, visibility, callsite Float coercion for gramsPerServing detection)**

Open `nutritionlib/src/main/java/com/graydyn/nutritionlib/TextBlocksInterpreter.kt`. Make these targeted edits.

**Edit A — make `readTextLines` `internal`:**

Locate line 85:

```kotlin
        private fun readTextLines(lines: List<String>, macros: Macros): Pair<Macros, List<MacroDetection>> {
```

Replace with:

```kotlin
        internal fun readTextLines(lines: List<String>, macros: Macros): Pair<Macros, List<MacroDetection>> {
```

**Edit B — coerce the gramsPerServing detection's Int value to Float for the new MacroDetection model:**

Locate the existing block (lines 95-100):

```kotlin
                if (macros.gramsPerServing == -1) {
                    detectGramsPerServing(lineNoPercent)?.let { value ->
                        macros.gramsPerServing = value
                        detections.add(MacroDetection(macro = "gramsPerServing", value = value, fromLine = line))
                    }
                }
```

Replace with:

```kotlin
                if (macros.gramsPerServing == -1) {
                    detectGramsPerServing(lineNoPercent)?.let { value ->
                        macros.gramsPerServing = value
                        detections.add(MacroDetection(macro = "gramsPerServing", value = value.toFloat(), fromLine = line))
                    }
                }
```

(`gramsPerServing` is still `Int`, but `MacroDetection.value` is now `Float`. The `.toFloat()` is the explicit widening.)

**Edit C — update the four "already found" sentinel comparisons:**

Locate lines 121-127:

```kotlin
                val alreadyFound = when (macro) {
                    "calories" -> macros.calories != -1
                    "fat"      -> macros.fat != -1
                    "protein"  -> macros.protein != -1
                    "carbs"    -> macros.carbs != -1
                    else       -> false
                }
```

Replace with:

```kotlin
                val alreadyFound = when (macro) {
                    "calories" -> macros.calories != -1f
                    "fat"      -> macros.fat != -1f
                    "protein"  -> macros.protein != -1f
                    "carbs"    -> macros.carbs != -1f
                    else       -> false
                }
```

**Edit D — change the macro-number regex to accept decimals and parse as Float:**

Locate lines 141-142:

```kotlin
                val match = Regex("""\d+""").find(lineNoPercent, keywordEnd) ?: continue
                val number = match.value.toIntOrNull() ?: continue
```

Replace with:

```kotlin
                val match = Regex("""\d+(?:\.\d+)?""").find(lineNoPercent, keywordEnd) ?: continue
                val number = match.value.toFloatOrNull() ?: continue
```

(Same `(?:\.\d+)?` decimal-optional group used by `SERVING_REGEX`. The unit-check guard at lines 149-156 keeps working unchanged: with the new regex matching `"0.4"` from `"0.4g"`, the character at `match.range.last + 1` is `g`, so the row is accepted instead of being rejected as it was when the old regex matched only `"0"`.)

**Edit E — update `isPlausible` to take Float and use Float ranges:**

Locate the existing function (lines 183-187):

```kotlin
        private fun isPlausible(macro: String, value: Int): Boolean = when (macro) {
            "calories" -> value in 0..5000
            "fat", "carbs", "protein" -> value in 0..200
            else -> true
        }
```

Replace with:

```kotlin
        private fun isPlausible(macro: String, value: Float): Boolean = when (macro) {
            "calories" -> value in 0f..5000f
            "fat", "carbs", "protein" -> value in 0f..200f
            else -> true
        }
```

The body of `readTextLines` between Edit D and Edit E (specifically `when (macro) { "calories" -> macros.calories = number ... }` at lines 166-170, and the `MacroDetection(macro = macro, value = number, fromLine = line)` callsite at line 172) needs **no edit**: `number` is now `Float`, and both `macros.X` targets and `MacroDetection.value` are now `Float`. They line up by Kotlin's type inference.

- [ ] **Step 7: Update `NutritionReaderActivity.kt` (bind Float, formatMacro helper, isCalorieConsistent Float math, abs import)**

Open `nutritionlib/src/main/java/com/graydyn/nutritionlib/NutritionReaderActivity.kt`. Make these edits.

**Edit A — add `kotlin.math.abs` import:**

Locate the import block (lines 1-33). Add a new import at the end of the block, after `import java.util.concurrent.Executors`:

```kotlin
import kotlin.math.abs
```

**Edit B — update `bind()` and add `formatMacro` helper:**

Locate the existing `updateProgressUI` function (lines 143-159):

```kotlin
    private fun updateProgressUI(macros: Macros) {
        runOnUiThread {
            fun bind(view: TextView, label: String, value: Int) {
                if (value == -1) {
                    view.text = "○  $label"
                    view.setTextColor(Color.parseColor("#80FFFFFF"))
                } else {
                    view.text = "✓  $label: $value"
                    view.setTextColor(Color.parseColor("#FF4CAF50"))
                }
            }
            bind(viewBinding.statusCalories, "Calories", macros.calories)
            bind(viewBinding.statusFat,      "Fat",      macros.fat)
            bind(viewBinding.statusCarbs,    "Carbs",    macros.carbs)
            bind(viewBinding.statusProtein,  "Protein",  macros.protein)
        }
    }
```

Replace with:

```kotlin
    private fun updateProgressUI(macros: Macros) {
        runOnUiThread {
            fun bind(view: TextView, label: String, value: Float) {
                if (value == -1f) {
                    view.text = "○  $label"
                    view.setTextColor(Color.parseColor("#80FFFFFF"))
                } else {
                    view.text = "✓  $label: ${formatMacro(value)}"
                    view.setTextColor(Color.parseColor("#FF4CAF50"))
                }
            }
            bind(viewBinding.statusCalories, "Calories", macros.calories)
            bind(viewBinding.statusFat,      "Fat",      macros.fat)
            bind(viewBinding.statusCarbs,    "Carbs",    macros.carbs)
            bind(viewBinding.statusProtein,  "Protein",  macros.protein)
        }
    }

    private fun formatMacro(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString()
        else "%.1f".format(value)
```

(The helper renders `5.0f` as `"5"`, `0.4f` as `"0.4"`, `157.0f` as `"157"`, and `12.34f` as `"12.3"`. Whole-number labels keep their old terse display; only decimals show a decimal point.)

**Edit C — update `isCalorieConsistent` to Float math:**

Locate the function (lines 174-183):

```kotlin
    private fun isCalorieConsistent(macros: Macros): Boolean {
        val expected = macros.fat * 9 + macros.carbs * 4 + macros.protein * 4
        val allowance = macros.calories * 0.20
        val passes = Math.abs(macros.calories - expected) <= allowance
        if (!passes) {
            Log.d(TAG, "Calorie check failed: detected=${macros.calories}, " +
                    "calculated=$expected (fat=${macros.fat}*9 + carbs=${macros.carbs}*4 + protein=${macros.protein}*4)")
        }
        return passes
    }
```

Replace with:

```kotlin
    private fun isCalorieConsistent(macros: Macros): Boolean {
        val expected = macros.fat * 9 + macros.carbs * 4 + macros.protein * 4
        val allowance = macros.calories * 0.20f
        val passes = abs(macros.calories - expected) <= allowance
        if (!passes) {
            Log.d(TAG, "Calorie check failed: detected=${macros.calories}, " +
                    "calculated=$expected (fat=${macros.fat}*9 + carbs=${macros.carbs}*4 + protein=${macros.protein}*4)")
        }
        return passes
    }
```

(`0.20` → `0.20f` keeps the multiplication result Float instead of widening to Double. `Math.abs(Float)` → `kotlin.math.abs(Float)` via the import added in Edit A.)

- [ ] **Step 8: Update `ScannedFoodDialog.kt` (add seed(Float) overload)**

Open `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt`. Locate line 55:

```kotlin
    fun seed(value: Int): String = if (value == -1) "" else value.toString()
```

Replace with:

```kotlin
    fun seed(value: Int): String = if (value == -1) "" else value.toString()
    fun seed(value: Float): String {
        if (value == -1f) return ""
        return if (value == value.toInt().toFloat()) value.toInt().toString()
        else "%.1f".format(value)
    }
```

The existing `seed(Int)` is retained because `seed(macros.gramsPerServing)` on line 60 still passes an `Int`. The four lines above it (`seed(macros.calories)`, `seed(macros.protein)`, `seed(macros.fat)`, `seed(macros.carbs)`) now pass `Float` and pick up the new overload automatically.

- [ ] **Step 9: Run the unit tests and verify they all pass**

```bash
./gradlew :nutritionlib:testDebugUnitTest --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`. The JUnit XML at `nutritionlib/build/test-results/testDebugUnitTest/` should show 23 tests passing:

- `TEST-com.graydyn.nutritionlib.MacrosTest.xml` — `tests="6"` (5 existing + 1 new `noArgConstructor_setsAllMacrosToMinusOneFloat`).
- `TEST-com.graydyn.nutritionlib.TextBlocksInterpreterTest.xml` — `tests="17"` (12 existing `detectGramsPerServing` tests + 5 new `readTextLines` macro-detection tests).

- [ ] **Step 10: Build the tracker module**

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. This confirms the `seed(Float)` overload satisfies the `seed(macros.calories)` callsites and that no other tracker code depends on the four macro fields being `Int`.

- [ ] **Step 11: Commit**

```bash
git add nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt \
        nutritionlib/src/main/java/com/graydyn/nutritionlib/model/OcrPassData.kt \
        nutritionlib/src/main/java/com/graydyn/nutritionlib/TextBlocksInterpreter.kt \
        nutritionlib/src/main/java/com/graydyn/nutritionlib/NutritionReaderActivity.kt \
        nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt \
        nutritionlib/src/test/java/com/graydyn/nutritionlib/TextBlocksInterpreterTest.kt \
        tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt
git commit -m "feat(nutritionlib): store macros as Float to capture decimal values"
```

---

### Task 2: Add the Accept button to the scan screen

**Files:**
- Modify: `nutritionlib/src/main/res/layout/activity_nutrition_reader.xml`
- Modify: `nutritionlib/src/main/java/com/graydyn/nutritionlib/NutritionReaderActivity.kt`

- [ ] **Step 1: Add the Accept button to the layout**

Open `nutritionlib/src/main/res/layout/activity_nutrition_reader.xml`. The `statusPanel` LinearLayout currently ends at line 80 with `</LinearLayout>`. Locate the `statusMessage` TextView (lines 71-78) and the closing `</LinearLayout>` on line 80. Insert the new Button between them so the panel layout reads (showing context for placement, lines 71-80 of the new file):

```xml
        <TextView
            android:id="@+id/statusMessage"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:textColor="#FFFFAA00"
            android:textSize="13sp"
            android:visibility="gone" />

        <Button
            android:id="@+id/acceptButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="Accept"
            android:textAllCaps="false"
            android:backgroundTint="#4CAF50" />

    </LinearLayout>
```

(Match the existing indentation pattern: each child of `statusPanel` uses 8-space indentation. The `backgroundTint` is the same green as the "macro detected" check-mark color, making the button visually consistent with the rest of the status panel.)

- [ ] **Step 2: Wire the Accept button click handler**

Open `nutritionlib/src/main/java/com/graydyn/nutritionlib/NutritionReaderActivity.kt`. Locate the existing block in `onCreate` (lines 50-56):

```kotlin
        proteinOnly = intent.getBooleanExtra(EXTRA_PROTEIN_ONLY, false)
        if (proteinOnly) {
            // updateProgressUI still calls bind() on statusFat/statusCarbs even when GONE;
            // writing into a GONE view is harmless and avoids branching the bind loop.
            viewBinding.statusFat.visibility = View.GONE
            viewBinding.statusCarbs.visibility = View.GONE
        }
```

Insert this block immediately after the closing `}` of the `if (proteinOnly)`:

```kotlin

        // Accept button: user opts into the current partial macros. Skips the calorie
        // consistency check because the user has explicitly vetted the result; the
        // downstream dialog gives them a chance to edit any field before saving.
        viewBinding.acceptButton.setOnClickListener {
            returnResult(macros)
        }
```

So the resulting onCreate region looks like:

```kotlin
        proteinOnly = intent.getBooleanExtra(EXTRA_PROTEIN_ONLY, false)
        if (proteinOnly) {
            // updateProgressUI still calls bind() on statusFat/statusCarbs even when GONE;
            // writing into a GONE view is harmless and avoids branching the bind loop.
            viewBinding.statusFat.visibility = View.GONE
            viewBinding.statusCarbs.visibility = View.GONE
        }

        // Accept button: user opts into the current partial macros. Skips the calorie
        // consistency check because the user has explicitly vetted the result; the
        // downstream dialog gives them a chance to edit any field before saving.
        viewBinding.acceptButton.setOnClickListener {
            returnResult(macros)
        }

        if (OCR_LOGGING_ENABLED) ocrPassLogger = OcrPassLogger(this)
```

`returnResult(macros)` is the existing private function at line 89; it sets the Intent extra and finishes the activity, which is exactly what the four-macro completion path also does.

- [ ] **Step 3: Build `:nutritionlib`**

```bash
./gradlew :nutritionlib:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. The auto-generated `ActivityNutritionReaderBinding` regenerates from the layout XML and exposes `acceptButton` as a `Button` field, which the click handler references.

- [ ] **Step 4: Build the tracker module**

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. (`:tracker` doesn't know about the button directly but consumes the AAR; this confirms the full build chain still works.)

- [ ] **Step 5: Manual verification on a device**

Run from the repo root after attaching a device/emulator:

```bash
./gradlew :tracker:installDebug
```

1. Open the Tracker app → Diary → tap the camera icon on any meal.
2. Confirm the green "Accept" button is visible at the bottom of the dark status panel.
3. Without aiming the camera at any label, tap "Accept". Confirm the `ScannedFoodDialog` opens with every macro field blank.
4. Dismiss the dialog. Reopen the scanner.
5. Aim at a real nutrition label. Watch the status rows fill in (✓ Calories: 157, ✓ Fat: 5, etc.). Before all four fill in, tap "Accept".
6. Confirm the dialog opens with the partially-filled macros pre-populated and any undetected field blank.
7. Open a label with `Fat 0.4g`. Confirm the status row shows `✓ Fat: 0.4`. Either let the scan complete naturally or tap Accept; confirm the dialog's Fat field reads `0.4`.

- [ ] **Step 6: Commit**

```bash
git add nutritionlib/src/main/res/layout/activity_nutrition_reader.xml \
        nutritionlib/src/main/java/com/graydyn/nutritionlib/NutritionReaderActivity.kt
git commit -m "feat(nutritionlib): add Accept button to scan screen for partial results"
```

---

## Self-Review Checklist (already run)

**Spec coverage**
- "Decimal regex `\d+(?:\.\d+)?` + Float parsing" → Task 1 Step 6 Edit D ✓
- "`Macros` four macro fields Int → Float, sentinel -1f" → Task 1 Step 4 ✓
- "`gramsPerServing` stays Int" → Task 1 Step 4 keeps it Int ✓
- "`isComplete()` body uses -1f" → Task 1 Step 4 ✓
- "`isPlausible` ranges become Float" → Task 1 Step 6 Edit E ✓
- "`MacroDetection.value` Int → Float" → Task 1 Step 5 ✓
- "Four `alreadyFound` sentinels compare against -1f" → Task 1 Step 6 Edit C ✓
- "`bind()` Float signature + `formatMacro` helper" → Task 1 Step 7 Edit B ✓
- "`isCalorieConsistent` Float math (`0.20f`, `kotlin.math.abs`)" → Task 1 Step 7 Edit C ✓
- "`seed(Float)` overload, keep `seed(Int)` for gramsPerServing" → Task 1 Step 8 ✓
- "Expose `readTextLines` as `internal` for testing" → Task 1 Step 6 Edit A ✓
- "Five new TextBlocksInterpreterTest macro-detection tests" → Task 1 Step 2 ✓
- "Updated MacrosTest literals + new sentinel test" → Task 1 Step 1 ✓
- "Accept button in layout (green, full-width, label 'Accept')" → Task 2 Step 1 ✓
- "Accept click handler calls `returnResult(macros)` and skips consistency check" → Task 2 Step 2 ✓
- "Spec promises `OcrPassLogger` needs no source change" → no Task touches that file, build success in Step 9/10 implicitly validates ✓
- "Spec promises `DiaryViewModel.logScannedEntry` needs no change" → confirmed by inspection that no such method exists; only `onScanResult(macros: Macros)` exists, opaque to fields, no change needed ✓

**Placeholder scan** — no "TBD" / "TODO" / "implement later"; every code block is complete and copy-pasteable.

**Type / name consistency**
- Sentinel value `-1f` is used identically in production (`Macros.kt`, `TextBlocksInterpreter.kt`) and tests (`MacrosTest.kt`, `TextBlocksInterpreterTest.kt`). ✓
- `Float` sentinel uses `0f` delta on `assertEquals` for exact equality (`MacrosTest.kt:53-56`, `TextBlocksInterpreterTest.kt` for `implausiblyLargeFat_isRejected`); decimal-value assertions use `0.001f` delta. ✓
- `formatMacro` defined in `NutritionReaderActivity`, the dialog's `seed(Float)` uses the same display logic inline. Two copies because they sit in different modules and the helper is only one line; centralizing it isn't worth a new shared class. ✓
- `acceptButton` is the XML id (Task 2 Step 1) and the property accessed via `viewBinding.acceptButton` (Task 2 Step 2). Identical spelling. ✓
- `readTextLines` visibility change in Task 1 Step 6 Edit A matches the test usage in Task 1 Step 2. ✓
