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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.PlanImage
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.PrimaryButton
import com.shangmentiyu.sportscoach.ui.theme.SecondaryButton
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary

/**
 * 课前准备卡片：展示单条排课的完整信息与签到入口。
 *
 * === 性能优化4：从 PreClassTab.kt 提取为独立 Composable ===
 * - 状态通过参数传入（schedule / signedLesson / contentItems 等），不读取全局 ViewModel
 * - 内部仅保留局部数据库订阅（planImages），不影响父组件重组范围
 * - 拆分后 PreClassTab.kt 主文件重组时，卡片本身可被 Compose 编译器跳过（参数未变则不重组）
 *
 * 三态显示：
 * - 未签到：显示"课前签到"主按钮，点击跳转签到页面
 * - 已签到未签退：显示"课堂详情"次要按钮，点击弹出详情对话框（含"前往签退"入口）
 * - 已签退：显示"课堂详情"次要按钮，点击弹出详情对话框（仅查看，无签退入口）
 *
 * === 防误操作设计 ===
 * 签到/签退后不再直接跳转签到页面，而是先弹出"课堂详情"对话框，
 * 教练可查看签到签退信息后，再决定是否进入签退流程。
 * 这样避免重复点击"进入课堂"导致课时误消除。
 *
 * @param schedule 排课数据
 * @param signedLesson 已签到的课时对象（null=未签到）
 * @param contentItems 解析后的动作列表
 * @param contentImages 训练内容图片路径列表
 * @param equipmentList 上课器材列表
 * @param onSign 签到/签退回调（未签到时跳转签到页，已签到未签退时从详情对话框内跳转签退页）
 * @param onEdit 编辑回调
 * @param onUploadMoment 上传精彩瞬间回调
 */
