package com.websmithing.gpstracker2.repository.upload

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface UploadRepository {
    val lastUploadStatus: Flow<UploadStatus>

    suspend fun resetUploadStatus()
    suspend fun uploadData(trackerIdentifier: String, location: Location): Boolean
}

sealed class UploadStatus {
    object Idle : UploadStatus()
    data class Success(val uploadedAtMillis: Long) : UploadStatus()
    object Offline : UploadStatus()
    data class Failure(val errorMessage: String?) : UploadStatus()
}
