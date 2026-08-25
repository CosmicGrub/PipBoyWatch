package com.pipboywatch.shared.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Before this registry, "what channels exist" was answerable only by
 * grepping WearableListenerService subclasses across two modules — these
 * tests exercise the declared table itself, plus NOTE's new encode/decode
 * pair (previously two independently hand-typed inline UTF-8 conversions
 * in wear/ and phone/ that happened to agree by discipline, not by
 * sharing code).
 */
class SyncChannelRegistryTest {

    @Test
    fun `the registry lists exactly the two real channels`() {
        assertEquals(setOf("stat", "note"), SyncChannelRegistry.all.map { it.name }.toSet())
    }

    @Test
    fun `STAT is a round-trip channel with both paths set`() {
        val stat = SyncChannelRegistry.STAT
        assertEquals(STAT_REQUEST_PATH, stat.path)
        assertEquals(STAT_RESPONSE_PATH, stat.responsePath)
        assertTrue(stat.requiresTrustedNode)
    }

    @Test
    fun `NOTE is a one-way channel with no response path`() {
        val note = SyncChannelRegistry.NOTE
        assertEquals(NOTE_PATH, note.path)
        assertNull(note.responsePath)
        assertTrue(note.requiresTrustedNode)
    }

    @Test
    fun `every registered path is non-blank and distinct`() {
        val paths = SyncChannelRegistry.all.flatMap { listOfNotNull(it.path, it.responsePath) }
        assertTrue(paths.all { it.isNotBlank() })
        assertEquals("no two channels should ever share a path", paths.size, paths.toSet().size)
    }

    @Test
    fun `encodeNote then decodeNote round-trips plain text`() {
        val text = "Buy RadAway before the next trip to the surface"
        assertEquals(text, decodeNote(encodeNote(text)))
    }

    @Test
    fun `encodeNote trims leading and trailing whitespace`() {
        val encoded = encodeNote("  padded on both sides  ")
        assertEquals("padded on both sides", decodeNote(encoded))
    }

    @Test
    fun `decodeNote trims even if encodeNote was bypassed`() {
        // Defends the "sender and receiver can't quietly drift" claim in
        // SyncChannel's doc comment — decode alone is still safe even if
        // some future caller sends raw bytes without going through encodeNote.
        val rawUntrimmedBytes = "  raw bytes, not pre-trimmed  ".toByteArray(Charsets.UTF_8)
        assertEquals("raw bytes, not pre-trimmed", decodeNote(rawUntrimmedBytes))
    }
}
