package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurfaceVariant
import java.util.Calendar
import java.util.Date

/**
 * 课表页周头部组件（v53 拆分自 ScheduleScreen）：
 * 周次范围标题行 + 周切换按钮组 + 胶囊日期选择条。
 */

/**
 * 课表页顶部周头部：
 * 第一行 = 周次范围标题（MM.dd - MM.dd）+ 上一周/今天/下一周按钮组；
 * 第二行 = 胶囊日期选择条（周内快速切换）。
 *
 * @param weekStart 本周起始日（周一）
 * @param weekDays 本周 7 天条目
 * @param selectedDayOfWeek 当前选中星期几（1=周一 ... 7=周日）
 * @param onDaySelected 选中某天回调
 * @param onShiftWeek 周切换回调，参数为偏移天数（-7 / +7）
 * @param onToday 回到本周回调
 */
@Composable
internal fun ScheduleWeekHeader(
    weekStart: Date,
    weekDays: List<DayItem>,
    selectedDayOfWeek: Int,
    onDaySelected: (Int) -> Unit,
    onShiftWeek: (Int) -> Unit,
    onToday: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH)
            .padding(top = Spacing.sm, bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = weekRangeText(weekStart),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = appOnSurface(),
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                WeekShiftButton(
                    text = "上一周",
                    icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    onClick = { onShiftWeek(-7) }
                )
                TodayButton(onClick = onToday)
                WeekShiftButton(
                    text = "下一周",
                    icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    onClick = { onShiftWeek(7) }
                )
            }
        }

        DaySelector(
            days = weekDays,
            selectedDayOfWeek = selectedDayOfWeek,
            onDaySelected = onDaySelected
        )
    }
}

/**
 * 横向滚动日历视图（v40 重构版）。
 *
 * === v40 布局重构（参照图2中间屏幕的日历视图）===
 * - 横向滚动的 7 天日历，每项为全圆角胶囊
 * - 未选中：浅灰背景（#F0F0F0）+ 深灰文字
 * - 选中：珊瑚橙背景（#FF6B47）+ 白色文字
 * - 自动滚动：选中项变化时，自动滚动到 LazyRow 可见区域中间
 *
 * @param days 本周 7 天数据
 * @param selectedDayOfWeek 当前选中星期几（1=周一 ... 7=周日）
 * @param onDaySelected 选中回调
 */
@Composable
internal fun DaySelector(
    days: List<DayItem>,
    selectedDayOfWeek: Int,
    onDaySelected: (Int) -> Unit
) {
    val todayCal = Calendar.getInstance()
    val capsuleShape = RoundedCornerShape(50)
    val listState = rememberLazyListState()

    // 选中项变化时，自动滚动到 LazyRow 可见区域中间
    LaunchedEffect(selectedDayOfWeek, days) {
        if (days.isNotEmpty()) {
            val idx = days.indexOfFirst { it.dayOfWeek == selectedDayOfWeek }
            if (idx >= 0) {
                // 滚动到选中项，偏移量让它大致居中（-2 表示往前 2 项，让选中项在中间）
                val target = (idx - 2).coerceAtLeast(0)
                listState.animateScrollToItem(target)
            }
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = Spacing.screenH),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // key 使用完整日期字符串（dateStr = yyyy-MM-dd）而非 dayOfWeek：
        // 切周时新旧两天序列若用 dayOfWeek 作 key 则全部相同（1~7），LazyRow 会
        // 把 7 个项原地复用并套用旧状态，出现"数字消失/渲染空洞"；
        // 改用完整日期字符串后切周即整组重建，保证显示与数据一致。
        items(days, key = { it.dateStr }) { day ->
            val isSelected = day.dayOfWeek == selectedDayOfWeek
            val isToday = remember(day.date) {
                val d = Calendar.getInstance().apply { time = day.date }
                d.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    d.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
            }
            val displayDayName = if (isToday) "今天" else day.dayName

            // 选中 = 珊瑚橙 #FF6B47 背景 + 白色文字；未选中 = 浅灰背景 + 深灰文字
            val selectedBg = appPrimary()
            val unselectedBg = appSurfaceVariant()
            val selectedText = Color.White
            val unselectedText = appOnSurface().copy(alpha = 0.7f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(capsuleShape)
                    .clickable { onDaySelected(day.dayOfWeek) }
                    .background(
                        if (isSelected) selectedBg else unselectedBg,
                        capsuleShape
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = displayDayName,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) selectedText else unselectedText
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = day.dateLabel,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    // v40 任务2b：未选中态日期数字用珊瑚橙 #FF6B47
                    color = if (isSelected) selectedText else appPrimary()
                )
            }
        }
    }
}

/**
 * 周切换按钮：图标 + 文字 + 圆角浅色背景。
 *
 * 设计要点：
 * - 圆角胶囊背景（主色 10% 透明度），主色文字与图标
 * - 充足的水平 padding（12dp）保证点击区可点击
 * - 文字尺寸 13sp，配合 16dp 图标
 * - 用于"上一周"/"下一周"切换，间距由外部 Row 控制（建议 Spacing.sm）
 *
 * @param text 按钮文字（如"上一周"）
 * @param icon 方向图标
 * @param onClick 点击回调
 */
@Composable
internal fun WeekShiftButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(appPrimary().copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = appPrimary(),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = appPrimary(),
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * "今天"按钮：实心主色背景 + 白色文字，突出快捷回到本周的主操作。
 *
 * 设计要点：
 * - 与 [WeekShiftButton] 的浅主色背景形成视觉对比，突出"回到今天"这一常用快捷操作
 * - 圆角胶囊形，文字居中
 * - 充足的水平 padding 保证点击区可点击
 *
 * @param onClick 点击回调，调用 OperationViewModel.resetToThisWeek
 */
@Composable
internal fun TodayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(appPrimary())
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "今天",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}
