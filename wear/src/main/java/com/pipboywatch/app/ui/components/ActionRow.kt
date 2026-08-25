package com.pipboywatch.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * Full-width tappable terminal row — the app's stand-in for Wear Compose's
 * Button, which defaults to a small circular icon shape that wraps short
 * text mid-word (e.g. "GRANT" -> "GRAN"/"T"). Used for every plain-text
 * tappable action (GRANT, RETRY, transport controls, "+ ADD ..."). Used to
 * be copy-pasted per screen as ActionRow/TransportRow/TerminalActionRow —
 * same body under three names in four files.
 */
@Composable
fun ActionRow(label: String, onClick: () -> Unit) {
    CrtCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(
            text = label,
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
