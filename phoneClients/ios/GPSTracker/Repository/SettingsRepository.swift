// /Users/nickfox137/Documents/gpstracker-clients/gpstracker-ios/GPSTracker/GPSTracker/Repository/SettingsRepository.swift

import Foundation
import os

/// Implementation of SettingsRepositoryProtocol
///
/// This class manages the storage and retrieval of application settings
/// using the persistence service.
///
/// ## Overview
/// The settings repository provides a type-safe interface for accessing
/// user preferences and application configuration. It uses the persistence
/// service for actual storage and applies default values when settings
/// don't exist.
///
/// ## Topics
/// ### User Identification
/// - ``getTrackerIdentifier()``
/// - ``saveTrackerIdentifier(_:)``
///
/// ### Server Configuration
/// - ``getUploadServer()``
/// - ``saveUploadServer(_:)``
///
/// ### Tracking Configuration
/// - ``getUploadTimeInterval()``
/// - ``getBufferTimeInterval()``
/// - ``getBufferDistanceInterval()``
class SettingsRepository: SettingsRepositoryProtocol {
    /// Logger for diagnostic information
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "com.waliot.tracker", category: "SettingsRepository")
    
    /// Service for data persistence
    private let persistenceService: PersistenceServiceProtocol
    
    /// Setting keys to avoid string literals throughout the code
    private enum SettingKeys {
        static let trackerIdentifier = "tracker_identifier"
        static let uploadServer = "upload_server"
        static let uploadTimeInterval = "upload_time_interval"
        static let bufferTimeInterval = "buffer_time_interval"
        static let bufferDistanceInterval = "buffer_distance_interval"
        static let trackInBackground = "track_in_background"
        static let trackingState = "tracking_state"
        static let appId = "app_id"
        
        static let legacyUsername = "username"
        static let legacyServerUrl = "server_url"
        static let legacyTrackingInterval = "tracking_interval"
        static let legacyDistanceFilter = "distance_filter"
        static let settingsMigrationVersion = "settings_migration_version"
    }
    
    /// Default values for settings
    private enum Defaults {
        static let trackerIdentifier = ""
        static let uploadServer = "device.waliot.com:30032"
        static let uploadTimeInterval = 5 // minutes
        static let bufferTimeInterval = 1 // minute
        static let bufferDistanceInterval = 100 // meters
        static let trackInBackground = true
        static let trackingState = false
        static let migrationVersion = 1
    }
    
    /// Initializes the settings repository with a persistence service
    ///
    /// - Parameter persistenceService: Service for storing and retrieving settings
    init(persistenceService: PersistenceServiceProtocol) {
        self.persistenceService = persistenceService
        migrateLegacySettingsIfNeeded()
        log("SettingsRepository initialized", logger: logger)
    }
    
    /// Retrieves the tracker identifier for identifying this device.
    func getTrackerIdentifier() -> String {
        let trackerIdentifier = sanitizeTrackerIdentifier(
            persistenceService.getValue(forKey: SettingKeys.trackerIdentifier, defaultValue: Defaults.trackerIdentifier),
            defaultValue: Defaults.trackerIdentifier
        )
        log("Retrieved tracker identifier from persistence", logger: logger)
        return trackerIdentifier
    }
    
    /// Stores a new tracker identifier.
    func saveTrackerIdentifier(_ trackerIdentifier: String) {
        let sanitized = sanitizeTrackerIdentifier(trackerIdentifier, defaultValue: Defaults.trackerIdentifier)
        persistenceService.setValue(sanitized, forKey: SettingKeys.trackerIdentifier)
        log("Saved tracker identifier", logger: logger)
    }
    
    /// Retrieves the server URL for uploading tracking data.
    func getUploadServer() -> String {
        let stored = sanitizeSingleLineInput(
            persistenceService.getValue(forKey: SettingKeys.uploadServer, defaultValue: Defaults.uploadServer)
        )
        return stored.isEmpty ? Defaults.uploadServer : stored
    }
    
    /// Stores a new server URL.
    func saveUploadServer(_ url: String) {
        let sanitized = sanitizeSingleLineInput(url).isEmpty ? Defaults.uploadServer : sanitizeSingleLineInput(url)
        persistenceService.setValue(sanitized, forKey: SettingKeys.uploadServer)
        log("Saved upload server configuration", logger: logger)
    }
    
    /// Retrieves the upload interval in minutes.
    func getUploadTimeInterval() -> Int {
        sanitizePositiveInterval(
            persistenceService.getValue(forKey: SettingKeys.uploadTimeInterval, defaultValue: Defaults.uploadTimeInterval),
            defaultValue: Defaults.uploadTimeInterval
        )
    }
    
    /// Stores a new upload interval in minutes.
    func saveUploadTimeInterval(_ interval: Int) {
        let sanitized = sanitizePositiveInterval(interval, defaultValue: Defaults.uploadTimeInterval)
        persistenceService.setValue(sanitized, forKey: SettingKeys.uploadTimeInterval)
        log("Saved upload time interval: \(sanitized) minutes", logger: logger)
    }
    
    /// Retrieves the time-based buffer interval in minutes.
    func getBufferTimeInterval() -> Int {
        sanitizePositiveInterval(
            persistenceService.getValue(forKey: SettingKeys.bufferTimeInterval, defaultValue: Defaults.bufferTimeInterval),
            defaultValue: Defaults.bufferTimeInterval
        )
    }
    
    /// Stores a new time-based buffer interval in minutes.
    func saveBufferTimeInterval(_ interval: Int) {
        let sanitized = sanitizePositiveInterval(interval, defaultValue: Defaults.bufferTimeInterval)
        persistenceService.setValue(sanitized, forKey: SettingKeys.bufferTimeInterval)
        log("Saved buffer time interval: \(sanitized) minutes", logger: logger)
    }
    
    /// Retrieves the distance-based buffer interval in meters.
    func getBufferDistanceInterval() -> Int {
        sanitizePositiveInterval(
            persistenceService.getValue(forKey: SettingKeys.bufferDistanceInterval, defaultValue: Defaults.bufferDistanceInterval),
            defaultValue: Defaults.bufferDistanceInterval
        )
    }
    
    /// Stores a new distance-based buffer interval in meters.
    func saveBufferDistanceInterval(_ distance: Int) {
        let sanitized = sanitizePositiveInterval(distance, defaultValue: Defaults.bufferDistanceInterval)
        persistenceService.setValue(sanitized, forKey: SettingKeys.bufferDistanceInterval)
        log("Saved buffer distance interval: \(sanitized) meters", logger: logger)
    }
    
    /// Retrieves the background tracking preference
    ///
    /// - Returns: Whether background tracking is enabled
    func getTrackInBackground() -> Bool {
        return persistenceService.getValue(forKey: SettingKeys.trackInBackground, defaultValue: Defaults.trackInBackground)
    }
    
    /// Stores a new background tracking preference
    ///
    /// - Parameter enabled: Whether background tracking should be enabled
    func saveTrackInBackground(_ enabled: Bool) {
        persistenceService.setValue(enabled, forKey: SettingKeys.trackInBackground)
        log("Saved track in background: \(enabled)", logger: logger)
    }

    /// Retrieves the persisted user intent for active tracking.
    func getTrackingState() -> Bool {
        persistenceService.getValue(forKey: SettingKeys.trackingState, defaultValue: Defaults.trackingState)
    }

    /// Stores the persisted user intent for active tracking.
    func saveTrackingState(_ isTracking: Bool) {
        persistenceService.setValue(isTracking, forKey: SettingKeys.trackingState)
        log("Saved tracking state: \(isTracking)", logger: logger)
    }
    
    /// Retrieves the app installation identifier
    ///
    /// - Returns: The stored app ID or generates a new one
    func getAppId() -> String {
        let appId = persistenceService.getValue(forKey: SettingKeys.appId, defaultValue: "")
        if appId.isEmpty {
            // Generate a new app ID if none exists
            let newAppId = UUID().uuidString
            saveAppId(newAppId)
            return newAppId
        }
        return appId
    }
    
    /// Stores a new app installation identifier
    ///
    /// - Parameter appId: The app ID to save
    func saveAppId(_ appId: String) {
        persistenceService.setValue(appId, forKey: SettingKeys.appId)
        log("Saved app installation identifier", logger: logger)
    }
    
    private func migrateLegacySettingsIfNeeded() {
        let currentVersion = persistenceService.getValue(
            forKey: SettingKeys.settingsMigrationVersion,
            defaultValue: 0
        )
        guard currentVersion < Defaults.migrationVersion else {
            return
        }
        
        if getTrackerIdentifier().isEmpty {
            let legacyTracker = sanitizeTrackerIdentifier(
                persistenceService.getValue(forKey: SettingKeys.legacyUsername, defaultValue: Defaults.trackerIdentifier),
                defaultValue: Defaults.trackerIdentifier
            )
            if !legacyTracker.isEmpty {
                saveTrackerIdentifier(legacyTracker)
            }
        }
        
        let uploadServer = persistenceService.getValue(
            forKey: SettingKeys.uploadServer,
            defaultValue: ""
        ) as String
        if sanitizeSingleLineInput(uploadServer).isEmpty {
            let legacyServer = sanitizeSingleLineInput(
                persistenceService.getValue(forKey: SettingKeys.legacyServerUrl, defaultValue: Defaults.uploadServer)
            )
            saveUploadServer(legacyServer)
        }
        
        let storedUploadInterval = persistenceService.getValue(
            forKey: SettingKeys.uploadTimeInterval,
            defaultValue: 0
        ) as Int
        let storedBufferTimeInterval = persistenceService.getValue(
            forKey: SettingKeys.bufferTimeInterval,
            defaultValue: 0
        ) as Int
        let storedBufferDistanceInterval = persistenceService.getValue(
            forKey: SettingKeys.bufferDistanceInterval,
            defaultValue: 0
        ) as Int
        
        let legacyTrackingInterval = sanitizePositiveInterval(
            persistenceService.getValue(forKey: SettingKeys.legacyTrackingInterval, defaultValue: 0),
            defaultValue: 0
        )
        let legacyDistanceFilter = sanitizePositiveInterval(
            persistenceService.getValue(forKey: SettingKeys.legacyDistanceFilter, defaultValue: 0),
            defaultValue: 0
        )
        
        if storedUploadInterval <= 0 {
            saveUploadTimeInterval(legacyTrackingInterval > 0 ? legacyTrackingInterval : Defaults.uploadTimeInterval)
        }
        if storedBufferTimeInterval <= 0 {
            saveBufferTimeInterval(legacyTrackingInterval > 0 ? legacyTrackingInterval : Defaults.bufferTimeInterval)
        }
        if storedBufferDistanceInterval <= 0 {
            saveBufferDistanceInterval(legacyDistanceFilter > 0 ? legacyDistanceFilter : Defaults.bufferDistanceInterval)
        }
        
        persistenceService.setValue(Defaults.migrationVersion, forKey: SettingKeys.settingsMigrationVersion)
        log("Legacy settings migration completed", logger: logger)
    }
}
