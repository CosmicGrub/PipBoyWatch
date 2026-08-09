package com.pipboywatch.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.pipboywatch.app.ui.home.HomeDialScreen
import com.pipboywatch.app.ui.tab.DataScreen
import com.pipboywatch.app.ui.tab.InvScreen
import com.pipboywatch.app.ui.tab.MapScreen
import com.pipboywatch.app.ui.tab.PlaceholderTabScreen
import com.pipboywatch.app.ui.tab.RadioScreen
import com.pipboywatch.app.ui.tab.StatScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_TAB = "tab/{tabName}"
private const val ARG_TAB_NAME = "tabName"

/**
 * Root composable: home dial <-> tab content, with Wear's standard
 * swipe-to-dismiss back gesture provided for free by SwipeDismissableNavHost.
 */
@Composable
fun PipBoyApp() {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = ROUTE_HOME
    ) {
        composable(ROUTE_HOME) {
            HomeDialScreen(
                onTabSelected = { tab -> navController.navigate("tab/${tab.name}") }
            )
        }
        composable(
            route = ROUTE_TAB,
            arguments = listOf(navArgument(ARG_TAB_NAME) { type = NavType.StringType })
        ) { backStackEntry ->
            val tabName = backStackEntry.arguments?.getString(ARG_TAB_NAME) ?: PipBoyTab.STAT.name
            when (val tab = PipBoyTab.valueOf(tabName)) {
                PipBoyTab.STAT -> StatScreen()
                PipBoyTab.INV -> InvScreen()
                PipBoyTab.DATA -> DataScreen()
                PipBoyTab.MAP -> MapScreen()
                PipBoyTab.RADIO -> RadioScreen()
                else -> PlaceholderTabScreen(tab)
            }
        }
    }
}
