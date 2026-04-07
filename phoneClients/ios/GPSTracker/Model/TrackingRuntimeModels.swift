import Foundation
import CoreLocation
import MapKit

let staleFixTimeout: TimeInterval = 5 * 60
let suspectTravelSpeedThresholdKmh = 180.0
let degradedHorizontalAccuracyThresholdMeters = 50.0
let maxBufferedLocations = 1_000

enum LocationProviderKind: String, Codable, Equatable {
    case gps
    case approximate
    case simulated
    case accessory
    case unknown

    var localizedDescription: String {
        switch self {
        case .gps:
            return String(localized: "location.provider.gps")
        case .approximate:
            return String(localized: "location.provider.approximate")
        case .simulated:
            return String(localized: "location.provider.simulated")
        case .accessory:
            return String(localized: "location.provider.accessory")
        case .unknown:
            return String(localized: "location.provider.unknown")
        }
    }
}

enum LocationFixQuality: Equatable {
    case noFix
    case trustedGps
    case trustedDegraded
    case suspect
}

enum LocationFixIssue: Equatable {
    case staleFix
    case simulatedFix
    case impossibleJump
    case timestampRegression
    case lowAccuracy
    case noTrustedFix

    var localizedDescription: String {
        switch self {
        case .staleFix:
            return String(localized: "location.issue.stale")
        case .simulatedFix:
            return String(localized: "location.issue.simulated")
        case .impossibleJump:
            return String(localized: "location.issue.jump")
        case .timestampRegression:
            return String(localized: "location.issue.timestampRegression")
        case .lowAccuracy:
            return String(localized: "location.issue.lowAccuracy")
        case .noTrustedFix:
            return String(localized: "location.issue.noFix")
        }
    }
}

struct LocationSnapshot: Codable, Equatable, Identifiable {
    let id: String
    let latitude: Double
    let longitude: Double
    let altitude: Double
    let horizontalAccuracy: Double
    let verticalAccuracy: Double?
    let speed: Double
    let course: Double
    let timestamp: Date
    let provider: LocationProviderKind
    let isSimulated: Bool

    init(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        horizontalAccuracy: Double,
        verticalAccuracy: Double?,
        speed: Double,
        course: Double,
        timestamp: Date,
        provider: LocationProviderKind,
        isSimulated: Bool
    ) {
        self.latitude = latitude
        self.longitude = longitude
        self.altitude = altitude
        self.horizontalAccuracy = horizontalAccuracy
        self.verticalAccuracy = verticalAccuracy
        self.speed = max(0, speed)
        self.course = course
        self.timestamp = timestamp
        self.provider = provider
        self.isSimulated = isSimulated
        self.id = "\(latitude),\(longitude),\(timestamp.timeIntervalSince1970)"
    }

    init(location: CLLocation, provider: LocationProviderKind, isSimulated: Bool) {
        self.init(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            altitude: location.altitude,
            horizontalAccuracy: location.horizontalAccuracy,
            verticalAccuracy: location.verticalAccuracy >= 0 ? location.verticalAccuracy : nil,
            speed: max(0, location.speed),
            course: location.course >= 0 ? location.course : 0,
            timestamp: location.timestamp,
            provider: provider,
            isSimulated: isSimulated
        )
    }

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    var asLocationData: LocationData {
        LocationData(
            coordinate: coordinate,
            altitude: altitude,
            horizontalAccuracy: horizontalAccuracy,
            verticalAccuracy: verticalAccuracy,
            speed: speed,
            course: course,
            timestamp: timestamp
        )
    }

    var asCLLocation: CLLocation {
        CLLocation(
            coordinate: coordinate,
            altitude: altitude,
            horizontalAccuracy: horizontalAccuracy,
            verticalAccuracy: verticalAccuracy ?? -1,
            course: course,
            speed: speed,
            timestamp: timestamp
        )
    }
}

