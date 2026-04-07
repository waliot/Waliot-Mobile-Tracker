import Foundation
import Combine
import CoreLocation
@testable import GPSTracker

final class TestLocationRepository: LocationRepositoryProtocol {
    var uploadedParameters: [LocationAPIRequestParameters] = []
    var attemptedParameters: [LocationAPIRequestParameters] = []
    var nextError: Error?
    var queuedResults: [Result<APIResponse, Error>] = []

    func uploadLocation(parameters: LocationAPIRequestParameters) -> AnyPublisher<APIResponse, Error> {
        attemptedParameters.append(parameters)

        if !queuedResults.isEmpty {
            let result = queuedResults.removeFirst()
            switch result {
            case .success(let response):
                uploadedParameters.append(parameters)
                return Just(response)
                    .setFailureType(to: Error.self)
                    .eraseToAnyPublisher()
            case .failure(let error):
                return Fail(error: error).eraseToAnyPublisher()
            }
        }

        if let nextError {
            return Fail(error: nextError).eraseToAnyPublisher()
        }
        uploadedParameters.append(parameters)
        return Just(APIResponse(status: "success", message: nil))
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }

    func enqueueUploadResults(_ results: Result<APIResponse, Error>...) {
        queuedResults.append(contentsOf: results)
    }
}

final class TestAPIService: APIServiceProtocol {
    var uploadedParameters: [LocationAPIRequestParameters] = []
    var nextResult: Result<APIResponse, Error> = .success(APIResponse(status: "success", message: nil))

    func uploadLocation(parameters: LocationAPIRequestParameters) -> AnyPublisher<APIResponse, Error> {
        uploadedParameters.append(parameters)

        switch nextResult {
        case .success(let response):
            return Just(response)
                .setFailureType(to: Error.self)
                .eraseToAnyPublisher()
        case .failure(let error):
            return Fail(error: error).eraseToAnyPublisher()
        }
    }
}

final class TestSettingsRepository: SettingsRepositoryProtocol {
    var trackerIdentifier = "9876543210"
    var uploadServer = "device.waliot.com:30032"
    var uploadTimeInterval = 5
    var bufferTimeInterval = 1
    var bufferDistanceInterval = 100
    var trackInBackground = true
    var trackingState = false
    var appId = "test-app-id"

    func getTrackerIdentifier() -> String { trackerIdentifier }
    func saveTrackerIdentifier(_ trackerIdentifier: String) { self.trackerIdentifier = trackerIdentifier }
    func getUploadServer() -> String { uploadServer }
    func saveUploadServer(_ url: String) { uploadServer = url }
    func getUploadTimeInterval() -> Int { uploadTimeInterval }
    func saveUploadTimeInterval(_ interval: Int) { uploadTimeInterval = interval }
    func getBufferTimeInterval() -> Int { bufferTimeInterval }
    func saveBufferTimeInterval(_ interval: Int) { bufferTimeInterval = interval }
    func getBufferDistanceInterval() -> Int { bufferDistanceInterval }
    func saveBufferDistanceInterval(_ distance: Int) { bufferDistanceInterval = distance }
    func getTrackInBackground() -> Bool { trackInBackground }
    func saveTrackInBackground(_ enabled: Bool) { trackInBackground = enabled }
    func getTrackingState() -> Bool { trackingState }
    func saveTrackingState(_ isTracking: Bool) { trackingState = isTracking }
    func getAppId() -> String { appId }
    func saveAppId(_ appId: String) { self.appId = appId }
}

final class TestLocationService: LocationServiceProtocol {
    private let locationSubject = PassthroughSubject<CLLocation, Never>()
    private let authorizationSubject = PassthroughSubject<CLAuthorizationStatus, Never>()

    var locationPublisher: AnyPublisher<CLLocation, Never> {
        locationSubject.eraseToAnyPublisher()
    }

    var authorizationStatusPublisher: AnyPublisher<CLAuthorizationStatus, Never> {
        authorizationSubject.eraseToAnyPublisher()
    }

    var authorizationStatus: CLAuthorizationStatus = .authorizedAlways
    var accuracyAuthorization: CLAccuracyAuthorization = .fullAccuracy
    var startUpdatingLocationCallCount = 0
    var stopUpdatingLocationCallCount = 0
    var startObservingNavigationStatusCallCount = 0
    var stopObservingNavigationStatusCallCount = 0
    var requestWhenInUsePermissionsCallCount = 0
    var requestAlwaysPermissionsCallCount = 0
    var backgroundTrackingEnabledHistory: [Bool] = []

    func requestWhenInUsePermissions() {
        requestWhenInUsePermissionsCallCount += 1
        authorizationStatus = .authorizedWhenInUse
        authorizationSubject.send(authorizationStatus)
    }

    func requestAlwaysPermissions() {
        requestAlwaysPermissionsCallCount += 1
        authorizationStatus = .authorizedAlways
        authorizationSubject.send(authorizationStatus)
    }

    func setBackgroundTrackingEnabled(_ enabled: Bool) {
        backgroundTrackingEnabledHistory.append(enabled)
    }

    func startObservingNavigationStatus() {
        startObservingNavigationStatusCallCount += 1
    }

    func stopObservingNavigationStatus() {
        stopObservingNavigationStatusCallCount += 1
    }

    func startUpdatingLocation() {
        startUpdatingLocationCallCount += 1
    }

    func stopUpdatingLocation() {
        stopUpdatingLocationCallCount += 1
    }

    func getCurrentAuthorizationStatus() -> CLAuthorizationStatus {
        authorizationStatus
    }

    func getCurrentAccuracyAuthorization() -> CLAccuracyAuthorization {
        accuracyAuthorization
    }

    func send(location: CLLocation) {
        locationSubject.send(location)
    }

    func sendAuthorization(status: CLAuthorizationStatus) {
        authorizationStatus = status
        authorizationSubject.send(status)
    }
}

final class InMemoryTrackingBufferStore: TrackingBufferStoreProtocol {
    private(set) var state = TrackingBufferState()

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

func makeLocation(
    latitude: Double,
    longitude: Double,
    altitude: Double = 10,
    accuracy: Double = 8,
    speed: Double = 3,
    course: Double = 45,
    timestamp: Date
) -> CLLocation {
    CLLocation(
        coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
        altitude: altitude,
        horizontalAccuracy: accuracy,
        verticalAccuracy: accuracy,
        course: course,
        speed: speed,
        timestamp: timestamp
    )
}

@MainActor
func flushMainQueue() async {
    await Task.yield()
    try? await Task.sleep(nanoseconds: 50_000_000)
}
