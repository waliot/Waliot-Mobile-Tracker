package com.websmithing.gpstracker2.ui

import android.app.Activity
import android.location.Location
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.websmithing.gpstracker2.R
import org.maplibre.spatialk.geojson.Position
import timber.log.Timber

fun Location.toPosition() =
    Position(longitude = longitude, latitude = latitude, altitude = altitude)

/**
 * Checks if Google Play Services is available and enabled
 *
 * Shows an appropriate error dialog if Google Play Services is unavailable
 * or needs to be updated.
 *
 * @return True if Google Play Services is available and up-to-date
 */
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

@Composable
inline fun <reified VM : ViewModel> activityHiltViewModel(): VM {
    val context = LocalContext.current
    val viewModelStoreOwner = context as? ViewModelStoreOwner
        ?: error("Context is not a ViewModelStoreOwner")
    return hiltViewModel(viewModelStoreOwner)
}