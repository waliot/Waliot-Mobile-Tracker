// /Users/nickfox137/Documents/gpstracker-clients/gpstracker-ios/GPSTracker/GPSTracker/View/StatsView.swift

import SwiftUI

struct StatsView: View {
    @Environment(\.colorScheme) private var colorScheme
    @EnvironmentObject private var viewModel: TrackingViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    statSection(
                        title: String(localized: "stats.distanceAndDuration"),
                        items: [
                            StatItem(
                                title: String(localized: "stats.totalDistance"),
                                value: distanceText,
                                icon: "map",
                                accessibilityIdentifier: "stats.value.totalDistance"
                            ),
                            StatItem(
                                title: String(localized: "stats.totalDuration"),
                                value: durationText,
                                icon: "clock",
                                accessibilityIdentifier: "stats.value.totalDuration"
                            )
                        ]
                    )

                    statSection(
                        title: String(localized: "stats.speed"),
                        items: [
                            StatItem(
                                title: String(localized: "stats.current"),
                                value: speedText(viewModel.currentSpeed),
                                icon: "speedometer",
                                accessibilityIdentifier: "stats.value.currentSpeed"
                            ),
                            StatItem(
                                title: String(localized: "stats.average"),
                                value: speedText(viewModel.averageSpeed),
                                icon: "function",
                                accessibilityIdentifier: "stats.value.averageSpeed"
                            ),
                            StatItem(
                                title: String(localized: "stats.maximum"),
                                value: speedText(viewModel.maxSpeed),
                                icon: "arrow.up",
                                accessibilityIdentifier: "stats.value.maximumSpeed"
                            )
                        ]
                    )

                    statSection(
                        title: String(localized: "stats.data"),
                        items: [
                            StatItem(
                                title: String(localized: "stats.data.collected"),
                                value: "\(viewModel.bufferCount)",
                                icon: "tray.full",
                                accessibilityIdentifier: "stats.value.collected"
                            ),
                            StatItem(
                                title: String(localized: "stats.data.uploaded"),
                                value: "\(viewModel.uploadedCount)",
                                icon: "arrow.up.to.line",
                                accessibilityIdentifier: "stats.value.uploaded"
                            ),
                            StatItem(
                                title: String(localized: "upload.card.lastUpload"),
                                value: lastUploadText,
                                icon: "icloud.and.arrow.up",
                                accessibilityIdentifier: "stats.value.lastUpload"
                            )
                        ]
                    )
                }
                .padding()
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle(Text("stats.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.regularMaterial, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("stats.done") {
                        dismiss()
                    }
                    .accessibilityIdentifier("stats.done")
                }
            }
        }
    }

    private var distanceText: String {
        String.localizedStringWithFormat(
            String(localized: "units.kilometers.precise"),
            viewModel.totalDistance / 1000
        )
    }

    private var durationText: String {
        let formatter = DateComponentsFormatter()
        formatter.unitsStyle = .short
        formatter.allowedUnits = [.hour, .minute, .second]
        formatter.zeroFormattingBehavior = [.dropLeading, .pad]
        return formatter.string(from: viewModel.sessionDuration) ?? String(localized: "stats.duration.zero")
    }

    private var lastUploadText: String {
        guard let lastSuccessfulUploadAt = viewModel.lastSuccessfulUploadAt else {
            return String(localized: "upload.card.never")
        }
        return lastSuccessfulUploadAt.formatted(date: .omitted, time: .shortened)
    }

    private func speedText(_ speed: Double) -> String {
        String.localizedStringWithFormat(
            String(localized: "units.speed.kmh.precise"),
            speed * 3.6
        )
    }

    private func statSection(title: String, items: [StatItem]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Color(uiColor: .secondaryLabel))

            VStack(spacing: 0) {
                ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                    StatItemRow(item: item)

                    if index < items.count - 1 {
                        Divider()
                            .overlay(dividerColor)
                            .padding(.leading, 52)
                    }
                }
            }
        }
        .padding(18)
        .background(cardBackground)
    }

    private var dividerColor: Color {
        colorScheme == .dark ? Color.white.opacity(0.10) : Color.black.opacity(0.08)
    }

    private var cardBackground: some View {
        RoundedRectangle(cornerRadius: 22, style: .continuous)
            .fill(.regularMaterial)
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(
                        colorScheme == .dark ? Color.white.opacity(0.12) : Color.white.opacity(0.5),
                        lineWidth: 1
                    )
            )
            .shadow(color: Color.black.opacity(colorScheme == .dark ? 0.18 : 0.08), radius: 16, y: 8)
    }
}

private struct StatItem: Identifiable {
    let id = UUID()
    let title: String
    let value: String
    let icon: String
    let accessibilityIdentifier: String
}

private struct StatItemRow: View {
    let item: StatItem

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(Color.accentColor.opacity(0.14))
                    .frame(width: 34, height: 34)

                Image(systemName: item.icon)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.accentColor)
            }

            Text(item.title)
                .foregroundStyle(Color(uiColor: .secondaryLabel))

            Spacer()

            Text(item.value)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color(uiColor: .label))
                .multilineTextAlignment(.trailing)
                .accessibilityIdentifier(item.accessibilityIdentifier)
        }
        .padding(.vertical, 10)
    }
}

struct StatsView_Previews: PreviewProvider {
    static var previews: some View {
        StatsView()
            .environmentObject(MockDependencies.previewViewModel)
            .preferredColorScheme(.dark)
    }
}
