package com.websmithing.gpstracker2

import android.Manifest
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    private val permissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(permissionRule)
        .around(composeRule)

    @Test
    fun launch_openSettings_and_toggleTracking() {
        val activity = composeRule.activity
        val settingsLabel = activity.getString(R.string.permission_button_settings)
        val settingsTitle = activity.getString(R.string.settings)
        val saveLabel = activity.getString(R.string.save)
        val trackingOffLabel = activity.getString(R.string.tracking_is_off)
        val trackingOnLabel = activity.getString(R.string.tracking_is_on)
        val bufferTimeInterval = "3"
        val bufferDistanceInterval = "180"

        waitForContentDescription(settingsLabel)

        openSettings(settingsLabel, settingsTitle)

        replaceField(0, "waliot-smoke-01")
        replaceField(2, "6")
        replaceField(3, bufferTimeInterval)
        replaceField(4, bufferDistanceInterval)
        composeRule.onNodeWithText(saveLabel).performClick()

        waitForContentDescription(trackingOffLabel)

        composeRule.onNodeWithContentDescription(trackingOffLabel).assertIsDisplayed().performClick()

        waitForContentDescription(trackingOnLabel)

        composeRule.onNodeWithContentDescription(trackingOnLabel).assertIsDisplayed().performClick()

        waitForContentDescription(trackingOffLabel)
    }

    @Test
    fun change_tracking_settings_while_active_and_restore_them_after_recreate() {
        val activity = composeRule.activity
        val settingsLabel = activity.getString(R.string.permission_button_settings)
        val settingsTitle = activity.getString(R.string.settings)
        val saveLabel = activity.getString(R.string.save)
        val closeLabel = activity.getString(R.string.close)
        val trackingOffLabel = activity.getString(R.string.tracking_is_off)
        val trackingOnLabel = activity.getString(R.string.tracking_is_on)
        val refreshedTrackerId = "waliot-smoke-02"
        val refreshedServer = "device.waliot.com:30032"
        val refreshedTimeInterval = "9"
        val refreshedBufferTimeInterval = "4"
        val refreshedBufferDistanceInterval = "250"

        waitForContentDescription(settingsLabel)
        openSettings(settingsLabel, settingsTitle)
        replaceField(0, refreshedTrackerId)
        replaceField(1, refreshedServer)
        replaceField(2, "6")
        replaceField(3, "2")
        replaceField(4, "150")
        composeRule.onNodeWithText(saveLabel).performClick()

        waitForContentDescription(trackingOffLabel)
        composeRule.onNodeWithContentDescription(trackingOffLabel).assertIsDisplayed().performClick()
        waitForContentDescription(trackingOnLabel)

        openSettings(settingsLabel, settingsTitle)
        replaceField(0, refreshedTrackerId)
        replaceField(1, refreshedServer)
        replaceField(2, refreshedTimeInterval)
        replaceField(3, refreshedBufferTimeInterval)
        replaceField(4, refreshedBufferDistanceInterval)
        composeRule.onNodeWithText(saveLabel).performClick()

        waitForContentDescription(trackingOnLabel)

        composeRule.activityRule.scenario.recreate()
        waitForContentDescription(trackingOnLabel)

        openSettings(settingsLabel, settingsTitle)
        assertFieldContains(0, refreshedTrackerId)
        assertFieldContains(1, refreshedServer)
        assertFieldContains(2, refreshedTimeInterval)
        assertFieldContains(3, refreshedBufferTimeInterval)
        assertFieldContains(4, refreshedBufferDistanceInterval)
        composeRule.onNodeWithContentDescription(closeLabel).assertIsDisplayed().performClick()

        waitForContentDescription(trackingOnLabel)
        composeRule.onNodeWithContentDescription(trackingOnLabel).assertIsDisplayed().performClick()
        waitForContentDescription(trackingOffLabel)
    }

    @Test
    fun invalid_settings_are_rejected_and_do_not_persist_after_reopen() {
        val activity = composeRule.activity
        val settingsLabel = activity.getString(R.string.permission_button_settings)
        val settingsTitle = activity.getString(R.string.settings)
        val saveLabel = activity.getString(R.string.save)
        val closeLabel = activity.getString(R.string.close)
        val trackerIdentifierError = activity.getString(R.string.tracker_identifier_error)
        val uploadServerError = activity.getString(R.string.upload_server_error)
        val intervalError = activity.getString(R.string.interval_error)

        val persistedTrackerId = "waliot-smoke-valid"
        val persistedServer = "device.waliot.com:30032"
        val persistedTimeInterval = "7"
        val persistedBufferTimeInterval = "3"
        val persistedBufferDistanceInterval = "180"

        waitForContentDescription(settingsLabel)

        openSettings(settingsLabel, settingsTitle)
        replaceField(0, persistedTrackerId)
        replaceField(1, persistedServer)
        replaceField(2, persistedTimeInterval)
        replaceField(3, persistedBufferTimeInterval)
        replaceField(4, persistedBufferDistanceInterval)
        composeRule.onNodeWithText(saveLabel).performClick()

        waitForContentDescription(settingsLabel)
        openSettings(settingsLabel, settingsTitle)

        replaceField(0, "invalid id")
        replaceField(1, "bad server value")
        replaceField(2, "")
        replaceField(3, "")
        replaceField(4, "")
        composeRule.onNodeWithText(saveLabel).assertIsNotEnabled()

        composeRule.onNodeWithText(settingsTitle).assertIsDisplayed()
        waitForText(trackerIdentifierError)
        waitForText(uploadServerError)
        waitForTextOccurrences(intervalError, 3)

        composeRule.onNodeWithContentDescription(closeLabel).assertIsDisplayed().performClick()
        waitForContentDescription(settingsLabel)

        openSettings(settingsLabel, settingsTitle)
        assertFieldContains(0, persistedTrackerId)
        assertFieldContains(1, persistedServer)
        assertFieldContains(2, persistedTimeInterval)
        assertFieldContains(3, persistedBufferTimeInterval)
        assertFieldContains(4, persistedBufferDistanceInterval)
        composeRule.onNodeWithContentDescription(closeLabel).assertIsDisplayed().performClick()
    }

    private fun openSettings(settingsLabel: String, settingsTitle: String) {
        composeRule.onNodeWithContentDescription(settingsLabel).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(settingsTitle).assertIsDisplayed()
    }

    private fun replaceField(index: Int, value: String) {
        composeRule.onAllNodes(hasSetTextAction())[index].performTextReplacement(value)
    }

    private fun assertFieldContains(index: Int, value: String) {
        composeRule.onAllNodes(hasSetTextAction())[index].assertTextContains(value)
    }

    private fun waitForContentDescription(label: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTextOccurrences(text: String, minCount: Int) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size >= minCount
        }
    }
}
