package com.shangmentiyu.sportscoach.ui.operation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Schedule
import org.koin.androidx.compose.koinViewModel
import com.shangmentiyu.sportscoach.ui.schedule.ScheduleEditDialog
import com.shangmentiyu.sportscoach.ui.dailyplan.DailyPlanViewModel
import com.shangmentiyu.sportscoach.ui.theme.GlassCard
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.PrimaryButton
import com.shangmentiyu.sportscoach.ui.theme.OutlinedDatePickerField
import com.shangmentiyu.sportscoach.ui.theme.FloatingSnackbarHost
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors
import java.util.Locale
import com.shangmentiyu.sportscoach.ui.theme.AppTextFieldShape
import com.shangmentiyu.sportscoach.ui.theme.appTextFieldColors

/**
 * 运营管理主页：排课日历 / 课时余额 / 教练管理 三标签页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationScreen(
    onBack: () -> Unit,
    onSign: (String) -> Unit = {},
    onOpenLesson: (String) -> Unit = {}
) {
        val vm: OperationViewModel = koinViewModel()

    var tabIndex by remember { mutableStateOf(0) }
    val snackbarHost = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsStateWithLifecycle()
    LaunchedEffect(toast) {
        toast?.let { msg ->
            snackbarHost.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            vm.clearToast()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { FloatingSnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("运营管理") },
                colors = glassTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("每日计划") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("排课日历") })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("课时余额") })
                Tab(selected = tabIndex == 3, onClick = { tabIndex = 3 }, text = { Text("教练管理") })
            }
            Crossfade(
                targetState = tabIndex,
                animationSpec = tween(durationMillis = 220),
                label = "OperationTabCrossfade"
            ) { index ->
                when (index) {
                    0 -> DailyPlanTab(onSign = onSign, onOpenLesson = onOpenLesson)
                    1 -> ScheduleTab(vm)
                    2 -> PackageTab(vm)
                    3 -> CoachTab(vm)
                }
            }
        }
    }
}

// =================== 排课日历标签页 ===================

@Composable
private fun ScheduleTab(vm: OperationViewModel) {
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val selectedDay by vm.selectedDay.collectAsStateWithLifecycle()
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val editing by vm.editingSchedule.collectAsStateWithLifecycle()
    // showEditDialog 控制对话框显示；isCreate 标记是新建还是编辑
    var showEditDialog by remember { mutableStateOf(false) }
    var isCreate by remember { mutableStateOf(true) }

    val dateFmt = remember { java.time.format.DateTimeFormatter.ofPattern("MM-dd", Locale.getDefault()) }
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    // 进入排课页时自动生成本周长期课记录（一次性触发，避免每次重组重复执行）
    LaunchedEffect(Unit) { vm.ensureLongTermLessonsForWeek() }

    // 预计算当日课程列表，避免重组重复 filter+sort
    val daySchedules = remember(schedules, selectedDay) {
        schedules.filter { it.dayOfWeek == selectedDay }.sortedBy { it.startTime }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screenH, vertical = Spacing.screenV),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // 周切换条
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shiftWeek(-7) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上一周")
                    }
                    Text(
                        "${weekStart.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(dateFmt)} 周",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.shiftWeek(7) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下一周")
                    }
                }
            }
        }

        // 周几选择
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 1..7) {
                    FilterChip(
                        selected = selectedDay == i,
                        onClick = { vm.selectDay(i) },
                        label = { Text(dayNames[i - 1], style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 当日课程列表
        if (daySchedules.isEmpty()) {
            item {
                GlassCard {
                    Text("当日无排课", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(daySchedules, key = { it.id }) { s ->
                ScheduleCard(
                    s = s,
                    onToggleActive = { vm.toggleScheduleActive(s) },
                    onDelete = { vm.deleteSchedule(s.id) },
                    onEdit = {
                        isCreate = false
                        vm.startEdit(s.id)
                        showEditDialog = true
                    }
                )
            }
        }

        // 新增排课按钮
        item {
            PrimaryButton(
                text = "新增排课",
                onClick = {
                    isCreate = true
                    vm.startCreate()
                    showEditDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Outlined.Add
            )
        }
    }

    // 排课编辑对话框：复用 ScheduleEditDialog，统一支持完整字段
    // 编辑模式下需等待 editing 加载完成才显示
    val readyToShow = if (isCreate) showEditDialog else (showEditDialog && editing != null)
    if (readyToShow) {
        ScheduleEditDialog(
            vm = vm,
            isCreate = isCreate,
            prefillDayOfWeek = if (isCreate) selectedDay else null,
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
}

@Composable
private fun ScheduleCard(
    s: Schedule,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${s.startTime}-${s.endTime()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = LightPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${s.studentName}${if (s.coachName.isNotBlank()) " · ${s.coachName}" else ""}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (s.location.isNotBlank()) {
                    Text(
                        s.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            TextButton(
                onClick = onEdit,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text("编辑", style = MaterialTheme.typography.labelMedium, color = LightPrimary)
            }
            IconButton(onClick = onToggleActive) {
                Icon(
                    if (s.isActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = "启停",
                    tint = if (s.isActive) MaterialTheme.colorScheme.outline else LightPrimary
                )
            }
            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text("删除", style = MaterialTheme.typography.labelMedium, color = LightPrimary)
            }
        }
    }
}

// =================== 课时余额标签页 ===================

@Composable
private fun PackageTab(vm: OperationViewModel) {
    val packages by vm.packages.collectAsStateWithLifecycle()
    val renewalAlerts by vm.renewalAlerts.collectAsStateWithLifecycle()
    val students by vm.students.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPkg by remember { mutableStateOf<LessonPackage?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screenH, vertical = Spacing.screenV),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // 续费提醒卡片
        if (renewalAlerts.isNotEmpty()) {
            item {
                GlassCard(glow = true) {
                    Text("续费提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LightPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    renewalAlerts.forEach { pkg ->
                        Text(
                            "• ${pkg.studentName} 的「${pkg.name}」" +
                            if (pkg.isLowBalance) " 仅剩 ${pkg.remainingLessons} 次" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 课时包列表
        if (packages.isEmpty()) {
            item {
                GlassCard { Text("暂无课时包", color = MaterialTheme.colorScheme.outline) }
            }
        } else {
            items(packages, key = { it.id }) { pkg ->
                PackageCard(
                    pkg = pkg,
                    onEdit = { editingPkg = pkg },
                    onDelete = { vm.deletePackage(pkg.id) }
                )
            }
        }

        item {
            PrimaryButton(
                text = "新增课时包",
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Outlined.Add
            )
        }
    }

    if (showAddDialog) {
        AddPackageDialog(
            students = students.map { it.name },
            onDismiss = { showAddDialog = false },
            onAdd = { sn, name, total, price, pd, ed ->
                vm.addPackage(sn, name, total, price, pd, ed)
                showAddDialog = false
            }
        )
    }

    editingPkg?.let { pkg ->
        EditPackageDialog(
            pkg = pkg,
            students = students.map { it.name },
            onDismiss = { editingPkg = null },
            onSave = { updated ->
                vm.updatePackage(updated)
                editingPkg = null
            }
        )
    }
}

@Composable
private fun PackageCard(
    pkg: LessonPackage,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = when {
        pkg.isExhausted -> MaterialTheme.colorScheme.error
        pkg.isLowBalance -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        pkg.isExpired -> MaterialTheme.colorScheme.outline
        else -> LightPrimary
    }

    // === v26 优化6：状态角标 ===
    // 对于"已过期"或"已退费"的课时包，在卡片右上角显示角标，
    // 让教练在查看"历史记录"时清晰看到该学员的包停用原因
    // - 已过期：浅灰色背景 + "过" 字角标
    // - 已退费：浅红色背景 + "退" 字角标
    // - 已用完：浅黄色背景 + "完" 字角标
    // 默认（活跃）：正常显示
    val statusBadge = when {
        pkg.status == "已退费" -> PackageStatusBadge("退", MaterialTheme.colorScheme.error)
        pkg.status == "已过期" || pkg.isExpired -> PackageStatusBadge("过", MaterialTheme.colorScheme.outline)
        pkg.status == "已用完" || pkg.isExhausted -> PackageStatusBadge("完", MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
        else -> null
    }

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${pkg.studentName} · ${pkg.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (statusBadge != null) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurface
                    )
                    // === v26 优化6：状态角标 ===
                    if (statusBadge != null) {
                        Spacer(Modifier.width(8.dp))
                        PackageStatusBadgeView(statusBadge)
                    }
                }
                Text(
                    "剩余 ${pkg.remainingLessons} / ${pkg.totalLessons} 次" +
                        if (pkg.status != "活跃") " · ${pkg.status}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentColor
                )
                if (pkg.expireDate.isNotBlank()) {
                    val days = pkg.daysToExpiry()
                    val expiryText = when {
                        days < 0 -> "已过期"
                        days == Int.MAX_VALUE -> ""
                        else -> "${days}天后过期"
                    }
                    if (expiryText.isNotBlank()) {
                        Text(
                            "到期：${pkg.expireDate}（$expiryText）",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (days in 0..30) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.outline
                        )
                    }
                }
                // 退费备注
                if (pkg.status == "已退费" && pkg.note.isNotBlank()) {
                    Text(
                        "退费原因：${pkg.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                // 进度条
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(3.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {}
                    Surface(
                        modifier = Modifier.fillMaxWidth(pkg.progress).fillMaxSize(),
                        shape = RoundedCornerShape(3.dp),
                        color = accentColor
                    ) {}
                }
            }
            TextButton(
                onClick = onEdit,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text("编辑", style = MaterialTheme.typography.labelMedium, color = LightPrimary)
            }
            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text("删除", style = MaterialTheme.typography.labelMedium, color = LightPrimary)
            }
        }
    }
}

/**
 * 课时包状态角标数据（v26 优化6）。
 *
 * @param text 角标文字（如"过"、"退"、"完"）
 * @param color 角标颜色
 */
