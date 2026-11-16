package com.websmithing.gpstracker2.ui.features.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.UiTestTags
import kotlinx.coroutines.delay
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle

const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
const val DEFAULT_MAP_ZOOM = 15.0
private const val MAP_LOAD_TIMEOUT_MS = 25_000L

@Composable
fun MapView(
    modifier: Modifier = Modifier,
    cameraState: CameraState,
    styleUrl: String = MAP_STYLE_URL,
) {
    var loadState by remember(styleUrl) { mutableStateOf(MapLoadState()) }
    // MapLibre Compose 0.12.1 remembers its callbacks independently of these lambdas.
    val onLoadFailed by rememberUpdatedState { loadState = loadState.failed() }
    val onLoadFinished by rememberUpdatedState { loadState = loadState.finished() }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, styleUrl, loadState.attempt, loadState.status) {
        if (loadState.status == MapLoadStatus.Loading) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(MAP_LOAD_TIMEOUT_MS)
                loadState = loadState.failed()
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize().then(
            if (loadState.status == MapLoadStatus.Ready) Modifier.testTag(UiTestTags.MAP_READY)
            else Modifier
        ),
    ) {
        MaplibreMap(
            baseStyle = BaseStyle.Uri(mapStyleUrlForAttempt(styleUrl, loadState.attempt)),
            cameraState = cameraState,
            options = MapOptions(
                ornamentOptions = OrnamentOptions.AllDisabled.copy(
                    isAttributionEnabled = true,
                    attributionAlignment = Alignment.BottomStart,
                ),
            ),
            onMapLoadFailed = { onLoadFailed() },
            onMapLoadFinished = { onLoadFinished() },
        )

        if (loadState.status != MapLoadStatus.Ready) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 16.dp, end = 16.dp, top = 88.dp)
                    .widthIn(max = 360.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                if (loadState.status == MapLoadStatus.Loading) {
                    Row(
                        modifier = Modifier.padding(16.dp).testTag(UiTestTags.MAP_LOADING),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.map_loading))
                    }
                } else {
                    Column(Modifier.padding(16.dp).testTag(UiTestTags.MAP_LOAD_ERROR)) {
                        Text(stringResource(R.string.map_load_failed))
                        TextButton(
                            onClick = { loadState = loadState.retry() },
                            modifier = Modifier.align(Alignment.End).testTag(UiTestTags.MAP_RETRY_BUTTON),
                        ) {
                            Text(stringResource(R.string.map_retry))
                        }
                    }
                }
            }
        }
    }
}
