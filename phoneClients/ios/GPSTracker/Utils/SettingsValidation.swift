import Foundation

private let trackerIdentifierAllowedCharacters = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")

func sanitizeSingleLineInput(_ input: String) -> String {
    input
        .replacingOccurrences(of: "\r", with: "")
        .replacingOccurrences(of: "\n", with: "")
}

func isTrackerIdentifierValid(_ identifier: String) -> Bool {
    let trimmed = sanitizeSingleLineInput(identifier).trimmingCharacters(in: .whitespacesAndNewlines)
    return !trimmed.isEmpty &&
        !trimmed.unicodeScalars.contains(where: { CharacterSet.whitespacesAndNewlines.contains($0) }) &&
        trimmed.unicodeScalars.allSatisfy { trackerIdentifierAllowedCharacters.contains($0) }
}

func sanitizeTrackerIdentifier(
    _ identifier: String,
    defaultValue: String = ""
) -> String {
    let sanitized = sanitizeSingleLineInput(identifier).trimmingCharacters(in: .whitespacesAndNewlines)
    return isTrackerIdentifierValid(sanitized) ? sanitized : defaultValue
}

func isPositiveIntervalValid(_ value: String) -> Bool {
    Int(value).map { $0 > 0 } ?? false
}

func sanitizePositiveInterval(
    _ value: Int,
    defaultValue: Int
) -> Int {
    value > 0 ? value : defaultValue
}

func sanitizePositiveIntervalString(
    _ value: String,
    defaultValue: Int
) -> String {
    let sanitized = sanitizeSingleLineInput(value).trimmingCharacters(in: .whitespacesAndNewlines)
    return Int(sanitized).flatMap { $0 > 0 ? String($0) : nil } ?? String(defaultValue)
}

struct SettingsValidationMessages {
    let trackerIdentifierError: String
    let uploadServerError: String
    let intervalError: String
}

struct SettingsFormState: Equatable {
    var trackerIdentifier: String = ""
    var uploadServer: String = ""
    var uploadTimeInterval: String = ""
    var bufferTimeInterval: String = ""
    var bufferDistanceInterval: String = ""
    var trackInBackground: Bool = true
    var languageCode: String = "ru"

    var trackerIdentifierError: String?
    var uploadServerError: String?
    var uploadTimeIntervalError: String?
    var bufferTimeIntervalError: String?
    var bufferDistanceIntervalError: String?
    var isValid: Bool = false

    func hasSameInputs(as other: SettingsFormState) -> Bool {
        trackerIdentifier == other.trackerIdentifier &&
            uploadServer == other.uploadServer &&
            uploadTimeInterval == other.uploadTimeInterval &&
            bufferTimeInterval == other.bufferTimeInterval &&
            bufferDistanceInterval == other.bufferDistanceInterval &&
            trackInBackground == other.trackInBackground &&
            languageCode == other.languageCode
    }
}

func validateSettingsFormState(
    state: SettingsFormState,
    messages: SettingsValidationMessages
) -> SettingsFormState {
    var validated = state
    validated.trackerIdentifier = sanitizeSingleLineInput(state.trackerIdentifier)
    validated.uploadServer = sanitizeSingleLineInput(state.uploadServer)
    validated.uploadTimeInterval = sanitizeSingleLineInput(state.uploadTimeInterval)
    validated.bufferTimeInterval = sanitizeSingleLineInput(state.bufferTimeInterval)
    validated.bufferDistanceInterval = sanitizeSingleLineInput(state.bufferDistanceInterval)

    validated.trackerIdentifierError = isTrackerIdentifierValid(validated.trackerIdentifier)
        ? nil
        : messages.trackerIdentifierError
    validated.uploadServerError = isUploadServerAddressValid(validated.uploadServer)
        ? nil
        : messages.uploadServerError
    validated.uploadTimeIntervalError = isPositiveIntervalValid(validated.uploadTimeInterval)
        ? nil
        : messages.intervalError
    validated.bufferTimeIntervalError = isPositiveIntervalValid(validated.bufferTimeInterval)
        ? nil
        : messages.intervalError
    validated.bufferDistanceIntervalError = isPositiveIntervalValid(validated.bufferDistanceInterval)
        ? nil
        : messages.intervalError
    validated.isValid = validated.trackerIdentifierError == nil &&
        validated.uploadServerError == nil &&
        validated.uploadTimeIntervalError == nil &&
        validated.bufferTimeIntervalError == nil &&
        validated.bufferDistanceIntervalError == nil
    return validated
}
