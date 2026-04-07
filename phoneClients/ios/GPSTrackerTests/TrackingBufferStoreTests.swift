import Foundation
import Testing
@testable import GPSTracker

struct TrackingBufferStoreTests {
    @Test
    func sqliteStorePersistsBufferAcrossNewStoreInstances() throws {
        let fileManager = FileManager.default
        let rootDirectory = fileManager.temporaryDirectory.appendingPathComponent("TrackingBufferStoreTests.\(UUID().uuidString)", isDirectory: true)
        try fileManager.createDirectory(at: rootDirectory, withIntermediateDirectories: true)
        defer { try? fileManager.removeItem(at: rootDirectory) }

        let firstSnapshot = makeSnapshot(latitude: 55.75, longitude: 37.61, accuracy: 8, timestamp: Date(timeIntervalSince1970: 1_700_000_000))
        let secondSnapshot = makeSnapshot(latitude: 55.751, longitude: 37.615, accuracy: 12, timestamp: Date(timeIntervalSince1970: 1_700_000_120))

        let store = TrackingBufferStore(fileManager: fileManager, directoryURL: rootDirectory)
        store.appendBufferedLocation(makeRecord(snapshot: firstSnapshot), maxSize: 10, lastBufferedLocation: firstSnapshot)
        store.appendBufferedLocation(makeRecord(snapshot: secondSnapshot), maxSize: 10, lastBufferedLocation: secondSnapshot)

        let restoredStore = TrackingBufferStore(fileManager: fileManager, directoryURL: rootDirectory)
        let restoredState = restoredStore.loadState()

        #expect(restoredState.bufferedLocations.count == 2)
        #expect(restoredState.bufferedLocations[0].snapshot.latitude == firstSnapshot.latitude)
        #expect(restoredState.bufferedLocations[1].snapshot.longitude == secondSnapshot.longitude)
        #expect(restoredState.lastBufferedLocation == secondSnapshot)

        restoredStore.removeOldestBufferedLocation()
        let drainedState = restoredStore.loadState()
        #expect(drainedState.bufferedLocations.count == 1)
        #expect(drainedState.bufferedLocations[0].snapshot == secondSnapshot)
    }

    @Test
    func sqliteStoreMigratesLegacyJsonSnapshotOnce() throws {
        let fileManager = FileManager.default
        let rootDirectory = fileManager.temporaryDirectory.appendingPathComponent("TrackingBufferStoreTests.\(UUID().uuidString)", isDirectory: true)
        let storageDirectory = rootDirectory.appendingPathComponent("TrackingBuffer", isDirectory: true)
        try fileManager.createDirectory(at: storageDirectory, withIntermediateDirectories: true)
        defer { try? fileManager.removeItem(at: rootDirectory) }

        let snapshot = makeSnapshot(latitude: 59.93, longitude: 30.31, accuracy: 15, timestamp: Date(timeIntervalSince1970: 1_700_000_500))
        let legacyState = TrackingBufferState(
            bufferedLocations: [makeRecord(snapshot: snapshot)],
            lastBufferedLocation: snapshot
        )
        let legacyURL = storageDirectory.appendingPathComponent("tracking-buffer.json")
        let data = try JSONEncoder().encode(legacyState)
        try data.write(to: legacyURL, options: [.atomic])

        let store = TrackingBufferStore(fileManager: fileManager, directoryURL: rootDirectory)
        let restoredState = store.loadState()

        #expect(restoredState.bufferedLocations.count == 1)
        #expect(restoredState.bufferedLocations[0].snapshot == snapshot)
        #expect(restoredState.lastBufferedLocation == snapshot)
        #expect(fileManager.fileExists(atPath: legacyURL.path) == false)
    }

    @Test
    func sqliteStoreKeepsOnlyLatestBufferedRecordsWhenMaxSizeIsExceeded() throws {
        let fileManager = FileManager.default
        let rootDirectory = fileManager.temporaryDirectory.appendingPathComponent("TrackingBufferStoreTests.\(UUID().uuidString)", isDirectory: true)
        try fileManager.createDirectory(at: rootDirectory, withIntermediateDirectories: true)
        defer { try? fileManager.removeItem(at: rootDirectory) }

        let firstSnapshot = makeSnapshot(latitude: 55.75, longitude: 37.61, accuracy: 8, timestamp: Date(timeIntervalSince1970: 1_700_000_000))
        let secondSnapshot = makeSnapshot(latitude: 55.751, longitude: 37.611, accuracy: 8, timestamp: Date(timeIntervalSince1970: 1_700_000_060))
        let thirdSnapshot = makeSnapshot(latitude: 55.752, longitude: 37.612, accuracy: 8, timestamp: Date(timeIntervalSince1970: 1_700_000_120))

        let store = TrackingBufferStore(fileManager: fileManager, directoryURL: rootDirectory)
        store.appendBufferedLocation(makeRecord(snapshot: firstSnapshot), maxSize: 2, lastBufferedLocation: firstSnapshot)
        store.appendBufferedLocation(makeRecord(snapshot: secondSnapshot), maxSize: 2, lastBufferedLocation: secondSnapshot)
        store.appendBufferedLocation(makeRecord(snapshot: thirdSnapshot), maxSize: 2, lastBufferedLocation: thirdSnapshot)

        let restoredState = store.loadState()
        #expect(restoredState.bufferedLocations.count == 2)
        #expect(restoredState.bufferedLocations[0].snapshot == secondSnapshot)
        #expect(restoredState.bufferedLocations[1].snapshot == thirdSnapshot)
        #expect(restoredState.lastBufferedLocation == thirdSnapshot)
    }

