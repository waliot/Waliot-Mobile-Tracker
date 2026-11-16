import Foundation

enum TrackingDurationFormatter {
    static func sessionDuration(_ duration: TimeInterval, locale: Locale = .current) -> String? {
        let formatter = DateComponentsFormatter()
        var calendar = Calendar.current
        calendar.locale = locale
        formatter.calendar = calendar
        formatter.unitsStyle = .short
        formatter.allowedUnits = [.hour, .minute, .second]
        formatter.zeroFormattingBehavior = [.dropLeading, .pad]
        return formatter.string(from: duration)?.replacingOccurrences(of: ", ", with: " ")
    }

    static func fixAge(_ age: TimeInterval, locale: Locale = .current) -> String? {
        let formatter = DateComponentsFormatter()
        var calendar = Calendar.current
        calendar.locale = locale
        formatter.calendar = calendar
        formatter.unitsStyle = .full
        formatter.allowedUnits = age >= 3600 ? [.hour, .minute] : [.minute, .second]
        formatter.maximumUnitCount = age >= 3600 ? 2 : 1
        return formatter.string(from: age)?.replacingOccurrences(of: ", ", with: " ")
    }
}
