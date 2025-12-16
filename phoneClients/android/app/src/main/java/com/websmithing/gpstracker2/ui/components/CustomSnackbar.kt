package com.websmithing.gpstracker2.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.theme.extendedColors

enum class CustomSnackbarType {
    success,
    warning
}

@Composable
fun CustomSnackbar(
    modifier: Modifier = Modifier,
    type: CustomSnackbarType,
    message: String,
    onClick: () -> Unit
) {
    Snackbar(
        containerColor = when (type) {
            CustomSnackbarType.success -> MaterialTheme.extendedColors.okContainer
            CustomSnackbarType.warning -> MaterialTheme.extendedColors.warningContainer
        },
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RectangleShape,
        modifier = modifier.clickable(true, onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painterResource(
                    when (type) {
                        CustomSnackbarType.success -> R.drawable.ic_ok_16
                        CustomSnackbarType.warning -> R.drawable.ic_warning_16
                    }
                ),
                tint = Color.Unspecified,
                contentDescription = null
            )
            Text(message)
        }
    }
}