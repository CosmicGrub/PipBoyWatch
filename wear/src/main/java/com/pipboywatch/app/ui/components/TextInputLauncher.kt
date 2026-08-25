package com.pipboywatch.app.ui.components

import android.app.Activity
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.wear.input.RemoteInputIntentHelper

private const val TEXT_INPUT_KEY = "pipboy_text_input"

/**
 * Standard Wear OS one-off text capture (voice or keyboard, whichever the
 * user picks from the system chooser) — the idiomatic way to grab a short
 * string on a watch without building a custom keyboard. Returns a lambda
 * to invoke to open the input flow; [onResult] fires with the trimmed text
 * if the user completed it.
 */
@Composable
fun rememberTextInputLauncher(label: String, onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.let { RemoteInput.getResultsFromIntent(it) }
                ?.getCharSequence(TEXT_INPUT_KEY)
                ?.toString()
                ?.trim()
            if (!text.isNullOrBlank()) onResult(text)
        }
    }
    return {
        val remoteInput = RemoteInput.Builder(TEXT_INPUT_KEY).setLabel(label).build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        launcher.launch(intent)
    }
}
