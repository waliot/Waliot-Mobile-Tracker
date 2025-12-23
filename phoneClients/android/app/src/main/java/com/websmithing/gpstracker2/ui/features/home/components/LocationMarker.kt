package com.websmithing.gpstracker2.ui.features.home.components

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.components.CustomFloatingButton
import com.websmithing.gpstracker2.ui.theme.WaliotTheme
import com.websmithing.gpstracker2.ui.theme.extendedColors

enum class LocationMarkerState {
    ACTIVE,
    INACTIVE,
    ERROR
}

val LocationMarkerSize = 48.dp

@Composable
fun LocationMarker(
    modifier: Modifier = Modifier,
    state: LocationMarkerState = LocationMarkerState.INACTIVE,
    onClick: () -> Unit,
) {
    val teardropShape = RoundedCornerShape(
        topStartPercent = 50,
        topEndPercent = 0,
        bottomEndPercent = 50,
        bottomStartPercent = 50
    )

    CustomFloatingButton(
        color = when (state) {
            LocationMarkerState.ACTIVE -> MaterialTheme.colorScheme.primary
            LocationMarkerState.INACTIVE -> MaterialTheme.extendedColors.fab
            LocationMarkerState.ERROR -> MaterialTheme.colorScheme.error
        },
        contentColor = when (state) {
            LocationMarkerState.ACTIVE, LocationMarkerState.ERROR -> Color.White
            LocationMarkerState.INACTIVE -> MaterialTheme.extendedColors.onFab
        },
        shape = teardropShape,
        onClick = onClick,
        modifier = modifier.size(LocationMarkerSize)
    ) {
        Icon(
            painterResource(R.drawable.ic_person_32),
            contentDescription = null,
            modifier = Modifier.requiredSize(32.dp)
        )
    }
}

@Preview
@Composable
private fun LocationMarkerPreview() {
    WaliotTheme {
        LocationMarker(onClick = {})
    }
}

@Preview
@Composable
private fun LocationMarkerActivePreview() {
    WaliotTheme {
        LocationMarker(onClick = {}, state = LocationMarkerState.ACTIVE)
    }
}

@Preview
@Composable
private fun LocationMarkerErrorPreview() {
    WaliotTheme {
        LocationMarker(onClick = {}, state = LocationMarkerState.ERROR)
    }
}
