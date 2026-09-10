package com.seeksky.braingames

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.seeksky.braingames.ui.theme.BrainGamesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrainGamesTheme(dynamicColor = false) {
                SchulteGameApp()
            }
        }
    }
}
