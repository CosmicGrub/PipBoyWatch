package com.pipboywatch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pipboywatch.app.ui.PipBoyApp
import com.pipboywatch.app.ui.theme.PipBoyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PipBoyTheme {
                PipBoyApp()
            }
        }
    }
}
