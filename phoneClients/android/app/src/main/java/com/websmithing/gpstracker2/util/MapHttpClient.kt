package com.websmithing.gpstracker2.util

import com.websmithing.gpstracker2.BuildConfig
import okhttp3.OkHttpClient

internal fun mapHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        // MapLibre's native disk cache owns freshness and conditional requests.
        // Preserve those headers; OSM requires an application-specific identity.
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", "WaliotTracker/${BuildConfig.VERSION_NAME} (https://github.com/waliot/Waliot-Mobile-Tracker)")
                .build(),
        )
    }
    .build()
