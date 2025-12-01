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

// Set of Material typography styles to start with
val Typography = Typography(
    displayMedium = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
    ),
    displaySmall = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
    ),
    headlineLarge = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
    ),
    headlineMedium = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
    ),
    headlineSmall = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
    ),
    titleLarge = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
    ),
    titleMedium = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
    ),
    titleSmall = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
    ),
    bodyLarge = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
    ),
    bodyMedium = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
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
    labelSmall = TextStyle(
        fontFamily = LabGrotesqueFontFamily,
    ),
)