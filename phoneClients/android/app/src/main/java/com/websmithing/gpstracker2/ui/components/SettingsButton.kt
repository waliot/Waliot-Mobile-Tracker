package com.websmithing.gpstracker2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.theme.IconTintSecondary
import com.websmithing.gpstracker2.ui.theme.SurfaceTertiary

/**
 * Displays a circular settings button with an icon, intended for use in toolbars or overlays.
 *
 * @param modifier Optional modifier applied to the button container
 * @param onClick Callback triggered when the button is pressed
 */
@Composable
fun SettingsButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SurfaceTertiary)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_settings),
            contentDescription = null,
            tint = IconTintSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
