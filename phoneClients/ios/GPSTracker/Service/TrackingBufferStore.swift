import Foundation
import SQLite3
import os

private let sqliteTransientDestructor = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

protocol TrackingBufferStoreProtocol {
    func loadState() -> TrackingBufferState
    func saveState(_ state: TrackingBufferState)
    func appendBufferedLocation(
        _ location: BufferedLocationRecord,
        maxSize: Int,
        lastBufferedLocation: LocationSnapshot?
    )
    func removeOldestBufferedLocation()
    func replaceLastBufferedLocation(_ location: LocationSnapshot?)
    func clear()
}

final class TrackingBufferStore: TrackingBufferStoreProtocol {
    private enum Schema {
        static let bufferedLocationsTable = "buffered_locations"
        static let metadataTable = "metadata"
        static let sequenceColumn = "sequence_id"
        static let keyColumn = "key_name"
        static let payloadColumn = "payload"
        static let lastBufferedLocationKey = "last_buffered_location"
        static let databaseFileName = "tracking-buffer.sqlite3"
        static let legacyStateFileName = "tracking-buffer.json"
    }

    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "com.waliot.tracker", category: "TrackingBufferStore")
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let fileManager: FileManager
    private let databaseURL: URL
    private let legacyStateURL: URL
    private let lock = NSLock()

    private var database: OpaquePointer?
    private var didAttemptLegacyMigration = false

    init(
        fileManager: FileManager = .default,
        directoryURL: URL? = nil,
        databaseFileName: String = Schema.databaseFileName,
        legacyStateFileName: String = Schema.legacyStateFileName
    ) {
        self.fileManager = fileManager

        let rootDirectory = directoryURL ?? fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!
        let trackingDirectory = rootDirectory.appendingPathComponent("TrackingBuffer", isDirectory: true)
        try? fileManager.createDirectory(at: trackingDirectory, withIntermediateDirectories: true, attributes: nil)

        self.databaseURL = trackingDirectory.appendingPathComponent(databaseFileName)
        self.legacyStateURL = trackingDirectory.appendingPathComponent(legacyStateFileName)
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    }

    deinit {
        lock.lock()
        defer { lock.unlock() }
        closeDatabase()
    }

    func loadState() -> TrackingBufferState {
        lock.lock()
        defer { lock.unlock() }

        guard ensureDatabaseReady() else {
            return TrackingBufferState()
        }

        do {
            return try unsafeLoadState()
        } catch {
            log("Failed to restore persisted tracking buffer state: \(error.localizedDescription)", level: .error, logger: logger)
            unsafeClear()
            return TrackingBufferState()
        }
    }

    func saveState(_ state: TrackingBufferState) {
        lock.lock()
        defer { lock.unlock() }

        guard ensureDatabaseReady() else { return }

        do {
            try unsafeSaveState(state)
        } catch {
            log("Failed to persist tracking buffer state: \(error.localizedDescription)", level: .error, logger: logger)
        }
    }

    func appendBufferedLocation(
        _ location: BufferedLocationRecord,
        maxSize: Int,
        lastBufferedLocation: LocationSnapshot?
    ) {
        lock.lock()
        defer { lock.unlock() }

        guard ensureDatabaseReady() else { return }

        do {
            try performTransaction {
                try insertBufferedLocation(location)
                try trimBufferedLocations(toLast: maxSize)
                try replaceLastBufferedLocationPayload(lastBufferedLocation)
            }
        } catch {
            log("Failed to append buffered location: \(error.localizedDescription)", level: .error, logger: logger)
        }
    }

    func removeOldestBufferedLocation() {
        lock.lock()
        defer { lock.unlock() }

        guard ensureDatabaseReady() else { return }

        do {
            try performTransaction {
                try execute(
                    """
                    DELETE FROM \(Schema.bufferedLocationsTable)
                    WHERE \(Schema.sequenceColumn) = (
                        SELECT \(Schema.sequenceColumn)
                        FROM \(Schema.bufferedLocationsTable)
                        ORDER BY \(Schema.sequenceColumn) ASC
                        LIMIT 1
                    );
                    """
                )
            }
        } catch {
            log("Failed to remove oldest buffered location: \(error.localizedDescription)", level: .error, logger: logger)
        }
    }

    func replaceLastBufferedLocation(_ location: LocationSnapshot?) {
        lock.lock()
        defer { lock.unlock() }

        guard ensureDatabaseReady() else { return }

        do {
            try performTransaction {
                try replaceLastBufferedLocationPayload(location)
            }
        } catch {
            log("Failed to replace last buffered location: \(error.localizedDescription)", level: .error, logger: logger)
        }
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        unsafeClear()
    }

    private func ensureDatabaseReady() -> Bool {
        if database == nil, !openDatabase() {
            return false
        }

        do {
            try createSchemaIfNeeded()
            if !didAttemptLegacyMigration {
                try migrateLegacyStateIfNeeded()
                didAttemptLegacyMigration = true
            }
            return true
        } catch {
            log("Failed to initialize tracking buffer database: \(error.localizedDescription)", level: .error, logger: logger)
            closeDatabase()
            return false
        }
    }

    private func openDatabase() -> Bool {
        var openedDatabase: OpaquePointer?
        let result = sqlite3_open_v2(
            databaseURL.path,
            &openedDatabase,
            SQLITE_OPEN_CREATE | SQLITE_OPEN_READWRITE | SQLITE_OPEN_FULLMUTEX,
            nil
        )

        guard result == SQLITE_OK, let openedDatabase else {
            let message = openedDatabase.map { databaseErrorMessage(for: $0) } ?? "Unknown SQLite open error"
            log("Failed to open tracking buffer database: \(message)", level: .error, logger: logger)
            if let openedDatabase {
                sqlite3_close_v2(openedDatabase)
            }
            return false
        }

        database = openedDatabase
        _ = sqlite3_exec(openedDatabase, "PRAGMA journal_mode=WAL;", nil, nil, nil)
        _ = sqlite3_exec(openedDatabase, "PRAGMA synchronous=NORMAL;", nil, nil, nil)
        return true
    }

    private func closeDatabase() {
        if let database {
            sqlite3_close_v2(database)
            self.database = nil
        }
    }

    private func createSchemaIfNeeded() throws {
        try execute(
            """
            CREATE TABLE IF NOT EXISTS \(Schema.bufferedLocationsTable) (
                \(Schema.sequenceColumn) INTEGER PRIMARY KEY AUTOINCREMENT,
                \(Schema.payloadColumn) BLOB NOT NULL
            );
            """
        )
        try execute(
            """
            CREATE TABLE IF NOT EXISTS \(Schema.metadataTable) (
                \(Schema.keyColumn) TEXT PRIMARY KEY,
                \(Schema.payloadColumn) BLOB
            );
            """
        )
    }

    private func migrateLegacyStateIfNeeded() throws {
        guard fileManager.fileExists(atPath: legacyStateURL.path) else {
            return
        }

        let currentState = try unsafeLoadState()
        if !currentState.bufferedLocations.isEmpty || currentState.lastBufferedLocation != nil {
            try? fileManager.removeItem(at: legacyStateURL)
            return
        }

        do {
            let data = try Data(contentsOf: legacyStateURL)
            let state = try decoder.decode(TrackingBufferState.self, from: data)
            try unsafeSaveState(state)
            try? fileManager.removeItem(at: legacyStateURL)
        } catch {
            log("Failed to migrate legacy JSON tracking buffer state: \(error.localizedDescription)", level: .error, logger: logger)
            try? fileManager.removeItem(at: legacyStateURL)
        }
    }

    private func unsafeLoadState() throws -> TrackingBufferState {
        let bufferedLocations = try loadBufferedLocations()
        let lastBufferedLocation = try loadLastBufferedLocation()
        return TrackingBufferState(
            bufferedLocations: bufferedLocations,
            lastBufferedLocation: lastBufferedLocation
        )
    }

    private func unsafeSaveState(_ state: TrackingBufferState) throws {
        try performTransaction {
            try execute("DELETE FROM \(Schema.bufferedLocationsTable);")
            try execute("DELETE FROM \(Schema.metadataTable);")
            for location in state.bufferedLocations {
                try insertBufferedLocation(location)
            }
            try replaceLastBufferedLocationPayload(state.lastBufferedLocation)
        }
    }

    private func unsafeClear() {
        guard ensureDatabaseReady() else { return }
        do {
            try performTransaction {
                try execute("DELETE FROM \(Schema.bufferedLocationsTable);")
                try execute("DELETE FROM \(Schema.metadataTable);")
            }
        } catch {
            log("Failed to clear persisted tracking buffer state: \(error.localizedDescription)", level: .error, logger: logger)
        }
    }

    private func loadBufferedLocations() throws -> [BufferedLocationRecord] {
        let statement = try prepare(
            """
            SELECT \(Schema.payloadColumn)
            FROM \(Schema.bufferedLocationsTable)
            ORDER BY \(Schema.sequenceColumn) ASC;
            """
        )
        defer { sqlite3_finalize(statement) }

        var locations: [BufferedLocationRecord] = []
        while true {
            let stepResult = sqlite3_step(statement)
            switch stepResult {
            case SQLITE_ROW:
                let payload = try data(from: statement, column: 0)
                locations.append(try decoder.decode(BufferedLocationRecord.self, from: payload))
            case SQLITE_DONE:
                return locations
            default:
                throw databaseError()
            }
        }
    }

    private func loadLastBufferedLocation() throws -> LocationSnapshot? {
        let statement = try prepare(
            """
            SELECT \(Schema.payloadColumn)
            FROM \(Schema.metadataTable)
            WHERE \(Schema.keyColumn) = ?
            LIMIT 1;
            """
        )
        defer { sqlite3_finalize(statement) }

        try bind(Schema.lastBufferedLocationKey, to: 1, in: statement)

        let stepResult = sqlite3_step(statement)
        switch stepResult {
        case SQLITE_ROW:
            let payload = try data(from: statement, column: 0)
            return try decoder.decode(LocationSnapshot.self, from: payload)
        case SQLITE_DONE:
            return nil
        default:
            throw databaseError()
        }
    }

    private func insertBufferedLocation(_ location: BufferedLocationRecord) throws {
        let statement = try prepare(
            """
            INSERT INTO \(Schema.bufferedLocationsTable) (\(Schema.payloadColumn))
            VALUES (?);
            """
        )
        defer { sqlite3_finalize(statement) }

        let payload = try encoder.encode(location)
        try bind(payload, to: 1, in: statement)
        try stepExpectDone(statement)
    }

    private func replaceLastBufferedLocationPayload(_ location: LocationSnapshot?) throws {
        if let location {
            let statement = try prepare(
                """
                INSERT INTO \(Schema.metadataTable) (\(Schema.keyColumn), \(Schema.payloadColumn))
                VALUES (?, ?)
                ON CONFLICT(\(Schema.keyColumn))
                DO UPDATE SET \(Schema.payloadColumn) = excluded.\(Schema.payloadColumn);
                """
            )
            defer { sqlite3_finalize(statement) }

            let payload = try encoder.encode(location)
            try bind(Schema.lastBufferedLocationKey, to: 1, in: statement)
            try bind(payload, to: 2, in: statement)
            try stepExpectDone(statement)
        } else {
            let statement = try prepare(
                """
                DELETE FROM \(Schema.metadataTable)
                WHERE \(Schema.keyColumn) = ?;
                """
            )
            defer { sqlite3_finalize(statement) }

            try bind(Schema.lastBufferedLocationKey, to: 1, in: statement)
            try stepExpectDone(statement)
        }
    }

    private func trimBufferedLocations(toLast maxSize: Int) throws {
        guard maxSize > 0 else {
            try execute("DELETE FROM \(Schema.bufferedLocationsTable);")
            return
        }

        let statement = try prepare(
            """
            DELETE FROM \(Schema.bufferedLocationsTable)
            WHERE \(Schema.sequenceColumn) NOT IN (
                SELECT \(Schema.sequenceColumn)
                FROM \(Schema.bufferedLocationsTable)
                ORDER BY \(Schema.sequenceColumn) DESC
                LIMIT ?
            );
            """
        )
        defer { sqlite3_finalize(statement) }

        try bind(Int64(maxSize), to: 1, in: statement)
        try stepExpectDone(statement)
    }

    private func performTransaction(_ block: () throws -> Void) throws {
        try execute("BEGIN IMMEDIATE TRANSACTION;")
        do {
            try block()
            try execute("COMMIT TRANSACTION;")
        } catch {
            try? execute("ROLLBACK TRANSACTION;")
            throw error
        }
    }

    private func execute(_ sql: String) throws {
        guard let database else {
            throw NSError(domain: "TrackingBufferStore", code: 1, userInfo: [NSLocalizedDescriptionKey: "SQLite database is not open"])
        }

        let result = sqlite3_exec(database, sql, nil, nil, nil)
        guard result == SQLITE_OK else {
            throw databaseError()
        }
    }

    private func prepare(_ sql: String) throws -> OpaquePointer? {
        guard let database else {
            throw NSError(domain: "TrackingBufferStore", code: 1, userInfo: [NSLocalizedDescriptionKey: "SQLite database is not open"])
        }

        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK else {
            throw databaseError()
        }
        return statement
    }

    private func bind(_ value: String, to index: Int32, in statement: OpaquePointer?) throws {
        let result = value.withCString { rawValue in
            sqlite3_bind_text(statement, index, rawValue, -1, sqliteTransientDestructor)
        }
        guard result == SQLITE_OK else {
            throw databaseError()
        }
    }

    private func bind(_ value: Int64, to index: Int32, in statement: OpaquePointer?) throws {
        guard sqlite3_bind_int64(statement, index, value) == SQLITE_OK else {
            throw databaseError()
        }
    }

    private func bind(_ value: Data, to index: Int32, in statement: OpaquePointer?) throws {
        let result = value.withUnsafeBytes { bytes in
            sqlite3_bind_blob(statement, index, bytes.baseAddress, Int32(value.count), sqliteTransientDestructor)
        }
        guard result == SQLITE_OK else {
            throw databaseError()
        }
    }

    private func stepExpectDone(_ statement: OpaquePointer?) throws {
        guard sqlite3_step(statement) == SQLITE_DONE else {
            throw databaseError()
        }
    }

    private func data(from statement: OpaquePointer?, column: Int32) throws -> Data {
        let length = Int(sqlite3_column_bytes(statement, column))
        guard let bytes = sqlite3_column_blob(statement, column), length > 0 else {
            return Data()
        }
        return Data(bytes: bytes, count: length)
    }

    private func databaseError() -> NSError {
        NSError(
            domain: "TrackingBufferStore",
            code: 2,
            userInfo: [NSLocalizedDescriptionKey: database.map(databaseErrorMessage(for:)) ?? "Unknown SQLite error"]
        )
    }

    private func databaseErrorMessage(for database: OpaquePointer) -> String {
        String(cString: sqlite3_errmsg(database))
    }
}
