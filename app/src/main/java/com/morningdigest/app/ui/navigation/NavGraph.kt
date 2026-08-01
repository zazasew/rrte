package com.morningdigest.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.morningdigest.app.ui.dashboard.DashboardScreen
import com.morningdigest.app.ui.history.HistoryScreen
import com.morningdigest.app.ui.history.ReportDetailScreen
import com.morningdigest.app.ui.settings.SettingsScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val REPORT_DETAIL = "report/{reportId}"
    fun reportDetail(id: Long) = "report/$id"
    const val ASSISTANTS = "assistants"
    const val ASSISTANT_DETAIL = "assistant/{characterId}"
    fun assistantDetail(id: String) = "assistant/$id"
}

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenAssistants = { navController.navigate(Routes.ASSISTANTS) },
                onOpenAssistantDetail = { id -> navController.navigate(Routes.assistantDetail(id)) }
            )
        }
        composable(Routes.ASSISTANTS) {
            com.morningdigest.app.ui.assistants.AssistantsListScreen(
                onBack = { navController.popBackStack() },
                onOpenCharacter = { id -> navController.navigate(Routes.assistantDetail(id)) }
            )
        }
        composable(
            Routes.ASSISTANT_DETAIL,
            arguments = listOf(navArgument("characterId") { type = NavType.StringType })
        ) { backStackEntry ->
            com.morningdigest.app.ui.assistants.AssistantDetailScreen(
                characterId = backStackEntry.arguments?.getString("characterId") ?: "",
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenReport = { id -> navController.navigate(Routes.reportDetail(id)) }
            )
        }
        composable(
            Routes.REPORT_DETAIL,
            arguments = listOf(navArgument("reportId") { type = NavType.LongType })
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getLong("reportId") ?: 0L
            ReportDetailScreen(reportId = reportId, onBack = { navController.popBackStack() })
        }
    }
}
