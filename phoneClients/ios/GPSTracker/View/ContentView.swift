// /Users/nickfox137/Documents/gpstracker-clients/gpstracker-ios/GPSTracker/GPSTracker/View/ContentView.swift

import SwiftUI
import MapKit
import CoreLocation

struct ContentView: View {
    private static let processInfo = ProcessInfo.processInfo
    private static let isUITestMode =
        processInfo.arguments.contains("UITEST_MODE") ||
        processInfo.environment["UITEST_MODE"] == "1"
    private static let onboardingDefaultsStore: UserDefaults? = {
        if isUITestMode {
            return UserDefaults(suiteName: "com.waliot.tracker.uitests")
        }
        return .standard
    }()

    @Environment(\.colorScheme) private var colorScheme
    @EnvironmentObject private var viewModel: TrackingViewModel

    @AppStorage(
        "com.waliot.tracker.didCompleteLocationPermissionOnboarding",
        store: ContentView.onboardingDefaultsStore
    )
    private var didCompleteLocationPermissionOnboarding = false

    @State private var showingSettings = false
    @State private var showingStats = false
    @State private var hasCenteredOnLiveLocation = false
    @State private var lastFollowHeading: CLLocationDirection = 0
    @State private var lastFollowLocation: CLLocation?
    @State private var mapViewportHeight: CGFloat = 0
    @State private var topOverlayHeight: CGFloat = 0
    @State private var mapCameraPosition: MapCameraPosition = .userLocation(
        followsHeading: false,
        fallback: .region(
            MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 55.751244, longitude: 37.618423),
                span: HomeMapCameraModel.defaultSpan
            )
        )
    )

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                mapView
                    .ignoresSafeArea()

                VStack(spacing: 12) {
                    topOverlay
                    Spacer()
                    controls
                }
                .padding()

                if shouldShowPermissionOnboarding {
                    permissionOnboardingOverlay
                        .transition(.opacity.combined(with: .scale(scale: 0.98)))
                }
            }
            .onAppear {
                mapViewportHeight = geometry.size.height
                syncPermissionOnboardingState()
            }
            .onChange(of: geometry.size.height) { _, newValue in
                mapViewportHeight = newValue
                if let currentLocation = viewModel.currentLocation {
                    centerMap(on: currentLocation, animated: false)
                }
            }
            .onPreferenceChange(TopOverlayHeightPreferenceKey.self) { newValue in
                topOverlayHeight = newValue
                if let currentLocation = viewModel.currentLocation {
                    centerMap(on: currentLocation, animated: false)
                }
            }
            .onChange(of: viewModel.locationAuthorizationStatus) { _, _ in
                syncPermissionOnboardingState()
            }
            .sheet(isPresented: $showingSettings) {
                SettingsView()
                    .environmentObject(viewModel)
            }
            .sheet(isPresented: $showingStats) {
                StatsView()
                    .environmentObject(viewModel)
            }
            .onChange(of: viewModel.isTracking) { _, isTracking in
                if !isTracking {
                    lastFollowHeading = 0
                    lastFollowLocation = nil
                }
                if let currentLocation = viewModel.currentLocation {
                    centerMap(on: currentLocation, animated: true)
                }
            }
            .onAppear {
                viewModel.onLocationUpdate = { location in
                    DispatchQueue.main.async {
                        let shouldAnimate = hasCenteredOnLiveLocation
                        centerMap(on: location, animated: shouldAnimate)
                        hasCenteredOnLiveLocation = true
                    }
                }
            }
        }
    }

    private var mapView: some View {
        Map(position: $mapCameraPosition, interactionModes: .all) {
            UserAnnotation()

            if mapPathCoordinates.count > 1 {
                MapPolyline(coordinates: mapPathCoordinates)
                    .stroke(
                        Color.accentColor.opacity(0.38),
                        style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round)
                    )
            }
        }
        .mapStyle(.standard(elevation: .realistic))
        .mapControlVisibility(.hidden)
        .accessibilityIdentifier("home.map")
    }

    private var topOverlay: some View {
        VStack(spacing: 12) {
            topBar
            objectCard
        }
        .background(
            GeometryReader { proxy in
                Color.clear
                    .preference(key: TopOverlayHeightPreferenceKey.self, value: proxy.size.height)
            }
        )
    }

    private var topBar: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                Text("app.name")
                    .font(.headline)
                    .foregroundStyle(primaryTextColor)

                Text(viewModel.isTracking ? "status.tracking" : "status.idle")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(viewModel.isTracking ? .green : primaryTextColor.opacity(0.88))
                    .accessibilityIdentifier("home.tracking.status")

                Text(uploadSummaryText)
                    .font(.footnote)
                    .foregroundStyle(uploadStatusColor)
                    .accessibilityIdentifier("home.upload.status")

                Text(lastUploadSummaryText)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(secondaryTextColor)
                    .lineLimit(1)
                    .accessibilityIdentifier("home.last.upload")
            }

            Spacer()

            Button {
                showingStats = true
            } label: {
                secondaryControlIcon(systemImage: "chart.bar.fill", size: 44)
            }
            .accessibilityIdentifier("home.open.stats")
        }
        .padding(16)
        .background(floatingCardBackground(cornerRadius: 22))
    }

    private var objectCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            LabeledValueRow(
                title: String(localized: "location.card.state"),
                value: displayedLocationStateText,
                valueColor: displayedLocationStateColor,
                accessibilityIdentifier: "home.location.state"
            )
            LabeledValueRow(
                title: String(localized: "location.card.provider"),
                value: displayedLocationProviderText,
                accessibilityIdentifier: "home.location.provider"
            )
            LabeledValueRow(
                title: String(localized: "location.card.age"),
                value: displayedLocationAgeText,
                accessibilityIdentifier: "home.location.age"
            )
            LabeledValueRow(
                title: String(localized: "location.card.accuracy"),
                value: displayedAccuracyText,
                accessibilityIdentifier: "home.location.accuracy"
            )
        }
        .padding(16)
        .background(floatingCardBackground(cornerRadius: 24))
    }

    private var permissionOnboardingOverlay: some View {
        ZStack {
            Color.black.opacity(colorScheme == .dark ? 0.42 : 0.18)
                .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 16) {
                Text("permissions.onboarding.title")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(primaryTextColor)

                Text("permissions.onboarding.message")
                    .font(.subheadline)
                    .foregroundStyle(secondaryTextColor)
                    .fixedSize(horizontal: false, vertical: true)

                Button {
                    handleOnboardingPermissionAction()
                } label: {
                    Text(viewModel.trackInBackground ? "permissions.onboarding.allowAlways" : "permissions.onboarding.allowWhenInUse")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("permissions.onboarding.allow")
            }
            .padding(22)
            .background(floatingCardBackground(cornerRadius: 28))
            .padding(.horizontal, 24)
            .accessibilityIdentifier("permissions.onboarding.card")
        }
    }

    private var controls: some View {
        HStack(spacing: 14) {
            Button {
                showingSettings = true
            } label: {
                secondaryControlIcon(systemImage: "gearshape.fill", size: 50)
            }
            .accessibilityLabel(Text("action.settings"))
            .accessibilityIdentifier("home.open.settings")

            Spacer(minLength: 0)

            Button {
                if viewModel.isTracking {
                    viewModel.stopTracking()
                } else {
                    viewModel.startTracking()
                }
            } label: {
                primaryControlIcon(
                    systemImage: viewModel.isTracking ? "stop.fill" : "play.fill",
                    background: viewModel.isTracking ? .red : .green,
                    size: 58
                )
            }
            .accessibilityLabel(Text(viewModel.isTracking ? "action.stopTracking" : "action.startTracking"))
            .accessibilityIdentifier("home.toggle.tracking")
        }
    }

    private var mapPathCoordinates: [CLLocationCoordinate2D] {
        HomeMapCameraModel.visibleTrailCoordinates(
            from: viewModel.pathPoints,
            isTracking: viewModel.isTracking
        )
    }

    private var shouldShowPermissionOnboarding: Bool {
        if ContentView.isUITestMode {
            return false
        }
        return !didCompleteLocationPermissionOnboarding && viewModel.locationAuthorizationStatus == .notDetermined
    }

    private var locationStateColor: Color {
        switch viewModel.locationPresentation.state {
        case .freshGps:
            return .green
        case .freshDegraded:
            return .yellow
        case .staleGps, .staleDegraded:
            return .orange
        case .suspect:
            return .red
        case .noFix:
            return secondaryTextColor
        }
    }

    private var displayedLocationStateText: String {
        permissionStateOverride?.title ?? viewModel.locationPresentation.state.localizedDescription
    }

    private var displayedLocationProviderText: String {
        permissionStateOverride?.provider ??
            viewModel.locationPresentation.provider?.localizedDescription ??
            String(localized: "location.provider.unknown")
    }

    private var displayedLocationAgeText: String {
        permissionStateOverride == nil
            ? viewModel.locationPresentation.ageDescription
            : String(localized: "location.age.none")
    }

    private var displayedAccuracyText: String {
        permissionStateOverride == nil ? accuracyText : String(localized: "location.age.none")
    }

    private var displayedLocationStateColor: Color {
        permissionStateOverride?.color ?? locationStateColor
    }

    private var permissionStateOverride: PermissionStateOverride? {
        switch viewModel.locationAuthorizationStatus {
        case .notDetermined:
            return PermissionStateOverride(
                title: String(localized: "location.permission.pending.state"),
                provider: String(localized: "location.permission.pending.provider"),
                color: secondaryTextColor
            )
        case .denied, .restricted:
            return PermissionStateOverride(
                title: String(localized: "location.permission.denied.state"),
                provider: String(localized: "location.permission.denied.provider"),
                color: .red
            )
        case .authorizedAlways, .authorizedWhenInUse:
            return nil
        @unknown default:
            return PermissionStateOverride(
                title: String(localized: "location.permission.pending.state"),
                provider: String(localized: "location.permission.pending.provider"),
                color: secondaryTextColor
            )
        }
    }

    private var uploadStatusColor: Color {
        switch viewModel.uploadStatus {
        case .idle:
            return secondaryTextColor
        case .uploading:
            return .yellow
        case .success:
            return secondaryTextColor
        case .offline:
            return .orange
        case .failure:
            return .red
        }
    }

    private var primaryTextColor: Color {
        Color(uiColor: .label)
    }

    private var secondaryTextColor: Color {
        Color(uiColor: .secondaryLabel)
    }

    private var cardStrokeColor: Color {
        colorScheme == .dark ? Color.white.opacity(0.14) : Color.white.opacity(0.48)
    }

    private var cardShadowColor: Color {
        Color.black.opacity(colorScheme == .dark ? 0.22 : 0.12)
    }

    private var accuracyText: String {
        guard let accuracy = viewModel.locationPresentation.accuracyMeters else {
            return String(localized: "location.age.none")
        }
        return String.localizedStringWithFormat(String(localized: "units.meters.precise"), Int(round(accuracy)))
    }

    private var lastUploadText: String {
        guard let lastSuccessfulUploadAt = viewModel.lastSuccessfulUploadAt else {
            return String(localized: "upload.card.never")
        }
        return lastSuccessfulUploadAt.formatted(date: .omitted, time: .shortened)
    }

    private var uploadSummaryText: String {
        switch viewModel.uploadStatus {
        case .uploading(let backlog):
            return String.localizedStringWithFormat(
                String(localized: "upload.status.uploading"),
                backlog
            )
        case .offline, .failure:
            return viewModel.uploadStatus.description
        case .idle, .success:
            if viewModel.bufferCount == 0 {
                return String(localized: "upload.status.idle")
            }
            return String.localizedStringWithFormat(
                String(localized: "upload.status.buffered"),
                viewModel.bufferCount
            )
        }
    }

    private var lastUploadSummaryText: String {
        "\(String(localized: "upload.card.lastUpload")): \(lastUploadText)"
    }

    private func syncPermissionOnboardingState() {
        if viewModel.locationAuthorizationStatus != .notDetermined {
            didCompleteLocationPermissionOnboarding = true
        }
    }

    private func handleOnboardingPermissionAction() {
        viewModel.requestLocationPermissions(always: viewModel.trackInBackground)
    }

    private func centerMap(on location: CLLocation, animated: Bool) {
        let update = {
            if viewModel.isTracking {
                mapCameraPosition = .camera(
                    MapCamera(
                        centerCoordinate: followCameraTarget(for: location),
                        distance: HomeMapCameraModel.followCameraDistance,
                        heading: headingForFollowCamera(using: location),
                        pitch: HomeMapCameraModel.followCameraPitch
                    )
                )
            } else {
                mapCameraPosition = .camera(
                    MapCamera(
                        centerCoordinate: adjustedMapCenter(for: location.coordinate),
                        distance: HomeMapCameraModel.idleCameraDistance,
                        heading: 0,
                        pitch: 0
                    )
                )
            }
        }
        if animated {
            withAnimation(.easeInOut(duration: 0.25), update)
        } else {
            update()
        }
    }

    private func followCameraTarget(for location: CLLocation) -> CLLocationCoordinate2D {
        HomeMapCameraModel.followCameraCenterCoordinate(
            for: location.coordinate,
            headingDegrees: headingForFollowCamera(using: location),
            defaultSpan: HomeMapCameraModel.defaultSpan,
            topOverlayHeight: topOverlayHeight,
            mapViewportHeight: mapViewportHeight,
            followCameraDistance: HomeMapCameraModel.followCameraDistance
        )
    }

    private func headingForFollowCamera(using location: CLLocation) -> CLLocationDirection {
        if let candidateHeading = HomeMapCameraModel.headingCandidate(
            for: location,
            previousLocation: lastFollowLocation
        ) {
            if lastFollowHeading == 0 {
                lastFollowHeading = candidateHeading
            } else {
                let delta = HomeMapCameraModel.shortestHeadingDelta(from: lastFollowHeading, to: candidateHeading)
                let clampedDelta = max(min(delta, HomeMapCameraModel.headingSmoothingStepDegrees), -HomeMapCameraModel.headingSmoothingStepDegrees)
                lastFollowHeading = HomeMapCameraModel.normalizedHeading(lastFollowHeading + clampedDelta)
            }
        }
        lastFollowLocation = location
        return lastFollowHeading
    }

    private func adjustedMapCenter(for coordinate: CLLocationCoordinate2D) -> CLLocationCoordinate2D {
        HomeMapCameraModel.overlayAwareCenterCoordinate(
            for: coordinate,
            defaultSpan: HomeMapCameraModel.defaultSpan,
            topOverlayHeight: topOverlayHeight,
            mapViewportHeight: mapViewportHeight
        )
    }

    private func secondaryControlIcon(systemImage: String, size: CGFloat) -> some View {
        Image(systemName: systemImage)
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(primaryTextColor)
            .frame(width: size, height: size)
            .background(floatingControlBackground(size: size))
    }

    private func primaryControlIcon(systemImage: String, background: Color, size: CGFloat) -> some View {
        Image(systemName: systemImage)
            .font(.system(size: 20, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(
                Circle()
                    .fill(background.gradient)
                    .overlay(
                        Circle()
                            .strokeBorder(Color.white.opacity(0.18), lineWidth: 1)
                    )
                    .shadow(color: background.opacity(0.28), radius: 16, y: 10)
            )
    }

    private func floatingControlBackground(size: CGFloat) -> some View {
        Circle()
            .fill(.regularMaterial)
            .overlay(
                Circle()
                    .strokeBorder(cardStrokeColor, lineWidth: 1)
            )
            .shadow(color: cardShadowColor, radius: 14, y: 8)
    }

    private func floatingCardBackground(cornerRadius: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .fill(.regularMaterial)
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(cardStrokeColor, lineWidth: 1)
            )
            .shadow(color: cardShadowColor, radius: 18, y: 10)
    }
}

private struct PermissionStateOverride {
    let title: String
    let provider: String
    let color: Color
}

private struct LabeledValueRow: View {
    let title: String
    let value: String
    var valueColor: Color = Color(uiColor: .label)
    var accessibilityIdentifier: String? = nil

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(title)
                .font(.footnote)
                .foregroundStyle(Color(uiColor: .secondaryLabel))
            Spacer()
            Text(value)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(valueColor)
                .multilineTextAlignment(.trailing)
                .accessibilityIdentifier(accessibilityIdentifier ?? "")
        }
    }
}

private struct MapPointAnnotation: Identifiable {
    let id: String
    let coordinate: CLLocationCoordinate2D
}

private struct TopOverlayHeightPreferenceKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
            .environmentObject(MockDependencies.previewViewModel)
            .environmentObject(LanguageManager())
            .preferredColorScheme(.dark)
    }
}
