package com.shangmentiyu.sportscoach.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.ui.theme.AppTextField
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.StyledSuggestionField
import kotlinx.coroutines.launch

/**
 * 单条课时反馈卡片：展示 + 内联编辑（详情/反馈）+ 删除 + 分享。
 * （v53 从 PostClassTab.kt 拆出，逻辑逐字保留）
 *
 * 展开后分两个编辑区：
 * 1. 课时详情：课时类型/教练/时长/地点/出勤状态（onUpdateDetail 持久化）
 * 2. 课堂反馈：表现评分/训练态度/教练寄语（onUpdateFeedback 持久化）
 *
 * 头部右上角"删除"图标按钮触发 onDelete，由父组件弹出 GlassAlertDialog 二次确认。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PostClassLessonCard(
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
    /** 是否处于多选模式（显示复选框替代删除按钮） */
    selectionMode: Boolean = false,
    /** 多选模式下是否被选中 */
    selected: Boolean = false,
    /** 多选模式下切换选中状态回调 */
    onToggleSelect: () -> Unit = {},
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
    LaunchedEffect(expanded) {
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
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
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
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null,
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
                // 多选模式显示复选框，否则显示删除按钮
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelect() }
                    )
                } else {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除课时",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
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
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(
                    onClick = onOpenLesson,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("查看详情", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(
                    onClick = onToggleExpand,
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
                StyledSuggestionField(
                    value = editLessonType,
                    onValueChange = { editLessonType = it },
                    label = "课时类型",
                    presets = listOf("训练课", "体测课", "技术课", "恢复课")
                )
                Spacer(Modifier.height(8.dp))

                // 教练 + 时长
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppTextField(
                        value = editCoach,
                        onValueChange = { editCoach = it },
                        label = { Text("教练") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f),
                    )
                    AppTextField(
                        value = editDuration,
                        onValueChange = { editDuration = it.filter { c -> c.isDigit() } },
                        label = { Text("时长(分)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))

                // 地点
                AppTextField(
                    value = editLocation,
                    onValueChange = { editLocation = it },
                    label = { Text("上课地点") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // 出勤状态（下拉建议 + 自定义）
                StyledSuggestionField(
                    value = editAttendance,
                    onValueChange = { editAttendance = it },
                    label = "出勤状态",
                    presets = listOf("准时", "迟到", "请假", "旷课")
                )
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
                AppTextField(
                    value = editComment,
                    onValueChange = { editComment = it },
                    label = { Text("教练寄语（给家长）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
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
