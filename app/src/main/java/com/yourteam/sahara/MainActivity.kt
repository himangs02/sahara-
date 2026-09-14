package com.yourteam.sahara

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.yourteam.sahara.navigation.SaharaNavHost
import com.yourteam.sahara.ui.theme.SaharaTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as SaharaApplication).languageManager.refreshFromConfiguration()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            SaharaTheme {
                SaharaNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }

    override fun onStop() {
        (application as SaharaApplication).voiceManager.stopListening()
        super.onStop()
    }
}
