package com.websmithing.gpstracker2.ui.router

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.websmithing.gpstracker2.ui.features.home.HomePage
import com.websmithing.gpstracker2.ui.features.splash.SplashPage

@Composable
fun AppRouter(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Splash,
        modifier = modifier
    ) {
        composable<AppDestination.Splash> { SplashPage() }
        composable<AppDestination.Home> { HomePage() }
    }
}