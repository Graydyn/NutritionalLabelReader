package com.graydyn.tracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.graydyn.tracker.ui.components.formatAmount
import com.graydyn.tracker.ui.theme.MacroCalories
import com.graydyn.tracker.ui.theme.MacroProtein

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
) {
    val stats by viewModel.monthStats.collectAsState()
    val label by viewModel.selectedLabel.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Statistics", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MonthSelector(
                label = label,
                onPrevious = { viewModel.prevMonth() },
                onNext = { viewModel.nextMonth() }
            )

            val hasData = stats.dailyCalories.isNotEmpty() || stats.dailyWeight.isNotEmpty()
            if (!hasData) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No data for this month",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Calories chart
                ChartCard(title = "Calories") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendSwatch(MacroCalories); Text("  Calories", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(16.dp))
                        LegendSwatch(Color(0xFF9E9E9E)); Text("  Goal", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    val calValues = stats.dailyCalories.values.map { it.toFloat() }
                    LineChart(
                        series = listOf(
                            ChartSeries(
                                points = stats.dailyCalories.mapValues { it.value.toFloat() },
                                color = MacroCalories,
                                breakOnGaps = true
                            )
                        ),
                        xDomainMax = stats.daysInMonth,
                        yRange = StatsMath.yRange(calValues, stats.calorieGoal?.toFloat()),
                        referenceLine = stats.calorieGoal?.toFloat(),
                        yLabel = { "${it.toInt()}" }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Weight chart
                ChartCard(title = "Weight (lbs)") {
                    val wValues = stats.dailyWeight.values.toList()
                    LineChart(
                        series = listOf(
                            ChartSeries(
                                points = stats.dailyWeight,
                                color = MacroProtein,
                                breakOnGaps = false
                            )
                        ),
                        xDomainMax = stats.daysInMonth,
                        yRange = StatsMath.yRange(wValues, null),
                        referenceLine = null,
                        yLabel = { formatAmount(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color) {
    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MonthSelector(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month",
                tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(label, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground)
        IconButton(
            onClick = onNext,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month",
                tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}
