package com.websmithing.gpstracker2.ui.features.settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websmithing.gpstracker2.BuildConfig
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.components.CustomExposedDropdownMenu
import com.websmithing.gpstracker2.ui.components.DropdownMenuOption
import com.websmithing.gpstracker2.ui.components.LabeledBox
import com.websmithing.gpstracker2.ui.features.settings.model.SettingsFormState
import com.websmithing.gpstracker2.ui.theme.WaliotTheme
import com.websmithing.gpstracker2.ui.theme.customOutlinedTextFieldColors
import com.websmithing.gpstracker2.ui.theme.customSegmentedButtonColors
import com.websmithing.gpstracker2.ui.theme.customSegmentedButtonShape
import com.websmithing.gpstracker2.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsForm(
    modifier: Modifier = Modifier,
    state: SettingsFormState,
    onChange: (SettingsFormState) -> Unit,
) {
    var intervalsExpanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(13.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 13.dp, horizontal = 16.dp)
    ) {
        LabeledBox(label = stringResource(R.string.user_name)) {
            OutlinedTextField(
                value = state.userName,
                onValueChange = { onChange(state.copy(userName = it)) },
                isError = state.userNameError != null,
                supportingText = { state.userNameError?.let { Text(it) } },
                colors = customOutlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        LabeledBox(label = stringResource(R.string.upload_website)) {
            OutlinedTextField(
                value = state.websiteUrl,
                onValueChange = { onChange(state.copy(websiteUrl = it)) },
                isError = state.websiteUrlError != null,
                supportingText = { state.websiteUrlError?.let { Text(it) } },
                colors = customOutlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
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
                value = "${state.interval}",
                onValueChange = { onChange(state.copy(interval = it.toInt())) },
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        LabeledBox(label = stringResource(R.string.language)) {
            SelectLanguage(
                selected = state.languageCode,
                onSelect = { onChange(state.copy(languageCode = it)) }
            )
        }

        Spacer(Modifier.weight(1f))

        Logo(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SelectLanguage(
    modifier: Modifier = Modifier,
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
                label = { Text(stringResource(label)) },
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
                modifier = modifier
            )
        }
    }
}

@Composable
private fun Logo(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Image(
            painterResource(R.drawable.logo_full),
            contentDescription = stringResource(R.string.app_name),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.width(140.dp)
        )

        Text(
            buildAnnotatedString {
                append(stringResource(R.string.app_version_format))
                append(" ")
                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(BuildConfig.VERSION_NAME)
                }
            },
            color = MaterialTheme.extendedColors.tertiaryText,
            textAlign = TextAlign.Center
        )
    }
}

@Preview
@Composable
private fun SettingsFormPreview() {
    val context = LocalContext.current

    WaliotTheme {
        SettingsForm(
            state = SettingsFormState(
                websiteUrl = context.getString(R.string.default_upload_website),
                languageCode = "ru"
            ),
            onChange = {},
        )
    }
}