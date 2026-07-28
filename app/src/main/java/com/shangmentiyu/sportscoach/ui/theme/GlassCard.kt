package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 活力珊瑚橙主题卡片组件库（Dark Mode 感知）。
 *
 * 设计原则（v39 令牌统一）：
 * - 亮色：纯白底 + 10pt 圆角（iOS Inset Grouped 结构）
 * - 暗色：自动切换至 iOS Dark 表面色 #2C2C2E
 * - 顶部可选 4dp 珊瑚橙渐变装饰条（BrandGradientStart→End），注入活力色不破坏整体克制
 * - 区块标题使用珊瑚橙强调色（暗色下自动切到 #FF8A65）
 * - TopAppBar 使用暖白背景（与 #FAFAFA 主背景融合），保持深色文字可读性
 *
 * 所有页面通过 GlassCard / glassTopAppBarColors() 自动获得统一主题 + Dark Mode。
 */

/**
 * 活力卡片：iOS 风格白底卡片 + 可选顶部渐变装饰条。
 *
 * @param accentGradient 是否显示顶部 4dp 渐变装饰条（默认 true）
 * @param glow 是否显示极淡蓝色边框（强调态）
 * @param contentPadding 内边距，默认 16dp
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    accentGradient: Boolean = true,
    contentPadding: androidx.compose.ui.unit.Dp = Spacing.cardPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    // 通过 MaterialTheme.colorScheme 自动跟随系统 Dark Mode
    val containerColor = MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // 顶部 4dp 珊瑚橙渐变装饰条（BrandGradientStart→End）
            if (accentGradient) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(BrandGradientStart, BrandGradientEnd)
                            )
                        )
                )
            }
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    }
}

/**
 * 区块标题：珊瑚橙强调色小节标题。
 */
@Composable
fun GlassSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    )
}

/**
 * 区块副标题：组标题下方的一行说明文字。
 */
@Composable
fun GlassSectionSubtitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    )
}

/**
 * 活力风格 TopAppBar 配色：暖白背景 + 深色文字 + 珊瑚橙返回按钮。
 *
 * 11 个页面通过此函数自动获得统一的活力顶栏风格 + Dark Mode。
 * 暖白背景与主背景 #FAFAFA 融合；暗色切换为 iOS Dark 表面色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glassTopAppBarColors(): TopAppBarColors {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val containerStart = if (isDark) NightSurface else LightBackground
    val scrolledColor = if (isDark) NightSurface else LightSurface
    return TopAppBarDefaults.topAppBarColors(
        containerColor = containerStart,
        scrolledContainerColor = scrolledColor,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        navigationIconContentColor = MaterialTheme.colorScheme.primary,
        actionIconContentColor = MaterialTheme.colorScheme.primary
    )
}

/**
 * 活力主按钮渐变色对：珊瑚橙渐变（BrandGradientStart→End）。
 * 用于 Button 的 containerColor（通过 Brush.linearGradient 在 Box 中模拟）。
 */
val VitalButtonGradient = listOf(BrandGradientStart, BrandGradientEnd)

/**
 * 活力 AppBar 暖色背景（保留别名兼容历史引用）。
 * 实际为暖白主背景 LightBackground，与 #FAFAFA 融合形成层次。
 */
@Deprecated("v39：使用 LightBackground", replaceWith = ReplaceWith("LightBackground"))
val VitalAppBarBgStart get() = LightBackground
@Deprecated("v39：使用 LightBackground", replaceWith = ReplaceWith("LightBackground"))
val VitalAppBarBgEnd get() = LightBackground

/**
 * 活力大标题：可选全宽珊瑚橙渐变背景头部，或仅珊瑚橙文字。
 *
 * 用于无 TopAppBar 的列表/设置页顶部，营造活力感。
 *
 * @param withGradientBg 是否使用全宽珊瑚橙渐变背景（默认 false，仅文字着色）
 *                       false 时标题用珊瑚橙；true 时背景珊瑚橙渐变 + 白色文字
 */
@Composable
fun VitalLargeTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    withGradientBg: Boolean = false
) {
    if (withGradientBg) {
        // 全宽渐变背景 + 白色文字（渐变背景保留亮色，文字保持白色对比）
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(HeroGradientStart, HeroGradientEnd)
                    ),
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                )
                .padding(horizontal = Spacing.screenH, vertical = Spacing.xl)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    } else {
        // 仅文字着色（用于有滚动动画的页面，标题会随滚动淡出）
        Column(modifier = modifier) {
            Text(
                title,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 活力列表卡片：白底 + 10pt 圆角 + 顶部 4dp 渐变装饰条。
 *
 * 用于替代各页面本地 IosGroupedListCard，统一注入珊瑚橙装饰条 + Dark Mode。
 *
 * @param accentGradient 是否显示顶部 4dp 渐变装饰条（默认 true）
 */
@Composable
fun VitalListCard(
    modifier: Modifier = Modifier,
    accentGradient: Boolean = true,
    content: @Composable () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(surfaceColor, RoundedCornerShape(10.dp))
    ) {
        if (accentGradient) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(BrandGradientStart, BrandGradientEnd)
                        )
                    )
            )
        }
        content()
    }
}

/**
 * 活力 Section Header：珊瑚橙小节标题（大写）。
 *
 * 替代各页面本地 IosSectionHeader，统一珊瑚橙 + Dark Mode。
 */
@Composable
fun VitalSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = Spacing.xs)
    )
}
