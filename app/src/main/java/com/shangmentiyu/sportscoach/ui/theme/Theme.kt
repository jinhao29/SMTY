package com.shangmentiyu.sportscoach.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 珊瑚橙主题入口，支持三态深色模式：
 *
 * - [darkTheme] = null（默认）：跟随系统 Dark Mode 自动切换
 * - [darkTheme] = true：强制深色（设置 → 深色模式 开关）
 * - [darkTheme] = false：强制亮色
 *
 * - 亮色：暖白背景 #FAFAFA + 纯白卡片
 * - 暗色：纯黑背景 #000000 + #2C2C2E 卡片
 * - 主强调色：珊瑚橙（亮色 #FF6B47 / 暗色 #FF8A65）
 * - 状态栏图标随主题反色：亮色背景=深色图标，暗色背景=浅色图标
 * - 所有页面通过 Scaffold 的 containerColor 控制底色，
 *   建议使用 [appBackground] / [appGroupedBackground] 等 @Composable 函数
 *   以获得自动主题切换能力。
 */
@Composable
fun SportsCoachTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    // 三态：null 跟随系统；非 null 以手动开关为准
    val isDark = darkTheme ?: systemDark
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 状态栏图标颜色跟随主题：
            // 亮色背景 → 深色图标
            // 暗色背景 → 浅色图标
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !isDark
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
