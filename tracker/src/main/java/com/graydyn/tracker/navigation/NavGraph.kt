package com.graydyn.tracker.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.graydyn.tracker.data.model.MealType
import com.graydyn.tracker.ui.diary.DiaryScreen
import com.graydyn.tracker.ui.goals.GoalsScreen
import com.graydyn.tracker.ui.savedmeal.SavedMealEditScreen
import com.graydyn.tracker.ui.search.SearchMode
import com.graydyn.tracker.ui.search.SearchScreen
import com.graydyn.tracker.ui.stats.StatsScreen

sealed class Route(val path: String) {
    object Diary : Route("diary")
    object Goals : Route("goals")
    object Stats : Route("stats")
    object Search : Route("search/{date}/{mealType}?mode={mode}") {
        fun createRoute(date: String, mealType: MealType) = "search/$date/${mealType.name}"
        fun createRouteForPick(date: String, mealType: MealType) =
            "search/$date/${mealType.name}?mode=PICK_FOR_SAVED_MEAL"
    }
    object SavedMealEdit : Route("savedMealEdit/{id}") {
        fun createRoute(id: Long) = "savedMealEdit/$id"
    }
}

@Composable
fun TrackerNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Route.Diary.path) {
        composable(Route.Diary.path) {
            DiaryScreen(navController = navController)
        }
        composable(Route.Goals.path) {
            GoalsScreen(navController = navController)
        }
        composable(Route.Stats.path) {
            StatsScreen(navController = navController)
        }
        composable(
            route = Route.Search.path,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("mealType") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType; defaultValue = "LOG"; nullable = false }
            )
        ) { backStackEntry ->
            val date = backStackEntry.arguments!!.getString("date")!!
            val mealType = MealType.valueOf(backStackEntry.arguments!!.getString("mealType")!!)
            val mode = SearchMode.valueOf(backStackEntry.arguments!!.getString("mode") ?: "LOG")
            SearchScreen(navController = navController, date = date, mealType = mealType, mode = mode)
        }
        composable(
            route = Route.SavedMealEdit.path,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments!!.getLong("id")
            SavedMealEditScreen(navController = navController, savedMealId = id)
        }
    }
}
