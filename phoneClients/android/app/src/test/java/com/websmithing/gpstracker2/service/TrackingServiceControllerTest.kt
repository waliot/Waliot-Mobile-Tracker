package com.websmithing.gpstracker2.service

import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import kotlin.test.assertSame

class TrackingServiceControllerTest {

    @Test
    fun `startTracking dispatches foreground start intent with start action`() {
        val context = mock<Context>()
        stubServiceDispatch(context)

        withConstructedIntent(context, { startTracking() }) { constructedIntent, constructorArgs ->
            verify(context).startForegroundService(constructedIntent)
            verifyNoMoreInteractions(context)
            verify(constructedIntent).setAction(TrackingService.ACTION_START_SERVICE)
            assertIntentConstructorArgs(constructorArgs, context)
        }
    }

    @Test
    fun `stopTracking dispatches background start intent with stop action`() {
        val context = mock<Context>()
        stubServiceDispatch(context)

        withConstructedIntent(context, { stopTracking() }) { constructedIntent, constructorArgs ->
            verify(context).startService(constructedIntent)
            verifyNoMoreInteractions(context)
            verify(constructedIntent).setAction(TrackingService.ACTION_STOP_SERVICE)
            assertIntentConstructorArgs(constructorArgs, context)
        }
    }

    @Test
    fun `refreshTracking dispatches background start intent with refresh action`() {
        val context = mock<Context>()
        stubServiceDispatch(context)

        withConstructedIntent(context, { refreshTracking() }) { constructedIntent, constructorArgs ->
            verify(context).startService(constructedIntent)
            verifyNoMoreInteractions(context)
            verify(constructedIntent).setAction(TrackingService.ACTION_REFRESH_SERVICE)
            assertIntentConstructorArgs(constructorArgs, context)
        }
    }

    @Test
    fun `ensureTrackingRunning delegates to foreground start path`() {
        val context = mock<Context>()
        stubServiceDispatch(context)

        withConstructedIntent(context, { ensureTrackingRunning() }) { constructedIntent, constructorArgs ->
            verify(context, times(1)).startForegroundService(constructedIntent)
            verifyNoMoreInteractions(context)
            verify(constructedIntent).setAction(TrackingService.ACTION_START_SERVICE)
            assertIntentConstructorArgs(constructorArgs, context)
        }
    }

    private fun withConstructedIntent(
        context: Context,
        action: DefaultTrackingServiceController.() -> Unit,
        assertions: (constructedIntent: Intent, constructorArgs: List<Any?>) -> Unit
    ) {
        val controller = DefaultTrackingServiceController(context)
        val constructorArgs = mutableListOf<List<Any?>>()

        mockConstruction(Intent::class.java) { _, constructionContext ->
            constructorArgs += constructionContext.arguments().toList()
        }.use { mockedIntent ->
            controller.action()

            assertions(mockedIntent.constructed().single(), constructorArgs.single())
        }
    }

    private fun assertIntentConstructorArgs(args: List<Any?>, expectedContext: Context) {
        assertEquals(2, args.size)
        assertSame(expectedContext, args[0])
        assertEquals(TrackingService::class.java, args[1])
    }

    private fun stubServiceDispatch(context: Context) {
        whenever(context.startService(any())).thenReturn(null)
        whenever(context.startForegroundService(any())).thenReturn(null)
    }
}
