package com.websmithing.gpstracker2.ui.features.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import androidx.navigation.NavHostController
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_LANGUAGE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_TRACKER_IDENTIFIER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_DISTANCE_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_SERVER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_TIME_INTERVAL
import com.websmithing.gpstracker2.ui.TrackingViewModel
import com.websmithing.gpstracker2.ui.activityHiltViewModel
import com.websmithing.gpstracker2.ui.components.CustomBackButton
import com.websmithing.gpstracker2.ui.features.settings.components.SettingsForm
import com.websmithing.gpstracker2.ui.features.settings.model.SettingsFormState
import com.websmithing.gpstracker2.ui.modifiers.unfocus
import com.websmithing.gpstracker2.ui.theme.WaliotTheme
import com.websmithing.gpstracker2.ui.theme.customButtonColors
import com.websmithing.gpstracker2.ui.theme.customButtonShape
import com.websmithing.gpstracker2.ui.theme.customTopAppBarColors
import com.websmithing.gpstracker2.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: TrackingViewModel = activityHiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val trackerIdentifier by viewModel.trackerIdentifier.observeAsState()
    val uploadServer by viewModel.uploadServer.observeAsState()
    val uploadTimeInterval by viewModel.uploadTimeInterval.observeAsState()
    val uploadDistanceInterval by viewModel.uploadDistanceInterval.observeAsState()
    val language by viewModel.language.observeAsState()

    var initialState = SettingsFormState(
        trackerIdentifier = trackerIdentifier ?: DEFAULT_TRACKER_IDENTIFIER,
        uploadServer = uploadServer ?: DEFAULT_UPLOAD_SERVER,
        uploadTimeInterval = uploadTimeInterval?.toString() ?: DEFAULT_UPLOAD_TIME_INTERVAL.toString(),
        uploadDistanceInterval = uploadDistanceInterval?.toString() ?: DEFAULT_UPLOAD_DISTANCE_INTERVAL.toString(),
        languageCode = language ?: DEFAULT_LANGUAGE
    )
    var state by remember { mutableStateOf(initialState) }
    val canSave by remember(state, initialState) {
        derivedStateOf {
            state != initialState
        }
    }

    fun saveAndValidate(): Boolean {
        val identifier = state.trackerIdentifier.trim()
        val serverAddress = state.uploadServer.trim()
        val timeInterval = state.uploadTimeInterval.trim().let { if (it.isEmpty()) 0 else it.toInt() }
        val distanceInterval = state.uploadDistanceInterval.trim().let { if (it.isEmpty()) 0 else it.toInt() }

        val isIdentifierValid = identifier.isEmpty() || identifier.isDigitsOnly()
        val isServerAddressValid = serverAddress.isNotBlank() && !serverAddress.contains(' ')
        val isTimeIntervalValid = timeInterval > 0
        val isDistanceIntervalValid = distanceInterval > 0

        state = if (!isIdentifierValid) {
            state.copy(trackerIdentifierError = context.getString(R.string.tracker_identifier_error))
        } else {
            state.copy(trackerIdentifierError = null)
        }

        state = if (!isServerAddressValid) {
            state.copy(uploadServerError = context.getString(R.string.upload_server_error))
        } else {
            state.copy(uploadServerError = null)
        }

        state = if (!isTimeIntervalValid) {
            state.copy(uploadTimeIntervalError = context.getString(R.string.interval_error))
        } else {
            state.copy(uploadTimeIntervalError = null)
        }

        state = if (!isDistanceIntervalValid) {
            state.copy(uploadDistanceIntervalError = context.getString(R.string.interval_error))
        } else {
            state.copy(uploadDistanceIntervalError = null)
        }

        focusManager.clearFocus(true)

        if (isIdentifierValid && isServerAddressValid && isDistanceIntervalValid && isTimeIntervalValid) {
            val trackerIdentifierChanged = initialState.trackerIdentifier != state.trackerIdentifier
            val uploadServerChanged = initialState.uploadServer != state.uploadServer
            val timeIntervalChanged = initialState.uploadTimeInterval != state.uploadTimeInterval
            val distanceIntervalChanged = initialState.uploadDistanceInterval != state.uploadDistanceInterval
            val languageChanged = initialState.languageCode != state.languageCode

            if (trackerIdentifierChanged) {
                viewModel.onTrackerIdentifierChanged(state.trackerIdentifier.trim())
            }
            if (uploadServerChanged) {
                viewModel.onUploadServerChanged(state.uploadServer.trim())
            }
            if (timeIntervalChanged) {
                viewModel.onTimeIntervalChanged(state.uploadTimeInterval.trim())
            }
            if (distanceIntervalChanged) {
                viewModel.onDistanceIntervalChanged(state.uploadDistanceInterval.trim())
            }
            if (languageChanged) {
                viewModel.onLanguageChanged(state.languageCode.trim())
            }

            initialState = state
            return true
        } else {
            Toast.makeText(context, R.string.text_fields_empty_or_spaces, Toast.LENGTH_LONG).show()
            return false
        }
    }

    Page(
        onBack = { navController.navigateUp() },
        onSave = {
            if (saveAndValidate()) {
                navController.navigateUp()
            }
        },
        onChange = { state = it },
        canSave = canSave,
        state = state,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
    onChange: (SettingsFormState) -> Unit,
    state: SettingsFormState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                colors = customTopAppBarColors(),
                navigationIcon = { CustomBackButton(onBack) },
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .background(MaterialTheme.extendedColors.appBar)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SaveButton(
                    onClick = onSave,
                    enabled = canSave,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.unfocus(LocalFocusManager.current)
    ) { paddingValues ->
        SettingsForm(
            state = state,
            onChange = onChange,
            modifier = modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun SaveButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = customButtonColors(),
        shape = customButtonShape(),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(56.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(R.drawable.ic_check_16), contentDescription = null)
            Text(stringResource(R.string.save))
        }
    }
}

@Preview
@Composable
private fun PagePreview() {
    WaliotTheme {
        Page(
            state = SettingsFormState(),
            canSave = true,
            onChange = {},
            onBack = {},
            onSave = {},
        )
    }
}
