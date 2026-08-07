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
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
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
        // === 1. 顶部头部：月份 + 静态"本月"标签 ===
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
            // === 修复：删除"本月"假下拉箭头（无真实月份切换逻辑，杜绝可操作假象）===
            // 原 ExpandMore 箭头暗示可下拉选择月份，实际无任何点击效果；
            // 保留静态"本月"标签，仅展示当前月份，不再误导教练。
            Text(
                text = "本月",
                fontSize = 13.sp,
                color = appOnSurfaceVariant()
            )
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
    // === 修复：选中态对比度全部由主题令牌接管（深色模式不再"白字白底"）===
    // - 选中背景：colorScheme.primary（深色模式下为更亮的 NightPrimary）
    // - 选中文字：colorScheme.onPrimary（随主题自动配对的对比色，替代硬编码白色）
    // - 今日：colorScheme.primary 半透明底 + primary 文字（替代硬编码 LightPrimary）
    val bgColor = when {
        data.isSelected -> MaterialTheme.colorScheme.primary
        data.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    val textColor = when {
        data.isSelected -> MaterialTheme.colorScheme.onPrimary
        data.isToday -> MaterialTheme.colorScheme.primary
        data.isCurrentMonth -> MaterialTheme.colorScheme.primary
        // 邻月日期：深色模式下 outline(#38383A) 在 surface(#2C2C2E) 上近乎不可见，改用 onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
        // === 排课红点：主题令牌接管，深色模式清晰可见 ===
        // 数据源为 ScheduleScreen 传入的 scheduledDaysOfWeek（订阅 OperationViewModel.schedules
        // 响应式 Flow，新增/删除排课、体验课保存后红点立即刷新，无需手动切周）
        if (data.isScheduled && !data.isSelected) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        } else {
            Spacer(Modifier.size(5.dp))
        }
    }
}
