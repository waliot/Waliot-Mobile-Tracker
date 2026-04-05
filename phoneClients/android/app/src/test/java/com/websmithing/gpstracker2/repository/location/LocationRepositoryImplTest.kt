package com.websmithing.gpstracker2.repository.location

import android.app.Activity
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.location.LocationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor

class LocationRepositoryImplTest {

    @Test
    fun `start requests initial fixes and ignores duplicate starts`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = true,
            currentTimeMillis = { 1_700_000_100_000L }
        )
        val lastKnownLocation = createLocation(55.75, 37.61, 1_700_000_000_000L)
        val currentLocation = createLocation(59.93, 30.31, 1_700_000_100_000L)
        val mainLooper = mock<Looper>()

        whenever(provider.lastLocation).thenReturn(successTask(lastKnownLocation))
        whenever(provider.getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull()))
            .thenReturn(successTask(currentLocation))
        whenever(provider.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper)))
            .thenReturn(successVoidTask())

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)

            repository.start(LocationConsumer.ForegroundUi)
            repository.start(LocationConsumer.ForegroundUi)
        }

        verify(provider, times(1)).requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper))
        verify(provider, times(1)).getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull())
        assertEquals(lastKnownLocation, repository.currentLocation.value)
    }

    @Test
    fun `current location fallback is used when last known location is absent and callback updates state`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = true,
            currentTimeMillis = { 1_700_000_200_000L }
        )
        val currentLocation = createLocation(59.93, 30.31, 1_700_000_100_000L)
        val callbackLocation = createLocation(48.85, 2.35, 1_700_000_200_000L)
        val mainLooper = mock<Looper>()

        whenever(provider.lastLocation).thenReturn(successTask(null))
        whenever(provider.getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull()))
            .thenReturn(successTask(currentLocation))
        whenever(provider.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper)))
            .thenReturn(successVoidTask())

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)

            repository.start(LocationConsumer.ForegroundUi)
        }

        assertEquals(currentLocation, repository.currentLocation.value)

        val callbackCaptor = argumentCaptor<LocationCallback>()
        verify(provider).requestLocationUpdates(any<LocationRequest>(), callbackCaptor.capture(), same(mainLooper))

        val locationResult = mock<LocationResult>()
        whenever(locationResult.lastLocation).thenReturn(callbackLocation)
        callbackCaptor.firstValue.onLocationResult(locationResult)

        assertEquals(callbackLocation, repository.currentLocation.value)
    }

    @Test
    fun `stop removes updates and allows restart with a new callback`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = true,
            currentTimeMillis = { 1_700_000_000_000L }
        )
        val mainLooper = mock<Looper>()

        whenever(provider.lastLocation).thenReturn(successTask(null))
        whenever(provider.getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull()))
            .thenReturn(successTask(null))
        whenever(provider.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper)))
            .thenReturn(successVoidTask())

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)

            repository.start(LocationConsumer.ForegroundUi)
            val callbackCaptor = argumentCaptor<LocationCallback>()
            verify(provider).requestLocationUpdates(any<LocationRequest>(), callbackCaptor.capture(), same(mainLooper))
            val firstCallback = callbackCaptor.firstValue

            repository.stop(LocationConsumer.ForegroundUi)
            verify(provider).removeLocationUpdates(firstCallback)

            repository.start(LocationConsumer.ForegroundUi)

            val restartCaptor = argumentCaptor<LocationCallback>()
            verify(provider, times(2)).requestLocationUpdates(any<LocationRequest>(), restartCaptor.capture(), same(mainLooper))
            val secondCallback = restartCaptor.allValues.last()

            assertNotSame(firstCallback, secondCallback)
        }
    }

    @Test
    fun `restart resets initial fix state so getCurrentLocation can seed the next session`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = true,
            currentTimeMillis = { 1_700_000_400_000L }
        )
        val mainLooper = mock<Looper>()
        val callbackLocation = createLocation(48.85, 2.35, 1_700_000_200_000L)
        val restartCurrentLocation = createLocation(59.93, 30.31, 1_700_000_300_000L)

        whenever(provider.lastLocation).thenReturn(successTask(null))
        whenever(provider.getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull()))
            .thenReturn(successTask(null), successTask(restartCurrentLocation))
        whenever(provider.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper)))
            .thenReturn(successVoidTask())

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)

            repository.start(LocationConsumer.ForegroundUi)

            val callbackCaptor = argumentCaptor<LocationCallback>()
            verify(provider).requestLocationUpdates(any<LocationRequest>(), callbackCaptor.capture(), same(mainLooper))
            val firstCallback = callbackCaptor.firstValue

            val firstLocationResult = mock<LocationResult>()
            whenever(firstLocationResult.lastLocation).thenReturn(callbackLocation)
            firstCallback.onLocationResult(firstLocationResult)

            repository.stop(LocationConsumer.ForegroundUi)
            repository.start(LocationConsumer.ForegroundUi)
        }

        assertEquals(restartCurrentLocation, repository.currentLocation.value)
    }

    @Test
    fun `stopping foreground consumer keeps updates active while tracking consumer is still registered`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = true,
            currentTimeMillis = { 1_700_000_000_000L }
        )
        val mainLooper = mock<Looper>()

        whenever(provider.lastLocation).thenReturn(successTask(null))
        whenever(provider.getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull()))
            .thenReturn(successTask(null))
        whenever(provider.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper)))
            .thenReturn(successVoidTask())

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)

            repository.start(LocationConsumer.ForegroundUi)
            repository.start(LocationConsumer.TrackingService)
            repository.stop(LocationConsumer.ForegroundUi)

            verify(provider, times(1)).requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper))
            verify(provider, times(0)).removeLocationUpdates(any<LocationCallback>())

            repository.stop(LocationConsumer.TrackingService)

            verify(provider, times(1)).removeLocationUpdates(any<LocationCallback>())
        }
    }

    @Test
    fun `non gms devices use platform location manager backend`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = false,
            currentTimeMillis = { 1_700_000_200_000L }
        )
        val mainLooper = mock<Looper>()
        val gpsLocation = createLocation(55.75, 37.61, 1_700_000_000_000L, provider = LocationManager.GPS_PROVIDER)
        val networkLocation = createLocation(59.93, 30.31, 1_700_000_100_000L, provider = LocationManager.NETWORK_PROVIDER)

        whenever(locationManager.getProviders(true)).thenReturn(
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        )
        whenever(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)).thenReturn(gpsLocation)
        whenever(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)).thenReturn(networkLocation)

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)

            repository.start(LocationConsumer.ForegroundUi)
        }

        verify(provider, never()).requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any())
        verify(locationManager, atLeastOnce()).requestLocationUpdates(
            eq(LocationManager.GPS_PROVIDER),
            eq(10_000L),
            eq(0f),
            any<LocationListener>(),
            same(mainLooper)
        )
        verify(locationManager).requestLocationUpdates(
            eq(LocationManager.NETWORK_PROVIDER),
            eq(10_000L),
            eq(0f),
            any<LocationListener>(),
            same(mainLooper)
        )
        assertEquals(networkLocation, repository.currentLocation.value)
        assertEquals(LocationFixQuality.TrustedDegraded, repository.locationFixStatus.value.quality)
    }

    @Test
    fun `fused provider without satellite metadata is marked as degraded`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = true,
            currentTimeMillis = { 1_700_000_100_000L }
        )
        val fusedLocation = createLocation(
            latitude = 55.75,
            longitude = 37.61,
            time = 1_700_000_000_000L,
            provider = "fused",
        )
        val mainLooper = mock<Looper>()

        whenever(provider.lastLocation).thenReturn(successTask(fusedLocation))
        whenever(provider.getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull()))
            .thenReturn(successTask(null))
        whenever(provider.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper)))
            .thenReturn(successVoidTask())

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)
            repository.start(LocationConsumer.ForegroundUi)
        }

        assertEquals(LocationFixQuality.TrustedDegraded, repository.locationFixStatus.value.quality)
        assertTrue(repository.locationFixStatus.value.trustedFixDegraded)
    }

    @Test
    fun `fused provider with satellite metadata remains trusted gps`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = true,
            currentTimeMillis = { 1_700_000_100_000L }
        )
        val fusedLocation = createLocation(
            latitude = 55.75,
            longitude = 37.61,
            time = 1_700_000_000_000L,
            provider = "fused",
            satellites = 7,
        )
        val mainLooper = mock<Looper>()

        whenever(provider.lastLocation).thenReturn(successTask(fusedLocation))
        whenever(provider.getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull()))
            .thenReturn(successTask(null))
        whenever(provider.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper)))
            .thenReturn(successVoidTask())

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)
            repository.start(LocationConsumer.ForegroundUi)
        }

        assertEquals(LocationFixQuality.TrustedGps, repository.locationFixStatus.value.quality)
        assertFalse(repository.locationFixStatus.value.trustedFixDegraded)
    }

    @Test
    fun `stopping platform backend removes updates from location manager`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = false,
            currentTimeMillis = { 1_700_000_000_000L }
        )
        val mainLooper = mock<Looper>()

        whenever(locationManager.getProviders(true)).thenReturn(listOf(LocationManager.GPS_PROVIDER))
        whenever(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)).thenReturn(null)

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)

            repository.start(LocationConsumer.ForegroundUi)

            val listenerCaptor = argumentCaptor<LocationListener>()
            verify(locationManager).requestLocationUpdates(
                eq(LocationManager.GPS_PROVIDER),
                eq(10_000L),
                eq(0f),
                listenerCaptor.capture(),
                same(mainLooper)
            )

            repository.stop(LocationConsumer.ForegroundUi)

            verify(locationManager).removeUpdates(listenerCaptor.firstValue)
        }
    }

    @Test
    fun `fused backend failure falls back to platform providers`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = true,
            currentTimeMillis = { 1_700_000_200_000L }
        )
        val mainLooper = mock<Looper>()
        val platformLocation = createLocation(59.93, 30.31, 1_700_000_100_000L, provider = LocationManager.GPS_PROVIDER)

        whenever(provider.lastLocation).thenReturn(successTask(null))
        whenever(provider.getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull()))
            .thenReturn(successTask(null))
        whenever(provider.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper)))
            .thenThrow(IllegalStateException("fused backend unavailable"))
        whenever(locationManager.getProviders(true)).thenReturn(listOf(LocationManager.GPS_PROVIDER))
        whenever(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)).thenReturn(platformLocation)

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)

            repository.start(LocationConsumer.ForegroundUi)
        }

        verify(locationManager, atLeastOnce()).requestLocationUpdates(
            eq(LocationManager.GPS_PROVIDER),
            eq(10_000L),
            eq(0f),
            any<LocationListener>(),
            same(mainLooper)
        )
        assertEquals(platformLocation, repository.currentLocation.value)
    }

    @Test
    fun `platform backend stays inactive when no enabled providers are available`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = false,
            currentTimeMillis = { 1_700_000_000_000L }
        )

        whenever(locationManager.getProviders(true)).thenReturn(emptyList())

        repository.start(LocationConsumer.ForegroundUi)

        verify(locationManager, never()).requestLocationUpdates(any<String>(), any<Long>(), any<Float>(), any<LocationListener>(), any())
        assertNull(repository.currentLocation.value)
        assertEquals(LocationFixQuality.NoFix, repository.locationFixStatus.value.quality)
        assertEquals(LocationFixIssue.NoProviders, repository.locationFixStatus.value.issue)
    }

    @Test
    fun `stale initial fix older than five minutes is ignored`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = true,
            currentTimeMillis = { 1_700_000_600_000L }
        )
        val staleLocation = createLocation(
            latitude = 55.75,
            longitude = 37.61,
            time = 1_700_000_200_000L,
            provider = LocationManager.GPS_PROVIDER,
        )
        val mainLooper = mock<Looper>()

        whenever(provider.lastLocation).thenReturn(successTask(staleLocation))
        whenever(provider.getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull()))
            .thenReturn(successTask(null))
        whenever(provider.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper)))
            .thenReturn(successVoidTask())

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)
            repository.start(LocationConsumer.ForegroundUi)
        }

        assertNull(repository.currentLocation.value)
        assertEquals(LocationFixQuality.NoFix, repository.locationFixStatus.value.quality)
        assertEquals(LocationFixIssue.StaleFix, repository.locationFixStatus.value.issue)
    }

    @Test
    fun `mock location is marked suspect and does not replace trusted position`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = true,
            currentTimeMillis = { 1_700_000_300_000L }
        )
        val trustedLocation = createLocation(
            latitude = 55.75,
            longitude = 37.61,
            time = 1_700_000_100_000L,
            provider = LocationManager.GPS_PROVIDER,
        )
        val suspectLocation = createLocation(
            latitude = 55.751,
            longitude = 37.612,
            time = 1_700_000_200_000L,
            provider = LocationManager.GPS_PROVIDER,
        )
        val mainLooper = mock<Looper>()

        whenever(provider.lastLocation).thenReturn(successTask(trustedLocation))
        whenever(provider.getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull()))
            .thenReturn(successTask(null))
        whenever(provider.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper)))
            .thenReturn(successVoidTask())

        mockStatic(Looper::class.java).use { looperMock ->
            mockStatic(LocationCompat::class.java).use { locationCompatMock ->
                locationCompatMock.`when`<Boolean> { LocationCompat.isMock(trustedLocation) }.thenReturn(false)
                locationCompatMock.`when`<Boolean> { LocationCompat.isMock(suspectLocation) }.thenReturn(true)
                looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)
                repository.start(LocationConsumer.ForegroundUi)

                val callbackCaptor = argumentCaptor<LocationCallback>()
                verify(provider).requestLocationUpdates(any<LocationRequest>(), callbackCaptor.capture(), same(mainLooper))
                callbackCaptor.firstValue.onLocationResult(LocationResult.create(listOf(suspectLocation)))
            }
        }

        assertEquals(trustedLocation.latitude, repository.currentLocation.value!!.latitude, 0.0)
        assertEquals(LocationFixQuality.Suspect, repository.locationFixStatus.value.quality)
        assertEquals(LocationFixIssue.MockFix, repository.locationFixStatus.value.issue)
    }

    @Test
    fun `impossible jump above threshold is marked suspect and ignored`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = true,
            currentTimeMillis = { 1_700_000_200_000L }
        )
        val trustedLocation = createLocation(
            latitude = 55.75,
            longitude = 37.61,
            time = 1_700_000_000_000L,
            provider = LocationManager.GPS_PROVIDER,
        )
        val impossibleJump = createLocation(
            latitude = 59.93,
            longitude = 30.31,
            time = 1_700_000_060_000L,
            provider = LocationManager.GPS_PROVIDER,
        )
        whenever(impossibleJump.distanceTo(trustedLocation)).thenReturn(10_000f)
        val mainLooper = mock<Looper>()

        whenever(provider.lastLocation).thenReturn(successTask(trustedLocation))
        whenever(provider.getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull()))
            .thenReturn(successTask(null))
        whenever(provider.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper)))
            .thenReturn(successVoidTask())

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)
            repository.start(LocationConsumer.ForegroundUi)

            val callbackCaptor = argumentCaptor<LocationCallback>()
            verify(provider).requestLocationUpdates(any<LocationRequest>(), callbackCaptor.capture(), same(mainLooper))
            callbackCaptor.firstValue.onLocationResult(LocationResult.create(listOf(impossibleJump)))
        }

        assertEquals(trustedLocation.latitude, repository.currentLocation.value!!.latitude, 0.0)
        assertEquals(LocationFixQuality.Suspect, repository.locationFixStatus.value.quality)
        assertEquals(LocationFixIssue.ImpossibleJump, repository.locationFixStatus.value.issue)
    }

    @Test
    fun `timestamp regression is marked suspect and ignored`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = true,
            currentTimeMillis = { 1_700_000_200_000L }
        )
        val trustedLocation = createLocation(
            latitude = 55.75,
            longitude = 37.61,
            time = 1_700_000_100_000L,
            provider = LocationManager.GPS_PROVIDER,
        )
        val regressedLocation = createLocation(
            latitude = 55.751,
            longitude = 37.612,
            time = 1_700_000_050_000L,
            provider = LocationManager.GPS_PROVIDER,
        )
        val mainLooper = mock<Looper>()

        whenever(provider.lastLocation).thenReturn(successTask(trustedLocation))
        whenever(provider.getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull()))
            .thenReturn(successTask(null))
        whenever(provider.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper)))
            .thenReturn(successVoidTask())

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)
            repository.start(LocationConsumer.ForegroundUi)

            val callbackCaptor = argumentCaptor<LocationCallback>()
            verify(provider).requestLocationUpdates(any<LocationRequest>(), callbackCaptor.capture(), same(mainLooper))
            callbackCaptor.firstValue.onLocationResult(LocationResult.create(listOf(regressedLocation)))
        }

        assertEquals(trustedLocation.latitude, repository.currentLocation.value!!.latitude, 0.0)
        assertEquals(LocationFixQuality.Suspect, repository.locationFixStatus.value.quality)
        assertEquals(LocationFixIssue.TimestampRegression, repository.locationFixStatus.value.issue)
    }

    @Test
    fun `equal timestamp does not trigger timestamp regression`() {
        val provider = mock<FusedLocationProviderClient>()
        val locationManager = mock<LocationManager>()
        val repository = LocationRepositoryImpl(
            provider,
            locationManager,
            googlePlayServicesAvailable = true,
            currentTimeMillis = { 1_700_000_200_000L }
        )
        val trustedLocation = createLocation(
            latitude = 55.75,
            longitude = 37.61,
            time = 1_700_000_100_000L,
            provider = LocationManager.GPS_PROVIDER,
        )
        val sameTimestampLocation = createLocation(
            latitude = 55.751,
            longitude = 37.612,
            time = 1_700_000_100_000L,
            provider = LocationManager.GPS_PROVIDER,
        )
        val mainLooper = mock<Looper>()

        whenever(provider.lastLocation).thenReturn(successTask(trustedLocation))
        whenever(provider.getCurrentLocation(eq(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY), isNull()))
            .thenReturn(successTask(null))
        whenever(provider.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), same(mainLooper)))
            .thenReturn(successVoidTask())

        mockStatic(Looper::class.java).use { looperMock ->
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)
            repository.start(LocationConsumer.ForegroundUi)

            val callbackCaptor = argumentCaptor<LocationCallback>()
            verify(provider).requestLocationUpdates(any<LocationRequest>(), callbackCaptor.capture(), same(mainLooper))
            callbackCaptor.firstValue.onLocationResult(LocationResult.create(listOf(sameTimestampLocation)))
        }

        assertEquals(sameTimestampLocation.latitude, repository.currentLocation.value!!.latitude, 0.0)
        assertEquals(LocationFixQuality.TrustedGps, repository.locationFixStatus.value.quality)
        assertNull(repository.locationFixStatus.value.issue)
    }

    @Test
    fun `location log message redacts raw coordinates`() {
        val location = mock<Location>()
        whenever(location.provider).thenReturn("gps")
        whenever(location.accuracy).thenReturn(4.6f)
        whenever(location.hasAccuracy()).thenReturn(true)
        whenever(location.latitude).thenReturn(55.75)
        whenever(location.longitude).thenReturn(37.61)

        val message = formatLocationLogMessage("Callback fix", location)

        assertTrue(message.contains("provider=gps"))
        assertTrue(message.contains("accuracy=5m"))
        assertTrue(message.contains("coordinates=redacted"))
        assertFalse(message.contains("55.75"))
        assertFalse(message.contains("37.61"))
    }

    private fun successTask(location: Location?): Task<Location> {
        @Suppress("UNCHECKED_CAST")
        return ImmediateSuccessTask(location) as Task<Location>
    }

    private fun successVoidTask(): Task<Void> {
        @Suppress("UNCHECKED_CAST")
        return ImmediateSuccessTask(null) as Task<Void>
    }

    private fun createLocation(
        latitude: Double,
        longitude: Double,
        time: Long,
        provider: String = "gps",
        satellites: Int? = null,
    ): Location {
        val extras = satellites?.let { satelliteCount ->
            mock<android.os.Bundle>().also { bundle ->
                whenever(bundle.getInt("satellites")).thenReturn(satelliteCount)
            }
        }
        return mock<Location>().also { location ->
            whenever(location.provider).thenReturn(provider)
            whenever(location.latitude).thenReturn(latitude)
            whenever(location.longitude).thenReturn(longitude)
            whenever(location.time).thenReturn(time)
            whenever(location.hasAccuracy()).thenReturn(true)
            whenever(location.accuracy).thenReturn(5f)
            whenever(location.extras).thenReturn(extras)
            whenever(location.distanceTo(any())).thenReturn(0f)
        }
    }

    private class ImmediateSuccessTask<TResult>(
        private val result: TResult,
    ) : Task<TResult>() {

        override fun addOnFailureListener(listener: com.google.android.gms.tasks.OnFailureListener): Task<TResult> = this

        override fun addOnFailureListener(activity: Activity, listener: com.google.android.gms.tasks.OnFailureListener): Task<TResult> = this

        override fun addOnFailureListener(executor: Executor, listener: com.google.android.gms.tasks.OnFailureListener): Task<TResult> = this

        override fun addOnSuccessListener(listener: OnSuccessListener<in TResult>): Task<TResult> {
            listener.onSuccess(result)
            return this
        }

        override fun addOnSuccessListener(activity: Activity, listener: OnSuccessListener<in TResult>): Task<TResult> {
            listener.onSuccess(result)
            return this
        }

        override fun addOnSuccessListener(executor: Executor, listener: OnSuccessListener<in TResult>): Task<TResult> {
            listener.onSuccess(result)
            return this
        }

        override fun getException(): Exception? = null

        override fun getResult(): TResult = result

        override fun <X : Throwable> getResult(exceptionType: Class<X>): TResult = result

        override fun isCanceled(): Boolean = false

        override fun isComplete(): Boolean = true

        override fun isSuccessful(): Boolean = true
    }
}
