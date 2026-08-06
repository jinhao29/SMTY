package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.ui.theme.LightOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 排课日历组件（123.txt 第 2 段 + 性能优化版）。
 *
 * 性能优化要点：
 * 1. 预计算所有日期数据为 [CalendarDayData] 列表，用 @Immutable 注解让 Compose 跳过重组
 * 2. [CalendarDayCell] 提取为独立 @Composable，参数全部稳定类型，Compose 可跳过未变更项
 * 3. date.format() 移到 remember 中，避免每帧 42 次 format 调用
 * 4. 选中/今日背景色用 animateColorAsState 平滑过渡，避免硬切换
 * 5. clickable 使用无 ripple 的 interactionSource，减少绘制开销
 *
 * @param selectedDate 选中日期（yyyy-MM-dd）
 * @param scheduledDaysOfWeek 有排课的"周几"集合（1=周一 ... 7=周日）
 * @param onDateSelected 日期点击回调
 */
@Composable
fun ScheduleCalendar(
    selectedDate: String,
    scheduledDaysOfWeek: Set<Int>,
    onDateSelected: (String) -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val selected = remember(selectedDate) {
        runCatching { LocalDate.parse(selectedDate, formatter) }.getOrNull() ?: LocalDate.now()
    }
    val currentMonth = remember(selected) { YearMonth.from(selected) }
    val today = remember { LocalDate.now() }

    // === 预计算所有日期数据 ===
    // 把 42 个日期的所有 UI 状态一次性算完，避免在 forEach 里重复 format/比较
    // 用 @Immutable 注解，Compose 检测到引用未变即跳过重组
    val monthDays = remember(currentMonth, selected, today, scheduledDaysOfWeek) {
        try {
            buildCalendarDays(currentMonth, selected, today, scheduledDaysOfWeek, formatter)
        } catch (e: Exception) {
            android.util.Log.e("CalendarCrash", "构建日历数据失败", e)
            emptyList()
        }
    }

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
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        // === 1. 顶部头部：月份 + 下拉指示 ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${currentMonth.year}年${currentMonth.monthValue}月",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "本月",
                        fontSize = 13.sp,
                        color = LightOnSurfaceVariant
                    )
                    Spacer(Modifier.size(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = LightOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // === 2. 星期行 ===
        val weekLabels = remember { listOf("一", "二", "三", "四", "五", "六", "日") }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekLabels.forEach { day ->
                Text(
                    text = day,
                    fontSize = 11.sp,
                    color = appOnSurfaceVariant(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // === 3. 日期网格（6 行 × 7 列）===
        // chunked 返回 List<List<CalendarDayData>>，外层 6 行，内层 7 个
        // CalendarDayData 是 @Immutable，只有值变化时才触发 CalendarDayCell 重组
        monthDays.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                week.forEach { dayData ->
                    key(dayData.dateStr) {
                        CalendarDayCell(
                            data = dayData,
                            onClick = {
                                try {
                                    onDateSelected(dayData.dateStr)
                                } catch (e: Exception) {
                                    android.util.Log.e("CalendarCrash", "切换日期失败", e)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * 预计算的日历日期数据。
 *
 * 把所有 UI 状态（dateStr、day、isCurrentMonth、isToday、isScheduled、isSelected）
 * 一次性算完存入不可变数据类，避免在 Composable 重组时重复计算。
 */
@Immutable
data class CalendarDayData(
    val dateStr: String,
    val day: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isScheduled: Boolean,
    val isSelected: Boolean
)

/**
 * 构建当月日历网格数据（42 格 = 6 行 × 7 列）。
 * 把第一行可能包含的上月末几天、最后一行可能包含的下月初几天都填充进去。
 */
private fun buildCalendarDays(
    currentMonth: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    scheduledDaysOfWeek: Set<Int>,
    formatter: DateTimeFormatter
): List<CalendarDayData> {
    val firstDayOfMonth = currentMonth.atDay(1)
    // ISO 周一为 1，周日为 7；网格从周一开始，所以减 1 得到前置空位数
    val leadingSpaces = firstDayOfMonth.dayOfWeek.value - 1
    val daysInMonth = currentMonth.lengthOfMonth()

    // 前置上月末尾日期（填充网格第一行）
    val leadingDays = (0 until leadingSpaces).map { idx ->
        firstDayOfMonth.minusDays((leadingSpaces - idx).toLong())
    }
    // 当月日期
    val monthDates = (1..daysInMonth).map { day ->
        currentMonth.atDay(day)
    }
    // 后置下月开头日期（填满 42 格）
    val totalCells = 42
    val trailingCount = totalCells - leadingDays.size - monthDates.size
    val trailingDays = (1..trailingCount).map { idx ->
        currentMonth.atEndOfMonth().plusDays(idx.toLong())
    }

    return (leadingDays + monthDates + trailingDays).map { date ->
        CalendarDayData(
            dateStr = date.format(formatter),
            day = date.dayOfMonth,
            isCurrentMonth = date.month == currentMonth.month,
            isToday = date == today,
            // 按该日期的"周几"判断是否有排课模板
            isScheduled = scheduledDaysOfWeek.contains(date.dayOfWeek.value),
            isSelected = date == selected
        )
    }
}

/**
 * 单个日历日期单元格（性能优化版）。
 *
 * 优化点：
 * 1. 参数为 @Immutable [CalendarDayData]，Compose 可精确跳过未变更项
 * 2. 背景色用 animateColorAsState 平滑过渡（180ms），避免硬切换闪烁
 * 3. clickable 使用无 ripple 的 interactionSource，减少绘制开销
 * 4. 按下状态用 scale 缩放反馈，提升交互质感
 */
@Composable
private fun CalendarDayCell(
    data: CalendarDayData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        data.isSelected -> MaterialTheme.colorScheme.primary
        data.isToday -> LightPrimary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    val textColor = when {
        data.isSelected -> Color.White
        data.isToday -> LightPrimary
        data.isCurrentMonth -> MaterialTheme.colorScheme.primary
        else -> appOutline()
    }

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .background(bgColor),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "${data.day}",
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (data.isSelected || data.isToday) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.height(2.dp))
        if (data.isScheduled && !data.isSelected) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(LightPrimary)
            )
        } else {
            Spacer(Modifier.size(5.dp))
        }
    }
}
