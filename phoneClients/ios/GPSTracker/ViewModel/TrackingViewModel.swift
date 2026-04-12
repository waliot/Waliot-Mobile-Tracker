// /Users/nickfox137/Documents/gpstracker-clients/gpstracker-ios/GPSTracker/GPSTracker/ViewModel/TrackingViewModel.swift

import Foundation
import Combine
import CoreLocation
import UIKit
import os

/// A data point containing speed information with timestamp.
struct SpeedDataPoint: Identifiable {
    let id = UUID()
    let speed: Double
    let timestamp: Date
}

/// The primary coordinator for tracking runtime, buffering, upload scheduling and UI state.
final class TrackingViewModel: ObservableObject {
    // MARK: - Published Properties

    @Published var isTracking = false
    @Published var currentLocation: CLLocation?
    @Published var uploadStatus: UploadStatus = .idle
    @Published var locationAuthorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published var totalDistance: Double = 0
    @Published var sessionDuration: TimeInterval = 0
    @Published var currentSpeed: Double = 0
    @Published var averageSpeed: Double = 0
    @Published var maxSpeed: Double = 0
    @Published var locationCount: Int = 0
    @Published var uploadedCount: Int = 0
    @Published var bufferCount: Int = 0
    @Published var pathPoints: [LocationData] = []
    @Published var speedData: [SpeedDataPoint] = []
    @Published var trackerIdentifier: String = ""
    @Published var uploadServer: String = "device.waliot.com:30032"
    @Published var uploadTimeInterval: Int = 5
    @Published var bufferTimeInterval: Int = 1
    @Published var bufferDistanceInterval: Int = 100
    @Published var trackInBackground: Bool = true
    @Published var locationPresentation = TrackingLocationPresentation()
    @Published var lastSuccessfulUploadAt: Date?
    @Published var isUploadRuntimeActive = false

    /// Optional callback used by the map UI to react to current trusted/degraded location updates.
    var onLocationUpdate: ((CLLocation) -> Void)?

