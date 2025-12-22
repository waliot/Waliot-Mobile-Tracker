package com.websmithing.gpstracker2.repository.location

import android.location.Location
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_WEBSITE_URL
import com.websmithing.gpstracker2.util.CrcUtils
import com.websmithing.gpstracker2.util.NmeaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val settingsRepository: SettingsRepository
) : LocationRepository {

    private val _lastUploadStatus = MutableStateFlow<UploadStatus>(UploadStatus.Idle)
    override val lastUploadStatus: StateFlow<UploadStatus> = _lastUploadStatus.asStateFlow()

    private val dateFormatter = SimpleDateFormat("ddMMyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val timeFormatter = SimpleDateFormat("HHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private companion object {
        const val TAG = "LocationRepository"

        const val DEFAULT_HOST = "device.waliot.com"
        const val DEFAULT_PORT = 30032

        const val PROTOCOL_VERSION = "2.0"
        const val NO_VALUE = "NA"
        const val DEFAULT_PASSWORD = NO_VALUE

        const val SOCKET_TIMEOUT_MS = 30_000
    }

    override suspend fun resetUploadStatus() = withContext(Dispatchers.IO) {
        _lastUploadStatus.value = UploadStatus.Idle
    }

    override suspend fun uploadLocationData(username: String, location: Location): Boolean = withContext(Dispatchers.IO) {
        var success = false
        var errorMessage: String? = null
        try {
            Timber.tag(TAG).i("REPO-CRITICAL: Starting location upload process")
            val (host, port) = getServerAddress()

            Socket(host, port).use { socket ->
                socket.soTimeout = SOCKET_TIMEOUT_MS

                val output = socket.getOutputStream()
                val input = socket.getInputStream().bufferedReader()

                // Login
                val loginPacket = createPacket("#L#$PROTOCOL_VERSION;$username;$DEFAULT_PASSWORD")
                Timber.tag(TAG).d("Sending login packet: $loginPacket")
                output.write(loginPacket.toByteArray(Charsets.UTF_8))

                val loginResponse = input.readLine()
                if (loginResponse?.startsWith("#AL#1") != true) {
                    throw IllegalStateException("Login failed: $loginResponse")
                }

                // Data
                val dataPayload = buildPayload(location)
                val dataPacket = createPacket("#D#$dataPayload")
                Timber.tag(TAG).d("Sending data packet: $dataPacket")
                output.write(dataPacket.toByteArray(Charsets.UTF_8))

                val dataResponse = input.readLine()
                if (dataResponse?.startsWith("#AD#1") != true) {
                    throw IllegalStateException("Upload failed: $dataResponse")
                }

                success = true
            }
            Timber.tag(TAG).i("REPO-CRITICAL: Location upload process is done successfully")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Upload process error")
            errorMessage = e.localizedMessage ?: "Unknown upload error"
            success = false

        } finally {
            Timber.tag(TAG).d("Finally block: success=$success, errorMessage='$errorMessage'")
            _lastUploadStatus.value = if (success) UploadStatus.Success else UploadStatus.Failure(errorMessage)
        }

        success
    }

    private suspend fun getServerAddress(): Pair<String, Int> {
        var serverAddress = settingsRepository.getCurrentWebsiteUrl()
        if (serverAddress.isBlank()) {
            Timber.tag(TAG).w("Server address is blank. Using default...")
            serverAddress = DEFAULT_WEBSITE_URL
        }
        val parts = serverAddress.split(":")
        val host = parts.getOrNull(0) ?: DEFAULT_HOST
        val port = parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_PORT
        return host to port
    }

    private fun createPacket(message: String): String {
        val crc = CrcUtils.formatCrcToHex(CrcUtils.calculateCrc16(message.toByteArray(Charsets.UTF_8)))
        return "$message;$crc\r\n"
    }

    private fun buildPayload(location: Location): String {
        return listOf(
            dateFormatter.format(location.time),
            timeFormatter.format(location.time),
            NmeaUtils.latitudeToDdm(location.latitude, ";"),
            NmeaUtils.longitudeToDdm(location.longitude, ";"),
            (location.speed * 3.6).roundToInt(),
            location.bearing.roundToInt(),
            location.altitude.roundToInt(),
            location.extras?.getInt("satellites")?.takeIf { it != 0 } ?: NO_VALUE,
            location.extras?.getDouble("hdop")?.takeIf { it != 0.0 } ?: NO_VALUE,
            NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, // inputs, outputs, adc, iButton
            "accuracy:2:${location.accuracy},provider:3:${location.provider}"
        ).joinToString(";")
    }
}
