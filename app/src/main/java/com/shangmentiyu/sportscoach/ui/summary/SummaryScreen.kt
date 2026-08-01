package com.shangmentiyu.sportscoach.ui.summary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.core.JsonSafe
import com.shangmentiyu.sportscoach.core.PhotoCrypto
import com.shangmentiyu.sportscoach.data.model.ExerciseItem
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.LightSecondary
import com.shangmentiyu.sportscoach.ui.theme.LightTertiary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.SafeAsyncImage
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.ScoreFail
import com.shangmentiyu.sportscoach.ui.theme.ScoreGood
import com.shangmentiyu.sportscoach.ui.theme.ScorePass
import com.shangmentiyu.sportscoach.ui.theme.IOSCard
import com.shangmentiyu.sportscoach.ui.theme.IOSSectionHeader
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors
import com.shangmentiyu.sportscoach.util.ShareUtils
import org.json.JSONObject
import java.io.File
/**
 * 课后小结页：完整展示签到信息（照片 + 文字）+ 可编辑小结文本 + 分享导出。
 *
 * 展示内容（签到页面填写的所有信息）：
 * 1. 签到照片（异步解密加载，AES-GCM 加密存储）
 * 2. 课时信息（时长/类型/出勤/教练/地点）
 * 3. 训练内容（ExerciseItem 列表）
 * 4. 成绩摘要
 * 5. 课堂评价（态度/表现/下次目标/教练寄语）
 * 6. 可编辑小结文本框 + 操作按钮（重生成/复制/保存/分享）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    lessonId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: SummaryViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val lesson by vm.lesson.collectAsStateWithLifecycle()
    val student by vm.student.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    var snackbar by remember { mutableStateOf<String?>(null) }
    var showShareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(lessonId) {
        vm.load(lessonId)
    }

    Scaffold(
        containerColor = appGroupedBackground(),
        topBar = {
            TopAppBar(
                title = { Text("课后小结") },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenH, vertical = Spacing.sm)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            val l = lesson ?: run {
                Text("加载中…", color = MaterialTheme.colorScheme.outline)
                return@Column
            }

            // 1. 签到照片卡片
            IOSSectionHeader("签到照片")
            SignPhotoDisplayCard(photoPath = l.photoPath)

            // 2. 课时信息卡片
            IOSSectionHeader("课时信息")
            LessonInfoCard(lesson = l)

            // 3. 训练内容卡片
            val exercises = remember(l.content) { parseExercisesForDisplay(l.content) }
            if (exercises.isNotEmpty()) {
                IOSSectionHeader("训练内容")
                TrainingContentCard(exercises = exercises)
            }

            // 4. 成绩摘要卡片
            val scores = parseScoresForDisplay(l.scores)
            if (scores.isNotEmpty()) {
                IOSSectionHeader("成绩摘要")
                IOSCard {
                    scores.forEach { (name, score, grade) ->
                        val color = when (grade) {
                            "优秀" -> ScoreExcellent
                            "良好" -> ScoreGood
                            "及格" -> ScorePass
                            else -> ScoreFail
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    String.format("%.1f", score),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = color,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(grade, style = MaterialTheme.typography.bodySmall, color = color)
                            }
                        }
                    }
                }
            }

            // 5. 课堂评价卡片
            IOSSectionHeader("课堂评价")
            EvalDisplayCard(
                attitude = l.attitude,
                performance = l.performance,
                nextGoal = l.nextGoal,
                coachComment = l.coachComment
            )

            // 6. 小结文本框（可编辑）
            IOSSectionHeader("课堂小结")
            OutlinedTextField(
                value = summary,
                onValueChange = { vm.updateSummary(it) },
                label = { Text("课堂小结") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            // 7. 操作按钮第一行：重生成 | 复制 | 保存
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { vm.regenerate() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重生成", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(
                    onClick = {
                        copyToClipboard(context, summary)
                        snackbar = "已复制到剪贴板"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Button(
                    onClick = {
                        vm.save { snackbar = "保存成功" }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("保存", style = MaterialTheme.typography.bodySmall)
                }
            }

            // 8. 操作按钮第二行：分享到家长
            Button(
                onClick = { showShareDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("分享给家长", style = MaterialTheme.typography.bodyMedium)
            }
        }
        snackbar?.let {
            Snackbar(modifier = Modifier.padding(16.dp)) { Text(it) }
        }
    }

    // 分享方式选择弹窗
    if (showShareDialog) {
        ShareMethodDialog(
            lesson = lesson,
            student = student,
            summary = summary,
            onDismiss = { showShareDialog = false },
            onShareText = { ShareUtils.shareText(context, summary, "分享课堂小结") },
            onShareImage = { l ->
                val bitmap = ShareUtils.renderLessonReportImage(l, student)
                ShareUtils.shareImage(context, bitmap, "分享课堂报告")
            },
            onShareExcel = { l ->
                val ok = ShareUtils.shareLessonReportExcel(context, l, student)
                if (!ok) snackbar = "生成报告失败"
            }
        )
    }
}

/**
 * 分享方式选择弹窗（文本/图片/Excel 三选一）。
 */