struct BufferedLocationRecord: Codable, Equatable, Identifiable {
    let id: UUID
    let latitude: Double
    let longitude: Double
    let speed: Double
    let direction: Double
    let distance: Double
    let gpsTime: String
    let gpsTimestamp: Date
    let locationMethod: String
    let accuracy: Double
    let altitude: Double
    let provider: String
    let battery: Int
    let trackerIdentifier: String
    let sessionId: String
    let appId: String
    let snapshot: LocationSnapshot

    init(
        trackerIdentifier: String,
        sessionId: String,
        appId: String,
        snapshot: LocationSnapshot,
        distance: Double,
        battery: Int
    ) {
        self.id = UUID()
        self.latitude = snapshot.latitude
        self.longitude = snapshot.longitude
        self.speed = snapshot.speed
        self.direction = snapshot.course
        self.distance = distance
        self.gpsTimestamp = snapshot.timestamp
        self.gpsTime = ISO8601DateFormatter().string(from: snapshot.timestamp)
        self.locationMethod = snapshot.provider == .gps ? "gps" : "approximate"
        self.accuracy = snapshot.horizontalAccuracy
        self.altitude = snapshot.altitude
        self.provider = snapshot.provider.rawValue
        self.battery = battery
        self.trackerIdentifier = trackerIdentifier
        self.sessionId = sessionId
        self.appId = appId
        self.snapshot = snapshot
    }

    func apiParameters(overridingTrackerIdentifier trackerIdentifier: String? = nil) -> LocationAPIRequestParameters {
        LocationAPIRequestParameters(
            username: trackerIdentifier ?? self.trackerIdentifier,
            sessionid: sessionId,
            appid: appId,
            latitude: latitude,
            longitude: longitude,
            speed: speed,
            direction: direction,
            distance: distance,
            gps_time: gpsTime,
            gps_timestamp: gpsTimestamp,
            location_method: locationMethod,
            accuracy: accuracy,
            altitude: altitude,
            provider: provider,
            battery: battery
        )
    }

    var apiParameters: LocationAPIRequestParameters {
        apiParameters(overridingTrackerIdentifier: nil)
    }
}

struct TrackingBufferState: Codable, Equatable {
    var bufferedLocations: [BufferedLocationRecord] = []
    var lastBufferedLocation: LocationSnapshot?
}

struct LocationFixStatus {
    var quality: LocationFixQuality = .noFix
    var trustedLocation: LocationSnapshot?
    var trustedFixDegraded: Bool = false
    var issue: LocationFixIssue?
    var provider: LocationProviderKind?
    var accuracyMeters: Double?
    var observedAt: Date = .distantPast
}

enum TrackingLocationUiState: Equatable {
    case noFix
    case freshGps
    case freshDegraded
    case staleGps
    case staleDegraded
    case suspect

    var localizedDescription: String {
        switch self {
        case .noFix:
            return String(localized: "location.state.noFix")
        case .freshGps:
            return String(localized: "location.state.freshGps")
        case .freshDegraded:
            return String(localized: "location.state.freshDegraded")
        case .staleGps:
            return String(localized: "location.state.staleGps")
        case .staleDegraded:
            return String(localized: "location.state.staleDegraded")
        case .suspect:
            return String(localized: "location.state.suspect")
        }
    }
}

struct TrackingLocationPresentation {
    var state: TrackingLocationUiState = .noFix
    var trustedLocation: LocationSnapshot?
    var issue: LocationFixIssue?
    var provider: LocationProviderKind?
    var accuracyMeters: Double?
    var fixAge: TimeInterval?

    var ageDescription: String {
        guard let fixAge else {
            return String(localized: "location.age.none")
        }
        if fixAge < 10 {
            return String(localized: "location.age.justNow")
        }
        let formatter = DateComponentsFormatter()
        formatter.unitsStyle = .full
        formatter.allowedUnits = fixAge >= 3600 ? [.hour, .minute] : [.minute, .second]
        formatter.maximumUnitCount = fixAge >= 3600 ? 2 : 1
        return formatter.string(from: fixAge) ?? String(localized: "location.age.justNow")
    }
}

