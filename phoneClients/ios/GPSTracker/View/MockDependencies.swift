// /Users/nickfox137/Documents/gpstracker-clients/gpstracker-ios/GPSTracker/GPSTracker/View/MockDependencies.swift

import Foundation
import Combine
import CoreLocation
import UIKit

/// Provides mock dependencies for previews and testing
///
/// This file contains mock implementations of the app's protocols
/// to facilitate UI previews in Xcode and unit testing.
///
/// ## Overview
/// MockDependencies creates versions of services and repositories that
/// provide consistent, predetermined responses without requiring actual
/// system resources or networking.
///
/// ## Topics
/// ### Mock Factories
/// - ``previewViewModel``
/// - ``mockLocationService()``
/// - ``mockSettingsRepository()``

/// A mock implementation of LocationRepositoryProtocol for testing and previews
class MockLocationRepository: LocationRepositoryProtocol {
    /// Simulates uploading location data
    ///
    /// Instead of making real network requests, this method returns
    /// a simulated successful response after a short delay.
    ///
    /// - Parameter parameters: The location parameters that would be sent to the server
    /// - Returns: A publisher that simulates a successful API response
    func uploadLocation(parameters: LocationAPIRequestParameters) -> AnyPublisher<APIResponse, Error> {
        // Simulate a successful response with a slight delay
        return Just(APIResponse(status: "success", message: "Mock upload successful"))
            .delay(for: .seconds(0.5), scheduler: RunLoop.main)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
}

/// A mock implementation of SettingsRepositoryProtocol for testing and previews
class MockSettingsRepository: SettingsRepositoryProtocol {
    /// Mock settings storage
    private var settings: [String: Any] = [
        "tracker_identifier": "9876543210",
        "upload_server": "device.waliot.com:30032",
        "upload_time_interval": 5,
        "buffer_time_interval": 1,
        "buffer_distance_interval": 100,
        "track_in_background": true,
        "tracking_state": false,
        "app_id": "mock_app_id_12345"
    ]
    
    /// Returns the mock tracker identifier.
    func getTrackerIdentifier() -> String {
        return settings["tracker_identifier"] as? String ?? ""
    }
    
    /// Returns the mock server URL
    /// - Returns: A predefined server URL for testing
    func getUploadServer() -> String {
        return settings["upload_server"] as? String ?? "device.waliot.com:30032"
    }
    
    /// Returns the mock upload interval.
    func getUploadTimeInterval() -> Int {
        return settings["upload_time_interval"] as? Int ?? 5
    }
    
    /// Returns the mock buffer time interval.
    func getBufferTimeInterval() -> Int {
        return settings["buffer_time_interval"] as? Int ?? 1
    }
    
    /// Returns the mock buffer distance interval.
    func getBufferDistanceInterval() -> Int {
        return settings["buffer_distance_interval"] as? Int ?? 100
    }
    
    /// Returns the mock background tracking setting
    /// - Returns: A predefined background tracking setting
    func getTrackInBackground() -> Bool {
        return settings["track_in_background"] as? Bool ?? true
    }

    func getTrackingState() -> Bool {
        return settings["tracking_state"] as? Bool ?? false
    }
    
    /// Returns the mock app ID
    /// - Returns: A predefined app ID for testing
    func getAppId() -> String {
        return settings["app_id"] as? String ?? "mock_app_id_12345"
    }
    
    /// Stores a tracker identifier value (mock implementation).
    func saveTrackerIdentifier(_ trackerIdentifier: String) {
        settings["tracker_identifier"] = trackerIdentifier
    }
    
    /// Stores a server URL value (mock implementation)
    /// - Parameter url: The server URL to store
    func saveUploadServer(_ url: String) {
        settings["upload_server"] = url
    }
    
    /// Stores an upload interval value (mock implementation).
    func saveUploadTimeInterval(_ interval: Int) {
        settings["upload_time_interval"] = interval
    }
    
