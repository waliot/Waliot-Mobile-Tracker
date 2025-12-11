package com.websmithing.gpstracker2.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.location.Location
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.theme.AccentPrimary
import com.websmithing.gpstracker2.ui.theme.AccentSecondary
import com.websmithing.gpstracker2.ui.theme.IconTintSecondary
import com.websmithing.gpstracker2.ui.theme.SurfaceTertiary
import com.websmithing.gpstracker2.ui.theme.TextPrimary
import com.websmithing.gpstracker2.util.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.api.IGeoPoint
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import java.io.File
import kotlin.math.atan2
import javax.inject.Inject

/**
 * Converts Color to ARGB integer (0xAARRGGBB).
 *
 * Scales RGB components from [0f, 1f] to [0, 255] and packs them with full opacity (0xFF alpha).
 */
fun Color.toHexInt(): Int = (0xFF shl 24) or
        ((red * 255).toInt() shl 16) or
        ((green * 255).toInt() shl 8) or
        ((blue * 255).toInt())

/**
 * Enum defining available background colors for map markers.
 *
 * Each variant stores the color as an ARGB integer, converted from the app's design system tokens.
 */
enum class MarkerBackgroundColor(val colorInt: Int) {
    GREY(SurfaceTertiary.toHexInt()),
    BLUE(AccentPrimary.toHexInt()),
    RED(AccentSecondary.toHexInt())
}

/**
 * Enum defining available icon colors for map markers.
 *
 * Each variant stores the color as an ARGB integer, converted from the app's design system tokens.
 */
enum class MarkerIconColor(val colorInt: Int) {
    GREY(IconTintSecondary.toHexInt()),
    WHITE(TextPrimary.toHexInt())
}

/**
 * Converts a drawable resource into a bitmap of the specified dimensions.
 *
 * Creates a new ARGB_8888 bitmap, draws the provided drawable into it using a canvas,
 * and returns the resulting bitmap.
 *
 * @param drawable The drawable to convert.
 * @param width The target bitmap width in pixels.
 * @param height The target bitmap height in pixels.
 *
 * @return A bitmap containing the rendered drawable.
 */
fun drawableToBitmap(drawable: android.graphics.drawable.Drawable, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return bitmap
}

/**
 * Creates a custom map marker consisting of a colored background and a tinted icon.
 *
 * The function attempts to load predefined drawable resources for the marker background
 * and user icon. If those drawables are unavailable, it programmatically generates
 * simple vector-style shapes instead. Both the background and the icon are then tinted
 * using the provided colors and returned as separate bitmaps.
 *
 * @param context Android context used to access drawable resources.
 * @param backgroundColor Color applied to the marker background.
 * @param markerSize Size (width and height in pixels) of the background bitmap.
 * @param iconSize Size (width and height in pixels) of the icon bitmap.
 * @param iconColor Color applied to the icon.
 *
 * @return A pair of bitmaps: (tinted background bitmap, tinted icon bitmap).
 */
fun createColoredMarkerBitmap(
    context: Context,
    backgroundColor: Int,
    markerSize: Int,
    iconSize: Int,
    iconColor: Int
): Pair<Bitmap, Bitmap> {
    val markerDrawable = ContextCompat.getDrawable(context, R.drawable.ic_marker_background)!!
    val markerBitmap = Bitmap.createBitmap(markerSize, markerSize, Bitmap.Config.ARGB_8888)
    val markerCanvas = Canvas(markerBitmap)

    markerDrawable.setBounds(0, 0, markerSize, markerSize)
    markerDrawable.setTint(backgroundColor)
    markerDrawable.draw(markerCanvas)

    val iconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_user)!!
    val iconBitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
    val iconCanvas = Canvas(iconBitmap)

    iconDrawable.setBounds(0, 0, iconSize, iconSize)
    iconDrawable.setTint(iconColor)
    iconDrawable.draw(iconCanvas)

    return Pair(markerBitmap, iconBitmap)
}

