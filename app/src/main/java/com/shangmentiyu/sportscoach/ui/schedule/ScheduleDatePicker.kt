package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.StyledDropdown
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant

/**
 * 周几选择器（排课日期）。
 *
 * - 新建模式（[isCreate]）：多选 Chip（支持一次排多天）
 * - 编辑模式：单条记录单选下拉
 *
 * 选中的天集合与回调由父级（ScheduleEditDialog）提升传入。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleDatePicker(
    isCreate: Boolean,
    dayOfWeek: Int,
    selectedDays: Set<Int>,
    onToggleDay: (Int) -> Unit,
    onDaySelected: (Int) -> Unit
) {
    val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    if (isCreate) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.CalendarToday,
                contentDescription = null,
                tint = appOnSurfaceVariant(),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "选择周几",
                style = MaterialTheme.typography.bodyMedium,
                color = appOnSurface()
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            dayLabels.forEachIndexed { idx, label ->
                val day = idx + 1
                FilterChip(
                    selected = selectedDays.contains(day),
                    onClick = { onToggleDay(day) },
                    label = { Text(label) },
                    leadingIcon = if (selectedDays.contains(day)) {
                        { Icon(Icons.Outlined.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    } else {
        // 编辑模式：下拉单选（编辑单条记录）
        StyledDropdown(
            selected = dayLabels.getOrNull(dayOfWeek - 1) ?: "周一",
            options = dayLabels,
            optionLabel = { it },
            optionIcon = { Icons.Outlined.CalendarToday },
            onSelected = { label ->
                onDaySelected(dayLabels.indexOf(label) + 1)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun ScheduleDatePickerPreview() {
    ScheduleDatePicker(
        isCreate = true,
        dayOfWeek = 1,
        selectedDays = setOf(1, 3),
        onToggleDay = {},
        onDaySelected = {}
    )
}
