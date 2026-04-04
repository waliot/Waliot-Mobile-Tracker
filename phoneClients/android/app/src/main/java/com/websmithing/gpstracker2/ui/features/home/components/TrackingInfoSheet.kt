package com.websmithing.gpstracker2.ui.features.home.components

import android.content.Context
import android.location.Location
import android.text.format.DateFormat
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.repository.upload.UploadStatus
import com.websmithing.gpstracker2.ui.features.home.TrackingLocationPresentation
import com.websmithing.gpstracker2.ui.features.home.TrackingLocationUiState
import com.websmithing.gpstracker2.ui.components.CustomDragHandle
import com.websmithing.gpstracker2.ui.theme.WaliotTheme
import com.websmithing.gpstracker2.ui.theme.extendedColors
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val coordinateFormatter = DecimalFormat("0.000000")

@Composable
fun TrackingInfoSheet(
    trackerIdentifier: String?,
    location: Location?,
    locationPresentation: TrackingLocationPresentation,
    lastUploadStatus: UploadStatus?,
    bufferCount: Int,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Sheet(
        onDismissRequest = onDismissRequest,
        trackerIdentifier = trackerIdentifier,
        location = location,
        locationPresentation = locationPresentation,
        lastUploadStatus = lastUploadStatus,
        bufferCount = bufferCount,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(
    trackerIdentifier: String?,
    location: Location?,
    locationPresentation: TrackingLocationPresentation,
    lastUploadStatus: UploadStatus?,
    bufferCount: Int,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.extendedColors.appBar,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RectangleShape,
        dragHandle = { CustomDragHandle() },
        scrimColor = Color.Transparent,
        modifier = modifier,
    ) {
        TrackerIdentifierBadge(value = trackerIdentifier)

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
                        if (location == null) context.getString(R.string.search_satellites)
                        else "${coordinateFormatter.format(location.latitude)}; ${coordinateFormatter.format(location.longitude)}"
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
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                item {
                    Text(
                        context.getString(R.string.location_status),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item(span = { GridItemSpan(2) }) {
                    Text(
                        if (location == null)
                            context.getString(R.string.no_data_placeholder)
                        else getLocationStatusDescription(locationPresentation)
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
                        if (location == null) {
                            context.getString(R.string.no_data_placeholder)
                        } else {
                            when (lastUploadStatus) {
                                null, is UploadStatus.Idle -> context.getString(R.string.no_data_placeholder)
                                is UploadStatus.Success -> context.getString(
                                    R.string.upload_status_success,
                                    formatUpdateTime(context, lastUploadStatus.uploadedAtMillis)
                                )

                                is UploadStatus.Offline -> context.getString(
                                    R.string.upload_status_offline
                                )

                                is UploadStatus.Failure -> context.getString(
                                    R.string.upload_status_failure,
                                    context.getString(R.string.no_data_placeholder)
                                )
                            }
                        }
                    )
                }
            }

            ExtraInfo(context, locationPresentation, bufferCount)
        }
    }
}

@Composable
private fun ExtraInfo(
    context: Context,
    locationPresentation: TrackingLocationPresentation,
    bufferCount: Int
) {
    val location = locationPresentation.trustedLocation
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
                value = if (locationPresentation.accuracyMeters == null)
                    context.getString(R.string.no_data_placeholder)
                else
                    context.getString(R.string.accuracy_format, locationPresentation.accuracyMeters)
            )

            ChipItem(
                label = context.getString(R.string.provider),
                value = describeLocationProvider(context, locationPresentation.provider)
            )

            ChipItem(
                label = context.getString(R.string.fix_age),
                value = locationPresentation.fixAgeMillis?.let { formatFixAge(context, it) }
                    ?: context.getString(R.string.no_data_placeholder)
            )

            ChipItem(
                label = stringResource(R.string.data_buffer),
                value = if (bufferCount > 0) {
                    bufferCount.toString()
                } else {
                    context.getString(R.string.no_data_placeholder)
                }
            )

            if (locationPresentation.state == TrackingLocationUiState.Suspect) {
                ChipItem(
                    label = context.getString(R.string.warning),
                    value = getLocationWarningDescription(locationPresentation)
                )
            }
        }
    }
}

