package com.pipboywatch.shared.sync

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "PipBoyRequestTracker"

/**
 * Generalizes the request/response correlation pattern PhoneStatRelay used
 * to hand-roll for exactly one channel (STAT): mint an id before sending a
 * request, reject any reply whose id doesn't match the most recently
 * minted one for that channel (a stale reply from an abandoned or retried
 * request), and always accept a blank id as an unsolicited push (e.g. the
 * phone's "Sync to Watch Now" button, which isn't answering any specific
 * request).
 *
 * One instance covers every channel a caller needs this pattern for —
 * "channel" is just a caller-chosen key (a relay with only one kind of
 * request can use a single constant; a relay correlating more than one
 * kind of round-trip can key by whichever name distinguishes them). What
 * this replaces: a hand-rolled `@Volatile outstandingRequestId` plus a
 * bare `MutableStateFlow` per call site, which is exactly what
 * PhoneStatRelay did before — fine for one channel, but the pattern this
 * app was always going to need to copy-paste a second and third time.
 *
 * Also closes a real gap the hand-rolled version never had: nothing ever
 * timed out. A request whose reply never arrives (dropped message,
 * disconnected node — a real, already-observed failure mode on this
 * transport) left the channel waiting forever. [expireStale] gives a
 * caller a way to sweep those out on a schedule of its choosing.
 *
 * Thread-safety: every channel's outstanding-request bookkeeping lives in
 * a [ConcurrentHashMap], and the two operations that mutate it —
 * [complete] accepting a reply, and [expireStale] timing one out — each
 * use the map's atomic compare-and-remove so a reply and an expiry (or a
 * retry's fresh [mint]) landing at the same instant can't clobber each
 * other's ideas of what the current outstanding request is. Per-channel
 * value flows are created with [ConcurrentHashMap.computeIfAbsent], not
 * the plain `getOrPut` extension, specifically because `getOrPut` isn't
 * atomic on a shared map — two racing first-time [observe] calls could
 * otherwise each construct their own [MutableStateFlow] and hand back
 * two different instances before the map converges on one.
 */
class PendingRequestTracker<T> {

    private data class Pending(val requestId: String, val expiresAtEpochMillis: Long)

    /** Present only while a channel has an unanswered request outstanding. */
    private val pending = ConcurrentHashMap<String, Pending>()
    private val values = ConcurrentHashMap<String, MutableStateFlow<T?>>()

    private fun valueFlow(channel: String): MutableStateFlow<T?> =
        values.computeIfAbsent(channel) { MutableStateFlow(null) }

    /**
     * Registers a new outstanding request for [channel] and returns its
     * id. Any reply for a previously minted id on this channel becomes
     * stale the instant this is called — including a reply for the
     * request this one is retrying.
     *
     * [timeoutMillis] is how long [expireStale] should wait before
     * treating this request as abandoned; callers should pick it based on
     * how long a reply on their transport can plausibly take, not reuse
     * one default across unrelated channels.
     */
    fun mint(channel: String, timeoutMillis: Long, nowEpochMillis: Long = System.currentTimeMillis()): String {
        val requestId = newRequestId()
        pending[channel] = Pending(requestId, nowEpochMillis + timeoutMillis)
        return requestId
    }

    /**
     * Records [value] as the result of [requestId] on [channel]. A blank
     * [requestId] is always accepted (an unsolicited push). Otherwise,
     * returns false without touching [observe]'s value if [requestId]
     * doesn't match the outstanding one for [channel] — already logged
     * here, so the caller can simply treat false as "silently drop."
     */
    fun complete(channel: String, requestId: String, value: T): Boolean {
        if (requestId.isNotBlank()) {
            val current = pending[channel]
            if (current == null || current.requestId != requestId) {
                Log.d(TAG, "Dropping stale reply channel=$channel requestId=$requestId outstanding=${current?.requestId}")
                return false
            }
            // Only clear the outstanding marker if it's still the exact
            // request we just matched against — if a retry's mint() raced
            // in between the check above and here, this must not clobber
            // that newer request's own bookkeeping. This MUST be the
            // atomic 2-arg remove(key, value), not a check-then-remove:
            // the latter reopens exactly the race this comment describes,
            // since a mint() could land in the gap between the check and
            // the removal.
            pending.remove(channel, current)
        }
        valueFlow(channel).value = value
        return true
    }

    /** The latest accepted value for [channel] — null before any reply has
     * arrived, after [clear], or after this channel's request has expired
     * via [expireStale] (which only drops the outstanding-request marker,
     * not the last value; see its own doc). */
    fun observe(channel: String): StateFlow<T?> = valueFlow(channel).asStateFlow()

    /** Clears both the outstanding request and the last observed value for
     * [channel] — e.g. when a screen is re-entered and any request from a
     * prior visit should stop being awaited. */
    fun clear(channel: String) {
        pending.remove(channel)
        valueFlow(channel).value = null
    }

    /**
     * Drops the outstanding-request marker for every channel whose
     * [mint] deadline has passed as of [nowEpochMillis], so a late reply
     * for it is rejected by [complete] as stale instead of the channel
     * waiting on it forever. Returns the set of channels actually
     * expired — a caller with an opinion about what "timed out" should
     * look like (e.g. PhoneStatRelay setting its value to Unavailable)
     * checks for its channel in the result and reacts; expiry alone
     * deliberately doesn't touch [observe]'s value; a raw timeout isn't
     * itself a well-typed T for a caller-agnostic generic tracker to
     * fabricate.
     *
     * Safe to call on any schedule from any thread — a channel that gets
     * a fresh [mint] (a retry) between when its old deadline passed and
     * when this runs is not expired, since the map's current entry no
     * longer matches the one this sweep is deciding about.
     */
    fun expireStale(nowEpochMillis: Long): Set<String> {
        val expired = mutableSetOf<String>()
        for ((channel, current) in pending) {
            if (nowEpochMillis < current.expiresAtEpochMillis) continue
            // Atomic compare-and-remove, same reasoning as complete()
            // above — a concurrent mint() (a retry) must not be wiped out
            // by a sweep that was only ever entitled to remove the OLD
            // entry it read at the top of this loop iteration.
            if (pending.remove(channel, current)) {
                Log.d(TAG, "Expired stale outstanding request channel=$channel requestId=${current.requestId}")
                expired += channel
            }
        }
        return expired
    }
}
