package com.websmithing.gpstracker2.repository.upload

import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_SERVER
import com.websmithing.gpstracker2.util.CrcUtils
import com.websmithing.gpstracker2.util.NmeaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.UnknownHostException
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.math.roundToInt

internal enum class UploadTransportSecurity {
    Plain,
    Tls,
}

internal data class UploadTarget(
    val host: String,
    val port: Int,
    val transportSecurity: UploadTransportSecurity = UploadTransportSecurity.Plain,
)

internal interface UploadSocketFactory {
    fun open(target: UploadTarget): Socket
}

internal object DefaultUploadSocketFactory : UploadSocketFactory {
    override fun open(target: UploadTarget): Socket {
        return when (target.transportSecurity) {
            UploadTransportSecurity.Plain -> Socket()
            UploadTransportSecurity.Tls -> (SSLSocketFactory.getDefault().createSocket() as SSLSocket).apply {
                useClientMode = true
                sslParameters = SSLParameters().apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                }
            }
        }
    }
}

internal fun parseResponseCode(response: String?, packetType: String): String? {
    val prefix = "#$packetType#"
    return response?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
}

internal fun isSuccessfulResponse(response: String?, packetType: String): Boolean {
    return parseResponseCode(response, packetType) == "1"
}

internal fun parseUploadServerAddress(serverAddress: String): UploadTarget {
    val rawAddress = serverAddress.trim()
    if (rawAddress.isBlank()) {
        return UploadTarget(
            host = UploadRepositoryImpl.DEFAULT_HOST,
            port = UploadRepositoryImpl.DEFAULT_PORT,
        )
    }

    val schemeSeparator = rawAddress.indexOf("://")
    val scheme = if (schemeSeparator >= 0) {
        rawAddress.substring(0, schemeSeparator).lowercase(Locale.US)
    } else {
        null
    }
    val addressPart = if (schemeSeparator >= 0) {
        rawAddress.substring(schemeSeparator + 3)
    } else {
        rawAddress
    }

    require(addressPart.isNotBlank()) { "Upload server host is missing" }
    require(addressPart.none(Char::isWhitespace)) { "Upload server cannot contain whitespace" }
    require(addressPart.none { it == '/' || it == '?' || it == '#' }) {
        "Upload server cannot contain path or query components"
    }

    val transportSecurity = when (scheme) {
        null, "tcp" -> UploadTransportSecurity.Plain
        "tls", "ssl" -> UploadTransportSecurity.Tls
        else -> throw IllegalArgumentException("Unsupported upload server scheme: $scheme")
    }

    val parts = addressPart.split(":", limit = 2)
    val host = parts.getOrNull(0)?.trim().orEmpty()
    require(host.isNotBlank()) { "Upload server host is missing" }

    val port = if (parts.size == 2) {
        val rawPort = parts[1].trim()
        require(rawPort.isNotBlank()) { "Upload server port is missing" }
        rawPort.toIntOrNull()
            ?.takeIf { it in 1..65535 }
            ?: throw IllegalArgumentException("Upload server port is invalid")
    } else {
        UploadRepositoryImpl.DEFAULT_PORT
    }

    return UploadTarget(
        host = host,
        port = port,
        transportSecurity = transportSecurity,
    )
}

internal fun isUploadServerValid(serverAddress: String): Boolean {
    val normalized = serverAddress.trim()
    if (normalized.isBlank()) {
        return false
    }

    return runCatching { parseUploadServerAddress(normalized) }.isSuccess
}

internal fun sanitizeTrackerIdentifier(trackerIdentifier: String): String {
    return trackerIdentifier.trim().also { sanitized ->
        require(sanitized.isNotEmpty()) { "Tracker identifier is blank" }
        require(sanitized.none(Char::isWhitespace)) { "Tracker identifier cannot contain whitespace" }
        require(';' !in sanitized) { "Tracker identifier cannot contain ';'" }
        require('\r' !in sanitized && '\n' !in sanitized) { "Tracker identifier cannot contain line breaks" }
    }
}

internal fun redactTrackerIdentifier(trackerIdentifier: String): String {
    return when {
        trackerIdentifier.length <= 2 -> "**"
        trackerIdentifier.length <= 6 -> "${trackerIdentifier.first()}***${trackerIdentifier.last()}"
        else -> "${trackerIdentifier.take(2)}***${trackerIdentifier.takeLast(2)}"
    }
}

