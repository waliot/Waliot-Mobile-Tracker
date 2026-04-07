import Foundation
import CoreLocation
import MapKit
import Testing
@testable import GPSTracker

struct TrackingRuntimeModelsTests {
    @Test
    func noTrustedFixPresentationUsesObservedFixAgeAndFriendlyFallbacks() {
        let presentation = presentLocationFixStatus(
            fixStatus: LocationFixStatus(
                quality: .noFix,
                trustedLocation: nil,
                trustedFixDegraded: false,
                issue: .staleFix,
                provider: .approximate,
                accuracyMeters: 120,
                observedAt: Date()
            ),
            now: Date()
        )

        #expect(presentation.state == .noFix)
        #expect(presentation.issue == .staleFix)
        #expect(presentation.provider == .approximate)
        #expect((presentation.fixAge ?? -1) >= 0)
        #expect(presentation.ageDescription == String(localized: "location.age.justNow"))
    }

    @Test
    func staleTrustedPresentationsDifferentiateGpsAndDegradedFixes() {
        let staleDate = Date().addingTimeInterval(-(staleFixTimeout + 30))
        let gpsSnapshot = makeRuntimeSnapshot(provider: .gps, accuracy: 8, timestamp: staleDate)
        let degradedSnapshot = makeRuntimeSnapshot(provider: .approximate, accuracy: 70, timestamp: staleDate)

        let staleGps = presentLocationFixStatus(
            fixStatus: LocationFixStatus(
                quality: .trustedGps,
                trustedLocation: gpsSnapshot,
                trustedFixDegraded: false,
                issue: nil,
                provider: .gps,
                accuracyMeters: 8,
                observedAt: staleDate
            ),
            now: Date()
        )
        let staleDegraded = presentLocationFixStatus(
            fixStatus: LocationFixStatus(
                quality: .trustedDegraded,
                trustedLocation: degradedSnapshot,
                trustedFixDegraded: true,
                issue: .lowAccuracy,
                provider: .approximate,
                accuracyMeters: 70,
                observedAt: staleDate
            ),
            now: Date()
        )

        #expect(staleGps.state == .staleGps)
        #expect(staleDegraded.state == .staleDegraded)
        #expect(staleDegraded.issue == .lowAccuracy)
        #expect(staleGps.fixAge ?? 0 > staleFixTimeout)
        #expect(staleDegraded.fixAge ?? 0 > staleFixTimeout)
    }

    @Test
    func suspectPresentationUsesObservedFixAgeWhileSurfacingProblem() {
        let trustedDate = Date().addingTimeInterval(-42)
        let trustedSnapshot = makeRuntimeSnapshot(provider: .gps, accuracy: 8, timestamp: trustedDate)
        let suspectPresentation = presentLocationFixStatus(
            fixStatus: LocationFixStatus(
                quality: .suspect,
                trustedLocation: trustedSnapshot,
                trustedFixDegraded: false,
                issue: .timestampRegression,
                provider: .simulated,
                accuracyMeters: 3,
                observedAt: Date()
            ),
            now: Date()
        )

        #expect(suspectPresentation.state == .suspect)
        #expect(suspectPresentation.issue == .timestampRegression)
        #expect(suspectPresentation.provider == .simulated)
        #expect((suspectPresentation.fixAge ?? 99) < 1)
    }

    @Test
    func ageDescriptionUsesJustNowForFreshFix() {
        let presentation = TrackingLocationPresentation(
            state: .freshGps,
            trustedLocation: nil,
            issue: nil,
            provider: .gps,
            accuracyMeters: 5,
            fixAge: 0.4
        )

        #expect(presentation.ageDescription == String(localized: "location.age.justNow"))
    }

    @Test
    func localizedDescriptionsCoverUserFacingStatesAndProviders() {
        let providerDescriptions = [
            LocationProviderKind.gps.localizedDescription,
            LocationProviderKind.approximate.localizedDescription,
            LocationProviderKind.simulated.localizedDescription,
            LocationProviderKind.accessory.localizedDescription,
            LocationProviderKind.unknown.localizedDescription
        ]
        let stateDescriptions = [
            TrackingLocationUiState.noFix.localizedDescription,
            TrackingLocationUiState.freshGps.localizedDescription,
            TrackingLocationUiState.freshDegraded.localizedDescription,
            TrackingLocationUiState.staleGps.localizedDescription,
            TrackingLocationUiState.staleDegraded.localizedDescription,
            TrackingLocationUiState.suspect.localizedDescription
        ]
        let issueDescriptions = [
            LocationFixIssue.staleFix.localizedDescription,
            LocationFixIssue.simulatedFix.localizedDescription,
            LocationFixIssue.impossibleJump.localizedDescription,
            LocationFixIssue.timestampRegression.localizedDescription,
            LocationFixIssue.lowAccuracy.localizedDescription,
            LocationFixIssue.noTrustedFix.localizedDescription
        ]

        #expect(providerDescriptions.allSatisfy { !$0.isEmpty })
        #expect(stateDescriptions.allSatisfy { !$0.isEmpty })
        #expect(issueDescriptions.allSatisfy { !$0.isEmpty })
    }

