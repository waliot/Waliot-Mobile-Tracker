package com.websmithing.gpstracker2.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.websmithing.gpstracker2.BuildConfig
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.TrackingViewModel
import com.websmithing.gpstracker2.ui.components.settings.LanguageOption
import com.websmithing.gpstracker2.ui.components.settings.LanguageSelector
import com.websmithing.gpstracker2.ui.components.settings.SaveButton
import com.websmithing.gpstracker2.ui.components.settings.SettingsField
import com.websmithing.gpstracker2.ui.components.settings.TopBar
import com.websmithing.gpstracker2.ui.theme.IconTintSecondary
import com.websmithing.gpstracker2.ui.theme.SurfaceBase
import com.websmithing.gpstracker2.ui.theme.SurfacePrimary
import com.websmithing.gpstracker2.ui.theme.TextSecondary

@Composable
fun TrackerSettingsScreen(
    viewModel: TrackingViewModel = hiltViewModel(),
    navController: NavHostController
) {
    val view = LocalView.current
    val statusBarHeight = with(LocalDensity.current) {
        WindowInsetsCompat.toWindowInsetsCompat(view.rootWindowInsets)
            .getInsets(WindowInsetsCompat.Type.statusBars()).top.toDp()
    }

    val username by viewModel.userName.observeAsState(initial = "")
    val website by viewModel.websiteUrl.observeAsState(initial = "")
    val intervalMinutes by viewModel.trackingInterval.observeAsState(initial = "")
    val intervalMeters by viewModel.trackingIntervalMeters.observeAsState(initial = 1)
    val language by viewModel.language.observeAsState(initial = "ru")

    var usernameInput by remember { mutableStateOf(username) }
    var websiteInput by remember { mutableStateOf(website) }
    var intervalMinutesInput by remember { mutableStateOf(intervalMinutes.toString()) }
    var intervalMetersInput by remember { mutableStateOf(intervalMeters.toString()) }

    var selectedLanguageInput by remember { mutableStateOf(language) }

    val isFormValid by derivedStateOf {
        usernameInput.isNotBlank() &&
        websiteInput.isNotBlank() &&
        intervalMinutesInput.toIntOrNull()?.let { it > 0 } == true
        intervalMetersInput.toIntOrNull()?.let { it > 0 } == true
    }

    val languageOptions = listOf(
        LanguageOption("ru", stringResource(R.string.settings_lang_ru), R.drawable.ic_flag_ru),
        LanguageOption("en", stringResource(R.string.settings_lang_en), R.drawable.ic_flag_en)
    )

    val fieldSpacing = 16.dp
    val labelToInputSpacing = 8.dp
    val sidePadding = 16.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBase)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp)
        ) {
            TopBar(statusBarHeight = statusBarHeight, navController = navController, viewModel = viewModel)

            Column(modifier = Modifier.padding(horizontal = sidePadding, vertical = 16.dp)) {
                SettingsField(
                    title = stringResource(R.string.settings_identifier),
                    value = usernameInput,
                    onValueChange = {
                        usernameInput = it.filter { char -> char.isDigit() }
                        viewModel.onUserNameChanged(usernameInput)
                    },
                    labelSpacing = labelToInputSpacing,
                    fieldPadding = sidePadding,
                    isError = usernameInput.isBlank()
                )
                Spacer(modifier = Modifier.height(fieldSpacing))

                SettingsField(
                    title = stringResource(R.string.settings_server_url),
                    value = websiteInput,
                    onValueChange = {
                        websiteInput = it.filter { char -> char.toString().matches(Regex("[a-zA-Z0-9.:/\\-_?&=]")) }
                        viewModel.onWebsiteUrlChanged(websiteInput)
                    },
                    labelSpacing = labelToInputSpacing,
                    fieldPadding = sidePadding,
                    isError = websiteInput.isBlank()
                )
                Spacer(modifier = Modifier.height(fieldSpacing))

                SettingsField(
                    title = stringResource(R.string.settings_interval_minutes),
                    value = intervalMinutesInput,
                    onValueChange = {
                        intervalMinutesInput = it.filter { char -> char.isDigit() }
                        intervalMinutesInput.toIntOrNull()?.let { viewModel.onIntervalChanged(it) }
                    },
                    labelSpacing = labelToInputSpacing,
                    fieldPadding = sidePadding,
                    isError = intervalMinutesInput.isBlank() || intervalMinutesInput.toIntOrNull() == null || intervalMinutesInput.toInt() <= 0
                )
                Spacer(modifier = Modifier.height(fieldSpacing))

                SettingsField(
                    title = stringResource(R.string.settings_interval_meters),
                    value = intervalMetersInput,
                    onValueChange = {
                        intervalMetersInput = it.filter { char -> char.isDigit() }
                        intervalMetersInput.toIntOrNull()?.let { viewModel.onIntervalMetersChanged(it) }
                    },
                    labelSpacing = labelToInputSpacing,
                    fieldPadding = sidePadding,
                    isError = intervalMetersInput.isBlank() || intervalMetersInput.toIntOrNull() == null || intervalMetersInput.toInt() <= 0
                )
                Spacer(modifier = Modifier.height(fieldSpacing))

                LanguageSelector(
                    options = languageOptions,
                    selectedLanguage = selectedLanguageInput,
                    onLanguageSelected = {
                        selectedLanguageInput = it
                        viewModel.onLanguageChanged(selectedLanguageInput)
                    },
                    title = stringResource(R.string.settings_language),
                    labelSpacing = labelToInputSpacing,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = "logo",
                modifier = Modifier
                    .width(140.dp)
                    .height(47.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(IconTintSecondary)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_app_version),
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = BuildConfig.VERSION_NAME,
                    fontSize = 14.sp,
                    color = IconTintSecondary
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(SurfacePrimary),
                contentAlignment = Alignment.Center
            ) {
                SaveButton(
                    onClick = {
                        if (isFormValid) {
                            viewModel.saveSettings()
                            navController.popBackStack()
                        }
                    },
                    enabled = isFormValid,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}