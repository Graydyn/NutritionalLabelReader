# Protein-Only OCR Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the OCR scanner finish as soon as calories and protein are detected when the tracker's existing "Only track protein and calories" preference is on, by passing a boolean Intent extra from `DiaryScreen` into `NutritionReaderActivity`.

**Architecture:** A new `EXTRA_PROTEIN_ONLY` Intent extra controls per-launch scanner behavior. When true, `NutritionReaderActivity` hides the Fat/Carbs status indicators, treats `Macros.isComplete(proteinOnly = true)` as the completion gate (calories + protein only), and skips the four-macro calorie consistency check. `TextBlocksInterpreter` is unchanged: fat and carbs are still captured opportunistically. The tracker's existing `proteinOnly` StateFlow on `DiaryViewModel` is already collected in `DiaryScreen`; the only tracker change is putting the extra on the scan Intent.

**Tech Stack:** Kotlin, Android Activity, MLKit Text Recognition (already wired), JUnit 4 (already wired in `:nutritionlib` for host unit tests).

**Spec:** `docs/superpowers/specs/2026-05-19-protein-only-ocr-mode-design.md`.

**Working modules:** `nutritionlib/` (primary), `tracker/` (one call-site change).

---

## File Structure

**Create**
- `nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt` — host JUnit tests for the new `isComplete(proteinOnly)` overload.

**Modify**
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt` — add a defaulted `proteinOnly` parameter to `isComplete`.
- `nutritionlib/src/main/java/com/graydyn/nutritionlib/NutritionReaderActivity.kt` — add `EXTRA_PROTEIN_ONLY` companion constant; read the extra in `onCreate`; hide Fat/Carbs status views when true; gate completion and validation in the `analyze` callback.
- `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt` — set the new extra on the Intent inside `launchScan`.

**Unchanged**
- `nutritionlib/.../TextBlocksInterpreter.kt`
- `nutritionlib/src/main/res/layout/activity_nutrition_reader.xml`
- All tracker DataStore plumbing and ViewModels (the `proteinOnly` StateFlow on `DiaryViewModel` and the collected `proteinOnly` variable at `DiaryScreen.kt:115` already exist from the prior protein-only feature).

**Note on testing**

The `:nutritionlib` module has JUnit 4 wired for host unit tests via `testImplementation(libs.junit)` in `nutritionlib/build.gradle.kts:77`. `Macros` is pure data so its new overload is unit-testable on the host — Task 1 uses TDD. The Activity and tracker UI changes cannot be unit-tested without UI test infrastructure that does not exist in this repo; verification for Tasks 2 and 3 is build + manual exercise on a device or emulator.

---

### Task 1: Add `proteinOnly` parameter to `Macros.isComplete` (TDD)

**Files:**
- Create: `nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt`
- Modify: `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt`

- [ ] **Step 1: Write the failing test file**

Create `nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt` with these exact contents:

```kotlin
package com.graydyn.nutritionlib