/**
 * ViewModel responsible for exposing and updating the application's
 * location-permission state.
 *
 * This ViewModel checks the current permission status through the injected
 * [PermissionChecker] and provides it to the UI as a [StateFlow]. The UI layer
 * can observe [hasLocationPermission] and react to changes (e.g., show dialogs,
 * enable features, or navigate accordingly).
 *
 * The method [refreshPermissions] allows the UI to request a re-evaluation of
 * the permission status, typically after the user responds to the system
 * permission dialog.
 *
 * @property permissionChecker Utility class that determines whether required
 * location permissions are granted.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val permissionChecker: PermissionChecker
) : androidx.lifecycle.ViewModel() {

    private val _hasLocationPermission = MutableStateFlow(permissionChecker.hasLocationPermissions())
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    fun refreshPermissions() {
        _hasLocationPermission.value = permissionChecker.hasLocationPermissions()
    }
}

/**
 * Sets the initial map center based on location permissions and available location data.
 * Priority: lastFixLocation > lastKnownLocation > moscowPoint (fallback).
 *
 * @param hasLocationPermission Whether the app has location permission.
 * @param lastFixLocation The most recent user location if available.
 * @return The GeoPoint that was set as the map center.
 */
private fun setInitialMapCenter(
    mapView: MapView,
    context: Context,
    moscowPoint: GeoPoint,
    hasLocationPermission: Boolean,
    lastFixLocation: Location? = null
): GeoPoint {
    val initialPoint = if (hasLocationPermission) {
        lastFixLocation?.let {
            GeoPoint(it.latitude, it.longitude)
        } ?: run {
            try {
                val locationProvider = GpsMyLocationProvider(context)
                locationProvider.lastKnownLocation?.let { lastKnownLocation ->
                    GeoPoint(lastKnownLocation.latitude, lastKnownLocation.longitude)
                } ?: moscowPoint
            } catch (_: Exception) {
                moscowPoint
            }
        }
    } else {
        moscowPoint
    }

    mapView.controller.setCenter(initialPoint)
    return initialPoint
}

/**
 * Displays an OSMDroid map inside a Compose layout and renders a customized
 * location marker with dynamic rotation, coloring, and tap handling.
 *
 * The map initializes OSMDroid configuration, creates a MapView, and attaches
 * a custom MyLocationNewOverlay that:
 * - updates marker background/icon colors;
 * - rotates the marker based on movement direction;
 * - tracks and centers on the user when permissions are granted;
 * - falls back to a default map position when permissions are missing.
 *
 * The marker is tappable and triggers [onPointerClick]. Bitmaps are recreated
 * only when colors change to avoid unnecessary processing.
 *
 * @param modifier Layout modifier.
 * @param markerBackgroundColor Color of the marker background.
 * @param markerIconColor Color of the marker icon.
 * @param onPointerClick Callback invoked when the marker is tapped.
 */
