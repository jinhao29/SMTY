package com.shangmentiyu.sportscoach.ui.lesson

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.SportsScore
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.core.TemplateData
import com.shangmentiyu.sportscoach.data.model.ExerciseItem
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.GlassCard
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    lessonId: String,
    onBack: () -> Unit,
    onScoring: () -> Unit,
    onSummary: () -> Unit
) {
    val context = LocalContext.current
    val vm: LessonViewModel = viewModel(factory = AppViewModelFactory(context.applicationContext as android.app.Application))

    val lesson by vm.lesson.collectAsState()
    val exercises by vm.exercises.collectAsState()

    LaunchedEffect(lessonId) {
        vm.loadLesson(lessonId)
    }

    // 离开页面时强制写库一次，避免最后一次防抖未触发导致数据丢失
    DisposableEffect(Unit) {
        onDispose {
            vm.flushSave()
        }
    }

    Scaffold(
        containerColor = Color(0xFF121212),  // v38：全局深色背景
        topBar = {
            TopAppBar(
                title = { Text(lesson?.let { "${it.studentName} · ${it.date}" } ?: "课堂记录") },
                colors = glassTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val l = lesson ?: return@Column

            // === 课时信息卡片 ===
            InfoCard(l, vm)

            // === 训练内容卡片（课中计划表）===
            ContentCard(exercises, vm, l.lessonType)

            // === 课后总结反馈卡片 ===
            SummaryFeedbackCard(l, vm)

            // === 课堂评价卡片 ===
            EvalCard(l, vm)

            // === 课后签退卡片（仅签退按钮 + 签退时间，无拍照） ===
            SignOutCard(l, vm)

            Spacer(modifier = Modifier.height(8.dp))

            // === 底部双按钮 ===
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onScoring,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.SportsScore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("去打分")
                }
                Button(
                    onClick = onSummary,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Summarize, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("生成小结")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoCard(lesson: com.shangmentiyu.sportscoach.data.model.Lesson, vm: LessonViewModel) {
    GlassCard {
        Text("课时信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        // 课时时长
        var duration by remember(lesson.duration) { mutableStateOf(lesson.duration.toString()) }
        var coach by remember(lesson.coach) { mutableStateOf(lesson.coach) }
        var location by remember(lesson.location) { mutableStateOf(lesson.location) }
        OutlinedTextField(
            value = duration,
            onValueChange = { duration = it },
            label = { Text("课时时长(分钟)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = { Text("分钟") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = coach,
                onValueChange = { coach = it },
                label = { Text("教练") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("地点") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 课时类型（自定义输入 + 快捷下拉建议）
        var lessonTypeExpanded by remember { mutableStateOf(false) }
        val lessonTypes = listOf("训练课", "体测课", "技术课", "恢复课")
        ExposedDropdownMenuBox(expanded = lessonTypeExpanded, onExpandedChange = { lessonTypeExpanded = !lessonTypeExpanded }) {
            OutlinedTextField(
                value = lesson.lessonType,
                onValueChange = { input -> vm.updateLesson { it.copy(lessonType = input) } },
                readOnly = false,
                label = { Text("课时类型（可自定义）") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lessonTypeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
            )
            ExposedDropdownMenu(expanded = lessonTypeExpanded, onDismissRequest = { lessonTypeExpanded = false }) {
                lessonTypes.forEach { t ->
                    DropdownMenuItem(text = { Text(t) }, onClick = {
                        vm.updateLesson { it.copy(lessonType = t) }
                        lessonTypeExpanded = false
                    })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 出勤状态（自定义输入 + 快捷下拉建议）
        var attendanceExpanded by remember { mutableStateOf(false) }
        val attendances = listOf("准时", "迟到", "请假", "旷课")
        ExposedDropdownMenuBox(expanded = attendanceExpanded, onExpandedChange = { attendanceExpanded = !attendanceExpanded }) {
            OutlinedTextField(
                value = lesson.attendance,
                onValueChange = { input -> vm.updateLesson { it.copy(attendance = input) } },
                readOnly = false,
                label = { Text("出勤状态（可自定义）") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = attendanceExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
            )
            ExposedDropdownMenu(expanded = attendanceExpanded, onDismissRequest = { attendanceExpanded = false }) {
                attendances.forEach { a ->
                    DropdownMenuItem(text = { Text(a) }, onClick = {
                        vm.updateLesson { it.copy(attendance = a) }
                        attendanceExpanded = false
                    })
                }
            }
        }
        // 保存时长/教练/地点（失焦保存）
        LaunchedEffect(duration) {
            val d = duration.toIntOrNull() ?: 60
            if (d != lesson.duration) vm.updateLesson { it.copy(duration = d) }
        }
        LaunchedEffect(coach, location) {
            vm.updateLesson { it.copy(coach = coach, location = location) }
        }
        // 课时包来源（只读展示）
        Spacer(modifier = Modifier.height(8.dp))
        val pkgName by vm.packageName.collectAsState()
        val pkgText = when {
            lesson.packageId.isBlank() -> "未关联课时包（签到时未扣减）"
            pkgName.isNotEmpty() -> "消耗自：$pkgName"
            else -> "加载中…"
        }
        Text(
            pkgText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * 课前任务卡片已移除（用户需求：课前任务去除）。
 * 训练计划图片改为在排课 ScheduleEditDialog 中导入，课前准备 PreClassTab 中展示。
 */

@Composable
private fun ContentCard(exercises: List<ExerciseItem>, vm: LessonViewModel, lessonType: String) {
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showExerciseDialog by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("训练内容", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            // 进度徽标：已完成 / 总数
            if (exercises.isNotEmpty()) {
                val doneCount = exercises.count { it.done }
                Text(
                    "已完成 $doneCount/${exercises.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (exercises.isEmpty()) {
            Text("暂无训练内容", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        } else {
            exercises.forEachIndexed { index, item ->
                ExerciseRow(
                    item = item,
                    onUpdate = { newItem -> vm.updateExercise(index, newItem) },
                    onDelete = { vm.removeExercise(index) }
                )
                if (index < exercises.size - 1) Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = { showTemplateDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("模板", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(
                onClick = { showExerciseDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("动作库", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(
                onClick = { showCustomDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("自定义", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    // 模板对话框
    if (showTemplateDialog) {
        TemplateDialog(
            onDismiss = { showTemplateDialog = false },
            onSelect = { templateName ->
                val items = TemplateData.getTemplate(templateName).map { (cat, ex) ->
                    ExerciseItem(
                        name = ex.name,
                        sets = ex.sets,
                        reps = ex.reps,
                        intensity = "中",
                        done = false,
                        note = ex.note
                    )
                }
                items.forEach { vm.addExercise(it) }
                showTemplateDialog = false
            }
        )
    }

    // 动作库对话框
    if (showExerciseDialog) {
        ExerciseLibraryDialog(
            onDismiss = { showExerciseDialog = false },
            onSelect = { exercise ->
                vm.addExercise(ExerciseItem(
                    name = exercise.name,
                    sets = exercise.sets,
                    reps = exercise.reps,
                    intensity = "中",
                    done = false,
                    note = exercise.note
                ))
                showExerciseDialog = false
            }
        )
    }

    // 自定义对话框
    if (showCustomDialog) {
        CustomExerciseDialog(
            onDismiss = { showCustomDialog = false },
            onAdd = { item ->
                vm.addExercise(item)
                showCustomDialog = false
            }
        )
    }
}

@Composable
private fun ExerciseRow(item: ExerciseItem, onUpdate: (ExerciseItem) -> Unit, onDelete: () -> Unit) {
    var name by remember(item.name) { mutableStateOf(item.name) }
    var sets by remember(item.sets) { mutableStateOf(item.sets.toString()) }
    var reps by remember(item.reps) { mutableStateOf(item.reps) }
    var intensity by remember(item.intensity) { mutableStateOf(item.intensity) }
    var note by remember(item.note) { mutableStateOf(item.note) }
    var done by remember(item.done) { mutableStateOf(item.done) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = done, onCheckedChange = {
                done = it
                onUpdate(item.copy(done = it))
            })
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("${sets}组 × $reps · $intensity", style = MaterialTheme.typography.bodySmall, color = appOnSurfaceVariant())
                if (note.isNotBlank()) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EvalCard(lesson: com.shangmentiyu.sportscoach.data.model.Lesson, vm: LessonViewModel) {
    var attitude by remember(lesson.attitude) { mutableStateOf(lesson.attitude) }
    var performance by remember(lesson.performance) { mutableStateOf(lesson.performance.toFloat()) }
    var nextGoal by remember(lesson.nextGoal) { mutableStateOf(lesson.nextGoal) }
    var coachComment by remember(lesson.coachComment) { mutableStateOf(lesson.coachComment) }

    GlassCard(glow = true) {
        Text("课堂评价", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // 训练态度：可自由输入的文本框
        OutlinedTextField(
            value = attitude,
            onValueChange = { attitude = it },
            label = { Text("训练态度") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("可自由输入或选择下方快捷项", style = MaterialTheme.typography.bodySmall) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        // 训练态度快捷选项
        val quickAttitudes = listOf("认真", "专注", "积极", "一般", "需努力", "散漫", "分心", "懒散")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            quickAttitudes.forEach { a ->
                FilterChip(
                    selected = attitude == a,
                    onClick = {
                        attitude = a
                        vm.updateLesson { it.copy(attitude = a) }
                    },
                    label = { Text(a, style = MaterialTheme.typography.bodySmall) }
                )
            }
        }
        LaunchedEffect(attitude) {
            if (attitude != lesson.attitude) vm.updateLesson { it.copy(attitude = attitude) }
        }

        Spacer(modifier = Modifier.height(8.dp))
        // 表现评分
        Text("整体表现: ${performance.toInt()}/10", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = performance,
            onValueChange = { performance = it },
            onValueChangeFinished = {
                vm.updateLesson { it.copy(performance = performance.toInt()) }
            },
            valueRange = 1f..10f,
            steps = 8
        )

        Spacer(modifier = Modifier.height(8.dp))
        // 下次课目标
        OutlinedTextField(
            value = nextGoal,
            onValueChange = { nextGoal = it },
            label = { Text("下次课目标") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        LaunchedEffect(nextGoal) {
            if (nextGoal != lesson.nextGoal) vm.updateLesson { it.copy(nextGoal = nextGoal) }
        }

        Spacer(modifier = Modifier.height(8.dp))
        // 教练寄语（自由编辑，给家长的寄语）
        OutlinedTextField(
            value = coachComment,
            onValueChange = { coachComment = it },
            label = { Text("教练寄语") },
            placeholder = { Text("自由填写给家长的寄语，分享时随报告一起发送", style = MaterialTheme.typography.bodySmall) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        LaunchedEffect(coachComment) {
            if (coachComment != lesson.coachComment) vm.updateLesson { it.copy(coachComment = coachComment) }
        }
    }
}

/**
 * 课后签退卡片：仅签退按钮 + 签退时间显示（已取消签退拍照）。
 *
 * 数据流：
 * - 点击「完成签退」按钮调用 [vm.signOut]，该方法设置 signOutTime 为当前 HH:mm 并立即写库
 * - 签退时间已存在时，显示已签退状态与签退时间，按钮变为「已签退」不可点
 *
 * 用户需求：取消课前课后签到签退拍照，正常上课结算即可。
 */
@Composable
private fun SignOutCard(lesson: com.shangmentiyu.sportscoach.data.model.Lesson, vm: LessonViewModel) {
    val signedOut = lesson.signOutTime.isNotBlank()
    var signingOut by remember { mutableStateOf(false) }

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Logout,
                contentDescription = null,
                tint = if (signedOut) ScoreExcellent else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "课后签退",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (signedOut) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = ScoreExcellent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "已签退 ${lesson.signOutTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ScoreExcellent
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // 签退按钮：已签退时禁用（取消拍照，仅保留签退结算功能）
        Button(
            onClick = {
                if (!signedOut && !signingOut) {
                    signingOut = true
                    vm.signOut { ok ->
                        signingOut = false
                    }
                }
            },
            enabled = !signedOut && !signingOut,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (signedOut) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primary
            )
        ) {
            if (signingOut) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("签退中…")
            } else if (signedOut) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("已完成签退")
            } else {
                Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("完成签退（结算本次课时）")
            }
        }
    }
}

/**
 * 课后总结反馈卡片：教练课后填写训练总结与学员反馈。
 * 自动保存到 lesson.summary，并支持快捷模板插入。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryFeedbackCard(lesson: com.shangmentiyu.sportscoach.data.model.Lesson, vm: LessonViewModel) {
    var summary by remember(lesson.id, lesson.summary) { mutableStateOf(lesson.summary) }

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Summarize,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "课后总结反馈",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // 完成度徽标
            val filled = summary.isNotBlank()
            Text(
                if (filled) "已填写" else "未填写",
                style = MaterialTheme.typography.bodySmall,
                color = if (filled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 总结多行文本框
        OutlinedTextField(
            value = summary,
            onValueChange = { summary = it },
            label = { Text("本节课训练总结") },
            placeholder = {
                Text(
                    "请填写学员本节课的训练表现、进步与不足…",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 8
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 快捷模板：点击追加到总结末尾
        Text("快捷模板", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(4.dp))
        val quickTemplates = listOf(
            "体能状态良好，技术动作标准。",
            "进步明显，建议加强核心力量训练。",
            "动作细节需继续打磨，下次课重点强化。",
            "训练态度认真，完成度高。",
            "今日疲劳度较高，注意恢复。"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            quickTemplates.forEach { tpl ->
                FilterChip(
                    selected = false,
                    onClick = {
                        val newSummary = if (summary.isBlank()) tpl
                            else "$summary\n$tpl"
                        summary = newSummary
                    },
                    label = { Text(tpl, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                )
            }
        }

        // 自动保存：当 summary 与数据库值不一致时写入
        LaunchedEffect(summary) {
            if (summary != lesson.summary) {
                vm.updateLesson { it.copy(summary = summary) }
            }
        }
    }
}

// === 对话框组件 ===

@Composable
private fun TemplateDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "选择模板",
        content = {
            Column {
                TemplateData.listTemplates().forEach { name ->
                    TextButton(onClick = { onSelect(name) }) {
                        Text(name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ExerciseLibraryDialog(onDismiss: () -> Unit, onSelect: (com.shangmentiyu.sportscoach.core.Exercise) -> Unit) {
    var selectedCategory by remember { mutableStateOf(TemplateData.listCategories().first()) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "动作库",
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 分类选择
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TemplateData.listCategories().forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 动作列表
                TemplateData.listExercises(selectedCategory).forEach { ex ->
                    TextButton(onClick = { onSelect(ex) }) {
                        Column {
                            Text(ex.name, style = MaterialTheme.typography.bodyMedium)
                            Text("${ex.sets}组 × ${ex.reps}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun CustomExerciseDialog(onDismiss: () -> Unit, onAdd: (ExerciseItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("") }
    var intensity by remember { mutableStateOf("中") }
    var note by remember { mutableStateOf("") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "自定义动作",
        content = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("动作名称 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sets, onValueChange = { sets = it }, label = { Text("组数") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reps, onValueChange = { reps = it }, label = { Text("次数/时长") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("低", "中", "高", "极限").forEach { i ->
                        FilterChip(selected = intensity == i, onClick = { intensity = i }, label = { Text(i) })
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onAdd(ExerciseItem(
                        name = name,
                        sets = sets.toIntOrNull() ?: 3,
                        reps = reps,
                        intensity = intensity,
                        done = false,
                        note = note
                    ))
                }
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
