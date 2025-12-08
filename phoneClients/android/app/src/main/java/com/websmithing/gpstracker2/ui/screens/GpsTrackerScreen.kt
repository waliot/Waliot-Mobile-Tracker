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
import timber.log.Timber

@Composable
fun GpsTrackerScreen(
    viewModel: TrackingViewModel = hiltViewModel(),
    navController: NavHostController

) {
        var menuVisible by remember { mutableStateOf(false) }
        var showNotifyBanner by remember { mutableStateOf(false) }
        var notifyStatus by remember { mutableStateOf(NotifyStatus.Success) }

        val username by viewModel.userName.observeAsState("")
        val language by viewModel.language.observeAsState("")
        Timber.d("LANGUAGE: $language")

        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                val markerColor = when {
                    !menuVisible -> MarkerColor.GREY
                    menuVisible && username.isNotBlank() -> MarkerColor.BLUE
                    else -> MarkerColor.RED
                }

                OsmMapContainer(
                    modifier = Modifier.fillMaxSize(),
                    markerColor = markerColor,
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
                    status = getTrackingButtonState(viewModel),
                    onClick = {
                        handleTrackingButtonClick(
                            username = username,
                            viewModel = viewModel,
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
    private fun getTrackingButtonState(viewModel: TrackingViewModel): TrackingButtonState {
        val username by viewModel.userName.observeAsState("")
        val website by viewModel.websiteUrl.observeAsState("")
        val interval by viewModel.trackingInterval.observeAsState(0)
        val isTracking by viewModel.isTracking.observeAsState(false)

        val isFormValid = username.isNotBlank() && website.isNotBlank() && interval > 0
        return when {
            !isFormValid -> TrackingButtonState.Disabled
            isTracking -> TrackingButtonState.Tracking
            else -> TrackingButtonState.Stopped
        }
    }

    private fun handleTrackingButtonClick(
        username: String,
        viewModel: TrackingViewModel,
        showNotifyBanner: () -> Unit,
        notifyStatus: (NotifyStatus) -> Unit
    ) {
        if (viewModel.isTracking.value == true) {
            viewModel.stopTracking()
            return
        }

        if (username.isEmpty()) {
            showNotifyBanner()
            notifyStatus(NotifyStatus.Warning)
            return
        }

        viewModel.startTracking()
        showNotifyBanner()
        notifyStatus(NotifyStatus.Success)
    }
