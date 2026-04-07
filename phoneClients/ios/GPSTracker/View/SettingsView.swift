// /Users/nickfox137/Documents/gpstracker-clients/gpstracker-ios/GPSTracker/GPSTracker/View/SettingsView.swift

import SwiftUI
import UIKit

struct SettingsView: View {
    @Environment(\.colorScheme) private var colorScheme
    @EnvironmentObject private var viewModel: TrackingViewModel
    @EnvironmentObject private var lang: LanguageManager
    @Environment(\.dismiss) private var dismiss

    @State private var draft = SettingsFormState()
    @State private var persistedState = SettingsFormState()

    var body: some View {
        NavigationStack {
            Form {
                trackerSection
                serverSection
                trackingSection
                languageSection
                permissionsSection
                aboutSection
            }
            .formStyle(.grouped)
            .listSectionSpacing(20)
            .scrollContentBackground(.hidden)
            .background(Color(uiColor: .systemGroupedBackground))
            .tint(.accentColor)
            .navigationTitle(Text("settings.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.regularMaterial, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("settings.actions.cancel") {
                        dismiss()
                    }
                    .accessibilityIdentifier("settings.cancel")
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("settings.actions.save") {
                        saveSettings()
                    }
                    .disabled(!canSave)
                    .accessibilityIdentifier("settings.save")
                }
            }
            .onAppear(perform: loadSettings)
        }
    }

    private var trackerSection: some View {
        Section {
            ValidatedTextField(
                title: String(localized: "settings.user.username"),
                text: trackerIdentifierBinding,
                error: draft.trackerIdentifierError,
                keyboardType: .asciiCapable,
                textContentType: .username,
                accessibilityIdentifier: "settings.field.trackerIdentifier"
            )
        } header: {
            sectionHeader("settings.user.header")
        }
    }

    private var serverSection: some View {
        Section {
            ValidatedTextField(
                title: String(localized: "settings.server.url"),
                text: uploadServerBinding,
                error: draft.uploadServerError,
                keyboardType: .URL,
                textContentType: .URL,
                accessibilityIdentifier: "settings.field.uploadServer"
            )
        } header: {
            sectionHeader("settings.server.header")
        }
    }

    private var trackingSection: some View {
        Section {
            ValidatedTextField(
                title: String(localized: "settings.tracking.uploadInterval"),
                text: uploadIntervalBinding,
                error: draft.uploadTimeIntervalError,
                keyboardType: .numberPad,
                accessibilityIdentifier: "settings.field.uploadInterval"
            )
            ValidatedTextField(
                title: String(localized: "settings.tracking.bufferTimeInterval"),
                text: bufferTimeIntervalBinding,
                error: draft.bufferTimeIntervalError,
                keyboardType: .numberPad,
                accessibilityIdentifier: "settings.field.bufferTimeInterval"
            )
            ValidatedTextField(
                title: String(localized: "settings.tracking.bufferDistanceInterval"),
                text: bufferDistanceIntervalBinding,
                error: draft.bufferDistanceIntervalError,
                keyboardType: .numberPad,
                accessibilityIdentifier: "settings.field.bufferDistanceInterval"
            )

            Toggle("settings.tracking.trackInBackground", isOn: trackInBackgroundBinding)
                .accessibilityIdentifier("settings.toggle.trackInBackground")

            if draft.trackInBackground && viewModel.locationAuthorizationStatus != .authorizedAlways {
                Text("settings.permissions.backgroundHint")
                    .font(.footnote)
                    .foregroundStyle(.orange)
            }
        } header: {
            sectionHeader("settings.tracking.header")
        }
    }

    private var languageSection: some View {
        Section {
            Picker("settings.lang.header", selection: languageBinding) {
                Text("settings.language.russian").tag("ru")
                Text("settings.language.english").tag("en")
            }
            .pickerStyle(.segmented)
        } header: {
            sectionHeader("settings.lang.header")
        }
    }

    private var permissionsSection: some View {
        Section {
            LabeledContent("settings.permissions.location.title", value: viewModel.locationAuthorizationStatus.description)
                .foregroundStyle(locationStatusColor)
                .accessibilityIdentifier("settings.permission.status")

            if permissionActionTitle != nil {
                Button(permissionActionTitle ?? "") {
                    handlePermissionAction()
                }
                .accessibilityIdentifier("settings.permission.action")
            }
        } header: {
            sectionHeader("settings.permissions.header")
        }
    }

    private var aboutSection: some View {
        Section {
            LabeledContent(
                "settings.about.version",
                value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? String(localized: "common.placeholder.unavailable")
            )
            LabeledContent(
                "settings.about.build",
                value: Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? String(localized: "common.placeholder.unavailable")
            )
        } header: {
            sectionHeader("settings.about.header")
        }
    }

    private var canSave: Bool {
        draft.isValid && !draft.hasSameInputs(as: persistedState)
    }

    private var locationStatusColor: Color {
        switch viewModel.locationAuthorizationStatus {
        case .authorizedAlways:
            return .green
        case .authorizedWhenInUse:
            return .yellow
        case .denied, .restricted:
            return .red
        case .notDetermined:
            return Color(uiColor: .secondaryLabel)
        @unknown default:
            return Color(uiColor: .secondaryLabel)
        }
    }

    private var permissionActionTitle: String? {
        switch viewModel.locationAuthorizationStatus {
        case .notDetermined:
            return draft.trackInBackground
                ? String(localized: "settings.permissions.requestAlways")
                : String(localized: "settings.permissions.requestWhenInUse")
        case .authorizedWhenInUse:
            return draft.trackInBackground ? String(localized: "settings.permissions.requestAlways") : nil
        case .denied, .restricted:
            return String(localized: "settings.permissions.openSettings")
        case .authorizedAlways:
            return nil
        @unknown default:
            return String(localized: "settings.permissions.openSettings")
        }
    }

    private var validationMessages: SettingsValidationMessages {
        SettingsValidationMessages(
            trackerIdentifierError: String(localized: "settings.validation.trackerIdentifier"),
            uploadServerError: String(localized: "settings.validation.uploadServer"),
            intervalError: String(localized: "settings.validation.interval")
        )
    }

    private var trackerIdentifierBinding: Binding<String> {
        Binding(
            get: { draft.trackerIdentifier },
            set: { newValue in
                updateDraft { $0.trackerIdentifier = sanitizeSingleLineInput(newValue) }
            }
        )
    }

    private var uploadServerBinding: Binding<String> {
        Binding(
            get: { draft.uploadServer },
            set: { newValue in
                updateDraft { $0.uploadServer = sanitizeSingleLineInput(newValue) }
            }
        )
    }

    private var uploadIntervalBinding: Binding<String> {
        Binding(
            get: { draft.uploadTimeInterval },
            set: { newValue in
                updateDraft { $0.uploadTimeInterval = newValue.filter(\.isNumber) }
            }
        )
    }

    private var bufferTimeIntervalBinding: Binding<String> {
        Binding(
            get: { draft.bufferTimeInterval },
            set: { newValue in
                updateDraft { $0.bufferTimeInterval = newValue.filter(\.isNumber) }
            }
        )
    }

    private var bufferDistanceIntervalBinding: Binding<String> {
        Binding(
            get: { draft.bufferDistanceInterval },
            set: { newValue in
                updateDraft { $0.bufferDistanceInterval = newValue.filter(\.isNumber) }
            }
        )
    }

    private var trackInBackgroundBinding: Binding<Bool> {
        Binding(
            get: { draft.trackInBackground },
            set: { newValue in
                updateDraft { $0.trackInBackground = newValue }
            }
        )
    }

    private var languageBinding: Binding<String> {
        Binding(
            get: { draft.languageCode },
            set: { newValue in
                updateDraft { $0.languageCode = newValue }
            }
        )
    }

    private func loadSettings() {
        let loaded = validateSettingsFormState(
            state: SettingsFormState(
                trackerIdentifier: viewModel.trackerIdentifier,
                uploadServer: viewModel.uploadServer,
                uploadTimeInterval: String(viewModel.uploadTimeInterval),
                bufferTimeInterval: String(viewModel.bufferTimeInterval),
                bufferDistanceInterval: String(viewModel.bufferDistanceInterval),
                trackInBackground: viewModel.trackInBackground,
                languageCode: lang.code
            ),
            messages: validationMessages
        )
        draft = loaded
        persistedState = loaded
    }

    private func saveSettings() {
        guard canSave else { return }

        viewModel.trackerIdentifier = sanitizeTrackerIdentifier(draft.trackerIdentifier, defaultValue: "")
        viewModel.uploadServer = sanitizeSingleLineInput(draft.uploadServer).trimmingCharacters(in: .whitespacesAndNewlines)
        viewModel.uploadTimeInterval = sanitizePositiveInterval(Int(draft.uploadTimeInterval) ?? 0, defaultValue: 5)
        viewModel.bufferTimeInterval = sanitizePositiveInterval(Int(draft.bufferTimeInterval) ?? 0, defaultValue: 1)
        viewModel.bufferDistanceInterval = sanitizePositiveInterval(Int(draft.bufferDistanceInterval) ?? 0, defaultValue: 100)
        viewModel.trackInBackground = draft.trackInBackground
        viewModel.saveSettings()
        lang.code = draft.languageCode

        persistedState = validateSettingsFormState(state: draft, messages: validationMessages)
        dismiss()
    }

    private func updateDraft(_ mutate: (inout SettingsFormState) -> Void) {
        var updated = draft
        mutate(&updated)
        draft = validateSettingsFormState(state: updated, messages: validationMessages)
    }

    private func handlePermissionAction() {
        switch viewModel.locationAuthorizationStatus {
        case .notDetermined:
            viewModel.requestLocationPermissions(always: draft.trackInBackground)
        case .authorizedWhenInUse:
            if draft.trackInBackground {
                viewModel.requestLocationPermissions(always: true)
            }
        case .denied, .restricted:
            openAppSettings()
        case .authorizedAlways:
            break
        @unknown default:
            openAppSettings()
        }
    }

    private func openAppSettings() {
        guard let settingsURL = URL(string: UIApplication.openSettingsURLString),
              UIApplication.shared.canOpenURL(settingsURL) else {
            return
        }
        UIApplication.shared.open(settingsURL)
    }

    private func sectionHeader(_ titleKey: LocalizedStringKey) -> some View {
        Text(titleKey)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(Color(uiColor: .secondaryLabel))
            .textCase(nil)
    }
}

