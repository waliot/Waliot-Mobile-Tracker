// /Users/nickfox137/Documents/gpstracker-clients/gpstracker-ios/GPSTracker/GPSTracker/View/ContentView.swift

import SwiftUI
import MapKit
import CoreLocation

/// The main view of the GPS Tracker application
///
/// This view serves as the primary interface for the application, displaying
/// the tracking map, controls, and providing navigation to other views.
///
/// ## Overview
/// ContentView includes:
/// - A map display showing the current location and tracking path
/// - Tracking controls (start/stop)
/// - Status information display
/// - Navigation to settings and statistics views
///
/// ## Topics
/// ### Location Display
/// - ``mapRegion``
/// - ``userTrackingMode``
///
/// ### Tracking Controls
/// - ``startTracking()``
/// - ``stopTracking()``
struct ContentView: View {
    /// The view model that manages tracking logic and state
    @EnvironmentObject private var viewModel: TrackingViewModel
    
    /// Controls whether the settings sheet is displayed
    @State private var showingSettings = false
    
    /// Controls whether the statistics view is displayed
    @State private var showingStats = false
    
    /// The region displayed on the map
    @State private var mapRegion = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 45.040764, longitude: 39.031908),
        span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
    )
    
    /// The mode for user tracking on the map
    @State private var userTrackingMode: MapUserTrackingMode = .none
    
    /// The body of the view defining its content and layout
    var body: some View {
        ZStack {
            // Map view taking up the entire screen
            mapView
                .ignoresSafeArea()
            
            // Overlay content on top of the map
            VStack {
                // Top status bar
                statusBar
                    .padding()
                    .background(Color.secondary.opacity(0.9))
                    .cornerRadius(10)
                    .padding()
                
                Spacer()
                
                // Bottom control panel
                controlPanel
                    .padding()
                    .background(Color.secondary.opacity(0.9))
                    .cornerRadius(10)
                    .padding()
            }
        }
        .sheet(isPresented: $showingSettings) {
            SettingsView()
                .environmentObject(viewModel)
        }
        .sheet(isPresented: $showingStats) {
            StatsView()
                .environmentObject(viewModel)
        }
        .onAppear {
            // Request location permissions when the view appears
            viewModel.requestLocationPermissions(always: false)
            
            // Set up location updates to update the map
            viewModel.onLocationUpdate = { location in
                withAnimation {
                    self.mapRegion = MKCoordinateRegion(
                        center: location.coordinate,
                        span: MKCoordinateSpan(latitudeDelta: 0.005, longitudeDelta: 0.005)
                    )
                }
            }
        }
    }
    
    /// The map view displaying the user's current location and path
    private var mapView: some View {
        Map(coordinateRegion: $mapRegion, 
            showsUserLocation: true, 
            userTrackingMode: $userTrackingMode,
            annotationItems: viewModel.pathPoints) { point in
            MapAnnotation(coordinate: point.coordinate) {
                Circle()
                    .fill(Color.accent)
                    .frame(width: 6, height: 6)
            }
        }
    }
    
    /// The status bar showing tracking information
    private var statusBar: some View {
        HStack {
            VStack(alignment: .leading) {
                Text("app.name")
                    .font(.headline)
                    .foregroundColor(Color.accent)
                
                if let location = viewModel.currentLocation {
                    let lat = location.coordinate.latitude.formatted(.number.precision(.fractionLength(6)))
                    let lon = location.coordinate.longitude.formatted(.number.precision(.fractionLength(6)))
                    Text("\(String(localized: "map.location")) \(lat); \(lon)")
                        .font(.caption)
                        .foregroundColor(.white)
                }

                Text("\(String(localized: "status.title")) \(viewModel.isTracking ? String(localized: "status.tracking") : String(localized: "status.idle"))")
                    .font(.caption)
                    .foregroundColor(viewModel.isTracking ? .green : .gray)

                Text("\(String(localized: "upload.title")) \(viewModel.uploadStatus.description)")
                    .font(.caption)
                    .foregroundColor(uploadStatusColor)
            }
            
            Spacer()
            
            Button(action: {
                showingStats = true
            }) {
                Image(systemName: "chart.bar")
                    .font(.title)
                    .foregroundColor(.white)
            }
        }
    }
    
    /// The control panel with tracking buttons
    private var controlPanel: some View {
        HStack {
            // Settings button
            Button(action: {
                showingSettings = true
            }) {
                Image(systemName: "gear")
                    .font(.title)
                    .padding()
                    .foregroundColor(.white)
                    .background(Color.primary)
                    .clipShape(Circle())
            }
            
            Spacer()
            
            // Start/Stop tracking button
            Button(action: {
                viewModel.isTracking ? viewModel.stopTracking() : viewModel.startTracking()
            }) {
                Image(systemName: viewModel.isTracking ? "stop.fill" : "play.fill")
                    .font(.largeTitle)
                    .padding()
                    .foregroundColor(.white)
                    .background(viewModel.isTracking ? Color.red : Color.green)
                    .clipShape(Circle())
                    .shadow(radius: 5)
            }
            
            Spacer()
            
            // Center on user button
            Button(action: {
                userTrackingMode = .none
                if let location = viewModel.currentLocation?.coordinate {
                    mapRegion.center = location
                }
            }) {
                Image(systemName: "location")
                    .font(.title)
                    .padding()
                    .foregroundColor(.white)
                    .background(Color.primary)
                    .clipShape(Circle())
            }
        }
    }
    
    /// The color to use for the upload status text
    private var uploadStatusColor: Color {
        switch viewModel.uploadStatus {
        case .idle:
            return .gray
        case .uploading:
            return .yellow
        case .success:
            return .green
        case .failure:
            return .red
        }
    }
}

/// Preview provider for displaying ContentView in Xcode previews
struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
            .environmentObject(MockDependencies.previewViewModel)
            .preferredColorScheme(.dark)
    }
}
