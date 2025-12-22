package com.websmithing.gpstracker2.di

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.location.FusedLocationProviderClient
import com.websmithing.gpstracker2.repository.location.ForegroundLocationRepository
import com.websmithing.gpstracker2.repository.location.ForegroundLocationRepositoryImpl
import com.websmithing.gpstracker2.repository.location.LocationRepository
import com.websmithing.gpstracker2.repository.location.LocationRepositoryImpl
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.repository.settings.SettingsRepositoryImpl
import com.websmithing.gpstracker2.util.PermissionChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(sharedPreferences: SharedPreferences): SettingsRepository {
        return SettingsRepositoryImpl(sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideLocationRepository(
        @ApplicationContext context: Context,
        fusedLocationProviderClient: FusedLocationProviderClient,
        settingsRepository: SettingsRepository,
        permissionChecker: PermissionChecker
    ): LocationRepository {
        return LocationRepositoryImpl(
            context,
            fusedLocationProviderClient,
            settingsRepository,
            permissionChecker
        )
    }

    @Provides
    fun provideForegroundLocationRepository(
        fusedLocationProviderClient: FusedLocationProviderClient,
    ): ForegroundLocationRepository {
        return ForegroundLocationRepositoryImpl(
            provider = fusedLocationProviderClient
        )
    }
}
