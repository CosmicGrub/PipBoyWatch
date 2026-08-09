package com.pipboywatch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pipboywatch.app.ui.theme.PipBoyTheme

/**
 * Phase 0 shell — confirms the wifi-ADB deploy loop works end to end.
 * Phase 1 replaces this with the rotary-driven home dial (see the
 * implementation plan in docs/superpowers/plans/).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PipBoyTheme {
                HelloPipBoyScreen()
            }
        }
    }
}

@Composable
fun HelloPipBoyScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "HELLO, PIP-BOY",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.title3
        )
    }
}
