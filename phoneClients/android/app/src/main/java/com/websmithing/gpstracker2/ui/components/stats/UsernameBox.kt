package com.websmithing.gpstracker2.ui.components.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.theme.AccentPrimary
import com.websmithing.gpstracker2.ui.theme.AccentSecondary
import com.websmithing.gpstracker2.ui.theme.TextPrimary

/**
 * Displays a username badge with dynamic styling depending on whether a username is set.
 *
 * @param username The username text to display; empty string triggers placeholder mode
 * @param modifier Optional modifier applied to the badge container
 */
@Composable
fun UsernameBox(
    username: String,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (username.isEmpty()) AccentSecondary else AccentPrimary
    val displayText = if (username.isNotEmpty()) username else stringResource(R.string.stats_id_specify)

    Box(
        modifier = modifier
            .height(24.dp)
            .background(backgroundColor, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}