    /// Stores a buffer time interval value (mock implementation).
    func saveBufferTimeInterval(_ interval: Int) {
        settings["buffer_time_interval"] = interval
    }
    
    /// Stores a buffer distance interval value (mock implementation).
    func saveBufferDistanceInterval(_ distance: Int) {
        settings["buffer_distance_interval"] = distance
    }
    
    /// Stores a background tracking setting (mock implementation)
    /// - Parameter enabled: The background tracking setting to store
    func saveTrackInBackground(_ enabled: Bool) {
        settings["track_in_background"] = enabled
    }

    func saveTrackingState(_ isTracking: Bool) {
        settings["tracking_state"] = isTracking
    }
    
    /// Stores an app ID value (mock implementation)
    /// - Parameter appId: The app ID to store
    func saveAppId(_ appId: String) {
        settings["app_id"] = appId
    }
}

/// A mock implementation of LocationServiceProtocol for testing and previews
class MockLocationService: LocationServiceProtocol {
    private enum SimulationMode: String {
        case random
        case backgroundRoute
    }

    private static let initialAuthorizationStatus: CLAuthorizationStatus = {
        switch ProcessInfo.processInfo.environment["UITEST_LOCATION_AUTH"] {
        case "notDetermined":
            return .notDetermined
        case "whenInUse":
            return .authorizedWhenInUse
        case "denied":
            return .denied
        default:
            return .authorizedAlways
        }
    }()

    /// Subject for publishing location updates
    private let locationSubject = PassthroughSubject<CLLocation, Never>()
    
    /// Publisher for location updates
    var locationPublisher: AnyPublisher<CLLocation, Never> {
        return locationSubject.eraseToAnyPublisher()
    }
    
    /// Subject for publishing authorization status changes
    private let authorizationSubject = PassthroughSubject<CLAuthorizationStatus, Never>()
    
    /// Publisher for authorization status changes
    var authorizationStatusPublisher: AnyPublisher<CLAuthorizationStatus, Never> {
        return authorizationSubject.eraseToAnyPublisher()
    }
    
    /// Mock authorization status
    private var authStatus: CLAuthorizationStatus = MockLocationService.initialAuthorizationStatus

    private let simulationMode = SimulationMode(
        rawValue: ProcessInfo.processInfo.environment["UITEST_LOCATION_MODE"] ?? "random"
    ) ?? .random
    private let routeQueue = DispatchQueue(label: "MockLocationService.route")
    private var randomTimer: Timer?
    private var routeTimer: DispatchSourceTimer?
    private var lifecycleCancellables = Set<AnyCancellable>()
    private var backgroundTaskIdentifier: UIBackgroundTaskIdentifier = .invalid
    private var isUpdatingLocation = false
    private var isObservingNavigationStatus = false
    private var routeStep = 0
    private var backgroundEnteredAt: Date?
    private var routeStepAtBackgroundEntry = 0
    
