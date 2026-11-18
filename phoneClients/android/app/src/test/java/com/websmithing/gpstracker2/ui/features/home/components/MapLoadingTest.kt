package com.websmithing.gpstracker2.ui.features.home.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MapLoadingTest {
    @Test
    fun `failure offers retry and retry starts a fresh loading attempt`() {
        val failed = MapLoadState().failed()
        assertEquals(MapLoadStatus.Failed, failed.status)
        val retry = failed.retry()
        assertEquals(MapLoadStatus.Loading, retry.status)
        assertEquals(1, retry.attempt)
        assertNotEquals(
            mapStyleUrlForAttempt("https://example.org/style", failed.attempt),
            mapStyleUrlForAttempt("https://example.org/style", retry.attempt),
        )
    }

    @Test
    fun `late failure or timeout cannot hide a loaded map`() {
        val ready = MapLoadState().finished()
        assertEquals(MapLoadStatus.Ready, ready.failed().status)
    }

    @Test
    fun `map completing after timeout clears the failure`() {
        assertEquals(MapLoadStatus.Ready, MapLoadState().failed().finished().status)
    }

    @Test
    fun `finished map with a failed resource still needs a retry`() {
        val failed = MapLoadState().resourceFailed()
        assertEquals(MapLoadStatus.Failed, failed.finished().status)
        assertEquals(MapLoadStatus.Ready, failed.retry().finished().status)
    }

    @Test
    fun `each retry changes style URI again`() {
        val secondRetry = MapLoadState().failed().retry().failed().retry()
        assertEquals(MapLoadStatus.Loading, secondRetry.status)
        assertEquals("https://example.org/style?_waliot_retry=2", mapStyleUrlForAttempt("https://example.org/style", secondRetry.attempt))
    }

    @Test
    fun `initial load preserves original URI including query and fragment`() {
        val original = "https://example.org/a%20b?key=a%2Fb#view"
        assertEquals(original, mapStyleUrlForAttempt(original, 0))
    }

    @Test
    fun `retry preserves existing escaped query and fragment`() {
        assertEquals(
            "https://example.org/a%20b?key=a%2Fb&_waliot_retry=1#view",
            mapStyleUrlForAttempt("https://example.org/a%20b?key=a%2Fb#view", 1),
        )
    }

    @Test
    fun `retry adds query before fragment`() {
        assertEquals(
            "https://example.org/style?_waliot_retry=1#view",
            mapStyleUrlForAttempt("https://example.org/style#view", 1),
        )
    }

    @Test
    fun `retry handles empty query and trailing separator`() {
        assertEquals("https://example.org/style?_waliot_retry=1", mapStyleUrlForAttempt("https://example.org/style?", 1))
        assertEquals("https://example.org/style?k=v&_waliot_retry=1", mapStyleUrlForAttempt("https://example.org/style?k=v&", 1))
    }
}
