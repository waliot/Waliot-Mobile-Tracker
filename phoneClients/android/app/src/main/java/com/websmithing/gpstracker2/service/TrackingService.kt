package com.websmithing.gpstracker2.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.di.SettingsRepositoryEntryPoint
import com.websmithing.gpstracker2.helper.LocaleHelper
import com.websmithing.gpstracker2.repository.location.LocationRepository
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private var wakeLock: PowerManager.WakeLock? = null
    private var backgroundExecutor: ExecutorService? = null
    private var locationCallback: LocationCallback? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "TrackingService"
        private const val WAKE_LOCK_TAG = "WaliotTracker::TrackingServiceWakeLock"

        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"

        private const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "GPS Tracking"
        private const val NOTIFICATION_ID = 1

        private const val RESTART_DELAY_MS = 5000L
        private const val RESTART_REQUEST_CODE = 1
    }

    //region attachBaseContext

    override fun attachBaseContext(newBase: Context) {
        val repo = getSettingsRepository(newBase)
        val newCtx = runBlocking {
            LocaleHelper.wrapContext(newBase, repo)
        }
        super.attachBaseContext(newCtx)
    }

    private fun getSettingsRepository(context: Context) = EntryPointAccessors.fromApplication(
        context.applicationContext,
        SettingsRepositoryEntryPoint::class.java
    ).settingsRepository()

    //endregion attachBaseContext

    //region onCreate

    override fun onCreate() {
        super.onCreate()
        setupNotificationChannel()
        setupWakeLock()
        setupBackgroundExecutor()
    }

    private fun setupNotificationChannel() {
        getSystemService(NotificationManager::class.java)?.apply {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            createNotificationChannel(channel)
            Timber.tag(TAG).d("Notification channel created.")
        }
    }

    private fun setupWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
        }
        Timber.tag(TAG).d("Wake lock created.")
    }

    private fun setupBackgroundExecutor() {
        backgroundExecutor = Executors.newSingleThreadExecutor()
        Timber.tag(TAG).d("Background executor initialized.")
    }

    //endregion onCreate

    //region onStartCommand

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = extractAction(intent)
        Timber.tag(TAG).d("TrackingService onStartCommand: $action")

        when (action) {
            ACTION_START_SERVICE -> handleStartService()
            ACTION_STOP_SERVICE -> handleStopService()
            else -> handleDefaultAction()
        }
        return START_STICKY
    }

    private fun extractAction(intent: Intent?): String? = intent?.action

    private fun handleDefaultAction() {
        Timber.tag(TAG).d("Service restarted without specific action. Re-initializing.")
        handleStartService()
    }

    private fun handleStartService() {
        startForeground(NOTIFICATION_ID, createNotification())
        startLocationUpdates()
    }

    private fun handleStopService() {
        stopLocationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification_tracking)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    //endregion onStartCommand

    //region onTaskRemoved

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.tag(TAG).d("TrackingService onTaskRemoved - application swiped away from recent apps")
        scheduleServiceRestart()
    }

    private fun scheduleServiceRestart() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val restartPendingIntent = createRestartPendingIntent()

        val triggerAtMillis = SystemClock.elapsedRealtime() + RESTART_DELAY_MS
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            triggerAtMillis,
            restartPendingIntent
        )

        val delayInSeconds = RESTART_DELAY_MS / 1000
        Timber.tag(TAG).d("TrackingService scheduled for restart in $delayInSeconds seconds")
    }

    private fun createRestartPendingIntent(): PendingIntent {
        val restartServiceIntent = Intent(applicationContext, TrackingService::class.java).apply {
            action = ACTION_START_SERVICE
        }
        return PendingIntent.getService(
            applicationContext,
            RESTART_REQUEST_CODE,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    //endregion onTaskRemoved

    //region onDestroy

    override fun onDestroy() {
        super.onDestroy()

        stopLocationUpdates()
        shutdownExecutor()
        releaseWakeLock()
    }

    private fun shutdownExecutor() {
        backgroundExecutor?.shutdown()
        backgroundExecutor = null
        Timber.tag(TAG).d("Background executor shutdown requested.")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
        Timber.tag(TAG).d("Wake lock released")
    }

    //endregion onDestroy

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        Timber.tag(TAG).d("Starting location updates...")
        acquireWakeLock()

        try {
            val trackingIntervalMinutes = runBlocking { settingsRepository.getCurrentTrackingInterval() }
            Timber.tag(TAG).d("Using tracking interval: $trackingIntervalMinutes minutes")

            val intervalMillis = TimeUnit.MINUTES.toMillis(trackingIntervalMinutes.toLong())
            val locationRequest = createLocationRequest(intervalMillis, 10f)
            locationCallback = createLocationCallback()

            requestImmediateLocation()
            subscribeToLocationUpdates(locationRequest)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Exception in startLocationUpdates: ${e.message}")
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        wakeLock?.apply {
            if (!isHeld) {
                acquire(TimeUnit.HOURS.toMillis(24))
                Timber.tag(TAG).d("Wake lock acquired")
            } else {
                Timber.tag(TAG).d("Wake lock already held")
            }
        } ?: Timber.tag(TAG).e("Wake lock is null, cannot acquire")
    }

    private fun createLocationRequest(intervalMillis: Long, intervalMeters: Float): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .setMaxUpdateDelayMillis(intervalMillis * 2)
            .setMinUpdateDistanceMeters(intervalMeters)
            .setWaitForAccurateLocation(true)
            .build()
    }

    private fun createLocationCallback(): LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                Timber.tag(TAG).d("Location received: ${location.latitude}, ${location.longitude}")
                handleNewLocation(location)
            } ?: Timber.tag(TAG).w("Received null location in onLocationResult")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestImmediateLocation() {
        Timber.tag(TAG).d("Requesting immediate location update...")
        fusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                location?.let {
                    Timber.tag(TAG).d("Got immediate location: ${it.latitude}, ${it.longitude}")
                    handleNewLocation(it)
                } ?: Timber.tag(TAG).w("Immediate location request returned null")
            }
            .addOnFailureListener { e -> Timber.tag(TAG).e(e, "Failed to get immediate location") }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToLocationUpdates(locationRequest: LocationRequest) {
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        ).addOnSuccessListener {
            Timber.tag(TAG).d("Location updates requested successfully.")
        }.addOnFailureListener { e ->
            Timber.tag(TAG).e(e, "Failed to request location updates.")
            stopSelf()
        }
    }

    private fun handleNewLocation(currentLocation: Location) {
        Timber.tag(TAG).d("handleNewLocation: Received location ${currentLocation.latitude}, ${currentLocation.longitude}")

        serviceScope.launch {
            try {
                Timber.tag(TAG).i("Location processing started: ${currentLocation.latitude}, ${currentLocation.longitude}")

                val username = settingsRepository.getCurrentUsername()
                val sessionId = settingsRepository.getCurrentSessionId()
                val appId = settingsRepository.getAppId()

                val success = uploadLocationWithRetry(currentLocation, username, sessionId, appId)

                if (!success) {
                    Timber.tag(TAG).e("All upload attempts failed for location ${currentLocation.latitude}, ${currentLocation.longitude}")
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Error processing location")
            } finally {
                Timber.tag(TAG).i("Location processing finished")
            }
        }
    }

    private suspend fun uploadLocationWithRetry(
        location: Location,
        username: String,
        sessionId: String,
        appId: String,
        maxRetries: Int = 3
    ): Boolean {
        repeat(maxRetries) { attempt ->
            try {
                val retryCount = attempt + 1
                Timber.tag(TAG).d("Upload Attempt $retryCount/$maxRetries")

                val isSuccessful = locationRepository.uploadLocationData(
                    username = username,
                    location = location
                )

                if (isSuccessful) {
                    Timber.tag(TAG).i("Upload SUCCESS! (Attempt $retryCount)")
                    return true
                }

                Timber.tag(TAG).w("Upload FAILED (Attempt $retryCount)")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Exception during upload attempt ${attempt + 1}")
            }

            if (attempt < maxRetries - 1) {
                delay(1000L * (attempt + 1))
            }
        }
        return false
    }

    private fun stopLocationUpdates() {
        Timber.tag(TAG).d("stopLocationUpdates() called")

        releaseWakeLock()

        val callback = locationCallback ?: run {
            Timber.tag(TAG).d("stopLocationUpdates called but locationCallback was already null.")
            return
        }

        Timber.tag(TAG).d("Stopping location updates...")
        try {
            fusedLocationProviderClient.removeLocationUpdates(callback)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Timber.tag(TAG).d("Location updates stopped successfully.")
                    } else {
                        Timber.tag(TAG).w(task.exception, "Failed to stop location updates.")
                    }
                }
        } catch (e: SecurityException) {
            Timber.tag(TAG).e(e, "SecurityException while stopping location updates.")
        } finally {
            locationCallback = null
            Timber.tag(TAG).d("Location callback cleared.")
        }
    }
}