@Composable
internal fun PreClassScheduleCard(
    schedule: Schedule,
    signedLesson: Lesson?,
    contentItems: List<com.shangmentiyu.sportscoach.data.model.ExerciseItem>,
    contentImages: List<String>,
    equipmentList: List<String>,
    onSign: () -> Unit,
    onEdit: () -> Unit,
    onUploadMoment: () -> Unit = {}
) {
    // === v25：订阅该学员的电脑端训练计划图片（局部状态，不影响父组件）===
    val context = LocalContext.current
    // === 性能优化 H5：在卡片入口缓存主题色 ===
    // appPrimary()/appOnSurfaceVariant() 内部调用 isSystemInDarkTheme()，
    // 该卡片在 LazyColumn 中按学员数量循环组合，每次重组都重复读取系统主题状态。
    // 在入口处计算一次，下方所有 tint/color 复用局部变量，避免重复读取。
    val primaryColor = appPrimary()
    val onSurfaceVariantColor = appOnSurfaceVariant()
    val planImages by produceState(
        initialValue = emptyList<PlanImage>(),
        schedule.studentName
    ) {
        val dao = AppDatabase.getDatabase(context.applicationContext as android.app.Application)
            .planImageDao()
        dao.getByStudent(schedule.studentName).collect { value = it }
    }

    val lessonStatus = signedLesson?.status
    val isSignedIn = signedLesson != null
    val isSignedOut = lessonStatus == "已签退"

    // === 课堂详情对话框状态 ===
    // 已签到/已签退后，点击"课堂详情"按钮弹出此对话框，避免直接跳转签到页导致误操作
    var showLessonDetail by remember { mutableStateOf(false) }

    IosCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    "${schedule.startTime} - ${schedule.endTime()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
                Spacer(Modifier.weight(1f))
                if (isSignedOut) {
                    StatusBadge(
                        text = "已完成",
                        icon = Icons.Outlined.CheckCircle,
                        color = com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
                    )
                } else if (isSignedIn) {
                    StatusBadge(
                        text = "上课中",
                        icon = Icons.Outlined.PlayArrow,
                        color = primaryColor
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "编辑课程",
                        tint = primaryColor,
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

            if (schedule.location.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null,
                        tint = onSurfaceVariantColor,
                        modifier = Modifier.size(14.dp).padding(end = 4.dp))
                    Text("地点：${schedule.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariantColor)
                }
            }

            val info = buildString {
                append(schedule.lessonType)
                if (schedule.coachName.isNotBlank()) append(" · ${schedule.coachName}")
            }
            Spacer(Modifier.height(4.dp))
            Text(info, style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariantColor)

            if (contentItems.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text("上课内容任务", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = primaryColor)
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

            if (contentImages.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text("训练计划图片", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    contentImages.forEach { path ->
                        ScheduleImageThumb(path = path)
                    }
                }
            }

            // 来自电脑端的训练计划截图画廊
            if (planImages.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "来自电脑端的训练计划",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${planImages.size} 张",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariantColor
                    )
                }
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(planImages, key = { it.id }) { planImage ->
                        LanPlanImageCard(planImage = planImage)
                    }
                }
            }

            if (equipmentList.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text("上课器材", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor)
                Text(equipmentList.joinToString("、"), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(Spacing.sm))
            when {
                // === 已签退：显示"课堂详情"按钮（可点击），弹出详情对话框查看签到签退信息 ===
                // 原实现为禁用按钮，教练无法查看已签到签退的详情
                isSignedOut -> {
                    SecondaryButton(
                        text = "课堂详情",
                        onClick = { showLessonDetail = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.CheckCircle
                    )
                }
                // === 已签到未签退：显示"课堂详情"按钮，弹出详情对话框 ===
                // 原实现为"进入课堂"直接跳转签到页，容易误操作导致重复签到
                isSignedIn -> {
                    SecondaryButton(
                        text = "课堂详情",
                        onClick = { showLessonDetail = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.PlayArrow
                    )
                }
                // === 未签到：显示"课前签到"主按钮，直接跳转签到页 ===
                else -> {
                    PrimaryButton(
                        text = "课前签到",
                        onClick = onSign,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.PlayArrow
                    )
                }
            }
            TextButton(
                onClick = onUploadMoment,
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = onSurfaceVariantColor)
                Spacer(Modifier.width(4.dp))
                Text("上传精彩瞬间",
                    color = onSurfaceVariantColor,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    // === 课堂详情对话框 ===
    // 已签到/已签退后，点击"课堂详情"按钮弹出，展示签到签退完整信息
    // 已签到未签退时：底部提供"前往签退"按钮（调用 onSign 跳转签到页）
    // 已签退时：底部仅"关闭"按钮，无签退入口（防止重复签退消课）
    if (showLessonDetail && signedLesson != null) {
        LessonDetailDialog(
            schedule = schedule,
            lesson = signedLesson,
            isSignedOut = isSignedOut,
            onDismiss = { showLessonDetail = false },
            onSign = {
                showLessonDetail = false
                onSign()
            }
        )
    }
}

/**
 * 课堂详情对话框：展示已签到/已签退课时的完整信息。
 *
 * 显示内容：
 * - 签到时间、签退时间
 * - 出勤情况、训练时长、地点、教练
 * - 签到照片状态、签退照片状态
 * - 课时状态（已签到/已签退）、消耗的课时包
 * - 教练寄语、课堂表现、训练态度（如已签退）
 *
 * 底部按钮：
 * - 已签到未签退："前往签退"（调用 onSign）+ "关闭"
 * - 已签退：仅"关闭"（防止重复签退消课）
 *
 * @param schedule 排课数据（补充显示排课信息）
 * @param lesson 已签到的课时对象
 * @param isSignedOut 是否已签退
 * @param onDismiss 关闭回调
 * @param onSign 前往签退回调（仅已签到未签退时可用）
 */
@Composable
private fun LessonDetailDialog(
    schedule: Schedule,
    lesson: Lesson,
    isSignedOut: Boolean,
    onDismiss: () -> Unit,
    onSign: () -> Unit
) {
    val primaryColor = appPrimary()
    val onSurfaceVariantColor = appOnSurfaceVariant()

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "课堂详情",
        confirmButton = {
            if (!isSignedOut) {
                // 已签到未签退：提供"前往签退"按钮
                TextButton(onClick = onSign) {
                    Text("前往签退", color = primaryColor, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // === 状态徽章 ===
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(
                    text = if (isSignedOut) "已签退" else "上课中",
                    icon = if (isSignedOut) Icons.Outlined.CheckCircle else Icons.Outlined.PlayArrow,
                    color = if (isSignedOut)
                        com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
                    else primaryColor
                )
            }

            // === 签到信息 ===
            DetailRow(label = "学员", value = lesson.studentName)
            DetailRow(label = "签到日期", value = lesson.date)
            DetailRow(label = "签到时间", value = lesson.time)
            DetailRow(
                label = "签到照片",
                value = if (lesson.photoPath.isNotBlank()) "已拍照" else "未拍照"
            )

            // === 签退信息（仅已签退时显示）===
            if (isSignedOut && lesson.signOutTime.isNotBlank()) {
                DetailRow(label = "签退时间", value = lesson.signOutTime)
                DetailRow(
                    label = "签退照片",
                    value = if (lesson.signOutPhotoPath.isNotBlank()) "已拍照" else "未拍照"
                )
            }

            // === 课时详情 ===
            DetailRow(label = "课时类型", value = lesson.lessonType)
            if (lesson.coach.isNotBlank()) {
                DetailRow(label = "教练", value = lesson.coach)
            }
            if (lesson.location.isNotBlank()) {
                DetailRow(label = "地点", value = lesson.location)
            }
            DetailRow(label = "训练时长", value = "${lesson.duration} 分钟")
            DetailRow(label = "出勤情况", value = lesson.attendance)

            // === 已签退：显示课后反馈信息 ===
            if (isSignedOut) {
                if (lesson.coachComment.isNotBlank()) {
                    DetailRow(label = "教练寄语", value = lesson.coachComment)
                }
                DetailRow(label = "课堂表现", value = "${lesson.performance}/10")
                if (lesson.attitude.isNotBlank()) {
                    DetailRow(label = "训练态度", value = lesson.attitude)
                }
            }

            // === 防误操作提示 ===
            if (!isSignedOut) {
                Text(
                    "提示：点击「前往签退」将进入签退页面，签退后会自动扣减课时包",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariantColor
                )
            } else {
                Text(
                    "本课时已完成签退，课时包已扣减，无需重复操作",
                    style = MaterialTheme.typography.labelSmall,
                    color = com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
                )
            }
        }
    }
}

/**
 * 详情行：标签 + 值 的横向排列。
 *
 * @param label 标签名（如"签到时间"）
 * @param value 值（如"14:30"）
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = appOnSurfaceVariant(),
            modifier = Modifier.width(72.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = appOnSurface()
        )
    }
}

/**
 * 状态徽章：圆角胶囊 + 图标 + 文字。
 */
@Composable
internal fun StatusBadge(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 排课训练计划图片缩略图：从内部存储路径加载显示。
 * 点击可放大查看（全屏 Dialog，支持双指缩放、拖动、双击重置）。
 */
@Composable
internal fun ScheduleImageThumb(path: String) {
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
            .background(appOnSurface().copy(alpha = 0.05f))
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
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = appOnSurfaceVariant(),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (fullscreen && bitmap != null) {
        ZoomableImageDialog(bitmap = bitmap, onDismiss = { fullscreen = false })
    }
}

/**
 * 电脑端训练计划图片画廊卡片。
 */
@Composable
internal fun LanPlanImageCard(planImage: PlanImage) {
    var fullscreen by remember { mutableStateOf(false) }
    val bitmap = remember(planImage.id, planImage.imagePath) {
        try {
            val file = java.io.File(planImage.imagePath)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (_: Exception) { null }
    }

    val dateText = remember(planImage.createdAt) {
        try {
            val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.CHINA)
            sdf.format(java.util.Date(planImage.createdAt))
        } catch (_: Exception) {
            ""
        }
    }

    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(appOnSurface().copy(alpha = 0.05f))
            .clickable { if (bitmap != null) fullscreen = true }
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(appOnSurface().copy(alpha = 0.05f))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "电脑端训练计划",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = appOnSurfaceVariant(),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        if (dateText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appOnSurface().copy(alpha = 0.08f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurface().copy(alpha = 0.7f)
                )
            }
        }
    }

    if (fullscreen && bitmap != null) {
        ZoomableImageDialog(bitmap = bitmap, onDismiss = { fullscreen = false })
    }
}

/**
 * 全屏可缩放图片查看器。
 *
 * 支持手势：双指捏合缩放（1x~5x）、单指拖动平移、双击切换 1x/2.5x。
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
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "训练计划图片（可缩放）",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .pointerInput(Unit) {
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
                    Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

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
