package com.websmithing.gpstracker2.service

import android.content.Context
import android.location.Location
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingBufferStoreContractTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val usedPreferences = mutableListOf<String>()
    private val usedDatabases = mutableListOf<String>()

    @After
    fun tearDown() {
        usedPreferences.forEach { prefsName ->
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        }
        usedPreferences.clear()
        usedDatabases.forEach { databaseName ->
            context.deleteDatabase(databaseName)
        }
        usedDatabases.clear()
    }

    @Test
    fun saveState_and_loadState_roundTrip_bufferedSnapshot_includingExtras() {
        val store = createStore("roundTrip")
        val buffered = listOf(
            createLocation(
                provider = "gps",
                latitude = 55.75,
                longitude = 37.61,
                altitude = 120.0,
                accuracy = 5f,
                bearing = 90f,
                speed = 10f,
                time = 1_700_000_000_000L,
                satellites = 7,
                hdop = 0.9,
            ),
            createLocation(
                provider = "network",
                latitude = 59.93,
                longitude = 30.31,
                altitude = 15.0,
                accuracy = 12f,
                bearing = 0f,
                speed = 0f,
                time = 1_700_000_500_000L,
            )
        )
        val lastBuffered = createLocation(
            provider = "gps",
            latitude = 48.85,
            longitude = 2.35,
            altitude = 42.0,
            accuracy = 3f,
            bearing = 180f,
            speed = 5f,
            time = 1_700_001_000_000L,
            satellites = 9,
            hdop = 0.7,
        )

        store.saveState(buffered, lastBuffered)
        val snapshot = store.loadState()

        assertEquals(2, snapshot.bufferedLocations.size)
        assertLocation(snapshot.bufferedLocations[0], 55.75, 37.61, 120.0, 5f, 90f, 10f, 1_700_000_000_000L, "gps", 7, 0.9)
        assertLocation(snapshot.bufferedLocations[1], 59.93, 30.31, 15.0, 12f, 0f, 0f, 1_700_000_500_000L, "network", null, null)
        assertLocation(snapshot.lastBufferedLocation!!, 48.85, 2.35, 42.0, 3f, 180f, 5f, 1_700_001_000_000L, "gps", 9, 0.7)
    }

    @Test
    fun saveState_overwrites_previousPayload_and_clear_removesPersistedSnapshot() {
        val prefsName = "TrackingBufferStoreContractTest.overwrite"
        val store = createStore(prefsName)
        val sharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        store.saveState(
            bufferedLocations = listOf(createLocation(latitude = 1.0, longitude = 2.0, time = 100L)),
            lastBufferedLocation = null,
        )
        store.saveState(
            bufferedLocations = listOf(
                createLocation(latitude = 3.0, longitude = 4.0, time = 200L),
                createLocation(latitude = 5.0, longitude = 6.0, time = 300L),
            ),
            lastBufferedLocation = createLocation(latitude = 7.0, longitude = 8.0, time = 400L),
        )

        val overwritten = store.loadState()
        assertEquals(2, overwritten.bufferedLocations.size)
        assertEquals(3.0, overwritten.bufferedLocations.first().latitude, 0.0)
        assertEquals(400L, overwritten.lastBufferedLocation?.time)

        store.clear()

        assertNull(sharedPreferences.getString(KEY_TRACKING_BUFFER_STATE, null))
        val cleared = store.loadState()
        assertTrue(cleared.bufferedLocations.isEmpty())
        assertNull(cleared.lastBufferedLocation)
    }

    @Test
    fun append_and_removeOldest_update_buffer_incrementally_without_losing_order() {
        val store = createStore("incremental")
        val first = createLocation(latitude = 1.0, longitude = 2.0, time = 100L)
        val second = createLocation(latitude = 3.0, longitude = 4.0, time = 200L)
        val third = createLocation(latitude = 5.0, longitude = 6.0, time = 300L)

        store.appendBufferedLocation(first, maxSize = 2, lastBufferedLocation = first)
        store.appendBufferedLocation(second, maxSize = 2, lastBufferedLocation = second)
        store.appendBufferedLocation(third, maxSize = 2, lastBufferedLocation = third)

        val trimmedSnapshot = store.loadState()
        assertEquals(2, trimmedSnapshot.bufferedLocations.size)
        assertEquals(3.0, trimmedSnapshot.bufferedLocations.first().latitude, 0.0)
        assertEquals(5.0, trimmedSnapshot.bufferedLocations.last().latitude, 0.0)
        assertEquals(300L, trimmedSnapshot.lastBufferedLocation?.time)

        store.removeOldestBufferedLocation()
        store.replaceLastBufferedLocation(null)

        val drainedSnapshot = store.loadState()
        assertEquals(1, drainedSnapshot.bufferedLocations.size)
        assertEquals(5.0, drainedSnapshot.bufferedLocations.single().latitude, 0.0)
        assertNull(drainedSnapshot.lastBufferedLocation)
    }

    @Test
    fun loadState_clears_corruptedPayload_and_returnsEmptySnapshot() {
        val prefsName = "TrackingBufferStoreContractTest.corrupted"
        val store = createStore(prefsName)
        val sharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        sharedPreferences.edit()
            .putString(KEY_TRACKING_BUFFER_STATE, "{broken-json")
            .commit()

        val snapshot = store.loadState()

        assertTrue(snapshot.bufferedLocations.isEmpty())
        assertNull(snapshot.lastBufferedLocation)
        assertNull(sharedPreferences.getString(KEY_TRACKING_BUFFER_STATE, null))
    }

    @Test
    fun loadState_migrates_legacy_shared_preferences_snapshot_into_sqlite_store() {
        val prefsName = "TrackingBufferStoreContractTest.migration"
        val sharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putString(
                KEY_TRACKING_BUFFER_STATE,
                """
                {
                  "bufferedLocations":[
                    {
                      "provider":"gps",
                      "latitude":55.75,
                      "longitude":37.61,
                      "altitude":120.0,
                      "accuracy":5.0,
                      "bearing":90.0,
                      "speed":10.0,
                      "time":1700000000000,
                      "satellites":7,
                      "hdop":0.9
                    }
                  ],
                  "lastBufferedLocation":{
                    "provider":"gps",
                    "latitude":48.85,
                    "longitude":2.35,
                    "altitude":42.0,
                    "accuracy":3.0,
                    "bearing":180.0,
                    "speed":5.0,
                    "time":1700001000000,
                    "satellites":9,
                    "hdop":0.7
                  }
                }
                """.trimIndent()
            )
            .commit()

        val store = createStore(prefsName)

        val snapshot = store.loadState()

        assertEquals(1, snapshot.bufferedLocations.size)
        assertLocation(snapshot.bufferedLocations[0], 55.75, 37.61, 120.0, 5f, 90f, 10f, 1_700_000_000_000L, "gps", 7, 0.9)
        assertLocation(snapshot.lastBufferedLocation!!, 48.85, 2.35, 42.0, 3f, 180f, 5f, 1_700_001_000_000L, "gps", 9, 0.7)
        assertNull(sharedPreferences.getString(KEY_TRACKING_BUFFER_STATE, null))
    }

    @Test
    fun saveState_and_loadState_preserve_largeSnapshot_order_and_count() {
        val store = createStore("large")
        val buffered = (0 until 250).map { index ->
            createLocation(
                provider = "gps",
                latitude = 50.0 + index,
                longitude = 30.0 + index,
                altitude = index.toDouble(),
                accuracy = index.toFloat(),
                bearing = (index % 360).toFloat(),
                speed = (index % 25).toFloat(),
                time = 1_700_000_000_000L + index,
                satellites = (index % 10) + 1,
                hdop = index / 10.0,
            )
        }

        store.saveState(buffered, buffered.last())
        val snapshot = store.loadState()

        assertEquals(250, snapshot.bufferedLocations.size)
        assertEquals(50.0, snapshot.bufferedLocations.first().latitude, 0.0)
        assertEquals(30.0, snapshot.bufferedLocations.first().longitude, 0.0)
        assertEquals(175.0, snapshot.bufferedLocations[125].latitude, 0.0)
        assertEquals(279.0, snapshot.bufferedLocations.last().longitude, 0.0)
        assertEquals(1_700_000_000_249L, snapshot.lastBufferedLocation?.time)
    }

    private fun createStore(suffix: String): SQLiteTrackingBufferStore {
        val prefsName = suffix
        val databaseName = "$suffix.db"
        usedPreferences += prefsName
        usedDatabases += databaseName
        return SQLiteTrackingBufferStore(
            context = context,
            sharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE),
            databaseName = databaseName,
        )
    }

    private fun createLocation(
        provider: String = "gps",
        latitude: Double,
        longitude: Double,
        altitude: Double = 0.0,
        accuracy: Float = 0f,
        bearing: Float = 0f,
        speed: Float = 0f,
        time: Long,
        satellites: Int? = null,
        hdop: Double? = null,
    ): Location {
        return Location(provider).apply {
            this.latitude = latitude
            this.longitude = longitude
            this.altitude = altitude
            this.accuracy = accuracy
            this.bearing = bearing
            this.speed = speed
            this.time = time
            val extrasBundle = Bundle()
            satellites?.let { extrasBundle.putInt(KEY_SATELLITES, it) }
            hdop?.let { extrasBundle.putDouble(KEY_HDOP, it) }
            if (!extrasBundle.isEmpty) {
                extras = extrasBundle
            }
        }
    }

    private fun assertLocation(
        location: Location,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        accuracy: Float,
        bearing: Float,
        speed: Float,
        time: Long,
        provider: String,
        satellites: Int?,
        hdop: Double?,
    ) {
        assertEquals(latitude, location.latitude, 0.0)
        assertEquals(longitude, location.longitude, 0.0)
        assertEquals(altitude, location.altitude, 0.0)
        assertEquals(accuracy, location.accuracy, 0.0f)
        assertEquals(bearing, location.bearing, 0.0f)
        assertEquals(speed, location.speed, 0.0f)
        assertEquals(time, location.time)
        assertEquals(provider, location.provider)
        assertEquals(satellites, location.extras?.getInt(KEY_SATELLITES))
        if (hdop != null) {
            assertEquals(hdop, location.extras?.getDouble(KEY_HDOP) ?: 0.0, 0.0)
        } else {
            assertTrue(location.extras?.containsKey(KEY_HDOP) != true)
        }
    }

    private companion object {
        const val KEY_TRACKING_BUFFER_STATE = "trackingBufferState"
        const val KEY_SATELLITES = "satellites"
        const val KEY_HDOP = "hdop"
    }
}
