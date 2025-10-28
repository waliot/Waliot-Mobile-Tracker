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
import CoreLocation

final class WialonIpsService: APIServiceProtocol {
    
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "com.waliot.tracker", category: "WialonIPSService")

    private let settings: SettingsRepositoryProtocol

    private let defaultHost = "device.waliot.com"
    private let defaultPort: UInt16 = 30032
    private let protocolVersion = "2.0"
    private let noValue = "NA"
    private let defaultPassword = "NA"

    init(settingsRepository: SettingsRepositoryProtocol) {
        self.settings = settingsRepository
    }

    // MARK: - APIServiceProtocol

    func uploadLocation(parameters: LocationAPIRequestParameters) -> AnyPublisher<APIResponse, Error> {
        let (host, port) = parseHostPort(from: settings.getServerUrl())

        let now = parameters.gps_timestamp
        let (dateStr, timeStr) = Self.makeDateTimeUTC(now)
        
        let lat = parameters.latitude
        let lon = parameters.longitude
        let latDMM = Self.decimalToDmm(lat, isLon: false)
        let latHem = lat >= 0 ? "N" : "S"
        let lonDMM = Self.decimalToDmm(lon, isLon: true)
        let lonHem = lon >= 0 ? "E" : "W"
        
        let speedKmh = Int(round((parameters.speed) * 3.6))
        let course = Int(round(parameters.direction))
        let altitude = Int(round(parameters.altitude))
        
        let acc = Int(round(parameters.accuracy))
        let provider = parameters.provider
        let params = "accuracy:2:\(acc),provider:3:\(provider)"

        let dataPayload = [
            dateStr, timeStr,            // DDMMYY ; HHMMSS (UTC)
            latDMM, latHem,              // широта
            lonDMM, lonHem,              // долгота
            "\(speedKmh)",               // скорость (км/ч)
            "\(course)",                 // курс
            "\(altitude)",               // высота (м)
            noValue,                     // спутники
            noValue,                     // HDOP
            noValue, noValue,            // inputs, outputs
            noValue,                     // ADC
            noValue,                     // iButton
            params                       // params
        ].joined(separator: ";")

        let loginMessage = "#L#\(protocolVersion);\(settings.getUsername());\(defaultPassword)"
        let dataMessage  = "#D#\(dataPayload)"

        return Future<APIResponse, Error> { [logger] promise in
            let connection = NWConnection(host: NWEndpoint.Host(host),
                                          port: NWEndpoint.Port(rawValue: port)!,
                                          using: .tcp)

            func fail(_ error: Error) {
                logger.error("WialonIPS: failed — \(error.localizedDescription, privacy: .public)")
                connection.cancel()
                promise(.failure(error))
            }

            func sendLine(_ text: String, _ next: @escaping () -> Void) {
                let withCRC = Self.appendCRC(to: text) + "\r\n"
                connection.send(content: withCRC.data(using: .utf8), completion: .contentProcessed { sendError in
                    if let sendError = sendError {
                        fail(sendError)
                    } else {
                        next()
                    }
                })
            }

            func receiveLine(_ handler: @escaping (String) -> Void) {
                connection.receive(minimumIncompleteLength: 1, maximumLength: 4096) { data, _, isDone, recvError in
                    if let recvError = recvError {
                        fail(recvError)
                        return
                    }
                    guard let data = data, let line = String(data: data, encoding: .utf8)?
                            .trimmingCharacters(in: .whitespacesAndNewlines), !line.isEmpty else {
                        if isDone {
                            fail(APIError.invalidResponse)
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
                    logger.debug("WialonIPS: connection ready to \(host):\(port)")
                    // 1) Login
                    sendLine(loginMessage) {
                        // 2) Receive #AL#1
                        receiveLine { resp in
                            guard resp.hasPrefix("#AL#1") else {
                                fail(APIError.serverError(statusCode: -1))
                                return
                            }
                            // 3) Send data
                            sendLine(dataMessage) {
                                // 4) Receive #AD#1
                                receiveLine { dataResp in
                                    guard dataResp.hasPrefix("#AD#1") else {
                                        fail(APIError.serverError(statusCode: -2))
                                        return
                                    }
                                    logger.notice("WialonIPS: upload OK (#AD#1)")
                                    connection.cancel()
                                    promise(.success(APIResponse(status: "success", message: dataResp)))
                                }
                            }
                        }
                    }
                case .failed(let e):
                    fail(e)
                case .cancelled:
                    break
                default:
                    break
                }
            }

            connection.start(queue: .global(qos: .utility))
        }
        .eraseToAnyPublisher()
    }

    // MARK: - Helpers

    private func parseHostPort(from raw: String?) -> (String, UInt16) {
        let s = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !s.isEmpty else { return (defaultHost, defaultPort) }
        let parts = s.split(separator: ":", maxSplits: 1).map(String.init)
        let host = parts.first?.isEmpty == false ? parts[0] : defaultHost
        let port = parts.count > 1 ? (UInt16(parts[1]) ?? defaultPort) : defaultPort
        return (host, port)
    }

    private static func makeDateTimeUTC(_ date: Date) -> (String, String) {
        let tz = TimeZone(secondsFromGMT: 0)!
        let df = DateFormatter()
        df.timeZone = tz

        df.dateFormat = "ddMMyy"
        let d = df.string(from: date)
        df.dateFormat = "HHmmss"
        let t = df.string(from: date)
        return (d, t)
    }

    private static func decimalToDmm(_ coordinate: Double, isLon: Bool) -> String {
        let absVal = abs(coordinate)
        let degrees = Int(absVal)
        let minutes = (absVal - Double(degrees)) * 60.0
        let dmm = Double(degrees) * 100.0 + minutes
        let fmt = isLon ? "%09.5f" : "%08.5f"
        return String(format: fmt, dmm)
    }

    private static func appendCRC(to message: String) -> String {
        let bytes = Array(message.utf8)
        let crc = crc16(bytes)
        let hi = (crc >> 8) & 0xFF
        let lo = crc & 0xFF
        let crcHex = String(format: "%02X%02X", hi, lo)
        return message + ";\(crcHex)"
    }

    private static func crc16(_ data: [UInt8]) -> Int {
        var crc = 0
        for b in data {
            let index = (crc ^ Int(b)) & 0xFF
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
