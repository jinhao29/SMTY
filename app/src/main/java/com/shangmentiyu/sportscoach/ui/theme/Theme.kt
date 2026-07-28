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
 * v36 视觉重构：全局深色卡片标准主题。
 *
 * 设计令牌（基于学员列表最新截图确立的全局 UI 标准）：
 * - 主色调：柔和紫 #6C5CE7（亮色）/ #8A79E9（暗色，更亮保证对比度）
 * - 辅色：青色渐变 #3A7BD5 → #00D2FF（头像、签到按钮、数据标签）
 * - 亮色背景：极浅灰白 #F5F7FA（v36 取消 #F4F6F9）
 * - 全局卡片：深色 #1C1C1E（统一替换原浅色白卡）
 * - 文字：主 #FFFFFF / 次 #A0A0A5（v36 取消 #8E8E93，更通透）
 * - 圆角：卡片 20dp / 按钮 12dp
 * - 阴影：offsetY=4dp, blurRadius=12dp, color=#0A000000（极柔和）
 * - 边框：全局禁用（outline = Transparent），仅依赖阴影与深色卡片色区隔
 * - 字号：标题 16sp / 副标题 14sp（见 [Typography]）
 *
 * 说明：
 * - 亮色 surface 保留 #FFFFFF，仅供底部导航栏等需要白底的组件使用
 * - 所有页面内容卡片统一使用 [BaseDarkCard] 组件（#1C1C1E）
 * - 通过 Scaffold 的 containerColor 应用 #F5F7FA 底色
 * - 建议使用 [appBackground] / [appGroupedBackground] 等 @Composable 函数
 *   以获得自动主题切换能力
 */
private val LightColorScheme = lightColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    background = DarkBackground,           // #F5F7FA 极浅灰白
    onBackground = DarkOnBackground,
    surface = DarkSurface,                 // #FFFFFF 仅供导航栏等组件使用
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    // v36：全局禁用 border，outline 设为 Transparent
    outline = Color.Transparent,
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
    // v35：全局禁用 border，outline 设为 Transparent
    outline = Color.Transparent,
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
