package com.websmithing.gpstracker2.di

import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsRepositoryEntryPoint {
    fun settingsRepository(): SettingsRepository
}
