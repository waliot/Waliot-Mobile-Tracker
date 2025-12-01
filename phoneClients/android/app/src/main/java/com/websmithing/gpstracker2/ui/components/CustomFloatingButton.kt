package com.websmithing.gpstracker2.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.theme.WaliotTheme
import com.websmithing.gpstracker2.ui.theme.extendedColors

@Composable
fun CustomFloatingButton(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.extendedColors.fab,
    contentColor: Color = MaterialTheme.extendedColors.onFab,
    shape: Shape = CircleShape,
    elevation: Dp = 4.dp,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val animatedContentColor: Color by animateColorAsState(targetValue = contentColor)
    val animatedColor: Color by animateColorAsState(targetValue = color)

    Surface(
        color = animatedColor,
        contentColor = animatedContentColor,
        shape = shape,
        shadowElevation = elevation,
        onClick = onClick,
        modifier = modifier,
    ) {
        icon()
    }
}

@Preview
@Composable
private fun CustomFloatingButtonPreview() {
    WaliotTheme {
        CustomFloatingButton(onClick = {}) {
            Icon(
                painterResource(R.drawable.ic_person_32),
                contentDescription = null,
                modifier = Modifier.requiredSize(32.dp)
            )
        }
    }
}