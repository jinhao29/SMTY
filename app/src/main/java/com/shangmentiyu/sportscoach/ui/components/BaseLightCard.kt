package com.shangmentiyu.sportscoach.ui.components

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
import com.shangmentiyu.sportscoach.ui.theme.LightCardBackground

/**
 * v36 全局 UI 统一：次要卡片组件（纯白背景）。
 *
 * 设计令牌（参考设置页 iOS Settings 风格）：
 * - 卡片背景：#FFFFFF（[LightCardBackground] 纯白）
 * - 卡片圆角：统一 20dp（与 [BaseDarkCard] 保持一致）
 * - 柔和阴影：offsetY=4dp, blurRadius=12dp, color=#1A000000
 * - 无 border，无顶部装饰条（取消原 IosGroupedListCard 顶部的 4dp 渐变条）
 * - 仅依赖阴影与浅灰底色区隔层级
 *
 * 使用场景：
 * - 设置页大分组容器（教练设置、统计信息、数据同步等）
 * - 任何需要纯白卡片的次要面板
 *
 * 与 [BaseDarkCard] 的区别：
 * - BaseDarkCard：#1C1C1E 深色卡，用于主内容（学员卡、课表卡）
 * - BaseLightCard：#FFFFFF 纯白卡，用于次要面板（设置页分组、统计卡）
 *
 * 使用方式：
 * ```
 * BaseLightCard {
 *     // 列表项内容
 *     Row { ... 图标 + 标题 + 右箭头 ... }
 *     // 极细分割线（alpha = 0.05）
 *     Box(
 *         modifier = Modifier
 *             .padding(start = 60.dp)
 *             .fillMaxWidth()
 *             .height(0.5.dp)
 *             .background(appDividerColor())
 *     )
 *     Row { ... 下一项 ... }
 * }
 * ```
 *
 * @param modifier 外部 Modifier
 * @param cornerRadius 圆角，默认 20dp
 * @param contentPadding 内边距，默认 0dp（设置页列表通常自带行内边距）
 * @param content 卡片内容
 */
@Composable
fun BaseLightCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
            ),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = LightCardBackground   // #FFFFFF 纯白
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
