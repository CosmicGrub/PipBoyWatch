package com.pipboywatch.app.backup

import android.content.Context
import com.pipboywatch.app.holotape.HolotapeRepository
import com.pipboywatch.app.inv.InvRepository
import com.pipboywatch.app.map.RunRepository
import com.pipboywatch.app.notes.NoteRepository
import com.pipboywatch.app.quest.QuestRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes both backup tiers. Deliberately targets app-private external
 * storage (getExternalFilesDir) rather than the Storage Access
 * Framework's document picker: Wear OS has no general-purpose file
 * browser most watches ship with, so an ACTION_CREATE_DOCUMENT intent is
 * a real risk of ActivityNotFoundException on-device rather than a
 * standard flow the way it is on phone/desktop Android. Files land
 * somewhere guaranteed to exist and be reachable via adb (already this
 * project's own established way of getting things off the watch) —
 * RestoreManager lists them for a simple in-app picker instead of
 * relying on a system one that may not exist here.
 */
class ExportManager(context: Context) {
    private val appContext = context.applicationContext
    private val invRepository = InvRepository(appContext)
    private val questRepository = QuestRepository(appContext)
    private val holotapeRepository = HolotapeRepository(appContext)
    private val noteRepository = NoteRepository(appContext)
    private val runRepository = RunRepository(appContext)

    data class ExportResult(val file: File, val rowCount: Int)

    private fun backupDir(): File =
        File(appContext.getExternalFilesDir(null), "backups").apply { mkdirs() }

    private fun timestampedName(extension: String): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        return "pipboy-backup-$stamp.$extension"
    }

    /** The shared JSON-Lines body underlying both tiers — header first,
     * then every row from all five tables. */
    private suspend fun buildLines(): List<String> {
        val lines = mutableListOf(BackupSchema.encodeHeader(System.currentTimeMillis()))
        invRepository.getAllOnce().forEach { lines += BackupSchema.encodeInvItem(it) }
        questRepository.getAllOnce().forEach { lines += BackupSchema.encodeQuest(it) }
        holotapeRepository.getAllOnce().forEach { lines += BackupSchema.encodeHolotape(it) }
        noteRepository.getAllOnce().forEach { lines += BackupSchema.encodeNote(it) }
        runRepository.getAllOnce().forEach { lines += BackupSchema.encodeRun(it) }
        return lines
    }

    /** Portable tier: plain JSON Lines, human/tool-inspectable, no
     * passphrase. Meant for migration, not for storing somewhere untrusted
     * — it's exactly as sensitive as the data it contains. */
    suspend fun exportPortable(): ExportResult {
        val lines = buildLines()
        val file = File(backupDir(), timestampedName("jsonl"))
        file.writeText(lines.joinToString("\n"), Charsets.UTF_8)
        return ExportResult(file, lines.size - 1) // -1 for the header line
    }

    /** Restore tier: same body, AES-256-GCM encrypted (see BackupCrypto)
     * with a passphrase the user supplies and must remember — there is no
     * recovery path if it's lost, by design (no separate key escrow to
     * defeat the whole point of encrypting it). */
    suspend fun exportEncrypted(passphrase: CharArray): ExportResult {
        val lines = buildLines()
        val body = lines.joinToString("\n").toByteArray(Charsets.UTF_8)
        val encrypted = BackupCrypto.encrypt(body, passphrase)
        val file = File(backupDir(), timestampedName("pbenc"))
        file.writeBytes(encrypted)
        return ExportResult(file, lines.size - 1)
    }
}
