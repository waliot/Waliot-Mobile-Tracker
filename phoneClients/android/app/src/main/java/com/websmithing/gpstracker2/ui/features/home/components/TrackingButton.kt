package com.websmithing.gpstracker2.ui.features.home.components

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.components.CustomFloatingButton
import com.websmithing.gpstracker2.ui.modifiers.debounced
import com.websmithing.gpstracker2.ui.theme.WaliotTheme
import com.websmithing.gpstracker2.ui.theme.extendedColors

enum class TrackingButtonState {
    Stop,
    Play,
    Pause,
}

val TrackingButtonSize = 56.dp

@Composable
fun TrackingButton(
    modifier: Modifier = Modifier,
    state: TrackingButtonState = TrackingButtonState.Stop,
    onClick: () -> Unit,
) {
    CustomFloatingButton(
        color = when (state) {
            TrackingButtonState.Play -> MaterialTheme.extendedColors.ok
            TrackingButtonState.Stop -> MaterialTheme.extendedColors.fab
            TrackingButtonState.Pause -> MaterialTheme.colorScheme.error
        },
        contentColor = when (state) {
            TrackingButtonState.Play, TrackingButtonState.Pause -> Color.White
            TrackingButtonState.Stop -> MaterialTheme.extendedColors.onFab
        },
        elevation = 12.dp,
        onClick = debounced(onClick = {
            onClick()
        }),
        modifier = modifier.size(TrackingButtonSize)
    ) {
        when (state) {
            TrackingButtonState.Play -> Icon(
                painterResource(R.drawable.ic_play_24),
                contentDescription = stringResource(R.string.tracking_is_off),
                modifier = Modifier.requiredSize(24.dp)
            )

            TrackingButtonState.Stop -> Icon(
                painterResource(R.drawable.ic_stop_24),
                contentDescription = stringResource(R.string.tracking_is_off),
                modifier = Modifier.requiredSize(24.dp)
            )

            TrackingButtonState.Pause -> Icon(
                painterResource(R.drawable.ic_pause_24),
                contentDescription = stringResource(R.string.tracking_is_on),
                modifier = Modifier.requiredSize(24.dp)
            )
        }
    }
}

@Preview
@Composable
private fun TrackingButtonPreview() {
    WaliotTheme {
        TrackingButton(onClick = {})
    }
}

@Preview
@Composable
private fun TrackingButtonStartPreview() {
    WaliotTheme {
        TrackingButton(onClick = {}, state = TrackingButtonState.Play)
    }
}

@Preview
@Composable
private fun TrackingButtonStopPreview() {
    WaliotTheme {
        TrackingButton(onClick = {}, state = TrackingButtonState.Pause)
    }
}