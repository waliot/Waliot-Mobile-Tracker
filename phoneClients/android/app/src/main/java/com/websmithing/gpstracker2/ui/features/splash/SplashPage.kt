package com.websmithing.gpstracker2.ui.features.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.router.AppDestination
import com.websmithing.gpstracker2.ui.theme.WaliotTheme
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

    Splash()
}

@Composable
fun Splash(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary)
            .fillMaxSize()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
                painterResource(R.drawable.logo_full),
                contentDescription = stringResource(R.string.app_name),
            )
            Text(
                stringResource(R.string.splash_logo_text),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Preview
@Composable
private fun SplashPreview() {
    WaliotTheme {
        Splash()
    }
}