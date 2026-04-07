import Testing
@testable import GPSTracker

struct UploadEndpointTests {
    @Test
    func blankAddressFallsBackToDefaultTarget() throws {
        let endpoint = try parseUploadServerAddress("", defaultHost: "device.waliot.com", defaultPort: 30032)

        #expect(endpoint.host == "device.waliot.com")
        #expect(endpoint.port == 30032)
        #expect(endpoint.transportSecurity == .plain)
    }

    @Test
    func parsesPlainTcpEndpointWithoutScheme() throws {
        let endpoint = try parseUploadServerAddress("example.com:30032", defaultHost: "fallback", defaultPort: 30032)

        #expect(endpoint.host == "example.com")
        #expect(endpoint.port == 30032)
        #expect(endpoint.transportSecurity == .plain)
    }

    @Test
    func parsesTlsEndpoint() throws {
        let endpoint = try parseUploadServerAddress("tls://secure.example.com:443", defaultHost: "fallback", defaultPort: 30032)

        #expect(endpoint.host == "secure.example.com")
        #expect(endpoint.port == 443)
        #expect(endpoint.transportSecurity == .tls)
    }

    @Test
    func parsesTcpAliasAndDefaultPort() throws {
        let endpoint = try parseUploadServerAddress("tcp://127.0.0.1", defaultHost: "fallback", defaultPort: 30032)

        #expect(endpoint.host == "127.0.0.1")
        #expect(endpoint.port == 30032)
        #expect(endpoint.transportSecurity == .plain)
    }

    @Test
    func parsesSslAliasAsTls() throws {
        let endpoint = try parseUploadServerAddress("ssl://secure.example.com:443", defaultHost: "fallback", defaultPort: 30032)

        #expect(endpoint.host == "secure.example.com")
        #expect(endpoint.port == 443)
        #expect(endpoint.transportSecurity == .tls)
    }

    @Test
    func rejectsAddressesWithPathComponents() {
        #expect(throws: UploadEndpointError.invalidAddress) {
            try parseUploadServerAddress("example.com:30032/path", defaultHost: "fallback", defaultPort: 30032)
        }
    }

    @Test
    func rejectsUnsupportedSchemeAndInvalidPort() {
        #expect(throws: UploadEndpointError.invalidAddress) {
            try parseUploadServerAddress("https://tracker.example.com:443", defaultHost: "fallback", defaultPort: 30032)
        }
        #expect(throws: UploadEndpointError.invalidAddress) {
            try parseUploadServerAddress("example.com:not-a-port", defaultHost: "fallback", defaultPort: 30032)
        }
        #expect(throws: UploadEndpointError.invalidAddress) {
            try parseUploadServerAddress("127.0.0.1:70000", defaultHost: "fallback", defaultPort: 30032)
        }
    }

    @Test
    func uploadParametersMatchRequestedTransportSecurity() throws {
        let plain = try parseUploadServerAddress("device.waliot.com:30032", defaultHost: "fallback", defaultPort: 30032)
        let tls = try parseUploadServerAddress("tls://tracker.example.com:443", defaultHost: "fallback", defaultPort: 30032)

        #expect(uploadParameters(for: plain).requiredLocalEndpoint == nil)
        #expect(uploadParameters(for: plain).defaultProtocolStack.applicationProtocols.isEmpty)
        #expect(uploadParameters(for: tls).defaultProtocolStack.internetProtocol != nil)
    }
}
