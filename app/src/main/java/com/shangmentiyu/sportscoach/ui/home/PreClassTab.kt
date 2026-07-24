package com.shangmentiyu.sportscoach.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.dailyplan.DailyPlanViewModel
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.schedule.ScheduleEditDialog
import com.shangmentiyu.sportscoach.ui.theme.Spacing

/**
 * 课前准备 Tab：展示选定日期的排课时间线。
 *
 * 每条排课卡片显示：
 * - 上课时间（开始-结束）
 * - 学员姓名 + 课时类型
 * - 上课地点
 * - 上课内容任务（来自排课 content 字段，解析为动作列表展示）
 * - 训练内容图片（来自排课 contentImages 字段，教练从电脑截图导入的训练计划）
 * - 上课器材（来自排课 equipment 字段）
 * - 快速签到入口（跳转 LESSON_CHECKIN）
 *
 * 新增：顶部"添加排课"按钮，复用 ScheduleEditDialog 快速为当前周几创建排课。
 */
@Composable
fun PreClassTab(
    vm: HomeViewModel,
    onLessonCheckIn: () -> Unit,
    onSchedule: () -> Unit = {}
) {
    val context = LocalContext.current
    val dailyVm: DailyPlanViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )
    val opVm: OperationViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val selectedDate by dailyVm.selectedDate.collectAsState()
    val schedules by dailyVm.schedules.collectAsState()
    val lessons by dailyVm.lessons.collectAsState()
    val dayOfWeek by dailyVm.dayOfWeek.collectAsState()

    var showAddSchedule by remember { mutableStateOf(false) }
    var editingScheduleId by remember { mutableStateOf<String?>(null) }

    val dayNames = mapOf(
        1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
        5 to "周五", 6 to "周六", 7 to "周日"
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenH, vertical = Spacing.screenV),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 日期切换器
        IosCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { dailyVm.previousDay() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前一天")
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
                IconButton(onClick = { dailyVm.nextDay() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "后一天")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedButton(
                    onClick = { dailyVm.goToday() },
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text("回到今天")
                }
                Button(
                    onClick = {
                        opVm.startCreate()
                        showAddSchedule = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加排课")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedButton(
                    onClick = onSchedule,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("查看周课表")
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }

        // 今日统计
        IosCard {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text("今日概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(label = "排课", value = "${schedules.size}")
                    StatItem(label = "已签到", value = "${lessons.size}")
                    StatItem(label = "待签到", value = "${(schedules.size - lessons.size).coerceAtLeast(0)}")
                }
            }
        }

        IosSectionHeader("课前准备清单")

        // 排课时间线
        if (schedules.isEmpty()) {
            IosCard {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("今日无排课", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            schedules.sortedBy { it.startTime }.forEach { schedule ->
                val signedLesson = dailyVm.findSignedLesson(schedule, selectedDate)
                PreClassScheduleCard(
                    schedule = schedule,
                    isSigned = signedLesson != null,
                    contentItems = opVm.parseContent(schedule.content),
                    contentImages = opVm.parseImages(schedule.contentImages),
                    equipmentList = opVm.parseEquipment(schedule.equipment),
                    onSign = { onLessonCheckIn() },
                    onEdit = {
                        opVm.startEdit(schedule.id)
                        editingScheduleId = schedule.id
                    }
                )
            }
        }
    }

    // 添加排课对话框：复用 ScheduleEditDialog，预填当前周几
    if (showAddSchedule) {
        ScheduleEditDialog(
            vm = opVm,
            isCreate = true,
            prefillDayOfWeek = dayOfWeek,
            onDismiss = { showAddSchedule = false },
            onSaved = { showAddSchedule = false }
        )
    }

    // 编辑排课对话框：点击课前准备清单中的课程卡片编辑按钮触发
    if (editingScheduleId != null) {
        ScheduleEditDialog(
            vm = opVm,
            isCreate = false,
            onDismiss = {
                opVm.cancelEdit()
                editingScheduleId = null
            },
            onSaved = {
                opVm.cancelEdit()
                editingScheduleId = null
            }
        )
    }
}

@Composable
private fun PreClassScheduleCard(
    schedule: Schedule,
    isSigned: Boolean,
    contentItems: List<com.shangmentiyu.sportscoach.data.model.ExerciseItem>,
    contentImages: List<String>,
    equipmentList: List<String>,
    onSign: () -> Unit,
    onEdit: () -> Unit
) {
    IosCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    "${schedule.startTime} - ${schedule.endTime()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                if (isSigned) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "已签到",
                        tint = com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("已签到", color = com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent,
                        style = MaterialTheme.typography.labelSmall)
                }
                // 编辑按钮：点击后打开 ScheduleEditDialog 修改课程内容
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑课程",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                schedule.studentName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            // 上课地点
            if (schedule.location.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp).padding(end = 4.dp))
                    Text("地点：${schedule.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }

            // 课时类型 + 教练
            val info = buildString {
                append(schedule.lessonType)
                if (schedule.coachName.isNotBlank()) append(" · ${schedule.coachName}")
            }
            Spacer(Modifier.height(4.dp))
            Text(info, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)

            // 上课内容任务（解析后的动作列表）
            if (contentItems.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text("上课内容任务", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                contentItems.forEach { item ->
                    val detail = buildString {
                        append("• ${item.name.ifBlank { "（未命名）" }}")
                        if (item.sets > 0) append(" · ${item.sets}组")
                        if (item.reps.isNotBlank()) append(" × ${item.reps}")
                        if (item.intensity.isNotBlank()) append(" · ${item.intensity}")
                    }
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                }
            }

            // 训练内容图片（教练从电脑截图导入的训练计划）
            if (contentImages.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text("训练计划图片", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = com.shangmentiyu.sportscoach.ui.theme.FeatureIconOrange)
                // 横向滚动展示图片缩略图
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    contentImages.forEach { path ->
                        ScheduleImageThumb(path = path)
                    }
                }
            }

            // 上课器材
            if (equipmentList.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text("上课器材", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = com.shangmentiyu.sportscoach.ui.theme.FeatureIconOrange)
                Text(equipmentList.joinToString("、"), style = MaterialTheme.typography.bodySmall)
            }

            // 签到按钮
            Spacer(Modifier.height(Spacing.sm))
            if (!isSigned) {
                Button(
                    onClick = onSign,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("课前签到")
                }
            }
        }
    }
}

