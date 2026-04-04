package com.websmithing.gpstracker2.ui.features.settings
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
import androidx.navigation.NavHostController
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_BUFFER_DISTANCE_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_BUFFER_TIME_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_LANGUAGE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_TRACKER_IDENTIFIER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_SERVER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_TIME_INTERVAL
import com.websmithing.gpstracker2.repository.settings.isTrackerIdentifierSettingValid
import com.websmithing.gpstracker2.repository.settings.isUploadServerSettingValid
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

internal fun isTrackerIdentifierValid(identifier: String): Boolean =
    isTrackerIdentifierSettingValid(identifier)

internal fun isUploadServerAddressValid(serverAddress: String): Boolean =
    isUploadServerSettingValid(serverAddress)

internal fun isPositiveIntervalValid(interval: String): Boolean =
    interval.trim().toIntOrNull()?.let { it > 0 } == true

internal data class SettingsValidationMessages(
    val trackerIdentifierError: String,
    val uploadServerError: String,
    val intervalError: String,
)

internal fun validateSettingsFormState(
    state: SettingsFormState,
    messages: SettingsValidationMessages,
): SettingsFormState {
    val isIdentifierValid = isTrackerIdentifierValid(state.trackerIdentifier.trim())
    val isServerAddressValid = isUploadServerAddressValid(state.uploadServer.trim())
    val isUploadIntervalValid = isPositiveIntervalValid(state.uploadTimeInterval)
    val isBufferTimeIntervalValid = isPositiveIntervalValid(state.bufferTimeInterval)
    val isBufferDistanceIntervalValid = isPositiveIntervalValid(state.bufferDistanceInterval)

    return state.copy(
        trackerIdentifierError = if (isIdentifierValid) null else messages.trackerIdentifierError,
        uploadServerError = if (isServerAddressValid) null else messages.uploadServerError,
        uploadTimeIntervalError = if (isUploadIntervalValid) null else messages.intervalError,
        bufferTimeIntervalError = if (isBufferTimeIntervalValid) null else messages.intervalError,
        bufferDistanceIntervalError = if (isBufferDistanceIntervalValid) null else messages.intervalError,
    )
}

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
    val bufferTimeInterval by viewModel.bufferTimeInterval.observeAsState()
    val bufferDistanceInterval by viewModel.bufferDistanceInterval.observeAsState()
    val language by viewModel.language.observeAsState()

    val validationMessages = remember(context) {
        SettingsValidationMessages(
            trackerIdentifierError = context.getString(R.string.tracker_identifier_error),
            uploadServerError = context.getString(R.string.upload_server_error),
            intervalError = context.getString(R.string.interval_error),
        )
    }
    val persistedState = remember(
        trackerIdentifier,
        uploadServer,
        uploadTimeInterval,
        bufferTimeInterval,
        bufferDistanceInterval,
        language,
        validationMessages,
    ) {
        validateSettingsFormState(
            state = SettingsFormState(
                trackerIdentifier = trackerIdentifier ?: DEFAULT_TRACKER_IDENTIFIER,
                uploadServer = uploadServer ?: DEFAULT_UPLOAD_SERVER,
                uploadTimeInterval = uploadTimeInterval?.toString() ?: DEFAULT_UPLOAD_TIME_INTERVAL.toString(),
                bufferTimeInterval = bufferTimeInterval?.toString() ?: DEFAULT_BUFFER_TIME_INTERVAL.toString(),
                bufferDistanceInterval = bufferDistanceInterval?.toString() ?: DEFAULT_BUFFER_DISTANCE_INTERVAL.toString(),
                languageCode = language ?: DEFAULT_LANGUAGE,
            ),
            messages = validationMessages,
        )
    }
    var initialState by remember(persistedState) { mutableStateOf(persistedState) }
    var state by remember(persistedState) { mutableStateOf(persistedState) }
    val canSave by remember(state, initialState) {
        derivedStateOf {
            !state.hasSameInputs(initialState) && state.isValid
        }
    }

    fun saveIfValid(): Boolean {
        val validatedState = validateSettingsFormState(state, validationMessages)
        state = validatedState
        focusManager.clearFocus(true)

        if (!validatedState.isValid) {
            return false
        }

        val trackerIdentifierChanged = initialState.trackerIdentifier != validatedState.trackerIdentifier
        val uploadServerChanged = initialState.uploadServer != validatedState.uploadServer
        val uploadTimeIntervalChanged = initialState.uploadTimeInterval != validatedState.uploadTimeInterval
        val bufferTimeIntervalChanged = initialState.bufferTimeInterval != validatedState.bufferTimeInterval
        val bufferDistanceIntervalChanged = initialState.bufferDistanceInterval != validatedState.bufferDistanceInterval
        val languageChanged = initialState.languageCode != validatedState.languageCode

        if (trackerIdentifierChanged) {
            viewModel.onTrackerIdentifierChanged(validatedState.trackerIdentifier.trim())
        }
        if (uploadServerChanged) {
            viewModel.onUploadServerChanged(validatedState.uploadServer.trim())
        }
        if (uploadTimeIntervalChanged) {
            viewModel.onUploadTimeIntervalChanged(validatedState.uploadTimeInterval.trim())
        }
        if (bufferTimeIntervalChanged) {
            viewModel.onBufferTimeIntervalChanged(validatedState.bufferTimeInterval.trim())
        }
        if (bufferDistanceIntervalChanged) {
            viewModel.onBufferDistanceIntervalChanged(validatedState.bufferDistanceInterval.trim())
        }
        if (languageChanged) {
            viewModel.onLanguageChanged(validatedState.languageCode.trim())
        }

        initialState = validatedState
        return true
    }

    Page(
        onBack = { navController.navigateUp() },
        onSave = {
            if (saveIfValid()) {
                navController.navigateUp()
            }
        },
        onChange = { nextState ->
            state = validateSettingsFormState(nextState, validationMessages)
        },
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
            .heightIn(52.dp)
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
