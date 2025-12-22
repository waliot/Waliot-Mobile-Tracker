package com.websmithing.gpstracker2

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.websmithing.gpstracker2.di.SettingsRepositoryEntryPoint
import com.websmithing.gpstracker2.ui.App
import com.websmithing.gpstracker2.ui.checkFirstTimeLoading
import com.websmithing.gpstracker2.ui.checkIfGooglePlayEnabled
import com.websmithing.gpstracker2.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            newBase.applicationContext,
            SettingsRepositoryEntryPoint::class.java
        )
        val repo = entryPoint.getSettingsRepository()
        val newCtx = LocaleHelper.onAttach(newBase, repo)
        super.attachBaseContext(newCtx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        checkIfGooglePlayEnabled()
        checkFirstTimeLoading()

        setContent {
            App()
        }
    }
}
