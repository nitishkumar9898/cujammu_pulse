package com.cujammu.pulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cujammu.pulse.ui.screens.AuthScreen
import com.cujammu.pulse.ui.theme.CUJammuPulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CUJammuPulseTheme {
                // No protected navigation graph is installed until this client
                // can establish a server-verified institutional session.
                AuthScreen()
            }
        }
    }
}
