package com.websmithing.gpstracker2.ui.features.settings

import com.websmithing.gpstracker2.repository.settings.canRunTrackingConfiguration
import com.websmithing.gpstracker2.ui.features.settings.components.sanitizeSingleLineInput
import com.websmithing.gpstracker2.ui.features.settings.model.SettingsFormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPageValidationTest {

    @Test
    fun `tracker identifier validation allows protocol compatible non blank identifiers`() {
        assertTrue(isTrackerIdentifierValid("12345"))
        assertTrue(isTrackerIdentifierValid("waliot-01"))
        assertFalse(isTrackerIdentifierValid(""))
        assertFalse(isTrackerIdentifierValid("12 345"))
        assertFalse(isTrackerIdentifierValid("waliot;01"))
        assertFalse(isTrackerIdentifierValid("waliot\n01"))
    }

    @Test
    fun `upload server validation allows supported host formats and rejects malformed values`() {
        assertTrue(isUploadServerAddressValid("device.waliot.com:30032"))
        assertTrue(isUploadServerAddressValid("tracker.example.com"))
        assertTrue(isUploadServerAddressValid("tcp://127.0.0.1:30032"))
        assertTrue(isUploadServerAddressValid("tls://tracker.example.com:443"))

        assertFalse(isUploadServerAddressValid(""))
        assertFalse(isUploadServerAddressValid("bad server value"))
        assertFalse(isUploadServerAddressValid("device.waliot.com:not-a-port"))
        assertFalse(isUploadServerAddressValid("127.0.0.1:70000"))
        assertFalse(isUploadServerAddressValid("https://tracker.example.com:443"))
        assertFalse(isUploadServerAddressValid("tcp://:30032"))
    }

    @Test
    fun `positive interval validation rejects empty zero and malformed values`() {
        assertTrue(isPositiveIntervalValid("1"))
        assertTrue(isPositiveIntervalValid("25"))
        assertFalse(isPositiveIntervalValid(""))
        assertFalse(isPositiveIntervalValid("0"))
        assertFalse(isPositiveIntervalValid("-1"))
        assertFalse(isPositiveIntervalValid("abc"))
    }

    @Test
    fun `settings form validation marks each invalid field`() {
        val validated = validateSettingsFormState(
            state = SettingsFormState(
                trackerIdentifier = "bad id",
                uploadServer = "bad server value",
                uploadTimeInterval = "0",
                bufferTimeInterval = "",
                bufferDistanceInterval = "0",
            ),
            messages = SettingsValidationMessages(
                trackerIdentifierError = "bad identifier",
                uploadServerError = "bad server",
                intervalError = "bad interval",
            ),
        )

        assertFalse(validated.isValid)
        assertEquals("bad identifier", validated.trackerIdentifierError)
        assertEquals("bad server", validated.uploadServerError)
        assertEquals("bad interval", validated.uploadTimeIntervalError)
        assertEquals("bad interval", validated.bufferTimeIntervalError)
        assertEquals("bad interval", validated.bufferDistanceIntervalError)
    }

    @Test
    fun `settings form validation keeps valid fields clear of errors`() {
        val validated = validateSettingsFormState(
            state = SettingsFormState(
                trackerIdentifier = "waliot-01",
                uploadServer = "device.waliot.com:30032",
                uploadTimeInterval = "5",
                bufferTimeInterval = "1",
                bufferDistanceInterval = "100",
            ),
            messages = SettingsValidationMessages(
                trackerIdentifierError = "bad identifier",
                uploadServerError = "bad server",
                intervalError = "bad interval",
            ),
        )

        assertTrue(validated.isValid)
        assertNull(validated.trackerIdentifierError)
        assertNull(validated.uploadServerError)
        assertNull(validated.uploadTimeIntervalError)
        assertNull(validated.bufferTimeIntervalError)
        assertNull(validated.bufferDistanceIntervalError)
    }

    @Test
    fun `settings form state compares only user inputs when checking changes`() {
        val initial = SettingsFormState(
            trackerIdentifier = "waliot-01",
            uploadServer = "device.waliot.com:30032",
            uploadTimeInterval = "5",
            bufferTimeInterval = "1",
            bufferDistanceInterval = "100",
        )
        val sameInputsDifferentErrors = initial.copy(
            trackerIdentifierError = "error",
            uploadServerError = "error",
        )
        val changedInputs = initial.copy(bufferDistanceInterval = "150")

        assertTrue(initial.hasSameInputs(sameInputsDifferentErrors))
        assertFalse(initial.hasSameInputs(changedInputs))
    }

    @Test
    fun `tracking configuration gate follows strict settings validation rules`() {
        assertTrue(canRunTrackingConfiguration("waliot-01", "device.waliot.com:30032"))
        assertFalse(canRunTrackingConfiguration("bad id", "device.waliot.com:30032"))
        assertFalse(canRunTrackingConfiguration("waliot-01", "bad server value"))
        assertFalse(canRunTrackingConfiguration("", "device.waliot.com:30032"))
    }

    @Test
    fun `single line sanitizer removes carriage returns and line feeds`() {
        assertEquals("waliot-01", sanitizeSingleLineInput("waliot-\n01"))
        assertEquals("device.waliot.com:30032", sanitizeSingleLineInput("device.waliot.com:\r30032"))
        assertEquals("abc123", sanitizeSingleLineInput("a\r\nb\nc1\r23"))
    }
}
