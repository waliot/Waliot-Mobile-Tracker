import Foundation
import Testing
@testable import GPSTracker

struct SettingsRepositoryTests {
    @Test
    func migratesLegacySettingsWithoutChangingExistingUploadCadence() {
        let suiteName = "SettingsRepositoryTests.\(UUID().uuidString)"
        guard let defaults = UserDefaults(suiteName: suiteName) else {
            Issue.record("Could not create test user defaults suite")
            return
        }
        defer {
            defaults.removePersistentDomain(forName: suiteName)
        }

        defaults.set("legacytracker01", forKey: "com.waliot.tracker.username")
        defaults.set("legacy.example.com:1234", forKey: "com.waliot.tracker.server_url")
        defaults.set(2, forKey: "com.waliot.tracker.tracking_interval")
        defaults.set(150, forKey: "com.waliot.tracker.distance_filter")

        let persistenceService = PersistenceService(userDefaults: defaults)
        let repository = SettingsRepository(persistenceService: persistenceService)

        #expect(repository.getTrackerIdentifier() == "legacytracker01")
        #expect(repository.getUploadServer() == "legacy.example.com:1234")
        #expect(repository.getUploadTimeInterval() == 2)
        #expect(repository.getBufferTimeInterval() == 2)
        #expect(repository.getBufferDistanceInterval() == 150)
    }

    @Test
    func usesNewDefaultCadencesForFreshInstall() {
        let suiteName = "SettingsRepositoryTests.\(UUID().uuidString)"
        guard let defaults = UserDefaults(suiteName: suiteName) else {
            Issue.record("Could not create test user defaults suite")
            return
        }
        defer {
            defaults.removePersistentDomain(forName: suiteName)
        }

        let persistenceService = PersistenceService(userDefaults: defaults)
        let repository = SettingsRepository(persistenceService: persistenceService)

        #expect(repository.getUploadTimeInterval() == 5)
        #expect(repository.getBufferTimeInterval() == 1)
        #expect(repository.getBufferDistanceInterval() == 100)
    }

    @Test
    func persistsTrackingStateSeparatelyFromCollectionIntervals() {
        let suiteName = "SettingsRepositoryTests.\(UUID().uuidString)"
        guard let defaults = UserDefaults(suiteName: suiteName) else {
            Issue.record("Could not create test user defaults suite")
            return
        }
        defer {
            defaults.removePersistentDomain(forName: suiteName)
        }

        let persistenceService = PersistenceService(userDefaults: defaults)
        let repository = SettingsRepository(persistenceService: persistenceService)

        #expect(repository.getTrackingState() == false)
        repository.saveTrackingState(true)
        #expect(repository.getTrackingState() == true)
        #expect(repository.getUploadTimeInterval() == 5)
        #expect(repository.getBufferTimeInterval() == 1)
        #expect(repository.getBufferDistanceInterval() == 100)
    }
}
