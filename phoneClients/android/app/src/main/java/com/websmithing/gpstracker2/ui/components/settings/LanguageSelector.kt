package com.websmithing.gpstracker2.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.websmithing.gpstracker2.ui.theme.IconTintSecondary
import com.websmithing.gpstracker2.ui.theme.SurfaceRaised
import com.websmithing.gpstracker2.ui.theme.SurfaceSecondary
import com.websmithing.gpstracker2.ui.theme.TextPrimary

data class LanguageOption(
    val code: String,
    val label: String,
    val icon: Int
)

/**
 * Renders a segmented control allowing users to select one language from a list of options.
 *
 * @param options A list of available language options to display
 * @param selectedLanguage The currently selected language code
 * @param onLanguageSelected Callback invoked when the user selects a different language
 * @param modifier Optional modifier applied to the root container
 * @param title Optional text label shown above the selector
 * @param labelSpacing Vertical spacing between the title and the selector row
 * @param showTitle Controls visibility of the optional title
 */
@Composable
fun LanguageSelector(
    options: List<LanguageOption>,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    labelSpacing: Dp = 8.dp,
    showTitle: Boolean = true
) {
    Column(modifier = modifier) {
        if (showTitle && title != null) {
            Text(
                text = title,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = IconTintSecondary
            )
            Spacer(modifier = Modifier.height(labelSpacing))
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    shape = when (index) {
                        0 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                        options.size - 1 -> RoundedCornerShape(
                            topEnd = 4.dp,
                            bottomEnd = 4.dp
                        )
                        else -> RoundedCornerShape(0.dp)
                    },
                    icon = {},
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = SurfaceRaised,
                        inactiveContainerColor = SurfaceSecondary
                    ),
                    border = BorderStroke(0.dp, Color.Transparent),
                    onClick = { onLanguageSelected(option.code) },
                    selected = option.code == selectedLanguage
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(option.icon),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = option.label,
                            color = if (option.code == selectedLanguage) TextPrimary else IconTintSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

