package com.shangmentiyu.sportscoach.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.shangmentiyu.sportscoach.core.PhotoCrypto
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.SafeAsyncImage
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 课后反馈 Tab：展示今日已签到课时列表，支持查看签到图片、
 * 教练寄语、课堂表现，并支持分享到微信。
 *
 * 增强功能：
 * - 学员筛选：顶部 FilterChip 行，点击学员名筛选对应课时
 * - 内联编辑：每张课时卡片可展开编辑课时详情（类型/教练/时长/地点/出勤）
 *   与课堂反馈（寄语/表现/态度），自动持久化
 * - 删除课时：每张卡片右上角删除按钮，二次确认后删除
 * - 图片分享微信：分享时自动解密签到/签退照片为临时文件，通过 FileProvider 分享图文到微信
 *
 * 每条课时卡片显示：
 * - 学员姓名 + 签到时间 + 签退时间（如有）
 * - 课前签到记录图片（如有，缩略图）
 * - 课后签退记录图片（如有，缩略图）
 * - 教练寄语（lesson.coachComment）
 * - 课堂表现评分（lesson.performance）
 * - 训练态度（lesson.attitude）
 * - 内联编辑区（详情/寄语/评分/态度）
 * - 删除按钮 + 分享到微信按钮（图文）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostClassTab(
    vm: HomeViewModel,
    onSign: (String) -> Unit,
    onOperation: () -> Unit
) {
    val context = LocalContext.current
    val todayLessons by vm.todayLessons.collectAsState()

    // 学员筛选状态：null=全部，否则按学员名筛选
    var selectedStudent by remember { mutableStateOf<String?>(null) }
    // 展开内联编辑的课时 ID
    var expandedLessonId by remember { mutableStateOf<String?>(null) }
    // 删除确认对话框
    var deletingLessonId by remember { mutableStateOf<String?>(null) }
    var deletingLessonName by remember { mutableStateOf("") }

    // 筛选后的课时列表
    val filteredLessons = remember(todayLessons, selectedStudent) {
        if (selectedStudent == null) todayLessons
        else todayLessons.filter { it.studentName == selectedStudent }
    }

    // 今日涉及的学员名集合（用于筛选 Chip）
    val todayStudents = remember(todayLessons) {
        todayLessons.map { it.studentName }.distinct()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenH, vertical = Spacing.screenV),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 今日概览
        IosCard {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text("今日课后反馈", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(label = "已签到", value = "${todayLessons.size}")
                    val signedOut = todayLessons.count { it.signOutTime.isNotBlank() }
                    StatItem(label = "已签退", value = "$signedOut")
                    val withNote = todayLessons.count { it.coachComment.isNotBlank() }
                    StatItem(label = "已写寄语", value = "$withNote")
                    val withContent = todayLessons.count { it.content.isNotBlank() && it.content != "[]" }
                    StatItem(label = "有训练图", value = "$withContent")
                }
            }
        }

        // 学员筛选 Chip 行
        if (todayStudents.isNotEmpty()) {
            IosSectionHeader("学员筛选")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                FilterChip(
                    selected = selectedStudent == null,
                    onClick = { selectedStudent = null },
                    label = { Text("全部") }
                )
                todayStudents.forEach { name ->
                    FilterChip(
                        selected = selectedStudent == name,
                        onClick = { selectedStudent = name },
                        label = { Text(name) }
                    )
                }
            }
        }

        IosSectionHeader("课时记录")

        if (filteredLessons.isEmpty()) {
            IosCard {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (todayLessons.isEmpty()) "今日暂无签到记录"
                        else "该学员今日暂无课时",
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            filteredLessons.forEach { lesson ->
                PostClassLessonCard(
                    lesson = lesson,
                    imageList = vm.parseLessonImages(lesson.contentImages),
                    expanded = expandedLessonId == lesson.id,
                    onToggleExpand = {
                        expandedLessonId = if (expandedLessonId == lesson.id) null else lesson.id
                    },
                    onOpenLesson = { onSign(lesson.id) },
                    onShare = { shareLessonToWechatWithImage(context, lesson) },
                    onUpdateFeedback = { comment, perf, attitude ->
                        // v27：保存反馈时触发签退消课（事务内扣减课时包 + 更新 status）
                        vm.saveFeedbackAndCheckOut(lesson.id, comment, perf, attitude)
                    },
                    onUpdateDetail = { lessonType, coach, duration, location, attendance ->
                        vm.updateLessonDetail(lesson.id, lessonType, coach, duration, location, attendance)
                    },
                    onUpdateImages = { paths ->
                        vm.updateLessonImages(lesson.id, paths)
                    },
                    onDelete = {
                        deletingLessonId = lesson.id
                        deletingLessonName = lesson.studentName
                    },
                    // v27：传入自动填充查询回调
                    onQueryScheduleForAutoFill = {
                        vm.findScheduleForStudentToday(lesson.studentName)
                    }
                )
            }
        }
    }

    // 删除确认对话框
    if (deletingLessonId != null) {
        GlassAlertDialog(
            onDismissRequest = { deletingLessonId = null },
            title = "删除课时记录",
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val id = deletingLessonId!!
                        deletingLessonId = null
                        vm.deleteLesson(id) { ok ->
                            if (ok) expandedLessonId = null
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { deletingLessonId = null }
                ) {
                    Text("取消")
                }
            }
        ) {
            Text(
                "确定要删除 ${deletingLessonName} 的课时记录吗？\n\n" +
                    "注意：此操作仅删除课时记录，不会退还已扣减的课时包次数。" +
                    "如需退还课时，请在课包管理中手动调整。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 单条课时反馈卡片：展示 + 内联编辑（详情/反馈）+ 删除 + 分享。
 *
 * 展开后分两个编辑区：
 * 1. 课时详情：课时类型/教练/时长/地点/出勤状态（onUpdateDetail 持久化）
 * 2. 课堂反馈：表现评分/训练态度/教练寄语（onUpdateFeedback 持久化）
 *
 * 头部右上角"删除"图标按钮触发 onDelete，由父组件弹出 GlassAlertDialog 二次确认。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PostClassLessonCard(
    lesson: Lesson,
    imageList: List<String>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenLesson: () -> Unit,
    onShare: suspend () -> Unit,
    onUpdateFeedback: (coachComment: String, performance: Int, attitude: String) -> Unit,
    onUpdateDetail: (lessonType: String, coach: String, duration: Int, location: String, attendance: String) -> Unit,
    onUpdateImages: (List<String>) -> Unit,
    onDelete: () -> Unit,
    /**
     * === v27：自动填充查询回调 ===
     *
     * 展开时调用此回调查询学员今日的活跃排课（Schedule）。
     * 若查询到排课记录，自动将 Schedule.startTime / durationMinutes / location
     * 预填充到反馈表单输入框作为默认值。
     *
     * 回调返回 List<Schedule>：今日该学员的全部活跃排课（按开始时间升序）
     */
    onQueryScheduleForAutoFill: suspend () -> List<com.shangmentiyu.sportscoach.data.model.Schedule>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }

    // 内联编辑状态：仅展开时初始化，保存时调用对应回调
    // 详情字段
    var editLessonType by remember(lesson.id, expanded) {
        mutableStateOf(if (expanded) lesson.lessonType else "")
    }
    var editCoach by remember(lesson.id, expanded) {
        mutableStateOf(if (expanded) lesson.coach else "")
    }
    var editDuration by remember(lesson.id, expanded) {
        mutableStateOf(if (expanded) lesson.duration.toString() else "60")
    }
    var editLocation by remember(lesson.id, expanded) {
        mutableStateOf(if (expanded) lesson.location else "")
    }
    var editAttendance by remember(lesson.id, expanded) {
        mutableStateOf(if (expanded) lesson.attendance else "准时")
    }
    // 反馈字段
    var editComment by remember(lesson.id, expanded) {
        mutableStateOf(if (expanded) lesson.coachComment else "")
    }
    var editPerf by remember(lesson.id, expanded) {
        mutableStateOf(if (expanded) lesson.performance.toFloat() else 7f)
    }
    var editAttitude by remember(lesson.id, expanded) {
        mutableStateOf(if (expanded) lesson.attitude else "认真")
    }

    // === v27：展开时自动查询今日排课并预填充 location/duration ===
    // 仅在 location/duration 为空时填充（教练可手动覆盖）
    var autoFillCompleted by remember(lesson.id, expanded) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(expanded) {
        if (expanded && !autoFillCompleted) {
            try {
                val schedules = onQueryScheduleForAutoFill()
                if (schedules.isNotEmpty()) {
                    // 取最近一次排课（按 startTime 升序后取最后一个，即今日最晚的排课）
                    // 或匹配 lesson.time 最近的排课
                    val matchedSchedule = schedules.firstOrNull { sched ->
                        // 优先匹配签到时间附近的排课
                        val lessonTime = lesson.time.toIntOrNull() ?: -1
                        val schedTime = sched.startTime.filter { it.isDigit() }.toIntOrNull() ?: -1
                        kotlin.math.abs(lessonTime - schedTime) <= 60
                    } ?: schedules.first()  // 退而求其次取第一条

                    // 仅在原值为空或默认值时预填充，保留教练手动修改的值
                    if (lesson.location.isBlank()) {
                        editLocation = matchedSchedule.location
                    } else {
                        editLocation = lesson.location
                    }
                    if (lesson.duration <= 0 || lesson.duration == 60) {
                        // 默认值 60 是 Lesson 实体默认值，视为未填写，使用排课时长填充
                        if (matchedSchedule.durationMinutes > 0) {
                            editDuration = matchedSchedule.durationMinutes.toString()
                        }
                    }
                    // 注意：不覆盖 lesson.startTime（签到时间字段不可改），duration 用于课后反馈统计
                }
                autoFillCompleted = true
            } catch (e: Exception) {
                // 查询失败不阻塞 UI，教练仍可手动输入
                autoFillCompleted = true
            }
        }
    }

    IosCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            // 头部：姓名 + 签到/签退时间 + 删除按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    lesson.studentName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                // 签退状态徽标
                if (lesson.signOutTime.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Logout, contentDescription = null,
                            tint = ScoreExcellent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("签退 ${lesson.signOutTime}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ScoreExcellent)
                    }
                } else {
                    Text(lesson.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                // 删除按钮：触发二次确认对话框
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除课时",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val info = buildString {
                append(lesson.lessonType)
                if (lesson.coach.isNotBlank()) append(" · ${lesson.coach}")
                if (lesson.duration > 0) append(" · ${lesson.duration}分钟")
                append(" · ${lesson.attendance}")
                append(" · 评分${lesson.performance}")
            }
            Text(info, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)

            // 签到/签退照片展示已取消（用户需求：取消签到签退拍照）
            // 训练内容图片：通过 Lesson.contentImages 字段存储与展示，便于反馈给家长

            // 训练内容图片预览（折叠时显示前2张缩略图，展开时显示全部 + 导入按钮）
            if (imageList.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text("训练内容图片", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                // 横向滚动展示所有图片缩略图
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    imageList.take(if (expanded) imageList.size else 2).forEachIndexed { idx, path ->
                        LessonImageThumb(
                            path = path,
                            canDelete = expanded,
                            onDelete = {
                                val newList = imageList.toMutableList().also { it.removeAt(idx) }
                                onUpdateImages(newList)
                            }
                        )
                    }
                    if (!expanded && imageList.size > 2) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+${imageList.size - 2}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // 教练寄语预览（折叠时）
            if (!expanded && lesson.coachComment.isNotBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Star, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Column {
                        Text("教练寄语", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(lesson.coachComment, style = MaterialTheme.typography.bodySmall,
                            maxLines = 2)
                    }
                }
            }

            // 操作按钮
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onOpenLesson,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("查看详情", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null,
                        modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(4.dp))
                    Text(
                        if (expanded) "收起编辑" else "编辑数据",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(
                    onClick = {
                        if (!sharing) {
                            sharing = true
                            scope.launch {
                                onShare()
                                sharing = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (sharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(Icons.Outlined.Share, contentDescription = null,
                            modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.size(4.dp))
                    Text("分享微信", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            // 内联编辑展开区
            if (expanded) {
                // === 第一部分：课时详情编辑 ===
                Spacer(Modifier.height(Spacing.md))
                Text("课时详情", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))

                // 课时类型（下拉建议 + 自定义）
                var typeExpanded by remember { mutableStateOf(false) }
                val typePresets = listOf("训练课", "体测课", "技术课", "恢复课")
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = editLessonType,
                        onValueChange = { editLessonType = it },
                        readOnly = false,
                        label = { Text("课时类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        typePresets.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = {
                                    editLessonType = t
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 教练 + 时长
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editCoach,
                        onValueChange = { editCoach = it },
                        label = { Text("教练") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f)
                    )
                    OutlinedTextField(
                        value = editDuration,
                        onValueChange = { editDuration = it.filter { c -> c.isDigit() } },
                        label = { Text("时长(分)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))

                // 地点
                OutlinedTextField(
                    value = editLocation,
                    onValueChange = { editLocation = it },
                    label = { Text("上课地点") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // 出勤状态（下拉建议 + 自定义）
                var attExpanded by remember { mutableStateOf(false) }
                val attPresets = listOf("准时", "迟到", "请假", "旷课")
                ExposedDropdownMenuBox(
                    expanded = attExpanded,
                    onExpandedChange = { attExpanded = !attExpanded }
                ) {
                    OutlinedTextField(
                        value = editAttendance,
                        onValueChange = { editAttendance = it },
                        readOnly = false,
                        label = { Text("出勤状态") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(attExpanded) },
                        modifier = Modifier.fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = attExpanded,
                        onDismissRequest = { attExpanded = false }
                    ) {
                        attPresets.forEach { a ->
                            DropdownMenuItem(
                                text = { Text(a) },
                                onClick = {
                                    editAttendance = a
                                    attExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 保存课时详情按钮
                Button(
                    onClick = {
                        val duration = editDuration.toIntOrNull() ?: 60
                        onUpdateDetail(editLessonType, editCoach, duration, editLocation, editAttendance)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("保存课时详情", style = MaterialTheme.typography.labelMedium)
                }

                // === 第二部分：课堂反馈编辑 ===
                Spacer(Modifier.height(Spacing.md))
                Text("课堂状况评分", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))

                // 表现评分 Slider
                Text("整体表现: ${editPerf.toInt()}/10",
                    style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = editPerf,
                    onValueChange = { editPerf = it },
                    valueRange = 1f..10f,
                    steps = 8
                )
                Spacer(Modifier.height(8.dp))

                // 训练态度快捷选项
                Text("训练态度", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("认真", "专注", "积极", "一般", "需努力", "散漫", "分心", "懒散").forEach { a ->
                        FilterChip(
                            selected = editAttitude == a,
                            onClick = { editAttitude = a },
                            label = { Text(a, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 教练寄语输入
                OutlinedTextField(
                    value = editComment,
                    onValueChange = { editComment = it },
                    label = { Text("教练寄语（给家长）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
                Spacer(Modifier.height(8.dp))

                // 保存反馈按钮
                Button(
                    onClick = {
                        onUpdateFeedback(editComment, editPerf.toInt(), editAttitude)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("保存反馈", style = MaterialTheme.typography.labelMedium)
                }

                // === 第三部分：训练内容图片导入 ===
                Spacer(Modifier.height(Spacing.md))
                LessonImagesImportSection(
                    images = imageList,
                    onAddImages = { newPaths ->
                        onUpdateImages(imageList + newPaths)
                    },
                    onRemoveImage = { idx ->
                        val newList = imageList.toMutableList().also { it.removeAt(idx) }
                        onUpdateImages(newList)
                    }
                )
            }
        }
    }
}

/**
 * 课后反馈图片导入区：支持从相册多选图片，用于反馈给家长。
 *
 * 功能：
 * - 点击"添加图片"按钮打开系统 PhotoPicker，支持多选
 * - 选中的图片复制到应用内部存储（filesDir/lesson_images/），持久化保存
 * - 以网格缩略图展示已导入图片，右上角带删除按钮
 *
 * @param images 图片路径列表（应用内部存储绝对路径）
 * @param onAddImages 新增图片路径列表回调
 * @param onRemoveImage 删除指定索引图片回调
 */
@Composable
private fun LessonImagesImportSection(
    images: List<String>,
    onAddImages: (List<String>) -> Unit,
    onRemoveImage: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // PhotoPicker：支持多选图片
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val savedPaths = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri -> copyUriToInternal(context, uri) }
                }
                if (savedPaths.isNotEmpty()) {
                    onAddImages(savedPaths)
                }
            }
        }
    }

    Text("训练内容图片（反馈给家长）",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))

    // 图片网格（每行2张）
    if (images.isNotEmpty()) {
        images.chunked(2).forEachIndexed { rowIdx, rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                rowImages.forEachIndexed { colIdx, path ->
                    val absoluteIdx = rowIdx * 2 + colIdx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(appGroupedBackground())
                    ) {
                        Image(
                            bitmap = loadImageBitmapFromFile(path),
                            contentDescription = "训练内容图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // 右上角删除按钮
                        IconButton(
                            onClick = { onRemoveImage(absoluteIdx) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(appSurface().copy(alpha = 0.8f))
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "删除图片",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                // 不足2张时填充空白保持对齐
                if (rowImages.size < 2) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    } else {
        Text("暂无图片，可添加课堂训练照片反馈给家长",
            style = MaterialTheme.typography.bodySmall,
            color = appOutline())
        Spacer(Modifier.height(Spacing.sm))
    }

    // 添加图片按钮（浅橙填充胶囊，呼应"反馈给家长"语义）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .clickable {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.AddPhotoAlternate,
            contentDescription = "添加图片",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            "添加图片（支持多选）",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 课后反馈图片缩略图：从内部存储路径加载显示。
 * 点击可放大查看（全屏 Dialog），展开编辑时可删除。
 */
@Composable
private fun LessonImageThumb(
    path: String,
    canDelete: Boolean = false,
    onDelete: () -> Unit = {}
) {
    var fullscreen by remember { mutableStateOf(false) }
    val bitmap = remember(path) {
        try {
            val file = File(path)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (_: Exception) { null }
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .clickable { fullscreen = true }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "训练内容图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // 删除按钮（仅展开编辑时显示）
        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(appSurface().copy(alpha = 0.85f))
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "删除图片",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }

    // 全屏查看：复用 PreClassTab 的 ZoomableImageDialog（如有则直接调用）
    if (fullscreen && bitmap != null) {
        ZoomableImageDialog(bitmap = bitmap, onDismiss = { fullscreen = false })
    }
}

/**
 * 将 Uri 图片复制到应用内部存储目录（filesDir/lesson_images/）。
 * 返回保存后的文件绝对路径，失败返回 null。
 */
private fun copyUriToInternal(context: android.content.Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "lesson_images").apply { if (!exists()) mkdirs() }
        val fileName = "img_${System.currentTimeMillis()}_${uri.lastPathSegment?.hashCode() ?: 0}.jpg"
        val destFile = File(dir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        destFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

/**
 * 从文件路径加载 ImageBitmap（同步，适用于小图缩略图）。
 * 文件不存在或解码失败返回空透明图。
 */
private fun loadImageBitmapFromFile(path: String): androidx.compose.ui.graphics.ImageBitmap {
    return try {
        val file = File(path)
        if (file.exists()) {
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            bitmap?.asImageBitmap() ?: androidx.compose.ui.graphics.ImageBitmap(1, 1)
        } else {
            androidx.compose.ui.graphics.ImageBitmap(1, 1)
        }
    } catch (e: Exception) {
        androidx.compose.ui.graphics.ImageBitmap(1, 1)
    }
}

/**
 * 加密照片缩略图：异步解密显示，复用 SignPhotoCard 的解密逻辑。
 */
@Composable
private fun PhotoThumb(path: String, label: String) {
    val context = LocalContext.current
    val photoBytes by produceState<ByteArray?>(initialValue = null, path) {
        if (path.isBlank()) { value = null; return@produceState }
        val file = File(path)
        if (!file.exists()) { value = null; return@produceState }
        value = withContext(Dispatchers.IO) {
            PhotoCrypto.readPhoto(context, file)
        }
    }

    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        if (photoBytes != null) {
            // === v28 优化5：使用 SafeAsyncImage 提供 Coil 容错兜底 ===
            // === v34：启用全屏预览，教练可双指缩放查看签到照片细节 ===
            // 防止用户删除 filesDir/SignPhotos 或照片损坏时 Compose 渲染崩溃
            SafeAsyncImage(
                model = photoBytes,
                contentDescription = "${label}照片",
                contentScale = ContentScale.Crop,
                cornerRadius = 8.dp,
                enableZoomPreview = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/**
 * 分享课时信息到微信（支持图文分享）。
 *
 * 用户需求：课后反馈的训练内容可以添加图片便于反馈给家长。
 * 因此分享功能升级为：文本 + 多张训练内容图片（如有）。
 *
 * 流程：
 * 1. 构建文本内容（学员、时间、寄语、表现等）
 * 2. 解析 Lesson.contentImages 得到图片路径列表
 * 3. 有图片：复制到 cacheDir 并通过 FileProvider 获取 content:// Uri，使用 ACTION_SEND_MULTIPLE
 * 4. 无图片：使用 ACTION_SEND 纯文本分享
 */
private suspend fun shareLessonToWechatWithImage(
    context: android.content.Context,
    lesson: Lesson
) {
    val shareText = buildString {
        append("【${lesson.studentName} 课后反馈】\n\n")
        append("日期：${lesson.date}\n")
        append("签到时间：${lesson.time}\n")
        if (lesson.signOutTime.isNotBlank()) append("签退时间：${lesson.signOutTime}\n")
        if (lesson.lessonType.isNotBlank()) append("课时类型：${lesson.lessonType}\n")
        if (lesson.coach.isNotBlank()) append("教练：${lesson.coach}\n")
        if (lesson.duration > 0) append("时长：${lesson.duration}分钟\n")
        append("训练态度：${lesson.attitude}\n")
        append("表现评分：${lesson.performance}/10\n")
        append("\n")
        if (lesson.content.isNotBlank() && lesson.content != "[]") {
            append("【课堂训练内容】\n${lesson.content}\n\n")
        }
        if (lesson.coachComment.isNotBlank()) {
            append("【教练寄语】\n${lesson.coachComment}\n")
        }
    }

    // 解析训练内容图片
    val imagePaths = parseLessonImagePaths(lesson.contentImages)

    if (imagePaths.isEmpty()) {
        // 纯文本分享
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${lesson.studentName} 课后反馈")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "分享到微信"))
    } else {
        // 图文分享：将图片复制到 cacheDir 并通过 FileProvider 获取 content:// Uri
        val imageUris = withContext(Dispatchers.IO) {
            imagePaths.mapNotNull { path ->
                try {
                    val srcFile = File(path)
                    if (!srcFile.exists()) return@mapNotNull null
                    val cacheDir = File(context.cacheDir, "share_images").apply { if (!exists()) mkdirs() }
                    val destFile = File(cacheDir, "share_${System.currentTimeMillis()}_${srcFile.name}")
                    srcFile.copyTo(destFile, overwrite = true)
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        destFile
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }

        if (imageUris.isEmpty()) {
            // 图片全部读取失败，回退为纯文本分享
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "${lesson.studentName} 课后反馈")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, "分享到微信"))
            return
        }

        // 多图 + 文本分享
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
            putExtra(Intent.EXTRA_SUBJECT, "${lesson.studentName} 课后反馈")
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享到微信"))
    }
}

/** 解析 Lesson.contentImages JSON 为图片路径列表（分享时使用） */
private fun parseLessonImagePaths(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    val arr = com.shangmentiyu.sportscoach.core.JsonSafe.parseArray(json) ?: return emptyList()
    val result = mutableListOf<String>()
    for (i in 0 until arr.length()) {
        val path = arr.optString(i)
        if (path.isNotBlank()) result.add(path)
    }
    return result
}
