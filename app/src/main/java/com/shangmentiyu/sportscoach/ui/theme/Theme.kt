package com.shangmentiyu.sportscoach.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 珊瑚橙主题入口，支持三态深色模式：
 *
 * - [darkTheme] = null（默认）：跟随系统 Dark Mode 自动切换
 * - [darkTheme] = true：强制深色（设置 → 深色模式 开关）
 * - [darkTheme] = false：强制亮色
 *
 * - 亮色：浅冷灰背景 #F5F6F8 + 纯白卡片
 * - 暗色：纯黑背景 #000000 + #2C2C2E 卡片
 * - 主强调色：珊瑚橙（亮色 #FF6B47 / 暗色 #FF8A65）
 * - 状态栏图标随主题反色：亮色背景=深色图标，暗色背景=浅色图标
 * - 所有页面通过 Scaffold 的 containerColor 控制底色，
 *   建议使用 [appBackground] / [appGroupedBackground] 等 @Composable 函数
 *   以获得自动主题切换能力。
 *
 * 主题切换动效（v49 修复切换突兀）：
 * - ColorScheme 每个字段都包一层 [animateColorAsState]，从旧色板平滑过渡到新色板
 * - durationMillis=350 / FastOutSlowInEasing 与 ThemeSwitch 指示器 260ms 衔接，
 *   视觉上一颗蓝球滑动到位时背景/卡片/文字恰好同步收尾
 * - 比 [androidx.compose.animation.Crossfade] 更干净：无重影、无二次组合开销
 * - 不影响 [isAppearanceLightStatusBars]（系统级属性，无插值）
 */
@Composable
fun SportsCoachTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    // 三态：null 跟随系统；非 null 以手动开关为准
    val isDark = darkTheme ?: systemDark
    val target = if (isDark) DarkColorScheme else LightColorScheme
    val animated = target.animated()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 状态栏图标颜色跟随主题：
            // 亮色背景 → 深色图标
            // 暗色背景 → 浅色图标
            // 注意：isAppearanceLightStatusBars 是 WindowInsetsController 的布尔属性，
            // 系统无插值支持，只能在 isDark 翻转的瞬间整段反色，无法做成渐变过渡。
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !isDark
        }
    }
    MaterialTheme(
        colorScheme = animated,
        typography = Typography,
        content = content
    )
}

/**
 * 主题切换动效规格：350ms / FastOutSlowInEasing。
 *
 * 选 350ms 是为了与 [com.shangmentiyu.sportscoach.ui.theme.ThemeSwitch]
 * 的 260ms 指示器滑动在时间上错开：指示器先到位，背景/卡片/文字再缓缓跟上，
 * 整段交互感受是"球先停，颜色追上来"，比一齐跳变更自然。
 */
private val ThemeTransitionSpec: AnimationSpec<Color> =
    tween(durationMillis = 350, easing = FastOutSlowInEasing)

/**
 * 把 [ColorScheme] 的每一个 Color 字段都套一层 [animateColorAsState]，
 * 组件读取 `MaterialTheme.colorScheme.*` 时拿到的是正在插值的中间色，
 * 实现"整屏颜色平滑过渡"而非瞬切。
 */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = ThemeTransitionSpec
    return copy(
        primary = animateColorAsState(primary, spec).value,
        onPrimary = animateColorAsState(onPrimary, spec).value,
        primaryContainer = animateColorAsState(primaryContainer, spec).value,
        onPrimaryContainer = animateColorAsState(onPrimaryContainer, spec).value,
        secondary = animateColorAsState(secondary, spec).value,
        onSecondary = animateColorAsState(onSecondary, spec).value,
        tertiary = animateColorAsState(tertiary, spec).value,
        onTertiary = animateColorAsState(onTertiary, spec).value,
        background = animateColorAsState(background, spec).value,
        onBackground = animateColorAsState(onBackground, spec).value,
        surface = animateColorAsState(surface, spec).value,
        onSurface = animateColorAsState(onSurface, spec).value,
        surfaceVariant = animateColorAsState(surfaceVariant, spec).value,
        onSurfaceVariant = animateColorAsState(onSurfaceVariant, spec).value,
        outline = animateColorAsState(outline, spec).value,
        outlineVariant = animateColorAsState(outlineVariant, spec).value,
        // M3 1.3.1 其余字段当前主题未使用，但保持对称动画，避免将来接入时回到瞬切
        error = animateColorAsState(error, spec).value,
        onError = animateColorAsState(onError, spec).value,
        errorContainer = animateColorAsState(errorContainer, spec).value,
        onErrorContainer = animateColorAsState(onErrorContainer, spec).value,
        scrim = animateColorAsState(scrim, spec).value,
        inverseSurface = animateColorAsState(inverseSurface, spec).value,
        inverseOnSurface = animateColorAsState(inverseOnSurface, spec).value,
        inversePrimary = animateColorAsState(inversePrimary, spec).value,
        surfaceTint = animateColorAsState(surfaceTint, spec).value,
    )
}
