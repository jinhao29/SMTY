package com.shangmentiyu.sportscoach.ui.growth

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.core.AbilityAnalyzer
import com.shangmentiyu.sportscoach.core.BmiProcessor
import com.shangmentiyu.sportscoach.core.Standards
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.AttendanceAbsent
import com.shangmentiyu.sportscoach.ui.theme.AttendanceLate
import com.shangmentiyu.sportscoach.ui.theme.AttendanceLeave
import com.shangmentiyu.sportscoach.ui.theme.AttendanceOnTime
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientEnd
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientStart
import com.shangmentiyu.sportscoach.ui.theme.MedalBronzeEnd
import com.shangmentiyu.sportscoach.ui.theme.MedalBronzeStart
import com.shangmentiyu.sportscoach.ui.theme.MedalGoldEnd
import com.shangmentiyu.sportscoach.ui.theme.MedalGoldStart
import com.shangmentiyu.sportscoach.ui.theme.MedalSilverEnd
import com.shangmentiyu.sportscoach.ui.theme.MedalSilverStart
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.ScoreFail
import com.shangmentiyu.sportscoach.ui.theme.ScoreGood
import com.shangmentiyu.sportscoach.ui.theme.ScorePass
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.LightSecondary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightTertiary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightSecondary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimaryContainer
import com.shangmentiyu.sportscoach.ui.theme.LightTertiary
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientEnd
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientStart
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientEnd
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientStart
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant

