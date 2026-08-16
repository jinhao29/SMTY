package com.shangmentiyu.sportscoach.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 课后反馈 Tab：展示**全部历史签到签退记录**，按日期分组、对应学员对应日期，
 * 支持查看签到图片、教练寄语、课堂表现，并支持分享到微信。
 *
 * 设计要点（v46：从"仅今日"扩展为"全部历史记录"）：
 * - 数据源：[HomeViewModel.allLessons]（lessons 表全量，date DESC/time DESC 排序）
 * - 按日期分组：每个日期作为 SectionHeader，下方罗列当日所有学员的签到签退记录
 * - 学员筛选：顶部 FilterChip 行，点击学员名筛选对应课时（跨日期）
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
 *
 * v53 拆分：课时卡片迁至 [PostClassLessonCard]，图片组件迁至 [LessonImageComponents]。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostClassTab(
    vm: HomeViewModel,
    onSign: (String) -> Unit
) {
    val context = LocalContext.current
    val allLessons by vm.allLessons.collectAsStateWithLifecycle()

    // 学员筛选状态：null=全部，否则按学员名筛选
    var selectedStudent by remember { mutableStateOf<String?>(null) }
    // 展开内联编辑的课时 ID
    var expandedLessonId by remember { mutableStateOf<String?>(null) }
    // 删除确认对话框
    var deletingLessonId by remember { mutableStateOf<String?>(null) }
    var deletingLessonName by remember { mutableStateOf("") }
    // === 多选删除状态 ===
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    // 筛选后的课时列表（按学员筛选，保留全部历史日期）
    val filteredLessons = remember(allLessons, selectedStudent) {
        if (selectedStudent == null) allLessons
        else allLessons.filter { it.studentName == selectedStudent }
    }

    // 全选目标 = 当前筛选后的全部课时 ID（支持筛选后一键全选）
    val allFilteredIds = remember(filteredLessons) { filteredLessons.map { it.id }.toSet() }
    val allSelected = allFilteredIds.isNotEmpty() && allFilteredIds.all { it in selectedIds }

    // 按日期分组：日期降序（最近在前），同一日期内课时已按 time DESC 排序
    // 每条记录对应"学员+日期"，通过日期 SectionHeader 体现"对应日期"
    val groupedByDate: List<Pair<String, List<Lesson>>> = remember(filteredLessons) {
        filteredLessons
            .groupBy { it.date }
            .toList()
            .sortedByDescending { it.first }
    }

    // 全部历史涉及的学员名集合（用于筛选 Chip，跨日期）
    val allStudents = remember(allLessons) {
        allLessons.map { it.studentName }.distinct()
    }

    // 日期格式化：YYYY-MM-DD → yyyy年MM月dd日 周X
    val dateFmt = remember {
        java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日", java.util.Locale.getDefault())
    }
    val weekDayNames = remember {
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }

    // 缓存概览统计数据，避免 allLessons 变化时重复 4 次 O(n) 遍历
    val totalRecords = remember(allLessons) { allLessons.size }
    val signedOutCount = remember(allLessons) { allLessons.count { it.signOutTime.isNotBlank() } }
    val withNoteCount = remember(allLessons) { allLessons.count { it.coachComment.isNotBlank() } }
    val withContentCount = remember(allLessons) { allLessons.count { it.content.isNotBlank() && it.content != "[]" } }

    // === 性能优化 H3+M5：改用 LazyColumn ===
    // 原 Column + verticalScroll + forEach 一次性把所有 PostClassLessonCard 组合进树，
    // 每张卡片展开后含 10+ OutlinedTextField、多个 ExposedDropdownMenuBox、Slider，
    // 课时数多时组合开销极大。LazyColumn 仅组合屏幕可见卡片，未可见的自动回收。
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.screenH, vertical = Spacing.screenV),
            // 悬浮底栏避让：多选模式下额外留出底部操作栏高度
            contentPadding = PaddingValues(bottom = if (multiSelectMode) 220.dp else 160.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 全部记录概览
            item(key = "overview") {
                IosCard {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text("课后反馈记录", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(Spacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(label = "总记录", value = "$totalRecords")
                            StatItem(label = "已签退", value = "$signedOutCount")
                            StatItem(label = "已写寄语", value = "$withNoteCount")
                            StatItem(label = "有训练图", value = "$withContentCount")
                        }
                    }
                }
            }

            // === 多选删除开关 ===
            item(key = "multi_select_toggle") {
                IosCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("多选删除", style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold)
                            Text(
                                if (multiSelectMode) "勾选要删除的课时记录，可全选后一键删除"
                                else "开启后每条课时记录前显示复选框，支持批量删除",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = multiSelectMode,
                            onCheckedChange = { checked ->
                                multiSelectMode = checked
                                if (!checked) selectedIds = emptySet()
                            }
                        )
                    }
                }
            }

            // 学员筛选 Chip 行
            if (allStudents.isNotEmpty()) {
                item(key = "filter_chips") {
                    Column {
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
                            allStudents.forEach { name ->
                                FilterChip(
                                    selected = selectedStudent == name,
                                    onClick = { selectedStudent = name },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "header_lessons") {
                IosSectionHeader("课时记录（按日期分组）")
            }

            if (groupedByDate.isEmpty()) {
                item(key = "empty") {
                    IosCard {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (allLessons.isEmpty()) "暂无签到签退记录"
                                else "该学员暂无课时记录",
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                // 按日期分组渲染：每个日期一个 SectionHeader + 当日全部学员的课时卡片
                groupedByDate.forEach { (date, lessonsOnDate) ->
                    item(key = "date_header_${date}") {
                        DateSectionHeader(date = date, dateFmt = dateFmt, weekDayNames = weekDayNames,
                            lessonCount = lessonsOnDate.size)
                    }
                    items(
                        items = lessonsOnDate,
                        key = { lesson -> lesson.id }
                    ) { lesson ->
                        // L3 优化：解析图片 JSON 用 remember 缓存，避免每次重组都重新解析
                        val imageList = remember(lesson.contentImages) {
                            vm.parseLessonImages(lesson.contentImages)
                        }
                        PostClassLessonCard(
                            lesson = lesson,
                            imageList = imageList,
                            expanded = !multiSelectMode && expandedLessonId == lesson.id,
                            selectionMode = multiSelectMode,
                            selected = lesson.id in selectedIds,
                            onToggleSelect = {
                                selectedIds = if (lesson.id in selectedIds) selectedIds - lesson.id else selectedIds + lesson.id
                            },
                            onToggleExpand = {
                                if (!multiSelectMode) {
                                    expandedLessonId = if (expandedLessonId == lesson.id) null else lesson.id
                                }
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
        }

        // === 底部多选操作栏 ===
        if (multiSelectMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(appSurface())
                    .padding(horizontal = Spacing.screenH)
                    .padding(top = Spacing.sm, bottom = 100.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        "已选 ${selectedIds.size} 节",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        selectedIds = if (allSelected) emptySet() else allFilteredIds
                    }) { Text(if (allSelected) "取消全选" else "全选") }
                    Button(
                        onClick = { showBatchDeleteConfirm = true },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("删除选中") }
                }
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

    // 批量删除确认对话框
    if (showBatchDeleteConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = "批量删除课时记录",
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val ids = selectedIds.toList()
                        showBatchDeleteConfirm = false
                        vm.deleteLessons(ids) { count ->
                            if (count >= 0) {
                                selectedIds = emptySet()
                                multiSelectMode = false
                            }
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showBatchDeleteConfirm = false }
                ) {
                    Text("取消")
                }
            }
        ) {
            Text(
                "确定要删除选中的 ${selectedIds.size} 条课时记录吗？\n\n" +
                    "注意：此操作仅删除课时记录，不会退还已扣减的课时包次数。" +
                    "如需退还课时，请在课包管理中手动调整。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 日期分组小标题：展示"yyyy年MM月dd日 周X · N 条记录"。
 *
 * 设计要点：
 * - 日期文本加粗深色，体现"对应日期"的视觉锚点
 * - 右侧附带当日记录条数，方便教练快速定位
 * - 与 [IosSectionHeader] 区分：日期分组带条数徽标，更具体
 *
 * @param date 日期字符串 YYYY-MM-DD
 * @param dateFmt 日期格式化器（线程安全 [java.time.format.DateTimeFormatter]）
 * @param weekDayNames 周几名称列表（index 0=周一 ... 6=周日）
 * @param lessonCount 当日课时记录条数
 */
@Composable
private fun DateSectionHeader(
    date: String,
    dateFmt: java.time.format.DateTimeFormatter,
    weekDayNames: List<String>,
    lessonCount: Int
) {
    // 线程安全：java.time.LocalDate.parse + DateTimeFormatter，替代 SimpleDateFormat
    val displayText = remember(date, dateFmt, weekDayNames) {
        try {
            val localDate = java.time.LocalDate.parse(date)
            val formatted = localDate.format(dateFmt)
            // ISO 周几：1=周一 ... 7=周日
            val weekIdx = localDate.dayOfWeek.value - 1
            val weekLabel = weekDayNames.getOrElse(weekIdx) { "" }
            "$formatted $weekLabel"
        } catch (e: Exception) {
            date
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = com.shangmentiyu.sportscoach.ui.theme.appPrimary()
        )
        Text(
            text = "$lessonCount 条",
            style = MaterialTheme.typography.labelSmall,
            color = com.shangmentiyu.sportscoach.ui.theme.appOutline()
        )
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
    val arr = com.shangmentiyu.sportscoach.data.internal.JsonSafe.parseArray(json) ?: return emptyList()
    val result = mutableListOf<String>()
    for (i in 0 until arr.length()) {
        val path = arr.optString(i)
        if (path.isNotBlank()) result.add(path)
    }
    return result
}
