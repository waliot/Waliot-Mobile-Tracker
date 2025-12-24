package com.websmithing.gpstracker2.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.websmithing.gpstracker2.R
import timber.log.Timber

@Composable
inline fun <reified VM : ViewModel> activityHiltViewModel(): VM {
    val context = LocalContext.current
    val viewModelStoreOwner = context as? ViewModelStoreOwner ?: error("Context is not a ViewModelStoreOwner")
    return hiltViewModel(viewModelStoreOwner)
}

fun Activity.checkIfGooglePlayEnabled(): Boolean {
    val googleApiAvailability = GoogleApiAvailability.getInstance()
    val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
    if (resultCode == ConnectionResult.SUCCESS) {
        return true
    } else {
        Timber.e("Google Play Services check failed with code: $resultCode")
        if (googleApiAvailability.isUserResolvableError(resultCode)) {
            googleApiAvailability.getErrorDialog(this, resultCode, 9000)?.show()
        } else {
            Toast.makeText(
                this.application,
                R.string.google_play_services_unavailable,
                Toast.LENGTH_LONG
            ).show()
        }
        return false
    }
}

fun isBackgroundLocationPermissionGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
}
