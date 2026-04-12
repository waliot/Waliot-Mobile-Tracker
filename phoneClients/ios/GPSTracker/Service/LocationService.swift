// /Users/nickfox137/Documents/gpstracker-clients/gpstracker-ios/GPSTracker/GPSTracker/Service/LocationService.swift

import Foundation
import CoreLocation
import Combine
import os
import UIKit

/// A protocol defining the interface for location tracking services.
///
/// This protocol abstracts the details of location management, allowing for
/// easier testing and potential alternative implementations.
///
/// ## Overview
/// The location service is responsible for:
/// - Requesting and managing location permissions
/// - Providing continuous location updates
/// - Monitoring authorization status changes
///
/// ## Topics
/// ### Location Updates
/// - ``locationPublisher``
/// - ``startUpdatingLocation()``
/// - ``stopUpdatingLocation()``
///
/// ### Permission Management
/// - ``authorizationStatusPublisher``
/// - ``requestPermissions()``
/// - ``getCurrentAuthorizationStatus()``
protocol LocationServiceProtocol {
    /// Publisher for location updates
    ///
    /// Subscribe to this publisher to receive real-time location data
    var locationPublisher: AnyPublisher<CLLocation, Never> { get }
    
    /// Publisher for location authorization status changes
    ///
    /// Subscribe to this publisher to respond to permission changes
    var authorizationStatusPublisher: AnyPublisher<CLAuthorizationStatus, Never> { get }

    func requestWhenInUsePermissions()
    
    func requestAlwaysPermissions()
    
    /// Updates whether background tracking should stay enabled while a tracking session is active.
    func setBackgroundTrackingEnabled(_ enabled: Bool)

    /// Starts foreground-only location updates used to present live navigation state in the UI.
    func startObservingNavigationStatus()

    /// Stops foreground-only location updates used to present live navigation state in the UI.
    func stopObservingNavigationStatus()

    /// Starts the location update process
    ///
    /// Begins continuous location monitoring. Requires appropriate permissions.
    func startUpdatingLocation()

    /// Stops the location update process
    ///
    /// Halts continuous location monitoring to conserve battery.
    func stopUpdatingLocation()

    /// Gets the current authorization status
    ///
    /// - Returns: The current location authorization status
    func getCurrentAuthorizationStatus() -> CLAuthorizationStatus
    
    /// Returns the current accuracy authorization level used by Core Location.
    func getCurrentAccuracyAuthorization() -> CLAccuracyAuthorization
}

/// Implementation of the LocationServiceProtocol using Core Location
///
/// This class manages device location tracking using iOS Core Location services.
/// It provides continuous location updates and handles permission changes.
///
/// ## Overview
/// The location service is configured for high accuracy tracking with background
/// capabilities enabled. It handles permission changes and delivers location
/// updates via Combine publishers.
///
/// ## Battery Considerations
/// This service is optimized for accurate tracking rather than battery efficiency.
/// It monitors battery levels to provide this information to the server.
class LocationService: NSObject, LocationServiceProtocol, CLLocationManagerDelegate {
    /// The Core Location manager that provides location updates
    private let locationManager: CLLocationManager
    
