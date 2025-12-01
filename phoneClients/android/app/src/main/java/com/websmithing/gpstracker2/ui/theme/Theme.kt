package com.websmithing.gpstracker2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ExtendedColors(
    val fab: Color,
    val onFab: Color,
    val ok: Color,
    val appBar: Color,
    val hover: Color,
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }

private val DarkExtendedColors = ExtendedColors(
    fab = fabColor,
    onFab = onSurfaceVariantColor,
    ok = okColor,
    appBar = appBarColor,
    hover = hoverColor,
)

private val DarkColorScheme = darkColorScheme(
    primary = primaryColor,
    error = errorColor,
    surface = surfaceColor,
    surfaceContainer = surfaceContainerColor,
    onSurface = onSurfaceColor,
    onSurfaceVariant = onSurfaceVariantColor,
    onPrimary = onPrimaryColor
)

@Composable
fun customTopAppBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.extendedColors.appBar,
    scrolledContainerColor = MaterialTheme.extendedColors.appBar,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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