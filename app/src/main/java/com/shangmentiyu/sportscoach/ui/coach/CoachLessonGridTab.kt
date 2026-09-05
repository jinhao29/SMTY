package com.shangmentiyu.sportscoach.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.CoachScheduleRepository
import com.shangmentiyu.sportscoach.ui.schedule.TodayButton
import com.shangmentiyu.sportscoach.ui.theme.AppTextField
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.StyledDropdown
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurfaceVariant
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Tab 2：学员排课周视图（v34）。
 *
 * 参考主流排课 App：每个时段一个方块，横向 7 天、纵向整点时段；
 * - 点击空方块 → 快速为绑定学员新建排课（默认每周重复）
 * - 点击有课方块 → 查看详情（学员/时段/类型/地点/备注），可编辑或删除
 * - 下方"本周安排"以"周几 时段 · 学员 · 类型 @ 地点"汇总主要任务
 */
@Composable
internal fun CoachLessonGridTab(viewModel: CoachManageViewModel) {
    val coaches by viewModel.activeCoaches.collectAsStateWithLifecycle()
    val selected by viewModel.selectedCoach.collectAsStateWithLifecycle()
    val lessons by viewModel.coachLessons.collectAsStateWithLifecycle()
    val boundStudents by viewModel.boundStudents.collectAsStateWithLifecycle()
    val allStudents by viewModel.students.collectAsStateWithLifecycle()

    val selectedCoach = coaches.firstOrNull { it.name == selected }

    // 未选中或所选教练已被删除时，自动选中第一位在职教练
    LaunchedEffect(coaches, selected) {
        if (coaches.isNotEmpty() && (selected.isBlank() || coaches.none { it.name == selected })) {
            viewModel.selectCoach(coaches.first().name)
        }
    }

    // 弹窗状态：detail = 查看详情；showEditor + editTarget(null=新建) 配预填周几与时段
    var detailSchedule by remember { mutableStateOf<Schedule?>(null) }
    var editTarget by remember { mutableStateOf<Schedule?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var prefillDay by remember { mutableStateOf(1) }
    var prefillTime by remember { mutableStateOf("15:00") }

    // 周课表当前显示周的周一：表头日期由此派生（weekStart + 0..6），切周/回今天只改此值
    var currentWeekStart by remember { mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY)) }

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
                    placeholder = "选择教练查看与排课",
                    emptyText = "暂无教练，请先到「档案」新增"
                )
            }
        }

        if (selectedCoach != null) {
            item {
                SectionCard(title = "周课表 · 共 ${lessons.size} 节") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        WeekNavRow(
                            weekStart = currentWeekStart,
                            onShiftWeek = { currentWeekStart = currentWeekStart.plusDays(it.toLong()) },
                            onToday = { currentWeekStart = LocalDate.now().with(DayOfWeek.MONDAY) }
                        )
                        WeekGrid(
                            weekStart = currentWeekStart,
                            lessons = lessons,
                            onEmptyCellClick = { day, hour ->
                                prefillDay = day
                                prefillTime = String.format("%02d:00", hour)
                                editTarget = null
                                showEditor = true
                            },
                            onLessonClick = { detailSchedule = it }
                        )
                        Text(
                            "点击空方块快速排课，点击有课方块查看详情",
                            style = MaterialTheme.typography.labelSmall,
                            color = appOnSurfaceVariant()
                        )
                    }
                }
            }
            item {
                SectionCard(title = "本周安排") {
                    if (lessons.isEmpty()) {
                        Text(
                            "本周暂无排课，点击上方空方块即可安排。",
                            style = MaterialTheme.typography.bodySmall,
                            color = appOnSurfaceVariant()
                        )
                    } else {
                        val sorted = remember(lessons) {
                            lessons.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            sorted.forEach { lesson ->
                                LessonTaskRow(lesson)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    "选择教练后可查看周课表、为绑定学员排课。",
                    style = MaterialTheme.typography.bodySmall,
                    color = appOnSurfaceVariant(),
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
        item { Spacer(Modifier.height(160.dp)) }
    }

    // 详情弹窗 → 编辑跳转、删除
    detailSchedule?.let { schedule ->
        CoachLessonDetailDialog(
            schedule = schedule,
            onDismiss = { detailSchedule = null },
            onEdit = {
                detailSchedule = null
                editTarget = schedule
                showEditor = true
            },
            onDelete = {
                viewModel.deleteCoachLesson(schedule.id)
                detailSchedule = null
            }
        )
    }

    // 新建/编辑弹窗：绑定学员优先，无绑定时回退全部学员
    if (showEditor && selectedCoach != null) {
        val candidateStudents = if (boundStudents.isNotEmpty()) boundStudents else allStudents
        CoachLessonEditDialog(
            initial = editTarget,
            students = candidateStudents,
            studentsFromAll = boundStudents.isEmpty(),
            prefillDay = prefillDay,
            prefillTime = prefillTime,
            onDismiss = { showEditor = false },
            onSave = { student, day, start, duration, location, type, note ->
                viewModel.saveCoachLesson(
                    scheduleId = editTarget?.id,
                    coachName = selectedCoach.name,
                    student = student,
                    dayOfWeek = day,
                    startTime = start,
                    durationMinutes = duration,
                    location = location,
                    lessonType = type,
                    note = note
                ) { ok -> if (ok) showEditor = false }
            }
        )
    }
}

/**
 * 周课表网格：表头（周一~周日 + 月/日两行，今天高亮、周末置灰）+ 整点时段行（07:00-21:00）。
 * 每格一个方块：有课显示学员+类型（珊瑚橙浅底），空格浅灰可点击新建。
 *
 * @param weekStart 当前显示周的周一，表头日期 = weekStart + 0..6 天
 */
@Composable
private fun WeekGrid(
    weekStart: LocalDate,
    lessons: List<Schedule>,
    onEmptyCellClick: (dayOfWeek: Int, hour: Int) -> Unit,
    onLessonClick: (Schedule) -> Unit
) {
    val today = remember { LocalDate.now() }
    // 按周几+起始小时建立索引：一格里若有多条（异常数据）取第一条展示
    val cellMap = remember(lessons) {
        lessons.groupBy { it.dayOfWeek to hourOf(it.startTime) }
    }

    Column {
        // 表头：空角 + 周一~周日（周几下方显示对应月/日）
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(36.dp))
            (1..7).forEach { day ->
                val date = weekStart.plusDays((day - 1).toLong())
                val isToday = date == today
                val isWeekend = day >= 6
                val headerColor = when {
                    isToday -> appPrimary()
                    isWeekend -> appOnSurfaceVariant().copy(alpha = 0.6f)
                    else -> appOnSurfaceVariant()
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isToday) appPrimary().copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = CoachScheduleRepository.dayName(day).removePrefix("周"),
                            fontSize = 12.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            color = headerColor
                        )
                        Text(
                            text = "${date.monthValue}/${date.dayOfMonth}",
                            fontSize = 12.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = headerColor
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        // 时段行：07:00 ~ 21:00
        (7..21).forEach { hour ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = String.format("%02d:00", hour),
                    fontSize = 10.sp,
                    color = appOnSurfaceVariant(),
                    modifier = Modifier
                        .width(36.dp)
                        .padding(top = 4.dp),
                    maxLines = 1
                )
                (1..7).forEach { day ->
                    val cellLessons = cellMap[day to hour]
                    val first = cellLessons?.firstOrNull()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 1.dp, vertical = 1.dp)
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (first != null) appPrimary().copy(alpha = 0.12f)
                                else appSurfaceVariant().copy(alpha = 0.35f)
                            )
                            .clickable {
                                if (first != null) onLessonClick(first)
                                else onEmptyCellClick(day, hour)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (first != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = first.studentName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = appPrimary(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (cellLessons.size > 1) {
                                    Text(
                                        text = "${cellLessons.size}节",
                                        fontSize = 8.sp,
                                        color = appPrimary()
                                    )
                                } else if (first.lessonType.isNotBlank() && first.lessonType != "训练课") {
                                    Text(
                                        text = first.lessonType,
                                        fontSize = 8.sp,
                                        color = appPrimary().copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 周课表切换行：左侧周范围（M/d - M/d）+ 右侧 上一周/今天/下一周按钮组。
 *
 * 上一周/下一周为图标胶囊（无文字），今天保留文字——把横向占用从 ~250dp
 * 降到 ~110dp，避免在窄 SectionCard 里被挤换行（v54 修复"下一周"折两行）。
 * 按钮复用 [WeekShiftButton] 同色系背景，保持视觉一致；仅 coach 周课表使用。
 */
@Composable
private fun WeekNavRow(
    weekStart: LocalDate,
    onShiftWeek: (Int) -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = weekRangeLabel(weekStart),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = appOnSurface(),
            maxLines = 1
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeekShiftIconButton(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                onClick = { onShiftWeek(-7) }
            )
            TodayButton(onClick = onToday)
            WeekShiftIconButton(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                onClick = { onShiftWeek(7) }
            )
        }
    }
}

/**
 * 周切换图标按钮：仅图标（无文字），用于 [WeekNavRow] 节省横向空间。
 *
 * 与 [WeekShiftButton] 同色系（同主色 10% 背景 + 主色图标），
 * 但宽度从 ~70dp 降到 32dp 固定值——三个按钮 + 今天按钮总宽仅约 110dp，
 * 配合左侧周范围文字（~100dp），整行在 SectionCard 内不再被挤换行。
 *
 * 仅供 [CoachLessonGridTab] 使用；[com.shangmentiyu.sportscoach.ui.schedule.ScheduleWeekHeader]
 * 中的 WeekShiftButton 仍保留文字版（页面较宽，不存在折行问题）。
 */
@Composable
private fun WeekShiftIconButton(
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(appPrimary().copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = appPrimary(),
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 周范围文本："M/d - M/d"（周一 ~ 周日）。 */
private fun weekRangeLabel(weekStart: LocalDate): String {
    val end = weekStart.plusDays(6)
    return "${weekStart.monthValue}/${weekStart.dayOfMonth} - ${end.monthValue}/${end.dayOfMonth}"
}

/** 本周安排任务行："周三 15:00-16:00 · 张三 · 体育中考 @ 市体育场" */
@Composable
private fun LessonTaskRow(lesson: Schedule) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = CoachScheduleRepository.dayName(lesson.dayOfWeek),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = appPrimary(),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(appPrimary().copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "${lesson.startTime}-${lesson.endTime()}",
            fontSize = 12.sp,
            color = appOnSurfaceVariant()
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = lesson.studentName,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (lesson.lessonType.isNotBlank()) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = lesson.lessonType,
                fontSize = 12.sp,
                color = appOnSurfaceVariant(),
                maxLines = 1
            )
        }
        Spacer(Modifier.weight(1f))
        if (lesson.location.isNotBlank()) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = appOnSurfaceVariant(),
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = lesson.location,
                fontSize = 12.sp,
                color = appOnSurfaceVariant(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 排课详情弹窗：完整信息 + 编辑 / 删除 */
@Composable
private fun CoachLessonDetailDialog(
    schedule: Schedule,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "课程详情",
        confirmButton = {
            Button(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("编辑")
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(4.dp))
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DetailLine("学员", schedule.studentName)
            DetailLine("时间", "${CoachScheduleRepository.dayName(schedule.dayOfWeek)} ${schedule.startTime}-${schedule.endTime()}")
            DetailLine("类型", schedule.lessonType.ifBlank { "训练课" })
            DetailLine("地点", schedule.location.ifBlank { "未填写" })
            if (schedule.note.isNotBlank()) DetailLine("备注", schedule.note)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = appOnSurfaceVariant(),
            modifier = Modifier.width(48.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 起始时间 HH:mm 的小时数（解析失败返回 -1，不落在任何网格行） */
private fun hourOf(startTime: String): Int =
    startTime.split(":").firstOrNull()?.toIntOrNull()?.takeIf { it in 0..23 } ?: -1

/**
 * 新建/编辑排课弹窗。
 *
 * 新建：学员下拉（绑定学员优先）+ 预填周几/开始时间；
 * 编辑：回填既有排课要素。保存经 ViewModel 走冲突检测，失败 toast 且弹窗不关闭。
 *
 * @param initial null=新建；非空=编辑该排课
 * @param students 学员候选列表（绑定学员优先，由调用方决定）
 * @param studentsFromAll true=候选为全部学员（未绑定任何学员时的回退提示文案）
 * @param prefillDay 点击空方块预填的周几（仅新建生效）
 * @param prefillTime 点击空方块预填的开始时间（仅新建生效）
 * @param onSave 保存回调（学员/周几/开始时间/时长分钟/地点/类型/备注）
 */
@Composable
private fun CoachLessonEditDialog(
    initial: Schedule?,
    students: List<Student>,
    studentsFromAll: Boolean,
    prefillDay: Int,
    prefillTime: String,
    onDismiss: () -> Unit,
    onSave: (
        student: Student,
        dayOfWeek: Int,
        startTime: String,
        durationMinutes: Int,
        location: String,
        lessonType: String,
        note: String
    ) -> Unit
) {
    val dayOptions = remember { (1..7).toList() }
    val durationOptions = remember { listOf(60, 90, 120) }
    val typeOptions = remember { listOf("训练课", "体育中考", "体测课", "体验课", "恢复课") }

    var student by remember { mutableStateOf(students.firstOrNull { it.name == initial?.studentName }) }
    var day by remember { mutableStateOf(initial?.dayOfWeek ?: prefillDay) }
    var startTime by remember { mutableStateOf(initial?.startTime ?: prefillTime) }
    var duration by remember { mutableStateOf(initial?.durationMinutes ?: 60) }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var lessonType by remember { mutableStateOf(initial?.lessonType ?: "训练课") }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = if (initial == null) "新建排课" else "编辑排课",
        confirmButton = {
            Button(
                onClick = {
                    val s = student ?: return@Button
                    onSave(s, day, startTime.trim(), duration, location.trim(), lessonType, note.trim())
                },
                enabled = student != null && startTime.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StyledDropdown(
                selected = student,
                options = students,
                optionLabel = { it.name },
                optionIcon = { Icons.Outlined.Person },
                onSelected = { student = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = if (studentsFromAll) "选择学员（可在教练档案绑定）" else "选择绑定学员"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StyledDropdown(
                    selected = day,
                    options = dayOptions,
                    optionLabel = { CoachScheduleRepository.dayName(it) },
                    optionIcon = { Icons.Outlined.Schedule },
                    onSelected = { day = it },
                    modifier = Modifier.weight(1f)
                )
                StyledDropdown(
                    selected = duration,
                    options = durationOptions,
                    optionLabel = { "${it}分钟" },
                    optionIcon = { Icons.Outlined.Timer },
                    onSelected = { duration = it },
                    modifier = Modifier.weight(1f)
                )
            }
            AppTextField(
                value = startTime,
                onValueChange = { startTime = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("开始时间 HH:mm，如 15:00") },
                singleLine = true
            )
            StyledDropdown(
                selected = lessonType,
                options = typeOptions,
                optionLabel = { it },
                optionIcon = { Icons.Outlined.Category },
                onSelected = { lessonType = it },
                modifier = Modifier.fillMaxWidth()
            )
            AppTextField(
                value = location,
                onValueChange = { location = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("地点，如 市体育场") },
                singleLine = true
            )
            AppTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("备注（可选）") },
                singleLine = true
            )
        }
    }
}
