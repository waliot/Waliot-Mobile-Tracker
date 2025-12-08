package com.websmithing.gpstracker2.ui.router

import kotlinx.serialization.Serializable

interface Destination

@Serializable
sealed class AppDestination : Destination {
    @Serializable
    data object Splash : AppDestination()

    @Serializable
    data object Home : AppDestination()

    @Serializable
    data object Settings : AppDestination()
}