@Singleton
class UploadRepositoryImpl internal constructor(
    private val settingsRepository: SettingsRepository,
    private val socketFactory: UploadSocketFactory,
    private val isNetworkAvailable: () -> Boolean,
) : UploadRepository {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ) : this(
        settingsRepository = settingsRepository,
        socketFactory = DefaultUploadSocketFactory,
        isNetworkAvailable = { context.hasNetworkConnectivity() },
    )

    internal constructor(settingsRepository: SettingsRepository) : this(
        settingsRepository = settingsRepository,
        socketFactory = DefaultUploadSocketFactory,
        isNetworkAvailable = { true },
    )

    internal constructor(
        settingsRepository: SettingsRepository,
        socketFactory: UploadSocketFactory,
    ) : this(
        settingsRepository = settingsRepository,
        socketFactory = socketFactory,
        isNetworkAvailable = { true },
    )

    private val _lastUploadStatus = MutableStateFlow<UploadStatus>(UploadStatus.Idle)
    override val lastUploadStatus: StateFlow<UploadStatus> = _lastUploadStatus.asStateFlow()

    private val dateFormatter = SimpleDateFormat("ddMMyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val timeFormatter = SimpleDateFormat("HHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    internal companion object {
        const val TAG = "UploadRepository"

        const val DEFAULT_HOST = "device.waliot.com"
        const val DEFAULT_PORT = 30032
        const val CONNECT_TIMEOUT = 5_000
        const val SEND_TIMEOUT = 30_000

        const val PROTOCOL_VERSION = "2.0"
        const val NO_VALUE = "NA"
        const val DEFAULT_PASSWORD = NO_VALUE
    }

    override suspend fun resetUploadStatus() = withContext(Dispatchers.IO) {
        _lastUploadStatus.value = UploadStatus.Idle
    }

    override suspend fun uploadData(trackerIdentifier: String, location: Location): Boolean = withContext(Dispatchers.IO) {
        var success = false
        var errorMessage: String? = null
        var offline = false
        val uploadAttemptedAtMillis = System.currentTimeMillis()
        try {
            val sanitizedTrackerIdentifier = sanitizeTrackerIdentifier(trackerIdentifier)
            val target = getServerAddress()
            if (!isNetworkAvailable()) {
                offline = true
                Timber.tag(TAG).w("Skipping location upload because no active network is available")
                return@withContext false
            }
            Timber.tag(TAG).i(
                "Starting location upload to %s:%s over %s for tracker %s",
                target.host,
                target.port,
                target.transportSecurity.name.lowercase(Locale.US),
                redactTrackerIdentifier(sanitizedTrackerIdentifier),
            )

            socketFactory.open(target).use { socket ->
                socket.connect(InetSocketAddress(target.host, target.port), CONNECT_TIMEOUT)
                socket.soTimeout = SEND_TIMEOUT
                if (socket is SSLSocket) {
                    socket.startHandshake()
                }

                val output = socket.getOutputStream()
                val input = socket.getInputStream().bufferedReader()

                // Login
                val loginPayload = "$PROTOCOL_VERSION;$sanitizedTrackerIdentifier;$DEFAULT_PASSWORD"
                val loginPacket = createPacket("L", loginPayload)
                Timber.tag(TAG).d("Sending login packet")
                output.write(loginPacket.toByteArray(Charsets.UTF_8))
                output.flush()

                val loginResponse = input.readLine()
                Timber.tag(TAG).d("Login response code: %s", parseResponseCode(loginResponse, "AL") ?: "invalid")
                if (!isSuccessfulResponse(loginResponse, "AL")) {
                    throw IllegalStateException("Login failed: $loginResponse")
                }

                // Data
                val dataPayload = buildPayload(location)
                val dataPacket = createPacket("D", dataPayload)
                Timber.tag(TAG).d("Sending data packet")
                output.write(dataPacket.toByteArray(Charsets.UTF_8))
                output.flush()

                val dataResponse = input.readLine()
                Timber.tag(TAG).d("Data response code: %s", parseResponseCode(dataResponse, "AD") ?: "invalid")
                if (!isSuccessfulResponse(dataResponse, "AD")) {
                    throw IllegalStateException("Upload failed: $dataResponse")
                }

                success = true
            }
            Timber.tag(TAG).i("Location upload completed successfully")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Upload process error")
            errorMessage = e.localizedMessage ?: "—"
            offline = e is UnknownHostException || e is NoRouteToHostException
            success = false

        } finally {
            Timber.tag(TAG).d("Finally block: success=$success, errorMessage='$errorMessage'")
            _lastUploadStatus.value = when {
                success -> UploadStatus.Success(uploadAttemptedAtMillis)
                offline -> UploadStatus.Offline
                else -> UploadStatus.Failure(errorMessage)
            }
        }

        success
    }

    private suspend fun getServerAddress(): UploadTarget {
        var serverAddress = settingsRepository.getUploadServer()
        if (serverAddress.isBlank()) {
            Timber.tag(TAG).w("Server address is blank. Using default...")
            serverAddress = DEFAULT_UPLOAD_SERVER
        }
        return parseUploadServerAddress(serverAddress)
    }

    internal fun createPacket(packetType: String, payload: String): String {
        val checksumSource = "$payload;"
        val crc = CrcUtils.formatCrcToHex(CrcUtils.calculateCrc16(checksumSource.toByteArray(Charsets.UTF_8)))
        return "#$packetType#$checksumSource$crc\r\n"
    }

    internal fun buildPayload(location: Location): String {
        val speed = if (location.hasSpeed()) {
            (location.speed * 3.6).roundToInt()
        } else {
            NO_VALUE
        }
        val course = if (location.hasBearing()) {
            location.bearing.roundToInt()
        } else {
            NO_VALUE
        }
        val altitude = if (location.hasAltitude()) {
            location.altitude.roundToInt()
        } else {
            NO_VALUE
        }

        return listOf(
            dateFormatter.format(location.time),
            timeFormatter.format(location.time),
            NmeaUtils.latitudeToDdm(location.latitude, ";"),
            NmeaUtils.longitudeToDdm(location.longitude, ";"),
            speed,
            course,
            altitude,
            location.extras?.getInt("satellites")?.takeIf { it != 0 } ?: NO_VALUE,
            location.extras?.getDouble("hdop")?.takeIf { it != 0.0 } ?: NO_VALUE,
            NO_VALUE, NO_VALUE, "", NO_VALUE, // inputs, outputs, adc, iButton
            "accuracy:2:${location.accuracy},provider:3:${location.provider}"
        ).joinToString(";")
    }
}

private fun Context.hasNetworkConnectivity(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
