package com.graydyn.tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
 * Post-scan dialog. Scanned macros are interpreted as per-serving by default
 * because nutrition labels are written per serving; the user can switch to
 * per-weight or per-item only if they've defined what a serving is.
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
        gramsPerServing: Float?,
        itemsPerServing: Float?,
        quantity: Float
    ) -> Unit
) {
    var unitType by remember { mutableStateOf(FoodUnitType.SERVING) }
    var name by remember { mutableStateOf("") }

    fun seed(value: Int): String = if (value == -1) "" else value.toString()
    var calories by remember { mutableStateOf(seed(macros.calories)) }
    var protein by remember { mutableStateOf(seed(macros.protein)) }
    var fat by remember { mutableStateOf(seed(macros.fat)) }
    var carbs by remember { mutableStateOf(seed(macros.carbs)) }
    var gramsPerServing by remember { mutableStateOf(seed(macros.gramsPerServing)) }
    var itemsPerServing by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var missingFieldMessage by remember { mutableStateOf<String?>(null) }

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

    val caloriesLabel = when (unitType) {
        FoodUnitType.GRAM -> "Calories per 100 g"
        FoodUnitType.ITEM -> "Calories per item"
        FoodUnitType.SERVING -> "Calories per serving"
    }
    val proteinLabel = when (unitType) {
        FoodUnitType.GRAM -> "Protein per 100 g (optional)"
        FoodUnitType.ITEM -> "Protein per item (optional)"
        FoodUnitType.SERVING -> "Protein per serving (optional)"
    }
    val fatLabel = when (unitType) {
        FoodUnitType.GRAM -> "Fat per 100 g (optional)"
        FoodUnitType.ITEM -> "Fat per item (optional)"
        FoodUnitType.SERVING -> "Fat per serving (optional)"
    }
    val carbsLabel = when (unitType) {
        FoodUnitType.GRAM -> "Carbs per 100 g (optional)"
        FoodUnitType.ITEM -> "Carbs per item (optional)"
        FoodUnitType.SERVING -> "Carbs per serving (optional)"
    }
    val quantityLabel = when (unitType) {
        FoodUnitType.GRAM -> "Grams to log now"
        FoodUnitType.ITEM -> "Count to log now"
        FoodUnitType.SERVING -> "Servings to log now"
    }

    fun selectUnit(next: FoodUnitType) {
        if (next == unitType) return
        when {
            unitType == FoodUnitType.SERVING && next == FoodUnitType.GRAM -> {
                val gPerServing = gramsPerServing.trim().toFloatOrNull()?.takeIf { it > 0f }
                if (gPerServing == null) {
                    missingFieldMessage =
                        "To switch to 'by weight', enter the weight per serving so we can convert the values."
                    return
                }
                calories = perServingToPer100g(calories.trim().toFloatOrNull(), gPerServing)?.let { fmt(it) } ?: ""
                protein = perServingToPer100g(protein.trim().toFloatOrNull(), gPerServing)?.let { fmt(it) } ?: ""
                fat = perServingToPer100g(fat.trim().toFloatOrNull(), gPerServing)?.let { fmt(it) } ?: ""
                carbs = perServingToPer100g(carbs.trim().toFloatOrNull(), gPerServing)?.let { fmt(it) } ?: ""
                quantity = "100"
            }
            unitType == FoodUnitType.SERVING && next == FoodUnitType.ITEM -> {
                val iPerServing = itemsPerServing.trim().toFloatOrNull()?.takeIf { it > 0f }
                if (iPerServing == null) {
                    missingFieldMessage =
                        "To switch to 'by item', enter the items per serving so we can convert the values."
                    return
                }
                calories = perServingToPerItem(calories.trim().toFloatOrNull(), iPerServing)?.let { fmt(it) } ?: ""
                protein = perServingToPerItem(protein.trim().toFloatOrNull(), iPerServing)?.let { fmt(it) } ?: ""
                fat = perServingToPerItem(fat.trim().toFloatOrNull(), iPerServing)?.let { fmt(it) } ?: ""
                carbs = perServingToPerItem(carbs.trim().toFloatOrNull(), iPerServing)?.let { fmt(it) } ?: ""
                quantity = "1"
            }
            else -> {
                calories = ""
                protein = ""
                fat = ""
                carbs = ""
                quantity = if (next == FoodUnitType.GRAM) "100" else "1"
            }
        }
        unitType = next
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save scanned food") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnitRadio("By weight", unitType == FoodUnitType.GRAM) { selectUnit(FoodUnitType.GRAM) }
                    UnitRadio("By item", unitType == FoodUnitType.ITEM) { selectUnit(FoodUnitType.ITEM) }
                    UnitRadio("By serving", unitType == FoodUnitType.SERVING) { selectUnit(FoodUnitType.SERVING) }
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
                if (unitType == FoodUnitType.SERVING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gramsPerServing,
                        onValueChange = { gramsPerServing = it },
                        label = { Text("Weight per serving (g, optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = itemsPerServing,
                        onValueChange = { itemsPerServing = it },
                        label = { Text("Items per serving (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                        if (unitType == FoodUnitType.SERVING) gramsPerServing.trim().toFloatOrNull() else null,
                        if (unitType == FoodUnitType.SERVING) itemsPerServing.trim().toFloatOrNull() else null,
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

    missingFieldMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { missingFieldMessage = null },
            title = { Text("Missing serving information") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { missingFieldMessage = null }) { Text("OK") }
            }
        )
    }
}

@Composable
internal fun RowScope.UnitRadio(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .clickable { onSelect() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}

internal fun fmt(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')
