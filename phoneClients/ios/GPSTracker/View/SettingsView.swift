// /Users/nickfox137/Documents/gpstracker-clients/gpstracker-ios/GPSTracker/GPSTracker/View/SettingsView.swift

import SwiftUI

/// Settings configuration view for the GPS Tracker app
///
/// This view allows users to configure tracking settings, server details,
/// and manage app permissions.
///
/// ## Overview
/// SettingsView provides interfaces for:
/// - Setting user identification details
/// - Configuring server URL and API endpoints
/// - Adjusting tracking frequency and accuracy
/// - Managing location permissions
///
/// ## Topics
/// ### User Settings
/// - ``username``
/// - ``serverUrl``
/// - ``trackingInterval``
///
/// ### Permissions
/// - ``requestLocationPermission()``
struct SettingsView: View {
    /// The view model that manages app state
    @EnvironmentObject private var viewModel: TrackingViewModel
    
    @EnvironmentObject private var lang: LanguageManager
    
    /// Environment object to dismiss this view
    @Environment(\.dismiss) private var dismiss
    
    /// Username for identifying the device on the server
    @State private var username: String = ""
    
    /// Server URL for uploading location data
    @State private var serverUrl: String = "device.waliot.com:30032"
    
    /// Tracking frequency in minutes
    @State private var trackingInterval: Double = 1
    
    /// Minimum distance between location updates in meters
    @State private var distanceFilter: Double = 10
    
    /// Flag indicating if the app should continue tracking in background
    @State private var trackInBackground: Bool = true
    
    /// The body of the view defining its content and layout
    var body: some View {
        NavigationView {
            Form {
                // User identification section
                Section(header: Text("settings.user.header")) {
                    TextField("settings.user.username", text: $username)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .keyboardType(.numberPad)
                }
                
                // Server configuration section
                Section(header: Text("settings.server.header")) {
                    TextField("settings.server.url", text: $serverUrl)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .keyboardType(.URL)
                }
                
                // Tracking settings section
                Section(header: Text("settings.tracking.header")) {
                    HStack {
                        Text("settings.tracking.updateInterval")
                        Spacer()
                        Text(
                            String.localizedStringWithFormat(
                                String(localized: "units.minutes", locale: lang.locale),
                                Int(trackingInterval)
                            )
                        )
                    }
                    Slider(value: $trackingInterval, in: 1...30, step: 1)
                    
                    HStack {
                        Text("settings.tracking.distanceFilter")
                        Spacer()
                        Text(
                            String.localizedStringWithFormat(
                                String(localized: "units.meters", locale: lang.locale),
                                Int(distanceFilter)
                            )
                        )
                    }
                    Slider(value: $distanceFilter, in: 10...100, step: 1)
                    
                    Toggle("settings.tracking.trackInBackground", isOn: $trackInBackground)
                }
                
//                Section(header: Text("settings.lang.header")) {
//                    Picker("", selection: $lang.code) {
//                        Text("Русский").tag("ru")
//                        Text("English").tag("en")
//                    }
//                    .pickerStyle(.segmented)
//                }
                
                // Permissions section
                Section(header: Text("settings.permissions.header")) {
                    VStack(alignment: .leading) {
                        HStack {
                            Text("settings.permissions.location.title")
                            Spacer()
                            Text(viewModel.locationAuthorizationStatus.description)
                                .foregroundColor(locationStatusColor)
                        }
        
                        Button("settings.permissions.open") {
                            viewModel.requestLocationPermissions(always: true)
                        }
                        .foregroundColor(.blue)
                        .padding(.top, 4)
                    }
                }
                
                // App info section
                Section(header: Text("settings.about.header")) {
                    HStack {
                        Text("settings.about.version")
                        Spacer()
                        Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—")
                    }
                }
            }
            .navigationTitle(Text("settings.title"))
            .navigationBarItems(trailing: Button("settings.done") {
                saveSettings()
                dismiss()
            })
            .onAppear {
                // Load current settings when view appears
                loadSettings()
            }
        }
    }
    
    /// The color to display based on location authorization status
    private var locationStatusColor: Color {
        switch viewModel.locationAuthorizationStatus {
        case .authorizedAlways:
            return .green
        case .authorizedWhenInUse:
            return .yellow
        case .denied, .restricted:
            return .red
        case .notDetermined:
            return .gray
        @unknown default:
            return .gray
        }
    }
    
    /// Opens the system settings app to allow changing permissions
    private func openAppSettings() {
        guard let settingsURL = URL(string: UIApplication.openSettingsURLString) else {
            return
        }
        
        if UIApplication.shared.canOpenURL(settingsURL) {
            UIApplication.shared.open(settingsURL)
        }
    }
    
    /// Loads current settings from the view model
    private func loadSettings() {
        username = viewModel.username
        serverUrl = viewModel.serverUrl
        trackingInterval = Double(viewModel.trackingInterval)
        distanceFilter = Double(viewModel.distanceFilter)
        trackInBackground = viewModel.trackInBackground
    }
    
    /// Saves the current settings to the view model
    private func saveSettings() {
        viewModel.username = username
        viewModel.serverUrl = serverUrl
        viewModel.trackingInterval = Int(trackingInterval)
        viewModel.distanceFilter = Int(distanceFilter)
        viewModel.trackInBackground = trackInBackground
        viewModel.saveSettings()

        // Apply settings to active tracking if currently tracking
        if viewModel.isTracking {
            viewModel.applySettings()
        }
    }
}

/// Preview provider for displaying SettingsView in Xcode previews
struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        SettingsView()
            .environmentObject(MockDependencies.previewViewModel)
            .preferredColorScheme(.dark)
    }
}
