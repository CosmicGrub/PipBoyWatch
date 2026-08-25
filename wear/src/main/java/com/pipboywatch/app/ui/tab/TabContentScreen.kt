package com.pipboywatch.app.ui.tab

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pipboywatch.app.ui.PipBoyTab
import com.pipboywatch.app.ui.components.CrtCard
import com.pipboywatch.app.ui.components.PipBoyTabScaffold

/**
 * Placeholder content for a tab that doesn't have a real screen yet.
 * INV/DATA/MAP/RADIO still use this; STAT graduated to StatScreen in Phase 2.
 */
@Composable
fun PlaceholderTabScreen(tab: PipBoyTab) {
    PipBoyTabScaffold(title = tab.label) {
        CrtCard(title = "STATUS") {
            Text(
                text = "Real ${tab.label} content arrives in a later phase.",
                color = MaterialTheme.colors.primary,
                style = MaterialTheme.typography.body2
            )
        }
        Spacer(Modifier.height(8.dp))
        repeat(6) { i ->
            CrtCard(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "Placeholder row ${i + 1}",
                    color = MaterialTheme.colors.primary,
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}