enum UploadFailureReason: Equatable {
    case offline
    case invalidConfiguration
    case timeout
    case serverRejected
    case transport

    var localizedDescription: String {
        switch self {
        case .offline:
            return String(localized: "upload.failure.offline")
        case .invalidConfiguration:
            return String(localized: "upload.failure.invalidConfiguration")
        case .timeout:
            return String(localized: "upload.failure.timeout")
        case .serverRejected:
            return String(localized: "upload.failure.serverRejected")
        case .transport:
            return String(localized: "upload.failure.transport")
        }
    }
}

enum UploadStatus: Equatable {
    case idle
    case uploading(Int)
    case success(Date)
    case offline(Date?)
    case failure(UploadFailureReason, Date?)

    var description: String {
        switch self {
        case .idle:
            return String(localized: "upload.status.idle")
        case .uploading(let backlog):
            return String.localizedStringWithFormat(
                String(localized: "upload.status.uploading"),
                backlog
            )
        case .success(let date):
            let dateStr = date.formatted(date: .numeric, time: .shortened)
            return String.localizedStringWithFormat(
                String(localized: "upload.status.success"),
                dateStr
            )
        case .offline:
            return String(localized: "upload.status.offline")
        case .failure(let reason, _):
            return reason.localizedDescription
        }
    }

    var isSameKindAsCurrent: Bool {
        switch self {
        case .offline:
            return true
        case .failure:
            return true
        case .idle, .uploading, .success:
            return false
        }
    }
}

enum HomeMapCameraModel {
    static let defaultSpan = MKCoordinateSpan(latitudeDelta: 0.008, longitudeDelta: 0.008)
    static let followCameraDistance: CLLocationDistance = 1_600
    static let followCameraPitch: CGFloat = 45
    static let desiredMarkerPositionFromBottom: CGFloat = 0.30
    static let followBaseLookAheadDistance: CLLocationDistance = 260
    static let followMaxLookAheadDistance: CLLocationDistance = 440
    static let followLookAheadProjectionFactor = 0.70
    static let idleCameraDistance: CLLocationDistance = 1_300
    static let minimumFollowSpeedMetersPerSecond: CLLocationSpeed = 1.0
    static let minimumHeadingDistanceMeters: CLLocationDistance = 8
    static let headingSmoothingStepDegrees: CLLocationDirection = 18
    static let maxVisibleTrailDistance: CLLocationDistance = 1_000
    static let maxVisibleTrailPoints = 120

    static func headingCandidate(
        for currentLocation: CLLocation,
        previousLocation: CLLocation?
    ) -> CLLocationDirection? {
        let course = currentLocation.course
        if currentLocation.speed >= minimumFollowSpeedMetersPerSecond,
           course.isFinite,
           course >= 0,
           course <= 360 {
            return normalizedHeading(course)
        }

        guard let previousLocation else {
            return nil
        }

        let distance = previousLocation.distance(from: currentLocation)
        guard distance >= minimumHeadingDistanceMeters else {
            return nil
        }

        return bearing(from: previousLocation.coordinate, to: currentLocation.coordinate)
    }

    static func normalizedHeading(_ heading: CLLocationDirection) -> CLLocationDirection {
        let normalized = heading.truncatingRemainder(dividingBy: 360)
        return normalized >= 0 ? normalized : normalized + 360
    }

    static func shortestHeadingDelta(
        from current: CLLocationDirection,
        to target: CLLocationDirection
    ) -> CLLocationDirection {
        var delta = normalizedHeading(target) - normalizedHeading(current)
        if delta > 180 {
            delta -= 360
        } else if delta < -180 {
            delta += 360
        }
        return delta
    }

    static func bearing(
        from source: CLLocationCoordinate2D,
        to destination: CLLocationCoordinate2D
    ) -> CLLocationDirection {
        let sourceLatitude = source.latitude.radians
        let sourceLongitude = source.longitude.radians
        let destinationLatitude = destination.latitude.radians
        let destinationLongitude = destination.longitude.radians

        let longitudeDelta = destinationLongitude - sourceLongitude
        let y = sin(longitudeDelta) * cos(destinationLatitude)
        let x =
            cos(sourceLatitude) * sin(destinationLatitude) -
            sin(sourceLatitude) * cos(destinationLatitude) * cos(longitudeDelta)

        return normalizedHeading(atan2(y, x).degrees)
    }

