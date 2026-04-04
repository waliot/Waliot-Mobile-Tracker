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
import android.os.Build
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    @Inject
    lateinit var trackingBufferStore: TrackingBufferStore

    @Inject
    lateinit var trackingPermissionChecker: TrackingPermissionChecker

    companion object {
        private const val TAG = "TrackingService"
        private const val WAKE_LOCK_TAG = "WaliotTracker::TrackingServiceWakeLock"

        private const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Waliot Tracker"
        private const val NOTIFICATION_ID = 1

        private const val RESTART_DELAY_MS = 5_000
        private const val RESTART_REQUEST_CODE = 1

        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_REFRESH_SERVICE = "ACTION_REFRESH_SERVICE"

        private val _bufferCount = MutableStateFlow(0)
        val bufferCount: StateFlow<Int> = _bufferCount
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private val trackingRuntime by lazy {
        TrackingRuntimeController(
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            serviceScope = serviceScope,
            onBufferCountChanged = { count -> _bufferCount.value = count }
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context) {
        val repo = getSettingsRepository(newBase)
        val newCtx = LocaleHelper.wrapContextFromSettings(newBase, repo.peekLanguage())
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
        trackingRuntime.destroy()
        releaseWakeLock()
        serviceScope.cancel()
    }

    //region onStartCommand

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Timber.tag(TAG).d("TrackingService onStartCommand: $action")

        when (action) {
            ACTION_START_SERVICE -> handleStartService()
            ACTION_STOP_SERVICE -> handleStopService()
            ACTION_REFRESH_SERVICE -> handleRefreshService()
            else -> handleDefaultAction()
        }
        return START_STICKY
    }

    private fun handleStartService() {
        if (!trackingPermissionChecker.canStartTrackingService()) {
            Timber.tag(TAG).w("Ignoring tracking start because foreground location permission is missing")
            stopSelf()
            return
        }

        if (!trackingRuntime.start()) {
            Timber.tag(TAG).d("Tracking already active. Ignoring duplicate start command")
            return
        }

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun handleStopService() {
        if (!trackingRuntime.stop()) {
            Timber.tag(TAG).d("Tracking already inactive. Stopping service")
            stopSelf()
            return
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        stopSelf()
    }

    private fun handleRefreshService() {
        if (!trackingRuntime.refresh()) {
            Timber.tag(TAG).d("Ignoring refresh command because tracking is not active")
            return
        }

        Timber.tag(TAG).d("Refreshing tracking workers with latest settings")
    }

    private fun handleDefaultAction() {
        handleStartService()
    }

    //endregion onStartCommand

    //region onTaskRemoved

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (trackingRuntime.isTrackingActive) {
            scheduleServiceRestart()
        }
    }

    private fun scheduleServiceRestart() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val restartPendingIntent = createRestartPendingIntent()

        val triggerAtMillis = SystemClock.elapsedRealtime() + RESTART_DELAY_MS
        when (chooseTrackingRestartAlarmMode(Build.VERSION.SDK_INT, alarmManager.canScheduleExactRestartAlarms())) {
            TrackingRestartAlarmMode.ExactAllowWhileIdle -> {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME,
                    triggerAtMillis,
                    restartPendingIntent
                )
            }

            TrackingRestartAlarmMode.AllowWhileIdle -> {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME,
                    triggerAtMillis,
                    restartPendingIntent
                )
            }
        }
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

    private fun AlarmManager.canScheduleExactRestartAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canScheduleExactAlarms()
        } else {
            true
        }
    }

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
                // Tracking sessions can legitimately run for longer than 24 hours.
                // Keep the wake lock for the active runtime lifetime and release it explicitly.
                acquire()
                Timber.tag(TAG).d("Wake lock acquired for active tracking session")
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
