package com.websmithing.gpstracker2.ui.features.settings

import android.widget.Toast
import androidx.activity.compose.LocalActivity
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
import com.websmithing.gpstracker2.ui.TrackingViewModel
import com.websmithing.gpstracker2.ui.activityHiltViewModel
import com.websmithing.gpstracker2.ui.components.CustomBackButton
import com.websmithing.gpstracker2.ui.features.settings.components.SettingsForm
import com.websmithing.gpstracker2.ui.features.settings.model.SettingsFormState
import com.websmithing.gpstracker2.ui.hasSpaces
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
    val activity = LocalActivity.current
    val focusManager = LocalFocusManager.current

    val userName by viewModel.userName.observeAsState()
    val websiteUrl by viewModel.websiteUrl.observeAsState()
    val language by viewModel.language.observeAsState()
    val interval by viewModel.trackingInterval.observeAsState()

    var initialState by remember {
        mutableStateOf(
            SettingsFormState(
                userName ?: "",
                websiteUrl ?: context.getString(R.string.default_upload_website),
                interval ?: 1,
                language ?: "ru"
            )
        )
    }
    var state by remember { mutableStateOf(initialState) }
    val isChanged by remember { derivedStateOf { state != initialState } }

    fun saveAndValidate() {
        val name = state.userName.trim()
        val website = state.websiteUrl.trim()

        val isNameValid = name.isNotBlank() && !hasSpaces(name)
        val isWebsiteValid = website.isNotBlank() && !hasSpaces(website)

        if (!isNameValid) {
            if (name.isBlank()) {
                state = state.copy(userNameError = context.getString(R.string.username_error_empty))
            } else if (hasSpaces(name)) {
                state =
                    state.copy(userNameError = context.getString(R.string.username_error_spaces))
            }
        } else {
            state = state.copy(websiteUrlError = null)
        }

        if (!isWebsiteValid) {
            if (website.isBlank()) {
                state =
                    state.copy(websiteUrlError = context.getString(R.string.website_error_empty))
            } else if (hasSpaces(website)) {
                state =
                    state.copy(websiteUrlError = context.getString(R.string.website_error_spaces))
            }
        } else {
            state = state.copy(websiteUrlError = null)
        }

        if (isNameValid && isWebsiteValid) {
            val languageChanged = initialState.languageCode != state.languageCode

            viewModel.onUserNameChanged(state.userName.trim())
            viewModel.onWebsiteUrlChanged(state.websiteUrl.trim())
            viewModel.onIntervalChanged(state.interval)
            if (languageChanged) {
                viewModel.onLanguageChanged(state.languageCode)
                activity?.recreate()
            }

            initialState = state
        } else {
            Toast.makeText(context, R.string.textfields_empty_or_spaces, Toast.LENGTH_LONG).show()
        }

        focusManager.clearFocus(true)
    }

    Page(
        onBack = { navController.navigateUp() },
        onSave = { saveAndValidate() },
        onChange = { state = it },
        isChanged = isChanged,
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
    isChanged: Boolean,
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
                    enabled = isChanged,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
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
    val context = LocalContext.current

    WaliotTheme {
        Page(
            state = SettingsFormState(
                websiteUrl = context.getString(R.string.default_upload_website),
                languageCode = "ru"
            ),
            isChanged = true,
            onChange = {},
            onBack = {},
            onSave = {},
        )
    }
}