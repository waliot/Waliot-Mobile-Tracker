// /Users/nickfox137/Documents/gpstracker-clients/gpstracker-ios/GPSTracker/GPSTracker/Repository/RepositoryProtocols.swift

import Foundation
import Combine

/// Protocol defining the interface for location data operations
///
/// This protocol abstracts the storage, processing, and transmission
/// of location data in the application.
///
/// ## Overview
/// The location repository is responsible for:
/// - Coordinating between location service and API service
/// - Managing location data processing
/// - Handling upload of location data to the server
///
/// ## Topics
/// ### Location Uploads
/// - ``uploadLocation(parameters:)``
protocol LocationRepositoryProtocol {
    /// Uploads location data to the tracking server
    ///
    /// - Parameter parameters: The location data and metadata to send
    /// - Returns: A publisher that emits upload success or failure
    func uploadLocation(parameters: LocationAPIRequestParameters) -> AnyPublisher<APIResponse, Error>
}

/// Protocol defining the interface for settings operations
///
/// This protocol abstracts the storage and retrieval of application
/// settings and user preferences.
///
/// ## Overview
/// The settings repository is responsible for:
/// - Storing and retrieving user preferences
/// - Managing configuration data
/// - Providing default values for settings
///
/// ## Topics
/// ### User Identification
/// - ``getTrackerIdentifier()``
/// - ``saveTrackerIdentifier(_:)``
/// - ``getAppId()``
/// - ``saveAppId(_:)``
///
/// ### Server Configuration
/// - ``getUploadServer()``
/// - ``saveUploadServer(_:)``
///
/// ### Tracking Settings
/// - ``getUploadTimeInterval()``
/// - ``saveUploadTimeInterval(_:)``
/// - ``getBufferTimeInterval()``
/// - ``saveBufferTimeInterval(_:)``
/// - ``getBufferDistanceInterval()``
/// - ``saveBufferDistanceInterval(_:)``
/// - ``getTrackInBackground()``
/// - ``saveTrackInBackground(_:)``
protocol SettingsRepositoryProtocol {
    /// Retrieves the tracker identifier used for this device on the server.
    func getTrackerIdentifier() -> String
    
    /// Stores a new tracker identifier.
    func saveTrackerIdentifier(_ trackerIdentifier: String)
    
    /// Retrieves the upload server address for tracking data.
    func getUploadServer() -> String
    
    /// Stores a new upload server address.
    func saveUploadServer(_ url: String)
    
    /// Retrieves the upload retry cadence in minutes.
    func getUploadTimeInterval() -> Int
    
    /// Stores a new upload retry cadence in minutes.
    func saveUploadTimeInterval(_ interval: Int)
    
    /// Retrieves the time-based buffer sampling interval in minutes.
    func getBufferTimeInterval() -> Int
    
    /// Stores a new time-based buffer sampling interval in minutes.
    func saveBufferTimeInterval(_ interval: Int)
    
    /// Retrieves the distance-based buffer sampling interval in meters.
    func getBufferDistanceInterval() -> Int
    
    /// Stores a new distance-based buffer sampling interval in meters.
    func saveBufferDistanceInterval(_ distance: Int)
    
    /// Retrieves the background tracking preference
    func getTrackInBackground() -> Bool
    
    /// Stores a new background tracking preference
    func saveTrackInBackground(_ enabled: Bool)

    /// Retrieves the last user-selected tracking runtime state.
    func getTrackingState() -> Bool

    /// Stores the last user-selected tracking runtime state.
    func saveTrackingState(_ isTracking: Bool)
    
    /// Retrieves the app installation identifier
    func getAppId() -> String
    
    /// Stores a new app installation identifier
    func saveAppId(_ appId: String)
}

extension SettingsRepositoryProtocol {
    @available(*, deprecated, message: "Use getTrackerIdentifier() instead.")
    func getUsername() -> String {
        getTrackerIdentifier()
    }
    
    @available(*, deprecated, message: "Use saveTrackerIdentifier(_:) instead.")
    func saveUsername(_ username: String) {
        saveTrackerIdentifier(username)
    }
    
    @available(*, deprecated, message: "Use getUploadServer() instead.")
    func getServerUrl() -> String {
        getUploadServer()
    }
    
    @available(*, deprecated, message: "Use saveUploadServer(_:) instead.")
    func saveServerUrl(_ url: String) {
        saveUploadServer(url)
    }
    
    @available(*, deprecated, message: "Use getUploadTimeInterval() instead.")
    func getTrackingInterval() -> Int {
        getUploadTimeInterval()
    }
    
    @available(*, deprecated, message: "Use saveUploadTimeInterval(_:) instead.")
    func saveTrackingInterval(_ interval: Int) {
        saveUploadTimeInterval(interval)
    }
    
    @available(*, deprecated, message: "Use getBufferDistanceInterval() instead.")
    func getDistanceFilter() -> Int {
        getBufferDistanceInterval()
    }
    
    @available(*, deprecated, message: "Use saveBufferDistanceInterval(_:) instead.")
    func saveDistanceFilter(_ distance: Int) {
        saveBufferDistanceInterval(distance)
    }
}
