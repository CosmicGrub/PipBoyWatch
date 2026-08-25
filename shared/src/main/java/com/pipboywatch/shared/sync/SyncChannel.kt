package com.pipboywatch.shared.sync

/**
 * One phone<->watch sync channel — the declared "table" this app's two
 * real channels (STAT, NOTE) used to answer with ad hoc per-feature
 * wiring instead: path constants independently declared in whichever
 * file happened to send or receive them, with no single place listing
 * what channels exist at all. NOTE_PATH in particular was hand-duplicated
 * across wear/ and phone/ with no shared source of truth — exactly the
 * failure mode STAT_REQUEST_PATH/STAT_RESPONSE_PATH already escaped by
 * living in :shared, just never extended to NOTE.
 *
 * [path] is the Data Layer message path that starts this channel's data
 * flow: for STAT that's the watch pinging the phone for a snapshot; for
 * a one-way push like NOTE, [path] carries the actual payload directly
 * and [responsePath] stays null — there's no separate "answer" to model.
 *
 * [requiresTrustedNode] documents whether this channel's receiver is
 * expected to reject messages from a node it isn't currently paired with
 * (see isFromTrustedNode) — every real channel today expects this; the
 * field exists so that expectation is visible in one table rather than
 * implicit in each listener service independently remembering to call it.
 *
 * Deliberately NOT generic over a payload type with encode/decode
 * function references as fields: with exactly two channels and no
 * runtime code ever iterating this table to dispatch generically (each
 * listener service already calls its own channel's specific encode/decode
 * functions directly), that would be exactly the "more machinery than a
 * solo two-writer app needs" this system's own pitch explicitly excluded
 * a generalized plugin-style registry for. encode/decode stay as ordinary
 * top-level functions per channel (see StatSync.kt, and encodeNote/
 * decodeNote below) — this class exists to make "what channels exist and
 * where" a single declared list, not to add indirection around calling them.
 */
data class SyncChannel(
    val name: String,
    val path: String,
    val responsePath: String? = null,
    val requiresTrustedNode: Boolean = true
)

/** Phone -> watch only, no reply: SendNoteActivity's Share-sheet sender
 * pushes text here, PipBoyMessageListenerService stores it as a note. */
const val NOTE_PATH = "/pipboy/note"

/** The wire format is just trimmed UTF-8 text — trivial, but centralizing
 * it here means the sender and receiver can't quietly drift on whether
 * trimming happens on the send side, the receive side, both, or neither,
 * the same way STAT_RESPONSE_PATH's format lives in one place instead of
 * being re-derived at each call site. */
fun encodeNote(text: String): ByteArray = text.trim().toByteArray(Charsets.UTF_8)

fun decodeNote(payload: ByteArray): String = String(payload, Charsets.UTF_8).trim()
