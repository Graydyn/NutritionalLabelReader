package com.graydyn.tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.graydyn.nutritionlib.model.Macros
import com.graydyn.tracker.data.model.FoodUnitType

/**
 * Post-scan dialog. Lets the user name the scanned label, choose its unit type,
 * confirm/edit the macros (prefilled from the scan; -1 sentinel rendered blank),
 * and pick the quantity to log immediately.
 *
 * Scanned macros are interpreted as per-serving (per-item). If the user switches
 * to GRAM, fields are cleared because per-serving values cannot be auto-converted
 * to per-100 g.
 */
@Composable
fun ScannedFoodDialog(
    macros: Macros,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        unitType: FoodUnitType,
        calories: Float,
        protein: Float?,
        fat: Float?,
        carbs: Float?,
        quantity: Float
    ) -> Unit
) {
    var unitType by remember { mutableStateOf(FoodUnitType.ITEM) }
    var name by remember { mutableStateOf("") }

    fun seed(value: Int): String = if (value == -1) "" else value.toString()
    var calories by remember { mutableStateOf(seed(macros.calories)) }
    var protein by remember { mutableStateOf(seed(macros.protein)) }
    var fat by remember { mutableStateOf(seed(macros.fat)) }
    var carbs by remember { mutableStateOf(seed(macros.carbs)) }
    var quantity by remember { mutableStateOf("1") }

    val nameFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocusRequester.requestFocus() }

    val trimmedName = name.trim()
    val nameBlank = trimmedName.isEmpty()
    val parsedCalories: Float? = calories.trim().toFloatOrNull()
    val caloriesBlank = calories.isBlank()
    val caloriesNonNumeric = !caloriesBlank && parsedCalories == null
    val caloriesNegative = parsedCalories != null && parsedCalories < 0f
    val parsedQuantity: Float? = quantity.trim().toFloatOrNull()?.takeIf { it > 0f }
    val quantityInvalid = parsedQuantity == null

    val canSave = !nameBlank && parsedCalories != null && parsedCalories >= 0f && !quantityInvalid

    val caloriesLabel = if (unitType == FoodUnitType.GRAM) "Calories per 100 g" else "Calories per item"
    val proteinLabel = if (unitType == FoodUnitType.GRAM) "Protein per 100 g (optional)" else "Protein per item (optional)"
    val fatLabel = if (unitType == FoodUnitType.GRAM) "Fat per 100 g (optional)" else "Fat per item (optional)"
    val carbsLabel = if (unitType == FoodUnitType.GRAM) "Carbs per 100 g (optional)" else "Carbs per item (optional)"
    val quantityLabel = if (unitType == FoodUnitType.GRAM) "Grams to log now" else "Count to log now"

    fun selectUnit(next: FoodUnitType) {
        if (next == unitType) return
        unitType = next
        // Per-serving and per-100 g aren't interchangeable; clear macros so the
        // user can re-enter per-100 g manually when switching to GRAM (and vice versa).
        calories = ""
        protein = ""
        fat = ""
        carbs = ""
        quantity = if (next == FoodUnitType.GRAM) "100" else "1"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save scanned food") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .clickable { selectUnit(FoodUnitType.GRAM) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = unitType == FoodUnitType.GRAM, onClick = { selectUnit(FoodUnitType.GRAM) })
                        Text("By weight")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clickable { selectUnit(FoodUnitType.ITEM) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = unitType == FoodUnitType.ITEM, onClick = { selectUnit(FoodUnitType.ITEM) })
                        Text("By item")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameBlank,
                    supportingText = if (nameBlank) { { Text("Required") } } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text(caloriesLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = caloriesBlank || caloriesNonNumeric || caloriesNegative,
                    supportingText = when {
                        caloriesBlank -> { { Text("Required") } }
                        caloriesNonNumeric -> { { Text("Must be a number") } }
                        caloriesNegative -> { { Text("Must be 0 or greater") } }
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = protein,
                    onValueChange = { protein = it },
                    label = { Text(proteinLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = { Text(fatLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = { Text(carbsLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(quantityLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = quantityInvalid,
                    supportingText = if (quantityInvalid) { { Text("Must be greater than 0") } } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        trimmedName,
                        unitType,
                        parsedCalories!!,
                        protein.trim().toFloatOrNull(),
                        fat.trim().toFloatOrNull(),
                        carbs.trim().toFloatOrNull(),
                        parsedQuantity!!
                    )
                },
                enabled = canSave
            ) { Text("Save & log") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}