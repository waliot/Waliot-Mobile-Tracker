// /Users/nickfox137/Documents/gpstracker-clients/gpstracker-ios/GPSTracker/GPSTracker/View/StatsView.swift

import SwiftUI
import Charts

/// Statistics view displaying tracking session metrics
///
/// This view presents detailed statistics about the current tracking session,
/// including distance traveled, duration, speed, and other metrics.
///
/// ## Overview
/// StatsView displays:
/// - Total distance traveled
/// - Session duration
/// - Current, average, and maximum speed
/// - Altitude changes
/// - Data points collected and uploaded
///
/// ## Topics
/// ### Statistics Display
/// - ``distanceSection``
/// - ``speedSection``
/// - ``dataSection``
struct StatsView: View {
    /// The view model that manages tracking data
    @EnvironmentObject private var viewModel: TrackingViewModel
    
    /// Environment object to dismiss this view
    @Environment(\.dismiss) private var dismiss
    
    /// Selected tab for the statistics display
    @State private var selectedTab = 0
    
    /// The body of the view defining its content and layout
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // Distance and duration statistics
                    distanceSection
                    
                    // Speed statistics
                    speedSection
                    
                    // Data collection statistics
                    dataSection
                    
                    // Speed over time chart
//                    if #available(iOS 16.0, *) {
//                        if !viewModel.speedData.isEmpty {
//                            chartSection
//                        }
//                    }
                }
                .padding()
            }
            .navigationTitle(Text("stats.title"))
            .navigationBarItems(trailing: Button("stats.done") {
                dismiss()
            })
        }
    }
    
    /// Section displaying distance and duration information
    private var distanceSection: some View {
        VStack {
            Text("stats.distanceAndDuration")
                .font(.headline)
                .frame(maxWidth: .infinity, alignment: .leading)
            
            HStack {
                StatCard(
                    title: String(localized: "stats.totalDistance"),
                    value: String(format: "%.2f km", viewModel.totalDistance / 1000),
                    icon: "map"
                )
                
                StatCard(
                    title: String(localized: "stats.totalDuration"),
                    value: formatDuration(viewModel.sessionDuration),
                    icon: "clock"
                )
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(10)
    }
    
    /// Section displaying speed metrics
    private var speedSection: some View {
        VStack {
            Text("stats.speed")
                .font(.headline)
                .frame(maxWidth: .infinity, alignment: .leading)
            
            HStack {
                StatCard(
                    title: String(localized: "stats.current"),
                    value: String(format: "%.1f km/h", viewModel.currentSpeed * 3.6),
                    icon: "speedometer"
                )
                
                StatCard(
                    title: String(localized: "stats.average"),
                    value: String(format: "%.1f km/h", viewModel.averageSpeed * 3.6),
                    icon: "function"
                )
                
                StatCard(
                    title: String(localized: "stats.maximum"),
                    value: String(format: "%.1f km/h", viewModel.maxSpeed * 3.6),
                    icon: "arrow.up"
                )
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(10)
    }
    
    /// Section displaying data collection information
    private var dataSection: some View {
        VStack {
            Text("stats.data")
                .font(.headline)
                .frame(maxWidth: .infinity, alignment: .leading)
            
            HStack {
                StatCard(
                    title: String(localized: "stats.data.collected"),
                    value: "\(viewModel.locationCount)",
                    icon: "location"
                )
                
                StatCard(
                    title: String(localized: "stats.data.uploaded"),
                    value: "\(viewModel.uploadedCount)",
                    icon: "arrow.up.to.line"
                )
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(10)
    }
    
    /// Section displaying speed charts
    private var chartSection: some View {
        VStack {
            Text("stats.charts.title")
                .font(.headline)
                .frame(maxWidth: .infinity, alignment: .leading)
            
            Picker("Chart Type", selection: $selectedTab) {
                Text("stats.charts.line").tag(0)
                Text("stats.charts.bar").tag(1)
            }
            .pickerStyle(SegmentedPickerStyle())
            .padding(.bottom)
            
            if selectedTab == 0 {
                Chart(viewModel.speedData) { dataPoint in
                    LineMark(
                        x: .value("Time", dataPoint.timestamp),
                        y: .value("Speed", dataPoint.speed * 3.6)
                    )
                    .foregroundStyle(.blue)
                }
                .frame(height: 250)
                .chartYAxis {
                    AxisMarks(position: .leading)
                }
            } else {
                Chart(viewModel.speedData) { dataPoint in
                    BarMark(
                        x: .value("Time", dataPoint.timestamp),
                        y: .value("Speed", dataPoint.speed * 3.6)
                    )
                    .foregroundStyle(.blue)
                }
                .frame(height: 250)
                .chartYAxis {
                    AxisMarks(position: .leading)
                }
            }
            
            Text("stats.charts.legend")
                .font(.caption)
                .foregroundColor(.gray)
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(10)
    }
    
    /// Formats a time interval into a human-readable duration string
    /// - Parameter interval: The time interval to format
    /// - Returns: A formatted string (e.g., "1h 23m 45s")
    private func formatDuration(_ interval: TimeInterval) -> String {
        let hours = Int(interval) / 3600
        let minutes = (Int(interval) % 3600) / 60
        let seconds = Int(interval) % 60
        
        if hours > 0 {
            return String(format: "%dh %dm %ds", hours, minutes, seconds)
        } else if minutes > 0 {
            return String(format: "%dm %ds", minutes, seconds)
        } else {
            return String(format: "%ds", seconds)
        }
    }
}

/// A card displaying a statistic with a title, value, and icon
struct StatCard: View {
    /// The title of the statistic
    let title: String
    
    /// The value to display
    let value: String
    
    /// SF Symbol name for the icon
    let icon: String
    
    var body: some View {
        VStack {
            Image(systemName: icon)
                .font(.title)
                .foregroundColor(.blue)
                .padding(.bottom, 4)
            
            Text(title)
                .font(.caption)
                .foregroundColor(.gray)
            
            Text(value)
                .font(.title3)
                .bold()
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }
}

/// Preview provider for displaying StatsView in Xcode previews
struct StatsView_Previews: PreviewProvider {
    static var previews: some View {
        StatsView()
            .environmentObject(MockDependencies.previewViewModel)
            .preferredColorScheme(.dark)
    }
}
