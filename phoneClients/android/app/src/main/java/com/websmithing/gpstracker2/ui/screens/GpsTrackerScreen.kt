package com.websmithing.gpstracker2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.websmithing.gpstracker2.ui.TrackingViewModel
import com.websmithing.gpstracker2.ui.components.*
import com.websmithing.gpstracker2.util.PermissionChecker

@Composable
fun GpsTrackerScreen(
    viewModel: TrackingViewModel = hiltViewModel(),
    permissionChecker: PermissionChecker,
    navController: NavHostController
) {
        var menuVisible by remember { mutableStateOf(false) }
        var showNotifyBanner by remember { mutableStateOf(false) }
        var notifyStatus by remember { mutableStateOf(NotifyStatus.Success) }

        val username by viewModel.userName.observeAsState("")

        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                val markerColor = when {
                    !menuVisible -> MarkerBackgroundColor.GREY
                    menuVisible && username.isNotBlank() -> MarkerBackgroundColor.BLUE
                    else -> MarkerBackgroundColor.RED
                }
                val markerIconColor = when (markerColor) {
                    MarkerBackgroundColor.GREY -> MarkerIconColor.GREY
                    MarkerBackgroundColor.BLUE -> MarkerIconColor.WHITE
                    MarkerBackgroundColor.RED -> MarkerIconColor.WHITE
                }

                OsmMapContainer(
                    modifier = Modifier.fillMaxSize(),
                    markerBackgroundColor = markerColor,
                    markerIconColor = markerIconColor,
                    onPointerClick = { menuVisible = !menuVisible }
                )

                SettingsButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = 16.dp, top = 8.dp),
                    onClick = { navController.navigate("TrackerSettingsScreen") }
                )

                TrackingButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                    status = getTrackingButtonState(viewModel, permissionChecker),
                    onClick = {
                        handleTrackingButtonClick(
                            username = username,
                            viewModel = viewModel,
                            permissionChecker=permissionChecker,
                            showNotifyBanner = { showNotifyBanner = true },
                            notifyStatus = { notifyStatus = it }
                        )
                    }
                )

                TrackingStatsMenu(
                    visible = menuVisible,
                    onClose = { menuVisible = false },
                    viewModel = viewModel,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                NotifyBanner(
                    notifyStatus = notifyStatus,
                    visible = showNotifyBanner,
                    onDismiss = { showNotifyBanner = false }
                )
            }
        }
    }

    @Composable
    private fun getTrackingButtonState(
        viewModel: TrackingViewModel,
        permissionChecker: PermissionChecker
    ): TrackingButtonState {
        val username by viewModel.userName.observeAsState("")
        val website by viewModel.websiteUrl.observeAsState("")
        val intervalMinutes by viewModel.trackingInterval.observeAsState(0)
        val intervalMeters by viewModel.trackingIntervalMeters.observeAsState(1)
        val isTracking by viewModel.isTracking.observeAsState(false)

        val isFormValid = username.isNotBlank() && website.isNotBlank()
                && intervalMinutes > 0 && intervalMeters > 0
                && permissionChecker.hasLocationPermissions()
                && permissionChecker.hasBackgroundLocationPermissions()
        return when {
            !isFormValid -> TrackingButtonState.Disabled
            isTracking -> TrackingButtonState.Tracking
            else -> TrackingButtonState.Stopped
        }
    }

    private fun handleTrackingButtonClick(
        username: String,
        viewModel: TrackingViewModel,
        permissionChecker: PermissionChecker,
        showNotifyBanner: () -> Unit,
        notifyStatus: (NotifyStatus) -> Unit
    ) {
        if (viewModel.isTracking.value == true) {
            viewModel.stopTracking()
            return
        }
        if (permissionChecker.hasLocationPermissions() &&
            permissionChecker.hasBackgroundLocationPermissions()) {
            if (username.isEmpty()) {
                notifyStatus(NotifyStatus.Warning)
                showNotifyBanner()
                return
            }
            viewModel.startTracking()
            notifyStatus(NotifyStatus.Success)
            showNotifyBanner()
        } else {
            notifyStatus(NotifyStatus.Warning)
            showNotifyBanner()
            return
        }
    }