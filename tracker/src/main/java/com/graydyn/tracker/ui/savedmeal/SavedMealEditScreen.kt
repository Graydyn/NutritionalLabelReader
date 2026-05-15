package com.graydyn.tracker.ui.savedmeal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SavedMealItem
import com.graydyn.tracker.navigation.Route
import com.graydyn.tracker.ui.diary.DiaryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMealEditScreen(
    navController: NavController,
    savedMealId: Long
) {
    val viewModel: SavedMealEditViewModel = viewModel(
        factory = SavedMealEditViewModel.factory(savedMealId)
    )
    val meal by viewModel.meal.collectAsState()
    val items by viewModel.items.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val saved by viewModel.saved.collectAsState()

    // Receive the picked food back from SearchScreen pick-mode
    val backEntry = navController.currentBackStackEntry
    val pickedIdState = backEntry?.savedStateHandle?.getStateFlow<Long?>("picked_food_id", null)?.collectAsState()
    val pickedQtyState = backEntry?.savedStateHandle?.getStateFlow<Float?>("picked_quantity", null)?.collectAsState()
    LaunchedEffect(pickedIdState?.value, pickedQtyState?.value) {
        val id = pickedIdState?.value ?: return@LaunchedEffect
        val qty = pickedQtyState?.value ?: return@LaunchedEffect
        viewModel.handlePickedFoodById(id, qty)
        backEntry.savedStateHandle["picked_food_id"] = null
        backEntry.savedStateHandle["picked_quantity"] = null
    }

    LaunchedEffect(saved) {
        if (saved) navController.popBackStack()
    }

    var renameDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meal?.name ?: "Edit meal") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { renameDialog = true }) { Text("Rename") }
                    TextButton(onClick = { viewModel.save() }) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            saveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    EditableItemRow(
                        item = item,
                        onQuantityChange = { newQty -> viewModel.updateQuantity(item.id, newQty) },
                        onDelete = { viewModel.deleteItem(item) }
                    )
                }
            }
            Button(
                onClick = {
                    val today = DiaryViewModel.todayString()
                    navController.navigate(Route.Search.createRouteForPick(today, MealType.BREAKFAST))
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add food")
            }
        }
    }

    if (renameDialog) {
        SaveMealDialog(
            suggestedName = meal?.name.orEmpty(),
            summary = "Rename this meal",
            onDismiss = { renameDialog = false },
            onSave = { name ->
                viewModel.rename(name)
                renameDialog = false
            }
        )
    }
}

@Composable
private fun EditableItemRow(
    item: SavedMealItem,
    onQuantityChange: (Float) -> Unit,
    onDelete: () -> Unit
) {
    var qtyText by remember(item.id) {
        mutableStateOf(((item.grams ?: item.count ?: 0f).toString()))
    }
    val qtySuffix = if (item.grams != null) "g" else "x"

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label)
            Text(
                "${item.calories ?: 0} kcal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = qtyText,
            onValueChange = { newText ->
                qtyText = newText
                newText.toFloatOrNull()?.takeIf { it > 0f }?.let { onQuantityChange(it) }
            },
            label = { Text(qtySuffix) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(100.dp)
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = "Remove")
        }
    }
}