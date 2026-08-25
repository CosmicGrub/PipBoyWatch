package com.pipboywatch.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * paceSecondsPerKm/formatPace/formatElapsed are pure functions with no
 * Android framework dependency — exactly the kind the audit flagged as
 * trivially unit-testable but untested. First test suite for this module.
 */
class RunRepositoryTest {

    @Test
    fun `paceSecondsPerKm returns null below the 10m noise floor`() {
        assertNull(paceSecondsPerKm(distanceMeters = 9.9, elapsedSeconds = 100))
        assertNull(paceSecondsPerKm(distanceMeters = 0.0, elapsedSeconds = 100))
    }

    @Test
    fun `paceSecondsPerKm computes seconds per km for a real distance`() {
        // 1000m in 300s = exactly 300 sec/km (5:00/km pace).
        assertEquals(300.0, paceSecondsPerKm(distanceMeters = 1000.0, elapsedSeconds = 300)!!, 0.001)
        // 500m in 150s = also 300 sec/km once normalized to a full km.
        assertEquals(300.0, paceSecondsPerKm(distanceMeters = 500.0, elapsedSeconds = 150)!!, 0.001)
    }

    @Test
    fun `paceSecondsPerKm at exactly the 10m boundary still computes (only below 10m is excluded)`() {
        assertEquals(1000.0, paceSecondsPerKm(distanceMeters = 10.0, elapsedSeconds = 10)!!, 0.001)
    }

    @Test
    fun `formatPace handles null, NaN, and infinite as no-data`() {
        assertEquals("--:--/km", formatPace(null))
        assertEquals("--:--/km", formatPace(Double.NaN))
        assertEquals("--:--/km", formatPace(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `formatPace zero-pads seconds under a minute`() {
        assertEquals("5:00/km", formatPace(300.0))
        assertEquals("1:05/km", formatPace(65.0))
        assertEquals("0:09/km", formatPace(9.0))
    }

    @Test
    fun `formatElapsed omits the hour component under an hour`() {
        assertEquals("0:00", formatElapsed(0))
        assertEquals("0:09", formatElapsed(9))
        assertEquals("1:00", formatElapsed(60))
        assertEquals("59:59", formatElapsed(3599))
    }

    @Test
    fun `formatElapsed includes hours once the run passes an hour`() {
        assertEquals("1:00:00", formatElapsed(3600))
        assertEquals("1:01:01", formatElapsed(3661))
    }
}
