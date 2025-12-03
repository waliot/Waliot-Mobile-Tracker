package com.websmithing.gpstracker2.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class ExtendedColors(
    val fab: Color,
    val onFab: Color,
    val ok: Color,
    val appBar: Color,
    val hover: Color,
    val tertiaryText: Color,
    val quaternaryText: Color
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }

private val DarkExtendedColors = ExtendedColors(
    fab = fabColor,
    onFab = onSurfaceVariantColor,
    ok = okColor,
    appBar = appBarColor,
    hover = hoverColor,
    tertiaryText = tertiaryTextColor,
    quaternaryText = quaternaryTextColor
)

private val DarkColorScheme = darkColorScheme(
    primary = primaryColor,
    error = errorColor,
    surface = surfaceColor,
    surfaceContainer = surfaceContainerColor,
    surfaceContainerLowest = surfaceContainerLowestColor,
    onSurface = onSurfaceColor,
    onSurfaceVariant = onSurfaceVariantColor,
    onPrimary = onPrimaryColor
)

@Composable
fun WaliotTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalExtendedColors provides DarkExtendedColors) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = Typography,
            content = content
        )
    }
}

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current

@Composable
fun customTopAppBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.extendedColors.appBar,
    scrolledContainerColor = MaterialTheme.extendedColors.appBar,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
fun customOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent
)

@Composable
fun customSegmentedButtonColors() = SegmentedButtonDefaults.colors(
    activeContentColor = MaterialTheme.colorScheme.onSurface,
    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    activeContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    activeBorderColor = Color.Transparent,
    disabledActiveBorderColor = Color.Transparent,
    disabledInactiveBorderColor = Color.Transparent,
    inactiveBorderColor = Color.Transparent,
)

@Composable
fun customButtonColors() = ButtonDefaults.buttonColors(
    disabledContainerColor = MaterialTheme.extendedColors.hover,
    disabledContentColor = MaterialTheme.extendedColors.quaternaryText,
)

@Composable
fun customButtonShape() = RoundedCornerShape(4.dp)

@Composable
@ReadOnlyComposable
fun customSegmentedButtonShape(index: Int, count: Int): Shape {
    val baseShape = MaterialTheme.shapes.extraSmall

    return when (index) {
        0 -> baseShape.copy(topEnd = ZeroCornerSize, bottomEnd = ZeroCornerSize)
        count - 1 -> baseShape.copy(topStart = ZeroCornerSize, bottomStart = ZeroCornerSize)
        else -> RectangleShape
    }
}