package com.websmithing.gpstracker2

import android.content.Context
import android.location.Location
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import com.websmithing.gpstracker2.repository.location.LocationConsumer
import com.websmithing.gpstracker2.repository.location.LocationFixStatus
import com.websmithing.gpstracker2.repository.location.LocationRepository
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.repository.settings.SettingsRepositoryImpl
import com.websmithing.gpstracker2.repository.upload.UploadRepository
import com.websmithing.gpstracker2.repository.upload.UploadStatus
import com.websmithing.gpstracker2.service.TrackingServiceController
import com.websmithing.gpstracker2.ui.TrackingViewModel
import com.websmithing.gpstracker2.ui.UiTestTags
import com.websmithing.gpstracker2.ui.features.settings.SettingsPage
import com.websmithing.gpstracker2.ui.theme.WaliotTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Rule
import org.junit.Test

class SettingsRestorationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModels = mutableListOf<TrackingViewModel>()

    @After
    fun tearDown() {
        composeRule.runOnIdle { viewModels.forEach { it.viewModelScope.cancel() } }
        composeRule.activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun restored_draft_survives_loading_settings_in_a_new_view_model() {
        checkRestoredDraft(uploadInterval = "9")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SAVE_BUTTON).assertIsEnabled()
    }

    @Test
    fun restored_invalid_draft_remains_invalid_after_settings_load() {
        checkRestoredDraft(uploadInterval = "")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SAVE_BUTTON).assertIsNotEnabled()
    }

    private fun checkRestoredDraft(uploadInterval: String) {
        val preferences = composeRule.activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        check(preferences.edit().clear()
            .putString(SettingsRepository.KEY_TRACKER_IDENTIFIER, "persisted-tracker")
            .putString(SettingsRepository.KEY_UPLOAD_SERVER, "127.0.0.1:33334")
            .putInt(SettingsRepository.KEY_UPLOAD_TIME_INTERVAL, 7)
            .putInt(SettingsRepository.KEY_BUFFER_TIME_INTERVAL, 3)
            .putInt(SettingsRepository.KEY_BUFFER_DISTANCE_INTERVAL, 200)
            .putString(SettingsRepository.KEY_LANGUAGE, "ru")
            .commit())
        val repository = SettingsRepositoryImpl(preferences)
        lateinit var activeViewModel: TrackingViewModel
        composeRule.runOnIdle { activeViewModel = newViewModel(repository) }
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            WaliotTheme {
                SettingsPage(navController = rememberNavController(), viewModel = activeViewModel)
            }
        }
        waitForSettings(activeViewModel)
        fields()[0].performTextReplacement("unsaved-tracker")
        fields()[1].performTextReplacement("127.0.0.1:33335")
        fields()[2].performTextReplacement(uploadInterval)
        fields()[3].performTextReplacement("4")
        fields()[4].performTextReplacement("250")

        val loadSettings = CompletableDeferred<Unit>()
        val delayedRepository = object : SettingsRepository by repository {
            override suspend fun getTrackingState(): Boolean {
                loadSettings.await()
                return repository.getTrackingState()
            }
        }
        // Unlike ActivityScenario.recreate(), process restoration creates a new ViewModel.
        composeRule.runOnIdle {
            activeViewModel.viewModelScope.cancel()
            activeViewModel = newViewModel(delayedRepository)
        }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        loadSettings.complete(Unit)
        waitForSettings(activeViewModel)

        fields()[0].assertTextContains("unsaved-tracker")
        fields()[1].assertTextContains("127.0.0.1:33335")
        fields()[2].assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(uploadInterval)))
        fields()[3].assertTextContains("4")
        fields()[4].assertTextContains("250")
    }

    private fun fields() = composeRule.onAllNodes(hasSetTextAction())

    private fun waitForSettings(viewModel: TrackingViewModel) {
        composeRule.waitUntil(5_000) { viewModel.language.value != null }
        composeRule.waitForIdle()
    }

    private fun newViewModel(repository: SettingsRepository) = TrackingViewModel(
        repository,
        object : LocationRepository {
            override val currentLocation = flowOf<Location?>(null)
            override val locationFixStatus = flowOf(LocationFixStatus())
            override fun start(consumer: LocationConsumer) = Unit
            override fun stop(consumer: LocationConsumer) = Unit
        },
        object : UploadRepository {
            override val lastUploadStatus = flowOf(UploadStatus.Idle)
            override suspend fun resetUploadStatus() = Unit
            override suspend fun uploadData(trackerIdentifier: String, location: Location) = false
        },
        object : TrackingServiceController {
            override fun startTracking() = Unit
            override fun stopTracking() = Unit
            override fun refreshTracking() = Unit
            override fun ensureTrackingRunning() = Unit
        },
    ).also { viewModels.add(it) }

    private companion object {
        const val PREFERENCES = "settings-restoration-test"
    }
}
