package com.websmithing.gpstracker2.ui.components.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.websmithing.gpstracker2.ui.theme.ButtonPrimaryOverlay
import com.websmithing.gpstracker2.ui.theme.DividerSecondary
import com.websmithing.gpstracker2.ui.theme.SurfaceRaised
import com.websmithing.gpstracker2.ui.theme.TextPrimary

/**
 * Renders a single labeled data cell with a configured title and value.
 *
 * @param title The label describing the value
 * @param value The displayed value associated with the label
 */
@Composable
fun DataItem(title: String, value: String) {
    Box(
        modifier = Modifier
            .height(20.dp)
            .background(Color.Transparent, RoundedCornerShape(4.dp)
            )
            .border(width = 0.5.dp, color = DividerSecondary)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(SurfaceRaised)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$title:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(ButtonPrimaryOverlay, RoundedCornerShape(1.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextPrimary
                )
            }
        }
    }
}