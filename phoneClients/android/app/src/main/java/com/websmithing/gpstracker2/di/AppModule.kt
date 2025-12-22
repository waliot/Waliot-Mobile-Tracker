package com.websmithing.gpstracker2.di

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.PREFS_NAME
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext ctx: Context
    ): SharedPreferences = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(
        @ApplicationContext ctx: Context
    ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(ctx)
}
