package com.websmithing.gpstracker2

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.websmithing.gpstracker2.service.TrackingService
import com.websmithing.gpstracker2.ui.UiTestTags
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class LocationDisclosureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun location_request_waits_for_disclosure_confirmation_on_first_attempt_and_retry() {
        val activity = composeRule.activity
        assumeTrue(
            "The disclosure test requires an installation without location permission",
            activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED &&
                activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED,
        )
        enableSystemViewIds()

        val foregroundTitle = activity.getString(R.string.permission_rationale_foreground_location_title)
        val backgroundTitle = activity.getString(R.string.permission_rationale_background_location_title)
        val agree = activity.getString(R.string.permission_button_grant)
        val decline = activity.getString(R.string.permission_button_deny)

        waitForDisclosure(foregroundTitle)
        composeRule.onNodeWithText(agree).performClick()
        clickForegroundPermissionButton("permission_deny_button")

        composeRule.onNodeWithText(foregroundTitle).assertDoesNotExist()
        configureTestTracker()
        composeRule.onNodeWithTag(UiTestTags.TRACKING_BUTTON_PLAY).performClick()

        waitForDisclosure(foregroundTitle)
        composeRule.onNodeWithText(agree).performClick()
        clickForegroundPermissionButton("permission_allow_foreground_only_button")

        waitForDisclosure(backgroundTitle)
        composeRule.onNodeWithText(decline).performClick()
        composeRule.waitForIdle()
        assertTrue(permissionControllerWindow() == null)
        composeRule.onNodeWithTag(UiTestTags.TRACKING_BUTTON_PLAY).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.TRACKING_BUTTON_PLAY).performClick()
        waitForDisclosure(backgroundTitle)
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    @Suppress("DEPRECATION")
    fun tracking_start_offers_notifications_once_without_prompting_on_home() {
        val activity = composeRule.activity
        assumeTrue(
            "This test needs both location grants and an unset notification grant",
            activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                activity.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED,
        )
        enableSystemViewIds()

        configureTestTracker()
        assertNull(permissionControllerWindow())
        composeRule.onNodeWithTag(UiTestTags.TRACKING_BUTTON_PLAY).performClick()
        val continueLabel = activity.getString(R.string.permission_button_continue)
        if (composeRule.onAllNodesWithText(continueLabel).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) {
            composeRule.onNodeWithText(continueLabel).performClick()
        }

        composeRule.waitUntil(10_000) {
            permissionControllerButton("permission_deny_button")?.isEnabled == true
        }
        assertNull(permissionControllerButton("permission_allow_foreground_only_button"))
        assertTrue(
            "The system notification denial button can be selected",
            permissionControllerButton("permission_deny_button")?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true,
        )
        composeRule.waitUntil(10_000) { permissionControllerWindow() == null }

        waitForTag(UiTestTags.TRACKING_BUTTON_PAUSE)
        val activityManager = activity.getSystemService(ActivityManager::class.java)
        composeRule.waitUntil(10_000) {
            activityManager.getRunningServices(Int.MAX_VALUE).any {
                it.service.className == TrackingService::class.java.name && it.foreground
            }
        }
        composeRule.onNodeWithTag(UiTestTags.TRACKING_BUTTON_PAUSE).performClick()
        waitForTag(UiTestTags.TRACKING_BUTTON_PLAY)
        composeRule.activityRule.scenario.recreate()
        waitForTag(UiTestTags.TRACKING_BUTTON_PLAY)
        assertNull(permissionControllerWindow())
        composeRule.onNodeWithTag(UiTestTags.TRACKING_BUTTON_PLAY).performClick()
        waitForTag(UiTestTags.TRACKING_BUTTON_PAUSE)
        assertNull(permissionControllerWindow())
        composeRule.onNodeWithTag(UiTestTags.TRACKING_BUTTON_PAUSE).performClick()
    }

    private fun configureTestTracker() {
        waitForTag(UiTestTags.HOME_SETTINGS_BUTTON)
        composeRule.onNodeWithTag(UiTestTags.HOME_SETTINGS_BUTTON).performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("waliot-permission-test")
        composeRule.onAllNodes(hasSetTextAction())[1].performTextReplacement("127.0.0.1:33334")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SAVE_BUTTON).performClick()
        waitForTag(UiTestTags.TRACKING_BUTTON_PLAY)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun enableSystemViewIds() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    private fun waitForDisclosure(title: String) {
        composeRule.waitUntil(15_000) {
            check(permissionControllerWindow() == null) {
                "Android requested a permission before the app displayed its location disclosure"
            }
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        composeRule.onNodeWithText(title).assertIsDisplayed()
    }

    private fun clickForegroundPermissionButton(viewId: String) {
        fun foregroundPermissionButton(): AccessibilityNodeInfo? {
            if (permissionControllerButton("permission_allow_foreground_only_button") == null) return null
            return permissionControllerButton(viewId)
        }

        composeRule.waitUntil(10_000) { foregroundPermissionButton() != null }
        assertTrue(foregroundPermissionButton()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        composeRule.waitUntil(10_000) { permissionControllerWindow() == null }
    }

    private fun permissionControllerButton(viewId: String): AccessibilityNodeInfo? =
        permissionControllerWindow()
            ?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$viewId")
            ?.firstOrNull()

    private fun permissionControllerWindow(): AccessibilityNodeInfo? =
        InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            ?.takeIf { it.packageName.toString().endsWith(".permissioncontroller") }
}
