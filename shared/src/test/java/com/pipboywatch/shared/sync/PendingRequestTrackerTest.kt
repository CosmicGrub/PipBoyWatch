package com.pipboywatch.shared.sync

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PhoneStatRelay used to hand-roll exactly this correlation pattern for
 * one channel with a @Volatile id and a bare MutableStateFlow — these
 * tests exercise the generalized version, including the timeout hook
 * PhoneStatRelay never had before, and the concurrency guarantees its own
 * doc comment claims (a retry racing an expiry must never clobber the
 * wrong request).
 */
class PendingRequestTrackerTest {

    @Test
    fun `a matching reply is accepted and observable`() {
        val tracker = PendingRequestTracker<String>()
        val id = tracker.mint("stat", timeoutMillis = 10_000)

        val accepted = tracker.complete("stat", id, "hello")

        assertTrue(accepted)
        assertEquals("hello", tracker.observe("stat").value)
    }

    @Test
    fun `a mismatched non-blank requestId is rejected and does not touch the observed value`() {
        val tracker = PendingRequestTracker<String>()
        tracker.mint("stat", timeoutMillis = 10_000)

        val accepted = tracker.complete("stat", "some-other-id", "should not land")

        assertFalse(accepted)
        assertNull(tracker.observe("stat").value)
    }

    @Test
    fun `a stale reply does not overwrite a value already set by the real reply`() {
        val tracker = PendingRequestTracker<String>()
        val id = tracker.mint("stat", timeoutMillis = 10_000)
        tracker.complete("stat", id, "real reply")

        // A slow duplicate/retransmit of an older, already-superseded id.
        val stale = tracker.complete("stat", "an-old-id-from-before", "stale reply")

        assertFalse(stale)
        assertEquals("real reply", tracker.observe("stat").value)
    }

    @Test
    fun `a blank requestId is always accepted even with a request outstanding`() {
        val tracker = PendingRequestTracker<String>()
        tracker.mint("stat", timeoutMillis = 10_000)

        val accepted = tracker.complete("stat", "", "unsolicited push")

        assertTrue(accepted)
        assertEquals("unsolicited push", tracker.observe("stat").value)
    }

    @Test
    fun `clear resets both the outstanding request and the observed value`() {
        val tracker = PendingRequestTracker<String>()
        val id = tracker.mint("stat", timeoutMillis = 10_000)
        tracker.complete("stat", id, "some value")

        tracker.clear("stat")

        assertNull(tracker.observe("stat").value)
        // The old id is meaningless post-clear; a late reply for it must
        // not be treated as matching some new, not-yet-minted request.
        assertFalse(tracker.complete("stat", id, "late reply after clear"))
    }

    @Test
    fun `expireStale does nothing before the deadline`() {
        val tracker = PendingRequestTracker<String>()
        val id = tracker.mint("stat", timeoutMillis = 10_000, nowEpochMillis = 1_000L)

        val expired = tracker.expireStale(nowEpochMillis = 5_000L)

        assertTrue(expired.isEmpty())
        // The original request is still live — a real reply for it still lands.
        assertTrue(tracker.complete("stat", id, "still on time"))
    }

    @Test
    fun `expireStale drops the outstanding marker once the deadline passes, rejecting a later reply as stale`() {
        val tracker = PendingRequestTracker<String>()
        val id = tracker.mint("stat", timeoutMillis = 10_000, nowEpochMillis = 1_000L)

        val expired = tracker.expireStale(nowEpochMillis = 20_000L)

        assertEquals(setOf("stat"), expired)
        // Expiry alone must not fabricate a value for a caller-agnostic T.
        assertNull(tracker.observe("stat").value)
        // A reply that finally shows up after the timeout is now stale.
        assertFalse(tracker.complete("stat", id, "too late"))
    }

    @Test
    fun `a retry minted after the original deadline is not clobbered by a sweep for the old request`() {
        val tracker = PendingRequestTracker<String>()
        tracker.mint("stat", timeoutMillis = 10_000, nowEpochMillis = 1_000L)

        // The retry happens (mint()s a fresh id) before anything ever
        // calls expireStale for the original — e.g. the caller retried on
        // its own schedule rather than waiting for a sweep.
        val retryId = tracker.mint("stat", timeoutMillis = 10_000, nowEpochMillis = 15_000L)

        // A sweep now runs, using "now" far enough past the ORIGINAL
        // deadline (1_000 + 10_000 = 11_000) that a naive implementation
        // comparing only by deadline would wrongly expire the retry too.
        val expired = tracker.expireStale(nowEpochMillis = 16_000L)

        assertTrue("retry's own deadline (25_000) hasn't passed yet", expired.isEmpty())
        assertTrue(tracker.complete("stat", retryId, "retry succeeded"))
    }

    @Test
    fun `channels are tracked independently`() {
        val tracker = PendingRequestTracker<String>()
        val statId = tracker.mint("stat", timeoutMillis = 10_000)
        val invId = tracker.mint("inv", timeoutMillis = 10_000)

        tracker.complete("stat", statId, "stat value")

        assertEquals("stat value", tracker.observe("stat").value)
        assertNull(tracker.observe("inv").value)
        // "inv"'s own outstanding request is untouched by "stat" completing.
        assertTrue(tracker.complete("inv", invId, "inv value"))
        assertEquals("inv value", tracker.observe("inv").value)
    }

    @Test
    fun `mint always returns a distinct id`() {
        val tracker = PendingRequestTracker<String>()
        val a = tracker.mint("stat", timeoutMillis = 10_000)
        val b = tracker.mint("stat", timeoutMillis = 10_000)
        assertNotEquals(a, b)
    }

    @Test
    fun `concurrent mint and complete across many channels never throws and every completed channel ends up observable`() {
        val tracker = PendingRequestTracker<Int>()
        val channelCount = 8
        val roundsPerChannel = 200
        val pool = Executors.newFixedThreadPool(channelCount)
        val ready = CountDownLatch(channelCount)
        val go = CountDownLatch(1)
        val failures = AtomicInteger(0)

        try {
            repeat(channelCount) { channelIndex ->
                pool.submit {
                    val channel = "channel-$channelIndex"
                    ready.countDown()
                    go.await()
                    try {
                        repeat(roundsPerChannel) { round ->
                            val id = tracker.mint(channel, timeoutMillis = 10_000)
                            tracker.complete(channel, id, round)
                            // Interleave an expiry sweep from every worker to
                            // pressure the same compare-and-remove path
                            // complete() itself relies on.
                            tracker.expireStale(System.currentTimeMillis())
                        }
                    } catch (e: Throwable) {
                        failures.incrementAndGet()
                    }
                }
            }
            ready.await(5, TimeUnit.SECONDS)
            go.countDown()
            pool.shutdown()
            assertTrue("worker pool did not finish in time", pool.awaitTermination(10, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }

        assertEquals("no worker should have thrown", 0, failures.get())
        repeat(channelCount) { channelIndex ->
            val value = tracker.observe("channel-$channelIndex").value
            assertEquals(roundsPerChannel - 1, value)
        }
    }
}
