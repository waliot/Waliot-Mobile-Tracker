package com.websmithing.gpstracker2.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.websmithing.gpstracker2.R

val LabGrotesqueFontFamily = FontFamily(
    listOf(
        Font(R.font.lab_grotesque),
        Font(R.font.lab_grotesque_medium, weight = FontWeight.Medium),
        Font(R.font.lab_grotesque_bold, weight = FontWeight.Bold),
    )
)

private val defaultTypography = Typography()
val Typography = Typography(
    displayMedium = defaultTypography.displayMedium.copy(
        fontFamily = LabGrotesqueFontFamily,
    ),
    displaySmall = defaultTypography.displaySmall.copy(
        fontFamily = LabGrotesqueFontFamily,
    ),
    headlineLarge = defaultTypography.headlineLarge.copy(
        fontFamily = LabGrotesqueFontFamily,
    ),
    headlineMedium = defaultTypography.headlineMedium.copy(
        fontFamily = LabGrotesqueFontFamily,
    ),
    headlineSmall = defaultTypography.headlineSmall.copy(
        fontFamily = LabGrotesqueFontFamily,
    ),
    titleLarge = defaultTypography.titleLarge.copy(
        fontFamily = LabGrotesqueFontFamily,
    ),
    titleMedium = defaultTypography.titleMedium.copy(
        fontFamily = LabGrotesqueFontFamily,
    ),
    titleSmall = defaultTypography.titleSmall.copy(
        fontFamily = LabGrotesqueFontFamily,
    ),
    bodyLarge = defaultTypography.bodyLarge.copy(
        fontFamily = LabGrotesqueFontFamily,
    ),
    bodyMedium = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    bodySmall = defaultTypography.bodySmall.copy(
        fontFamily = LabGrotesqueFontFamily,
    ),
    labelLarge = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
        fontSize = 12.sp,
        lineHeight = 15.sp
    ),
    labelSmall = defaultTypography.labelSmall.copy(
        fontFamily = LabGrotesqueFontFamily,
    ),
)
