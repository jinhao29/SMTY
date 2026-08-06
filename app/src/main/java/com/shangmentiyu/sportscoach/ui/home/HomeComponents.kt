package com.shangmentiyu.sportscoach.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.ui.theme.LightOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightSecondary
import com.shangmentiyu.sportscoach.ui.theme.LightTertiary
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.ScoreFail
import com.shangmentiyu.sportscoach.ui.theme.ScorePass
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface

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
                // ponytail: 投影色保持 M3 默认黑（0x1A000000），明暗主题通用，无对应令牌
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
            )
            .background(
                color = appSurface(),
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
                color = appSurface(),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        content()
    }
}

/** iOS 列表分组头：20pt Bold 活力珊瑚橙大写标题（标题层级） */
@Composable
internal fun IosSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = appPrimary(),
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
            color = appPrimary()
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = appOnSurfaceVariant()
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
        else -> appOnSurfaceVariant().copy(alpha = 0.15f) to appOnSurfaceVariant()
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

/** 根据姓名首字符 hash 分配珊瑚橙暖色系头像背景 */
internal fun avatarColorFor(name: String): Color {
    val colors = listOf(
        LightPrimary,            // 主珊瑚橙 #FF6B47
        LightSecondary,           // 浅橙 #FF9E7A
        LightTertiary,           // 暖金黄 #FFB74D
        LightOnSurfaceVariant    // 中灰 #6B6B6B
    )
    val idx = name.firstOrNull()?.code?.rem(4) ?: 0
    return colors[(idx + 4).rem(4)]
}

// ============================================================
// === 123.txt UI 重构组件：珊瑚橙渐变头部 + 浮动统计卡片 + 本周进度点
// ============================================================

/**
 * 珊瑚橙渐变头部卡片。
 *
 * 设计要点（参考 123.txt 第 1 段）：
 * - 珊瑚橙 `#FF6B47` → 浅橙 `#FF9E7A` 的水平渐变
 * - 24dp 大圆角 + 柔和弥散阴影
 * - 内部文字纯白，展示"今日排课 X 节"
 *
 * @param scheduleCount 今日排课数量
 */
@Composable
internal fun TodayOverviewHeader(scheduleCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = appPrimary().copy(alpha = 0.10f),
                spotColor = appPrimary().copy(alpha = 0.16f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(LightPrimary, LightSecondary)
                )
            )
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column {
            Text(
                text = "今日概览",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "今日排课 ",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$scheduleCount",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                    lineHeight = 38.sp
                )
                Text(
                    text = " 节",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (scheduleCount > 0) "加油，今天也要上好每一节课！" else "今天暂无排课，可以稍作休息",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 独立悬浮白色大圆角统计卡片。
 *
 * 设计要点（参考 123.txt 第 1 段）：
 * - 纯白底 + 24dp 大圆角 + 柔和弥散阴影
 * - 数字大、黑、加粗（28sp），下方说明文字灰、小（12sp）
 * - 与头部渐变卡片形成层次对比
 *
 * @param label 标签（如"排课数"）
 * @param value 数值文本
 * @param modifier 外部布局修饰
 */
@Composable
internal fun FloatingStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0x0D000000),
                spotColor = Color(0x14000000)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(appSurface())
            .padding(vertical = 20.dp, horizontal = 8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = appOnSurface(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            lineHeight = 34.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = appOnSurfaceVariant(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 本周进度点卡片（周一到周日）。
 *
 * 设计要点（参考 123.txt 第 1 段）：
 * - 纯白底 + 24dp 大圆角 + 柔和弥散阴影
 * - 横向排列周一到周日
 * - 珊瑚橙实心圆点代表"今天/已上课"，浅灰空心圆点代表"未上课"
 *
 * @param selectedDate 当前选中日期（yyyy-MM-dd）
 * @param schedules 当日排课列表（用于判断是否有课）
 * @param lessons 当日已签到课时列表（用于判断是否已上课）
 */
@Composable
internal fun WeeklyProgressDots(
    selectedDate: String,
    schedules: List<com.shangmentiyu.sportscoach.data.model.Schedule>,
    lessons: List<com.shangmentiyu.sportscoach.data.model.Lesson>
) {
    // === 计算本周 7 天的日期与状态 ===
    // 用 remember 缓存，避免每次重组都重新计算
    val weekDays = remember(selectedDate) {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val today = java.time.LocalDate.parse(selectedDate, formatter)
        // 计算本周周一（ISO 周一为一周第一天）
        val monday = today.minusDays(today.dayOfWeek.value - 1L)
        (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            val dateStr = date.format(formatter)
            val isToday = date == java.time.LocalDate.now()
            val dayLabel = listOf("一", "二", "三", "四", "五", "六", "日")[offset]
            Triple(dateStr, dayLabel, isToday)
        }
    }

    // === 判断每天是否有排课 ===
    // 注：此处用当日 schedules/lessons 仅作展示，实际本周其他日数据需 ViewModel 扩展
    // 为保持简单，"今天"用实际数据，其他日根据 selectedDate 与 schedules 推断
    val todayLessonsCount = lessons.size
    val todaySchedulesCount = schedules.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0x0D000000),
                spotColor = Color(0x14000000)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(appSurface())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            weekDays.forEach { (dateStr, dayLabel, isToday) ->
                WeeklyDayDot(
                    dayLabel = dayLabel,
                    dateStr = dateStr,
                    isToday = isToday,
                    isCurrentSelected = dateStr == selectedDate,
                    hasSchedule = isToday && todaySchedulesCount > 0,
                    isCompleted = isToday && todayLessonsCount > 0
                )
            }
        }
    }
}

/**
 * 单个本周进度日点。
 *
 * - 今天且有课：珊瑚橙实心圆点 + 珊瑚橙文字
 * - 今天无课：浅灰空心圆点 + 深灰文字
 * - 选中日期：深色背景圆 + 白色文字
 *
 * @param dayLabel 周几标签（一/二/三/...）
 * @param dateStr 日期字符串
 * @param isToday 是否是今天
 * @param isCurrentSelected 是否是当前选中日期
 * @param hasSchedule 当天是否有排课
 * @param isCompleted 当天是否已完成签到
 */
@Composable
private fun WeeklyDayDot(
    dayLabel: String,
    dateStr: String,
    isToday: Boolean,
    isCurrentSelected: Boolean,
    hasSchedule: Boolean,
    isCompleted: Boolean
) {
    val dayNum = remember(dateStr) {
        dateStr.substring(8).trimStart('0').ifEmpty { "1" }
    }
    // === 颜色策略 ===
    // 选中日期：深黑圆 + 白字（参考 123.txt 第 2 段"选中变成深色背景正圆形"）
    // 今天有课/已上课：珊瑚橙圆点
    // 其他：浅灰
    val circleColor = when {
        isCurrentSelected -> MaterialTheme.colorScheme.inverseSurface
        hasSchedule || isCompleted -> LightPrimary
        else -> appDividerColor()
    }
    val textColor = when {
        isCurrentSelected -> MaterialTheme.colorScheme.inverseOnSurface
        isToday -> LightPrimary
        else -> appOnSurfaceVariant()
    }
    Column(
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = dayLabel,
            color = appOnSurfaceVariant(),
            fontSize = 10.sp
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = dayNum,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isCurrentSelected || isToday) FontWeight.Bold else FontWeight.Medium
            )
        }
        // 小圆点指示器：有排课时显示珊瑚橙小点
        if (hasSchedule) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(LightPrimary)
            )
        } else {
            Spacer(Modifier.size(4.dp))
        }
    }
}
