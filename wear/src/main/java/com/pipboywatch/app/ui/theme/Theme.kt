package com.pipboywatch.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

// CRT phosphor palette — everything else in the app should pull from these
// via MaterialTheme.colors rather than hardcoding hex values.
val PhosphorGreen = Color(0xFF33FF33)
val PhosphorGreenDim = Color(0xFF1AAA1A)
val CrtBlack = Color(0xFF0A1A0A)
val AlertRed = Color(0xFFFF3333)

private val PipBoyColors = Colors(
    primary = PhosphorGreen,
    primaryVariant = PhosphorGreenDim,
    secondary = PhosphorGreen,
    secondaryVariant = PhosphorGreenDim,
    background = CrtBlack,
    surface = CrtBlack,
    error = AlertRed,
    onPrimary = CrtBlack,
    onSecondary = CrtBlack,
    onBackground = PhosphorGreen,
    onSurface = PhosphorGreen,
    onError = CrtBlack
)

@Composable
fun PipBoyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = PipBoyColors,
        content = content
    )
}