    @Test
    func replaceLastBufferedLocationCanUpdateAndClearMetadataIndependently() throws {
        let fileManager = FileManager.default
        let rootDirectory = fileManager.temporaryDirectory.appendingPathComponent("TrackingBufferStoreTests.\(UUID().uuidString)", isDirectory: true)
        try fileManager.createDirectory(at: rootDirectory, withIntermediateDirectories: true)
        defer { try? fileManager.removeItem(at: rootDirectory) }

        let firstSnapshot = makeSnapshot(latitude: 55.75, longitude: 37.61, accuracy: 8, timestamp: Date(timeIntervalSince1970: 1_700_000_000))
        let secondSnapshot = makeSnapshot(latitude: 55.76, longitude: 37.62, accuracy: 12, timestamp: Date(timeIntervalSince1970: 1_700_000_120))

        let store = TrackingBufferStore(fileManager: fileManager, directoryURL: rootDirectory)
        store.appendBufferedLocation(makeRecord(snapshot: firstSnapshot), maxSize: 10, lastBufferedLocation: firstSnapshot)

        store.replaceLastBufferedLocation(secondSnapshot)
        var state = store.loadState()
        #expect(state.bufferedLocations.count == 1)
        #expect(state.lastBufferedLocation == secondSnapshot)

        store.replaceLastBufferedLocation(nil)
        state = store.loadState()
        #expect(state.bufferedLocations.count == 1)
        #expect(state.lastBufferedLocation == nil)
    }

    @Test
    func appendWithZeroMaxSizeDropsRowsButPreservesAnchorMetadata() throws {
        let fileManager = FileManager.default
        let rootDirectory = fileManager.temporaryDirectory.appendingPathComponent("TrackingBufferStoreTests.\(UUID().uuidString)", isDirectory: true)
        try fileManager.createDirectory(at: rootDirectory, withIntermediateDirectories: true)
        defer { try? fileManager.removeItem(at: rootDirectory) }

        let snapshot = makeSnapshot(latitude: 55.75, longitude: 37.61, accuracy: 8, timestamp: Date(timeIntervalSince1970: 1_700_000_000))
        let store = TrackingBufferStore(fileManager: fileManager, directoryURL: rootDirectory)

        store.appendBufferedLocation(makeRecord(snapshot: snapshot), maxSize: 0, lastBufferedLocation: snapshot)

        let state = store.loadState()
        #expect(state.bufferedLocations.isEmpty)
        #expect(state.lastBufferedLocation == snapshot)
    }

    @Test
    func clearRemovesBufferedRowsAndMetadata() throws {
        let fileManager = FileManager.default
        let rootDirectory = fileManager.temporaryDirectory.appendingPathComponent("TrackingBufferStoreTests.\(UUID().uuidString)", isDirectory: true)
        try fileManager.createDirectory(at: rootDirectory, withIntermediateDirectories: true)
        defer { try? fileManager.removeItem(at: rootDirectory) }

        let snapshot = makeSnapshot(latitude: 55.75, longitude: 37.61, accuracy: 8, timestamp: Date(timeIntervalSince1970: 1_700_000_000))
        let store = TrackingBufferStore(fileManager: fileManager, directoryURL: rootDirectory)
        store.appendBufferedLocation(makeRecord(snapshot: snapshot), maxSize: 10, lastBufferedLocation: snapshot)

        store.clear()

        let state = store.loadState()
        #expect(state.bufferedLocations.isEmpty)
        #expect(state.lastBufferedLocation == nil)
    }

    private func makeSnapshot(
        latitude: Double,
        longitude: Double,
        accuracy: Double,
        timestamp: Date
    ) -> LocationSnapshot {
        LocationSnapshot(
            latitude: latitude,
            longitude: longitude,
            altitude: 120,
            horizontalAccuracy: accuracy,
            verticalAccuracy: 6,
            speed: 10,
            course: 90,
            timestamp: timestamp,
            provider: .gps,
            isSimulated: false
        )
    }

    private func makeRecord(snapshot: LocationSnapshot) -> BufferedLocationRecord {
        BufferedLocationRecord(
            trackerIdentifier: "ABC123",
            sessionId: "session-1",
            appId: "app-1",
            snapshot: snapshot,
            distance: 150,
            battery: 87
        )
    }
}
