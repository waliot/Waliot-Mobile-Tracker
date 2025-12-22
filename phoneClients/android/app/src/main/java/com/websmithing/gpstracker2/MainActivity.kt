package com.websmithing.gpstracker2

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.websmithing.gpstracker2.di.SettingsRepositoryEntryPoint
import com.websmithing.gpstracker2.helper.LocaleHelper
import com.websmithing.gpstracker2.ui.App
import com.websmithing.gpstracker2.ui.checkIfGooglePlayEnabled
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val repo = getSettingsRepository(newBase)
        val newCtx = runBlocking {
            LocaleHelper.wrapContext(newBase, repo)
        }
        super.attachBaseContext(newCtx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        checkIfGooglePlayEnabled()

        setContent {
            App()
        }
    }

    private fun getSettingsRepository(context: Context) = EntryPointAccessors.fromApplication(
        context.applicationContext,
        SettingsRepositoryEntryPoint::class.java
    ).settingsRepository()
}