    @Test
    func uploadStatusesUseFriendlyLocalizedDescriptions() {
        let timestamp = Date(timeIntervalSince1970: 1_775_433_600)

        #expect(UploadStatus.idle.description == String(localized: "upload.status.idle"))
        #expect(UploadStatus.offline(nil).description == String(localized: "upload.status.offline"))
        #expect(UploadStatus.failure(.invalidConfiguration, nil).description == String(localized: "upload.failure.invalidConfiguration"))
        #expect(UploadStatus.failure(.offline, nil).isSameKindAsCurrent)
        #expect(UploadStatus.failure(.transport, nil).isSameKindAsCurrent)
        #expect(UploadStatus.uploading(3).description.contains("3"))
        #expect(UploadStatus.success(timestamp).description.contains(timestamp.formatted(date: .numeric, time: .shortened)))
    }

    @Test
    func followLookAheadDistanceStaysBoundedForOverlaySizes() {
        let noOverlay = HomeMapCameraModel.followLookAheadDistance(
            topOverlayHeight: 0,
            mapViewportHeight: 800,
            followCameraDistance: 1_600
        )
        let largeOverlay = HomeMapCameraModel.followLookAheadDistance(
            topOverlayHeight: 420,
            mapViewportHeight: 800,
            followCameraDistance: 1_600
        )

        #expect(noOverlay >= 260)
        #expect(noOverlay < 320)
        #expect(largeOverlay <= 440)
        #expect(largeOverlay > noOverlay)
    }

    @Test
    func overlayAwareCenterCoordinateTargetsMarkerHigherAboveBottomEdge() {
        let coordinate = CLLocationCoordinate2D(latitude: 55.751244, longitude: 37.618423)

        let shifted = HomeMapCameraModel.overlayAwareCenterCoordinate(
            for: coordinate,
            defaultSpan: MKCoordinateSpan(latitudeDelta: 0.008, longitudeDelta: 0.008),
            topOverlayHeight: 220,
            mapViewportHeight: 852
        )

        #expect(abs(shifted.latitude - (coordinate.latitude + 0.008 * 0.20)) < 0.000_001)
        #expect(shifted.longitude == coordinate.longitude)
    }

    @Test
    func overlayAwareCenterCoordinateStillUsesTargetOffsetBeforeLayoutIsMeasured() {
        let coordinate = CLLocationCoordinate2D(latitude: 55.751244, longitude: 37.618423)

        let shifted = HomeMapCameraModel.overlayAwareCenterCoordinate(
            for: coordinate,
            defaultSpan: MKCoordinateSpan(latitudeDelta: 0.008, longitudeDelta: 0.008),
            topOverlayHeight: 0,
            mapViewportHeight: 0
        )

        #expect(abs(shifted.latitude - (coordinate.latitude + 0.008 * 0.20)) < 0.000_001)
        #expect(shifted.longitude == coordinate.longitude)
    }

    @Test
    func followCameraCenterCoordinateFallsBackToOverlayAwarePositionWithoutHeading() {
        let coordinate = CLLocationCoordinate2D(latitude: 55.751244, longitude: 37.618423)

        let shifted = HomeMapCameraModel.followCameraCenterCoordinate(
            for: coordinate,
            headingDegrees: 0,
            defaultSpan: MKCoordinateSpan(latitudeDelta: 0.008, longitudeDelta: 0.008),
            topOverlayHeight: 220,
            mapViewportHeight: 852,
            followCameraDistance: 1_600
        )

        #expect(abs(shifted.latitude - (coordinate.latitude + 0.008 * 0.20)) < 0.000_001)
        #expect(shifted.longitude == coordinate.longitude)
    }

    @Test
    func followCameraCenterCoordinateProjectsForwardWhenHeadingIsKnown() {
        let coordinate = CLLocationCoordinate2D(latitude: 55.751244, longitude: 37.618423)

        let shifted = HomeMapCameraModel.followCameraCenterCoordinate(
            for: coordinate,
            headingDegrees: 0,
            defaultSpan: MKCoordinateSpan(latitudeDelta: 0.008, longitudeDelta: 0.008),
            topOverlayHeight: 220,
            mapViewportHeight: 852,
            followCameraDistance: 1_600
        )
        let projected = HomeMapCameraModel.followCameraCenterCoordinate(
            for: coordinate,
            headingDegrees: 90,
            defaultSpan: MKCoordinateSpan(latitudeDelta: 0.008, longitudeDelta: 0.008),
            topOverlayHeight: 220,
            mapViewportHeight: 852,
            followCameraDistance: 1_600
        )

        #expect(projected.longitude > shifted.longitude)
        #expect(abs(projected.latitude - coordinate.latitude) < 0.001)
    }