private data class PackageStatusBadge(
    val text: String,
    val color: androidx.compose.ui.graphics.Color
)

/**
 * 课时包状态角标视图（v26 优化6）。
 *
 * 在卡片标题旁显示一个圆角小标签，让教练快速识别该课时包的状态：
 * - "过"：已过期，浅灰色背景
 * - "退"：已退费，浅红色背景
 * - "完"：已用完，浅黄色背景
 */
@Composable
private fun PackageStatusBadgeView(badge: PackageStatusBadge) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = badge.color.copy(alpha = 0.15f)
    ) {
        Text(
            badge.text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = badge.color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AddPackageDialog(
    students: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, Double, String, String) -> Unit
) {
    var student by remember { mutableStateOf(students.firstOrNull() ?: "") }
    var name by remember { mutableStateOf("10次卡") }
    var total by remember { mutableStateOf("10") }
    var price by remember { mutableStateOf("800") }
    var purchaseDate by remember {
        mutableStateOf(
            java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))
        )
    }
    var expireDate by remember { mutableStateOf("") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "新增课时包",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = student, onValueChange = { student = it }, label = { Text("学员姓名") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                 shape = AppTextFieldShape,
                 colors = appTextFieldColors(),)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("套餐名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                 shape = AppTextFieldShape,
                 colors = appTextFieldColors(),)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = total, onValueChange = { total = it.filter { c -> c.isDigit() } }, label = { Text("总次数") }, singleLine = true, modifier = Modifier.weight(1f),
                     shape = AppTextFieldShape,
                     colors = appTextFieldColors(),)
                    OutlinedTextField(value = price, onValueChange = { price = it.filter { c -> c.isDigit() } }, label = { Text("价格(元)") }, singleLine = true, modifier = Modifier.weight(1f),
                     shape = AppTextFieldShape,
                     colors = appTextFieldColors(),)
                }
                OutlinedDatePickerField(value = purchaseDate, onValueChange = { purchaseDate = it }, label = "购买日期")
                OutlinedDatePickerField(value = expireDate, onValueChange = { expireDate = it }, label = "到期日期(可选)")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(student, name, total.toIntOrNull() ?: 10, price.toDoubleOrNull() ?: 0.0, purchaseDate, expireDate)
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 编辑课时包对话框：可修改全部字段（学员、套餐名、总课时、已用课时、价格、
 * 购买日期、到期日期、状态、备注）。
 * 修改「已用课时」即等价于调整剩余课时。
 */
