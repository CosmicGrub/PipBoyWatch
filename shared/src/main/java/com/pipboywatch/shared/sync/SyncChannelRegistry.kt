package com.pipboywatch.shared.sync

/** See StatSync.kt for the STAT wire format these paths carry. Moved here
 * (out of StatSync.kt) so both of this app's real channels' paths live in
 * the same place — same package, so every existing
 * `import com.pipboywatch.shared.sync.STAT_REQUEST_PATH` across wear/ and
 * phone/ keeps resolving unchanged; only the file they're physically
 * declared in moved. */
const val STAT_REQUEST_PATH = "/pipboy/stat/request"
const val STAT_RESPONSE_PATH = "/pipboy/stat"

/**
 * Every sync channel this app declares, in one place. Before this, the
 * only way to answer "what channels exist between phone and watch" was
 * to grep for WearableListenerService subclasses across two modules —
 * NOTE_PATH in particular was two independently hand-typed string
 * literals that happened to agree, with nothing enforcing that beyond
 * discipline. A second real channel now means adding one entry here
 * (see SyncChannel's own doc comment on what's deliberately NOT here:
 * no generic dispatch, no runtime plugin surface).
 */
object SyncChannelRegistry {
    val STAT = SyncChannel(
        name = "stat",
        path = STAT_REQUEST_PATH,
        responsePath = STAT_RESPONSE_PATH
    )

    val NOTE = SyncChannel(
        name = "note",
        path = NOTE_PATH
    )

    val all: List<SyncChannel> = listOf(STAT, NOTE)
}
