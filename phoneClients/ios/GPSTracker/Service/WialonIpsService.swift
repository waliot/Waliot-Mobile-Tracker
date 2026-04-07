//
//  WialonIpsService.swift
//  GpsTracker
//
//  Created by Ivan Muratov on 17.10.2025.
//

import Foundation
import Combine
import Network
import OSLog

enum WialonUploadError: LocalizedError, Equatable {
    case invalidConfiguration
    case serverRejected
    case timeout
    case offline
    case transport

    var failureReason: UploadFailureReason {
        switch self {
        case .invalidConfiguration:
            return .invalidConfiguration
        case .serverRejected:
            return .serverRejected
        case .timeout:
            return .timeout
        case .offline:
            return .offline
        case .transport:
            return .transport
        }
    }

    var errorDescription: String? {
        failureReason.localizedDescription
    }
}

final class WialonIpsService: APIServiceProtocol {
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "com.waliot.tracker", category: "WialonIPSService")

    private let settings: SettingsRepositoryProtocol

    private let defaultHost = "device.waliot.com"
    private let defaultPort: UInt16 = 30032
    private let protocolVersion = "2.0"
    private let noValue = "NA"
    private let defaultPassword = "NA"
    private let connectionTimeout: TimeInterval = 15

    init(settingsRepository: SettingsRepositoryProtocol) {
        self.settings = settingsRepository
    }

    // MARK: - APIServiceProtocol

    func uploadLocation(parameters: LocationAPIRequestParameters) -> AnyPublisher<APIResponse, Error> {
        let trackerIdentifier = sanitizeTrackerIdentifier(parameters.username, defaultValue: "")
        guard !trackerIdentifier.isEmpty else {
            return Fail(error: WialonUploadError.invalidConfiguration).eraseToAnyPublisher()
        }

        let target: UploadTarget
        do {
            target = try parseUploadServerAddress(
                settings.getUploadServer(),
                defaultHost: defaultHost,
                defaultPort: defaultPort
            )
        } catch {
            return Fail(error: WialonUploadError.invalidConfiguration).eraseToAnyPublisher()
        }

        let now = parameters.gps_timestamp
        let (dateStr, timeStr) = Self.makeDateTimeUTC(now)

        let lat = parameters.latitude
        let lon = parameters.longitude
        let latDMM = Self.decimalToDmm(lat, isLon: false)
        let latHem = lat >= 0 ? "N" : "S"
        let lonDMM = Self.decimalToDmm(lon, isLon: true)
        let lonHem = lon >= 0 ? "E" : "W"

        let speedKmh = Int(round(parameters.speed * 3.6))
        let course = Int(round(parameters.direction))
        let altitude = Int(round(parameters.altitude))
        let params = "accuracy:2:\(parameters.accuracy),provider:3:\(parameters.provider)"

        let dataPayload = [
            dateStr, timeStr,
            latDMM, latHem,
            lonDMM, lonHem,
            "\(speedKmh)",
            "\(course)",
            "\(altitude)",
            noValue,
            noValue,
            noValue,
            noValue,
            "",
            noValue,
            params
        ].joined(separator: ";")

        let loginPayload = "\(protocolVersion);\(trackerIdentifier);\(defaultPassword)"

        return Future<APIResponse, Error> { [logger, connectionTimeout] promise in
            let connection = NWConnection(
                host: NWEndpoint.Host(target.host),
                port: NWEndpoint.Port(rawValue: target.port)!,
                using: uploadParameters(for: target)
            )
            let queue = DispatchQueue(label: "com.waliot.tracker.wialon.upload", qos: .utility)
            let completionLock = NSLock()
            var didComplete = false

            func finish(_ result: Result<APIResponse, Error>) {
                completionLock.lock()
                guard !didComplete else {
                    completionLock.unlock()
                    return
                }
                didComplete = true
                completionLock.unlock()
                connection.cancel()
                promise(result)
            }

            let timeoutItem = DispatchWorkItem {
                logger.error("WialonIPS: upload timed out")
                finish(.failure(WialonUploadError.timeout))
            }

            queue.asyncAfter(deadline: .now() + connectionTimeout, execute: timeoutItem)

            func complete(_ result: Result<APIResponse, Error>) {
                timeoutItem.cancel()
                finish(result)
            }

            func fail(_ error: Error) {
                let mappedError = Self.mapUploadError(error)
                logger.error("WialonIPS: failed - \(mappedError.localizedDescription, privacy: .private)")
                complete(.failure(mappedError))
            }

            func sendPacket(_ type: String, payload: String, _ next: @escaping () -> Void) {
                let line = Self.createPacket(type: type, payload: payload)
                connection.send(content: line.data(using: .utf8), completion: .contentProcessed { sendError in
                    if let sendError {
                        fail(sendError)
                    } else {
                        next()
                    }
                })
            }

            func receiveLine(_ handler: @escaping (String) -> Void) {
                connection.receive(minimumIncompleteLength: 1, maximumLength: 4096) { data, _, isDone, receiveError in
                    if let receiveError {
                        fail(receiveError)
                        return
                    }

                    guard let data,
                          let line = String(data: data, encoding: .utf8)?
                            .trimmingCharacters(in: .whitespacesAndNewlines),
                          !line.isEmpty else {
                        if isDone {
                            fail(WialonUploadError.serverRejected)
                        } else {
                            receiveLine(handler)
                        }
                        return
                    }

                    handler(line)
                }
            }

            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    logger.debug("WialonIPS: connection ready")
                    sendPacket("L", payload: loginPayload) {
                        receiveLine { loginResponse in
                            guard loginResponse.hasPrefix("#AL#1") else {
                                fail(WialonUploadError.serverRejected)
                                return
                            }
                            sendPacket("D", payload: dataPayload) {
                                receiveLine { dataResponse in
                                    guard dataResponse.hasPrefix("#AD#1") else {
                                        fail(WialonUploadError.serverRejected)
                                        return
                                    }
                                    logger.notice("WialonIPS: upload OK (#AD#1)")
                                    complete(.success(APIResponse(status: "success", message: dataResponse)))
                                }
                            }
                        }
                    }
                case .failed(let error):
                    fail(error)
                case .waiting(let error):
                    fail(error)
                case .cancelled:
                    break
                default:
                    break
                }
            }

            connection.start(queue: queue)
        }
        .eraseToAnyPublisher()
    }

    // MARK: - Helpers

    private static func mapUploadError(_ error: Error) -> WialonUploadError {
        if let uploadError = error as? WialonUploadError {
            return uploadError
        }
        if error is UploadEndpointError {
            return .invalidConfiguration
        }
        if let urlError = error as? URLError {
            switch urlError.code {
            case .notConnectedToInternet, .networkConnectionLost, .cannotFindHost, .cannotConnectToHost, .dnsLookupFailed:
                return .offline
            case .timedOut:
                return .timeout
            default:
                return .transport
            }
        }
        if let nwError = error as? NWError {
            switch nwError {
            case .dns:
                return .offline
            case .posix(let posixError):
                switch posixError {
                case .ENETDOWN, .ENETUNREACH, .ENOTCONN, .ECONNABORTED, .ECONNRESET, .ECONNREFUSED, .EHOSTUNREACH:
                    return .offline
                case .ETIMEDOUT:
                    return .timeout
                default:
                    return .transport
                }
            case .tls:
                return .transport
            case .wifiAware:
                return .transport
            @unknown default:
                return .transport
            }
        }

        let nsError = error as NSError
        if nsError.domain == NSPOSIXErrorDomain {
            if nsError.code == ETIMEDOUT {
                return .timeout
            }
            let offlineCodes = [ENETDOWN, ENETUNREACH, ENOTCONN, ECONNABORTED, ECONNRESET, ECONNREFUSED, EHOSTUNREACH].map(Int.init)
            if offlineCodes.contains(nsError.code) {
                return .offline
            }
        }

        return .transport
    }

    private static func makeDateTimeUTC(_ date: Date) -> (String, String) {
        let timeZone = TimeZone(secondsFromGMT: 0)!
        let formatter = DateFormatter()
        formatter.timeZone = timeZone

        formatter.dateFormat = "ddMMyy"
        let dateString = formatter.string(from: date)
        formatter.dateFormat = "HHmmss"
        let timeString = formatter.string(from: date)
        return (dateString, timeString)
    }

    private static func decimalToDmm(_ coordinate: Double, isLon: Bool) -> String {
        let absValue = abs(coordinate)
        var degrees = Int(floor(absValue))
        var totalMinutes = Int(round((absValue - Double(degrees)) * 60.0 * 10_000.0))

        if totalMinutes >= 600_000 {
            degrees += 1
            totalMinutes = 0
        }

        let wholeMinutes = totalMinutes / 10_000
        let fractionalMinutes = totalMinutes % 10_000
        let format = isLon ? "%03d%02d.%04d" : "%02d%02d.%04d"
        return String(format: format, degrees, wholeMinutes, fractionalMinutes)
    }

    private static func createPacket(type: String, payload: String) -> String {
        let checksumSource = "\(payload);"
        let bytes = Array(checksumSource.utf8)
        let crc = crc16(bytes)
        let crcHex = String(format: "%04X", crc)
        return "#\(type)#\(checksumSource)\(crcHex)\r\n"
    }

    private static func crc16(_ data: [UInt8]) -> Int {
        var crc = 0
        for byte in data {
            let index = (crc ^ Int(byte)) & 0xFF
            crc = (crc >> 8) ^ table[index]
        }
        return crc & 0xFFFF
    }

    private static let table: [Int] = [
        0x0000,0xC0C1,0xC181,0x0140,0xC301,0x03C0,0x0280,0xC241,
        0xC601,0x06C0,0x0780,0xC741,0x0500,0xC5C1,0xC481,0x0440,
        0xCC01,0x0CC0,0x0D80,0xCD41,0x0F00,0xCFC1,0xCE81,0x0E40,
        0x0A00,0xCAC1,0xCB81,0x0B40,0xC901,0x09C0,0x0880,0xC841,
        0xD801,0x18C0,0x1980,0xD941,0x1B00,0xDBC1,0xDA81,0x1A40,
        0x1E00,0xDEC1,0xDF81,0x1F40,0xDD01,0x1DC0,0x1C80,0xDC41,
        0x1400,0xD4C1,0xD581,0x1540,0xD701,0x17C0,0x1680,0xD641,
        0xD201,0x12C0,0x1380,0xD341,0x1100,0xD1C1,0xD081,0x1040,
        0xF001,0x30C0,0x3180,0xF141,0x3300,0xF3C1,0xF281,0x3240,
        0x3600,0xF6C1,0xF781,0x3740,0xF501,0x35C0,0x3480,0xF441,
        0x3C00,0xFCC1,0xFD81,0x3D40,0xFF01,0x3FC0,0x3E80,0xFE41,
        0xFA01,0x3AC0,0x3B80,0xFB41,0x3900,0xF9C1,0xF881,0x3840,
        0x2800,0xE8C1,0xE981,0x2940,0xEB01,0x2BC0,0x2A80,0xEA41,
        0xEE01,0x2EC0,0x2F80,0xEF41,0x2D00,0xEDC1,0xEC81,0x2C40,
        0xE401,0x24C0,0x2580,0xE541,0x2700,0xE7C1,0xE681,0x2640,
        0x2200,0xE2C1,0xE381,0x2340,0xE101,0x21C0,0x2080,0xE041,
        0xA001,0x60C0,0x6180,0xA141,0x6300,0xA3C1,0xA281,0x6240,
        0x6600,0xA6C1,0xA781,0x6740,0xA501,0x65C0,0x6480,0xA441,
        0x6C00,0xACC1,0xAD81,0x6D40,0xAF01,0x6FC0,0x6E80,0xAE41,
        0xAA01,0x6AC0,0x6B80,0xAB41,0x6900,0xA9C1,0xA881,0x6840,
        0x7800,0xB8C1,0xB981,0x7940,0xBB01,0x7BC0,0x7A80,0xBA41,
        0xBE01,0x7EC0,0x7F80,0xBF41,0x7D00,0xBDC1,0xBC81,0x7C40,
        0xB401,0x74C0,0x7580,0xB541,0x7700,0xB7C1,0xB681,0x7640,
        0x7200,0xB2C1,0xB381,0x7340,0xB101,0x71C0,0x7080,0xB041,
        0x5000,0x90C1,0x9181,0x5140,0x9301,0x53C0,0x5280,0x9241,
        0x9601,0x56C0,0x5780,0x9741,0x5500,0x95C1,0x9481,0x5440,
        0x9C01,0x5CC0,0x5D80,0x9D41,0x5F00,0x9FC1,0x9E81,0x5E40,
        0x5A00,0x9AC1,0x9B81,0x5B40,0x9901,0x59C0,0x5880,0x9841,
        0x8801,0x48C0,0x4980,0x8941,0x4B00,0x8BC1,0x8A81,0x4A40,
        0x4E00,0x8EC1,0x8F81,0x4F40,0x8D01,0x4DC0,0x4C80,0x8C41,
        0x4400,0x84C1,0x8581,0x4540,0x8701,0x47C0,0x4680,0x8641,
        0x8201,0x42C0,0x4380,0x8341,0x4100,0x81C1,0x8081,0x4040
    ]
}
