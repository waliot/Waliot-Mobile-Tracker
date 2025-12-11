package com.websmithing.gpstracker2

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.websmithing.gpstracker2.di.SettingsRepositoryEntryPoint
import com.websmithing.gpstracker2.ui.App
import com.websmithing.gpstracker2.ui.checkFirstTimeLoading
import com.websmithing.gpstracker2.ui.checkIfGooglePlayEnabled
import com.websmithing.gpstracker2.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Main activity for the GPS Tracker application.
 *
 * This activity serves as the primary user interface for the GPS tracking functionality.
 * It handles:
 * - User configuration (username, server URL, tracking interval)
 * - Permission management for location tracking
 * - Starting and stopping the tracking service
 * - Displaying real-time location data and tracking statistics
 * - Communicating with the backend ViewModel that manages data and services
 *
 * The activity is integrated with Hilt for dependency injection.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        // 1. Get the EntryPoint accessor from the application context
        val entryPoint = EntryPointAccessors.fromApplication(
            newBase.applicationContext,
            SettingsRepositoryEntryPoint::class.java
        )

        // 2. Use the EntryPoint to get the repository instance
        val repo = entryPoint.getSettingsRepository()

        // 3. Use your LocaleHelper to create the new context
        val newCtx = LocaleHelper.onAttach(newBase, repo)

        super.attachBaseContext(newCtx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        checkIfGooglePlayEnabled()
        checkFirstTimeLoading()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            App()
        }
    }
}