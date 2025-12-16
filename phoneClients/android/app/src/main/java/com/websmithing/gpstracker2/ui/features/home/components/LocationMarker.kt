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
    Active,
    Inactive,
    Error
}

val LocationMarkerSize = 48.dp

@Composable
fun LocationMarker(
    modifier: Modifier = Modifier,
    state: LocationMarkerState = LocationMarkerState.Inactive,
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
            LocationMarkerState.Active -> MaterialTheme.colorScheme.primary
            LocationMarkerState.Inactive -> MaterialTheme.extendedColors.fab
            LocationMarkerState.Error -> MaterialTheme.colorScheme.error
        },
        contentColor = when (state) {
            LocationMarkerState.Active, LocationMarkerState.Error -> Color.White
            LocationMarkerState.Inactive -> MaterialTheme.extendedColors.onFab
        },
        shape = teardropShape,
        onClick = onClick,
        modifier = modifier.size(LocationMarkerSize)
    ) {
        Icon(
            painterResource(R.drawable.ic_person_32),
            contentDescription = "Текущая локация",
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
        LocationMarker(onClick = {}, state = LocationMarkerState.Active)
    }
}

@Preview
@Composable
private fun LocationMarkerErrorPreview() {
    WaliotTheme {
        LocationMarker(onClick = {}, state = LocationMarkerState.Error)
    }
}