    /// Logger for diagnostic information
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "com.waliot.tracker", category: "LocationService")
    private var backgroundTrackingEnabled = true
    private var isUpdatingLocation = false
    private var isObservingNavigationStatus = false

    // MARK: - Publishers
    
    /// Subject that emits location updates
    private let _locationSubject = PassthroughSubject<CLLocation, Never>()
    
    /// Publisher for location updates
    ///
    /// Subscribe to this publisher to receive location data in real-time.
    /// The publisher emits CLLocation objects containing coordinates, accuracy,
    /// speed, and other location-related information.
    var locationPublisher: AnyPublisher<CLLocation, Never> {
        _locationSubject.eraseToAnyPublisher()
    }

    /// Subject that emits authorization status changes
    private let _authorizationStatusSubject = PassthroughSubject<CLAuthorizationStatus, Never>()
    
    /// Publisher for location authorization status changes
    ///
    /// Subscribe to this publisher to be notified when the user changes
    /// location permission settings.
    var authorizationStatusPublisher: AnyPublisher<CLAuthorizationStatus, Never> {
        _authorizationStatusSubject.eraseToAnyPublisher()
    }

    /// Initializes the location service
    ///
    /// Sets up the Core Location manager with appropriate settings for
    /// continuous tracking in both foreground and background.
    override init() {
        self.locationManager = CLLocationManager()
        super.init()
        self.locationManager.delegate = self
        self.locationManager.desiredAccuracy = kCLLocationAccuracyBest // High accuracy needed for tracking
        self.locationManager.pausesLocationUpdatesAutomatically = false // Prevent system from pausing updates
        self.locationManager.activityType = .automotiveNavigation // Vehicle-centric tracking profile
        reconcileLocationDelivery()

        // Enable battery monitoring for reporting to server
        UIDevice.current.isBatteryMonitoringEnabled = true
        log("Battery monitoring enabled.", logger: logger)

        log("LocationService initialized. Current status: \(self.getCurrentAuthorizationStatus().description)", level: .info, logger: logger)
        // Publish initial status
        _authorizationStatusSubject.send(self.getCurrentAuthorizationStatus())
    }

    func requestWhenInUsePermissions() {        
        log("Requesting 'When In Use' location permissions.", level: .info, logger: logger)
        locationManager.requestWhenInUseAuthorization()
    }
    
    func requestAlwaysPermissions() {
        log("Requesting 'Always' location permissions.", level: .info, logger: logger)
        locationManager.requestAlwaysAuthorization()
    }
    
    func setBackgroundTrackingEnabled(_ enabled: Bool) {
        backgroundTrackingEnabled = enabled
        reconcileLocationDelivery()
        log("Background tracking preference updated to \(enabled)", level: .info, logger: logger)
    }

    func startObservingNavigationStatus() {
        let status = getCurrentAuthorizationStatus()
        guard status == .authorizedAlways || status == .authorizedWhenInUse else {
            log("Cannot start navigation status updates. Authorization status: \(status.description)", level: .info, logger: logger)
            return
        }

        guard !isObservingNavigationStatus else { return }

        log("Starting foreground navigation status updates.", level: .info, logger: logger)
        isObservingNavigationStatus = true
        locationManager.startUpdatingLocation()
        reconcileLocationDelivery()
    }

    func stopObservingNavigationStatus() {
        guard isObservingNavigationStatus else { return }

        log("Stopping foreground navigation status updates.", level: .info, logger: logger)
        isObservingNavigationStatus = false
        if !isUpdatingLocation {
            locationManager.stopUpdatingLocation()
        }
        reconcileLocationDelivery()
    }

    /// Starts the location update process
    ///
    /// Begins continuous location monitoring if appropriate permissions
    /// have been granted. Otherwise, logs an error and requests permissions
    /// if not yet determined.
    func startUpdatingLocation() {
        let status = getCurrentAuthorizationStatus()
        guard status == .authorizedAlways || status == .authorizedWhenInUse else {
            log("Cannot start location updates. Authorization status: \(status.description)", level: .error, logger: logger)
            // Optionally request permissions again or handle error
            if status == .notDetermined {
                requestAlwaysPermissions()
            }
            return
        }

        log("Starting location updates.", level: .info, logger: logger)
        isUpdatingLocation = true
        locationManager.startUpdatingLocation()
        reconcileLocationDelivery()
    }

    /// Stops the location update process
    ///
    /// Halts continuous location monitoring to conserve battery.
    func stopUpdatingLocation() {
        log("Stopping location updates.", level: .info, logger: logger)
        isUpdatingLocation = false
        if !isObservingNavigationStatus {
            locationManager.stopUpdatingLocation()
        }
        reconcileLocationDelivery()
    }

    /// Gets the current authorization status
    ///
    /// - Returns: The current location permissions status
    func getCurrentAuthorizationStatus() -> CLAuthorizationStatus {
        return locationManager.authorizationStatus
    }
    
    func getCurrentAccuracyAuthorization() -> CLAccuracyAuthorization {
        locationManager.accuracyAuthorization
    }

    // MARK: - CLLocationManagerDelegate Methods

    /// Processes location updates from Core Location
    ///
    /// - Parameters:
    ///   - manager: The location manager providing the update
    ///   - locations: Array of location objects in chronological order
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        // Process the latest location
        guard let location = locations.last else {
            log("Received empty locations array in didUpdateLocations.", level: .error, logger: logger)
            return
        }
        log("Received location update from Core Location", logger: logger)
        // Publish the location
        _locationSubject.send(location)
    }

    /// Handles location update failures
    ///
    /// - Parameters:
    ///   - manager: The location manager reporting the error
    ///   - error: The error that occurred
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        log("Location manager failed with error: \(error.localizedDescription)", level: .error, logger: logger)
        // Error handling strategy depends on error type
        // Some errors may be transient and can be ignored
    }

    /// Processes changes to location authorization status
    ///
    /// - Parameter manager: The location manager reporting the status change
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let newStatus = manager.authorizationStatus
        log("Location authorization status changed to: \(newStatus.description)", level: .info, logger: logger)
        // Publish the new status
        _authorizationStatusSubject.send(newStatus)
        reconcileLocationDelivery()

        // Handle status changes
        switch newStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            log("Authorization granted.", logger: logger)
        case .denied, .restricted:
            log("Location access denied or restricted. Stopping updates.", level: .error, logger: logger)
            isObservingNavigationStatus = false
            stopUpdatingLocation() // Ensure updates are stopped if permissions revoked
        case .notDetermined:
            log("Authorization status is not determined.", level: .info, logger: logger)
        @unknown default:
            log("Unknown location authorization status encountered.", level: .fault, logger: logger)
        }
    }

    private func reconcileLocationDelivery() {
        let authorizationStatus = getCurrentAuthorizationStatus()
        let shouldAllowBackgroundUpdates = isUpdatingLocation && backgroundTrackingEnabled
        let shouldMonitorSignificantChanges = isUpdatingLocation &&
            backgroundTrackingEnabled &&
            authorizationStatus == .authorizedAlways &&
            CLLocationManager.significantLocationChangeMonitoringAvailable()

        locationManager.allowsBackgroundLocationUpdates = shouldAllowBackgroundUpdates
        locationManager.showsBackgroundLocationIndicator = isUpdatingLocation &&
            backgroundTrackingEnabled &&
            authorizationStatus == .authorizedAlways

        if shouldMonitorSignificantChanges {
            locationManager.startMonitoringSignificantLocationChanges()
        } else if CLLocationManager.significantLocationChangeMonitoringAvailable() {
            locationManager.stopMonitoringSignificantLocationChanges()
        }
    }
}
