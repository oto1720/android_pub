package com.example.oto1720.dojo2026.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.oto1720.dojo2026.ui.digest.DigestScreen
import com.example.oto1720.dojo2026.ui.done.DoneDetailScreen
import com.example.oto1720.dojo2026.ui.done.DoneScreen
import com.example.oto1720.dojo2026.ui.portal.PortalScreen
import com.example.oto1720.dojo2026.ui.tsundoku.TsundokuScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Portal.route,
        modifier = modifier,
    ) {
        composable(Screen.Portal.route) {
            PortalScreen(
                onNavigateToTsundoku = {
                    navController.navigate(Screen.Tsundoku.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToDone = {
                    navController.navigate(Screen.Done.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }

        composable(Screen.Tsundoku.route) {
            TsundokuScreen(
                onNavigateToDigest = { articleId ->
                    navController.navigate(Screen.Digest.createRoute(articleId))
                },
            )
        }

        composable(
            route = Screen.Digest.ROUTE,
            arguments = listOf(navArgument(Screen.Digest.ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val articleId = backStackEntry.arguments?.getString(Screen.Digest.ARG).orEmpty()
            DigestScreen(
                articleId = articleId,
                onNavigateToTsundoku = {
                    navController.navigate(Screen.Tsundoku.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToDone = {
                    navController.navigate(Screen.Done.route) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Done.route) {
            DoneScreen(
                onNavigateToDoneDetail = { articleId ->
                    navController.navigate(Screen.DoneDetail.createRoute(articleId))
                },
            )
        }

        composable(
            route = Screen.DoneDetail.ROUTE,
            arguments = listOf(navArgument(Screen.DoneDetail.ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val articleId = backStackEntry.arguments?.getString(Screen.DoneDetail.ARG).orEmpty()
            DoneDetailScreen(
                articleId = articleId,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
