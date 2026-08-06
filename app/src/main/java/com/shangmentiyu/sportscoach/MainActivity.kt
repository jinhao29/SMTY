package com.shangmentiyu.sportscoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.ui.SportsApp
import com.shangmentiyu.sportscoach.ui.settings.SettingsViewModel
import com.shangmentiyu.sportscoach.ui.theme.SportsCoachTheme
import org.koin.androidx.compose.koinViewModel

/**
 * 主 Activity，承载 Compose UI。
 * 悬浮窗服务的启停由设置页开关控制，不再在此自动启动。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // === v48 终极打磨：应用主题（三态深色模式）===
            // 亮色/暗色由「设置 → 深色模式」开关控制，null = 跟随系统。
            // 注意：此前 SportsCoachTheme 从未被调用，MaterialTheme.colorScheme
            // 一直使用 M3 默认色板；本次在 Activity 根节点统一注入主题。
            val settingsVm: SettingsViewModel = koinViewModel()
            val darkTheme by settingsVm.darkTheme.collectAsStateWithLifecycle()
            SportsCoachTheme(darkTheme = darkTheme) {
                SportsApp()
            }
        }
    }
}
