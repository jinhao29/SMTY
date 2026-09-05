package com.shangmentiyu.sportscoach.ui.coach

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.data.model.CoachSchedule
import com.shangmentiyu.sportscoach.data.repo.CoachScheduleRepository
import com.shangmentiyu.sportscoach.ui.theme.AppTextField
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.StyledDropdown
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appWarningContainer
import com.shangmentiyu.sportscoach.ui.theme.appOnWarningContainer

/**
 * Tab 2：教练排班与工作量。
 * - 可上课时段管理（周几 + 起止时间，保存时同教练重叠检测）
 * - 工作量看板：当前排课数（主讲/助教）+ 本月消课数 + 每日负荷预警
 */
@Composable
internal fun CoachScheduleTab(viewModel: CoachManageViewModel) {
    val coaches by viewModel.activeCoaches.collectAsStateWithLifecycle()
    val selected by viewModel.selectedCoach.collectAsStateWithLifecycle()
    val slots by viewModel.availability.collectAsStateWithLifecycle()
    val scheduledCounts by viewModel.scheduledCounts.collectAsStateWithLifecycle()
    val assistCounts by viewModel.assistCounts.collectAsStateWithLifecycle()

    var showAddSlot by remember { mutableStateOf(false) }
    var consumedThisMonth by remember { mutableStateOf(0) }
    var overloadDays by remember { mutableStateOf<List<String>>(emptyList()) }

    val selectedCoach = coaches.firstOrNull { it.name == selected }
    val month = viewModel.periodOf(viewModel.selectedMonth.value.toString())

    // 未选中或所选教练已被删除时，自动选中第一位在职教练
    LaunchedEffect(coaches, selected) {
        if (coaches.isNotEmpty() && (selected.isBlank() || coaches.none { it.name == selected })) {
            viewModel.selectCoach(coaches.first().name)
        }
    }

    // 选中教练后拉取本月消课数与每日负荷（一次性查询）
    LaunchedEffect(selected) {
        if (selected.isBlank()) {
            consumedThisMonth = 0
            overloadDays = emptyList()
            return@LaunchedEffect
        }
        val counts = viewModel.consumedCounts(month.first, month.second)
        consumedThisMonth = counts[selected] ?: 0
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Spacing.screenH, vertical = Spacing.md
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("选择教练", style = MaterialTheme.typography.labelMedium, color = appOnSurfaceVariant())
                StyledDropdown(
                    selected = selectedCoach,
                    options = coaches,
                    optionLabel = { it.name },
                    optionIcon = { Icons.Outlined.Person },
                    onSelected = { viewModel.selectCoach(it.name) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "选择教练查看排班与工作量",
                    emptyText = "暂无教练，请先到「档案」新增"
                )
            }
        }

        if (selectedCoach != null) {
            item {
                SectionCard(title = "工作量概览 · ${CoachManageViewModel.monthLabel(viewModel.selectedMonth.value.toString())}") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatCell("当前排课", "${scheduledCounts[selectedCoach.name] ?: 0} 节")
                        StatCell("担任助教", "${assistCounts[selectedCoach.name] ?: 0} 节")
                        StatCell("本月消课", "$consumedThisMonth 节")
                        StatCell("日上限", "${selectedCoach.dailyLimit} 节")
                    }
                }
            }
            item {
                SectionCard(
                    title = "可上课时段",
                    trailing = {
                        TextButton(onClick = { showAddSlot = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = appPrimary())
                            Spacer(Modifier.width(2.dp))
                            Text("添加时段", color = appPrimary())
                        }
                    }
                ) {
                    if (slots.isEmpty()) {
                        Text(
                            "尚未设置可上课时段。此处设置的时段用于声明教练的可用时间，排课冲突检测在排课保存时自动进行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = appOnSurfaceVariant()
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            slots.forEach { slot ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        tint = appPrimary(),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(Spacing.sm))
                                    Text(
                                        "${CoachScheduleRepository.dayName(slot.dayOfWeek)}  ${slot.startTime} - ${slot.endTime}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.deleteSlot(slot.id) }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "删除时段",
                                            tint = appOnSurfaceVariant(),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    "选择教练后可设置每周可上课时段、查看工作量与负荷预警。",
                    style = MaterialTheme.typography.bodySmall,
                    color = appOnSurfaceVariant(),
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
        item { Spacer(Modifier.height(160.dp)) }
    }

    if (showAddSlot && selectedCoach != null) {
        AddSlotDialog(
            coachName = selectedCoach.name,
            existing = slots,
            onDismiss = { showAddSlot = false },
            onSave = { slot ->
                viewModel.saveSlot(slot)
                showAddSlot = false
            }
        )
    }
}

/** 添加时段弹窗：周几胶囊 + HH:mm 起止输入（前端即时重叠提示由 Repository 兜底） */
@Composable
private fun AddSlotDialog(
    coachName: String,
    existing: List<CoachSchedule>,
    onDismiss: () -> Unit,
    onSave: (CoachSchedule) -> Unit
) {
    var dayOfWeek by remember { mutableStateOf(1) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("10:00") }
    var note by remember { mutableStateOf("") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "添加可上课时段（$coachName）",
        confirmButton = {
            Button(onClick = {
                onSave(
                    CoachSchedule(
                        coachName = coachName,
                        dayOfWeek = dayOfWeek,
                        startTime = startTime.trim(),
                        endTime = endTime.trim(),
                        note = note.trim()
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("周几", style = MaterialTheme.typography.labelMedium, color = appOnSurfaceVariant())
            FilterChipGroup(
                options = (1..7).map { it.toString() },
                selected = dayOfWeek.toString(),
                label = { CoachScheduleRepository.dayName(it.toInt()) },
                onSelect = { dayOfWeek = it.toInt() },
                modifier = Modifier.fillMaxWidth()
            )
            AppTextField(
                value = startTime, onValueChange = { startTime = it },
                label = { Text("开始时间 (HH:mm)") }, singleLine = true
            )
            AppTextField(
                value = endTime, onValueChange = { endTime = it },
                label = { Text("结束时间 (HH:mm)") }, singleLine = true
            )
            if (existing.any { it.dayOfWeek == dayOfWeek }) {
                Text(
                    "该教练${CoachScheduleRepository.dayName(dayOfWeek)}已有时段，重叠将无法保存",
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnWarningContainer(),
                    modifier = Modifier.background(appWarningContainer(), RoundedCornerShape(8.dp)).padding(8.dp)
                )
            }
        }
    }
}
