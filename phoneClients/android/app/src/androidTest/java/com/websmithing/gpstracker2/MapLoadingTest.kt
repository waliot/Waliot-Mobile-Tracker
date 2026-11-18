package com.websmithing.gpstracker2

import android.graphics.Color
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.websmithing.gpstracker2.ui.UiTestTags
import com.websmithing.gpstracker2.ui.features.home.components.MapView
import com.websmithing.gpstracker2.ui.features.home.components.TrackingButton
import com.websmithing.gpstracker2.ui.features.home.components.TrackingButtonState
import com.websmithing.gpstracker2.ui.theme.WaliotTheme
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView as NativeMapView

@RunWith(AndroidJUnit4::class)
class MapLoadingTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun failed_style_retries_in_same_native_view_and_renders_successful_style() {
        LocalStyleServer(StyleResponse.Invalid).use { server ->
            val trackingClicked = AtomicBoolean()
            showMap(server, trackingClicked)
            waitForTag(UiTestTags.MAP_LOAD_ERROR)
            val originalMap = nativeMap()
            composeRule.onNodeWithTag(UiTestTags.TRACKING_BUTTON_PLAY)
                .assertIsDisplayed().assertIsEnabled().performClick()
            assertTrue(trackingClicked.get())
            composeRule.onNodeWithTag(UiTestTags.MAP_RETRY_BUTTON).performClick()
            waitForTag(UiTestTags.MAP_READY)
            assertSame(originalMap, nativeMap())
            assertTrue(server.requests.any { it == "/style?_waliot_retry=1" })
            composeRule.onNodeWithTag(UiTestTags.MAP_LOAD_ERROR).assertDoesNotExist()
            composeRule.onNodeWithTag(UiTestTags.MAP_LOADING).assertDoesNotExist()
            assertTestColorRendered(originalMap)

            composeRule.mainClock.advanceTimeBy(30_000)
            composeRule.onNodeWithTag(UiTestTags.MAP_READY).assertIsDisplayed()
            composeRule.onNodeWithTag(UiTestTags.MAP_LOAD_ERROR).assertDoesNotExist()
        }
    }

    @Test
    fun stalled_style_times_out_and_retry_can_recover() {
        LocalStyleServer(StyleResponse.Stall).use { server ->
            showMap(server)
            composeRule.waitUntil(15_000) { server.requests.contains("/style") }
            composeRule.onNodeWithTag(UiTestTags.MAP_LOADING).assertIsDisplayed()
            composeRule.mainClock.advanceTimeBy(30_000)
            waitForTag(UiTestTags.MAP_LOAD_ERROR)
            composeRule.onNodeWithTag(UiTestTags.MAP_RETRY_BUTTON).performClick()
            waitForTag(UiTestTags.MAP_READY)
            assertTestColorRendered(nativeMap())
        }
    }

    @Test
    fun background_time_does_not_expire_loading_and_resume_gets_a_fresh_deadline() {
        LocalStyleServer(StyleResponse.Stall).use { server ->
            showMap(server)
            composeRule.waitUntil(15_000) { server.requests.contains("/style") }
            composeRule.mainClock.advanceTimeBy(10_000)
            composeRule.onNodeWithTag(UiTestTags.MAP_LOADING).assertIsDisplayed()

            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            composeRule.mainClock.advanceTimeBy(30_000)
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

            waitForTag(UiTestTags.MAP_LOADING)
            composeRule.onNodeWithTag(UiTestTags.MAP_LOAD_ERROR).assertDoesNotExist()
            composeRule.mainClock.advanceTimeBy(20_000)
            composeRule.onNodeWithTag(UiTestTags.MAP_LOADING).assertIsDisplayed()
            composeRule.mainClock.advanceTimeBy(6_000)
            waitForTag(UiTestTags.MAP_LOAD_ERROR)
            composeRule.onNodeWithTag(UiTestTags.MAP_RETRY_BUTTON).performClick()
            waitForTag(UiTestTags.MAP_READY)
        }
    }

    @Test
    fun previous_deadline_cannot_fail_a_retry_that_is_still_loading() {
        LocalStyleServer(StyleResponse.InvalidThenStall).use { server ->
            showMap(server)
            waitForTag(UiTestTags.MAP_LOAD_ERROR)
            composeRule.mainClock.advanceTimeBy(20_000)
            composeRule.onNodeWithTag(UiTestTags.MAP_RETRY_BUTTON).performClick()
            composeRule.waitUntil(15_000) { server.requests.contains("/style?_waliot_retry=1") }

            // Cross the original load deadline while the retry still has time remaining.
            composeRule.mainClock.advanceTimeBy(10_000)
            composeRule.onNodeWithTag(UiTestTags.MAP_LOADING).assertIsDisplayed()
            composeRule.onNodeWithTag(UiTestTags.MAP_LOAD_ERROR).assertDoesNotExist()
            composeRule.mainClock.advanceTimeBy(16_000)
            waitForTag(UiTestTags.MAP_LOAD_ERROR)
            composeRule.onNodeWithTag(UiTestTags.MAP_RETRY_BUTTON).performClick()
            waitForTag(UiTestTags.MAP_READY)
            assertTrue(server.requests.contains("/style?_waliot_retry=2"))
        }
    }

    @Test
    fun successful_style_with_unavailable_tiles_does_not_claim_ready() {
        assertTileRecovery(StyleResponse.RasterTiles)
    }

    @Test
    fun unavailable_vector_tiles_recover_in_the_same_map() {
        assertTileRecovery(StyleResponse.VectorTiles)
    }

    private fun assertTileRecovery(response: StyleResponse) {
        LocalStyleServer(response).use { server ->
            showMap(server)
            composeRule.waitUntil(15_000) { server.requests.any { it.startsWith("/tile/") } }
            composeRule.mainClock.advanceTimeBy(30_000)
            waitForTag(UiTestTags.MAP_LOAD_ERROR)
            composeRule.onNodeWithTag(UiTestTags.MAP_READY).assertDoesNotExist()
            val originalView = nativeMap()
            lateinit var map: MapLibreMap
            composeRule.runOnIdle {
                originalView.getMapAsync { map = it }
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(10.0, 15.0), 3.5))
            }
            val camera = composeRule.runOnIdle { map.cameraPosition }
            composeRule.onNodeWithTag(UiTestTags.MAP_RETRY_BUTTON).performClick()
            waitForTag(UiTestTags.MAP_READY)
            assertSame(originalView, nativeMap())
            assertTrue(server.requests.contains("/style?_waliot_retry=1"))
            composeRule.runOnIdle { assertEquals(camera, map.cameraPosition) }
            assertTestColorRendered(originalView)
        }
    }

    private fun showMap(server: LocalStyleServer, trackingClicked: AtomicBoolean = AtomicBoolean()) {
        composeRule.setContent {
            WaliotTheme {
                Box(Modifier.fillMaxSize()) {
                    MapView(cameraState = rememberCameraState(), styleUrl = server.styleUrl)
                    TrackingButton(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        state = TrackingButtonState.PLAY,
                        onClick = { trackingClicked.set(true) },
                    )
                }
            }
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun nativeMap(): NativeMapView = composeRule.runOnIdle {
        fun find(view: View): NativeMapView? {
            if (view is NativeMapView) return view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    find(view.getChildAt(index))?.let { return it }
                }
            }
            return null
        }
        checkNotNull(find(composeRule.activity.window.decorView))
    }

    private fun assertTestColorRendered(map: NativeMapView) {
        val center = composeRule.runOnIdle {
            assertTrue(map.width > 0 && map.height > 0)
            IntArray(2).also {
                map.getLocationOnScreen(it)
                it[0] += map.width / 2
                it[1] += map.height / 2
            }
        }
        composeRule.waitUntil(10_000) {
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?: return@waitUntil false
            try {
                val pixel = screenshot.getPixel(center[0], center[1])
                abs(Color.red(pixel) - 0x24) < 15 &&
                    abs(Color.green(pixel) - 0x68) < 15 &&
                    abs(Color.blue(pixel) - 0xac) < 15
            } finally {
                screenshot.recycle()
            }
        }
    }
}

