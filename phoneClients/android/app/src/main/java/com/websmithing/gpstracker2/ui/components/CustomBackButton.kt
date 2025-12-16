package com.websmithing.gpstracker2.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.theme.WaliotTheme

@Composable
fun CustomBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            painterResource(R.drawable.ic_close_24),
            contentDescription = stringResource(R.string.close),
            modifier = Modifier.size(28.dp)
        )
    }
}

@Preview
@Composable
private fun CustomBackButtonPreview() {
    WaliotTheme {
        CustomBackButton(onBack = {})
    }
}