@Composable
private fun ShareMethodDialog(
    lesson: Lesson?,
    student: com.shangmentiyu.sportscoach.data.model.Student?,
    summary: String,
    onDismiss: () -> Unit,
    onShareText: () -> Unit,
    onShareImage: (Lesson) -> Unit,
    onShareExcel: (Lesson) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择分享方式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "可选择以下方式分享课堂情况给家长",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))

                ShareOptionButton(
                    icon = Icons.AutoMirrored.Outlined.TextSnippet,
                    title = "分享文本",
                    subtitle = "推荐微信好友",
                    onClick = { onDismiss(); onShareText() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ShareOptionButton(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "分享图片报告",
                    subtitle = "推荐家长查看",
                    onClick = { onDismiss(); lesson?.let { onShareImage(it) } }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ShareOptionButton(
                    icon = Icons.Outlined.TableChart,
                    title = "分享 Excel 文件",
                    subtitle = "完整数据报表",
                    onClick = { onDismiss(); lesson?.let { onShareExcel(it) } }
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("取消", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * 分享选项按钮（图标 + 标题 + 副标题）。
 */
@Composable
private fun ShareOptionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

/**
 * 签到照片展示卡片：异步解密加载 AES-GCM 加密照片并显示。
 *
 * 安全性：照片在 IO 线程解密为 ByteArray，Coil 直接加载内存字节，
 * 不写回明文文件，符合 PIPL 对生物特征数据的要求。
 */
@Composable
private fun SignPhotoDisplayCard(photoPath: String) {
    val context = LocalContext.current

    val photoBytes by produceState<ByteArray?>(initialValue = null, photoPath) {
        if (photoPath.isBlank()) {
            value = null
            return@produceState
        }
        val file = File(photoPath)
        if (!file.exists()) {
            value = null
            return@produceState
        }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            PhotoCrypto.readPhoto(context, file)
        }
    }

    // 无照片时不显示卡片
    if (photoPath.isBlank() || !File(photoPath).exists()) return

    IOSCard {
        if (photoBytes != null) {
            // === v28 优化5：使用 SafeAsyncImage 提供 Coil 容错兜底 ===
            // === v34：启用全屏预览，教练可双指缩放查看签到照片细节 ===
            // 防止照片损坏或解密失败导致 Compose 渲染崩溃
            SafeAsyncImage(
                model = photoBytes,
                contentDescription = "签到照片",
                contentScale = ContentScale.Crop,
                cornerRadius = 10.dp,
                enableZoomPreview = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text("加载中…", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/**
 * 课时信息卡片：展示时长、类型、出勤、教练、地点。
 */
@Composable
private fun LessonInfoCard(lesson: Lesson) {
    IOSCard {
        InfoRow(icon = Icons.Outlined.Schedule, label = "时长", value = "${lesson.duration}分钟")
        InfoRow(icon = Icons.Outlined.CheckCircle, label = "类型", value = lesson.lessonType)
        InfoRow(icon = Icons.Outlined.Person, label = "出勤", value = lesson.attendance)
        if (lesson.coach.isNotBlank()) {
            InfoRow(icon = Icons.Outlined.Person, label = "教练", value = lesson.coach)
        }
        if (lesson.location.isNotBlank()) {
            InfoRow(icon = Icons.Outlined.LocationOn, label = "地点", value = lesson.location)
        }
    }
}

/**
 * 训练内容卡片：展示 ExerciseItem 列表（动作/组数/次数/强度/完成状态/备注）。
 */
@Composable
private fun TrainingContentCard(exercises: List<ExerciseItem>) {
    IOSCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.ViewList,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "训练内容",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.weight(1f))
            val doneCount = exercises.count { it.done }
            Text(
                "$doneCount/${exercises.size} 完成",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        exercises.forEachIndexed { idx, item ->
            ExerciseDisplayRow(item = item)
            if (idx < exercises.size - 1) {
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/**
 * 单条训练动作展示行。
 */
@Composable
private fun ExerciseDisplayRow(item: ExerciseItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Icon(
            imageVector = if (item.done) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,
            contentDescription = null,
            tint = if (item.done) LightPrimary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (item.done) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                "${item.sets}组 × ${item.reps}（强度${item.intensity}）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            if (item.note.isNotBlank()) {
                Text(
                    "备注：${item.note}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 课堂评价卡片：训练态度 + 整体表现进度条 + 下次课目标 + 教练寄语。
 */
@Composable
private fun EvalDisplayCard(
    attitude: String,
    performance: Int,
    nextGoal: String,
    coachComment: String
) {
    IOSCard {
        InfoRow(icon = Icons.Outlined.Star, label = "训练态度", value = attitude)

        // 整体表现进度条
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "整体表现",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.width(72.dp)
            )
            Spacer(Modifier.width(8.dp))
            LinearProgressIndicator(
                progress = { performance / 10f },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when {
                    performance >= 8 -> LightPrimary
                    performance >= 6 -> LightSecondary
                    performance >= 4 -> LightPrimary
                    else -> LightOnSurfaceVariant
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$performance/10",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (nextGoal.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "下次课目标",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                nextGoal,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (coachComment.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "教练寄语",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                            LightPrimary.copy(alpha = 0.08f),
                            LightSecondary.copy(alpha = 0.05f)
                        )
                        )
                    )
                    .padding(Spacing.sm)
            ) {
                Text(
                    coachComment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * 通用信息行：图标 + 标签 + 值。
 */
@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.width(72.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 解析成绩 JSON 为 (项目名, 得分, 等级) 三元组列表，用于界面展示。 */
private fun parseScoresForDisplay(json: String): List<Triple<String, Double, String>> {
    if (json.isBlank() || json == "{}") return emptyList()
    return try {
        val obj = JSONObject(json)
        obj.keys().asSequence().toList().map { key ->
            val item = obj.getJSONObject(key)
            Triple(key, item.optDouble("score", 0.0), item.optString("grade", ""))
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/** 解析训练内容 JSON 数组为 ExerciseItem 列表（展示用，走 JsonSafe 兜底）。 */
private fun parseExercisesForDisplay(json: String): List<ExerciseItem> {
    if (json.isBlank()) return emptyList()
    val arr = JsonSafe.parseArray(json) ?: return emptyList()
    val result = mutableListOf<ExerciseItem>()
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        result.add(ExerciseItem(
            name = obj.optString("name"),
            sets = obj.optInt("sets", 3),
            reps = obj.optString("reps"),
            intensity = obj.optString("intensity", "中"),
            done = obj.optBoolean("done", false),
            note = obj.optString("note")
        ))
    }
    return result
}

/** 复制文本到系统剪贴板。 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("课堂小结", text))
}
