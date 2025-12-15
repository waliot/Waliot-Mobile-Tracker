package com.websmithing.gpstracker2.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.websmithing.gpstracker2.ui.router.AppRouter
import com.websmithing.gpstracker2.ui.theme.WaliotTheme

@Composable
fun App(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    WaliotTheme {
        AppRouter(
            navController = navController,
            modifier = modifier,
        )
    }
}