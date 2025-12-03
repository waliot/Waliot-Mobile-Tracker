package com.websmithing.gpstracker2

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.websmithing.gpstracker2.di.SettingsRepositoryEntryPoint
import com.websmithing.gpstracker2.ui.App
import com.websmithing.gpstracker2.ui.checkFirstTimeLoading
import com.websmithing.gpstracker2.ui.checkIfGooglePlayEnabled
import com.websmithing.gpstracker2.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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