    static func followCameraCenterCoordinate(
        for coordinate: CLLocationCoordinate2D,
        headingDegrees: CLLocationDirection,
        defaultSpan: MKCoordinateSpan,
        topOverlayHeight: CGFloat,
        mapViewportHeight: CGFloat,
        followCameraDistance: CLLocationDistance
    ) -> CLLocationCoordinate2D {
        guard headingDegrees != 0 else {
            return overlayAwareCenterCoordinate(
                for: coordinate,
                defaultSpan: defaultSpan,
                topOverlayHeight: topOverlayHeight,
                mapViewportHeight: mapViewportHeight
            )
        }

        let dynamicLookAheadDistance = followLookAheadDistance(
            topOverlayHeight: topOverlayHeight,
            mapViewportHeight: mapViewportHeight,
            followCameraDistance: followCameraDistance
        )

        return coordinateByMoving(
            from: coordinate,
            meters: dynamicLookAheadDistance,
            headingDegrees: headingDegrees
        )
    }

    static func overlayAwareCenterCoordinate(
        for coordinate: CLLocationCoordinate2D,
        defaultSpan: MKCoordinateSpan,
        topOverlayHeight: CGFloat,
        mapViewportHeight: CGFloat,
        desiredMarkerPositionFromBottom: CGFloat = desiredMarkerPositionFromBottom,
        maxShiftFraction: CGFloat = 0.40,
        overlaySafetyInsetFraction: CGFloat = 0.12
    ) -> CLLocationCoordinate2D {
        let desiredShiftFraction = max((1 - desiredMarkerPositionFromBottom) - 0.5, 0)
        let overlayDrivenShiftFraction: CGFloat
        if mapViewportHeight > 0, topOverlayHeight > 0 {
            overlayDrivenShiftFraction = max((topOverlayHeight / mapViewportHeight) - overlaySafetyInsetFraction, 0)
        } else {
            overlayDrivenShiftFraction = 0
        }

        let markerVerticalShiftFraction = min(
            max(desiredShiftFraction, overlayDrivenShiftFraction),
            maxShiftFraction
        )
        return CLLocationCoordinate2D(
            latitude: coordinate.latitude + (defaultSpan.latitudeDelta * markerVerticalShiftFraction),
            longitude: coordinate.longitude
        )
    }

    static func visibleTrailCoordinates(
        from points: [LocationData],
        isTracking: Bool,
        maxDistance: CLLocationDistance = maxVisibleTrailDistance,
        maxPoints: Int = maxVisibleTrailPoints
    ) -> [CLLocationCoordinate2D] {
        guard isTracking else {
            return []
        }

        let trailPoints = Array(points.suffix(maxPoints))
        guard trailPoints.count > 1 else {
            return trailPoints.map(\.coordinate)
        }

        var visibleReversed: [LocationData] = []
        var accumulatedDistance: CLLocationDistance = 0

        for point in trailPoints.reversed() {
            if let previousPoint = visibleReversed.last {
                accumulatedDistance += CLLocation(
                    latitude: point.coordinate.latitude,
                    longitude: point.coordinate.longitude
                )
                .distance(from: CLLocation(
                    latitude: previousPoint.coordinate.latitude,
                    longitude: previousPoint.coordinate.longitude
                ))
            }

            if !visibleReversed.isEmpty, accumulatedDistance > maxDistance {
                break
            }

            visibleReversed.append(point)
        }

        return visibleReversed.reversed().map(\.coordinate)
    }