import com.graydyn.nutritionlib.model.Macros
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacrosTest {

    @Test
    fun isComplete_default_requiresAllFourMacros() {
        val allFour = Macros(calories = 200, fat = 5, protein = 12, carbs = 25)
        val missingCarbs = Macros(calories = 200, fat = 5, protein = 12, carbs = -1)

        assertTrue(allFour.isComplete())
        assertFalse(missingCarbs.isComplete())
    }

    @Test
    fun isComplete_proteinOnlyTrue_ignoresFatAndCarbs() {
        val caloriesAndProteinOnly = Macros(calories = 200, fat = -1, protein = 12, carbs = -1)
        val missingProtein = Macros(calories = 200, fat = -1, protein = -1, carbs = -1)
        val missingCalories = Macros(calories = -1, fat = -1, protein = 12, carbs = -1)

        assertTrue(caloriesAndProteinOnly.isComplete(proteinOnly = true))
        assertFalse(missingProtein.isComplete(proteinOnly = true))
        assertFalse(missingCalories.isComplete(proteinOnly = true))
    }

    @Test
    fun isComplete_proteinOnlyFalse_matchesDefault() {
        val twoOfFour = Macros(calories = 200, fat = -1, protein = 12, carbs = -1)
        assertFalse(twoOfFour.isComplete(proteinOnly = false))
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail to compile**

From the repo root:

```bash
./gradlew :nutritionlib:testDebugUnitTest --tests "com.graydyn.nutritionlib.MacrosTest"
```

Expected: build fails with a Kotlin compilation error along the lines of `Too many arguments for public final fun isComplete(): Boolean` on the `isComplete(proteinOnly = true)` / `isComplete(proteinOnly = false)` calls. This confirms the production signature is what we need to change.

- [ ] **Step 3: Update `Macros.isComplete` to accept the new parameter**

Replace the entire contents of `nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt` with:

```kotlin
package com.graydyn.nutritionlib.model

import java.io.Serializable

data class Macros(var calories: Int, var fat: Int, var protein: Int, var carbs: Int) : Serializable {

    constructor() : this(-1, -1, -1, -1)

    fun isComplete(proteinOnly: Boolean = false): Boolean {
        if (proteinOnly) return calories != -1 && protein != -1
        return calories != -1 && fat != -1 && protein != -1 && carbs != -1
    }
}
```

The default value of `false` preserves all existing callers (notably `NutritionReaderActivity.analyze()` at this point in time — Task 2 updates the call to pass the flag explicitly).

- [ ] **Step 4: Run the tests and verify they pass**

```bash
./gradlew :nutritionlib:testDebugUnitTest --tests "com.graydyn.nutritionlib.MacrosTest"
```

Expected: `BUILD SUCCESSFUL`. The three test methods report passing.

- [ ] **Step 5: Confirm the wider nutritionlib module still compiles**

```bash
./gradlew :nutritionlib:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. (This catches accidental signature breakage in `NutritionReaderActivity` even though we have not modified it yet; the no-arg call `macros.isComplete()` continues to compile because of the default.)

- [ ] **Step 6: Commit**

```bash
git add nutritionlib/src/test/java/com/graydyn/nutritionlib/MacrosTest.kt nutritionlib/src/main/java/com/graydyn/nutritionlib/model/Macros.kt
git commit -m "feat(nutritionlib): add proteinOnly parameter to Macros.isComplete"
```

---

### Task 2: Add `EXTRA_PROTEIN_ONLY` and wire mode into `NutritionReaderActivity`

**Files:**
- Modify: `nutritionlib/src/main/java/com/graydyn/nutritionlib/NutritionReaderActivity.kt`

This task makes four targeted edits to the file: add the companion constant, add an instance field, read the extra in `onCreate` and hide the Fat/Carbs status views when set, and update the completion/validation gate inside the `TextAnalyzer.analyze` success listener.

- [ ] **Step 1: Add `View` import for visibility constants**

The file already imports `android.view.View` (used by `View.VISIBLE` / `View.GONE` inside `showValidationMessage` at lines 154-159). Verify it is present:

```bash
grep -n "^import android.view.View" nutritionlib/src/main/java/com/graydyn/nutritionlib/NutritionReaderActivity.kt
```

Expected: one line, `import android.view.View`. If missing, add it alphabetically among the `android.*` imports. (It is present in the file as of the spec's reference state.)

- [ ] **Step 2: Add the `EXTRA_PROTEIN_ONLY` companion constant**

Locate the `companion object` block (currently starting at the line containing `companion object {` near the bottom of the class — search for `companion object`). The existing block reads:

```kotlin
    companion object {
        private const val TAG = "CameraXApp"
        private const val OCR_LOGGING_ENABLED = false
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
```

Replace it with:

```kotlin
    companion object {
        const val EXTRA_PROTEIN_ONLY = "protein_only"

        private const val TAG = "CameraXApp"
        private const val OCR_LOGGING_ENABLED = false
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
```

`EXTRA_PROTEIN_ONLY` is `public` so the tracker can reference it without copying the string. The value `"protein_only"` is namespaced enough for a single extra on a single Activity.

- [ ] **Step 3: Add a `proteinOnly` instance field**

Locate the existing instance fields near the top of the class. The current block reads:

```kotlin
    private lateinit var cameraExecutor: ExecutorService
    private val TAG = "NutritionReaderActivity"
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private lateinit var viewBinding: ActivityNutritionReaderBinding
    private var macros = Macros()
    private lateinit var ocrPassLogger: OcrPassLogger
    private val messageHandler = Handler(Looper.getMainLooper())
```

Replace it with:

```kotlin
    private lateinit var cameraExecutor: ExecutorService
    private val TAG = "NutritionReaderActivity"
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private lateinit var viewBinding: ActivityNutritionReaderBinding
    private var macros = Macros()
    private lateinit var ocrPassLogger: OcrPassLogger
    private val messageHandler = Handler(Looper.getMainLooper())
    private var proteinOnly: Boolean = false
```

- [ ] **Step 4: Read the extra and hide Fat/Carbs status rows in `onCreate`**

Locate the existing `onCreate` method:

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityNutritionReaderBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        if (OCR_LOGGING_ENABLED) ocrPassLogger = OcrPassLogger(this)
        updateProgressUI(Macros())

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }
```

Replace it with:

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityNutritionReaderBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        proteinOnly = intent.getBooleanExtra(EXTRA_PROTEIN_ONLY, false)
        if (proteinOnly) {
            viewBinding.statusFat.visibility = View.GONE
            viewBinding.statusCarbs.visibility = View.GONE
        }

        if (OCR_LOGGING_ENABLED) ocrPassLogger = OcrPassLogger(this)
        updateProgressUI(Macros())

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }
```

The `bind()` calls in `updateProgressUI` continue to write text into `statusFat` / `statusCarbs`; that is harmless on a `GONE` view, and avoids branching the bind loop.

- [ ] **Step 5: Gate completion and skip calorie consistency in `analyze`**

Locate the success listener inside `TextAnalyzer.analyze`. The current block reads:

```kotlin
                        if (macros.isComplete()) {
                            if (isCalorieConsistent(macros)) {
                                returnResult(macros)
                            } else {
                                showValidationMessage("Validation failed, rescanning...")
                                macros = Macros()
                            }
                        }
```

Replace it with:

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

Two-macro consistency cannot be meaningfully validated, so in protein-only mode we skip the check entirely and accept the first calories + protein pair the OCR pipeline confirms.

- [ ] **Step 6: Verify the module compiles**

```bash
./gradlew :nutritionlib:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. No new warnings.

- [ ] **Step 7: Re-run the Macros unit tests to confirm nothing regressed**

```bash
./gradlew :nutritionlib:testDebugUnitTest --tests "com.graydyn.nutritionlib.MacrosTest"
```

Expected: `BUILD SUCCESSFUL`, three tests pass.

- [ ] **Step 8: Commit**

```bash
git add nutritionlib/src/main/java/com/graydyn/nutritionlib/NutritionReaderActivity.kt
git commit -m "feat(nutritionlib): add EXTRA_PROTEIN_ONLY mode to NutritionReaderActivity"
```

---

### Task 3: Pass `EXTRA_PROTEIN_ONLY` from `DiaryScreen.launchScan`

**Files:**
- Modify: `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt`

The composable already collects `val proteinOnly by viewModel.proteinOnly.collectAsState()` at line 115 from the prior protein-only feature, so the value is in scope inside `launchScan`. The only change is on the Intent.

- [ ] **Step 1: Update `launchScan` to set the extra**

Open `tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt` and locate the `launchScan` function (the lines starting `fun launchScan(mealType: MealType) {`, currently around line 152):

```kotlin
    fun launchScan(mealType: MealType) {
        scanTargetMeal = mealType
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            scanLauncher.launch(Intent(context, NutritionReaderActivity::class.java))
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
```

Replace it with:

```kotlin
    fun launchScan(mealType: MealType) {
        scanTargetMeal = mealType
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val intent = Intent(context, NutritionReaderActivity::class.java).apply {
                putExtra(NutritionReaderActivity.EXTRA_PROTEIN_ONLY, proteinOnly)
            }
            scanLauncher.launch(intent)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
```

`NutritionReaderActivity` is already imported at line 74 of this file, so no new import is required.

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

- [ ] **Step 4: Manually verify the OFF path (regression check)**

In the running Tracker app:

1. Open the gear icon → Goals.
2. Confirm "Only track protein and calories" is OFF (toggle off, all four `GoalField`s visible). If it is on, toggle it off and tap Save.
3. Return to Diary, tap the camera icon on any meal.
4. Confirm the scan screen shows all four status indicators (Calories, Fat, Carbs, Protein).
5. Aim at any nutrition label. The scan should still wait for all four macros and run the calorie consistency check (i.e., behaviour is unchanged from before this plan).

Expected: Identical to pre-change behaviour. If the scan finishes after only calories + protein are detected here, `EXTRA_PROTEIN_ONLY` is being read incorrectly — investigate before continuing.

- [ ] **Step 5: Manually verify the ON path**

In the running Tracker app:

1. Open Goals, toggle "Only track protein and calories" ON, tap Save.
2. Return to Diary, tap the camera icon on any meal.
3. Confirm the scan screen now shows only two status indicators: Calories and Protein. The Fat and Carbs rows should be hidden (not just unchecked — gone from the layout).
4. Aim at any nutrition label. The scan should return as soon as Calories and Protein both show a green check, without waiting for Fat / Carbs detection and without the "Validation failed, rescanning..." message firing.
5. Confirm the resulting entry appears in the diary (open the meal — the label name shows "Scanned label" with the captured calories and protein).

Expected: scan finishes noticeably faster than the OFF path on the same label.

- [ ] **Step 6: Commit**

```bash
git add tracker/src/main/java/com/graydyn/tracker/ui/diary/DiaryScreen.kt
git commit -m "feat(tracker): pass proteinOnly mode into NutritionReaderActivity scan Intent"
```

---

## Self-Review Checklist (already run)

**Spec coverage**
- "Add `EXTRA_PROTEIN_ONLY` Intent extra (Boolean, default false)" → Task 2 Step 2 ✓
- "`Macros.isComplete(proteinOnly: Boolean = false)`" → Task 1 Step 3 ✓
- "Skip `isCalorieConsistent` when `proteinOnly`" → Task 2 Step 5 ✓
- "Hide `statusFat` and `statusCarbs` views when `proteinOnly`" → Task 2 Step 4 ✓
- "`TextBlocksInterpreter` unchanged" → no task touches it ✓
- "Layout XML unchanged" → no task touches it ✓
- "Tracker reads existing `proteinOnly` StateFlow, sets Intent extra in `launchScan`" → Task 3 Step 1 ✓
- "No DataStore dependency in `nutritionlib`" → no nutritionlib task adds DataStore ✓
- "Default `false` preserves existing callers" → Task 1 Step 3 uses default parameter; Task 2 Step 4 uses `getBooleanExtra(EXTRA_PROTEIN_ONLY, false)` ✓
- "Unit test for `Macros.isComplete(proteinOnly = true)`" → Task 1 Step 1 covers it (plus regression cases) ✓

**Type / name consistency**
- Constant name `EXTRA_PROTEIN_ONLY` and string value `"protein_only"` — Task 2 Step 2 declares; Task 3 Step 1 references via `NutritionReaderActivity.EXTRA_PROTEIN_ONLY`. ✓
- Field name `proteinOnly` in `NutritionReaderActivity` (Task 2 Step 3) matches the parameter name on `Macros.isComplete(proteinOnly: Boolean)` (Task 1 Step 3). ✓
- Variable name `proteinOnly` in `DiaryScreen.launchScan` (Task 3 Step 1) matches the composable-scope `val proteinOnly by viewModel.proteinOnly.collectAsState()` at line 115 of the existing file. ✓
- `View.GONE` / `View.VISIBLE` use the already-imported `android.view.View` (Task 2 Step 1 verifies). ✓
