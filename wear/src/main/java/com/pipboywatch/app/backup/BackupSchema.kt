package com.pipboywatch.app.backup

import com.pipboywatch.app.data.HolotapeEntity
import com.pipboywatch.app.data.InvItemEntity
import com.pipboywatch.app.data.NoteEntity
import com.pipboywatch.app.data.QuestEntity
import com.pipboywatch.app.data.RunEntity
import org.json.JSONObject

/**
 * JSON Lines encoding for the backup/export system — one JSON object per
 * line, each self-describing via a "kind" field so RestoreManager can
 * dispatch on it without needing a fixed line order or a separate schema
 * per section. Both the portable (plaintext) and restore (encrypted)
 * tiers share this exact body; only what happens to the resulting string
 * afterward differs (see ExportManager).
 *
 * Deliberately independent of Room/DatabasePassphrase — this is an
 * app-level export format, not a database dump, so it survives a schema
 * migration on either side as long as these encode/decode functions do.
 *
 * IDs are never round-tripped: every decode*() function ignores the
 * source row's id (RestoreManager always inserts as id=0, or upserts by
 * a natural key for INV — see InvRepository.restoreItem) since backed-up
 * primary keys have no meaning against a different (or the same, later)
 * database's auto-increment sequence.
 */
object BackupSchema {
    const val VERSION = 1

    // -- header -------------------------------------------------------

    fun encodeHeader(exportedAtEpochMillis: Long): String = JSONObject().apply {
        put("kind", "header")
        put("schemaVersion", VERSION)
        put("exportedAtEpochMillis", exportedAtEpochMillis)
    }.toString()

    fun kindOf(line: String): String = JSONObject(line).optString("kind")

    // -- inv_item -------------------------------------------------------

    fun encodeInvItem(item: InvItemEntity): String = JSONObject().apply {
        put("kind", "inv_item")
        put("label", item.label)
        put("sortOrder", item.sortOrder)
        put("isSystemLinked", item.isSystemLinked)
        put("isChecked", item.isChecked)
    }.toString()

    fun decodeInvItem(json: JSONObject): InvItemEntity = InvItemEntity(
        label = json.getString("label"),
        sortOrder = json.getInt("sortOrder"),
        isSystemLinked = json.getBoolean("isSystemLinked"),
        isChecked = json.getBoolean("isChecked")
    )

    // -- quest ----------------------------------------------------------

    fun encodeQuest(quest: QuestEntity): String = JSONObject().apply {
        put("kind", "quest")
        put("text", quest.text)
        put("isDone", quest.isDone)
        put("createdAt", quest.createdAt)
    }.toString()

    fun decodeQuest(json: JSONObject): QuestEntity = QuestEntity(
        text = json.getString("text"),
        isDone = json.getBoolean("isDone"),
        createdAt = json.getLong("createdAt")
    )

    // -- holotape ---------------------------------------------------------

    fun encodeHolotape(tape: HolotapeEntity): String = JSONObject().apply {
        put("kind", "holotape")
        put("appLabel", tape.appLabel)
        put("title", tape.title)
        put("text", tape.text)
        put("postedAt", tape.postedAt)
    }.toString()

    fun decodeHolotape(json: JSONObject): HolotapeEntity = HolotapeEntity(
        appLabel = json.getString("appLabel"),
        title = json.getString("title"),
        text = json.getString("text"),
        postedAt = json.getLong("postedAt")
    )

    // -- note -------------------------------------------------------------

    fun encodeNote(note: NoteEntity): String = JSONObject().apply {
        put("kind", "note")
        put("text", note.text)
        put("receivedAt", note.receivedAt)
        put("source", note.source)
    }.toString()

    fun decodeNote(json: JSONObject): NoteEntity = NoteEntity(
        text = json.getString("text"),
        receivedAt = json.getLong("receivedAt"),
        source = json.getString("source")
    )

    // -- run ----------------------------------------------------------------

    // Same -1-as-null sentinel StatSync.kt already uses for heart rate —
    // avgHeartRateBpm is never legitimately negative.
    private const val NO_HEART_RATE = -1.0

    fun encodeRun(run: RunEntity): String = JSONObject().apply {
        put("kind", "run")
        put("startTime", run.startTime)
        put("endTime", run.endTime)
        put("distanceMeters", run.distanceMeters)
        put("elevationGainMeters", run.elevationGainMeters)
        put("avgHeartRateBpm", run.avgHeartRateBpm ?: NO_HEART_RATE)
        put("routePointsJson", run.routePointsJson)
    }.toString()

    fun decodeRun(json: JSONObject): RunEntity = RunEntity(
        startTime = json.getLong("startTime"),
        endTime = json.getLong("endTime"),
        distanceMeters = json.getDouble("distanceMeters"),
        elevationGainMeters = json.getDouble("elevationGainMeters"),
        avgHeartRateBpm = json.getDouble("avgHeartRateBpm").takeIf { it >= 0.0 },
        routePointsJson = json.getString("routePointsJson")
    )
}
