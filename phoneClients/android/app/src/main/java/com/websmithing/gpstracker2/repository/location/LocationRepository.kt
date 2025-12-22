package com.websmithing.gpstracker2.repository.location

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {

    val latestLocation: Flow<Location?>
    val totalDistance: Flow<Float>
    val lastUploadStatus: Flow<UploadStatus>

    suspend fun getCurrentLocation(): Location?
    suspend fun getPreviousLocation(): Location?
    suspend fun saveAsPreviousLocation(location: Location)
    suspend fun resetLocationState()
    suspend fun uploadLocationData(
        location: Location,
        username: String,
        appId: String,
        sessionId: String,
        eventType: String
    ): Boolean
}

sealed class UploadStatus {
    object Idle : UploadStatus()
    object Success : UploadStatus()
    data class Failure(val errorMessage: String?) : UploadStatus()
}
