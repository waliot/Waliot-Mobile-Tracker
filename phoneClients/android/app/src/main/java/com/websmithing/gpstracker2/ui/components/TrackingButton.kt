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
import com.websmithing.gpstracker2.ui.theme.AccentSecondary
import com.websmithing.gpstracker2.ui.theme.AccentSuccessAction
import com.websmithing.gpstracker2.ui.theme.IconTintSecondary
import com.websmithing.gpstracker2.ui.theme.SurfaceTertiary
import com.websmithing.gpstracker2.ui.theme.TextPrimary

enum class TrackingButtonState {
    Disabled,
    Tracking,
    Stopped
}

/**
 * Displays a circular tracking control button whose appearance changes based on tracking state.
 *
 * @param modifier Optional modifier applied to the button container
 * @param status Current tracking state determining background, icon, and tint
 * @param onClick Callback triggered when the button is pressed
 */
@Composable
fun TrackingButton(
    modifier: Modifier = Modifier,
    status: TrackingButtonState = TrackingButtonState.Disabled,
    onClick: () -> Unit
) {
    val (bgColor, iconRes, iconTint) = when (status) {
        TrackingButtonState.Disabled -> Triple(
            SurfaceTertiary,
            R.drawable.ic_stop,
            IconTintSecondary
        )
        TrackingButtonState.Tracking -> Triple(
            AccentSecondary,
            R.drawable.ic_pause,
            TextPrimary
        )
        TrackingButtonState.Stopped -> Triple(
            AccentSuccessAction,
            R.drawable.ic_start,
            TextPrimary
        )
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

