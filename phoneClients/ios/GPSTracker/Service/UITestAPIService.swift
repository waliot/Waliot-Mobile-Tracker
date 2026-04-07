import Foundation
import Combine

/// Test-only API shim activated via `UITEST_MODE` launch argument.
final class UITestAPIService: APIServiceProtocol {
    private enum UploadMode: String {
        case success
        case offline
        case timeout
        case serverRejected
    }

    func uploadLocation(parameters: LocationAPIRequestParameters) -> AnyPublisher<APIResponse, Error> {
        let mode = UploadMode(rawValue: ProcessInfo.processInfo.environment["UITEST_UPLOAD_MODE"] ?? "success") ?? .success

        switch mode {
        case .success:
            return Just(APIResponse(status: "success", message: "UITest upload successful"))
                .setFailureType(to: Error.self)
                .eraseToAnyPublisher()
        case .offline:
            return Fail(error: WialonUploadError.offline).eraseToAnyPublisher()
        case .timeout:
            return Fail(error: WialonUploadError.timeout).eraseToAnyPublisher()
        case .serverRejected:
            return Fail(error: WialonUploadError.serverRejected).eraseToAnyPublisher()
        }
    }
}
