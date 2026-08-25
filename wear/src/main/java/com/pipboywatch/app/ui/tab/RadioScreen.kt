package com.pipboywatch.app.ui.tab

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pipboywatch.app.holotape.HolotapeRepository
import com.pipboywatch.app.media.MediaSessionHolder
import com.pipboywatch.app.ui.components.ActionRow
import com.pipboywatch.app.ui.components.CrtCard
import com.pipboywatch.app.ui.components.PipBoyTabScaffold

@Composable
fun RadioScreen() {
    val context = LocalContext.current
    val holotapeRepository = remember { HolotapeRepository(context) } // same permission gate as Holotapes
    var accessGranted by remember { mutableStateOf(false) }
    var settingsUnavailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { accessGranted = holotapeRepository.isAccessGranted() }

    val controller by MediaSessionHolder.controller.collectAsState()
    var title by remember { mutableStateOf<String?>(null) }
    var artist by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        val current = controller
        if (current == null) {
            title = null
            artist = null
            isPlaying = false
            onDispose {}
        } else {
            fun refresh() {
                val metadata = current.metadata
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                isPlaying = current.playbackState?.state == PlaybackState.STATE_PLAYING
            }
            refresh()
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
                override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
            }
            current.registerCallback(callback)
            onDispose { current.unregisterCallback(callback) }
        }
    }

    PipBoyTabScaffold(title = "RADIO") {
        when {
            !accessGranted -> {
                CrtCard(title = "ACCESS REQUIRED") {
                    Text(
                        "Grant notification access to control phone media.",
                        color = MaterialTheme.colors.primary,
                        style = MaterialTheme.typography.body2
                    )
                    Spacer(Modifier.height(8.dp))
                    if (settingsUnavailable) {
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
                                settingsUnavailable = true
                            }
                        }
                    }
                }
            }
            controller == null -> {
                CrtCard(title = "NO SIGNAL") {
                    Text(
                        "Nothing playing on the phone right now.",
                        color = MaterialTheme.colors.primary,
                        style = MaterialTheme.typography.body2
                    )
                }
            }
            else -> {
                CrtCard(title = "NOW PLAYING") {
                    Text(
                        title ?: "Unknown title",
                        color = MaterialTheme.colors.primary,
                        style = MaterialTheme.typography.body2
                    )
                    if (!artist.isNullOrBlank()) {
                        Text(artist!!, color = MaterialTheme.colors.primary, style = MaterialTheme.typography.caption1)
                    }
                    Text(
                        if (isPlaying) "[PLAYING]" else "[PAUSED]",
                        color = MaterialTheme.colors.primary.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.caption2
                    )
                }
                Spacer(Modifier.height(8.dp))
                ActionRow("<< PREVIOUS") { controller?.transportControls?.skipToPrevious() }
                Spacer(Modifier.height(6.dp))
                ActionRow(if (isPlaying) "PAUSE" else "PLAY") {
                    if (isPlaying) controller?.transportControls?.pause() else controller?.transportControls?.play()
                }
                Spacer(Modifier.height(6.dp))
                ActionRow("NEXT >>") { controller?.transportControls?.skipToNext() }
                Spacer(Modifier.height(8.dp))
                ActionRow("VOL -") { controller?.adjustVolume(AudioManager.ADJUST_LOWER, 0) }
                Spacer(Modifier.height(6.dp))
                ActionRow("VOL +") { controller?.adjustVolume(AudioManager.ADJUST_RAISE, 0) }
            }
        }
    }
}