    static func followLookAheadDistance(
        topOverlayHeight: CGFloat,
        mapViewportHeight: CGFloat,
        followCameraDistance: CLLocationDistance,
        desiredMarkerPositionFromBottom: CGFloat = desiredMarkerPositionFromBottom,
        minimumDistance: CLLocationDistance = followBaseLookAheadDistance,
        maxDistance: CLLocationDistance = followMaxLookAheadDistance,
        forwardProjectionFactor: Double = followLookAheadProjectionFactor,
        overlaySafetyInsetFraction: CGFloat = 0.12
    ) -> CLLocationDistance {
        let desiredShiftFraction = max((1 - desiredMarkerPositionFromBottom) - 0.5, 0)
        let overlayDrivenShiftFraction = max(
            min(max(topOverlayHeight / max(mapViewportHeight, 1), 0), 0.5) - overlaySafetyInsetFraction,
            0
        )
        let targetShiftFraction = max(desiredShiftFraction, overlayDrivenShiftFraction)

        return min(
            maxDistance,
            max(
                minimumDistance,
                followCameraDistance * Double(targetShiftFraction) * forwardProjectionFactor
            )
        )
    }

    static func coordinateByMoving(
        from coordinate: CLLocationCoordinate2D,
        meters: CLLocationDistance,
        headingDegrees: CLLocationDirection
    ) -> CLLocationCoordinate2D {
        guard meters > 0 else { return coordinate }

        let earthRadius = 6_378_137.0
        let distanceRadians = meters / earthRadius
        let bearing = headingDegrees.radians
        let latitude = coordinate.latitude.radians
        let longitude = coordinate.longitude.radians

        let shiftedLatitude = asin(
            sin(latitude) * cos(distanceRadians) +
            cos(latitude) * sin(distanceRadians) * cos(bearing)
        )

        let shiftedLongitude = longitude + atan2(
            sin(bearing) * sin(distanceRadians) * cos(latitude),
            cos(distanceRadians) - sin(latitude) * sin(shiftedLatitude)
        )

        return CLLocationCoordinate2D(
            latitude: shiftedLatitude.degrees,
            longitude: shiftedLongitude.degrees
        )
    }
}

private extension CLLocationDegrees {
    var radians: Double { self * .pi / 180 }
}

private extension Double {
    var degrees: Double { self * 180 / .pi }
}

func presentLocationFixStatus(
    fixStatus: LocationFixStatus,
    now: Date
) -> TrackingLocationPresentation {
    let observedAge: TimeInterval? = fixStatus.observedAt == .distantPast
        ? nil
        : max(0, now.timeIntervalSince(fixStatus.observedAt))

    if fixStatus.quality == .suspect {
        let trustedLocation = fixStatus.trustedLocation
        return TrackingLocationPresentation(
            state: .suspect,
            trustedLocation: trustedLocation,
            issue: fixStatus.issue,
            provider: fixStatus.provider ?? trustedLocation?.provider,
            accuracyMeters: fixStatus.accuracyMeters,
            fixAge: observedAge
        )
    }

    guard let trustedLocation = fixStatus.trustedLocation else {
        return TrackingLocationPresentation(
            state: .noFix,
            trustedLocation: nil,
            issue: fixStatus.issue ?? .noTrustedFix,
            provider: fixStatus.provider,
            accuracyMeters: fixStatus.accuracyMeters,
            fixAge: observedAge
        )
    }

    let age = max(0, now.timeIntervalSince(trustedLocation.timestamp))
    let isDegraded = switch fixStatus.quality {
    case .trustedDegraded:
        true
    case .trustedGps:
        false
    case .noFix:
        fixStatus.trustedFixDegraded
    case .suspect:
        false
    }

    let state: TrackingLocationUiState
    if age > staleFixTimeout {
        state = isDegraded ? .staleDegraded : .staleGps
    } else {
        state = isDegraded ? .freshDegraded : .freshGps
    }

    return TrackingLocationPresentation(
        state: state,
        trustedLocation: trustedLocation,
        issue: fixStatus.issue,
        provider: fixStatus.provider ?? trustedLocation.provider,
        accuracyMeters: fixStatus.accuracyMeters ?? trustedLocation.horizontalAccuracy,
        fixAge: age
    )
}
