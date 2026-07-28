package com.shangmentiyu.sportscoach.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * v38 全局深色主题重构：彻底替换浅色背景，统一为深色/黑色主题 UI。
 *
 * 设计令牌（全局深色视觉规范）：
 * - 全局应用背景：#121212
 * - 全局主要卡片背景：#1C1C1E
 * - 所有卡片圆角：统一 20.dp
 * - 主色调：#6C5CE7（柔和紫）
 * - 主标题：#FFFFFF 纯白
 * - 次要信息：#A6A8AB 浅灰
 * - 边框：全局禁用（outline = Transparent）
 *
 * v38 起：取消"亮色/暗色"双轨制，LightColorScheme 与 DarkColorScheme
 * 共用同一套深色令牌，确保不论系统主题如何，App 始终呈现深色 UI。
 */
private val LightColorScheme = lightColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    background = DarkBackground,            // #121212 全局应用背景
    onBackground = DarkOnBackground,        // #FFFFFF 纯白
    surface = CardBackground,                // #1C1C1E 全局卡片背景
    onSurface = CardOnDark,                 // #FFFFFF 纯白
    surfaceVariant = DarkSurfaceVariant,
    outline = DarkOutline,                  // Transparent 全局禁用边框
)

private val DarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    secondary = NightSecondary,
    onSecondary = NightOnSecondary,
    background = NightBackground,           // #121212
    onBackground = NightOnBackground,       // #FFFFFF
    surface = NightSurface,                 // #1C1C1E
    onSurface = NightOnSurface,             // #FFFFFF
    surfaceVariant = NightSurfaceVariant,
    outline = Color.Transparent,
)

@Composable
fun SportsCoachTheme(
    content: @Composable () -> Unit
) {
    // v38：始终使用深色主题，忽略系统 isSystemInDarkTheme()
    val isDark = true
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 深色背景 → 浅色状态栏图标
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
