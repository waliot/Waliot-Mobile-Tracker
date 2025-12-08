package com.websmithing.gpstracker2

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.websmithing.gpstracker2.data.repository.SettingsRepository
import com.websmithing.gpstracker2.di.SettingsRepositoryEntryPoint
import com.websmithing.gpstracker2.ui.AppNavGraph
import com.websmithing.gpstracker2.ui.TrackingViewModel
import com.websmithing.gpstracker2.ui.components.PermissionDialog
import com.websmithing.gpstracker2.ui.screens.SplashScreen
import com.websmithing.gpstracker2.util.LocaleHelper
import com.websmithing.gpstracker2.util.PermissionChecker
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Main activity for the GPS Tracker application.
 *
 * This activity serves as the primary user interface for the GPS tracking functionality.
 * It handles:
 * - User configuration (username, server URL, tracking interval)
 * - Permission management for location tracking
 * - Starting and stopping the tracking service
 * - Displaying real-time location data and tracking statistics
 * - Communicating with the backend ViewModel that manages data and services
 *
 * The activity is integrated with Hilt for dependency injection.
 */
@AndroidEntryPoint
class GpsTrackerActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var permissionChecker: PermissionChecker
    private val viewModel: TrackingViewModel by viewModels()
    private lateinit var prefs: SharedPreferences

    private var permissionsRequestedInThisSession = false
    private var showPermissionDialog by mutableStateOf(false)
    private var dialogTitle by mutableStateOf("")
    private var dialogMessage by mutableStateOf("")
    private var onDialogConfirm by mutableStateOf<() -> Unit>({})

    // --- Permission Handling ---

    /**
     * Activity result launcher for requesting multiple location permissions
     *
     * Handles the result of requesting foreground location permissions and
     * proceeds to request background permissions if needed.
     */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineGranted || coarseGranted) {
                Timber.i("Foreground permissions granted")
                if (permissionsRequestedInThisSession) window.decorView.post { recreate() }
                requestBackgroundLocationPermissions()
            }
        }

    /**
     * Activity result launcher for requesting background location permission
     *
     * Handles the result of requesting the background location permission
     * and starts tracking if granted.
     */
    private val requestBackgroundPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && permissionsRequestedInThisSession) window.decorView.post { recreate() }
        }

    // --- Activity Lifecycle ---

    /**
     * Initializes and displays the splash screen for the app.
     *
     * Sets the splash screen theme and installs the splash screen,
     * ensuring it is shown only briefly (condition always false).
     */
    private fun setupSplashScreen() {
        setTheme(R.style.Theme_AppTheme_Splash)
        installSplashScreen()
            .setKeepOnScreenCondition { false }
    }

    /**
     * Called when the activity is first created.
     *
     * Initializes the UI, sets up listeners and observers, and checks for first-time app setup.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being
     *                           shut down, this contains the data it most recently supplied in
     *                           onSaveInstanceState(Bundle).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        setupSplashScreen()
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        checkPermissions()

        setContent {
            val navController = rememberNavController()
            val showSplash = rememberSaveable { mutableStateOf(savedInstanceState?.getBoolean("showSplash") ?: true) }

            LaunchedEffect(Unit) {
                delay(3000)
                showSplash.value = false
            }

            Crossfade(targetState = showSplash.value) { isSplash ->
                if (isSplash) SplashScreen()
                else AppNavGraph(navController, viewModel)
            }

            PermissionDialog(
                showDialog = showPermissionDialog,
                title = dialogTitle,
                message = dialogMessage,
                onDismiss = { showPermissionDialog = false },
                onConfirm = { showPermissionDialog = false; onDialogConfirm() }
            )
        }

        checkFirstTimeLoading()
        checkIfGooglePlayEnabled()
    }

    // --- Permission Logic ---

    /**
     * Checks if the app has required permissions
     */
    private fun checkPermissions() {
        if (!permissionChecker.hasLocationPermissions()) {
            val showRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (showRationale) {
                showPermissionDialog(
                    title = getString(R.string.permission_rationale_foreground_location_title),
                    message = getString(R.string.permission_rationale_foreground_location_message)
                ) { requestForegroundLocationPermissions() }
            } else requestForegroundLocationPermissions()
        } else requestBackgroundLocationPermissions()
    }

    /**
     * Requests foreground location permissions (and notification permission on Android 13+)
     */
    private fun requestForegroundLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionsRequestedInThisSession = true
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    /**
     * Requests background location permission on Android Q (10) and above
     */
    private fun requestBackgroundLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !permissionChecker.hasBackgroundLocationPermissions()) {
            val showRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (showRationale) {
                showPermissionDialog(
                    title = getString(R.string.permission_rationale_background_location_title),
                    message = getString(R.string.permission_rationale_background_location_message)
                ) {
                    permissionsRequestedInThisSession = true
                    requestBackgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            } else {
                permissionsRequestedInThisSession = true
                requestBackgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    // --- UI Feedback for Permissions ---

    /**
     * Shows a dialog explaining why a permission is needed
     *
     * @param title The dialog title
     * @param message The explanation message
     * @param onConfirm Callback for when the user clicks the positive button
     */
    private fun showPermissionDialog(title: String, message: String, onConfirm: () -> Unit) {
        dialogTitle = title
        dialogMessage = message
        this.onDialogConfirm = onConfirm
        showPermissionDialog = true
    }


    // --- Utility / Other Methods ---

    /**
     * Performs first-time app setup
     *
     * Generates a unique app ID and stores it in SharedPreferences along with
     * a flag indicating the app has been run at least once.
     */
    private fun checkFirstTimeLoading() {
        val firstTime = prefs.getBoolean(KEY_FIRST_TIME_LOADING, true)
        if (firstTime) {
            prefs.edit().apply {
                putBoolean(KEY_FIRST_TIME_LOADING, false)
                putString(KEY_APP_ID, UUID.randomUUID().toString())
                apply()
            }
            Timber.d("First time loading setup complete.")
        }
    }

    /**
     * Checks if Google Play Services is available and enabled
     *
     * Shows an appropriate error dialog if Google Play Services is unavailable
     * or needs to be updated.
     *
     * @return True if Google Play Services is available and up-to-date
     */
    private fun checkIfGooglePlayEnabled(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val result = availability.isGooglePlayServicesAvailable(this)
        if (result == ConnectionResult.SUCCESS) return true
        if (availability.isUserResolvableError(result)) availability.getErrorDialog(this, result, 9000)?.show()
        else android.widget.Toast.makeText(applicationContext, R.string.google_play_services_unavailable, android.widget.Toast.LENGTH_LONG).show()
        return false
    }

    /**
     * Constants used by the application
     */
    companion object {
        private const val PREFS_NAME = "com.waliot.tracker.prefs"
        private const val KEY_FIRST_TIME_LOADING = "firstTimeLoadingApp"
        private const val KEY_APP_ID = "appID"
    }
}
