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
    object Success : UploadStatus()
    data class Failure(val errorMessage: String?) : UploadStatus()
}