@Composable
fun TrackerIdentifierBadge(modifier: Modifier = Modifier, value: String?) {
    val noValue = value.isNullOrBlank()
    Text(
        if (noValue) stringResource(R.string.provide_identifier) else value,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .clip(shape = MaterialTheme.shapes.extraSmall)
            .background(
                color = if (noValue)
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

@Composable
private fun getLocationStatusDescription(locationPresentation: TrackingLocationPresentation): String {
    val context = LocalContext.current
    return when (locationPresentation.state) {
        TrackingLocationUiState.NoFix -> context.getString(R.string.location_state_no_fix)
        TrackingLocationUiState.FreshGps -> context.getString(R.string.location_state_fresh_gps)
        TrackingLocationUiState.FreshDegraded -> context.getString(R.string.location_state_fresh_degraded)
        TrackingLocationUiState.StaleGps -> context.getString(R.string.location_state_stale_gps)
        TrackingLocationUiState.StaleDegraded -> context.getString(R.string.location_state_stale_degraded)
        TrackingLocationUiState.Suspect -> context.getString(R.string.location_state_suspect)
    }
}

@Composable
private fun getLocationWarningDescription(locationPresentation: TrackingLocationPresentation): String {
    val context = LocalContext.current
    return when (locationPresentation.issue) {
        com.websmithing.gpstracker2.repository.location.LocationFixIssue.MockFix ->
            context.getString(R.string.location_warning_mock)
        com.websmithing.gpstracker2.repository.location.LocationFixIssue.ImpossibleJump ->
            context.getString(R.string.location_warning_impossible_jump)
        com.websmithing.gpstracker2.repository.location.LocationFixIssue.TimestampRegression ->
            context.getString(R.string.location_warning_timestamp_regression)
        com.websmithing.gpstracker2.repository.location.LocationFixIssue.StaleFix ->
            context.getString(R.string.location_warning_stale)
        com.websmithing.gpstracker2.repository.location.LocationFixIssue.NoProviders,
        null -> context.getString(R.string.location_warning_unknown)
    }
}

private fun formatFixAge(context: Context, ageMillis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ageMillis)
    if (totalSeconds <= 0) {
        return context.getString(R.string.fix_age_just_now)
    }
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        context.getString(R.string.fix_age_minutes_seconds_format, minutes, seconds)
    } else {
        context.getString(R.string.fix_age_seconds_format, seconds)
    }
}

private fun describeLocationProvider(context: Context, provider: String?): String {
    return when (provider?.lowercase(Locale.ROOT)) {
        null -> context.getString(R.string.no_data_placeholder)
        "gps" -> context.getString(R.string.provider_gps)
        "network" -> context.getString(R.string.provider_network)
        "fused" -> context.getString(R.string.provider_fused)
        "passive" -> context.getString(R.string.provider_passive)
        "persisted" -> context.getString(R.string.provider_buffered)
        else -> provider
    }
}

private fun formatUpdateTime(context: Context, timeMillis: Long): String {
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, "HHmmssddMMyy")
    return SimpleDateFormat(pattern, locale).format(Date(timeMillis))
}

@Preview
@Composable
private fun SheetPreview() {
    WaliotTheme {
        Sheet(
            onDismissRequest = {},
            trackerIdentifier = "89181201004",
            location = Location(""),
            locationPresentation = TrackingLocationPresentation(
                state = TrackingLocationUiState.FreshGps,
                trustedLocation = Location("gps"),
                provider = "gps",
                accuracyMeters = 5f,
                fixAgeMillis = 15_000L,
            ),
            lastUploadStatus = UploadStatus.Success(uploadedAtMillis = 1_700_000_000_000L),
            bufferCount = 7
        )
    }
}

@Preview
@Composable
private fun EmptySheetPreview() {
    WaliotTheme {
        Sheet(
            onDismissRequest = {},
            trackerIdentifier = null,
            location = null,
            locationPresentation = TrackingLocationPresentation(),
            lastUploadStatus = null,
            bufferCount = 0
        )
    }
}
