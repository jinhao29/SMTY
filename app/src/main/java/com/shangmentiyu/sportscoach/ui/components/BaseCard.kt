package com.shangmentiyu.sportscoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.ui.theme.Spacing

/**
 * v36 视觉重构：浅色卡组件（保留供底部导航栏等需白底组件使用）。
 *
 * 设计令牌：
 * - 卡片圆角：20dp
 * - 柔和阴影：offsetY=4dp, blurRadius=12dp, color=#0A000000（亮色）/ #33000000（暗色）
 * - 浅色卡：纯白 #FFFFFF 背景（仅亮色模式使用，暗色自动切换 #2C2C2E）
 * - 无 border，仅依赖阴影与背景色区隔层级
 *
 * 注意：v36 起所有页面内容卡片统一使用 [BaseDarkCard]（#1C1C1E）。
 * 本组件仅保留给 Scaffold 的 bottomBar 等需要白色背景的容器使用。
 *
 * @param modifier 外部 Modifier
 * @param contentPadding 内边距，默认 [Spacing.cardPadding]
 * @param content 卡片内容
 */
@Composable
fun BaseCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = Spacing.cardPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) Color(0xFF2C2C2E) else Color.White
    val shadowColor = if (isDark) Color(0x33000000) else Color(0x0A000000)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = shadowColor,
                spotColor = shadowColor
            ),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
