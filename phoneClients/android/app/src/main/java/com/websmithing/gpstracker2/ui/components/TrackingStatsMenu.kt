package com.websmithing.gpstracker2.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.websmithing.gpstracker2.ui.TrackingViewModel
import kotlinx.coroutines.launch
import com.websmithing.gpstracker2.data.repository.UploadStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.components.stats.AdditionalDataSection
import com.websmithing.gpstracker2.ui.components.stats.MenuSlider
import com.websmithing.gpstracker2.ui.components.stats.MenuRow
import com.websmithing.gpstracker2.ui.components.stats.UsernameBox
import com.websmithing.gpstracker2.ui.theme.SurfaceBase
import com.websmithing.gpstracker2.ui.theme.SurfacePrimary
import com.websmithing.gpstracker2.ui.theme.SurfaceSecondary

/**
 * Displays a bottom sliding statistics panel for the tracking screen.
 *
 * The panel includes user information, location metrics, GPS/GSM signal details,
 * upload status, and additional tracking data. It animates vertically when shown
 * or hidden and closes automatically when the user taps outside its area.
 *
 * @param visible Controls whether the menu is displayed and animated into view
 * @param onClose Callback invoked when the menu should be dismissed
 * @param viewModel ViewModel providing tracking, location, and upload state data
 * @param modifier Optional modifier applied to the sliding menu container
 */
@Composable
fun TrackingStatsMenu(
    visible: Boolean,
    onClose: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val scope = rememberCoroutineScope()
    val maxOffsetDp = 320.dp
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { maxOffsetDp.toPx() }
    val offsetPx = remember { Animatable(maxOffsetPx) }

    val username by viewModel.userName.observeAsState(initial = "")

    val location by viewModel.latestLocation.collectAsState(initial = null)
    val coordinateText = location?.let { "${it.latitude}; ${it.longitude}" } ?: "–"
    val speedText = location?.let { String.format(
        "%.1f ${stringResource(R.string.stats_km_per_hour)}",
        it.speed * 3.6f
    ) } ?: "–"

    val uploadStatus by viewModel.lastUploadStatus.collectAsState(initial = UploadStatus.Idle)
    val distanceMeters by viewModel.totalDistance.collectAsState(initial = 0f)

    LaunchedEffect(visible) {
        if (visible) {
            offsetPx.animateTo(0f, tween(300))
        } else {
            offsetPx.animateTo(maxOffsetPx, tween(300))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    scope.launch {
                        offsetPx.animateTo(maxOffsetPx, tween(250))
                        onClose()
                    }
                }
            }
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(maxOffsetDp)
                .align(Alignment.BottomCenter)
                .offset { IntOffset(x = 0, y = offsetPx.value.toInt()) }
                .background(SurfacePrimary),
            contentAlignment = Alignment.TopCenter
        ) {

            Column(modifier = Modifier.fillMaxWidth()) {
                MenuSlider(offsetPx, maxOffsetPx, scope, onClose)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    UsernameBox(
                        username = username,
                        modifier = Modifier
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceBase)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MenuRow(stringResource(R.string.stats_coordinates), coordinateText)
                            MenuRow(stringResource(R.string.stats_speed), speedText)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceSecondary)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MenuRow(stringResource(R.string.stats_gsm_level), "–")
                            MenuRow(
                                stringResource(R.string.stats_gps_signal),
                                location?.let { getSignalStrengthDescription(it.accuracy) } ?: "–"
                            )
                            MenuRow(
                                stringResource(R.string.stats_updated_at),
                                getUploadStatusText(uploadStatus, location?.time)
                            )
                        }
                    }

                    AdditionalDataSection(
                        data = listOf(
                            stringResource(R.string.stats_accuracy) to (location?.accuracy?.toString() ?: "–"),
                            stringResource(R.string.stats_distance) to String.format("%.2f ${stringResource(R.string.stats_km_distance)}", distanceMeters / 1000f),
                            stringResource(R.string.stats_data_buffer) to "–"
                        )
                    )
                }
            }
        }
    }
}

/**
 * Updates the upload status display with the provided status and timestamp
 *
 * @param status The upload status to display
 * @param lastLocationTime The timestamp of the last location, or null if not available
 */
@Composable
private fun getUploadStatusText(status: UploadStatus, lastLocationTime: Long?): String {
    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeString = lastLocationTime?.let { timeFormatter.format(Date(it)) } ?: "--:--:--"

    return when (status) {
        is UploadStatus.Idle -> stringResource(R.string.upload_status_idle)
        is UploadStatus.Success -> stringResource(R.string.upload_status_success, timeString)
        is UploadStatus.Failure -> stringResource(R.string.upload_status_failure, timeString, status.errorMessage ?: "Unknown error")
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
    return when {
        accuracy <= 0 -> stringResource(R.string.signal_unknown)
        accuracy <= 10 -> stringResource(R.string.signal_excellent)
        accuracy <= 25 -> stringResource(R.string.signal_good)
        accuracy <= 50 -> stringResource(R.string.signal_fair)
        else -> stringResource(R.string.signal_poor)
    }
}
