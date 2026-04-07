import Foundation
import Combine
import Network
import Testing
@testable import GPSTracker

struct WialonIpsServiceTests {
    @Test
    func uploadLocationSendsExpectedLoginAndDataPackets() async throws {
        let server = try ScriptedTcpServer(responses: ["#AL#1", "#AD#1"])
        let port = try await server.start()
        defer { server.stop() }

        let settings = TestSettingsRepository()
        settings.uploadServer = "127.0.0.1:\(port)"
        let service = WialonIpsService(settingsRepository: settings)

        let response = try await firstValue(
            from: service.uploadLocation(
                parameters: makeUploadParameters(username: " 12345 ")
            )
        )

        #expect(response.status == "success")
        #expect(response.message == "#AD#1")

        try await server.waitForReceivedLineCount(2)
        let receivedLines = server.snapshotReceivedLines()
        #expect(receivedLines.count == 2)
        #expect(receivedLines[0] == "#L#2.0;12345;NA;5FEC")

        guard let (body, crc) = splitCRC(from: receivedLines[1]) else {
            Issue.record("Expected CRC suffix in data packet: \(receivedLines[1])")
            return
        }

        #expect(
            body ==
                "#D#121023;153959;5354.4926;N;02731.4499;E;0;0;300;NA;NA;NA;NA;;NA;accuracy:2:7.0,provider:3:gps"
        )
        #expect(crc.range(of: "^[0-9A-F]{4}$", options: .regularExpression) != nil)
    }

    @Test
    func uploadLocationFailsFastForBlankTrackerIdentifier() async {
        let service = WialonIpsService(settingsRepository: TestSettingsRepository())

        do {
            _ = try await firstValue(from: service.uploadLocation(parameters: makeUploadParameters(username: "   ")))
            Issue.record("Expected invalid configuration for blank tracker identifier")
        } catch let error as WialonUploadError {
            #expect(error == .invalidConfiguration)
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test
    func uploadLocationFailsFastForMalformedUploadServerAddress() async {
        let settings = TestSettingsRepository()
        settings.uploadServer = "https://tracker.example.com:443"
        let service = WialonIpsService(settingsRepository: settings)

        do {
            _ = try await firstValue(from: service.uploadLocation(parameters: makeUploadParameters()))
            Issue.record("Expected invalid configuration for malformed upload server")
        } catch let error as WialonUploadError {
            #expect(error == .invalidConfiguration)
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test
    func uploadLocationMapsConnectionRefusalToOffline() async throws {
        let settings = TestSettingsRepository()
        settings.uploadServer = "127.0.0.1:\(try await unusedLocalPort())"
        let service = WialonIpsService(settingsRepository: settings)

        do {
            _ = try await firstValue(from: service.uploadLocation(parameters: makeUploadParameters()))
            Issue.record("Expected offline error for refused localhost connection")
        } catch let error as WialonUploadError {
            #expect(error == .offline)
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test
    func uploadLocationReturnsServerRejectedWhenLoginFails() async throws {
        let server = try ScriptedTcpServer(responses: ["#AL#0"])
        let port = try await server.start()
        defer { server.stop() }

        let settings = TestSettingsRepository()
        settings.uploadServer = "127.0.0.1:\(port)"
        let service = WialonIpsService(settingsRepository: settings)

        do {
            _ = try await firstValue(from: service.uploadLocation(parameters: makeUploadParameters()))
            Issue.record("Expected server rejection when login response is not #AL#1")
        } catch let error as WialonUploadError {
            #expect(error == .serverRejected)
        } catch {
            Issue.record("Unexpected error: \(error)")
        }

        try await server.waitForReceivedLineCount(1)
        #expect(server.snapshotReceivedLines().count == 1)
    }

    @Test
    func uploadLocationReturnsServerRejectedWhenDataAckFails() async throws {
        let server = try ScriptedTcpServer(responses: ["#AL#1", "#AD#0"])
        let port = try await server.start()
        defer { server.stop() }

        let settings = TestSettingsRepository()
        settings.uploadServer = "127.0.0.1:\(port)"
        let service = WialonIpsService(settingsRepository: settings)

        do {
            _ = try await firstValue(from: service.uploadLocation(parameters: makeUploadParameters()))
            Issue.record("Expected server rejection when data response is not #AD#1")
        } catch let error as WialonUploadError {
            #expect(error == .serverRejected)
        } catch {
            Issue.record("Unexpected error: \(error)")
        }

        try await server.waitForReceivedLineCount(2)
        #expect(server.snapshotReceivedLines().count == 2)
    }
}

private enum WialonIpsServiceTestError: Error {
    case completedWithoutValue
    case timedOut
}

private final class ResumeGate: @unchecked Sendable {
    private let lock = NSLock()
    private var didResume = false

    func claim() -> Bool {
        lock.lock()
        defer { lock.unlock() }

        guard !didResume else { return false }
        didResume = true
        return true
    }
}

private final class FirstValueState {
    var cancellable: AnyCancellable?
    var didResume = false
}

private final class ScriptedTcpServer {
    private let listener: NWListener
    private let queue = DispatchQueue(label: "WialonIpsServiceTests.ScriptedTcpServer")
    private let lock = NSLock()
    private var connection: NWConnection?
    private var receivedLines: [String] = []
    private var pendingResponses: [String]
    private var receiveBuffer = ""

    init(responses: [String]) throws {
        self.listener = try NWListener(using: .tcp, on: .any)
        self.pendingResponses = responses
    }

    func start() async throws -> UInt16 {
        try await withCheckedThrowingContinuation { continuation in
            let resumeGate = ResumeGate()

            listener.stateUpdateHandler = { [weak self] state in
                switch state {
                case .ready:
                    guard let self, let port = self.listener.port?.rawValue else { return }
                    guard resumeGate.claim() else { return }
                    continuation.resume(returning: port)
                case .failed(let error):
                    guard resumeGate.claim() else { return }
                    continuation.resume(throwing: error)
                default:
                    break
                }
            }

            listener.newConnectionHandler = { [weak self] connection in
                self?.accept(connection)
            }
            listener.start(queue: queue)
        }
    }

    func stop() {
        connection?.cancel()
        listener.cancel()
    }

    func waitForReceivedLineCount(_ count: Int, timeoutNanoseconds: UInt64 = 2_000_000_000) async throws {
        let deadline = DispatchTime.now().uptimeNanoseconds + timeoutNanoseconds
        while DispatchTime.now().uptimeNanoseconds < deadline {
            if snapshotReceivedLines().count >= count {
                return
            }
            try await Task.sleep(nanoseconds: 20_000_000)
        }
        throw WialonIpsServiceTestError.timedOut
    }

    func snapshotReceivedLines() -> [String] {
        lock.lock()
        defer { lock.unlock() }
        return receivedLines
    }

    private func accept(_ connection: NWConnection) {
        self.connection = connection
        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                self?.receive(on: connection)
            default:
                break
            }
        }
        connection.start(queue: queue)
    }

    private func receive(on connection: NWConnection) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 4096) { [weak self] data, _, isComplete, error in
            guard let self else { return }

            if let data, !data.isEmpty {
                self.process(data, on: connection)
            }

            if error == nil && !isComplete {
                self.receive(on: connection)
            }
        }
    }

    private func process(_ data: Data, on connection: NWConnection) {
        receiveBuffer.append(String(decoding: data, as: UTF8.self))

        while let lineBreak = receiveBuffer.range(of: "\r\n") {
            let line = String(receiveBuffer[..<lineBreak.lowerBound])
            receiveBuffer.removeSubrange(..<lineBreak.upperBound)

            lock.lock()
            receivedLines.append(line)
            lock.unlock()

            guard !pendingResponses.isEmpty else { continue }
            let response = pendingResponses.removeFirst() + "\r\n"
            connection.send(content: response.data(using: .utf8), completion: .contentProcessed { _ in })
        }
    }
}

private func firstValue<P: Publisher>(from publisher: P) async throws -> P.Output where P.Failure == Error {
    let stateLock = NSLock()
    let state = FirstValueState()

    return try await withCheckedThrowingContinuation { continuation in
        state.cancellable = publisher.sink(
            receiveCompletion: { completion in
                stateLock.lock()
                defer { stateLock.unlock() }
                guard !state.didResume else { return }
                state.didResume = true
                state.cancellable?.cancel()
                state.cancellable = nil

                switch completion {
                case .finished:
                    continuation.resume(throwing: WialonIpsServiceTestError.completedWithoutValue)
                case .failure(let error):
                    continuation.resume(throwing: error)
                }
            },
            receiveValue: { value in
                stateLock.lock()
                defer { stateLock.unlock() }
                guard !state.didResume else { return }
                state.didResume = true
                state.cancellable?.cancel()
                state.cancellable = nil
                continuation.resume(returning: value)
            }
        )
    }
}

private func splitCRC(from packet: String) -> (String, String)? {
    guard let separator = packet.lastIndex(of: ";") else {
        return nil
    }
    let body = String(packet[..<separator])
    let crc = String(packet[packet.index(after: separator)...])
    return (body, crc)
}

private func makeUploadParameters(
    username: String = "12345",
    timestamp: Date = makeFixedUTCDate()
) -> LocationAPIRequestParameters {
    LocationAPIRequestParameters(
        username: username,
        sessionid: "session-1",
        appid: "app-1",
        latitude: 53.90821,
        longitude: 27.524165,
        speed: 0,
        direction: 0,
        distance: 0,
        gps_time: ISO8601DateFormatter().string(from: timestamp),
        gps_timestamp: timestamp,
        location_method: "gps",
        accuracy: 7,
        altitude: 300,
        provider: "gps",
        battery: 87
    )
}

private func makeFixedUTCDate() -> Date {
    var components = DateComponents()
    components.calendar = Calendar(identifier: .gregorian)
    components.timeZone = TimeZone(secondsFromGMT: 0)
    components.year = 2023
    components.month = 10
    components.day = 12
    components.hour = 15
    components.minute = 39
    components.second = 59
    return components.date ?? Date(timeIntervalSince1970: 1_697_124_399)
}

private func unusedLocalPort() async throws -> UInt16 {
    let server = try ScriptedTcpServer(responses: [])
    let port = try await server.start()
    server.stop()
    try await Task.sleep(nanoseconds: 100_000_000)
    return port
}
