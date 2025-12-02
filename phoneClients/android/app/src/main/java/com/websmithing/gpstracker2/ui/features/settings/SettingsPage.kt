package com.websmithing.gpstracker2.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.TrackingViewModel
import com.websmithing.gpstracker2.ui.activityHiltViewModel
import com.websmithing.gpstracker2.ui.components.CustomBackButton
import com.websmithing.gpstracker2.ui.components.CustomExposedDropdownMenu
import com.websmithing.gpstracker2.ui.components.DropdownMenuOption
import com.websmithing.gpstracker2.ui.components.LabeledBox
import com.websmithing.gpstracker2.ui.theme.WaliotTheme
import com.websmithing.gpstracker2.ui.theme.customOutlinedTextFieldColors
import com.websmithing.gpstracker2.ui.theme.customSegmentedButtonColors
import com.websmithing.gpstracker2.ui.theme.customSegmentedButtonShape
import com.websmithing.gpstracker2.ui.theme.customTopAppBarColors

@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: TrackingViewModel = activityHiltViewModel(),
) {
    val context = LocalContext.current

    val userName by viewModel.userName.observeAsState()
    val websiteUrl by viewModel.websiteUrl.observeAsState()
    val language by viewModel.language.observeAsState()

    Page(
        onBack = { navController.navigateUp() },
        userName = userName ?: "",
        websiteUrl = websiteUrl ?: context.getString(R.string.default_upload_website),
        languageCode = language ?: "ru",
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(
    modifier: Modifier = Modifier,
    userName: String,
    websiteUrl: String,
    languageCode: String,
    onBack: () -> Unit,
) {

    var userName by remember { mutableStateOf(userName) }
    var userNameError by remember { mutableStateOf(false) }

    var websiteUrl by remember { mutableStateOf(websiteUrl) }
    var websiteUrlError by remember { mutableStateOf(false) }

    var interval by remember { mutableStateOf(1) }
    var intervalsExpanded by remember { mutableStateOf(false) }

    var languageCode by remember { mutableStateOf(languageCode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                colors = customTopAppBarColors(),
                navigationIcon = { CustomBackButton(onBack) },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) { paddingValues ->
        Column(
            verticalArrangement = Arrangement.spacedBy(13.dp),
            modifier = Modifier
                .padding(paddingValues)
                .padding(vertical = 13.dp, horizontal = 16.dp)
        ) {
            LabeledBox(label = stringResource(R.string.user_name)) {
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    isError = userNameError,
                    colors = customOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LabeledBox(label = stringResource(R.string.upload_website)) {
                OutlinedTextField(
                    value = websiteUrl,
                    onValueChange = { websiteUrl = it },
                    isError = websiteUrlError,
                    colors = customOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LabeledBox(label = stringResource(R.string.upload_frequency)) {
                CustomExposedDropdownMenu(
                    options = listOf(
                        DropdownMenuOption(
                            content = {
                                Text(stringResource(R.string.one_minute))
                            },
                            value = "1",
                        ),
                        DropdownMenuOption(
                            content = {
                                Text(stringResource(R.string.five_minutes))
                            },
                            value = "5",
                        ),
                        DropdownMenuOption(
                            content = {
                                Text(stringResource(R.string.fifteen_minutes))
                            },
                            value = "15",
                        )
                    ),
                    expanded = intervalsExpanded,
                    onExpandedChange = { intervalsExpanded = it },
                    value = "$interval",
                    onValueChange = { interval = it.toInt() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SelectLanguage(
                selected = languageCode,
                onSelect = { languageCode = it }
            )
        }
    }
}

@Composable
private fun SelectLanguage(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val languages = listOf(
        "ru" to R.string.lang_ru to R.drawable.ic_russian_16,
        "en" to R.string.lang_en to R.drawable.ic_english_16
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        languages.forEachIndexed { index, (pair, icon) ->
            val (code, label) = pair
            SegmentedButton(
                shape = customSegmentedButtonShape(
                    index = index,
                    count = languages.size,
                ),
                colors = customSegmentedButtonColors(),
                onClick = { onSelect(code) },
                selected = selected == code,
                icon = {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = Color.Unspecified
                    )
                },
                label = { Text(stringResource(label)) }
            )
        }
    }
}

@Preview
@Composable
private fun PagePreview() {
    val context = LocalContext.current

    WaliotTheme {
        Page(
            onBack = {},
            userName = "",
            websiteUrl = context.getString(R.string.default_upload_website),
            languageCode = "ru"
        )
    }
}