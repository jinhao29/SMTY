package com.shangmentiyu.sportscoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shangmentiyu.sportscoach.ui.SportsApp

/**
 * 主 Activity，承载 Compose UI。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SportsApp()
        }
    }
}
