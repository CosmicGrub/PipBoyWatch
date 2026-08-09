package com.pipboywatch.app.data.notifications

import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pipboywatch.app.data.HolotapeEntity
import com.pipboywatch.app.data.PipBoyDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Feeds the DATA tab's "Holotapes" log. Requires the user to manually grant
 * notification access via Settings — there's no runtime permission dialog
 * for this, unlike Health Connect or location.
 */
class PipBoyNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return // nothing worth logging

        val appLabel = try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            sbn.packageName
        }

        serviceScope.launch {
            val dao = PipBoyDatabase.getInstance(applicationContext).holotapeDao()
            dao.insert(
                HolotapeEntity(
                    appLabel = appLabel,
                    title = title,
                    text = text,
                    postedAt = sbn.postTime
                )
            )
            dao.trimOldEntries()
        }
    }
}
