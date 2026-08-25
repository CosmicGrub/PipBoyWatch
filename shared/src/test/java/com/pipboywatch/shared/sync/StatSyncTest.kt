package com.pipboywatch.shared.sync

import com.pipboywatch.shared.health.StatSnapshot
import com.pipboywatch.shared.health.WorkoutSummary
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire format used to be hand-duplicated across wear/ and phone/ with
 * a comment warning to keep the two in sync by hand — exactly the kind of
 * thing that should have a round-trip test, per the audit's own
 * recommendation. First real test suite in this project.
 */
class StatSyncTest {

    @Test
    fun `round-trips a full snapshot with workouts`() {
        val snapshot = StatSnapshot(
            steps = 8231,
            activeMinutes = 42,
            latestHeartRateBpm = 71,
            sleepMinutesLastNight = 430,
            recentWorkouts = listOf(
                WorkoutSummary("Morning Run", Instant.ofEpochMilli(1_700_000_000_000), 35),
                WorkoutSummary("Evening Walk", Instant.ofEpochMilli(1_700_050_000_000), 20)
            ),
            hasStepStreak = true
        )

        val encoded = "req-1|" + encodeStatSnapshot(snapshot)
        val reply = decodeStatReply(encoded)

        assertEquals("req-1", reply.requestId)
        val result = reply.result as StatDecodeResult.Success
        assertEquals(snapshot.steps, result.snapshot.steps)
        assertEquals(snapshot.activeMinutes, result.snapshot.activeMinutes)
        assertEquals(snapshot.latestHeartRateBpm, result.snapshot.latestHeartRateBpm)
        assertEquals(snapshot.sleepMinutesLastNight, result.snapshot.sleepMinutesLastNight)
        assertEquals(true, result.snapshot.hasStepStreak)
        assertEquals(2, result.snapshot.recentWorkouts.size)
        assertEquals("Morning Run", result.snapshot.recentWorkouts[0].title)
        assertEquals(35, result.snapshot.recentWorkouts[0].durationMinutes)
    }

    @Test
    fun `null heart rate encodes as the -1 sentinel and decodes back to null`() {
        val snapshot = StatSnapshot(
            steps = 100, activeMinutes = 0, latestHeartRateBpm = null,
            sleepMinutesLastNight = 0, recentWorkouts = emptyList()
        )
        val encoded = encodeStatSnapshot(snapshot)
        assertTrue("expected -1 sentinel in $encoded", encoded.contains("|-1|"))

        val result = decodeStatReply("|$encoded").result as StatDecodeResult.Success
        assertNull(result.snapshot.latestHeartRateBpm)
    }

    @Test
    fun `empty request id round-trips as an unsolicited push`() {
        val reply = decodeStatReply("|UNAVAILABLE")
        assertEquals("", reply.requestId)
        assertEquals(StatDecodeResult.Unavailable, reply.result)
    }

    @Test
    fun `NEEDS_PERMISSION sentinel decodes correctly`() {
        val reply = decodeStatReply("abc|NEEDS_PERMISSION")
        assertEquals("abc", reply.requestId)
        assertEquals(StatDecodeResult.NeedsPermission, reply.result)
    }

    @Test
    fun `malformed body degrades to Unavailable rather than throwing`() {
        val reply = decodeStatReply("abc|not-a-real-payload")
        assertEquals(StatDecodeResult.Unavailable, reply.result)
    }

    @Test
    fun `a workout title containing the delimiter characters is sanitized, not corrupting the format`() {
        val snapshot = StatSnapshot(
            steps = 1, activeMinutes = 1, latestHeartRateBpm = 1, sleepMinutesLastNight = 1,
            recentWorkouts = listOf(WorkoutSummary("Run | Walk, Fast; Pace", Instant.ofEpochMilli(1000), 10))
        )
        val result = decodeStatReply("|" + encodeStatSnapshot(snapshot)).result as StatDecodeResult.Success
        assertEquals(1, result.snapshot.recentWorkouts.size)
        assertFalse(result.snapshot.recentWorkouts[0].title.contains('|'))
        assertFalse(result.snapshot.recentWorkouts[0].title.contains(';'))
        assertFalse(result.snapshot.recentWorkouts[0].title.contains(','))
    }

    @Test
    fun `two requestIds minted in a row are never equal`() {
        val a = newRequestId()
        val b = newRequestId()
        assertTrue(a != b)
    }
}
