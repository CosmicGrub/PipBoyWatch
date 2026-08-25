package com.pipboywatch.app.backup

import com.pipboywatch.app.data.HolotapeEntity
import com.pipboywatch.app.data.InvItemEntity
import com.pipboywatch.app.data.NoteEntity
import com.pipboywatch.app.data.QuestEntity
import com.pipboywatch.app.data.RunEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip tests for the backup wire format. IDs are intentionally NOT
 * asserted to round-trip — see BackupSchema's own doc comment on why
 * decode*() never reads a source row's id back.
 */
class BackupSchemaTest {

    @Test
    fun `inv_item round-trips`() {
        val item = InvItemEntity(id = 99, label = "Wallet", sortOrder = 2, isSystemLinked = false, isChecked = true)
        val decoded = BackupSchema.decodeInvItem(JSONObject(BackupSchema.encodeInvItem(item)))
        assertEquals(item.label, decoded.label)
        assertEquals(item.sortOrder, decoded.sortOrder)
        assertEquals(item.isSystemLinked, decoded.isSystemLinked)
        assertEquals(item.isChecked, decoded.isChecked)
        assertEquals(0L, decoded.id) // never round-tripped
    }

    @Test
    fun `quest round-trips`() {
        val quest = QuestEntity(id = 5, text = "Find the Water Chip", isDone = true, createdAt = 1_700_000_000_000)
        val decoded = BackupSchema.decodeQuest(JSONObject(BackupSchema.encodeQuest(quest)))
        assertEquals(quest.text, decoded.text)
        assertEquals(quest.isDone, decoded.isDone)
        assertEquals(quest.createdAt, decoded.createdAt)
    }

    @Test
    fun `holotape round-trips`() {
        val tape = HolotapeEntity(id = 7, appLabel = "Messages", title = "Mom", text = "Call me back", postedAt = 1_700_000_000_000)
        val decoded = BackupSchema.decodeHolotape(JSONObject(BackupSchema.encodeHolotape(tape)))
        assertEquals(tape.appLabel, decoded.appLabel)
        assertEquals(tape.title, decoded.title)
        assertEquals(tape.text, decoded.text)
        assertEquals(tape.postedAt, decoded.postedAt)
    }

    @Test
    fun `note round-trips`() {
        val note = NoteEntity(id = 3, text = "Buy RadAway", receivedAt = 1_700_000_000_000, source = "phone")
        val decoded = BackupSchema.decodeNote(JSONObject(BackupSchema.encodeNote(note)))
        assertEquals(note.text, decoded.text)
        assertEquals(note.receivedAt, decoded.receivedAt)
        assertEquals(note.source, decoded.source)
    }

    @Test
    fun `run round-trips including a real heart rate`() {
        val run = RunEntity(
            id = 1, startTime = 1000, endTime = 2000, distanceMeters = 500.5,
            elevationGainMeters = 12.0, avgHeartRateBpm = 142.0, routePointsJson = "[]"
        )
        val decoded = BackupSchema.decodeRun(JSONObject(BackupSchema.encodeRun(run)))
        assertEquals(run.startTime, decoded.startTime)
        assertEquals(run.endTime, decoded.endTime)
        assertEquals(run.distanceMeters, decoded.distanceMeters, 0.001)
        assertEquals(run.elevationGainMeters, decoded.elevationGainMeters, 0.001)
        assertEquals(run.avgHeartRateBpm, decoded.avgHeartRateBpm)
        assertEquals(run.routePointsJson, decoded.routePointsJson)
    }

    @Test
    fun `run with null heart rate round-trips through the -1 sentinel`() {
        val run = RunEntity(startTime = 1000, endTime = 2000, distanceMeters = 0.0, elevationGainMeters = 0.0, avgHeartRateBpm = null, routePointsJson = "[]")
        val encoded = BackupSchema.encodeRun(run)
        assertEquals(true, encoded.contains("\"avgHeartRateBpm\":-1"))
        val decoded = BackupSchema.decodeRun(JSONObject(encoded))
        assertNull(decoded.avgHeartRateBpm)
    }

    @Test
    fun `header carries the schema version and kindOf identifies every line`() {
        val header = BackupSchema.encodeHeader(1_700_000_000_000)
        assertEquals("header", BackupSchema.kindOf(header))
        assertEquals(BackupSchema.VERSION, JSONObject(header).getInt("schemaVersion"))

        val quest = BackupSchema.encodeQuest(QuestEntity(text = "x", createdAt = 0))
        assertEquals("quest", BackupSchema.kindOf(quest))
    }
}
