package com.websmithing.gpstracker2.ui.features.home.components

import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.allViews
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.tile.TileOperation
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle

private val resetStyle = BaseStyle.Uri("asset://map_load_reset.json")

@Composable
internal fun ObservedMap(
    cameraState: CameraState,
    styleUri: String,
    attempt: Int,
    onError: () -> Unit,
    onFinished: () -> Unit,
) {
    val host = LocalView.current
    val context = LocalContext.current
    // Compose 0.12.1 owns the native MapView. Mark only this map's factory context so
    // another map in the same Compose host can never receive our listeners.
    val mapContext = remember(context) { ContextWrapper(context) }
    var nativeView by remember(host, mapContext) { mutableStateOf<MapView?>(null) }
    var baseStyle by remember(host, mapContext) { mutableStateOf<BaseStyle>(resetStyle) }

    CompositionLocalProvider(LocalContext provides mapContext) {
        MaplibreMap(
            modifier = Modifier.onGloballyPositioned {
                if (nativeView == null) {
                    nativeView = host.allViews.filterIsInstance<MapView>().firstOrNull { it.context === mapContext }
                }
            },
            baseStyle = baseStyle,
            cameraState = cameraState,
            options = MapOptions(
                ornamentOptions = OrnamentOptions.AllDisabled,
            ),
        )
    }

    val view = nativeView ?: return
    DisposableEffect(view, styleUri, attempt) {
        var disposed = false
        var map: MapLibreMap? = null
        var resetting = true
        var awaitingFrame: Style? = null

        fun resourceError() {
            if (!resetting && map?.style?.uri == styleUri) onError()
        }

        val tile = MapView.OnTileActionListener { operation, _, _, _, _, _, _ ->
            if (operation == TileOperation.Error) resourceError()
        }
        val glyphs = MapView.OnGlyphsErrorListener { _, _, _ -> resourceError() }
        val sprite = MapView.OnSpriteErrorListener { _, _ -> resourceError() }
        val failed = MapView.OnDidFailLoadingMapListener { if (!resetting) onError() }
        fun requestFrameForCurrentStyle() {
            val style = map?.style ?: return
            val forReset = resetting
            if (style.uri != (if (forReset) resetStyle.uri else styleUri)) return

            // Drain earlier frames on the renderer thread before requesting one for this
            // style. A fast or cached style can finish without a map-finished transition.
            view.queueEvent {
                view.post {
                    if (!disposed && resetting == forReset && map?.style === style) {
                        awaitingFrame = style
                        map?.triggerRepaint()
                    }
                }
            }
        }

        val styleLoaded = MapView.OnDidFinishLoadingStyleListener { requestFrameForCurrentStyle() }
        val rendered = MapView.OnDidFinishRenderingFrameListener { fullyRendered, _, _ ->
            val style = awaitingFrame ?: return@OnDidFinishRenderingFrameListener
            if (fullyRendered && map?.style === style) {
                awaitingFrame = null
                if (resetting) {
                    resetting = false
                    baseStyle = BaseStyle.Uri(styleUri)
                } else {
                    onFinished()
                }
            }
        }
        view.addOnDidFinishLoadingStyleListener(styleLoaded)
        view.addOnDidFinishRenderingFrameListener(rendered)
        view.addOnTileActionListener(tile)
        view.addOnGlyphsErrorListener(glyphs)
        view.addOnSpriteErrorListener(sprite)
        view.addOnDidFailLoadingMapListener(failed)
        view.getMapAsync {
            if (!disposed) {
                map = it
                it.setPrefetchZoomDelta(0)
                // Loading an asset URI cancels the previous style request. Rendering
                // it without sources also retires failed tiles before their URLs are retried.
                baseStyle = resetStyle
                requestFrameForCurrentStyle()
            }
        }
        onDispose {
            disposed = true
            view.removeOnDidFinishRenderingFrameListener(rendered)
            view.removeOnDidFinishLoadingStyleListener(styleLoaded)
            view.removeOnDidFailLoadingMapListener(failed)
            view.removeOnSpriteErrorListener(sprite)
            view.removeOnGlyphsErrorListener(glyphs)
            view.removeOnTileActionListener(tile)
        }
    }
}
