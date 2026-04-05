package com.websmithing.gpstracker2.repository.upload

import android.location.Location
import android.os.Bundle
import com.websmithing.gpstracker2.testing.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
class UploadRepositoryImplTest {

    @Test
    fun `createPacket matches Wialon login example from specification`() {
        val repository = UploadRepositoryImpl(FakeSettingsRepository())

        val packet = repository.createPacket("L", "2.0;imei;NA")

        assertEquals("#L#2.0;imei;NA;A932\r\n", packet)
    }

    @Test
    fun `createPacket matches Wialon extended data example checksum from specification`() {
        val repository = UploadRepositoryImpl(FakeSettingsRepository())
        val payload = "231012;153959;5354.49260;N;02731.44990;E;0;0;300;7;1.1;0;0;1,0,0,0;NA;ign:1:1,dparam:2:3.14159265,tparam:3:lorem,iparam:1:-55,SOS:1:1"

        val packet = repository.createPacket("D", payload)

        assertEquals("#D#$payload;4BC3\r\n", packet)
    }

    @Test
    fun `isSuccessfulResponse accepts only exact success code`() {
        assertTrue(isSuccessfulResponse("#AL#1", "AL"))
        assertFalse(isSuccessfulResponse("#AL#10", "AL"))
        assertFalse(isSuccessfulResponse("#AL#01", "AL"))

        assertTrue(isSuccessfulResponse("#AD#1", "AD"))
        assertFalse(isSuccessfulResponse("#AD#10", "AD"))
        assertFalse(isSuccessfulResponse("#AD#16", "AD"))
    }

    @Test
    fun `redactTrackerIdentifier masks most of the identifier in logs`() {
        assertEquals("**", redactTrackerIdentifier("12"))
        assertEquals("1***5", redactTrackerIdentifier("12345"))
        assertEquals("wa***01", redactTrackerIdentifier("waliot-01"))
    }

