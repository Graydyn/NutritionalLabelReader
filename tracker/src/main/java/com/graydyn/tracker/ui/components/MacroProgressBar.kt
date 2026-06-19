package com.graydyn.tracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A labeled progress bar showing current vs. goal for a single macro. [current] and [goal]
 * accept any Number so callers can pass Int (calories) or Float (grams) interchangeably.
 */
@Composable
fun MacroProgressBar(
    label: String,
    current: Number,
    goal: Number,
    color: Color,
    unit: String = "g",
    modifier: Modifier = Modifier
) {
    val currentValue = current.toFloat()
    val goalValue = goal.toFloat()
    val fraction = if (goalValue > 0f) (currentValue / goalValue).coerceIn(0f, 1f) else 0f

    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${currentValue.roundToInt()} / ${goalValue.roundToInt()}$unit",
                style = MaterialTheme.typography.bodySmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = color.copy(alpha = 0.18f)
        )
    }
}
