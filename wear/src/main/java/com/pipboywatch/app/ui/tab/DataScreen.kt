package com.pipboywatch.app.ui.tab

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pipboywatch.app.backup.ExportManager
import com.pipboywatch.app.backup.RestoreManager
import com.pipboywatch.app.data.HolotapeEntity
import com.pipboywatch.app.data.NoteEntity
import com.pipboywatch.app.data.QuestEntity
import com.pipboywatch.app.holotape.HolotapeRepository
import com.pipboywatch.app.notes.NoteRepository
import com.pipboywatch.app.perks.Perk
import com.pipboywatch.app.perks.PerksRepository
import com.pipboywatch.app.quest.QuestRepository
import com.pipboywatch.app.ui.components.ActionRow
import com.pipboywatch.app.ui.components.CrtCard
import com.pipboywatch.app.ui.components.PipBoyTabScaffold
import com.pipboywatch.app.ui.components.rememberTextInputLauncher
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.launch

@Composable
fun DataScreen() {
    val context = LocalContext.current
    val questRepo = remember { QuestRepository(context) }
    val holotapeRepo = remember { HolotapeRepository(context) }
    val noteRepo = remember { NoteRepository(context) }
    val perksRepo = remember { PerksRepository(context) }
    val exportManager = remember { ExportManager(context) }
    val restoreManager = remember { RestoreManager(context) }
    val coroutineScope = rememberCoroutineScope()
    var backupStatus by remember { mutableStateOf<String?>(null) }

    val quests by questRepo.observeAll().collectAsState(initial = emptyList())
    val holotapes by holotapeRepo.observeRecent().collectAsState(initial = emptyList())
    val notes by noteRepo.observeAll().collectAsState(initial = emptyList())
    var perks by remember { mutableStateOf<List<Perk>>(emptyList()) }
    var holotapeAccessGranted by remember { mutableStateOf(false) }
    var holotapeSettingsUnavailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        perks = perksRepo.computePerks()
        holotapeAccessGranted = holotapeRepo.isAccessGranted()
    }

    val addQuest = rememberTextInputLauncher("New quest") { text ->
        coroutineScope.launch { questRepo.addQuest(text) }
    }
    val addNote = rememberTextInputLauncher("New note") { text ->
        coroutineScope.launch { noteRepo.addFromWatch(text) }
    }
    val exportEncrypted = rememberTextInputLauncher("Backup passphrase") { passphrase ->
        coroutineScope.launch {
            try {
                val result = exportManager.exportEncrypted(passphrase.toCharArray())
                backupStatus = "Exported ${result.rowCount} rows -> ${result.file.name}"
            } catch (e: Exception) {
                backupStatus = "Export failed: ${e.message}"
            }
        }
    }
    val restoreEncrypted = rememberTextInputLauncher("Backup passphrase") { passphrase ->
        coroutineScope.launch {
            val latest = restoreManager.listBackupFiles().firstOrNull { it.extension == "pbenc" }
            if (latest == null) {
                backupStatus = "No encrypted backup found."
            } else {
                try {
                    val result = restoreManager.restoreEncrypted(latest, passphrase.toCharArray())
                    backupStatus = "Restored ${result.total} rows from ${latest.name}"
                } catch (e: AEADBadTagException) {
                    backupStatus = "Restore failed: wrong passphrase or corrupted file"
                } catch (e: Exception) {
                    backupStatus = "Restore failed: ${e.message}"
                }
            }
        }
    }

    PipBoyTabScaffold(title = "DATA") {
        SectionHeader("QUESTS")
        if (quests.isEmpty()) {
            CrtCard { Text("No active quests.", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.body2) }
            Spacer(Modifier.height(6.dp))
        } else {
            quests.forEach { quest ->
                QuestRow(
                    quest = quest,
                    onToggle = { coroutineScope.launch { questRepo.toggleDone(quest) } },
                    onDelete = { coroutineScope.launch { questRepo.remove(quest) } }
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        ActionRow("+ ADD QUEST", onClick = addQuest)
        Spacer(Modifier.height(16.dp))

        SectionHeader("HOLOTAPES")
        when {
            !holotapeAccessGranted -> {
                CrtCard(title = "ACCESS REQUIRED") {
                    Text(
                        "Grant notification access to log holotapes.",
                        color = MaterialTheme.colors.primary,
                        style = MaterialTheme.typography.body2
                    )
                    Spacer(Modifier.height(8.dp))
                    if (holotapeSettingsUnavailable) {
                        Text(
                            "This watch doesn't expose that settings screen directly. " +
                                "Try the paired phone's Galaxy Wearable app instead.",
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption2
                        )
                    } else {
                        ActionRow("GRANT") {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (e: ActivityNotFoundException) {
                                holotapeSettingsUnavailable = true
                            }
                        }
                    }
                }
            }
            holotapes.isEmpty() -> {
                CrtCard { Text("No recent notifications.", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.body2) }
            }
            else -> {
                holotapes.forEach { tape ->
                    HolotapeRow(tape)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionHeader("PERKS")
        perks.forEach { perk ->
            PerkRow(perk)
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(16.dp))

        SectionHeader("NOTES")
        if (notes.isEmpty()) {
            CrtCard { Text("No notes yet.", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.body2) }
            Spacer(Modifier.height(6.dp))
        } else {
            notes.forEach { note ->
                NoteRow(note)
                Spacer(Modifier.height(6.dp))
            }
        }
        ActionRow("+ ADD NOTE", onClick = addNote)
        Spacer(Modifier.height(16.dp))

        SectionHeader("BACKUP")
        if (backupStatus != null) {
            CrtCard(title = "STATUS") {
                Text(backupStatus.orEmpty(), color = MaterialTheme.colors.primary, style = MaterialTheme.typography.caption1)
            }
            Spacer(Modifier.height(6.dp))
        }
        ActionRow("EXPORT (PLAIN)") {
            coroutineScope.launch {
                try {
                    val result = exportManager.exportPortable()
                    backupStatus = "Exported ${result.rowCount} rows -> ${result.file.name}"
                } catch (e: Exception) {
                    backupStatus = "Export failed: ${e.message}"
                }
            }
        }
        ActionRow("EXPORT (ENCRYPTED)", onClick = exportEncrypted)
        ActionRow("RESTORE LATEST PLAIN") {
            coroutineScope.launch {
                val latest = restoreManager.listBackupFiles().firstOrNull { it.extension == "jsonl" }
                if (latest == null) {
                    backupStatus = "No plain backup found."
                } else {
                    try {
                        val result = restoreManager.restorePortable(latest)
                        backupStatus = "Restored ${result.total} rows from ${latest.name}"
                    } catch (e: Exception) {
                        backupStatus = "Restore failed: ${e.message}"
                    }
                }
            }
        }
        ActionRow("RESTORE LATEST ENCRYPTED", onClick = restoreEncrypted)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.primary.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun QuestRow(quest: QuestEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    CrtCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (quest.isDone) "[X] ${quest.text}" else "[ ] ${quest.text}",
                color = MaterialTheme.colors.primary,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.weight(1f).clickable(onClick = onToggle)
            )
            Text(
                text = "x",
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(start = 8.dp).clickable(onClick = onDelete)
            )
        }
    }
}

@Composable
private fun HolotapeRow(tape: HolotapeEntity) {
    CrtCard(title = tape.appLabel) {
        Text(tape.title, color = MaterialTheme.colors.primary, style = MaterialTheme.typography.body2)
        if (tape.text.isNotBlank()) {
            Text(tape.text, color = MaterialTheme.colors.primary, style = MaterialTheme.typography.caption1)
        }
        Text(
            DateUtils.getRelativeTimeSpanString(tape.postedAt).toString(),
            color = MaterialTheme.colors.primary.copy(alpha = 0.5f),
            style = MaterialTheme.typography.caption2
        )
    }
}

@Composable
private fun PerkRow(perk: Perk) {
    val color = if (perk.unlocked) MaterialTheme.colors.primary else MaterialTheme.colors.primary.copy(alpha = 0.4f)
    CrtCard {
        Text(
            text = if (perk.unlocked) "[UNLOCKED] ${perk.name}" else "[LOCKED] ${perk.name}",
            color = color,
            style = MaterialTheme.typography.body2
        )
        Text(perk.detail, color = color, style = MaterialTheme.typography.caption2)
    }
}

@Composable
private fun NoteRow(note: NoteEntity) {
    CrtCard(title = if (note.source == "phone") "FROM PHONE" else "ON WATCH") {
        Text(note.text, color = MaterialTheme.colors.primary, style = MaterialTheme.typography.body2)
        Text(
            DateUtils.getRelativeTimeSpanString(note.receivedAt).toString(),
            color = MaterialTheme.colors.primary.copy(alpha = 0.5f),
            style = MaterialTheme.typography.caption2
        )
    }
}
