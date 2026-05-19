# Grams Per Serving OCR Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opportunistic fifth `Macros` field, `gramsPerServing`, populated by a new "per/pour ... (Xg)" detector inside `TextBlocksInterpreter`, and prefill the existing `gramsPerServing` text field in the tracker's `ScannedFoodDialog` from that value.

**Architecture:** `Macros` gains a `gramsPerServing: Int = -1` field that never gates `isComplete()`. `TextBlocksInterpreter.readTextLines` runs a new pure-string helper `detectGramsPerServing(line)` on each row before macro detection; the helper matches a case-insensitive regex anchored on the whole word `per` or `pour` followed by a parenthetical containing a number and a `g` unit. The value is stored once (subsequent frames don't overwrite). The tracker reuses its existing `seed()` helper to convert `-1` → `""` when initializing the dialog's state.

**Tech Stack:** Kotlin, JUnit 4 (already wired in `:nutritionlib`), `kotlin.math.roundToInt` for half-up rounding of decimal grams. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-05-19-grams-per-serving-ocr-design.md`.

**Working modules:** `nutritionlib/` (data class, detector, tests) and `tracker/` (one prefill line in `ScannedFoodDialog`).

---

## File Structure

**Create**
- `nutritionlib/src/test/java/com/graydyn/nutritionlib/TextBlocksInterpreterTest.kt` — unit tests for the new `detectGramsPerServing` helper (English, French, decimal, plausibility, anchor word-boundary, non-match cases).

**Modify**
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt` — add the `gramsPerServing: Int` field and update the no-arg constructor.
- `nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt` — update each `Macros(...)` literal with `gramsPerServing = -1`; add a default-value test confirming `Macros().gramsPerServing == -1`; add an `isComplete_ignoresGramsPerServing` test.
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/TextBlocksInterpreter.kt` — add the `SERVING_REGEX` companion val, the `internal fun detectGramsPerServing(line: String): Int?` companion helper, and a one-block wire-up inside `readTextLines` that runs the helper at the top of the per-row loop.
- `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt` — one-line change at line 60 to seed `gramsPerServing` from `macros.gramsPerServing` via the existing `seed()` helper.

**Unchanged**
- `NutritionReaderActivity.kt` — the no-arg `Macros()` constructor still compiles; the activity never reads the new field directly.
- The scan UI layout (`activity_nutrition_reader.xml`) — no fifth status indicator.
- Tracker DataStore/repository/ViewModel layer.

**Note on testing**

`:nutritionlib` has JUnit 4 wired via `testImplementation(libs.junit)` in `nutritionlib/build.gradle.kts`. The existing test file `MacrosTest.kt` is the convention. The new helper is pure-string and lives in a `companion object`, so it is directly callable from a host JUnit test as `TextBlocksInterpreter.detectGramsPerServing(...)` (made `internal` so the same Gradle module's test source set can see it).

The tracker UI prefill cannot be unit tested without Compose UI test infrastructure that doesn't exist in this repo. Verification for Task 3 is build + manual exercise on a device.

---

### Task 1: Add `gramsPerServing` to `Macros` (TDD)

**Files:**
- Modify: `nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt`
- Modify: `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt`

- [ ] **Step 1: Update `MacrosTest.kt` to expect the new field**

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
        val allFour = Macros(calories = 200, fat = 5, protein = 12, carbs = 25, gramsPerServing = -1)
        val missingCarbs = Macros(calories = 200, fat = 5, protein = 12, carbs = -1, gramsPerServing = -1)

        assertTrue(allFour.isComplete())
        assertFalse(missingCarbs.isComplete())
    }

    @Test
    fun isComplete_proteinOnlyTrue_ignoresFatAndCarbs() {
        val caloriesAndProteinOnly = Macros(calories = 200, fat = -1, protein = 12, carbs = -1, gramsPerServing = -1)
        val missingProtein = Macros(calories = 200, fat = -1, protein = -1, carbs = -1, gramsPerServing = -1)
        val missingCalories = Macros(calories = -1, fat = -1, protein = 12, carbs = -1, gramsPerServing = -1)

        assertTrue(caloriesAndProteinOnly.isComplete(proteinOnly = true))
        assertFalse(missingProtein.isComplete(proteinOnly = true))
        assertFalse(missingCalories.isComplete(proteinOnly = true))
    }

    @Test
    fun isComplete_proteinOnlyFalse_matchesDefault() {
        val twoOfFour = Macros(calories = 200, fat = -1, protein = 12, carbs = -1, gramsPerServing = -1)
        assertFalse(twoOfFour.isComplete(proteinOnly = false))
    }

    @Test
    fun noArgConstructor_setsGramsPerServingToMinusOne() {
        val empty = Macros()
        assertEquals(-1, empty.gramsPerServing)
    }

    @Test
    fun isComplete_ignoresGramsPerServing_inBothModes() {
        val withServingButMissingCarbs = Macros(calories = 200, fat = 5, protein = 12, carbs = -1, gramsPerServing = 30)
        assertFalse(withServingButMissingCarbs.isComplete())

        val proteinOnlyComplete = Macros(calories = 200, fat = -1, protein = 12, carbs = -1, gramsPerServing = 30)
        assertTrue(proteinOnlyComplete.isComplete(proteinOnly = true))

        val proteinOnlyMissingProtein = Macros(calories = 200, fat = -1, protein = -1, carbs = -1, gramsPerServing = 30)
        assertFalse(proteinOnlyMissingProtein.isComplete(proteinOnly = true))
    }
}
```

This file does three things at once: (a) updates the three existing `Macros(...)` literals to include the new `gramsPerServing = -1` named argument, (b) adds the `noArgConstructor_setsGramsPerServingToMinusOne` test for the new field's default, (c) adds the `isComplete_ignoresGramsPerServing_inBothModes` test to lock in that the new field never gates completion.

- [ ] **Step 2: Run the tests and verify they fail to compile**

From the repo root:

```bash
./gradlew :nutritionlib:testDebugUnitTest --tests "com.graydyn.nutritionlib.MacrosTest"
```

Expected: build fails with Kotlin errors along the lines of `No value passed for parameter 'gramsPerServing'` (named argument refers to a parameter that doesn't exist yet) or `Unresolved reference: gramsPerServing`. Both confirm the production type is the one to change.

- [ ] **Step 3: Update `Macros.kt` to add the new field**

Replace the entire contents of `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt` with:

```kotlin
package com.graydyn.nutritionlib.model

import java.io.Serializable

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

The new field rides at the end of the constructor; `isComplete()` is unchanged, preserving the opportunistic semantics promised by the spec.

- [ ] **Step 4: Run the tests and verify they pass**

```bash
./gradlew :nutritionlib:testDebugUnitTest --tests "com.graydyn.nutritionlib.MacrosTest"
```

Expected: `BUILD SUCCESSFUL`, with five test methods passing: `isComplete_default_requiresAllFourMacros`, `isComplete_proteinOnlyTrue_ignoresFatAndCarbs`, `isComplete_proteinOnlyFalse_matchesDefault`, `noArgConstructor_setsGramsPerServingToMinusOne`, `isComplete_ignoresGramsPerServing_inBothModes`.

- [ ] **Step 5: Confirm the wider `:nutritionlib` module still compiles**

```bash
./gradlew :nutritionlib:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. The two `Macros()` no-arg constructor call sites in `NutritionReaderActivity.kt` continue to compile because the secondary constructor still takes no arguments.

- [ ] **Step 6: Confirm the tracker still compiles**

The tracker references `Macros` in `DiaryViewModel.logScannedEntry` (it constructs a `DiaryEntry` from fields of an existing `Macros` instance — no constructor calls). It also passes `Macros` into `ScannedFoodDialog` (also reads fields, no construction). So the tracker should still compile against the new five-field `Macros`. Verify:

```bash
./gradlew :tracker:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt
git commit -m "feat(nutritionlib): add gramsPerServing field to Macros"
```

---

### Task 2: Add `detectGramsPerServing` helper and wire it into the OCR loop (TDD)

**Files:**
- Create: `nutritionlib/src/test/java/com/graydyn/nutritionlib/TextBlocksInterpreterTest.kt`
- Modify: `nutritionlib/src/main/java/com/graydyn/nutritionlib/TextBlocksInterpreter.kt`

- [ ] **Step 1: Create the failing test file**

Create `nutritionlib/src/test/java/com/graydyn/nutritionlib/TextBlocksInterpreterTest.kt` with these exact contents:

```kotlin
package com.graydyn.nutritionlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextBlocksInterpreterTest {

    @Test
    fun englishParenthetical_isDetected() {
        assertEquals(30, TextBlocksInterpreter.detectGramsPerServing("Per serving (30g)"))
    }

    @Test
    fun englishWithSpaceBeforeG_isDetected() {
        assertEquals(15, TextBlocksInterpreter.detectGramsPerServing("Per 1 cookie (15 g)"))
    }

    @Test
    fun frenchParenthetical_isDetected() {
        assertEquals(28, TextBlocksInterpreter.detectGramsPerServing("Pour 1 portion (28g)"))
    }

    @Test
    fun upperCase_isDetected() {
        assertEquals(32, TextBlocksInterpreter.detectGramsPerServing("PER 2 TBSP (32 g)"))
    }

    @Test
    fun firstParenAfterAnchor_isCaptured() {
        // "(28g)" is the FIRST parenthetical after "Per"; "100g" appears earlier but
        // is not inside parens, so it is correctly skipped.
        assertEquals(28, TextBlocksInterpreter.detectGramsPerServing("Per 100g of product (28g)"))
    }

    @Test
    fun decimalGrams_areRoundedHalfUp() {
        assertEquals(28, TextBlocksInterpreter.detectGramsPerServing("Per serving (27.5g)"))
        assertEquals(27, TextBlocksInterpreter.detectGramsPerServing("Per serving (27.4g)"))
    }

    @Test
    fun missingParenthesis_returnsNull() {
        assertNull(TextBlocksInterpreter.detectGramsPerServing("Serving size: 30g"))
    }

    @Test
    fun missingGramsUnit_returnsNull() {
        assertNull(TextBlocksInterpreter.detectGramsPerServing("Per 100g"))
    }

    @Test
    fun nonGramUnitInsideParens_returnsNull() {
        assertNull(TextBlocksInterpreter.detectGramsPerServing("Per 1 oz (1/8 cup)"))
    }

    @Test
    fun anchorWordInsideOtherWord_doesNotMatch() {
        // "Performance" should NOT trigger the "per" anchor.
        assertNull(TextBlocksInterpreter.detectGramsPerServing("Performance metrics (15 ops)"))
    }

    @Test
    fun implausiblyLargeValue_returnsNull() {
        assertNull(TextBlocksInterpreter.detectGramsPerServing("Per serving (3000g)"))
    }

    @Test
    fun zeroGrams_returnsNull() {
        // 0g is implausible and almost certainly an OCR misread.
        assertNull(TextBlocksInterpreter.detectGramsPerServing("Per serving (0g)"))
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail to compile**

```bash
./gradlew :nutritionlib:testDebugUnitTest --tests "com.graydyn.nutritionlib.TextBlocksInterpreterTest"
```

Expected: build fails with `Unresolved reference: detectGramsPerServing`.

- [ ] **Step 3: Add the `SERVING_REGEX` and `detectGramsPerServing` to the companion object**

Open `nutritionlib/src/main/java/com/graydyn/nutritionlib/TextBlocksInterpreter.kt`. Add a new import at the top of the file, alphabetically among the `kotlin.*` imports — there are none currently, so add it after the existing `com.graydyn.nutritionlib.*` imports and before the class declaration:

```kotlin
import kotlin.math.roundToInt
```

Then, inside the existing `companion object` (the block starting `companion object {` near the top of the class body), add the `SERVING_REGEX` and `detectGramsPerServing` declarations directly below the existing `private val TAG = "TextBlocksInterpreter"` line. The companion object's opening should now look like:

```kotlin
    companion object {
        private val TAG = "TextBlocksInterpreter"

        private val SERVING_REGEX = Regex(
            """\b(per|pour)\b\s+.*?\(\s*(\d+(?:\.\d+)?)\s*g\s*\)""",
            RegexOption.IGNORE_CASE
        )

        /**
         * Detects "Per X (Yg)" / "Pour X (Yg)" serving-size phrasing in a single OCR row.
         * Returns the grams value rounded to the nearest integer, or null if the row does
         * not match or the value is outside the plausible 1..2000 range. Pure function;
         * exposed as `internal` so the same module's tests can exercise it directly.
         */
        internal fun detectGramsPerServing(line: String): Int? {
            val match = SERVING_REGEX.find(line) ?: return null
            val numberStr = match.groupValues[2]
            val value = numberStr.toDoubleOrNull()?.roundToInt() ?: return null
            if (value !in 1..2000) return null
            return value
        }

        fun read(blocks: List<Text.TextBlock>, oldMacros: Macros): Pair<Macros, OcrPassData> {
```

(The existing `fun read(...)` line should remain immediately below the new helper — the snippet above shows it for placement reference. Do not delete it.)

- [ ] **Step 4: Run the new tests and verify they pass**

```bash
./gradlew :nutritionlib:testDebugUnitTest --tests "com.graydyn.nutritionlib.TextBlocksInterpreterTest"
```

Expected: `BUILD SUCCESSFUL`, 12 test methods passing.

- [ ] **Step 5: Wire the detector into `readTextLines`**

In the same file, locate the `readTextLines` function (currently starts at line 65 with `private fun readTextLines(lines: List<String>, macros: Macros): Pair<Macros, List<MacroDetection>> {`). The current opening of the per-row loop reads:

```kotlin
            for (line in lines) {
                // Strip percentage values (daily value %) so they don't interfere with number extraction
                val lineNoPercent = Regex("""\d+\s*%""").replace(line, "")
                val lower = lineNoPercent.lowercase()

                val macro = detectMacro(lower) ?: continue
```

Replace those five lines (the `for` header through `val macro = ...`) with:

```kotlin
            for (line in lines) {
                // Strip percentage values (daily value %) so they don't interfere with number extraction
                val lineNoPercent = Regex("""\d+\s*%""").replace(line, "")
                val lower = lineNoPercent.lowercase()

                // Opportunistic serving-size detection. Runs BEFORE macro detection so a row
                // that has no macro keyword (and would `continue` below) still gets a chance
                // to populate gramsPerServing.
                if (macros.gramsPerServing == -1) {
                    detectGramsPerServing(lineNoPercent)?.let { value ->
                        macros.gramsPerServing = value
                        detections.add(MacroDetection(macro = "gramsPerServing", value = value, fromLine = line))
                    }
                }

                val macro = detectMacro(lower) ?: continue
```

The detector runs against `lineNoPercent` (matching the macro detector's preprocessing), uses the existing `MacroDetection` model with the string key `"gramsPerServing"` for `OcrPassData` logging, and short-circuits on the `gramsPerServing == -1` guard so subsequent frames don't overwrite a found value.

- [ ] **Step 6: Re-run all `:nutritionlib` unit tests and the compile to confirm nothing regressed**

```bash
./gradlew :nutritionlib:testDebugUnitTest :nutritionlib:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. Both `MacrosTest` (5 tests) and `TextBlocksInterpreterTest` (12 tests) pass.

- [ ] **Step 7: Commit**

```bash
git add nutritionlib/src/main/java/com/graydyn/nutritionlib/TextBlocksInterpreter.kt nutritionlib/src/test/java/com/graydyn/nutritionlib/TextBlocksInterpreterTest.kt
git commit -m "feat(nutritionlib): detect grams-per-serving from per/pour parenthetical"
```

---

### Task 3: Prefill `ScannedFoodDialog`'s `gramsPerServing` field from OCR

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt`

- [ ] **Step 1: Change the dialog's initial state to use the OCR-captured value**

Open `tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt`. Locate line 60, currently:

```kotlin
    var gramsPerServing by remember { mutableStateOf("") }
```

Replace that single line with:

```kotlin
    var gramsPerServing by remember { mutableStateOf(seed(macros.gramsPerServing)) }
```

The `seed(value: Int): String` helper is defined directly above at line 55 (`fun seed(value: Int): String = if (value == -1) "" else value.toString()`), so a `-1` value (OCR did not detect serving size) yields an empty string — identical to today's behavior. Any non-`-1` value is rendered as a numeric string and the user can edit it.

- [ ] **Step 2: Build the tracker module**

```bash
./gradlew :tracker:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install on a device or emulator**

```bash
./gradlew :tracker:installDebug
```

Expected: `BUILD SUCCESSFUL` and the Tracker app appears on the connected device.

- [ ] **Step 4: Manually verify on a label WITH a parenthetical serving size**

Find a packaged-food nutrition label that contains a line like `Per serving (30g)` or `Pour 1 portion (28g)` (most boxed snack foods qualify). In the Tracker app:

1. Open Diary → tap the camera icon on any meal.
2. Aim at the label, wait for the scan to complete and the `ScannedFoodDialog` to appear.
3. Confirm the "Grams per serving" text field is pre-populated with the value from the label (not blank).
4. Edit it or accept it, then save and confirm the entry appears in the diary.

Expected: pre-population works for at least one of: English label, French label, label with `(15g)` or `(15 g)` spacing variant.

- [ ] **Step 5: Manually verify on a label WITHOUT a matching serving size pattern**

Find a label that either has no serving-size line in the matched format (e.g., a US label saying `Serving size: 1 cup (240 mL)` with no `g`), or whose serving size is in a non-parenthetical form (`Serving size: 30g`). In the Tracker app:

1. Scan the label.
2. Confirm the `ScannedFoodDialog`'s "Grams per serving" field is empty (the original behaviour) — the user can still type a value manually.

Expected: empty field, no crash, save still works.

- [ ] **Step 6: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/components/ScannedFoodDialog.kt
git commit -m "feat(tracker): prefill ScannedFoodDialog gramsPerServing from OCR Macros"
```

---

## Self-Review Checklist (already run)

**Spec coverage**
- "Add `gramsPerServing: Int` field to `Macros`, default `-1`" → Task 1 Steps 1 + 3 ✓
- "Update no-arg constructor" → Task 1 Step 3 ✓
- "`isComplete()` unchanged" → Task 1 Step 3 keeps the body identical; Task 1 Step 1 adds `isComplete_ignoresGramsPerServing_inBothModes` to lock the invariant ✓
- "Update existing MacrosTest constructor calls" → Task 1 Step 1 ✓
- "New TextBlocksInterpreterTest covering the regex" → Task 2 Step 1 covers all examples in the spec's Examples table + decimal/plausibility/anchor tests ✓
- "`SERVING_REGEX` companion val with `\b(per|pour)\b\s+.*?\(\s*(\d+(?:\.\d+)?)\s*g\s*\)` IGNORE_CASE" → Task 2 Step 3 ✓
- "`internal fun detectGramsPerServing(line: String): Int?`" → Task 2 Step 3 (returns `Int?`, plausibility 1..2000, uses `Double.roundToInt`) ✓
- "Wire into `readTextLines` with don't-overwrite guard" → Task 2 Step 5 ✓
- "Emit `MacroDetection` with key `gramsPerServing`" → Task 2 Step 5 ✓
- "`ScannedFoodDialog` one-line prefill using existing `seed()` helper" → Task 3 Step 1 ✓
- "No change to `NutritionReaderActivity`, layout, or persistence" → no task touches those files ✓
- "Manual verification on English and missing-pattern labels" → Task 3 Steps 4–5 ✓

**Placeholder scan**
- No "TBD" / "TODO" / "implement later" / vague phrases in any task body.
- Every code block is complete and copy-pasteable.

**Type / name consistency**
- `gramsPerServing` is the field name on `Macros` (Task 1), the `MacroDetection.macro` key string (`"gramsPerServing"`, Task 2 Step 5), and the local var name in `ScannedFoodDialog` (Task 3 Step 1). All identical, no drift.
- `detectGramsPerServing(line: String): Int?` signature is identical in the test file (Task 2 Step 1) and the implementation (Task 2 Step 3).
- `SERVING_REGEX` is private; `detectGramsPerServing` is internal so the test source set can reach it. Both confirmed in Task 2 Step 3.
- The `Macros(...)` named-argument literals in `MacrosTest` (Task 1 Step 1) include `gramsPerServing = -1` for every constructor call, matching the new five-parameter primary constructor (Task 1 Step 3).