    @Test
    fun `uploadData sends login and data packets and exposes success status`() = runTest {
        val receivedLines = LinkedBlockingQueue<String>()

        ServerSocket(0).use { serverSocket ->
            val port = serverSocket.localPort
            val settingsRepository = FakeSettingsRepository(uploadServer = "127.0.0.1:$port")
            val repository = UploadRepositoryImpl(settingsRepository)

            val serverThread = thread(start = true) {
                serverSocket.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val writer = socket.getOutputStream().bufferedWriter()

                    receivedLines.put(reader.readLine())
                    writer.write("#AL#1\r\n")
                    writer.flush()

                    receivedLines.put(reader.readLine())
                    writer.write("#AD#1\r\n")
                    writer.flush()
                }
            }

            val success = repository.uploadData(" 12345 ", mockLocation())
            serverThread.join()

            assertTrue(success)
            val status = repository.lastUploadStatus.value
            assertTrue(status is UploadStatus.Success)
            status as UploadStatus.Success
            assertTrue(status.uploadedAtMillis > 0L)

            val loginPacket = receivedLines.take()
            val dataPacket = receivedLines.take()
            assertEquals("#L#2.0;12345;NA;5FEC", loginPacket)
            assertTrue(dataPacket.startsWith("#D#"))
        }
    }

    @Test
    fun `uploadData fails fast when tracker identifier is blank`() = runTest {
        val settingsRepository = FakeSettingsRepository(uploadServer = "127.0.0.1:30032")
        val repository = UploadRepositoryImpl(settingsRepository)

        val success = repository.uploadData("   ", mockLocation())

        assertFalse(success)
        val status = repository.lastUploadStatus.value
        assertTrue(status is UploadStatus.Failure)
        status as UploadStatus.Failure
        assertTrue(status.errorMessage?.contains("Tracker identifier is blank") == true)
    }

    @Test
    fun `parseUploadServerAddress keeps defaults only for blank input and rejects malformed non blank values`() {
        assertEquals(
            UploadTarget(
                UploadRepositoryImpl.DEFAULT_HOST,
                UploadRepositoryImpl.DEFAULT_PORT,
                UploadTransportSecurity.Plain,
            ),
            parseUploadServerAddress("")
        )
        assertEquals(
            UploadTarget("127.0.0.1", 30032, UploadTransportSecurity.Plain),
            parseUploadServerAddress(" 127.0.0.1:30032 ")
        )
        assertFalse(isUploadServerValid("device.waliot.com:not-a-port"))
        assertFalse(isUploadServerValid("127.0.0.1:70000"))
    }

    @Test
    fun `parseUploadServerAddress supports opt-in tls scheme`() {
        assertEquals(
            UploadTarget("device.waliot.com", 30032, UploadTransportSecurity.Tls),
            parseUploadServerAddress("tls://device.waliot.com:30032")
        )
        assertEquals(
            UploadTarget("tracker.example.com", 443, UploadTransportSecurity.Tls),
            parseUploadServerAddress("ssl://tracker.example.com:443")
        )
        assertEquals(
            UploadTarget("127.0.0.1", 30032, UploadTransportSecurity.Plain),
            parseUploadServerAddress("tcp://127.0.0.1:30032")
        )
    }

    @Test
    fun `uploadData opens tls socket when upload server uses tls scheme`() = runTest {
        val socketFactory = RecordingSocketFactory()
        val repository = UploadRepositoryImpl(
            settingsRepository = FakeSettingsRepository(uploadServer = "tls://tracker.example.com:443"),
            socketFactory = socketFactory,
        )

        val success = repository.uploadData("12345", mockLocation())

        assertTrue(success)
        assertEquals(
            listOf(UploadTarget("tracker.example.com", 443, UploadTransportSecurity.Tls)),
            socketFactory.requestedTargets
        )
        assertEquals(InetSocketAddress("tracker.example.com", 443), socketFactory.socket.connectedEndpoint)
        assertTrue(socketFactory.socket.writtenPayload().contains("#L#2.0;12345;NA;"))
        assertTrue(socketFactory.socket.writtenPayload().contains("#D#"))
    }

    @Test
    fun `uploadData reports offline status when network is unavailable`() = runTest {
        val repository = UploadRepositoryImpl(
            settingsRepository = FakeSettingsRepository(uploadServer = "device.waliot.com:30032"),
            socketFactory = RecordingSocketFactory(),
            isNetworkAvailable = { false },
        )

        val success = repository.uploadData("12345", mockLocation())

        assertFalse(success)
        assertTrue(repository.lastUploadStatus.value is UploadStatus.Offline)
    }

    @Test
    fun `uploadData fails for unsupported upload server scheme`() = runTest {
        val repository = UploadRepositoryImpl(
            settingsRepository = FakeSettingsRepository(uploadServer = "https://tracker.example.com:443"),
        )

        val success = repository.uploadData("12345", mockLocation())

        assertFalse(success)
        val status = repository.lastUploadStatus.value
        assertTrue(status is UploadStatus.Failure)
        status as UploadStatus.Failure
        assertTrue(status.errorMessage?.contains("Unsupported upload server scheme") == true)
    }

    @Test
    fun `uploadData exposes failure status when backend rejects login`() = runTest {
        ServerSocket(0).use { serverSocket ->
            val port = serverSocket.localPort
            val settingsRepository = FakeSettingsRepository(uploadServer = "127.0.0.1:$port")
            val repository = UploadRepositoryImpl(settingsRepository)

            val serverThread = thread(start = true) {
                serverSocket.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val writer = socket.getOutputStream().bufferedWriter()

                    reader.readLine()
                    writer.write("#AL#0\r\n")
                    writer.flush()
                }
            }

            val success = repository.uploadData("12345", mockLocation())
            serverThread.join()

            assertFalse(success)
            val status = repository.lastUploadStatus.value
            assertTrue(status is UploadStatus.Failure)
            status as UploadStatus.Failure
            assertTrue(status.errorMessage?.contains("Login failed") == true)
        }
    }

    @Test
    fun `uploadData treats login checksum error as failure`() = runTest {
        ServerSocket(0).use { serverSocket ->
            val port = serverSocket.localPort
            val settingsRepository = FakeSettingsRepository(uploadServer = "127.0.0.1:$port")
            val repository = UploadRepositoryImpl(settingsRepository)

            val serverThread = thread(start = true) {
                serverSocket.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val writer = socket.getOutputStream().bufferedWriter()

                    reader.readLine()
                    writer.write("#AL#10\r\n")
                    writer.flush()
                }
            }

            val success = repository.uploadData("12345", mockLocation())
            serverThread.join()

            assertFalse(success)
            val status = repository.lastUploadStatus.value
            assertTrue(status is UploadStatus.Failure)
            status as UploadStatus.Failure
            assertTrue(status.errorMessage?.contains("Login failed") == true)
        }
    }

    @Test
    fun `uploadData treats data validation error as failure`() = runTest {
        ServerSocket(0).use { serverSocket ->
            val port = serverSocket.localPort
            val settingsRepository = FakeSettingsRepository(uploadServer = "127.0.0.1:$port")
            val repository = UploadRepositoryImpl(settingsRepository)

            val serverThread = thread(start = true) {
                serverSocket.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val writer = socket.getOutputStream().bufferedWriter()

                    reader.readLine()
                    writer.write("#AL#1\r\n")
                    writer.flush()

                    reader.readLine()
                    writer.write("#AD#10\r\n")
                    writer.flush()
                }
            }

            val success = repository.uploadData("12345", mockLocation())
            serverThread.join()

            assertFalse(success)
            val status = repository.lastUploadStatus.value
            assertTrue(status is UploadStatus.Failure)
            status as UploadStatus.Failure
            assertTrue(status.errorMessage?.contains("Upload failed") == true)
        }
    }

    @Test
    fun `buildPayload uses protocol missing-value semantics for absent optional fields`() {
        val repository = UploadRepositoryImpl(FakeSettingsRepository())

        val payload = repository.buildPayload(
            mockLocation(
                hasSpeed = false,
                hasBearing = false,
                hasAltitude = false,
                satellites = null,
                hdop = null,
            )
        )

        val fields = payload.split(";")
        assertEquals(16, fields.size)
        assertEquals("NA", fields[6])
        assertEquals("NA", fields[7])
        assertEquals("NA", fields[8])
        assertEquals("NA", fields[9])
        assertEquals("NA", fields[10])
        assertEquals("NA", fields[11])
        assertEquals("NA", fields[12])
        assertEquals("", fields[13])
        assertEquals("NA", fields[14])
    }

    private fun mockLocation(
        hasSpeed: Boolean = true,
        hasBearing: Boolean = true,
        hasAltitude: Boolean = true,
        satellites: Int? = 8,
        hdop: Double? = 0.9,
    ): Location {
        val extras = mock<Bundle>()
        whenever(extras.getInt("satellites")).thenReturn(satellites ?: 0)
        whenever(extras.getDouble("hdop")).thenReturn(hdop ?: 0.0)

        val location = mock<Location>()
        whenever(location.time).thenReturn(1_700_000_000_000L)
        whenever(location.latitude).thenReturn(55.75)
        whenever(location.longitude).thenReturn(37.61)
        whenever(location.hasSpeed()).thenReturn(hasSpeed)
        whenever(location.speed).thenReturn(10f)
        whenever(location.hasBearing()).thenReturn(hasBearing)
        whenever(location.bearing).thenReturn(90f)
        whenever(location.hasAltitude()).thenReturn(hasAltitude)
        whenever(location.altitude).thenReturn(120.0)
        whenever(location.accuracy).thenReturn(5f)
        whenever(location.provider).thenReturn("gps")
        whenever(location.extras).thenReturn(extras)
        return location
    }

    private class RecordingSocketFactory : UploadSocketFactory {
        val requestedTargets = mutableListOf<UploadTarget>()
        val socket = FakeSocket()

        override fun open(target: UploadTarget): Socket {
            requestedTargets += target
            return socket
        }
    }

    private class FakeSocket : Socket() {
        private val input = ByteArrayInputStream("#AL#1\r\n#AD#1\r\n".toByteArray())
        private val output = ByteArrayOutputStream()
        var connectedEndpoint: InetSocketAddress? = null
            private set
        private var readTimeout: Int = 0

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            connectedEndpoint = endpoint as InetSocketAddress
        }

        override fun getInputStream() = input

        override fun getOutputStream() = output

        override fun setSoTimeout(timeout: Int) {
            readTimeout = timeout
        }

        override fun getSoTimeout(): Int = readTimeout

        fun writtenPayload(): String = output.toString(Charsets.UTF_8.name())
    }
}
