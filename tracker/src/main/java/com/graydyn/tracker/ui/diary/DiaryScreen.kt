package com.graydyn.tracker.ui.diary

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.graydyn.nutritionlib.NutritionReaderActivity
import com.graydyn.nutritionlib.model.Macros
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.FoodUnitType
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.data.model.SourceType
import com.graydyn.tracker.navigation.Route
import com.graydyn.tracker.ui.components.MacroProgressBar
import com.graydyn.tracker.ui.theme.MacroCalories
import com.graydyn.tracker.ui.theme.MacroCarbs
import com.graydyn.tracker.ui.theme.MacroFat
import com.graydyn.tracker.ui.theme.MacroProtein

private data class MealStyle(val label: String, val icon: ImageVector, val tint: Color)

private fun formatCount(c: Float): String =
    if (c == c.toInt().toFloat()) c.toInt().toString() else "%.2f".format(c).trimEnd('0').trimEnd('.')

@Composable
private fun mealStyle(mealType: MealType): MealStyle = when (mealType) {
    MealType.BREAKFAST -> MealStyle("Breakfast", Icons.Default.WbSunny, MacroCarbs)
    MealType.LUNCH -> MealStyle("Lunch", Icons.Default.Restaurant, MacroCalories)
    MealType.DINNER -> MealStyle("Dinner", Icons.Default.LunchDining, MacroProtein)
    MealType.SNACK -> MealStyle("Snack", Icons.Default.LocalCafe, MacroFat)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    navController: NavController,
    viewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.Factory)
) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val entriesByMeal by viewModel.entriesByMeal.collectAsState()
    val dailyTotals by viewModel.dailyTotals.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val proteinOnly by viewModel.proteinOnly.collectAsState()
    val scanInProgress by viewModel.scanInProgress.collectAsState()

    var scanTargetMeal by remember { mutableStateOf(MealType.BREAKFAST) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* user will retap if denied */ }

    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val macros: Macros? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getSerializableExtra("ActivityResult", Macros::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getSerializableExtra("ActivityResult") as? Macros
            }
            macros?.let { viewModel.onScanResult(it) }
        }
    }

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Food Diary",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Route.Goals.path) }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Goals",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                DateSelector(
                    date = selectedDate,
                    onPrevious = { viewModel.navigateDate(-1) },
                    onNext = { viewModel.navigateDate(1) },
                    onToday = { viewModel.goToToday() }
                )
            }

            item {
                SummaryCard(
                    calorieGoal = goals?.caloriesGoal ?: 2000,
                    proteinGoal = goals?.proteinGoal ?: 150,
                    fatGoal = goals?.fatGoal ?: 65,
                    carbsGoal = goals?.carbsGoal ?: 250,
                    calories = dailyTotals.calories,
                    protein = dailyTotals.protein,
                    fat = dailyTotals.fat,
                    carbs = dailyTotals.carbs,
                    proteinOnly = proteinOnly
                )
            }

            MealType.entries.forEach { mealType ->
                item {
                    MealCard(
                        mealType = mealType,
                        entries = entriesByMeal[mealType] ?: emptyList(),
                        proteinOnly = proteinOnly,
                        onAddFood = {
                            navController.navigate(Route.Search.createRoute(selectedDate, mealType))
                        },
                        onScan = { launchScan(mealType) },
                        onDelete = { viewModel.deleteEntry(it) }
                    )
                }
            }
        }
        scanInProgress?.let { macros ->
            com.graydyn.tracker.ui.components.ScannedFoodDialog(
                macros = macros,
                onDismiss = { viewModel.dismissScannedFoodDialog() },
                onSave = { name, unitType, calories, protein, fat, carbs, quantity ->
                    viewModel.logScannedFood(
                        name = name,
                        unitType = unitType,
                        calories = calories,
                        protein = protein,
                        fat = fat,
                        carbs = carbs,
                        quantity = quantity,
                        mealType = scanTargetMeal
                    )
                }
            )
        }
    }
}

@Composable
private fun DateSelector(
    date: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous day",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            OutlinedButton(
                onClick = onToday,
                modifier = Modifier.padding(top = 4.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text("Today", style = MaterialTheme.typography.labelMedium)
            }
        }
        IconButton(
            onClick = onNext,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next day",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SummaryCard(
    calorieGoal: Int,
    proteinGoal: Int,
    fatGoal: Int,
    carbsGoal: Int,
    calories: Int,
    protein: Float,
    fat: Float,
    carbs: Float,
    proteinOnly: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$calories",
                    style = MaterialTheme.typography.displaySmall,
                    color = MacroCalories,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " / $calorieGoal kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            MacroProgressBar(
                label = "Calories",
                current = calories,
                goal = calorieGoal,
                unit = " kcal",
                color = MacroCalories
            )
            MacroProgressBar(
                label = "Protein",
                current = protein,
                goal = proteinGoal,
                color = MacroProtein
            )
            if (!proteinOnly) {
                MacroProgressBar(
                    label = "Fat",
                    current = fat,
                    goal = fatGoal,
                    color = MacroFat
                )
                MacroProgressBar(
                    label = "Carbs",
                    current = carbs,
                    goal = carbsGoal,
                    color = MacroCarbs
                )
            }
        }
    }
}

@Composable
private fun MealCard(
    mealType: MealType,
    entries: List<DiaryEntry>,
    proteinOnly: Boolean,
    onAddFood: () -> Unit,
    onScan: () -> Unit,
    onDelete: (DiaryEntry) -> Unit
) {
    val style = mealStyle(mealType)
    val mealCalories = entries.sumOf { it.calories ?: 0 }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(style.tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = null,
                        tint = style.tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (entries.isEmpty()) "Nothing logged" else "$mealCalories kcal · ${entries.size} item${if (entries.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                entries.forEach { entry ->
                    DiaryEntryRow(
                        entry = entry,
                        accent = style.tint,
                        proteinOnly = proteinOnly,
                        onDelete = { onDelete(entry) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onScan,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan", style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(
                    onClick = onAddFood,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DiaryEntryRow(
    entry: DiaryEntry,
    accent: Color,
    proteinOnly: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(accent.copy(alpha = 0.7f))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(entry.label)
                    if (entry.sourceType == SourceType.DATABASE) {
                        when (entry.unitType) {
                            FoodUnitType.GRAM -> entry.grams?.let { append("  ·  ${it.toInt()}g") }
                            FoodUnitType.ITEM -> entry.count?.let { append("  ·  ×${formatCount(it)}") }
                        }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buildString {
                    entry.calories?.let { append("$it kcal") } ?: append("-- kcal")
                    append("  ·  P ")
                    entry.protein?.let { append("${"%.0f".format(it)}g") } ?: append("--")
                    if (!proteinOnly) {
                        append("  F ")
                        entry.fat?.let { append("${"%.0f".format(it)}g") } ?: append("--")
                        append("  C ")
                        entry.carbs?.let { append("${"%.0f".format(it)}g") } ?: append("--")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
