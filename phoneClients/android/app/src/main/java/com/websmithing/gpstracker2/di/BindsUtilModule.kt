package com.websmithing.gpstracker2.di

import com.websmithing.gpstracker2.util.PermissionChecker
import com.websmithing.gpstracker2.util.PermissionCheckerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindsUtilModule {

    @Binds
    @Singleton
    abstract fun bindPermissionChecker(
        permissionCheckerImpl: PermissionCheckerImpl
    ): PermissionChecker
}