@Composable
private fun EditPackageDialog(
    pkg: LessonPackage,
    students: List<String>,
    onDismiss: () -> Unit,
    onSave: (LessonPackage) -> Unit
) {
    var student by remember { mutableStateOf(pkg.studentName) }
    var name by remember { mutableStateOf(pkg.name) }
    var total by remember { mutableStateOf(pkg.totalLessons.toString()) }
    var used by remember { mutableStateOf(pkg.usedLessons.toString()) }
    var price by remember { mutableStateOf(pkg.price.toInt().toString()) }
    var purchaseDate by remember { mutableStateOf(pkg.purchaseDate) }
    var expireDate by remember { mutableStateOf(pkg.expireDate) }
    var status by remember { mutableStateOf(pkg.status) }
    var note by remember { mutableStateOf(pkg.note) }

    val statusOptions = listOf("活跃", "已用完", "已过期", "已退费")

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "编辑课时包",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = student,
                    onValueChange = { student = it },
                    label = { Text("学员姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),

                 shape = AppTextFieldShape,
                 colors = appTextFieldColors(),)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("套餐名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),

                 shape = AppTextFieldShape,
                 colors = appTextFieldColors(),)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = total,
                        onValueChange = { total = it.filter { c -> c.isDigit() } },
                        label = { Text("总次数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),

                     shape = AppTextFieldShape,
                     colors = appTextFieldColors(),)
                    OutlinedTextField(
                        value = used,
                        onValueChange = { used = it.filter { c -> c.isDigit() } },
                        label = { Text("已用次数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),

                     shape = AppTextFieldShape,
                     colors = appTextFieldColors(),)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it.filter { c -> c.isDigit() } },
                        label = { Text("价格(元)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),

                     shape = AppTextFieldShape,
                     colors = appTextFieldColors(),)
                    // 状态选择
                    OutlinedTextField(
                        value = status,
                        onValueChange = { status = it },
                        label = { Text("状态") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),

                     shape = AppTextFieldShape,
                     colors = appTextFieldColors(),)
                }
                OutlinedDatePickerField(value = purchaseDate, onValueChange = { purchaseDate = it }, label = "购买日期")
                OutlinedDatePickerField(value = expireDate, onValueChange = { expireDate = it }, label = "到期日期(可选)")
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),

                 shape = AppTextFieldShape,
                 colors = appTextFieldColors(),)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val totalInt = total.toIntOrNull() ?: pkg.totalLessons
                val usedInt = (used.toIntOrNull() ?: pkg.usedLessons).coerceIn(0, totalInt)
                onSave(
                    pkg.copy(
                        studentName = student.trim(),
                        name = name.trim(),
                        totalLessons = totalInt,
                        usedLessons = usedInt,
                        price = price.toDoubleOrNull() ?: pkg.price,
                        purchaseDate = purchaseDate,
                        expireDate = expireDate,
                        status = status,
                        note = note
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// =================== 教练管理标签页 ===================

@Composable
private fun CoachTab(vm: OperationViewModel) {
    val coaches by vm.coaches.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screenH, vertical = Spacing.screenV),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        if (coaches.isEmpty()) {
            item {
                GlassCard { Text("暂无教练", color = MaterialTheme.colorScheme.outline) }
            }
        } else {
            items(coaches, key = { it.name }) { c -> CoachCard(c, vm) }
        }

        item {
            PrimaryButton(
                text = "新增教练",
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Outlined.Add
            )
        }
    }

    if (showAddDialog) {
        AddCoachDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, phone, spec ->
                vm.addCoach(name, phone, spec)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun CoachCard(c: Coach, vm: OperationViewModel) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = LightPrimary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (c.phone.isNotBlank() || c.specialty.isNotBlank()) {
                    Text(
                        listOfNotNull(
                            c.phone.takeIf { it.isNotBlank() },
                            c.specialty.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            TextButton(
                onClick = { vm.deleteCoach(c.name) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text("删除", style = MaterialTheme.typography.labelMedium, color = LightPrimary)
            }
        }
    }
}

@Composable
private fun AddCoachDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "新增教练",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("姓名") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                 shape = AppTextFieldShape,
                 colors = appTextFieldColors(),)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("电话") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                 shape = AppTextFieldShape,
                 colors = appTextFieldColors(),)
                OutlinedTextField(value = specialty, onValueChange = { specialty = it }, label = { Text("专长（如田径、球类）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                 shape = AppTextFieldShape,
                 colors = appTextFieldColors(),)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, phone, specialty) }, enabled = name.isNotBlank()) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// =================== 每日计划标签页（合并自 DailyPlanScreen） ===================

/**
 * 每日计划标签页：展示选定日期的排课与签到状态。
 * 从原 DailyPlanScreen 移植，复用 DailyPlanViewModel。
 */
@Composable
private fun DailyPlanTab(
    onSign: (String) -> Unit,
    onOpenLesson: (String) -> Unit
) {
        val vm: DailyPlanViewModel = koinViewModel()

    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val lessons by vm.lessons.collectAsStateWithLifecycle()
    val dayOfWeek by vm.dayOfWeek.collectAsStateWithLifecycle()

    val dayNames = mapOf(
        1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
        5 to "周五", 6 to "周六", 7 to "周日"
    )

    // 预排序排课时间线，避免重组重复排序
    val sortedSchedules = remember(schedules) {
        schedules.sortedBy { it.startTime }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screenH, vertical = Spacing.screenV),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // 日期切换器
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { vm.previousDay() }) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "前一天")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            selectedDate,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            dayNames[dayOfWeek] ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { vm.nextDay() }) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "后一天")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { vm.goToday() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("回到今天", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // 当日统计
        item {
            GlassCard {
                Text("今日概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DailyStatItem(label = "排课", value = "${schedules.size}")
                    DailyStatItem(label = "已签到", value = "${lessons.size}")
                    DailyStatItem(label = "待签到", value = "${(schedules.size - lessons.size).coerceAtLeast(0)}")
                }
            }
        }

        // 排课时间线
        if (schedules.isEmpty()) {
            item {
                GlassCard {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("今日无排课", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        } else {
            item {
                Text("今日排课", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            }
            items(sortedSchedules, key = { it.id }) { schedule ->
                val signedLesson = vm.findSignedLesson(schedule, selectedDate)
                DailyScheduleCard(
                    schedule = schedule,
                    signedLessonId = signedLesson?.id,
                    dayName = dayNames[dayOfWeek] ?: "",
                    onSign = { onSign(schedule.studentName) },
                    onOpenLesson = { signedLesson?.let { onOpenLesson(it.id) } }
                )
            }
        }
    }
}

@Composable
private fun DailyScheduleCard(
    schedule: Schedule,
    signedLessonId: String?,
    dayName: String,
    onSign: () -> Unit,
    onOpenLesson: () -> Unit
) {
    val isSigned = signedLessonId != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSigned) ScoreExcellent.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                Text(
                    "${schedule.startTime} - ${schedule.endTime()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isSigned) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = "已签到",
                        tint = ScoreExcellent, modifier = Modifier.padding(end = 4.dp))
                    Text("已签到", style = MaterialTheme.typography.labelSmall,
                        color = ScoreExcellent)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                schedule.studentName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            val info = buildString {
                append(schedule.lessonType)
                if (schedule.coachName.isNotBlank()) append(" · ${schedule.coachName}")
                if (schedule.location.isNotBlank()) append(" · ${schedule.location}")
                append(" · $dayName")
            }
            Text(info, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSigned) {
                    TextButton(
                        onClick = onOpenLesson,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("查看课时", color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    PrimaryButton(
                        text = "签到",
                        onClick = onSign,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline)
    }
}
