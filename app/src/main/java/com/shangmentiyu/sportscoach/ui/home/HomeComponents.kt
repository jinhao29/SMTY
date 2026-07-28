package com.shangmentiyu.sportscoach.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.ui.theme.GradientStart
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.ScoreFail
import com.shangmentiyu.sportscoach.ui.theme.ScorePass
import com.shangmentiyu.sportscoach.ui.theme.Spacing

/**
 * 主页共用 iOS 风格组件集合。
 *
 * 包含卡片容器、分组头/脚注、统计项、指标 chip、课时徽章等，
 * 供主页 4 个 Tab 复用，保持视觉一致性。
 */

/** iOS Inset Grouped 卡片：纯白 + 10dp 圆角 + 4.dp 柔和阴影 */
@Composable
internal fun IosCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
            )
            .background(
                color = Color.White,
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        content()
    }
}

/** iOS Inset Grouped 列表卡片：允许多行内容 + 柔和阴影 */
@Composable
internal fun IosGroupedListCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
            )
            .background(
                color = Color.White,
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        content()
    }
}

/** iOS 列表分组头：13pt SemiBold 活力蓝紫色大写标题 */
@Composable
internal fun IosSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = GradientStart,
        modifier = Modifier.padding(
            start = Spacing.screenH,
            end = Spacing.screenH,
            top = Spacing.lg,
            bottom = Spacing.xs
        )
    )
}

/** 统计项：大号数值 + 小号标签，居中竖排 */
@Composable
internal fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = GradientStart
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/** 指标小 chip：彩色淡背景 + 同色文字（身高/体重/BMI） */
@Composable
internal fun MetricChip(label: String, color: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 剩余课时药丸徽章：>5绿/1-5橙/0红/-1灰 */
@Composable
internal fun RemainingBadge(remaining: Int) {
    val (bg, fg) = when {
        remaining > 5 -> ScoreExcellent.copy(alpha = 0.15f) to ScoreExcellent
        remaining in 1..5 -> ScorePass.copy(alpha = 0.15f) to ScorePass
        remaining == 0 -> ScoreFail.copy(alpha = 0.15f) to ScoreFail
        else -> Color(0xFF8E8E93).copy(alpha = 0.15f) to Color(0xFF8E8E93)
    }
    Text(
        text = if (remaining >= 0) "剩 $remaining" else "无课时",
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** 根据姓名首字符 hash 分配紫/灰色系头像背景 */
internal fun avatarColorFor(name: String): Color {
    val colors = listOf(
        Color(0xFF6A5ACD), // 主紫
        Color(0xFF8A79D9), // 浅紫
        Color(0xFFB8AEE6), // 淡紫
        Color(0xFF8E8E93)  // 灰
    )
    val idx = name.firstOrNull()?.code?.rem(4) ?: 0
    return colors[(idx + 4).rem(4)]
}
