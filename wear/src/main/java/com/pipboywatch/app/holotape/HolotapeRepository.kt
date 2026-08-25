package com.pipboywatch.app.holotape

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.pipboywatch.app.data.HolotapeEntity
import com.pipboywatch.app.data.PipBoyDatabase
import kotlinx.coroutines.flow.Flow

class HolotapeRepository(private val context: Context) {
    private val dao = PipBoyDatabase.getInstance(context.applicationContext).holotapeDao()

    fun observeRecent(): Flow<List<HolotapeEntity>> = dao.observeRecent()

    /** No runtime permission dialog exists for this — user grants it via Settings. */
    fun isAccessGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /**
     * Used by PipBoyNotificationListenerService. Holotapes is, by design,
     * a general in-universe log of *every* notification the listener sees
     * (not scoped to media apps) — themed as "Holotapes" the same way real
     * Fallout holotapes are recorded messages/logs, not literally audio
     * cassettes. That's a real privacy-relevant scope: it mirrors title,
     * body text, and app name of anything the user is notified about
     * system-wide, including messaging/banking/etc. apps, onto this
     * on-watch log. If that scope ever needs narrowing, this is the one
     * place to do it — kept in the repository rather than the listener
     * service so there's a single source of truth for what gets persisted.
     */
    suspend fun logNotification(appLabel: String, title: String, text: String, postedAt: Long) {
        if (title.isBlank() && text.isBlank()) return
        dao.insert(HolotapeEntity(appLabel = appLabel, title = title, text = text, postedAt = postedAt))
        dao.trimOldEntries()
    }
}
