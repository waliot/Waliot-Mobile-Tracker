import Foundation
import CoreLocation
import Testing
@testable import GPSTracker

@MainActor
struct TrackingViewModelTests {
    @Test
    func buffersTrustedPointsByDistanceAndAllowsEqualTimestamps() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        let timestamp = Date()
        viewModel.startTracking()
        locationService.send(location: makeLocation(latitude: 55.751244, longitude: 37.618423, timestamp: timestamp))
        await flushMainQueue()

        #expect(viewModel.bufferCount == 1)
        #expect(viewModel.locationCount == 1)

        locationService.send(location: makeLocation(latitude: 55.752744, longitude: 37.618423, timestamp: timestamp))
        await flushMainQueue()

        #expect(viewModel.bufferCount == 2)
        #expect(viewModel.locationCount == 2)
        #expect(viewModel.locationPresentation.state == .freshGps)
        #expect(viewModel.totalDistance > 0)
    }

    @Test
    func rejectsImpossibleJumpAndKeepsTrustedPoint() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        let start = Date()
        viewModel.startTracking()
        locationService.send(location: makeLocation(latitude: 55.751244, longitude: 37.618423, timestamp: start))
        await flushMainQueue()

        locationService.send(location: makeLocation(latitude: 55.951244, longitude: 37.618423, timestamp: start.addingTimeInterval(60)))
        await flushMainQueue()

        #expect(viewModel.bufferCount == 1)
        #expect(viewModel.locationPresentation.state == .suspect)
        #expect(viewModel.locationPresentation.issue == .impossibleJump)
    }

    @Test
    func suspectStateSurvivesUiRefreshTick() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        let start = Date()
        viewModel.startTracking()
        locationService.send(location: makeLocation(latitude: 55.751244, longitude: 37.618423, timestamp: start))
        await flushMainQueue()

        locationService.send(location: makeLocation(latitude: 55.951244, longitude: 37.618423, timestamp: start.addingTimeInterval(60)))
        await flushMainQueue()

        #expect(viewModel.locationPresentation.state == .suspect)
        viewModel.performUiRefreshTickForTests()

        #expect(viewModel.locationPresentation.state == .suspect)
        #expect(viewModel.locationPresentation.issue == .impossibleJump)
    }

    @Test
    func simulatedFixIsAcceptedForTrackingRuntime() {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        let status = viewModel.evaluateLocationSnapshotForTests(
            LocationSnapshot(
                latitude: 55.751244,
                longitude: 37.618423,
                altitude: 120,
                horizontalAccuracy: 30,
                verticalAccuracy: 8,
                speed: 10,
                course: 45,
                timestamp: Date(),
                provider: .simulated,
                isSimulated: true
            )
        )

        #expect(status.quality == .trustedGps)
        #expect(status.issue == nil)
        #expect(status.provider == .simulated)
    }

    @Test
    func locationPresentationUpdatesWithoutTrackingWhenAuthorized() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        settings.trackingState = false
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        locationService.send(
            location: makeLocation(
                latitude: 55.751244,
                longitude: 37.618423,
                accuracy: 12,
                timestamp: Date()
            )
        )
        await flushMainQueue()

        #expect(viewModel.isTracking == false)
        #expect(viewModel.locationPresentation.state == .freshGps)
        #expect(viewModel.locationPresentation.accuracyMeters == 12)
        #expect(viewModel.bufferCount == 0)
        #expect(viewModel.locationCount == 0)
        #expect(viewModel.currentLocation != nil)
        #expect(locationService.startObservingNavigationStatusCallCount >= 1)
        #expect(locationService.startUpdatingLocationCallCount == 0)
    }

    @Test
    func mapCallbackReceivesForegroundLocationUpdatesWithoutTracking() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        settings.trackingState = false
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        var callbackLocation: CLLocation?
        viewModel.onLocationUpdate = { location in
            callbackLocation = location
        }

        let expectedLocation = makeLocation(
            latitude: 55.751244,
            longitude: 37.618423,
            accuracy: 12,
            timestamp: Date()
        )

        locationService.send(location: expectedLocation)
        await flushMainQueue()

        #expect(viewModel.isTracking == false)
        #expect(callbackLocation?.coordinate.latitude == expectedLocation.coordinate.latitude)
        #expect(callbackLocation?.coordinate.longitude == expectedLocation.coordinate.longitude)
    }

    @Test
    func stoppingTrackingStopsNewBufferingButAllowsBufferedUploadDrain() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        let start = Date()
        viewModel.startTracking()
        locationService.send(location: makeLocation(latitude: 55.751244, longitude: 37.618423, timestamp: start))
        await flushMainQueue()
        #expect(viewModel.bufferCount == 1)

        viewModel.stopTracking()
        locationService.send(location: makeLocation(latitude: 55.753244, longitude: 37.618423, timestamp: start.addingTimeInterval(120)))
        await flushMainQueue()
        #expect(viewModel.bufferCount == 1)

        viewModel.performUploadCycleForTests()
        await flushMainQueue()

        #expect(viewModel.isTracking == false)
        #expect(repository.uploadedParameters.count == 1)
        #expect(viewModel.bufferCount == 0)
    }

    @Test
    func stoppingTrackingKeepsNavigationCardLiveWithoutBuffering() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        let start = Date()
        viewModel.startTracking()
        locationService.send(location: makeLocation(latitude: 55.751244, longitude: 37.618423, timestamp: start))
        await flushMainQueue()

        viewModel.stopTracking()
        locationService.send(
            location: makeLocation(
                latitude: 55.752244,
                longitude: 37.618923,
                accuracy: 14,
                timestamp: start.addingTimeInterval(30)
            )
        )
        await flushMainQueue()

        #expect(viewModel.isTracking == false)
        #expect(viewModel.bufferCount == 1)
        #expect(viewModel.locationCount == 1)
        #expect(viewModel.locationPresentation.state == .freshGps)
        #expect(viewModel.locationPresentation.accuracyMeters == 14)
        #expect(viewModel.currentLocation?.timestamp == start.addingTimeInterval(30))
        #expect(locationService.startObservingNavigationStatusCallCount >= 1)
    }

    @Test
    func offlineUploadSetsFriendlyOfflineState() async {
        let repository = TestLocationRepository()
        repository.nextError = URLError(.notConnectedToInternet)
        let settings = TestSettingsRepository()
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        let start = Date()
        viewModel.startTracking()
        locationService.send(location: makeLocation(latitude: 55.751244, longitude: 37.618423, timestamp: start))
        await flushMainQueue()

        viewModel.performUploadCycleForTests()
        await flushMainQueue()

        if case .offline = viewModel.uploadStatus {
        } else {
            Issue.record("Expected offline upload status, got \(viewModel.uploadStatus)")
        }
    }

    @Test
    func reducedAccuracyFixIsTrustedAsDegradedAndStillBuffered() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        let locationService = TestLocationService()
        locationService.accuracyAuthorization = .reducedAccuracy
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        viewModel.startTracking()
        locationService.send(
            location: makeLocation(
                latitude: 55.751244,
                longitude: 37.618423,
                accuracy: 65,
                timestamp: Date()
            )
        )
        await flushMainQueue()

        #expect(viewModel.bufferCount == 1)
        #expect(viewModel.locationCount == 1)
        #expect(viewModel.locationPresentation.state == .freshDegraded)
        #expect(viewModel.locationPresentation.issue == .lowAccuracy)
    }

    @Test
    func staleInitialFixIsIgnoredUntilFreshTrustedLocationArrives() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        viewModel.startTracking()
        locationService.send(
            location: makeLocation(
                latitude: 55.751244,
                longitude: 37.618423,
                timestamp: Date().addingTimeInterval(-(staleFixTimeout + 10))
            )
        )
        await flushMainQueue()

        #expect(viewModel.bufferCount == 0)
        #expect(viewModel.locationCount == 0)
        #expect(viewModel.locationPresentation.state == .noFix)
        #expect(viewModel.locationPresentation.issue == .staleFix)

        locationService.send(
            location: makeLocation(
                latitude: 55.751544,
                longitude: 37.618823,
                timestamp: Date()
            )
        )
        await flushMainQueue()

        #expect(viewModel.bufferCount == 1)
        #expect(viewModel.locationPresentation.state == .freshGps)
    }

    @Test
    func timestampRegressionIsRejectedButDoesNotDiscardTrustedFix() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        let trustedTime = Date()
        viewModel.startTracking()
        locationService.send(
            location: makeLocation(
                latitude: 55.751244,
                longitude: 37.618423,
                timestamp: trustedTime
            )
        )
        await flushMainQueue()

        locationService.send(
            location: makeLocation(
                latitude: 55.751544,
                longitude: 37.618523,
                timestamp: trustedTime.addingTimeInterval(-5)
            )
        )
        await flushMainQueue()

        #expect(viewModel.bufferCount == 1)
        #expect(viewModel.locationPresentation.state == .suspect)
        #expect(viewModel.locationPresentation.issue == .timestampRegression)
        #expect(viewModel.currentLocation?.timestamp == trustedTime)
    }

    @Test
    func failedUploadAfterStopIsRetriedWithoutRestartingTracking() async {
        let repository = TestLocationRepository()
        repository.enqueueUploadResults(
            .failure(URLError(.notConnectedToInternet)),
            .success(APIResponse(status: "success", message: nil))
        )
        let settings = TestSettingsRepository()
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        let start = Date()
        viewModel.startTracking()
        locationService.send(location: makeLocation(latitude: 55.751244, longitude: 37.618423, timestamp: start))
        await flushMainQueue()
        #expect(viewModel.bufferCount == 1)

        viewModel.stopTracking()
        viewModel.performUploadCycleForTests()
        await flushMainQueue()

        #expect(viewModel.isTracking == false)
        #expect(repository.attemptedParameters.count == 1)
        #expect(repository.uploadedParameters.isEmpty)
        #expect(viewModel.bufferCount == 1)
        if case .offline = viewModel.uploadStatus {
        } else {
            Issue.record("Expected offline upload status after failed drain, got \(viewModel.uploadStatus)")
        }

        viewModel.performUploadCycleForTests()
        await flushMainQueue()

        #expect(viewModel.isTracking == false)
        #expect(repository.attemptedParameters.count == 2)
        #expect(repository.uploadedParameters.count == 1)
        #expect(viewModel.bufferCount == 0)
    }

    @Test
    func failedUploadSurvivesRelaunchAndRetriesWithoutResumingTracking() async {
        let firstRepository = TestLocationRepository()
        firstRepository.enqueueUploadResults(.failure(URLError(.notConnectedToInternet)))
        let settings = TestSettingsRepository()
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let firstViewModel = TrackingViewModel(
            locationRepository: firstRepository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        let start = Date()
        firstViewModel.startTracking()
        locationService.send(location: makeLocation(latitude: 55.751244, longitude: 37.618423, timestamp: start))
        await flushMainQueue()
        firstViewModel.stopTracking()
        firstViewModel.performUploadCycleForTests()
        await flushMainQueue()

        #expect(firstViewModel.isTracking == false)
        #expect(firstViewModel.bufferCount == 1)
        #expect(firstRepository.attemptedParameters.count == 1)

        let secondRepository = TestLocationRepository()
        secondRepository.enqueueUploadResults(.success(APIResponse(status: "success", message: nil)))
        let relaunchedViewModel = TrackingViewModel(
            locationRepository: secondRepository,
            settingsRepository: settings,
            locationService: TestLocationService(),
            bufferStore: bufferStore
        )

        await flushMainQueue()
        await flushMainQueue()

        #expect(relaunchedViewModel.isTracking == false)
        #expect(secondRepository.attemptedParameters.count == 1)
        #expect(secondRepository.uploadedParameters.count == 1)
        #expect(relaunchedViewModel.bufferCount == 0)
    }

    @Test
    func uploadUsesLatestTrackerIdentifierAtCycleTime() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        settings.trackerIdentifier = "OLD123"
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        let start = Date()
        viewModel.startTracking()
        locationService.send(location: makeLocation(latitude: 55.751244, longitude: 37.618423, timestamp: start))
        await flushMainQueue()

        viewModel.trackerIdentifier = "NEW456"
        viewModel.performUploadCycleForTests()
        await flushMainQueue()

        #expect(repository.uploadedParameters.count == 1)
        #expect(repository.uploadedParameters[0].username == "NEW456")
    }

    @Test
    func restoresPersistedBacklogAndDrainsItWithoutResumingTracking() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        settings.trackingState = false
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let snapshot = makeSnapshot(
            latitude: 55.751244,
            longitude: 37.618423,
            timestamp: Date().addingTimeInterval(-60)
        )
        let record = makeBufferedRecord(snapshot: snapshot)
        bufferStore.saveState(
            TrackingBufferState(
                bufferedLocations: [record],
                lastBufferedLocation: snapshot
            )
        )

        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        await flushMainQueue()
        await flushMainQueue()

        #expect(viewModel.isTracking == false)
        #expect(repository.attemptedParameters.count == 1)
        #expect(repository.uploadedParameters.count == 1)
        #expect(viewModel.bufferCount == 0)
    }

    @Test
    func bufferingTimeThresholdIsIndependentFromUploadInterval() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        settings.uploadTimeInterval = 60
        settings.bufferTimeInterval = 5
        settings.bufferDistanceInterval = 1_000
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        let firstTimestamp = Date()
        viewModel.startTracking()
        locationService.send(
            location: makeLocation(
                latitude: 55.751244,
                longitude: 37.618423,
                speed: 0,
                timestamp: firstTimestamp
            )
        )
        await flushMainQueue()

        locationService.send(
            location: makeLocation(
                latitude: 55.751244,
                longitude: 37.618423,
                speed: 0,
                timestamp: firstTimestamp.addingTimeInterval(6 * 60)
            )
        )
        await flushMainQueue()

        #expect(viewModel.bufferCount == 2)
        #expect(viewModel.locationCount == 2)
    }

    @Test
    func restoresPersistedTrackingRuntimeWhenAuthorized() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        settings.trackingState = true
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        await flushMainQueue()

        #expect(viewModel.isTracking)
        #expect(locationService.startUpdatingLocationCallCount == 1)
    }

    @Test
    func startTrackingRequestsAlwaysPermissionsWhenBackgroundCollectionIsEnabled() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        settings.trackInBackground = true
        let locationService = TestLocationService()
        locationService.authorizationStatus = .notDetermined
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: InMemoryTrackingBufferStore()
        )

        viewModel.startTracking()
        await flushMainQueue()

        #expect(viewModel.isTracking)
        #expect(locationService.requestAlwaysPermissionsCallCount == 1)
        #expect(locationService.requestWhenInUsePermissionsCallCount == 0)
    }

    @Test
    func startTrackingRequestsWhenInUsePermissionsWhenBackgroundCollectionIsDisabled() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        settings.trackInBackground = false
        let locationService = TestLocationService()
        locationService.authorizationStatus = .notDetermined
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: InMemoryTrackingBufferStore()
        )

        viewModel.startTracking()
        await flushMainQueue()

        #expect(viewModel.isTracking)
        #expect(locationService.requestWhenInUsePermissionsCallCount == 1)
        #expect(locationService.requestAlwaysPermissionsCallCount == 0)
    }

    @Test
    func losingAuthorizationStopsTrackingRuntimeImmediately() async {
        let repository = TestLocationRepository()
        let settings = TestSettingsRepository()
        let locationService = TestLocationService()
        let bufferStore = InMemoryTrackingBufferStore()
        let viewModel = TrackingViewModel(
            locationRepository: repository,
            settingsRepository: settings,
            locationService: locationService,
            bufferStore: bufferStore
        )

        viewModel.startTracking()
        locationService.sendAuthorization(status: .denied)
        await flushMainQueue()

        #expect(viewModel.isTracking == false)
        #expect(locationService.stopUpdatingLocationCallCount == 1)
        #expect(viewModel.isUploadRuntimeActive == false)
    }
}

private func makeSnapshot(
    latitude: Double,
    longitude: Double,
    accuracy: Double = 5,
    timestamp: Date
) -> LocationSnapshot {
    LocationSnapshot(
        latitude: latitude,
        longitude: longitude,
        altitude: 120,
        horizontalAccuracy: accuracy,
        verticalAccuracy: 7,
        speed: 10,
        course: 90,
        timestamp: timestamp,
        provider: .gps,
        isSimulated: false
    )
}

private func makeBufferedRecord(snapshot: LocationSnapshot) -> BufferedLocationRecord {
    BufferedLocationRecord(
        trackerIdentifier: "9876543210",
        sessionId: "session-1",
        appId: "test-app-id",
        snapshot: snapshot,
        distance: 120,
        battery: 75
    )
}
