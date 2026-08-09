package com.pipboywatch.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * Reusable terminal-style bordered card. Use this instead of ad hoc
 * borders/padding so every screen's cards look consistent.
 */
@Composable
fun CrtCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colors.primary, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.primary
            )
            Spacer(Modifier.height(4.dp))
        }
        content()
    }
}
