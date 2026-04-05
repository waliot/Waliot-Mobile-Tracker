package com.websmithing.gpstracker2

import android.Manifest
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.ui.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    companion object {
        private const val UI_TIMEOUT_MS = 20_000L
    }

    private val resetRule = AppStateResetRule()

    private val permissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(resetRule)
        .around(permissionRule)
        .around(composeRule)

    @Test
    fun first_launch_renders_home_controls_without_blocking_dialogs() {
        waitForTag(UiTestTags.HOME_SETTINGS_BUTTON)
        composeRule.onNodeWithTag(UiTestTags.HOME_SETTINGS_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.TRACKING_BUTTON_STOP).assertIsDisplayed()
        composeRule.onAllNodesWithTag(UiTestTags.SETTINGS_CLOSE_BUTTON).hasAnyNode().let { isVisible ->
            if (isVisible) {
                throw AssertionError("Settings screen should not be visible on first launch")
            }
        }
    }

    @Test
    fun battery_optimization_notice_is_shown_only_once() {
        val activity = composeRule.activity
        val continueLabel = activity.getString(R.string.permission_button_continue)

        saveMinimalValidSettings("waliot-battery-smoke")
        waitForTag(UiTestTags.TRACKING_BUTTON_PLAY)
        startTrackingHandlingBatteryNotice(
            trackingPlayTag = UiTestTags.TRACKING_BUTTON_PLAY,
            trackingPauseTag = UiTestTags.TRACKING_BUTTON_PAUSE,
            continueLabel = continueLabel,
        )
        clickButtonUntilTag(
            currentTag = UiTestTags.TRACKING_BUTTON_PAUSE,
            expectedTag = UiTestTags.TRACKING_BUTTON_PLAY,
        )

        waitForTag(UiTestTags.TRACKING_BUTTON_PLAY)
        startTrackingWithoutBatteryNotice(
            trackingPlayTag = UiTestTags.TRACKING_BUTTON_PLAY,
            trackingPauseTag = UiTestTags.TRACKING_BUTTON_PAUSE,
            continueLabel = continueLabel,
        )

        clickButtonUntilTag(
            currentTag = UiTestTags.TRACKING_BUTTON_PAUSE,
            expectedTag = UiTestTags.TRACKING_BUTTON_PLAY,
        )
    }

    @Test
    fun language_switch_updates_home_and_persists_after_recreate() {
        val activity = composeRule.activity
        val englishSettingsTitle = "Settings"
        val englishSaveLabel = "Save"
        val russianSettingsTitle = activity.getString(R.string.settings)

        waitForTag(UiTestTags.HOME_SETTINGS_BUTTON)
        openSettings()
        replaceField(0, "waliot-language-smoke")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_LANGUAGE_EN).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SAVE_BUTTON).assertIsEnabled().performClick()

        waitForHomeScreen()
        openSettings()
        waitForText(englishSettingsTitle)
        waitForText(englishSaveLabel)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_CLOSE_BUTTON).assertIsDisplayed().performClick()

        waitForHomeScreen()
        composeRule.activityRule.scenario.recreate()
        waitForHomeScreen()

        openSettings()
        waitForText(englishSettingsTitle)
        waitForText(englishSaveLabel)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_LANGUAGE_RU).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SAVE_BUTTON).assertIsEnabled().performClick()

        waitForHomeScreen()
        openSettings()
        waitForText(russianSettingsTitle)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_CLOSE_BUTTON).assertIsDisplayed().performClick()
    }

    @Test
    fun settings_close_without_save_keeps_previous_values() {
        val russianSettingsTitle = composeRule.activity.getString(R.string.settings)
        val persistedTrackerId = "waliot-smoke-close"
        val persistedServer = "device.waliot.com:30032"
        val persistedTimeInterval = "7"
        val persistedBufferTimeInterval = "3"
        val persistedBufferDistanceInterval = "180"
        val draftTrackerId = "waliot-unsaved-draft"
        val draftServer = "127.0.0.1:33333"

        waitForTag(UiTestTags.HOME_SETTINGS_BUTTON)

        openSettings()
        replaceField(0, persistedTrackerId)
        replaceField(1, persistedServer)
        replaceField(2, persistedTimeInterval)
        replaceField(3, persistedBufferTimeInterval)
        replaceField(4, persistedBufferDistanceInterval)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SAVE_BUTTON).assertIsEnabled().performClick()

        waitForHomeScreen()
        openSettings()
        replaceField(0, draftTrackerId)
        replaceField(1, draftServer)
        replaceField(2, "9")
        replaceField(3, "4")
        replaceField(4, "250")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_LANGUAGE_EN).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_CLOSE_BUTTON).assertIsDisplayed().performClick()

        waitForHomeScreen()
        openSettings()
        assertFieldContains(0, persistedTrackerId)
        assertFieldContains(1, persistedServer)
        assertFieldContains(2, persistedTimeInterval)
        assertFieldContains(3, persistedBufferTimeInterval)
        assertFieldContains(4, persistedBufferDistanceInterval)
        waitForText(russianSettingsTitle)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_CLOSE_BUTTON).assertIsDisplayed().performClick()
    }

    @Test
    fun launch_openSettings_and_toggleTracking() {
        val activity = composeRule.activity
        val continueLabel = activity.getString(R.string.permission_button_continue)
        val bufferTimeInterval = "3"
        val bufferDistanceInterval = "180"

        waitForTag(UiTestTags.HOME_SETTINGS_BUTTON)

        openSettings()

        replaceField(0, "waliot-smoke-01")
        replaceField(2, "6")
        replaceField(3, bufferTimeInterval)
        replaceField(4, bufferDistanceInterval)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SAVE_BUTTON).assertIsEnabled().performClick()

        waitForTag(UiTestTags.TRACKING_BUTTON_PLAY)

        startTrackingHandlingBatteryNotice(
            trackingPlayTag = UiTestTags.TRACKING_BUTTON_PLAY,
            trackingPauseTag = UiTestTags.TRACKING_BUTTON_PAUSE,
            continueLabel = continueLabel,
        )

        clickButtonUntilTag(
            currentTag = UiTestTags.TRACKING_BUTTON_PAUSE,
            expectedTag = UiTestTags.TRACKING_BUTTON_PLAY,
        )
    }

    @Test
    fun change_tracking_settings_while_active_and_restore_them_after_recreate() {
        val activity = composeRule.activity
        val continueLabel = activity.getString(R.string.permission_button_continue)
        val refreshedTrackerId = "waliot-smoke-02"
        val refreshedServer = "device.waliot.com:30032"
        val refreshedTimeInterval = "9"
        val refreshedBufferTimeInterval = "4"
        val refreshedBufferDistanceInterval = "250"

        waitForTag(UiTestTags.HOME_SETTINGS_BUTTON)
        openSettings()
        replaceField(0, refreshedTrackerId)
        replaceField(1, refreshedServer)
        replaceField(2, "6")
        replaceField(3, "2")
        replaceField(4, "150")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SAVE_BUTTON).assertIsEnabled().performClick()

        waitForTag(UiTestTags.TRACKING_BUTTON_PLAY)
        startTrackingHandlingBatteryNotice(
            trackingPlayTag = UiTestTags.TRACKING_BUTTON_PLAY,
            trackingPauseTag = UiTestTags.TRACKING_BUTTON_PAUSE,
            continueLabel = continueLabel,
        )

        openSettings()
        replaceField(0, refreshedTrackerId)
        replaceField(1, refreshedServer)
        replaceField(2, refreshedTimeInterval)
        replaceField(3, refreshedBufferTimeInterval)
        replaceField(4, refreshedBufferDistanceInterval)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SAVE_BUTTON).assertIsEnabled().performClick()

        waitForHomeScreen()

        composeRule.activityRule.scenario.recreate()
        waitForHomeScreen()

        openSettings()
        assertFieldContains(0, refreshedTrackerId)
        assertFieldContains(1, refreshedServer)
        assertFieldContains(2, refreshedTimeInterval)
        assertFieldContains(3, refreshedBufferTimeInterval)
        assertFieldContains(4, refreshedBufferDistanceInterval)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_CLOSE_BUTTON).assertIsDisplayed().performClick()

        waitForTag(UiTestTags.HOME_SETTINGS_BUTTON)
        if (composeRule.onAllNodesWithTag(UiTestTags.TRACKING_BUTTON_PAUSE).fetchSemanticsNodes().isNotEmpty()) {
            clickButtonUntilTag(
                currentTag = UiTestTags.TRACKING_BUTTON_PAUSE,
                expectedTag = UiTestTags.TRACKING_BUTTON_PLAY,
            )
        }
    }

    @Test
    fun invalid_settings_are_rejected_and_do_not_persist_after_reopen() {
        val activity = composeRule.activity
        val trackerIdentifierError = activity.getString(R.string.tracker_identifier_error)
        val uploadServerError = activity.getString(R.string.upload_server_error)
        val intervalError = activity.getString(R.string.interval_error)

        val persistedTrackerId = "waliot-smoke-valid"
        val persistedServer = "device.waliot.com:30032"
        val persistedTimeInterval = "7"
        val persistedBufferTimeInterval = "3"
        val persistedBufferDistanceInterval = "180"

        waitForTag(UiTestTags.HOME_SETTINGS_BUTTON)

        openSettings()
        replaceField(0, persistedTrackerId)
        replaceField(1, persistedServer)
        replaceField(2, persistedTimeInterval)
        replaceField(3, persistedBufferTimeInterval)
        replaceField(4, persistedBufferDistanceInterval)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SAVE_BUTTON).assertIsEnabled().performClick()

        waitForTag(UiTestTags.HOME_SETTINGS_BUTTON)
        openSettings()

        replaceField(0, "invalid id")
        replaceField(1, "bad server value")
        replaceField(2, "")
        replaceField(3, "")
        replaceField(4, "")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SAVE_BUTTON).assertIsNotEnabled()

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_CLOSE_BUTTON).assertIsDisplayed()
        waitForText(trackerIdentifierError)
        waitForText(uploadServerError)
        waitForTextOccurrences(intervalError, 3)

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_CLOSE_BUTTON).assertIsDisplayed().performClick()
        waitForTag(UiTestTags.HOME_SETTINGS_BUTTON)

        openSettings()
        assertFieldContains(0, persistedTrackerId)
        assertFieldContains(1, persistedServer)
        assertFieldContains(2, persistedTimeInterval)
        assertFieldContains(3, persistedBufferTimeInterval)
        assertFieldContains(4, persistedBufferDistanceInterval)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_CLOSE_BUTTON).assertIsDisplayed().performClick()
    }

    private fun openSettings() {
        waitForTag(UiTestTags.HOME_SETTINGS_BUTTON)
        repeat(3) {
            composeRule.onNodeWithTag(UiTestTags.HOME_SETTINGS_BUTTON).assertIsDisplayed().performClick()
            if (waitForTagIfPresent(UiTestTags.SETTINGS_CLOSE_BUTTON, shortTimeoutMillis = 5_000L)) {
                waitForFieldCount(5)
                return
            }
        }

        waitForTag(UiTestTags.SETTINGS_CLOSE_BUTTON)
        waitForFieldCount(5)
    }

    private fun replaceField(index: Int, value: String) {
        composeRule.onAllNodes(hasSetTextAction())[index].performTextReplacement(value)
    }

    private fun saveMinimalValidSettings(trackerIdentifier: String) {
        openSettings()
        replaceField(0, trackerIdentifier)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SAVE_BUTTON).assertIsEnabled().performClick()
        waitForHomeScreen()
    }

    private fun assertFieldContains(index: Int, value: String) {
        composeRule.onAllNodes(hasSetTextAction())[index].assertTextContains(value)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTagIfPresent(tag: String, shortTimeoutMillis: Long): Boolean {
        return try {
            composeRule.waitUntil(timeoutMillis = shortTimeoutMillis) {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
            true
        } catch (_: ComposeTimeoutException) {
            false
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTextOccurrences(text: String, minCount: Int) {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size >= minCount
        }
    }

    private fun waitForFieldCount(minCount: Int) {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= minCount
        }
    }

    private fun waitForHomeScreen() {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(UiTestTags.HOME_SETTINGS_BUTTON).fetchSemanticsNodes().isNotEmpty() &&
                (
                    composeRule.onAllNodesWithTag(UiTestTags.TRACKING_BUTTON_PLAY).fetchSemanticsNodes()
                        .isNotEmpty() ||
                        composeRule.onAllNodesWithTag(UiTestTags.TRACKING_BUTTON_PAUSE).fetchSemanticsNodes()
                            .isNotEmpty() ||
                        composeRule.onAllNodesWithTag(UiTestTags.TRACKING_BUTTON_STOP).fetchSemanticsNodes()
                            .isNotEmpty()
                    )
        }
    }

    private fun startTrackingHandlingBatteryNotice(
        trackingPlayTag: String,
        trackingPauseTag: String,
        continueLabel: String,
    ): Boolean {
        composeRule.onNodeWithTag(trackingPlayTag).assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(trackingPauseTag).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(continueLabel).fetchSemanticsNodes().isNotEmpty()
        }

        val showedBatteryDialog = composeRule.onAllNodesWithText(continueLabel).hasAnyNode()
        if (composeRule.onAllNodesWithText(continueLabel).hasAnyNode()) {
            composeRule.onNodeWithText(continueLabel).assertIsDisplayed().performClick()
        }

        waitForTag(trackingPauseTag)
        return showedBatteryDialog
    }

    private fun clickButtonUntilTag(
        currentTag: String,
        expectedTag: String,
    ) {
        waitForTag(currentTag)
        repeat(3) {
            composeRule.onNodeWithTag(currentTag).assertIsDisplayed().performClick()
            if (waitForTagIfPresent(expectedTag, shortTimeoutMillis = 5_000L)) {
                return
            }
        }

        waitForTag(expectedTag)
    }

    private fun startTrackingWithoutBatteryNotice(
        trackingPlayTag: String,
        trackingPauseTag: String,
        continueLabel: String,
    ) {
        waitForTag(trackingPlayTag)
        repeat(3) {
            composeRule.onNodeWithTag(trackingPlayTag).assertIsDisplayed().performClick()
            if (waitForTagIfPresent(trackingPauseTag, shortTimeoutMillis = 5_000L)) {
                return
            }
            if (composeRule.onAllNodesWithText(continueLabel).hasAnyNode()) {
                throw AssertionError("Battery optimization notice was shown more than once")
            }
        }

        if (composeRule.onAllNodesWithText(continueLabel).hasAnyNode()) {
            throw AssertionError("Battery optimization notice was shown more than once")
        }
        waitForTag(trackingPauseTag)
    }

    private fun SemanticsNodeInteractionCollection.hasAnyNode(): Boolean {
        return fetchSemanticsNodes().isNotEmpty()
    }
}

private class AppStateResetRule : TestRule {
    override fun apply(base: Statement, description: org.junit.runner.Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
                context.deleteDatabase("tracking-buffer.db")
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(SettingsRepository.DEFAULT_LANGUAGE)
                )
                base.evaluate()
            }
        }
    }
}
