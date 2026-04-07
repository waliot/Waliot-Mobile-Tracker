import Foundation
import Network

enum UploadTransportSecurity: Equatable {
    case plain
    case tls
}

struct UploadTarget: Equatable {
    let host: String
    let port: UInt16
    let transportSecurity: UploadTransportSecurity
}

enum UploadEndpointError: Error, Equatable {
    case invalidAddress
}

func parseUploadServerAddress(
    _ rawAddress: String,
    defaultHost: String = "device.waliot.com",
    defaultPort: UInt16 = 30032
) throws -> UploadTarget {
    let trimmed = sanitizeSingleLineInput(rawAddress).trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty {
        return UploadTarget(host: defaultHost, port: defaultPort, transportSecurity: .plain)
    }

    let schemeSeparator = trimmed.range(of: "://")
    let scheme = schemeSeparator.map { String(trimmed[..<$0.lowerBound]).lowercased() }
    let addressPart = schemeSeparator.map { String(trimmed[$0.upperBound...]) } ?? trimmed

    guard !addressPart.isEmpty,
          !addressPart.contains(where: { $0.isWhitespace || $0 == "/" || $0 == "?" || $0 == "#" })
    else {
        throw UploadEndpointError.invalidAddress
    }

    let transportSecurity: UploadTransportSecurity
    switch scheme {
    case nil, "tcp":
        transportSecurity = .plain
    case "tls", "ssl":
        transportSecurity = .tls
    default:
        throw UploadEndpointError.invalidAddress
    }

    let parts = addressPart.split(separator: ":", maxSplits: 1, omittingEmptySubsequences: false).map(String.init)
    guard let host = parts.first, !host.isEmpty else {
        throw UploadEndpointError.invalidAddress
    }

    let port: UInt16
    if parts.count == 2 {
        guard let parsedPort = UInt16(parts[1]), parsedPort > 0 else {
            throw UploadEndpointError.invalidAddress
        }
        port = parsedPort
    } else {
        port = defaultPort
    }

    return UploadTarget(host: host, port: port, transportSecurity: transportSecurity)
}

func isUploadServerAddressValid(_ address: String) -> Bool {
    let trimmed = sanitizeSingleLineInput(address).trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else {
        return false
    }
    return (try? parseUploadServerAddress(trimmed)) != nil
}

func uploadParameters(for target: UploadTarget) -> NWParameters {
    switch target.transportSecurity {
    case .plain:
        return .tcp
    case .tls:
        return .tls
    }
}
