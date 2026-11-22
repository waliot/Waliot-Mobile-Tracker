package com.websmithing.gpstracker2

import android.app.Application
import com.websmithing.gpstracker2.util.mapHttpClient
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import timber.log.Timber

@HiltAndroidApp
class GpsTrackerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupTimber()
        MapLibre.getInstance(this)
        HttpRequestUtil.setOkHttpClient(mapHttpClient())
    }

    private fun setupTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber DebugTree planted")
        }
    }
}
