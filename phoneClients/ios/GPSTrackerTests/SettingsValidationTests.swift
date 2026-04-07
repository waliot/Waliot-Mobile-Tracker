import Testing
@testable import GPSTracker

struct SettingsValidationTests {
    @Test
    func trackerIdentifierValidationAcceptsOnlyAsciiLettersAndDigits() {
        #expect(isTrackerIdentifierValid("waliot001"))
        #expect(isTrackerIdentifierValid("ABC123"))
        #expect(!isTrackerIdentifierValid(""))
        #expect(!isTrackerIdentifierValid("waliot-001"))
        #expect(!isTrackerIdentifierValid("waliot 001"))
        #expect(!isTrackerIdentifierValid("waliot;001"))
        #expect(!isTrackerIdentifierValid("валиот001"))
    }

    @Test
    func trackerIdentifierSanitizerFallsBackForInvalidValues() {
        #expect(sanitizeTrackerIdentifier("waliot001", defaultValue: "") == "waliot001")
        #expect(sanitizeTrackerIdentifier("waliot-001", defaultValue: "") == "")
        #expect(sanitizeTrackerIdentifier("  ABC123  ", defaultValue: "") == "ABC123")
    }

    @Test
    func uploadServerValidationAcceptsSupportedHostFormatsAndRejectsMalformedValues() {
        #expect(isUploadServerAddressValid("device.waliot.com:30032"))
        #expect(isUploadServerAddressValid("tracker.example.com"))
        #expect(isUploadServerAddressValid("tcp://127.0.0.1:30032"))
        #expect(isUploadServerAddressValid("tls://tracker.example.com:443"))

        #expect(!isUploadServerAddressValid(""))
        #expect(!isUploadServerAddressValid("bad server value"))
        #expect(!isUploadServerAddressValid("device.waliot.com:not-a-port"))
        #expect(!isUploadServerAddressValid("127.0.0.1:70000"))
        #expect(!isUploadServerAddressValid("https://tracker.example.com:443"))
        #expect(!isUploadServerAddressValid("tcp://:30032"))
    }

    @Test
    func positiveIntervalValidationRejectsEmptyZeroAndMalformedValues() {
        #expect(isPositiveIntervalValid("1"))
        #expect(isPositiveIntervalValid("25"))
        #expect(!isPositiveIntervalValid(""))
        #expect(!isPositiveIntervalValid("0"))
        #expect(!isPositiveIntervalValid("-1"))
        #expect(!isPositiveIntervalValid("abc"))
    }

    @Test
    func settingsFormValidationMarksEachInvalidField() {
        let validated = validateSettingsFormState(
            state: SettingsFormState(
                trackerIdentifier: "bad id",
                uploadServer: "bad server value",
                uploadTimeInterval: "0",
                bufferTimeInterval: "",
                bufferDistanceInterval: "0"
            ),
            messages: SettingsValidationMessages(
                trackerIdentifierError: "bad identifier",
                uploadServerError: "bad server",
                intervalError: "bad interval"
            )
        )

        #expect(validated.isValid == false)
        #expect(validated.trackerIdentifierError == "bad identifier")
        #expect(validated.uploadServerError == "bad server")
        #expect(validated.uploadTimeIntervalError == "bad interval")
        #expect(validated.bufferTimeIntervalError == "bad interval")
        #expect(validated.bufferDistanceIntervalError == "bad interval")
    }

    @Test
    func settingsFormValidationKeepsValidFieldsClearOfErrors() {
        let validated = validateSettingsFormState(
            state: SettingsFormState(
                trackerIdentifier: "ABC123",
                uploadServer: "device.waliot.com:30032",
                uploadTimeInterval: "5",
                bufferTimeInterval: "1",
                bufferDistanceInterval: "100"
            ),
            messages: SettingsValidationMessages(
                trackerIdentifierError: "bad identifier",
                uploadServerError: "bad server",
                intervalError: "bad interval"
            )
        )

        #expect(validated.isValid)
        #expect(validated.trackerIdentifierError == nil)
        #expect(validated.uploadServerError == nil)
        #expect(validated.uploadTimeIntervalError == nil)
        #expect(validated.bufferTimeIntervalError == nil)
        #expect(validated.bufferDistanceIntervalError == nil)
    }

    @Test
    func settingsFormStateComparesOnlyUserInputsWhenCheckingChanges() {
        let initial = SettingsFormState(
            trackerIdentifier: "ABC123",
            uploadServer: "device.waliot.com:30032",
            uploadTimeInterval: "5",
            bufferTimeInterval: "1",
            bufferDistanceInterval: "100"
        )
        var sameInputsDifferentErrors = initial
        sameInputsDifferentErrors.trackerIdentifierError = "error"
        sameInputsDifferentErrors.uploadServerError = "error"

        let changedInputs = SettingsFormState(
            trackerIdentifier: initial.trackerIdentifier,
            uploadServer: initial.uploadServer,
            uploadTimeInterval: initial.uploadTimeInterval,
            bufferTimeInterval: initial.bufferTimeInterval,
            bufferDistanceInterval: "150",
            trackInBackground: initial.trackInBackground,
            languageCode: initial.languageCode
        )

        #expect(initial.hasSameInputs(as: sameInputsDifferentErrors))
        #expect(!initial.hasSameInputs(as: changedInputs))
    }

    @Test
    func singleLineSanitizerRemovesCarriageReturnsAndLineFeeds() {
        #expect(sanitizeSingleLineInput("waliot\n001") == "waliot001")
        #expect(sanitizeSingleLineInput("device.waliot.com:\r30032") == "device.waliot.com:30032")
        #expect(sanitizeSingleLineInput("a\r\nb\nc1\r23") == "abc123")
    }

    @Test
    func positiveIntervalStringSanitizerFallsBackToDefaultsForInvalidValues() {
        #expect(sanitizePositiveIntervalString("15", defaultValue: 5) == "15")
        #expect(sanitizePositiveIntervalString("0", defaultValue: 5) == "5")
        #expect(sanitizePositiveIntervalString("1\n2", defaultValue: 5) == "12")
        #expect(sanitizePositiveIntervalString("abc", defaultValue: 5) == "5")
    }
}
