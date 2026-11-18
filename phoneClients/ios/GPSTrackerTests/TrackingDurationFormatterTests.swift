import Foundation
import XCTest
@testable import GPSTracker

final class TrackingDurationFormatterTests: XCTestCase {
    private let russian = Locale(identifier: "ru_RU")
    private let english = Locale(identifier: "en_US")

    func testSessionDurationOmitsListCommasInScreenshot() {
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(3932, locale: russian), "1 ч 5 мин 32 с")
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(3932, locale: english), "1 hr 5 min 32 sec")
    }

    func testSessionDurationKeepsZeroSeconds() {
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(0, locale: russian), "0 с")
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(0, locale: english), "0 sec")
    }

    func testSessionDurationDropsLeadingHoursAndKeepsTrailingSeconds() {
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(60, locale: russian), "1 мин 0 с")
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(60, locale: english), "1 min 0 sec")
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(65, locale: russian), "1 мин 5 с")
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(65, locale: english), "1 min 5 sec")
    }

    func testSessionDurationKeepsZeroMinutesAndSecondsAfterHours() {
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(3600, locale: russian), "1 ч 0 мин 0 с")
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(3600, locale: english), "1 hr 0 min 0 sec")
    }

    func testSessionDurationKeepsHoursBeyondOneDay() {
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(90061, locale: russian), "25 ч 1 мин 1 с")
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(90061, locale: english), "25 hr 1 min 1 sec")
    }

    func testFixAgeOmitsListCommasAndKeepsFullUnits() {
        XCTAssertEqual(TrackingDurationFormatter.fixAge(3900, locale: russian), "1 час 5 минут")
        XCTAssertEqual(TrackingDurationFormatter.fixAge(3900, locale: english), "1 hour 5 minutes")
    }

    func testFixAgeBelowOneHourKeepsOneUnit() {
        XCTAssertEqual(TrackingDurationFormatter.fixAge(32, locale: russian), "32 секунды")
        XCTAssertEqual(TrackingDurationFormatter.fixAge(32, locale: english), "32 seconds")
        XCTAssertEqual(TrackingDurationFormatter.fixAge(65, locale: russian), "1 минута")
        XCTAssertEqual(TrackingDurationFormatter.fixAge(65, locale: english), "1 minute")
    }

    func testFixAgeKeepsExistingMinuteTruncation() {
        XCTAssertEqual(TrackingDurationFormatter.fixAge(3932, locale: russian), "1 час 5 минут")
        XCTAssertEqual(TrackingDurationFormatter.fixAge(3932, locale: english), "1 hour 5 minutes")
    }

    func testDurationFormattingPreservesNumericGrouping() {
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(3600000, locale: russian), "1\u{00a0}000 ч 0 мин 0 с")
        XCTAssertEqual(TrackingDurationFormatter.sessionDuration(3600000, locale: english), "1,000 hr 0 min 0 sec")
        XCTAssertEqual(TrackingDurationFormatter.fixAge(3600000, locale: russian), "1\u{00a0}000 часов")
        XCTAssertEqual(TrackingDurationFormatter.fixAge(3600000, locale: english), "1,000 hours")
    }

    func testLocationAgePresentationUsesProvidedLanguage() {
        let presentation = TrackingLocationPresentation(fixAge: 3932)
        XCTAssertEqual(presentation.ageDescription(locale: english), "1 hour 5 minutes")
        XCTAssertEqual(presentation.ageDescription(locale: russian), "1 час 5 минут")
    }

    func testMissingLocationAgeUsesProvidedLanguage() {
        let presentation = TrackingLocationPresentation(fixAge: nil)
        XCTAssertEqual(presentation.ageDescription(locale: english), "No data")
        XCTAssertEqual(presentation.ageDescription(locale: russian), "Нет данных")
    }

    func testFreshLocationAgeUsesProvidedLanguage() {
        for age in [0.0, 9.999] {
            let presentation = TrackingLocationPresentation(fixAge: age)
            XCTAssertEqual(presentation.ageDescription(locale: english), "Just now")
            XCTAssertEqual(presentation.ageDescription(locale: russian), "Только что")
        }
    }
}