    /// Initializes the mock location service
    init() {
        // Emit initial authorization status
        authorizationSubject.send(authStatus)

        if simulationMode == .backgroundRoute {
            NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)
                .sink { [weak self] _ in
                    self?.recordBackgroundEntry()
                    self?.beginBackgroundTaskIfNeeded()
                }
                .store(in: &lifecycleCancellables)

            NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)
                .sink { [weak self] _ in
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                        self?.emitBackgroundCatchUpIfNeeded()
                        self?.endBackgroundTask()
                    }
                }
                .store(in: &lifecycleCancellables)
        }
    }
    
    func requestWhenInUsePermissions() {
        // Simulate permission request with delayed response
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            guard let self = self else { return }
            self.authStatus = .authorizedWhenInUse
            self.authorizationSubject.send(self.authStatus)
        }
    }
    
    func requestAlwaysPermissions() {
        // Simulate permission request with delayed response
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            guard let self = self else { return }
            self.authStatus = .authorizedAlways
            self.authorizationSubject.send(self.authStatus)
        }
    }

    func setBackgroundTrackingEnabled(_ enabled: Bool) {
        _ = enabled
    }

    func startObservingNavigationStatus() {
        isObservingNavigationStatus = true
        startLocationEmissionIfNeeded()
    }

    func stopObservingNavigationStatus() {
        isObservingNavigationStatus = false
        stopLocationEmissionIfPossible()
    }
    
    /// Simulates starting location updates
    ///
    /// Instead of using real device location, this method
    /// generates synthetic location data on a timer.
    func startUpdatingLocation() {
        isUpdatingLocation = true
        startLocationEmissionIfNeeded()
    }
    
    /// Simulates stopping location updates
    ///
    /// Stops the timer that generates synthetic location data.
    func stopUpdatingLocation() {
        isUpdatingLocation = false
        stopLocationEmissionIfPossible()
    }
    
    /// Returns the mock authorization status
    /// - Returns: The current mock authorization status
    func getCurrentAuthorizationStatus() -> CLAuthorizationStatus {
        return authStatus
    }
    
    func getCurrentAccuracyAuthorization() -> CLAccuracyAuthorization {
        .fullAccuracy
    }

    private func startLocationEmissionIfNeeded() {
        guard authStatus == .authorizedAlways || authStatus == .authorizedWhenInUse else {
            return
        }

        switch simulationMode {
        case .random:
            startRandomTimerIfNeeded()
        case .backgroundRoute:
            startBackgroundRouteIfNeeded()
        }
    }

    private func stopLocationEmissionIfPossible() {
        guard !isUpdatingLocation && !isObservingNavigationStatus else { return }

        randomTimer?.invalidate()
        randomTimer = nil

        routeTimer?.cancel()
        routeTimer = nil
        endBackgroundTask()
    }

    private func startRandomTimerIfNeeded() {
        guard randomTimer == nil else { return }

        let timer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            self?.emitRandomLocation()
        }
        timer.tolerance = 0.5
        randomTimer = timer
        timer.fire()
    }

    private func emitRandomLocation() {
        let latitude = 45.040711 + Double.random(in: -0.01...0.01)
        let longitude = 39.031912 + Double.random(in: -0.01...0.01)
        let altitude = 10.0 + Double.random(in: 0...50)
        let speed = Double.random(in: 0...5)
        let course = Double.random(in: 0...360)

        let location = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            altitude: altitude,
            horizontalAccuracy: 10.0,
            verticalAccuracy: 10.0,
            course: course,
            speed: speed,
            timestamp: Date()
        )

        locationSubject.send(location)
    }

    private func startBackgroundRouteIfNeeded() {
        guard routeTimer == nil else { return }

        beginBackgroundTaskIfNeeded()

        let timer = DispatchSource.makeTimerSource(queue: routeQueue)
        timer.schedule(
            deadline: .now(),
            repeating: Self.backgroundRouteIntervalSeconds,
            leeway: .milliseconds(100)
        )
        timer.setEventHandler { [weak self] in
            self?.emitBackgroundRouteLocation()
        }
        routeTimer = timer
        timer.resume()
    }

    private func emitBackgroundRouteLocation() {
        emitBackgroundRouteLocation(at: Date())
    }

    private func emitBackgroundRouteLocation(at timestamp: Date) {
        let stepIndex = routeStep
        routeStep += 1

        let coordinate = Self.coordinate(
            from: Self.backgroundRouteStart,
            distanceMeters: Double(stepIndex) * Self.backgroundRouteStepMeters,
            bearingDegrees: Self.backgroundRouteBearingDegrees
        )
        let location = CLLocation(
            coordinate: coordinate,
            altitude: 12.0,
            horizontalAccuracy: 5.0,
            verticalAccuracy: 8.0,
            course: Self.backgroundRouteBearingDegrees,
            speed: Self.backgroundRouteSpeedMetersPerSecond,
            timestamp: timestamp
        )

        locationSubject.send(location)
    }

    private func recordBackgroundEntry() {
        guard simulationMode == .backgroundRoute else { return }
        backgroundEnteredAt = Date()
        routeStepAtBackgroundEntry = routeStep
    }

    private func emitBackgroundCatchUpIfNeeded() {
        guard simulationMode == .backgroundRoute else { return }
        guard let backgroundEnteredAt else { return }

        defer {
            self.backgroundEnteredAt = nil
            self.routeStepAtBackgroundEntry = routeStep
        }

        let elapsed = max(0, Date().timeIntervalSince(backgroundEnteredAt))
        let expectedAdditionalSteps = Int(elapsed / Self.backgroundRouteIntervalSeconds)
        guard expectedAdditionalSteps > 0 else { return }

        let expectedRouteStep = routeStepAtBackgroundEntry + expectedAdditionalSteps
        guard routeStep < expectedRouteStep else { return }

        let missingStepCount = expectedRouteStep - routeStep
        for _ in 0..<missingStepCount {
            let routeStepOffset = routeStep - routeStepAtBackgroundEntry + 1
            let stepTimeOffset = TimeInterval(routeStepOffset) * Self.backgroundRouteIntervalSeconds
            let timestamp = backgroundEnteredAt.addingTimeInterval(stepTimeOffset)
            emitBackgroundRouteLocation(at: timestamp)
        }
    }

    private func beginBackgroundTaskIfNeeded() {
        guard simulationMode == .backgroundRoute else { return }
        guard backgroundTaskIdentifier == .invalid else { return }
        guard isUpdatingLocation || isObservingNavigationStatus else { return }

        backgroundTaskIdentifier = UIApplication.shared.beginBackgroundTask(withName: "UITestMockRoute") { [weak self] in
            self?.endBackgroundTask()
        }
    }

    private func endBackgroundTask() {
        guard backgroundTaskIdentifier != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTaskIdentifier)
        backgroundTaskIdentifier = .invalid
    }

    private static let backgroundRouteStart = CLLocationCoordinate2D(
        latitude: 55.751244,
        longitude: 37.618423
    )
    private static let backgroundRouteBearingDegrees: CLLocationDirection = 18
    private static let backgroundRouteStepMeters: Double = 20
    private static let backgroundRouteIntervalSeconds: TimeInterval = 2
    private static let backgroundRouteSpeedMetersPerSecond: CLLocationSpeed = 10

    private static func coordinate(
        from start: CLLocationCoordinate2D,
        distanceMeters: Double,
        bearingDegrees: CLLocationDirection
    ) -> CLLocationCoordinate2D {
        let earthRadius = 6_378_137.0
        let bearing = bearingDegrees * .pi / 180
        let latitudeRadians = start.latitude * .pi / 180
        let longitudeRadians = start.longitude * .pi / 180
        let angularDistance = distanceMeters / earthRadius

        let newLatitude = asin(
            sin(latitudeRadians) * cos(angularDistance) +
            cos(latitudeRadians) * sin(angularDistance) * cos(bearing)
        )
        let newLongitude = longitudeRadians + atan2(
            sin(bearing) * sin(angularDistance) * cos(latitudeRadians),
            cos(angularDistance) - sin(latitudeRadians) * sin(newLatitude)
        )

        return CLLocationCoordinate2D(
            latitude: newLatitude * 180 / .pi,
            longitude: newLongitude * 180 / .pi
        )
    }
}

