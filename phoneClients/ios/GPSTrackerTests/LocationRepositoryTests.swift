import Combine
import Foundation
import Testing
@testable import GPSTracker

struct LocationRepositoryTests {
    @Test
    func uploadLocationForwardsParametersAndReturnsSuccess() async throws {
        let locationService = TestLocationService()
        let apiService = TestAPIService()
        apiService.nextResult = .success(APIResponse(status: "success", message: "ok"))
        let settings = TestSettingsRepository()
        let repository = LocationRepository(
            locationService: locationService,
            apiService: apiService,
            settingsRepository: settings
        )

        let parameters = makeRepositoryUploadParameters()
        let response = try await firstValue(from: repository.uploadLocation(parameters: parameters))

        #expect(response.status == "success")
        #expect(response.message == "ok")
        #expect(apiService.uploadedParameters.count == 1)
        #expect(apiService.uploadedParameters[0].username == parameters.username)
        #expect(apiService.uploadedParameters[0].latitude == parameters.latitude)
        #expect(apiService.uploadedParameters[0].longitude == parameters.longitude)
    }

    @Test
    func uploadLocationPropagatesApiFailure() async {
        let locationService = TestLocationService()
        let apiService = TestAPIService()
        apiService.nextResult = .failure(URLError(.timedOut))
        let settings = TestSettingsRepository()
        let repository = LocationRepository(
            locationService: locationService,
            apiService: apiService,
            settingsRepository: settings
        )

        do {
            _ = try await firstValue(from: repository.uploadLocation(parameters: makeRepositoryUploadParameters()))
            Issue.record("Expected uploadLocation to forward API failure")
        } catch let error as URLError {
            #expect(error.code == .timedOut)
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }
}

private enum LocationRepositoryTestError: Error {
    case completedWithoutValue
}

private final class LocationRepositoryFirstValueState {
    var cancellable: AnyCancellable?
    var didResume = false
}

private func makeRepositoryUploadParameters() -> LocationAPIRequestParameters {
    LocationAPIRequestParameters(
        username: "ABC123",
        sessionid: "session-1",
        appid: "app-1",
        latitude: 55.751244,
        longitude: 37.618423,
        speed: 12,
        direction: 45,
        distance: 250,
        gps_time: "2026-04-06T00:00:00Z",
        gps_timestamp: Date(timeIntervalSince1970: 1_775_433_600),
        location_method: "gps",
        accuracy: 7,
        altitude: 120,
        provider: "gps",
        battery: 88
    )
}

private func firstValue<P: Publisher>(from publisher: P) async throws -> P.Output where P.Failure == Error {
    let lock = NSLock()
    let state = LocationRepositoryFirstValueState()

    return try await withCheckedThrowingContinuation { continuation in
        state.cancellable = publisher.sink(
            receiveCompletion: { completion in
                lock.lock()
                defer { lock.unlock() }
                guard !state.didResume else { return }
                state.didResume = true
                state.cancellable?.cancel()
                state.cancellable = nil

                switch completion {
                case .finished:
                    continuation.resume(throwing: LocationRepositoryTestError.completedWithoutValue)
                case .failure(let error):
                    continuation.resume(throwing: error)
                }
            },
            receiveValue: { value in
                lock.lock()
                defer { lock.unlock() }
                guard !state.didResume else { return }
                state.didResume = true
                state.cancellable?.cancel()
                state.cancellable = nil
                continuation.resume(returning: value)
            }
        )
    }
}
