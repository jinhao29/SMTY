package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.data.model.ScheduleMemory
import com.shangmentiyu.sportscoach.ui.theme.AppTextField
import com.shangmentiyu.sportscoach.ui.theme.Spacing

/**
 * 时间选择器：开始时间 + 时长（分钟）+ 历史时间快捷选择。
 *
 * 状态与回调由父级（ScheduleEditDialog）提升传入，本组件只负责渲染。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleTimePicker(
    startTime: String,
    onStartTimeChange: (String) -> Unit,
    durationMinutes: String,
    onDurationChange: (String) -> Unit,
    timeMemories: List<ScheduleMemory>,
    onTimeMemorySelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        AppTextField(
            value = startTime,
            onValueChange = onStartTimeChange,
            label = { Text("开始时间") },
            leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        AppTextField(
            value = durationMinutes,
            onValueChange = onDurationChange,
            label = { Text("时长(分)") },
            leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
    // 历史时间快捷选择（辅助）
    if (timeMemories.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.sm))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            timeMemories.take(6).forEach { memory ->
                FilterChip(
                    selected = startTime == memory.value,
                    onClick = { onTimeMemorySelected(memory.value) },
                    label = { Text(memory.value) }
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun ScheduleTimePickerPreview() {
    ScheduleTimePicker(
        startTime = "09:00",
        onStartTimeChange = {},
        durationMinutes = "60",
        onDurationChange = {},
        timeMemories = listOf(
            ScheduleMemory(coachName = "李", field = "time", value = "09:00"),
            ScheduleMemory(coachName = "李", field = "time", value = "14:00")
        ),
        onTimeMemorySelected = {}
    )
}