/**
 * 成长档案页：明亮活力渐变风格（Dribbble-inspired）。
 *
 * 设计原则：
 * - 头部学员卡片：蓝紫渐变 + 装饰圆圈，视觉冲击力强
 * - 身体形态：4 个独立彩色渐变卡片，每项独立色系
 * - 统计卡：3 个彩色数字（蓝/紫/绿）+ 白底卡片
 * - 个人最佳：金银铜奖牌渐变
 * - 保留 iOS Inset Grouped 整体结构
 *
 * 数据驱动：即使学员只有 1-2 次成绩，也能展示有意义内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowthScreen(
    studentName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: GrowthViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val student by vm.student.collectAsState()
    val lessons by vm.lessons.collectAsState()
    val scores by vm.scores.collectAsState()
    val latestScores by vm.latestScores.collectAsState()
    val personalBests by vm.personalBests.collectAsState()
    val stats by vm.stats.collectAsState()
    val archiveToast by vm.archiveToast.collectAsState()
    // === v10 终极架构：5 维能力雷达 ===
    val radar by vm.radar.collectAsState()

    // === v28 优化2：PDF 报告生成状态 ===
    val isGenerating by vm.isGenerating.collectAsState()
    val reportUri by vm.reportUri.collectAsState()
    val reportToast by vm.reportToast.collectAsState()

    // === v31 优化4：家长端加密 PDF 分享状态 ===
    val parentShareResult by vm.parentShareResult.collectAsState()
    // 家长姓名输入对话框
    var showParentDialog by remember { mutableStateOf(false) }
    // 密码展示 + 一键微信发送对话框
    var showPasswordDialog by remember { mutableStateOf(false) }

    // 归档确认对话框是否打开
    var showArchiveDialog by remember { mutableStateOf(false) }

    // 监听归档 Toast 消息：通过 Android Toast 显示并自动清除状态
    LaunchedEffect(archiveToast) {
        archiveToast?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            kotlinx.coroutines.delay(2500)
            vm.clearArchiveToast()
        }
    }

    // === v28 优化2：监听 PDF 生成 Toast ===
    LaunchedEffect(reportToast) {
        reportToast?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            kotlinx.coroutines.delay(2500)
            vm.clearReportToast()
        }
    }

    // === v28 优化2：监听 PDF Uri 生成完成，触发系统分享面板 ===
    LaunchedEffect(reportUri) {
        reportUri?.let { uri ->
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                android.content.Intent.createChooser(shareIntent, "分享成长报告")
            )
            vm.clearReportUri()
        }
    }

    // === v31 优化4：监听加密 PDF 生成完成，弹出"密码 + 一键微信发送"对话框 ===
    LaunchedEffect(parentShareResult) {
        if (parentShareResult != null) {
            showPasswordDialog = true
        }
    }

    LaunchedEffect(studentName) {
        vm.load(studentName)
    }

    Scaffold(
        containerColor = appGroupedBackground(),
        topBar = {
            TopAppBar(
                title = { Text("成长档案", color = Color.White, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                // v22 新增：设置入口"归档一年前记录"按钮
                // 使用 Archive 图标，不改变原有标题与返回按钮位置，保持 UI 风格
                actions = {
                    // === v28 优化2：生成成长 PDF 报告按钮 ===
                    // 与"归档"按钮并列在 TopAppBar actions 区，保持珊瑚橙主色风格
                    IconButton(onClick = { vm.generateGrowthReport(context) }) {
                        if (isGenerating) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = "生成成长报告")
                        }
                    }
                    // === v31 优化4：家长端加密 PDF 一键微信分享按钮 ===
                    IconButton(onClick = { showParentDialog = true }) {
                        Icon(Icons.Outlined.Share, contentDescription = "家长端分享")
                    }
                    IconButton(onClick = { showArchiveDialog = true }) {
                        Icon(Icons.Outlined.Archive, contentDescription = "归档一年前记录")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = 88.dp
            )
        ) {
            // === 1. 头部学员卡片：蓝紫渐变 + 装饰圆圈 ===
            item {
                HeroStudentCard(student = student, fallbackName = studentName)
            }

            // === 2. 身体形态：4 个彩色渐变卡片 ===
            val s = student
            if (s != null && (s.age > 0 || s.heightCm > 0 || s.weightKg > 0f || s.bmi > 0f)) {
                item {
                    VitalMetricsGrid(student = s)
                }
            }

            // === v10 终极架构：能力画像（5 维能力雷达图） ===
            // 紧跟身体形态之后，让教练一眼看清学员运动强弱项
            item {
                AbilityRadarCard(radar = radar)
            }

            // === 3. 统计卡：彩色数字（蓝/紫/绿） ===
            item {
                StatsCard(stats = stats)
            }

            // === 4. 最近成绩 ===
            if (latestScores.isNotEmpty()) {
                item {
                    IosSectionHeader(text = "最近成绩 · ${latestScores.first().date}")
                }
                item {
                    IosGroupedListCard {
                        latestScores.forEachIndexed { index, score ->
                            val previous = scores
                                .filter { it.projectName == score.projectName && it.date < score.date }
                                .maxByOrNull { it.date }
                            ScoreRow(
                                score = score,
                                previousScore = previous,
                                showTopDivider = index > 0
                            )
                        }
                    }
                }
            }

            // === 5. 个人最佳：金银铜奖牌 ===
            if (personalBests.isNotEmpty()) {
                item {
                    IosSectionHeader(text = "个人最佳")
                }
                item {
                    IosGroupedListCard {
                        personalBests.forEachIndexed { index, best ->
                            PersonalBestRow(
                                best = best,
                                rank = index + 1,
                                showTopDivider = index > 0
                            )
                        }
                    }
                }
            }

            // === 6. 训练历程（按月分组） ===
            if (lessons.isNotEmpty()) {
                item {
                    IosSectionHeader(text = "训练历程")
                }
                val grouped = lessons.groupBy { it.date.take(7) }.toSortedMap(reverseOrder())
                grouped.forEach { (month, lessonsInMonth) ->
                    item(key = "month_header_$month") {
                        Text(
                            text = month,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = appOnSurfaceVariant(),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = Spacing.xs)
                        )
                    }
                    item(key = "month_group_$month") {
                        IosGroupedListCard {
                            lessonsInMonth
                                .sortedByDescending { it.date }
                                .forEachIndexed { index, lesson ->
                                    HistoryRow(
                                        lesson = lesson,
                                        showTopDivider = index > 0
                                    )
                                }
                        }
                    }
                }
            }

            // === 空状态 ===
            if (lessons.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(Spacing.md))
                            Text(
                                "暂无训练记录",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                "签到后即可查看成长档案",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }

    // v22 新增：归档一年前记录确认对话框
    // - 使用 GlassAlertDialog 保持与现有 UI 风格一致
    // - 确认后调用 ViewModel.archiveLessonsOlderThanOneYear
    // - 结果通过 archiveToast StateFlow 反馈
    if (showArchiveDialog) {
        GlassAlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = "归档一年前记录",
            content = {
                Text(
                    "将一年前的全部课时记录从主表迁移到归档表，" +
                        "释放主表体积以加速日常查询。\n\n" +
                        "归档后的记录仍可在历史报表中查询，不会被删除。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArchiveDialog = false
                        vm.archiveLessonsOlderThanOneYear()
                    }
                ) { Text("归档", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) { Text("取消") }
            }
        )
    }

    // === v31 优化4：家长姓名输入对话框 ===
    // 教练输入家长称呼（如"张爸爸"），点击"生成加密报告"后：
    // 1. 后台生成加密 PDF + 4 位密码
    // 2. 通过 parentShareResult 暴露给 UI
    // 3. 触发 LaunchedEffect 弹出"密码 + 一键微信发送"对话框
    if (showParentDialog) {
        ParentNameInputDialog(
            studentName = studentName,
            onDismiss = { showParentDialog = false },
            onConfirm = { parentName ->
                showParentDialog = false
                vm.generateParentEncryptedReport(context, parentName)
            }
        )
    }

    // === v31 优化4：密码展示 + 一键微信发送对话框 ===
    // 显示 4 位密码，教练口头告知家长；点击"发送到微信"按钮直接跳转微信分享面板
    if (showPasswordDialog && parentShareResult != null) {
        PasswordShareDialog(
            result = parentShareResult!!,
            onDismiss = {
                showPasswordDialog = false
                vm.clearParentShareResult()
            },
            onShareToWeChat = {
                ParentShareHelper.shareToWeChat(context, parentShareResult!!)
            }
        )
    }
}

/**
 * === v31 优化4：家长姓名输入对话框 ===
 *
 * 输入家长称呼（如"张爸爸"、"李妈妈"），用于：
 * - PDF 文件名标识（ParentReport_{学员}_{家长}_{密码}_{时间}.pdf）
 * - 分享文本中的家长专属称呼
 * - "心理压力"防止家长随意转发 PDF
 *
 * 默认填入"XX家长"占位，教练可快速修改。
 */
