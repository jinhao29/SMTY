package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.theme.FeatureIconBlue
import com.shangmentiyu.sportscoach.ui.theme.FeatureIconGreen
import com.shangmentiyu.sportscoach.ui.theme.FeatureIconOrange
import com.shangmentiyu.sportscoach.ui.theme.FeatureIconPink
import com.shangmentiyu.sportscoach.ui.theme.FeatureIconPurple
import com.shangmentiyu.sportscoach.ui.theme.FeatureIconTeal
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 课表页面（wakeup 课表风格周视图）。
 *
 * 设计要点：
 * - 顶部：周切换条（上一周/本周范围/下一周）+ 「本周新增」按钮
 * - 主体：7 列横向滚动网格，每列代表一天（周一~周日）
 * - 每列顶部：周几标签 + 日期（MM-dd）
 * - 每列内：按开始时间排序的课程卡片
 * - 课程卡片：左侧 4dp 色条（对应 Schedule.color）+ 时间 + 学员 + 地点
 * - 点击课程卡片：进入编辑（复用 ScheduleEditDialog）
 * - 点击列底部「+」：为该天快速新增课程
 *
 * 数据流：ScheduleScreen → OperationViewModel → ScheduleRepository → Room
 *
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: OperationViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val schedules by vm.schedules.collectAsState()
    val weekStart by vm.weekStart.collectAsState()
    val editing by vm.editingSchedule.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var isCreate by remember { mutableStateOf(true) }
    var prefillDay by remember { mutableStateOf<Int?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("MM-dd", Locale.getDefault()) }
    val weekFmt = remember { SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()) }
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    // 计算本周 7 天的日期
    val weekDates = remember(weekStart) {
        val cal = Calendar.getInstance()
        cal.time = weekStart
        (0..6).map { i ->
            if (i > 0) cal.add(Calendar.DATE, 1)
            cal.time
        }
    }

    // 本周日期范围标题
    val weekTitle = remember(weekStart) {
        val cal = Calendar.getInstance()
        cal.time = weekStart
        val startStr = weekFmt.format(weekStart)
        cal.add(Calendar.DATE, 6)
        val endStr = weekFmt.format(cal.time)
        "$startStr ~ $endStr"
    }

    Scaffold(
        containerColor = appGroupedBackground(),
        topBar = {
            TopAppBar(
                title = { Text("课表", fontWeight = FontWeight.Bold) },
                colors = glassTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 清空全部课表按钮（仅当有课表时显示）
                    if (schedules.isNotEmpty()) {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "清空全部",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // === 周切换条 ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenH, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.shiftWeek(-7) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周")
                }
                Text(
                    text = weekTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { vm.shiftWeek(7) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周")
                }
            }

            // === 7 列横向滚动课表网格 ===
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Spacing.screenH,
                    vertical = Spacing.sm
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(weekDates.size) { idx ->
                    val date = weekDates[idx]
                    val dayOfWeek = idx + 1
                    val daySchedules = schedules
                        .filter { it.dayOfWeek == dayOfWeek }
                        .sortedBy { it.startTime }

                    DayColumn(
                        dayName = dayNames[idx],
                        dateLabel = dateFmt.format(date),
                        daySchedules = daySchedules,
                        onScheduleClick = { s ->
                            isCreate = false
                            prefillDay = null
                            vm.startEdit(s.id)
                            showEditDialog = true
                        },
                        onAddNew = {
                            isCreate = true
                            prefillDay = dayOfWeek
                            vm.startCreate()
                            showEditDialog = true
                        }
                    )
                }
            }
        }
    }

    // 编辑/新建对话框
    val readyToShow = if (isCreate) showEditDialog else (showEditDialog && editing != null)
    if (readyToShow) {
        ScheduleEditDialog(
            vm = vm,
            isCreate = isCreate,
            prefillDayOfWeek = prefillDay,
            onDismiss = {
                vm.cancelEdit()
                showEditDialog = false
            },
            onSaved = {
                vm.cancelEdit()
                showEditDialog = false
            }
        )
    }

    // 清空全部课表确认对话框
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清空全部课表", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "确认清空所有排课记录吗？\n\n" +
                    "此操作将删除全部 ${schedules.size} 条排课，" +
                    "但不会影响已签到的课时记录。\n此操作不可撤销。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteAllSchedules()
                        showClearAllDialog = false
                    }
                ) {
                    Text("清空全部", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 单日列：周几标签 + 日期 + 课程卡片列表 + 新增按钮。
 *
 * @param dayName 周几文本（如 "周一"）
 * @param dateLabel 日期文本（如 "03-04"）
 * @param daySchedules 当天所有课程（按时间升序）
 * @param onScheduleClick 点击课程卡片
 * @param onAddNew 点击新增
 */
@Composable
private fun DayColumn(
    dayName: String,
    dateLabel: String,
    daySchedules: List<Schedule>,
    onScheduleClick: (Schedule) -> Unit,
    onAddNew: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // 列头：周几 + 日期
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    dayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // 课程列表
        if (daySchedules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    "无课",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                items(daySchedules, key = { it.id }) { s ->
                    ScheduleBlock(
                        schedule = s,
                        onClick = { onScheduleClick(s) }
                    )
                }
            }
        }

        // 底部新增按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                .clickable(onClick = onAddNew)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "新增",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "新增",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 单个课程方块：左侧色条 + 时间 + 学员 + 地点。
 *
 * @param schedule 课程数据
 * @param onClick 点击回调
 */
@Composable
private fun ScheduleBlock(
    schedule: Schedule,
    onClick: () -> Unit
) {
    val accentColor = scheduleColor(schedule.color)
    val inactiveAlpha = if (schedule.isActive) 1f else 0.45f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = inactiveAlpha))
            .clickable(onClick = onClick)
            .padding(end = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 左侧 4dp 色条
        Box(
            modifier = Modifier
                .width(4.dp)
                .background(accentColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 时间
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = accentColor
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    schedule.startTime,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    fontSize = 11.sp
                )
            }
            // 学员名 + 课时类型
            Text(
                buildString {
                    append(schedule.studentName)
                    if (schedule.lessonType.isNotBlank()) {
                        append(" · ").append(schedule.lessonType)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
            )
            // 地点
            if (schedule.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        schedule.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp
                    )
                }
            }
            // 暂停标记
            if (!schedule.isActive) {
                Text(
                    "已暂停",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * 将 Schedule.color 字符串映射为 Color。
 * 默认返回蓝色（与 wakeup 课表风格一致）。
 */
private fun scheduleColor(colorKey: String): Color = when (colorKey) {
    "blue" -> FeatureIconBlue
    "green" -> FeatureIconGreen
    "orange" -> FeatureIconOrange
    "purple" -> FeatureIconPurple
    "pink" -> FeatureIconPink
    "teal" -> FeatureIconTeal
    else -> FeatureIconBlue
}
