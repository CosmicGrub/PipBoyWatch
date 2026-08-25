package com.pipboywatch.app.backup

import android.content.Context
import com.pipboywatch.app.holotape.HolotapeRepository
import com.pipboywatch.app.inv.InvRepository
import com.pipboywatch.app.map.RunRepository
import com.pipboywatch.app.notes.NoteRepository
import com.pipboywatch.app.quest.QuestRepository
import java.io.File
import org.json.JSONObject

/**
 * Reverses ExportManager's pipeline through the same repositories every
 * other write in this app goes through — never raw SQL, so a restore
 * can't bypass a repository's own validation (e.g. InvRepository's
 * upsert-by-label logic, HolotapeRepository's trim-after-insert).
 */
class RestoreManager(context: Context) {
    private val appContext = context.applicationContext
    private val invRepository = InvRepository(appContext)
    private val questRepository = QuestRepository(appContext)
    private val holotapeRepository = HolotapeRepository(appContext)
    private val noteRepository = NoteRepository(appContext)
    private val runRepository = RunRepository(appContext)

    data class RestoreResult(
        val invItems: Int,
        val quests: Int,
        val holotapes: Int,
        val notes: Int,
        val runs: Int
    ) {
        val total: Int get() = invItems + quests + holotapes + notes + runs
    }

    private fun backupDir(): File = File(appContext.getExternalFilesDir(null), "backups")

    /** Both tiers land in the same directory ExportManager writes to —
     * lists them newest-first for a simple in-app picker (see the "Wear OS
     * has no general file browser" note on ExportManager). */
    fun listBackupFiles(): List<File> =
        backupDir().listFiles { file -> file.extension == "jsonl" || file.extension == "pbenc" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    suspend fun restorePortable(file: File): RestoreResult {
        val lines = file.readLines(Charsets.UTF_8)
        return restoreLines(lines)
    }

    /** Throws javax.crypto.AEADBadTagException if the passphrase is wrong
     * or the file is corrupted/tampered — let the caller catch that
     * specifically to show "wrong passphrase" rather than a generic error. */
    suspend fun restoreEncrypted(file: File, passphrase: CharArray): RestoreResult {
        val plaintext = BackupCrypto.decrypt(file.readBytes(), passphrase)
        val lines = String(plaintext, Charsets.UTF_8).lines()
        return restoreLines(lines)
    }

    private suspend fun restoreLines(lines: List<String>): RestoreResult {
        var invCount = 0
        var questCount = 0
        var holotapeCount = 0
        var noteCount = 0
        var runCount = 0

        for (line in lines) {
            if (line.isBlank()) continue
            val json = JSONObject(line)
            when (json.optString("kind")) {
                "header" -> Unit // no schema migration needed yet at VERSION 1
                "inv_item" -> {
                    invRepository.restoreItem(BackupSchema.decodeInvItem(json))
                    invCount++
                }
                "quest" -> {
                    questRepository.restoreQuest(BackupSchema.decodeQuest(json))
                    questCount++
                }
                "holotape" -> {
                    holotapeRepository.restoreHolotape(BackupSchema.decodeHolotape(json))
                    holotapeCount++
                }
                "note" -> {
                    noteRepository.restoreNote(BackupSchema.decodeNote(json))
                    noteCount++
                }
                "run" -> {
                    runRepository.restoreRun(BackupSchema.decodeRun(json))
                    runCount++
                }
                // Forward-compatible: a newer export's unknown "kind" is
                // skipped rather than failing the whole restore.
                else -> Unit
            }
        }
        return RestoreResult(invCount, questCount, holotapeCount, noteCount, runCount)
    }
}
