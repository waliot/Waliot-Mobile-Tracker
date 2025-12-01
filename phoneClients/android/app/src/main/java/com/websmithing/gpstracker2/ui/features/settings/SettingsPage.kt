package com.websmithing.gpstracker2.ui.features.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.theme.WaliotTheme
import com.websmithing.gpstracker2.ui.theme.customTopAppBarColors

@Composable
fun SettingsPage(modifier: Modifier = Modifier, navController: NavHostController) {
    Page(onBack = { navController.navigateUp() }, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                colors = customTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_close_24),
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) { }
}

@Preview
@Composable
private fun PagePreview() {
    WaliotTheme {
        Page(onBack = {})
    }
}