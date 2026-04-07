
import SwiftUI
import os // Import OSLog

/// The main entry point for the GPSTracker iOS application.
///
/// This struct handles application initialization, dependency injection, and user interface setup.
/// It follows a clean architecture design pattern with separate view, viewmodel, repository and service layers.
///
/// - Important: This app requires location permissions to function correctly.
///
/// ## Overview
/// GPSTracker is an open source location tracking application that records and transmits
/// location data to a server. It operates in both foreground and background modes to
/// provide continuous tracking capabilities.
///
/// ## Topics
/// ### Application Structure
/// - ``ContentView``
/// - ``TrackingViewModel``
/// - ``LocationRepository``
/// - ``SettingsRepository``
@main
struct GPSTrackerApp: App {
    private static let processInfo = ProcessInfo.processInfo
    // Logger for the application lifecycle - Use static for access before init if needed elsewhere
    /// Application-wide logger for monitoring and debugging
    private static let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "com.waliot.tracker", category: "GPSTrackerApp")
    
    private static let isUITestMode =
        processInfo.arguments.contains("UITEST_MODE") ||
        processInfo.environment["UITEST_MODE"] == "1"
    private static let shouldResetUITestState =
        processInfo.arguments.contains("UITEST_RESET_STATE") ||
        processInfo.environment["UITEST_RESET_STATE"] == "1"
    private static let uiTestDefaultsSuite = "com.waliot.tracker.uitests"

    // MARK: - Dependencies
    
    /// Persistence service for user settings and local data storage
    private static let persistenceService: PersistenceServiceProtocol = {
        if isUITestMode {
            let defaults = UserDefaults(suiteName: uiTestDefaultsSuite) ?? .standard
            if shouldResetUITestState {
                defaults.removePersistentDomain(forName: uiTestDefaultsSuite)
                defaults.set("9876543210", forKey: "com.waliot.tracker.tracker_identifier")
                defaults.set("device.waliot.com:30032", forKey: "com.waliot.tracker.upload_server")
            }
            return PersistenceService(userDefaults: defaults)
        }
        return PersistenceService()
    }()
    
    /// Repository for managing user settings and preferences
    private static let settingsRepository: SettingsRepositoryProtocol = SettingsRepository(persistenceService: persistenceService)
    
    /// Service for managing location updates and permissions
    private static let locationService: LocationServiceProtocol = isUITestMode ? MockLocationService() : LocationService()
    
    /// Persistent store for buffered tracking points.
    private static let trackingBufferStore: TrackingBufferStoreProtocol = {
        let store = TrackingBufferStore()
        if shouldResetUITestState {
            store.clear()
        }
        return store
    }()
    
    /// Service for communication with the remote tracking server
    private static let apiService: APIServiceProtocol = isUITestMode
        ? UITestAPIService()
        : WialonIpsService(settingsRepository: settingsRepository)
    
    /// Repository for location data processing and transmission
    private static let locationRepository: LocationRepositoryProtocol = LocationRepository(
        locationService: locationService,
        apiService: apiService,
        settingsRepository: settingsRepository
    )

    /// Primary view model that coordinates tracking functionality
    ///
    /// This StateObject is injected into the view hierarchy through the environment
    @StateObject private var viewModel: TrackingViewModel = TrackingViewModel(
        locationRepository: Self.locationRepository,
        settingsRepository: Self.settingsRepository,
        locationService: Self.locationService,
        bufferStore: Self.trackingBufferStore
    )

    @StateObject private var lang = LanguageManager()
    
    /// The root scene of the application
    ///
    /// Creates the main window group and injects dependencies into the environment
    var body: some Scene {
        WindowGroup {
            makeContentView()
                .onAppear {
                    // Log appearance from the WindowGroup level
                    log("Root WindowGroup appeared.", level: .debug, logger: Self.logger)
                }
        }
    }

    /// Creates the main content view with properly injected dependencies
    ///
    /// - Returns: The configured ContentView with its required environment objects
    private func makeContentView() -> some View {
        ContentView()
            .environmentObject(viewModel)
            .environmentObject(lang)
            .environment(\.locale, lang.locale)
    }
}
