package com.graydyn.tracker.ui.savedmeal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.graydyn.tracker.data.db.SavedMealSummary
import com.graydyn.tracker.data.model.SavedMealItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMealPickerSheet(
    title: String,
    summaries: List<SavedMealSummary>,
    expandedItems: Map<Long, List<SavedMealItem>>,
    onDismiss: () -> Unit,
    onExpand: (Long) -> Unit,
    onCollapse: (Long) -> Unit,
    onApply: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onRename: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            if (summaries.isEmpty()) {
                Text(
                    "No saved meals yet. Save one from a populated meal card.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(summaries, key = { it.id }) { summary ->
                        SummaryRow(
                            summary = summary,
                            expanded = expandedItems.containsKey(summary.id),
                            items = expandedItems[summary.id].orEmpty(),
                            onTap = {
                                if (expandedItems.containsKey(summary.id)) onCollapse(summary.id)
                                else onExpand(summary.id)
                            },
                            onApply = { onApply(summary.id) },
                            onEdit = { onEdit(summary.id) },
                            onRename = { onRename(summary.id) },
                            onDelete = { onDelete(summary.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    summary: SavedMealSummary,
    expanded: Boolean,
    items: List<SavedMealItem>,
    onTap: () -> Unit,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    Text(summary.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${summary.itemCount} item${if (summary.itemCount == 1) "" else "s"} · ${summary.totalCalories} kcal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onTap) {
                    Text(if (expanded) "Hide" else "Open", style = MaterialTheme.typography.labelSmall)
                }
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Saved meal options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                items.forEach { item ->
                    val qty = if (item.grams != null) "${item.grams.toInt()} g" else "${item.count?.toInt() ?: 0} ×"
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(item.label, modifier = Modifier.weight(1f))
                        Text(qty, modifier = Modifier.padding(end = 8.dp))
                        Text("${item.calories ?: 0} kcal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                    Text("Add to meal")
                }
            }
        }
    }
}
