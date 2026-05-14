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
import com.graydyn.tracker.data.model.FoodUnitType

@Composable
fun CreateFoodDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        unitType: FoodUnitType,
        calories: Float,
        protein: Float?,
        fat: Float?,
        carbs: Float?
    ) -> Unit
) {
    var unitType by remember { mutableStateOf(FoodUnitType.GRAM) }
    var name by remember { mutableStateOf(initialName.trim()) }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }

    val nameFocusRequester = remember { FocusRequester() }
    val nameInitiallyBlank = remember { initialName.isBlank() }
    LaunchedEffect(Unit) {
        if (nameInitiallyBlank) nameFocusRequester.requestFocus()
    }

    val trimmedName = name.trim()
    val nameBlank = trimmedName.isEmpty()

    val parsedCalories: Float? = calories.trim().toFloatOrNull()
    val caloriesBlank = calories.isBlank()
    val caloriesNonNumeric = !caloriesBlank && parsedCalories == null
    val caloriesNegative = parsedCalories != null && parsedCalories < 0f

    val canSave = !nameBlank && parsedCalories != null && parsedCalories >= 0f

    val caloriesLabel = when (unitType) {
        FoodUnitType.GRAM -> "Calories per 100 g"
        FoodUnitType.ITEM -> "Calories per item"
    }
    val proteinLabel = when (unitType) {
        FoodUnitType.GRAM -> "Protein per 100 g (optional)"
        FoodUnitType.ITEM -> "Protein per item (optional)"
    }
    val fatLabel = when (unitType) {
        FoodUnitType.GRAM -> "Fat per 100 g (optional)"
        FoodUnitType.ITEM -> "Fat per item (optional)"
    }
    val carbsLabel = when (unitType) {
        FoodUnitType.GRAM -> "Carbs per 100 g (optional)"
        FoodUnitType.ITEM -> "Carbs per item (optional)"
    }

    fun selectUnit(next: FoodUnitType) {
        if (next == unitType) return
        unitType = next
        // Clear macro fields so leftover per-100g values aren't silently saved as per-item.
        calories = ""
        protein = ""
        fat = ""
        carbs = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New food") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectUnit(FoodUnitType.GRAM) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = unitType == FoodUnitType.GRAM,
                            onClick = { selectUnit(FoodUnitType.GRAM) }
                        )
                        Text("Measured by weight")
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectUnit(FoodUnitType.ITEM) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = unitType == FoodUnitType.ITEM,
                            onClick = { selectUnit(FoodUnitType.ITEM) }
                        )
                        Text("Counted as items")
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
                        carbs.trim().toFloatOrNull()
                    )
                },
                enabled = canSave
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}