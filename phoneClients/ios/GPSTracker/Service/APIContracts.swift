import Foundation
import Combine

/// Protocol defining the interface for upload communication.
protocol APIServiceProtocol {
    /// Uploads location data to the tracking server.
    ///
    /// - Parameter parameters: The location data and metadata to send
    /// - Returns: A publisher that emits upload success or failure
    func uploadLocation(parameters: LocationAPIRequestParameters) -> AnyPublisher<APIResponse, Error>
}

/// Errors specific to upload operations.
enum APIError: Error, LocalizedError {
    case invalidResponse
    case serverError(statusCode: Int)
    case networkError(URLError)
    case encodingError(Error)

    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return String(localized: "api.error.invalidResponse")
        case .serverError(let statusCode):
            return String.localizedStringWithFormat(String(localized: "api.error.serverError"), statusCode)
        case .networkError:
            return String(localized: "api.error.networkError")
        case .encodingError:
            return String(localized: "api.error.encodingError")
        }
    }
}
