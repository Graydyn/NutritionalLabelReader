package com.graydyn.tracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType

/**
 * Enter or update the current weight (pounds). Pre-fills with [currentLbs] when
 * a value already exists. Save is disabled until the input parses to a positive
 * number.
 */
@Composable
fun WeightDialog(
    currentLbs: Float?,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit
) {
    var text by remember {
        mutableStateOf(currentLbs?.let { formatAmount(it) } ?: "")
    }
    val parsed = text.trim().toFloatOrNull()
    val isValid = parsed != null && parsed > 0f

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weight") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Weight (lbs)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onSave(it) } },
                enabled = isValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
