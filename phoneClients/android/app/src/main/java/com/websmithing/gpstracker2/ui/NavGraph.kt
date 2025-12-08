package com.websmithing.gpstracker2.ui

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.websmithing.gpstracker2.ui.screens.GpsTrackerScreen
import com.websmithing.gpstracker2.ui.screens.TrackerSettingsScreen

/**
 * Defines the navigation graph for the app using Jetpack Compose Navigation.
 *
 * Sets up composable destinations for the GPS tracker screen and tracker settings screen,
 * passing the shared TrackingViewModel and NavHostController to each destination.
 *
 * @param navController Controller that handles app navigation between composables.
 * @param viewModel Shared ViewModel for tracking-related data and actions.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    NavHost(
        navController = navController,
        startDestination = "GpsTrackerScreen"
    ) {
        composable("GpsTrackerScreen") { backStackEntry ->
            GpsTrackerScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable("TrackerSettingsScreen") { backStackEntry ->
            TrackerSettingsScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
    }
}