@Composable
private fun ParentNameInputDialog(
    studentName: String,
    onDismiss: () -> Unit,
    onConfirm: (parentName: String) -> Unit
) {
    var parentName by remember { mutableStateOf("") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "家长端加密分享",
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "为 ${studentName} 生成加密成长报告",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "家长称呼",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(Spacing.xs))
                androidx.compose.material3.OutlinedTextField(
                    value = parentName,
                    onValueChange = { parentName = it },
                    placeholder = { Text("如：张爸爸 / 李妈妈") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "加密后生成 4 位密码，需口头告知家长查看",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(parentName.ifBlank { "家长" }) },
                enabled = parentName.isNotBlank()
            ) { Text("生成加密报告", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * === v31 优化4：密码展示 + 一键微信发送对话框 ===
 *
 * 显示生成结果：
 * - 4 位密码（大字号珊瑚橙突出显示）
 * - PDF 文件名标识（让教练确认家长专属）
 * - "发送到微信"按钮（直接跳转微信分享面板）
 * - "复制密码"按钮（教练可复制密码到剪贴板，方便发送给家长）
 */
@Composable
private fun PasswordShareDialog(
    result: ParentShareHelper.ShareResult,
    onDismiss: () -> Unit,
    onShareToWeChat: () -> Unit
) {
    val context = LocalContext.current
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "加密报告已生成",
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "查看密码",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(Spacing.xs))
                // 大字号密码显示（珊瑚橙主题色）
                Text(
                    result.password,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(Spacing.md))
                // 微信安装状态提示
                if (result.fallbackToSystemShare) {
                    Text(
                        "（未检测到微信，将弹出系统分享面板）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    Text(
                        "（已检测到微信，可一键发送）",
                        style = MaterialTheme.typography.labelSmall,
                        color = ScoreExcellent
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                // 文件路径简略显示（仅显示文件名）
                val fileName = result.filePath.substringAfterLast("/")
                Text(
                    "文件：$fileName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Row {
                // 复制密码按钮
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("查看密码", result.password)
                        )
                        android.widget.Toast.makeText(
                            context, "密码已复制", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                ) { Text("复制密码", color = MaterialTheme.colorScheme.outline) }
                Spacer(Modifier.width(Spacing.sm))
                // 一键微信发送按钮（主按钮珊瑚橙）
                TextButton(
                    onClick = {
                        onShareToWeChat()
                        onDismiss()
                    }
                ) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("发送到微信", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

// ============ 私有 Composable ============

/**
 * 头部学员卡片：蓝紫渐变背景 + 大头像 + 装饰圆圈。
 *
 * 视觉亮点：
 * - 蓝紫渐变（BrandGradientStart → BrandGradientEnd）作为整卡片背景
 * - 右上角两个半透明装饰大圆圈，营造层次感
 * - 64dp 圆形头像（白色描边）+ 白色文字
 * - 副信息使用半透明白色
 */
@Composable
private fun HeroStudentCard(
    student: com.shangmentiyu.sportscoach.data.model.Student?,
    fallbackName: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(BrandGradientStart, BrandGradientEnd)
                ),
                shape = RoundedCornerShape(0.dp)
            )
    ) {
        // 装饰圆圈：右上角两个大圆，半透明白色
        Box(
            modifier = Modifier
                .size(180.dp)
                .offset(x = 220.dp, y = (-60).dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = 280.dp, y = 80.dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 64dp 圆形头像（白色描边）
            val displayName = student?.name ?: fallbackName
            val initial = displayName.firstOrNull()?.toString() ?: "?"
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White.copy(alpha = 0.25f), CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
            }
            // 姓名 + 副信息（白色文字）
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                val s = student
                val subtitle = if (s != null) {
                    listOf(
                        s.gender,
                        Standards.gradeLabel(s.grade),
                        s.school.ifEmpty { null }
                    ).filterNotNull().joinToString(" · ")
                } else ""
                Text(
                    subtitle.ifEmpty { "暂无基本信息" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * 身体形态网格：2×2 彩色渐变卡片。
 *
 * 每个卡片独立色系（橙/蓝/紫/绿），渐变背景 + 白色图标 + 大号数值。
 */
@Composable
private fun VitalMetricsGrid(student: com.shangmentiyu.sportscoach.data.model.Student) {
    // BMI 兜底：旧数据 bmi=0 但身高体重有效时实时计算（与 HomeScreen 保持一致）
    val displayBmi = if (student.bmi > 0f) {
        student.bmi
    } else if (student.heightCm > 0 && student.weightKg > 0f) {
        BmiProcessor.compute(student.heightCm, student.weightKg).bmi
    } else 0f

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VitalCard(
                label = "年龄",
                value = if (student.age > 0) "${student.age}" else "—",
                unit = if (student.age > 0) "岁" else "",
                icon = Icons.Outlined.Cake,
                BrandGradientStart = BrandGradientStart,
                BrandGradientEnd = BrandGradientEnd,
                modifier = Modifier.weight(1f)
            )
            VitalCard(
                label = "身高",
                value = if (student.heightCm > 0) "${student.heightCm}" else "—",
                unit = if (student.heightCm > 0) "cm" else "",
                icon = Icons.Outlined.Height,
                BrandGradientStart = LightSecondary,
                BrandGradientEnd = LightPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VitalCard(
                label = "体重",
                value = if (student.weightKg > 0f) String.format("%.1f", student.weightKg) else "—",
                unit = if (student.weightKg > 0f) "kg" else "",
                icon = Icons.Outlined.MonitorWeight,
                BrandGradientStart = BrandGradientStart,
                BrandGradientEnd = BrandGradientEnd,
                modifier = Modifier.weight(1f)
            )
            VitalCard(
                label = "BMI",
                value = if (displayBmi > 0f) String.format("%.1f", displayBmi) else "—",
                unit = if (displayBmi > 0f) BmiProcessor.classify(displayBmi).label else "",
                icon = Icons.Outlined.Analytics,
                BrandGradientStart = LightTertiary,
                BrandGradientEnd = LightPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 单个身体形态卡片：彩色渐变背景 + 白色图标 + 白色数值/标签。
 */
@Composable
private fun VitalCard(
    label: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    BrandGradientStart: Color,
    BrandGradientEnd: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1.1f)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(BrandGradientStart, BrandGradientEnd)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        // 左上角图标
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(32.dp)
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        // 左下角数值 + 标签
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

/**
 * 统计卡：白底卡片 + 3 个彩色数字（蓝/紫/绿）。
 */
@Composable
private fun StatsCard(stats: GrowthStats) {
    IosGroupedListCard(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                label = "累计课时",
                value = stats.totalLessons.toString(),
                valueColor = LightPrimary,
                modifier = Modifier.weight(1f)
            )
            StatDivider()
            StatItem(
                label = "训练时长",
                value = "${String.format("%.1f", stats.totalHours)}h",
                valueColor = LightSecondary,
                modifier = Modifier.weight(1f)
            )
            StatDivider()
            StatItem(
                label = "准时率",
                value = "${(stats.onTimeRate * 100).toInt()}%",
                valueColor = LightTertiary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * iOS 分组头：13pt SemiBold 次级文字。
 */
@Composable
private fun IosSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = appOnSurfaceVariant(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = Spacing.xs)
    )
}

/**
 * iOS Inset Grouped 卡片：纯白 + 10pt 圆角 + 无阴影。
 */
@Composable
private fun IosGroupedListCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
            )
            .background(Color.White, RoundedCornerShape(10.dp))
    ) {
        content()
    }
}

/**
 * 统计项：彩色大数字（指定颜色）+ 灰色标签。
 */
@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = appOnSurfaceVariant()
        )
    }
}

/**
 * 统计项分隔线：0.5dp 宽 + 40dp 高 + 极浅灰。
 */
@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(40.dp)
            .background(appDividerColor())
    )
}

/**
 * 成绩行：项目名 + 实测值 + 与上次对比箭头 + 分数徽章。
 */
@Composable
private fun ScoreRow(
    score: AbilityAnalyzer.ScoreEntry,
    previousScore: AbilityAnalyzer.ScoreEntry?,
    showTopDivider: Boolean
) {
    Column {
        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .padding(start = Spacing.cardPadding)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(appDividerColor())
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    score.projectName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    score.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            if (previousScore != null) {
                val diff = score.score - previousScore.score
                val arrow = if (diff > 0.1) "↑" else if (diff < -0.1) "↓" else "→"
                val color = when {
                    diff > 0.1 -> ScoreExcellent
                    diff < -0.1 -> ScoreFail
                    else -> MaterialTheme.colorScheme.outline
                }
                Text(
                    "$arrow ${String.format("%.1f", kotlin.math.abs(diff))}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }
            ScoreBadge(score = score.score, grade = score.grade)
        }
    }
}

/**
 * 个人最佳行：奖牌渐变图标 + 项目名 + 最佳成绩/日期 + 分数徽章。
 *
 * @param rank 排名（1=金，2=银，3=铜，其余=蓝色）
 */
@Composable
private fun PersonalBestRow(
    best: AbilityAnalyzer.ScoreEntry,
    rank: Int,
    showTopDivider: Boolean
) {
    val (medalStart, medalEnd) = when (rank) {
        1 -> MedalGoldStart to MedalGoldEnd
        2 -> MedalSilverStart to MedalSilverEnd
        3 -> MedalBronzeStart to MedalBronzeEnd
        else -> LightSecondary to LightPrimary
    }
    Column {
        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 60.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(appDividerColor())
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 奖牌渐变方形图标
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(medalStart, medalEnd)
                        ),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    best.projectName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${best.value} · ${best.date}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            ScoreBadge(score = best.score, grade = best.grade)
        }
    }
}

/**
 * 历史课堂行：出勤状态圆点 + 日期/时间 + 课时类型/时长 + 出勤标签。
 */
@Composable
private fun HistoryRow(
    lesson: Lesson,
    showTopDivider: Boolean
) {
    val attendanceColor = when (lesson.attendance) {
        "准时" -> AttendanceOnTime
        "迟到" -> AttendanceLate
        "请假" -> AttendanceLeave
        "旷课" -> AttendanceAbsent
        else -> MaterialTheme.colorScheme.outline
    }
    val scoreCount = if (lesson.scores.isBlank() || lesson.scores == "{}") 0
                     else try { org.json.JSONObject(lesson.scores).length() } catch (e: Exception) { 0 }

    Column {
        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 44.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(appDividerColor())
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(attendanceColor, CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${lesson.date.takeLast(5)} · ${lesson.time}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${lesson.lessonType} · ${lesson.duration}分钟 · 成绩${scoreCount}项",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            Text(
                lesson.attendance,
                style = MaterialTheme.typography.bodyMedium,
                color = attendanceColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 分数徽章：根据等级显示不同颜色背景 + 白色粗体分数。
 */
@Composable
private fun ScoreBadge(score: Double, grade: String) {
    val bgColor = when (grade) {
        "优秀" -> ScoreExcellent
        "良好" -> ScoreGood
        "及格" -> ScorePass
        else -> ScoreFail
    }
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            String.format("%.1f", score),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
