package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS 风分段控件（Segmented Control）— 顶部 Tab 切换的统一组件。
 *
 * 视觉结构（替代旧的"每个 Tab 独立胶囊"形态）：
 * - 一条浅灰轨道（appSurfaceVariant，全圆角胶囊）承载所有 Tab
 * - 选中项：白色滑块（亮色）/ 抬升灰（暗色）+ 2dp 轻投影 + 珊瑚橙粗体字
 * - 未选中项：无底色透明 + 次级灰字
 *
 * 设计动机：
 * - 旧形态每个 Tab 自带 #F2F2F5 灰底块，页面背景改为 #F5F6F8 后两者近乎同色，
 *   视觉脏重；分段控件的"轨道+滑块"层次不受背景色影响。
 * - 与"浅灰底 + 白卡片"的设计语言同构：白滑块 = 微缩的白卡片。
 *
 * 暗色模式：轨道 #3A3A3C / 滑块 #5A5A5F（抬升灰，比轨道亮一档，符合 iOS 暗色分段控件）。
 *
 * @param labels Tab 文本列表
 * @param selectedIndex 当前选中下标
 * @param onSelect 点击回调，参数为选中的下标
 * @param modifier 外部修饰符（调用方负责水平边距）
 * @param fontSize 文字字号，默认 12sp 与旧胶囊一致
 */
@Composable
fun AppSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(appSurfaceVariant())
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        labels.forEachIndexed { index, label ->
            SegmentedTabItem(
                label = label,
                selected = index == selectedIndex,
                fontSize = fontSize,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 单个分段项：独立 @Composable 隔离重组范围，只有选中态变化的两个项会重组。
 * 颜色与滑块投影均 100ms 平滑过渡（iOS 原生 Segmented Control 量级），
 * 旧 200ms×3 通道叠加感知偏慢，按"懒"原则统一压缩。
 */
@Composable
private fun SegmentedTabItem(
    label: String,
    selected: Boolean,
    fontSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbColor by animateColorAsState(
        targetValue = if (selected) appSegmentThumb() else Color.Transparent,
        animationSpec = tween(durationMillis = 100),
        label = "segment_thumb"
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 100),
        label = "segment_elevation"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) appPrimary() else appOnSurfaceVariant(),
        animationSpec = tween(durationMillis = 100),
        label = "segment_text"
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(thumbColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
