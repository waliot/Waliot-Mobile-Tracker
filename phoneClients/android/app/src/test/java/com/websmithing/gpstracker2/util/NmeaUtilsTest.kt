package com.websmithing.gpstracker2.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NmeaUtilsTest {

    @Test
    fun `latitudeToDdm carries rounded minutes into degrees`() {
        val ddm = NmeaUtils.latitudeToDdm(12.999999999, ";")

        assertEquals("1300.0000;N", ddm)
    }

    @Test
    fun `longitudeToDdm carries rounded minutes into degrees for western hemisphere`() {
        val ddm = NmeaUtils.longitudeToDdm(-179.999999999, ";")

        assertEquals("18000.0000;W", ddm)
    }

    @Test
    fun `latitudeToDdm preserves normal conversion away from rounding boundary`() {
        val ddm = NmeaUtils.latitudeToDdm(53.90821, ";")

        assertEquals("5354.4926;N", ddm)
    }
}
