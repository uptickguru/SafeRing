package com.safering.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.safering.android.ui.navigation.NavGraph
import com.safering.android.ui.theme.SafeRingTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity entry point for SafeRing.
 * Hosts Jetpack Compose navigation graph with Material 3 theming.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeRingTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph()
                }
            }
        }
    }
}
