package com.websmithing.gpstracker2.ui.features.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.router.AppDestination
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun SplashPage(navController: NavHostController, modifier: Modifier = Modifier) {
    LaunchedEffect(true) {
        delay(3.seconds)
        navController.navigate(AppDestination.Home) {
            popUpTo(AppDestination.Splash) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary)
            .fillMaxSize()
    ) {
        Image(
            painterResource(R.drawable.splash_logo),
            contentDescription = stringResource(R.string.app_name),
        )
    }
}