final class MockTrackingBufferStore: TrackingBufferStoreProtocol {
    private var state = TrackingBufferState()
    
    func loadState() -> TrackingBufferState {
        state
    }
    
    func saveState(_ state: TrackingBufferState) {
        self.state = state
    }
    
    func appendBufferedLocation(_ location: BufferedLocationRecord, maxSize: Int, lastBufferedLocation: LocationSnapshot?) {
        state.bufferedLocations.append(location)
        if state.bufferedLocations.count > maxSize {
            state.bufferedLocations = Array(state.bufferedLocations.suffix(maxSize))
        }
        state.lastBufferedLocation = lastBufferedLocation
    }
    
    func removeOldestBufferedLocation() {
        guard !state.bufferedLocations.isEmpty else { return }
        state.bufferedLocations.removeFirst()
    }
    
    func replaceLastBufferedLocation(_ location: LocationSnapshot?) {
        state.lastBufferedLocation = location
    }
    
    func clear() {
        state = TrackingBufferState()
    }
}

/// Factory for mock dependencies to use in SwiftUI previews
struct MockDependencies {
    /// Creates a fully configured view model with sample data for previews
    ///
    /// - Returns: A TrackingViewModel populated with realistic mock data
    static var previewViewModel: TrackingViewModel {
        let locationService = MockLocationService()
        let settingsRepository = MockSettingsRepository()
        let locationRepository = MockLocationRepository()
        let bufferStore = MockTrackingBufferStore()
        
        let viewModel = TrackingViewModel(
            locationRepository: locationRepository,
            settingsRepository: settingsRepository,
            locationService: locationService,
            bufferStore: bufferStore
        )
        
        // Add sample data for preview
        viewModel.isTracking = true
        viewModel.totalDistance = 1582.5
        viewModel.sessionDuration = 1245.0 // About 20 minutes
        viewModel.currentSpeed = 3.2
        viewModel.averageSpeed = 2.6
        viewModel.maxSpeed = 5.8
        viewModel.locationCount = 78
        viewModel.uploadedCount = 75
        viewModel.bufferCount = 3
        viewModel.uploadStatus = .success(Date().addingTimeInterval(-30))
        viewModel.lastSuccessfulUploadAt = Date().addingTimeInterval(-30)
        viewModel.isUploadRuntimeActive = true
        viewModel.trackerIdentifier = "9876543210"
        viewModel.uploadServer = "device.waliot.com:30032"
        viewModel.locationPresentation = TrackingLocationPresentation(
            state: .freshGps,
            trustedLocation: LocationSnapshot(
                latitude: 45.040764,
                longitude: 39.031908,
                altitude: 25.0,
                horizontalAccuracy: 8.0,
                verticalAccuracy: 12.0,
                speed: 3.2,
                course: 75.0,
                timestamp: Date(),
                provider: .gps,
                isSimulated: false
            ),
            issue: nil,
            provider: .gps,
            accuracyMeters: 8,
            fixAge: 0.5
        )
        
        // Sample current location
        viewModel.currentLocation = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 45.040764, longitude: 39.031908),
            altitude: 25.0,
            horizontalAccuracy: 8.0,
            verticalAccuracy: 12.0,
            course: 75.0,
            speed: 3.2,
            timestamp: Date()
        )
        
        // Generate simulated path
        var pathPoints: [LocationData] = []
        var speedData: [SpeedDataPoint] = []
        
        let startTime = Date().addingTimeInterval(-1245.0)
        for i in 0..<40 {
            let timeOffset = Double(i) * 30.0
            let timestamp = startTime.addingTimeInterval(timeOffset)
            
            // Create a sinusoidal path for visual interest
            let latitude = 45.040764 + Double(i) * 0.0005 + sin(Double(i) * 0.2) * 0.001
            let longitude = 39.031908 + Double(i) * 0.0005 + cos(Double(i) * 0.2) * 0.001
            let speed = 2.0 + sin(Double(i) * 0.4) * 2.0
            
            let locationData = LocationData(
                coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
                altitude: 10.0 + Double(i % 10),
                horizontalAccuracy: 8.0,
                verticalAccuracy: 12.0,
                speed: speed,
                course: Double((i * 10) % 360),
                timestamp: timestamp
            )
            
            pathPoints.append(locationData)
            speedData.append(SpeedDataPoint(speed: speed, timestamp: timestamp))
        }
        
        viewModel.pathPoints = pathPoints
        viewModel.speedData = speedData
        
        return viewModel
    }
}