    @Test
    func headingCandidatePrefersReliableCourseWhenDeviceIsMoving() {
        let location = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 55.751244, longitude: 37.618423),
            altitude: 120,
            horizontalAccuracy: 10,
            verticalAccuracy: 6,
            course: 95,
            speed: 12,
            timestamp: Date()
        )

        let heading = HomeMapCameraModel.headingCandidate(
            for: location,
            previousLocation: nil
        )

        #expect(heading == 95)
    }

    @Test
    func headingCandidateFallsBackToBearingWhenCourseIsUnavailable() {
        let previous = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 55.751244, longitude: 37.618423),
            altitude: 120,
            horizontalAccuracy: 10,
            verticalAccuracy: 6,
            course: -1,
            speed: 0,
            timestamp: Date()
        )
        let current = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 55.751244, longitude: 37.628423),
            altitude: 120,
            horizontalAccuracy: 10,
            verticalAccuracy: 6,
            course: -1,
            speed: 0,
            timestamp: Date()
        )

        let heading = HomeMapCameraModel.headingCandidate(
            for: current,
            previousLocation: previous
        )

        #expect(heading != nil)
        #expect(abs((heading ?? 0) - 90) < 1)
    }

    @Test
    func shortestHeadingDeltaUsesShortestTurnAcrossZeroDegrees() {
        let delta = HomeMapCameraModel.shortestHeadingDelta(from: 350, to: 10)

        #expect(delta == 20)
    }

    @Test
    func normalizedHeadingWrapsNegativeAnglesIntoCompassRange() {
        let normalized = HomeMapCameraModel.normalizedHeading(-15)

        #expect(normalized == 345)
    }

    @Test
    func overlayAwareCenterCoordinateRespectsTallOverlayWhenItNeedsMoreSpaceThanDefaultTarget() {
        let coordinate = CLLocationCoordinate2D(latitude: 55.751244, longitude: 37.618423)

        let shifted = HomeMapCameraModel.overlayAwareCenterCoordinate(
            for: coordinate,
            defaultSpan: MKCoordinateSpan(latitudeDelta: 0.008, longitudeDelta: 0.008),
            topOverlayHeight: 360,
            mapViewportHeight: 800
        )

        #expect(shifted.latitude > coordinate.latitude + (0.008 * 0.20))
        #expect(shifted.longitude == coordinate.longitude)
    }

    @Test
    func visibleTrailCoordinatesAreEmptyWithoutTracking() {
        let points = [
            makeLocationData(latitude: 55.751244, longitude: 37.618423, timestamp: Date()),
            makeLocationData(latitude: 55.752244, longitude: 37.618423, timestamp: Date())
        ]

        let coordinates = HomeMapCameraModel.visibleTrailCoordinates(
            from: points,
            isTracking: false
        )

        #expect(coordinates.isEmpty)
    }

    @Test
    func visibleTrailCoordinatesKeepOnlyRecentShortTail() {
        let start = Date()
        let points = stride(from: 0, through: 20, by: 1).map { index in
            makeLocationData(
                latitude: 55.751244 + (Double(index) * 0.0007),
                longitude: 37.618423,
                timestamp: start.addingTimeInterval(Double(index))
            )
        }

        let coordinates = HomeMapCameraModel.visibleTrailCoordinates(
            from: points,
            isTracking: true,
            maxDistance: 1_000,
            maxPoints: 120
        )

        #expect(coordinates.count < points.count)
        #expect(coordinates.last?.latitude == points.last?.coordinate.latitude)
        #expect(coordinates.last?.longitude == points.last?.coordinate.longitude)
    }
}

private func makeRuntimeSnapshot(
    provider: LocationProviderKind,
    accuracy: Double,
    timestamp: Date
) -> LocationSnapshot {
    LocationSnapshot(
        latitude: 55.751244,
        longitude: 37.618423,
        altitude: 120,
        horizontalAccuracy: accuracy,
        verticalAccuracy: 6,
        speed: 10,
        course: 45,
        timestamp: timestamp,
        provider: provider,
        isSimulated: provider == .simulated
    )
}

private func makeLocationData(latitude: Double, longitude: Double, timestamp: Date) -> LocationData {
    LocationData(
        coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
        altitude: 120,
        horizontalAccuracy: 10,
        verticalAccuracy: 6,
        speed: 10,
        course: 45,
        timestamp: timestamp
    )
}
