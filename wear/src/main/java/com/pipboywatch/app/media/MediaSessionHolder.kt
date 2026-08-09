package com.pipboywatch.app.media

import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared holder so PipBoyNotificationListenerService (which extracts the
 * phone's bridged MediaSession token from its notification) and the RADIO
 * screen (which reads/controls it) can talk without IPC — both run in the
 * same app process, so a plain singleton is enough.
 */
object MediaSessionHolder {
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    fun update(controller: MediaController?) {
        _controller.value = controller
    }
}