private struct ValidatedTextField: View {
    @Environment(\.colorScheme) private var colorScheme

    let title: String
    let text: Binding<String>
    let error: String?
    var keyboardType: UIKeyboardType = .default
    var textContentType: UITextContentType? = nil
    var accessibilityIdentifier: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Color(uiColor: .secondaryLabel))

            TextField("", text: text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(keyboardType)
                .textContentType(textContentType)
                .lineLimit(1)
                .submitLabel(.done)
                .accessibilityLabel(Text(title))
                .accessibilityIdentifier(accessibilityIdentifier ?? "")
                .padding(.vertical, 10)
                .padding(.horizontal, 12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color(uiColor: .secondarySystemGroupedBackground))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(
                            error == nil ? Color(uiColor: .separator).opacity(colorScheme == .dark ? 0.28 : 0.18) : Color.red,
                            lineWidth: error == nil ? 0.8 : 1.2
                        )
                )
                .shadow(
                    color: Color.black.opacity(colorScheme == .dark ? 0.08 : 0.03),
                    radius: 4,
                    y: 1
                )

            if let error {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
        }
    }
}

struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        SettingsView()
            .environmentObject(MockDependencies.previewViewModel)
            .environmentObject(LanguageManager())
            .preferredColorScheme(.dark)
    }
}
