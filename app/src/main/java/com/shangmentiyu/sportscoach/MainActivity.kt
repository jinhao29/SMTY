package com.shangmentiyu.sportscoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shangmentiyu.sportscoach.ui.SportsApp

/**
 * 主 Activity，承载 Compose UI。
 * 悬浮窗服务的启停由设置页开关控制，不再在此自动启动。
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
