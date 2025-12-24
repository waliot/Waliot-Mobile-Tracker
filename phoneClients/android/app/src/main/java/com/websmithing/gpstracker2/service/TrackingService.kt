package com.websmithing.gpstracker2.service

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
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.di.SettingsRepositoryEntryPoint
import com.websmithing.gpstracker2.helper.LocaleHelper
import com.websmithing.gpstracker2.repository.location.LocationRepository
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.repository.upload.UploadRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var uploadRepository: UploadRepository

    companion object {
        private const val TAG = "TrackingService"
        private const val WAKE_LOCK_TAG = "WaliotTracker::TrackingServiceWakeLock"

        private const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Waliot Tracker"
        private const val NOTIFICATION_ID = 1

        private const val RESTART_DELAY_MS = 5000L
        private const val RESTART_REQUEST_CODE = 1

        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"

        private val _bufferCount = MutableStateFlow(0)
        val bufferCount: StateFlow<Int> = _bufferCount
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    private val bufferMutex = Mutex()
    private val uploadMutex = Mutex()

    private val locationBuffer = mutableListOf<Location>()
    private var lastBufferLocation: Location? = null

    override fun onBind(intent: Intent?): IBinder? = null

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

    override fun onCreate() {
        super.onCreate()
        setupWakeLock()
        setupNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()

        locationRepository.stop()
    }

    //region onStartCommand

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Timber.tag(TAG).d("TrackingService onStartCommand: $action")

        when (action) {
            ACTION_START_SERVICE -> handleStartService()
            ACTION_STOP_SERVICE -> handleStopService()
            else -> handleDefaultAction()
        }
        return START_STICKY
    }

    private fun handleStartService() {
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createNotification())

        locationRepository.start()

        serviceScope.launch {
            val uploadTimeInterval = settingsRepository.getUploadTimeInterval().toLong()
            val uploadDistanceInterval = settingsRepository.getUploadDistanceInterval().toLong()

            locationRepository.currentLocation.collect { location ->
                if (location != null) {
                    if (shouldAddToBuffer(location, uploadTimeInterval, uploadDistanceInterval)) {
                        lastBufferLocation = location

                        bufferMutex.withLock {
                            locationBuffer.add(location)
                            _bufferCount.value = locationBuffer.size
                        }

                        Timber.tag(TAG).i("Location added to buffer. Total: ${_bufferCount.value}")
                    }
                }
            }
        }

        serviceScope.launch {
            val trackerId = settingsRepository.getTrackerIdentifier()
            val uploadTimeInterval = settingsRepository.getUploadTimeInterval().toLong()

            while (true) {
                delay(TimeUnit.MINUTES.toMillis(uploadTimeInterval))
                uploadBuffer(trackerId)
            }
        }
    }

    private fun handleStopService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        locationRepository.stop()
        stopSelf()
    }

    private fun handleDefaultAction() {
        handleStartService()
    }

    private fun shouldAddToBuffer(location: Location, uploadTimeInterval: Long, uploadDistanceInterval: Long): Boolean {
        val lastLoc = lastBufferLocation ?: return true

        val timeElapsed = System.currentTimeMillis() - lastLoc.time
        val distance = location.distanceTo(lastLoc)

        return timeElapsed > TimeUnit.MINUTES.toMillis(uploadTimeInterval)
            || distance > uploadDistanceInterval
    }

    private suspend fun uploadBuffer(trackerId: String) {
        uploadMutex.withLock {
            Timber.tag(TAG).i("Attempting to upload buffer: ${locationBuffer.size} points")
            if (locationBuffer.isEmpty()) {
                return
            }

            while (true) {
                val next: Location = bufferMutex.withLock {
                    val loc = locationBuffer.firstOrNull() ?: return
                    locationBuffer.removeAt(0)
                    _bufferCount.value = locationBuffer.size
                    loc
                }

                val success = uploadRepository.uploadData(trackerId, next)

                if (!success) {
                    bufferMutex.withLock {
                        locationBuffer.add(0, next)
                        _bufferCount.value = locationBuffer.size
                    }

                    Timber.tag(TAG).w("Upload failed, stopping buffer processing until next cycle")
                    return
                }
            }
        }
    }

    //endregion onStartCommand

    //region onTaskRemoved

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleServiceRestart()
    }

    private fun scheduleServiceRestart() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val restartPendingIntent = createRestartPendingIntent()

        val triggerAtMillis = SystemClock.elapsedRealtime() + RESTART_DELAY_MS
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            triggerAtMillis,
            restartPendingIntent
        )
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

    //region WAKE LOCK

    private fun setupWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
        }
        Timber.tag(TAG).d("Wake lock created")
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

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
        Timber.tag(TAG).d("Wake lock released")
    }

    //endregion WAKE LOCK

    //region NOTIFICATION

    private fun setupNotificationChannel() {
        getSystemService(NotificationManager::class.java)?.apply {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            createNotificationChannel(channel)
            Timber.tag(TAG).d("Notification channel created")
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification_tracking)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    //endregion NOTIFICATION
}
