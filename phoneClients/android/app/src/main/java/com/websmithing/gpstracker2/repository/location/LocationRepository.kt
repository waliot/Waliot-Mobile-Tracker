package com.websmithing.gpstracker2.repository.location

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    val lastUploadStatus: Flow<UploadStatus>

    suspend fun resetUploadStatus()
    suspend fun uploadLocationData(username: String, location: Location): Boolean
}

sealed class UploadStatus {
    object Idle : UploadStatus()
    object Success : UploadStatus()
    data class Failure(val errorMessage: String?) : UploadStatus()
}
