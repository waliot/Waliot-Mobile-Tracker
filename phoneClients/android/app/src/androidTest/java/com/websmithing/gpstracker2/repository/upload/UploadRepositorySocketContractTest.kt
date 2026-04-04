package com.websmithing.gpstracker2.repository.upload

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class UploadRepositorySocketContractTest {

    @Test
    fun uploadData_sends_login_and_data_packets_to_local_backend() = runBlocking {
        val receivedLines = LinkedBlockingQueue<String>()

        ServerSocket(0).use { serverSocket ->
            val port = serverSocket.localPort
            val repository = UploadRepositoryImpl(
                settingsRepository = InstrumentedSettingsRepository(uploadServer = "127.0.0.1:$port"),
            )

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

            val success = repository.uploadData("waliot-emulator-01", createLocation())
            serverThread.join()

            assertTrue(success)
            val status = repository.lastUploadStatus.value
            assertTrue(status is UploadStatus.Success)
            status as UploadStatus.Success
            assertTrue(status.uploadedAtMillis > 0L)

            val loginPacket = receivedLines.take()
            val dataPacket = receivedLines.take()
            assertTrue(loginPacket.startsWith("#L#2.0;waliot-emulator-01;NA;"))
            assertTrue(dataPacket.startsWith("#D#"))
        }
    }

    private fun createLocation(): Location {
        return Location("gps").apply {
            latitude = 55.75
            longitude = 37.61
            altitude = 120.0
            accuracy = 5f
            bearing = 90f
            speed = 10f
            time = 1_700_000_000_000L
        }
    }
}

private class InstrumentedSettingsRepository(
    private val uploadServer: String,
) : SettingsRepository {

    override fun peekTrackingState(): Boolean = false

    override suspend fun getTrackingState(): Boolean = false

    override suspend fun setTrackingState(isTracking: Boolean) = Unit

    override suspend fun getTrackerIdentifier(): String = ""

    override suspend fun setTrackingIdentifier(trackerIdentifier: String) = Unit

    override suspend fun getUploadServer(): String = uploadServer

    override suspend fun setUploadServer(serverAddress: String) = Unit

    override suspend fun getUploadTimeInterval(): Int = SettingsRepository.DEFAULT_UPLOAD_TIME_INTERVAL

    override suspend fun setUploadTimeInterval(intervalMinutes: Int) = Unit

    override suspend fun getBufferTimeInterval(): Int = SettingsRepository.DEFAULT_BUFFER_TIME_INTERVAL

    override suspend fun setBufferTimeInterval(intervalMinutes: Int) = Unit

    override suspend fun getBufferDistanceInterval(): Int = SettingsRepository.DEFAULT_BUFFER_DISTANCE_INTERVAL

    override suspend fun setBufferDistanceInterval(intervalMeters: Int) = Unit

    override fun peekLanguage(): String = SettingsRepository.DEFAULT_LANGUAGE

    override suspend fun getLanguage(): String = SettingsRepository.DEFAULT_LANGUAGE

    override suspend fun setLanguage(language: String) = Unit

    override fun peekBatteryOptimizationWarningShown(): Boolean = false

    override suspend fun getBatteryOptimizationWarningShown(): Boolean = false

    override suspend fun setBatteryOptimizationWarningShown(shown: Boolean) = Unit
}
