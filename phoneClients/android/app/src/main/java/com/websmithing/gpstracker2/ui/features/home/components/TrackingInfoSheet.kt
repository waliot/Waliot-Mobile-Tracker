package com.websmithing.gpstracker2.ui.features.home.components

import android.content.Context
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.components.DragHandle
import com.websmithing.gpstracker2.ui.theme.WaliotTheme
import com.websmithing.gpstracker2.ui.theme.extendedColors
import kotlinx.coroutines.flow.StateFlow
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val coordinateFormatter = DecimalFormat("0.00000")
private val distanceFormatter = DecimalFormat("0.0")

@Composable
fun TrackingInfoSheet(
    userName: LiveData<String>,
    location: StateFlow<Location?>,
    totalDistance: StateFlow<Float>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val userNameValue by userName.observeAsState()
    val locationValue by location.collectAsStateWithLifecycle()
    val totalDistanceValue by totalDistance.collectAsStateWithLifecycle()

    Sheet(
        onDismissRequest = onDismissRequest,
        userName = userNameValue,
        location = locationValue,
        totalDistance = totalDistanceValue,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(
    userName: String?,
    location: Location?,
    totalDistance: Float,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss, dd.MM.yy", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.extendedColors.appBar,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RectangleShape,
        dragHandle = { DragHandle() },
        scrimColor = Color.Transparent,
        modifier = modifier,
    ) {
        UserNameBadge(value = userName)

        ProvideTextStyle(MaterialTheme.typography.labelMedium) {
            Grid(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                item {
                    Text(
                        context.getString(R.string.coordinates),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item(span = { GridItemSpan(2) }) {
                    Text(
                        if (location == null)
                            context.getString(R.string.search_satellites)
                        else "${
                            coordinateFormatter.format(location.latitude)
                        }, ${coordinateFormatter.format(location.longitude)}"
                    )
                }

                item {
                    Text(
                        context.getString(R.string.speed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )
                }

                item(span = { GridItemSpan(2) }) {
                    Text(
                        if (location == null)
                            context.getString(R.string.no_data_placeholder)
                        else
                            context.getString(R.string.speed_format_kmh, location.speed * 3.6)
                    )
                }
            }

            Grid(
                modifier = Modifier.background(Color(0xFF1D2328))
            ) {
                item {
                    Text(
                        context.getString(R.string.signal_strength),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item(span = { GridItemSpan(2) }) {
                    Text(
                        if (location == null)
                            context.getString(R.string.no_data_placeholder)
                        else getSignalStrengthDescription(location.accuracy)
                    )
                }

                item {
                    Text(
                        context.getString(R.string.update_time),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item(span = { GridItemSpan(2) }) {
                    Text(
                        if (location == null)
                            context.getString(R.string.no_data_placeholder)
                        else timeFormatter.format(location.time)
                    )
                }
            }

            ExtraInfo(context, location, totalDistance)
        }
    }
}

@Composable
private fun ExtraInfo(
    context: Context,
    location: Location?,
    totalDistance: Float
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Text(
            context.getString(R.string.extra_data),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ChipItem(
                label = context.getString(R.string.accuracy),
                value = if (location == null)
                    context.getString(R.string.no_data_placeholder)
                else
                    context.getString(R.string.accuracy_format, location.accuracy)
            )

            ChipItem(
                label = context.getString(R.string.distance),
                value = if (totalDistance == 0f)
                    context.getString(R.string.no_data_placeholder)
                else
                    context.getString(
                        R.string.distance_format_km,
                        distanceFormatter.format(totalDistance / 1000f)
                    )
            )

            ChipItem(
                label = context.getString(R.string.altitude),
                value = if (location == null)
                    context.getString(R.string.no_data_placeholder)
                else
                    context.getString(R.string.altitude_format, location.altitude)
            )

            ChipItem(
                label = context.getString(R.string.bearing),
                value = if (location == null)
                    context.getString(R.string.no_data_placeholder)
                else
                    context.getString(R.string.bearing_format, location.bearing)
            )
        }
    }
}

@Composable
fun UserNameBadge(modifier: Modifier = Modifier, value: String?) {
    val noUserName = value == null || value.isEmpty()
    Text(
        if (noUserName) "Укажите идентификатор" else value,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .clip(shape = MaterialTheme.shapes.extraSmall)
            .background(
                color = if (noUserName)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
private fun Grid(modifier: Modifier = Modifier, content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
        modifier = modifier.padding(vertical = 12.dp, horizontal = 16.dp)
    )
}

@Composable
private fun ChipItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            shape = RoundedCornerShape(2.dp)
        )
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .background(MaterialTheme.extendedColors.hover)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

/**
 * Gets a human-readable description of signal strength based on accuracy
 *
 * @param accuracy The accuracy of the location in meters
 * @return A string describing the signal strength
 */
@Composable
private fun getSignalStrengthDescription(accuracy: Float): String {
    val context = LocalContext.current
    return when {
        accuracy <= 0 -> context.getString(R.string.signal_unknown) // Accuracy shouldn't be <= 0
        accuracy <= 10 -> context.getString(R.string.signal_excellent) // meters
        accuracy <= 25 -> context.getString(R.string.signal_good)
        accuracy <= 50 -> context.getString(R.string.signal_fair)
        else -> context.getString(R.string.signal_poor)
    }
}

@Preview
@Composable
private fun TrackingInfoSheetPreview() {
    WaliotTheme {
        Sheet(onDismissRequest = {}, userName = null, location = null, totalDistance = 0f)
    }
}

@Preview
@Composable
private fun TrackingInfoSheetEmpty() {
    WaliotTheme {
        Sheet(
            onDismissRequest = {},
            userName = "89181201004",
            location = Location(""),
            totalDistance = 7000f
        )
    }
}