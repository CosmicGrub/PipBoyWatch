package com.pipboywatch.app.data.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pipboywatch.app.holotape.HolotapeRepository
import com.pipboywatch.app.media.MediaSessionHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Feeds the DATA tab's "Holotapes" log, and — since a MediaStyle
 * notification carries its session token as an extra — doubles as how
 * RADIO finds the phone's currently-playing media. Requires the user to
 * manually grant notification access via Settings; there's no runtime
 * permission dialog for this, unlike Health Connect or location.
 */
class PipBoyNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val holotapeRepository by lazy { HolotapeRepository(applicationContext) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshMediaSession()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        logHolotape(sbn)
        extractMediaToken(sbn)?.let { token ->
            MediaSessionHolder.update(MediaController(applicationContext, token))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // If the notification backing our current controller just vanished,
        // re-scan whatever's left rather than assume nothing's playing.
        if (extractMediaToken(sbn) != null) {
            refreshMediaSession()
        }
    }

    private fun refreshMediaSession() {
        val token = activeNotifications
            ?.mapNotNull { extractMediaToken(it) }
            ?.firstOrNull()
        MediaSessionHolder.update(token?.let { MediaController(applicationContext, it) })
    }

    private fun extractMediaToken(sbn: StatusBarNotification): MediaSession.Token? {
        val extras = sbn.notification.extras
        return if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        }
    }

    private fun logHolotape(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()

        val appLabel = try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            sbn.packageName
        }

        serviceScope.launch {
            holotapeRepository.logNotification(appLabel, title, text, sbn.postTime)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
