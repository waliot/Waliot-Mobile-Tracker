package com.websmithing.gpstracker2.di

import com.websmithing.gpstracker2.repository.location.LocationRepository
import com.websmithing.gpstracker2.repository.location.LocationRepositoryImpl
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.repository.settings.SettingsRepositoryImpl
import com.websmithing.gpstracker2.repository.upload.UploadRepository
import com.websmithing.gpstracker2.repository.upload.UploadRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindUploadRepository(impl: UploadRepositoryImpl): UploadRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository
}