    // MARK: - Private Properties

    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "com.waliot.tracker", category: "TrackingViewModel")
    private let locationRepository: LocationRepositoryProtocol
    private let settingsRepository: SettingsRepositoryProtocol
    private let locationService: LocationServiceProtocol
    private let bufferStore: TrackingBufferStoreProtocol

    private var authStatusCancellable: AnyCancellable?
    private var locationUpdatesCancellable: AnyCancellable?
    private var uploadCancellable: AnyCancellable?
    private var lifecycleCancellables = Set<AnyCancellable>()
    private var uploadTimer: Timer?
    private var uiRefreshTimer: Timer?
    private var sessionStartTime: Date?
    private var currentSessionId = ""
    private var appId = ""
    private var trackingRequested = false
    private var pendingStartAfterAuthorization = false
    private var bufferedState = TrackingBufferState()
    private var lastTrustedLocation: LocationSnapshot?
    private var lastTrustedFixDegraded = false
    private var lastObservedFixStatus: LocationFixStatus?
    private var backgroundTaskIdentifier: UIBackgroundTaskIdentifier = .invalid
    private var isAppActive = true
    private var didEnterBackgroundWhileTracking = false
    private var pendingForegroundRecoveryFix = false

    // MARK: - Initialization

    init(
        locationRepository: LocationRepositoryProtocol,
        settingsRepository: SettingsRepositoryProtocol,
        locationService: LocationServiceProtocol,
        bufferStore: TrackingBufferStoreProtocol = TrackingBufferStore()
    ) {
        self.locationRepository = locationRepository
        self.settingsRepository = settingsRepository
        self.locationService = locationService
        self.bufferStore = bufferStore

        loadSettings()
        restoreBufferedState()
        bindAuthorizationStatus()
        observeLifecycle()
        startUiRefreshTimer()
        subscribeToLocationUpdatesIfNeeded()
        restoreTrackingIfNeeded()
        syncNavigationStatusMonitoring()
        if !isTracking {
            restartUploadLoopIfNeeded(forceImmediate: bufferCount > 0)
        }

        log("TrackingViewModel initialized", logger: logger)
    }

    deinit {
        uploadTimer?.invalidate()
        uiRefreshTimer?.invalidate()
        locationService.stopObservingNavigationStatus()
        endBackgroundUploadTask()
    }

    // MARK: - Public Methods

    func startTracking() {
        guard !isTracking else { return }

        let status = locationService.getCurrentAuthorizationStatus()
        guard status == .authorizedAlways || status == .authorizedWhenInUse else {
            pendingStartAfterAuthorization = true
            requestLocationPermissions(always: trackInBackground)
            return
        }

        pendingStartAfterAuthorization = false
        trackingRequested = true
        settingsRepository.saveTrackingState(true)
        activateTrackingRuntime(
            shouldResetSessionStatistics: true,
            resetBufferedAnchor: true,
            logMessage: "Location tracking started"
        )
    }

    func stopTracking() {
        guard isTracking else { return }

        locationService.stopUpdatingLocation()
        trackingRequested = false
        pendingStartAfterAuthorization = false
        settingsRepository.saveTrackingState(false)
        isTracking = false
        sessionStartTime = nil
        currentSpeed = 0
        didEnterBackgroundWhileTracking = false
        pendingForegroundRecoveryFix = false
        syncNavigationStatusMonitoring()

        restartUploadLoopIfNeeded(forceImmediate: false)

        log("Location tracking stopped. Buffered points pending: \(bufferCount)", logger: logger)
    }

    func saveSettings() {
        settingsRepository.saveTrackerIdentifier(trackerIdentifier)
        settingsRepository.saveUploadServer(uploadServer)
        settingsRepository.saveUploadTimeInterval(uploadTimeInterval)
        settingsRepository.saveBufferTimeInterval(bufferTimeInterval)
        settingsRepository.saveBufferDistanceInterval(bufferDistanceInterval)
        settingsRepository.saveTrackInBackground(trackInBackground)
        applySettings()
    }

    func applySettings() {
        locationService.setBackgroundTrackingEnabled(trackInBackground)
        restartUploadLoopIfNeeded(forceImmediate: false)
        log("Applied tracking settings", logger: logger)
    }

    func requestLocationPermissions(always: Bool) {
        if always && locationAuthorizationStatus == .authorizedWhenInUse {
            locationService.requestAlwaysPermissions()
        } else if always {
            locationService.requestAlwaysPermissions()
        } else {
            locationService.requestWhenInUsePermissions()
        }
    }

    func performUploadCycleForTests() {
        performUploadCycle()
    }

    func performUiRefreshTickForTests() {
        handleUiRefreshTick()
    }

    func evaluateLocationSnapshotForTests(_ snapshot: LocationSnapshot) -> LocationFixStatus {
        evaluateLocation(snapshot)
    }

    // MARK: - Setup

    private func bindAuthorizationStatus() {
        authStatusCancellable = locationService.authorizationStatusPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                self?.locationAuthorizationStatus = status
                self?.handleAuthorizationChange(status)
            }

        locationAuthorizationStatus = locationService.getCurrentAuthorizationStatus()
    }

    private func observeLifecycle() {
        NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                if self?.isTracking == true && self?.didEnterBackgroundWhileTracking == true {
                    self?.pendingForegroundRecoveryFix = true
                }
                self?.isAppActive = true
                self?.syncNavigationStatusMonitoring()
                self?.endBackgroundUploadTask()
                self?.refreshLocationPresentation()
                self?.restartUploadLoopIfNeeded(forceImmediate: true)
            }
            .store(in: &lifecycleCancellables)

        NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self else { return }
                self.isAppActive = false
                self.didEnterBackgroundWhileTracking = self.isTracking
                self.syncNavigationStatusMonitoring()
                if !self.isTracking && self.bufferCount > 0 {
                    self.beginBackgroundUploadTaskIfNeeded()
                    self.performUploadCycle()
                }
            }
            .store(in: &lifecycleCancellables)
    }

    private func startUiRefreshTimer() {
        uiRefreshTimer?.invalidate()
        uiRefreshTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            self?.handleUiRefreshTick()
        }
    }

    private func handleUiRefreshTick() {
        if isTracking, let sessionStartTime {
            sessionDuration = max(0, Date().timeIntervalSince(sessionStartTime))
            if sessionDuration > 0 {
                averageSpeed = totalDistance / sessionDuration
            }
        }
        refreshLocationPresentation()
    }

    private func subscribeToLocationUpdatesIfNeeded() {
        guard locationUpdatesCancellable == nil else { return }
        locationUpdatesCancellable = locationService.locationPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] location in
                self?.handleLocationUpdate(location)
            }
    }

    // MARK: - Settings + Buffer Restore

    private func loadSettings() {
        trackerIdentifier = settingsRepository.getTrackerIdentifier()
        uploadServer = settingsRepository.getUploadServer()
        uploadTimeInterval = settingsRepository.getUploadTimeInterval()
        bufferTimeInterval = settingsRepository.getBufferTimeInterval()
        bufferDistanceInterval = settingsRepository.getBufferDistanceInterval()
        trackInBackground = settingsRepository.getTrackInBackground()
        trackingRequested = settingsRepository.getTrackingState()
        appId = settingsRepository.getAppId()
        locationService.setBackgroundTrackingEnabled(trackInBackground)
    }

    private func restoreBufferedState() {
        bufferedState = bufferStore.loadState()
        if bufferedState.bufferedLocations.count > maxBufferedLocations {
            bufferedState.bufferedLocations = Array(bufferedState.bufferedLocations.suffix(maxBufferedLocations))
            bufferStore.saveState(bufferedState)
        }
        bufferCount = bufferedState.bufferedLocations.count
        if let lastBufferedLocation = bufferedState.lastBufferedLocation {
            lastTrustedLocation = lastBufferedLocation
            lastTrustedFixDegraded = isDegraded(snapshot: lastBufferedLocation)
            currentLocation = lastBufferedLocation.asCLLocation
            refreshLocationPresentation(
                using: LocationFixStatus(
                    quality: lastTrustedFixDegraded ? .trustedDegraded : .trustedGps,
                    trustedLocation: lastBufferedLocation,
                    trustedFixDegraded: lastTrustedFixDegraded,
                    issue: nil,
                    provider: lastBufferedLocation.provider,
                    accuracyMeters: lastBufferedLocation.horizontalAccuracy,
                    observedAt: lastBufferedLocation.timestamp
                )
            )
        } else {
            refreshLocationPresentation()
        }
    }

    // MARK: - Tracking Runtime

    private func resetSessionStatistics() {
        totalDistance = 0
        sessionDuration = 0
        currentSpeed = 0
        averageSpeed = 0
        maxSpeed = 0
        locationCount = 0
        uploadedCount = 0
        pathPoints = []
        speedData = []
        lastTrustedLocation = nil
        lastTrustedFixDegraded = false
        lastObservedFixStatus = nil
        currentLocation = nil
        locationPresentation = TrackingLocationPresentation()
        didEnterBackgroundWhileTracking = false
        pendingForegroundRecoveryFix = false
    }

    private func restoreTrackingIfNeeded() {
        guard trackingRequested else { return }
        let status = locationService.getCurrentAuthorizationStatus()
        guard status == .authorizedAlways || status == .authorizedWhenInUse else {
            return
        }
        activateTrackingRuntime(
            shouldResetSessionStatistics: false,
            resetBufferedAnchor: false,
            logMessage: "Restored tracking runtime from persisted state"
        )
    }

    private func activateTrackingRuntime(
        shouldResetSessionStatistics: Bool,
        resetBufferedAnchor: Bool,
        logMessage: String
    ) {
        guard !isTracking else { return }

        if shouldResetSessionStatistics {
            currentSessionId = UUID().uuidString
            self.resetSessionStatistics()
        } else if currentSessionId.isEmpty {
            currentSessionId = UUID().uuidString
        }

        if resetBufferedAnchor {
            bufferedState.lastBufferedLocation = nil
            bufferStore.replaceLastBufferedLocation(nil)
        }

        didEnterBackgroundWhileTracking = false
        pendingForegroundRecoveryFix = false
        locationService.setBackgroundTrackingEnabled(trackInBackground)
        subscribeToLocationUpdatesIfNeeded()
        locationService.startUpdatingLocation()

        sessionStartTime = Date()
        isTracking = true
        restartUploadLoopIfNeeded(forceImmediate: bufferCount > 0)
        log("\(logMessage). Session ID: \(currentSessionId)", logger: logger)
    }

    private func handleLocationUpdate(_ location: CLLocation) {
        let snapshot = makeSnapshot(from: location)
        let fixStatus = evaluateLocation(snapshot)
        let previousTrustedLocation = lastTrustedLocation
        lastObservedFixStatus = fixStatus

        if fixStatus.quality == .trustedGps || fixStatus.quality == .trustedDegraded {
            updateNavigationState(
                snapshot,
                degraded: fixStatus.quality == .trustedDegraded
            )
            pendingForegroundRecoveryFix = false
            didEnterBackgroundWhileTracking = false
        }

        refreshLocationPresentation(using: fixStatus)

        guard isTracking else { return }

        guard fixStatus.quality == .trustedGps || fixStatus.quality == .trustedDegraded else {
            log("Ignoring untrusted location update with issue: \(String(describing: fixStatus.issue))", logger: logger)
            return
        }

        processTrustedLocation(snapshot, previousTrustedLocation: previousTrustedLocation)
        log("Processed trusted location update", logger: logger)
    }

    private func updateNavigationState(
        _ snapshot: LocationSnapshot,
        degraded: Bool
    ) {
        lastTrustedLocation = snapshot
        lastTrustedFixDegraded = degraded
        currentLocation = snapshot.asCLLocation
        onLocationUpdate?(snapshot.asCLLocation)
    }

    private func processTrustedLocation(
        _ snapshot: LocationSnapshot,
        previousTrustedLocation: LocationSnapshot?
    ) {
        currentSpeed = max(0, snapshot.speed)
        maxSpeed = max(maxSpeed, currentSpeed)
        pathPoints.append(snapshot.asLocationData)
        if pathPoints.count > maxBufferedLocations {
            pathPoints = Array(pathPoints.suffix(maxBufferedLocations))
        }
        speedData.append(SpeedDataPoint(speed: currentSpeed, timestamp: snapshot.timestamp))
        if speedData.count > maxBufferedLocations {
            speedData = Array(speedData.suffix(maxBufferedLocations))
        }

        if let previousTrustedLocation {
            totalDistance += snapshot.asCLLocation.distance(from: previousTrustedLocation.asCLLocation)
        }

        if shouldBuffer(snapshot: snapshot) {
            appendToBuffer(snapshot: snapshot)
        }
    }

    private func shouldBuffer(snapshot: LocationSnapshot) -> Bool {
        guard let lastBuffered = bufferedState.lastBufferedLocation else {
            return true
        }

        let elapsed = snapshot.timestamp.timeIntervalSince(lastBuffered.timestamp)
        if elapsed >= TimeInterval(max(bufferTimeInterval, 1) * 60) {
            return true
        }

        let distance = snapshot.asCLLocation.distance(from: lastBuffered.asCLLocation)
        return distance >= Double(max(bufferDistanceInterval, 1))
    }

    private func appendToBuffer(snapshot: LocationSnapshot) {
        let record = BufferedLocationRecord(
            trackerIdentifier: trackerIdentifier,
            sessionId: currentSessionId,
            appId: appId,
            snapshot: snapshot,
            distance: totalDistance,
            battery: currentBatteryLevel()
        )

        bufferedState.bufferedLocations.append(record)
        if bufferedState.bufferedLocations.count > maxBufferedLocations {
            bufferedState.bufferedLocations = Array(bufferedState.bufferedLocations.suffix(maxBufferedLocations))
        }
        bufferedState.lastBufferedLocation = snapshot
        bufferStore.appendBufferedLocation(
            record,
            maxSize: maxBufferedLocations,
            lastBufferedLocation: snapshot
        )

        bufferCount = bufferedState.bufferedLocations.count
        locationCount += 1
    }

    private func currentBatteryLevel() -> Int {
        let batteryLevel = UIDevice.current.batteryLevel
        guard batteryLevel >= 0 else { return 0 }
        return Int(round(batteryLevel * 100))
    }

    // MARK: - Fix Quality

    private func makeSnapshot(from location: CLLocation) -> LocationSnapshot {
        let sourceInformation = location.sourceInformation
        let isSimulated = sourceInformation?.isSimulatedBySoftware ?? false

        let provider: LocationProviderKind
        if isSimulated {
            provider = .simulated
        } else if sourceInformation?.isProducedByAccessory == true {
            provider = .accessory
        } else if locationService.getCurrentAccuracyAuthorization() == .reducedAccuracy {
            provider = .approximate
        } else if location.horizontalAccuracy > degradedHorizontalAccuracyThresholdMeters {
            provider = .approximate
        } else {
            provider = .gps
        }

        return LocationSnapshot(location: location, provider: provider, isSimulated: isSimulated)
    }

    private func evaluateLocation(_ snapshot: LocationSnapshot) -> LocationFixStatus {
        var status = LocationFixStatus(
            quality: .noFix,
            trustedLocation: lastTrustedLocation,
            trustedFixDegraded: lastTrustedFixDegraded,
            issue: nil,
            provider: snapshot.provider,
            accuracyMeters: snapshot.horizontalAccuracy,
            observedAt: snapshot.timestamp
        )

        let fixAge = max(0, Date().timeIntervalSince(snapshot.timestamp))
        if fixAge > staleFixTimeout {
            status.issue = .staleFix
            return status
        }

        let shouldBypassTrustedAnchorChecks =
            pendingForegroundRecoveryFix &&
            isTracking &&
            lastTrustedLocation != nil

        if !shouldBypassTrustedAnchorChecks {
            if let lastTrustedLocation, snapshot.timestamp < lastTrustedLocation.timestamp {
                status.quality = .suspect
                status.issue = .timestampRegression
                return status
            }

            if let lastTrustedLocation {
                let timeDelta = snapshot.timestamp.timeIntervalSince(lastTrustedLocation.timestamp)
                if timeDelta > 0 {
                    let distance = snapshot.asCLLocation.distance(from: lastTrustedLocation.asCLLocation)
                    let speedKmh = (distance / timeDelta) * 3.6
                    if speedKmh > suspectTravelSpeedThresholdKmh {
                        status.quality = .suspect
                        status.issue = .impossibleJump
                        return status
                    }
                }
            }
        }

        let degraded = isDegraded(snapshot: snapshot)
        status.quality = degraded ? .trustedDegraded : .trustedGps
        status.trustedLocation = snapshot
        status.trustedFixDegraded = degraded
        status.issue = degraded ? .lowAccuracy : nil
        return status
    }

    private func isDegraded(snapshot: LocationSnapshot) -> Bool {
        snapshot.provider == .approximate || snapshot.horizontalAccuracy > degradedHorizontalAccuracyThresholdMeters
    }

    private func refreshLocationPresentation(using fixStatus: LocationFixStatus? = nil) {
        let status = fixStatus ?? lastObservedFixStatus ?? LocationFixStatus(
            quality: lastTrustedFixDegraded ? .trustedDegraded : (lastTrustedLocation == nil ? .noFix : .trustedGps),
            trustedLocation: lastTrustedLocation,
            trustedFixDegraded: lastTrustedFixDegraded,
            issue: lastTrustedLocation == nil ? .noTrustedFix : nil,
            provider: lastTrustedLocation?.provider,
            accuracyMeters: lastTrustedLocation?.horizontalAccuracy,
            observedAt: lastTrustedLocation?.timestamp ?? .distantPast
        )
        locationPresentation = presentLocationFixStatus(fixStatus: status, now: Date())
    }

    private func handleAuthorizationChange(_ status: CLAuthorizationStatus) {
        let isAuthorized = status == .authorizedAlways || status == .authorizedWhenInUse
        if isAuthorized {
            if !isTracking && (trackingRequested || pendingStartAfterAuthorization) {
                pendingStartAfterAuthorization = false
                trackingRequested = true
                settingsRepository.saveTrackingState(true)
                activateTrackingRuntime(
                    shouldResetSessionStatistics: false,
                    resetBufferedAnchor: false,
                    logMessage: "Resumed tracking runtime after authorization update"
                )
            }
            syncNavigationStatusMonitoring()
            return
        }

        if status == .denied || status == .restricted {
            pendingStartAfterAuthorization = false
        }

        syncNavigationStatusMonitoring()

        guard isTracking else { return }
        locationService.stopUpdatingLocation()
        isTracking = false
        sessionStartTime = nil
        currentSpeed = 0
        didEnterBackgroundWhileTracking = false
        pendingForegroundRecoveryFix = false
        restartUploadLoopIfNeeded(forceImmediate: bufferCount > 0)
        log("Suspended tracking runtime because authorization no longer allows location updates", level: .error, logger: logger)
    }

    private func syncNavigationStatusMonitoring() {
        let status = locationService.getCurrentAuthorizationStatus()
        let isAuthorized = status == .authorizedAlways || status == .authorizedWhenInUse

        guard isAuthorized, !isTracking, isAppActive else {
            locationService.stopObservingNavigationStatus()
            return
        }

        locationService.startObservingNavigationStatus()
    }

    // MARK: - Upload Runtime

    private func restartUploadLoopIfNeeded(forceImmediate: Bool) {
        uploadTimer?.invalidate()
        uploadTimer = nil
        isUploadRuntimeActive = false

        guard isTracking || bufferCount > 0 else {
            if uploadCancellable == nil {
                setUploadStatus(.idle)
            }
            endBackgroundUploadTask()
            return
        }

        let interval = TimeInterval(max(uploadTimeInterval, 1) * 60)
        let timer = Timer(timeInterval: interval, repeats: true) { [weak self] _ in
            self?.performUploadCycle()
        }
        timer.tolerance = min(5, interval * 0.1)
        RunLoop.main.add(timer, forMode: .common)
        uploadTimer = timer
        isUploadRuntimeActive = true

        if forceImmediate {
            DispatchQueue.main.async { [weak self] in
                self?.performUploadCycle()
            }
        }
    }

    private func performUploadCycle() {
        guard uploadCancellable == nil else { return }

        guard !bufferedState.bufferedLocations.isEmpty else {
            if !isTracking {
                restartUploadLoopIfNeeded(forceImmediate: false)
            } else {
                setUploadStatus(.idle)
            }
            return
        }

        let tracker = sanitizeTrackerIdentifier(trackerIdentifier, defaultValue: "")
        guard !tracker.isEmpty, isUploadServerAddressValid(uploadServer) else {
            setUploadStatus(.failure(.invalidConfiguration, lastSuccessfulUploadAt))
            return
        }

        let record = bufferedState.bufferedLocations[0]
        setUploadStatus(.uploading(bufferedState.bufferedLocations.count))

        uploadCancellable = locationRepository.uploadLocation(
            parameters: record.apiParameters(overridingTrackerIdentifier: tracker)
        )
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { [weak self] completion in
                    guard let self else { return }
                    self.uploadCancellable = nil

                    if case .failure(let error) = completion {
                        self.handleUploadFailure(error)
                    }
                },
                receiveValue: { [weak self] _ in
                    self?.handleUploadSuccess(record)
                }
            )
    }

    private func handleUploadSuccess(_ record: BufferedLocationRecord) {
        if !bufferedState.bufferedLocations.isEmpty {
            bufferedState.bufferedLocations.removeFirst()
        }
        bufferStore.removeOldestBufferedLocation()
        bufferCount = bufferedState.bufferedLocations.count
        lastSuccessfulUploadAt = Date()
        if record.sessionId == currentSessionId {
            uploadedCount += 1
        }
        setUploadStatus(.success(lastSuccessfulUploadAt!))

        if bufferedState.bufferedLocations.isEmpty {
            if !isTracking {
                restartUploadLoopIfNeeded(forceImmediate: false)
            }
        } else {
            DispatchQueue.main.async { [weak self] in
                self?.performUploadCycle()
            }
        }
    }

    private func handleUploadFailure(_ error: Error) {
        let reason = mapUploadFailureReason(error)
        if reason == .offline {
            setUploadStatus(.offline(lastSuccessfulUploadAt))
        } else {
            setUploadStatus(.failure(reason, lastSuccessfulUploadAt))
        }
    }

    private func mapUploadFailureReason(_ error: Error) -> UploadFailureReason {
        if let uploadError = error as? WialonUploadError {
            return uploadError.failureReason
        }
        if let urlError = error as? URLError {
            switch urlError.code {
            case .notConnectedToInternet, .networkConnectionLost, .cannotConnectToHost, .cannotFindHost, .dnsLookupFailed:
                return .offline
            case .timedOut:
                return .timeout
            default:
                return .transport
            }
        }
        if let apiError = error as? APIError {
            switch apiError {
            case .invalidResponse:
                return .serverRejected
            case .serverError:
                return .serverRejected
            case .networkError(let urlError):
                switch urlError.code {
                case .notConnectedToInternet, .networkConnectionLost, .cannotConnectToHost, .cannotFindHost, .dnsLookupFailed:
                    return .offline
                case .timedOut:
                    return .timeout
                default:
                    return .transport
                }
            case .encodingError:
                return .invalidConfiguration
            }
        }
        return .transport
    }

    private func setUploadStatus(_ newStatus: UploadStatus) {
        switch (uploadStatus, newStatus) {
        case (.offline, .offline):
            return
        case (.failure(let oldReason, _), .failure(let newReason, _)) where oldReason == newReason:
            return
        default:
            uploadStatus = newStatus
        }
    }

    // MARK: - Background Drain

    private func beginBackgroundUploadTaskIfNeeded() {
        guard backgroundTaskIdentifier == .invalid else { return }
        backgroundTaskIdentifier = UIApplication.shared.beginBackgroundTask(withName: "DrainBufferedUploads") { [weak self] in
            self?.endBackgroundUploadTask()
        }
    }

    private func endBackgroundUploadTask() {
        guard backgroundTaskIdentifier != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTaskIdentifier)
        backgroundTaskIdentifier = .invalid
    }
}
