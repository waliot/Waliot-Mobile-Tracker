package com.websmithing.gpstracker2.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.catch
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
    lateinit var uploadRepository: UploadRepository

    private var wakeLock: PowerManager.WakeLock? = null
    private var backgroundExecutor: ExecutorService? = null

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
        val action = intent?.action
        Timber.tag(TAG).d("TrackingService onStartCommand: $action")

        when (action) {
            ACTION_START_SERVICE -> handleStartService()
            ACTION_STOP_SERVICE -> handleStopService()
            else -> handleDefaultAction()
        }
        return START_STICKY
    }

    private fun handleDefaultAction() {
        Timber.tag(TAG).d("Service restarted without specific action. Re-initializing.")
        handleStartService()
    }

    private fun handleStartService() {
        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()
        locationRepository.start()
        // TODO UPLOAD
    }

    private fun handleStopService() {
        locationRepository.stop()
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

        locationRepository.stop()
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
}
