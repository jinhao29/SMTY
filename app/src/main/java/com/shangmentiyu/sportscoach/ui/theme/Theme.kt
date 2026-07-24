package com.shangmentiyu.sportscoach.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * iOS 18 风格主题，跟随系统 Dark Mode 自动切换。
 *
 * - 亮色：iOS SystemBackground（纯白）+ SystemGroupedBackground（浅灰）
 * - 暗色：iOS Dark Mode Black (#000000) + #1C1C1E
 * - 主强调色：SystemBlue（亮色 #007AFF / 暗色 #0A84FF）
 * - 状态栏图标随主题反色：亮色背景=深色图标，暗色背景=浅色图标
 * - 所有页面通过 Scaffold 的 containerColor 控制底色，
 *   建议改用 [appBackground] / [appGroupedBackground] 等 @Composable 函数
 *   以获得自动主题切换能力。
 */
private val LightColorScheme = lightColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    outline = DarkOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    secondary = NightSecondary,
    onSecondary = NightOnSecondary,
    background = NightBackground,
    onBackground = NightOnBackground,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    outline = NightOutline,
)

@Composable
fun SportsCoachTheme(
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
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
