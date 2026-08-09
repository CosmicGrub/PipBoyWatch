package com.pipboywatch.app.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pipboywatch.app.ui.theme.PipBoyTheme

/**
 * Shown when the user taps the privacy-policy link on Health Connect's
 * permission grant screen. Required by Health Connect for the app to be
 * eligible to request health permissions at all.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PipBoyTheme {
                RationaleScreen()
            }
        }
    }
}

@Composable
private fun RationaleScreen() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "Pip-Boy reads steps, heart rate, sleep, and workout data " +
                "from Health Connect to show on the STAT tab. This data stays " +
                "on your watch and is never transmitted anywhere.",
            modifier = Modifier.verticalScroll(rememberScrollState()),
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
