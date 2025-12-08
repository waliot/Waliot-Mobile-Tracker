package com.websmithing.gpstracker2.ui.components.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.theme.IconTintSecondary
import com.websmithing.gpstracker2.ui.theme.SurfaceBase

/**
 * Displays a section with additional statistic items in a structured layout.
 *
 * @param data A list of key–value pairs representing the labels and values to render
 * @param modifier Optional modifier applied to the container
 */
@Composable
fun AdditionalDataSection(
    data: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceBase)
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.stats_additional_data),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = IconTintSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val allEmpty = data.all { it.second == "–" }

            if (allEmpty) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    data.forEach { (title, value) ->
                        DataItem(title, value)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DataItem(data[0].first, data[0].second)
                    DataItem(data[1].first, data[1].second)
                }
                if (data.size > 2) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DataItem(data[2].first, data[2].second)
                    }
                }
            }
        }
    }
}