@Composable
fun OsmMapContainer(
    modifier: Modifier = Modifier,
    markerBackgroundColor: MarkerBackgroundColor = MarkerBackgroundColor.GREY,
    markerIconColor: MarkerIconColor = MarkerIconColor.GREY,
    onPointerClick: () -> Unit
) {
    var lastFixLocation by remember { mutableStateOf<Location?>(null) }
    val moscowPoint = remember { GeoPoint(55.7558, 37.6173) }
    var directionAngle by remember { mutableStateOf(0f) }

    val currentBgColor = rememberUpdatedState(markerBackgroundColor)
    val currentIconColor = rememberUpdatedState(markerIconColor)

    val viewModel: MapViewModel = hiltViewModel()
    val hasLocationPermission by viewModel.hasLocationPermission.collectAsState()

    var initialPositionSet by remember { mutableStateOf(false) }
    var followLocationEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    AndroidView(factory = { ctx ->
        val cacheDir = File(ctx.cacheDir, "osmdroid_tiles").apply { mkdirs() }
        Configuration.getInstance().apply {
            userAgentValue = "WaliotTracker/1.0"
            osmdroidBasePath = ctx.cacheDir
            osmdroidTileCache = cacheDir
        }

        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(18.0)
            isHorizontalMapRepetitionEnabled = false
            setBuiltInZoomControls(false)
            setTilesScaledToDpi(true)
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            isClickable = true

            setInitialMapCenter(
                mapView = this,
                context = ctx,
                moscowPoint = moscowPoint,
                hasLocationPermission = hasLocationPermission,
                lastFixLocation = null
            )

            val density = ctx.resources.displayMetrics.density
            val markerSize = (48 * density).toInt()
            val iconSize = (32 * density).toInt()

            val overlay = object : MyLocationNewOverlay(GpsMyLocationProvider(ctx), this) {
                private var currentBackgroundBitmap: Bitmap? = null
                private var currentIconBitmap: Bitmap? = null
                private var lastBgColor: MarkerBackgroundColor? = null
                private var lastIconColor: MarkerIconColor? = null
                private var firstFixReceived = false

                private fun updateBitmaps(bgColor: MarkerBackgroundColor, iconColor: MarkerIconColor) {
                    if (lastBgColor != bgColor || lastIconColor != iconColor) {
                        currentBackgroundBitmap?.recycle()
                        currentIconBitmap?.recycle()

                        val (bg, icon) = createColoredMarkerBitmap(
                            context = ctx,
                            backgroundColor = bgColor.colorInt,
                            markerSize = markerSize,
                            iconSize = iconSize,
                            iconColor = iconColor.colorInt
                        )

                        currentBackgroundBitmap = bg
                        currentIconBitmap = icon
                        lastBgColor = bgColor
                        lastIconColor = iconColor
                    }
                }

                override fun drawMyLocation(canvas: Canvas?, pj: Projection?, lastFix: Location?) {
                    if (canvas == null || pj == null) return
                    val loc = lastFix ?: lastFixLocation ?: return
                    updateBitmaps(currentBgColor.value, currentIconColor.value)

                    lastFixLocation?.let { prev ->
                        val dLat = loc.latitude - prev.latitude
                        val dLon = loc.longitude - prev.longitude
                        if (dLat != 0.0 || dLon != 0.0) {
                            directionAngle = Math.toDegrees(atan2(dLon, dLat)).toFloat()
                        }
                    }

                    val point = pj.toPixels(GeoPoint(loc.latitude, loc.longitude), null)

                    currentBackgroundBitmap?.let {
                        val bgMatrix = Matrix().apply {
                            postTranslate(-it.width / 2f, -it.height / 2f)
                            postRotate(directionAngle - 45)
                            postTranslate(point.x.toFloat(), point.y.toFloat())
                        }
                        canvas.drawBitmap(it, bgMatrix, null)
                    }

                    currentIconBitmap?.let {
                        val iconMatrix = Matrix().apply {
                            postTranslate(-it.width / 2f, -it.height / 2f)
                            postTranslate(point.x.toFloat(), point.y.toFloat())
                        }
                        canvas.drawBitmap(it, iconMatrix, null)
                    }
                }

                override fun onSingleTapConfirmed(e: android.view.MotionEvent?, map: MapView?): Boolean {
                    if (e == null || map == null) return false
                    val loc = myLocation ?: lastFixLocation ?: return false
                    val point = map.projection.toPixels(loc as IGeoPoint?, null)
                    val radius = currentBackgroundBitmap?.width?.div(2) ?: 0
                    if (e.x.toInt() in (point.x - radius)..(point.x + radius) &&
                        e.y.toInt() in (point.y - radius)..(point.y + radius)
                    ) {
                        onPointerClick()
                        map.invalidate()
                        return true
                    }
                    return false
                }

                override fun onLocationChanged(location: Location?, source: IMyLocationProvider?) {
                    super.onLocationChanged(location, source)

                    location?.let { loc ->

                        if (hasLocationPermission && !firstFixReceived && !initialPositionSet) {
                            firstFixReceived = true
                            post {
                                controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                                initialPositionSet = true

                                if (!followLocationEnabled) {
                                    enableFollowLocation()
                                }
                            }
                        }
                    }
                }

                override fun onDetach(mapView: MapView?) {
                    currentBackgroundBitmap?.recycle()
                    currentIconBitmap?.recycle()
                    super.onDetach(mapView)
                }
            }

            overlays.add(overlay)
        }
    }, update = { mapView ->
        mapView.post {
            viewModel.refreshPermissions()

            val overlay = mapView.overlays.find { it is MyLocationNewOverlay } as? MyLocationNewOverlay
            overlay?.apply {
                if (hasLocationPermission) {
                    enableMyLocation()
                    isDrawAccuracyEnabled = false

                    if (!initialPositionSet) {
                        myLocation?.let { geoPoint ->
                            mapView.controller.setCenter(geoPoint)
                            enableFollowLocation()
                        } ?: run {
                            disableFollowLocation()
                        }
                    } else {
                        if (followLocationEnabled) {
                            enableFollowLocation()
                        } else {
                            disableFollowLocation()
                        }
                    }
                } else {
                    disableMyLocation()
                    disableFollowLocation()
                    mapView.controller.setCenter(moscowPoint)
                }
            }
            mapView.invalidate()
        }
    }, modifier = modifier)
}