package com.graydyn.tracker.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.graydyn.tracker.data.model.MealType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyMealDialog(
    sourceLabel: String,
    sourceDate: String,
    sourceItemCount: Int,
    sourceCalories: Int,
    initialTargetDate: String,
    initialTargetMealType: MealType,
    onDismiss: () -> Unit,
    onCopy: (targetDate: String, targetMealType: MealType) -> Unit,
) {
    var targetDate by rememberSaveable { mutableStateOf(initialTargetDate) }
    var targetMealType by rememberSaveable { mutableStateOf(initialTargetMealType) }
    val itemNoun = if (sourceItemCount == 1) "item" else "items"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy to...") },
        text = {
            Column {
                Text(
                    text = "From: $sourceLabel · $sourceDate · $sourceItemCount $itemNoun, $sourceCalories kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { targetDate = shiftDate(targetDate, -1) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous day"
                        )
                    }
                    Text(
                        text = targetDate,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { targetDate = shiftDate(targetDate, 1) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next day"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MealType.entries.forEach { mt ->
                        FilterChip(
                            selected = targetMealType == mt,
                            onClick = { targetMealType = mt },
                            label = { Text(chipLabel(mt)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCopy(targetDate, targetMealType) }) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun shiftDate(dateString: String, days: Int): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val date = sdf.parse(dateString) ?: return dateString
    val cal = Calendar.getInstance().apply {
        time = date
        add(Calendar.DAY_OF_YEAR, days)
    }
    return sdf.format(cal.time)
}

private fun chipLabel(mt: MealType): String = when (mt) {
    MealType.BREAKFAST -> "Breakfast"
    MealType.LUNCH -> "Lunch"
    MealType.DINNER -> "Dinner"
    MealType.SNACK -> "Snack"
}
