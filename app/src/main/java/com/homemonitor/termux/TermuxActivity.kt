package com.google.android.system.termux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * TermuxActivity
 *
 * Activity hosting the TermuxShellScreen Compose interface with Android 15 edge-to-edge support.
 */
class TermuxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TermuxShellScreen()
        }
    }
}
