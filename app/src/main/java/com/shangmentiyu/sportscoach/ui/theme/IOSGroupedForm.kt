package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * iOS 分组风格共享组件库（Inset Grouped Form）。
 *
 * 设计规格（方案 1：现代极简 / iOS 分组风）：
 * - 浅灰分组大背景（appGroupedBackground #F2F2F7）
 * - 白色圆角大卡片（10dp 圆角 + 2dp 柔和投影）
 * - 紧凑内边距（12dp）避免视觉过大
 * - 分组小标题（55% alpha 次级文字色）
 *
 * 全局复用：课表编辑、课后小结、设置等表单/详情页统一调用本组件。
 */

/**
 * iOS 分组风格白色圆角卡片容器（Inset Grouped Card）。
 *
 * 视觉规格：
 * - 白色背景（appSurface）
 * - 10dp 圆角（与 GlassCard 一致）
 * - 6dp 柔和投影（低 alpha 黑色，模拟 iOS 卡片阴影，提升浮动感）
 * - 12dp 内边距（紧凑，避免过大）
 *
 * @param contentPadding 内边距，默认 12dp
 */
@Composable
fun IOSCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
            .clip(RoundedCornerShape(10.dp))
            .background(appSurface())
            .padding(contentPadding),
        content = content
    )
}

/**
 * 分组小标题（iOS Form section header 风格）。
 *
 * 视觉规格：
 * - 次级文字色（55% alpha）
 * - labelMedium 字号
 * - Medium 字重
 * - 左侧 4dp 缩进，底部 4dp 间距
 */
@Composable
fun IOSSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = appOnSurfaceVariant(),
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(start = Spacing.xs, bottom = Spacing.xs)
    )
}

/**
 * 胶囊状颜色选择器（Pill-shaped color selector）。
 *
 * 视觉规格：
 * - 6 色胶囊横向等宽排列
 * - 每个胶囊 weight(1f) × 26dp 高（紧凑）
 * - 13dp 圆角（全圆角胶囊形）
 * - 选中态：内嵌白色 Check 图标（16dp）
 * - 未选中态：纯色填充
 *
 * @param selected 当前选中色键（blue/green/orange/purple/pink/teal）
 * @param onSelect 选中回调，参数为色键
 */
@Composable
fun IOSColorPillSelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    val colorOptions = listOf(
        "blue" to LightSecondary,
        "green" to LightTertiary,
        "orange" to LightPrimary,
        "purple" to LightPrimary,
        "pink" to LightPrimaryContainer,
        "teal" to LightOnSurfaceVariant
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        colorOptions.forEach { (key, colorValue) ->
            val isSelected = selected == key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colorValue)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "已选中",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