private enum class StyleResponse { Invalid, Stall, InvalidThenStall, RasterTiles, VectorTiles }

private class LocalStyleServer(private val response: StyleResponse) : Closeable {
    private val socket = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newFixedThreadPool(4)
    private val closed = AtomicBoolean()
    private val releaseStalledRequests = CountDownLatch(1)
    private val clients = ConcurrentLinkedQueue<Socket>()
    val requests = ConcurrentLinkedQueue<String>()
    private val origin = "http://127.0.0.1:${socket.localPort}"
    val styleUrl = "$origin/style"

    private val acceptThread = thread(name = "map-style-fixture", isDaemon = true) {
        while (!closed.get()) {
            try {
                val client = socket.accept()
                clients.add(client)
                workers.execute { handle(client) }
            } catch (error: IOException) {
                if (!closed.get()) throw error
            }
        }
    }

    private fun handle(client: Socket) {
        try {
            client.use {
                it.soTimeout = 10_000
                val reader = it.getInputStream().bufferedReader()
                val target = reader.readLine()?.split(' ')?.getOrNull(1) ?: return
                while (!reader.readLine().isNullOrEmpty()) { /* Consume HTTP headers. */ }
                requests.add(target)
                val isRetry = target.startsWith("/style?")
                val isTile = target.startsWith("/tile/")
                val serveTile = isTile && requests.contains("/style?_waliot_retry=1")
                val status = if (isTile && !serveTile) "503 Service Unavailable" else "200 OK"
                val contentType: String
                val bytes: ByteArray
                if (isTile) {
                    val raster = response == StyleResponse.RasterTiles
                    contentType = if (raster) "image/png" else "application/vnd.mapbox-vector-tile"
                    bytes = if (serveTile) Base64.decode(if (raster) RASTER_TILE else VECTOR_TILE, Base64.DEFAULT)
                        else ByteArray(0)
                } else {
                    contentType = "application/json"
                    val body = when {
                        response == StyleResponse.RasterTiles -> tileStyle("raster")
                        response == StyleResponse.VectorTiles -> tileStyle("vector")
                        response == StyleResponse.InvalidThenStall && target == "/style?_waliot_retry=1" ||
                            response == StyleResponse.Stall && !isRetry -> {
                            releaseStalledRequests.await(60, TimeUnit.SECONDS)
                            return
                        }
                        isRetry -> BACKGROUND_STYLE
                        else -> "invalid style JSON"
                    }
                    bytes = body.toByteArray(Charsets.UTF_8)
                }
                val headers = "HTTP/1.1 $status\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
                it.getOutputStream().apply {
                    write(headers.toByteArray(Charsets.US_ASCII))
                    write(bytes)
                    flush()
                }
            }
        } catch (_: IOException) {
            // MapLibre cancels in-flight requests when Retry changes the style URI.
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            clients.remove(client)
        }
    }

    private fun tileStyle(type: String): String {
        val source = """{"type":"$type","tiles":["$origin/tile/{z}/{x}/{y}"],"minzoom":0,"maxzoom":0}"""
        val layer = if (type == "raster") """{"id":"tile","type":"raster","source":"tiles"}"""
            else """{"id":"tile","type":"fill","source":"tiles","source-layer":"ground","paint":{"fill-color":"#2468ac"}}"""
        return """{"version":8,"sources":{"tiles":$source},"layers":[$layer]}"""
    }

    override fun close() {
        closed.set(true)
        socket.close()
        releaseStalledRequests.countDown()
        clients.forEach { it.close() }
        workers.shutdownNow()
        acceptThread.join(1_000)
        workers.awaitTermination(1, TimeUnit.SECONDS)
    }

    private companion object {
        // One opaque #2468ac pixel, and a z0 vector tile with a full-tile polygon in layer "ground".
        const val RASTER_TILE = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNQyVgDAAHsATleaPIZAAAAAElFTkSuQmCC"
        const val VECTOR_TILE = "GiEKBmdyb3VuZBISGAMiDgkAABqAQAAAgED/PwAPKIAgeAI="
        const val BACKGROUND_STYLE = """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#2468ac"}}]}"""
    }
}