/**
 * 排课训练计划图片缩略图：从内部存储路径加载显示。
 * 点击可放大查看（全屏 Dialog，支持双指缩放、拖动、双击重置）。
 */
@Composable
private fun ScheduleImageThumb(path: String) {
    var fullscreen by remember { mutableStateOf(false) }
    val bitmap = remember(path) {
        try {
            val file = java.io.File(path)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (_: Exception) { null }
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .clickable { fullscreen = true }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "训练计划图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // 全屏缩放查看
    if (fullscreen && bitmap != null) {
        ZoomableImageDialog(bitmap = bitmap, onDismiss = { fullscreen = false })
    }
}

/**
 * 全屏可缩放图片查看器。
 *
 * 支持手势：
 * - 双指捏合缩放（1x ~ 5x）
 * - 放大后单指拖动平移
 * - 双击切换 1x / 2.5x
 * - 缩放回到 1x 时自动归位
 *
 * @param bitmap 待显示的 Bitmap
 * @param onDismiss 关闭回调（点击关闭按钮或返回键）
 */
@Composable
internal fun ZoomableImageDialog(
    bitmap: android.graphics.Bitmap,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
            // 图片：应用缩放与平移变换
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "训练计划图片（可缩放）",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // 双指缩放 + 单指拖动（仅放大时生效）
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                // 缩回 1x 时归位
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // 双击切换 1x / 2.5x
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )

            // 右上角关闭按钮
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 底部缩放比例提示
            Text(
                "${scale.toInt()}x  ·  双击切换  ·  双指缩放",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
