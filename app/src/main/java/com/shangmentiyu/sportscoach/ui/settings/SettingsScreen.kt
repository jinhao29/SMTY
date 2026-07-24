package com.shangmentiyu.sportscoach.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.update.UpdateManager
import com.shangmentiyu.sportscoach.update.UpdateResult
import com.shangmentiyu.sportscoach.BuildConfig
import kotlinx.coroutines.launch
import com.shangmentiyu.sportscoach.ui.theme.FeatureIconBlue
import com.shangmentiyu.sportscoach.ui.theme.FeatureIconGreen
import com.shangmentiyu.sportscoach.ui.theme.FeatureIconOrange
import com.shangmentiyu.sportscoach.ui.theme.FeatureIconPurple
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.VitalBlueStart
import com.shangmentiyu.sportscoach.ui.theme.VitalPurpleEnd
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import androidx.compose.ui.graphics.Brush

/**
 * 设置页：iOS Settings 风格。
 *
 * 设计要点（对标 iOS HIG）：
 * - 34pt Large Title "设置"，左对齐
 * - 教练设置：iOS 表单分组（纯白卡片 + 无外框输入框）
 * - 统计信息：纯白卡片 + 三栏数据（今日/总记录/教练）
 * - 数据同步：iOS Settings 列表项（彩色方形图标 + 标题 + 右箭头）
 * - 关于：纯文字信息卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val coach by vm.coach.collectAsState()
    val todayCount by vm.todayCount.collectAsState()
    val totalCount by vm.totalCount.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()

    // 检查更新协程作用域：用于同步检查更新的协程启动
    val checkScope = androidx.compose.runtime.rememberCoroutineScope()

    // 教练编辑对话框状态
    var showCoachDialog by remember { mutableStateOf(false) }
    var coachInput by remember { mutableStateOf("") }

    // 文件选择器（SAF 目录选择，直接传递 Uri 给 ViewModel）
    val exportDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // 持久化读写权限，避免下次选择目录后丢失访问权
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            vm.exportTodayRecords(it)
        }
    }

    val exportArchiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            vm.exportScoresArchive(it)
        }
    }

    val importDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            vm.importStudents(it)
        }
    }

    Scaffold(
        containerColor = appSurface()
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Large Title
                Column(
                    modifier = Modifier.padding(
                        start = Spacing.screenH,
                        end = Spacing.screenH,
                        top = Spacing.xl
                    )
                ) {
                    Text(
                        "设置",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary  // 活力蓝紫
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "教练信息 · 数据同步 · 关于",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // 分组 1：教练设置（点击行打开编辑对话框，确认后保存）
                IosSectionWrapper(text = "教练设置") {
                    IosGroupedListCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coachInput = coach  // 预填当前值，便于修改
                                    showCoachDialog = true
                                }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm + 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            IosIconBadge(
                                icon = Icons.Filled.Person,
                                iconBgColor = FeatureIconBlue,
                                contentDescription = "教练"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "教练姓名",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (coach.isBlank()) "未设置" else coach,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (coach.isBlank())
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = "编辑",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 分组 2：统计信息（三栏数据）
                IosSectionWrapper(text = "统计信息") {
                    IosGroupedListCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.md),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(label = "今日课程", value = todayCount.toString())
                            StatDivider()
                            StatItem(label = "总记录数", value = totalCount.toString())
                            StatDivider()
                            StatItem(label = "教练", value = if (coach.isBlank()) "未设置" else coach)
                        }
                    }
                }

                // 分组 3：数据同步（iOS Settings 风格列表项）
                IosSectionWrapper(text = "数据同步") {
                    IosGroupedListCard {
                        SettingsActionRow(
                            icon = Icons.Filled.Download,
                            iconBgColor = FeatureIconBlue,
                            iconContentDescription = "导出今日记录",
                            title = "导出今日课堂记录",
                            subtitle = "将今日签到记录导出到指定目录",
                            showTopDivider = false,
                            onClick = { exportDirLauncher.launch(null) }
                        )
                        SettingsActionRow(
                            icon = Icons.Filled.CloudUpload,
                            iconBgColor = FeatureIconOrange,
                            iconContentDescription = "导出成绩档案",
                            title = "导出成绩到档案",
                            subtitle = "同步成绩到桌面端 Excel 档案",
                            showTopDivider = true,
                            onClick = { exportArchiveLauncher.launch(null) }
                        )
                        SettingsActionRow(
                            icon = Icons.Filled.FolderOpen,
                            iconBgColor = FeatureIconGreen,
                            iconContentDescription = "导入学员",
                            title = "从档案导入学员",
                            subtitle = "从桌面端 Excel 档案批量导入学员",
                            showTopDivider = true,
                            onClick = { importDirLauncher.launch(null) }
                        )
                    }
                }

                // 分组 4：应用更新（检查新版本 + 安装已下载的更新）
                IosSectionWrapper(text = "应用更新") {
                    IosGroupedListCard {
                        SettingsActionRow(
                            icon = Icons.Filled.Refresh,
                            iconBgColor = FeatureIconGreen,
                            iconContentDescription = "检查更新",
                            title = "检查更新",
                            subtitle = "当前版本 v${BuildConfig.VERSION_NAME} · 点击立即检查",
                            showTopDivider = false,
                            onClick = {
                                // 同步检查：直接调用 UpdateChecker，立即显示结果，
                                // 失败时携带 HTTP 状态码等错误信息，便于诊断
                                // （如私有仓库 404、网络异常等）
                                vm.updateStatus("正在检查更新…")
                                checkScope.launch {
                                    val result = UpdateManager.checkNowSync()
                                    val msg = when (result) {
                                        is UpdateResult.UpToDate ->
                                            "已是最新版本 (v${BuildConfig.VERSION_NAME})"
                                        is UpdateResult.NewVersionAvailable ->
                                            "发现新版本 ${result.tagName}，正在后台下载，下载完成后会弹出通知"
                                        is UpdateResult.Error ->
                                            "检查失败：${result.message}"
                                    }
                                    vm.updateStatus(msg)
                                    // 若有新版本，触发后台下载（沿用原 WorkManager 流程）
                                    if (result is UpdateResult.NewVersionAvailable) {
                                        UpdateManager.checkNow(context)
                                    }
                                }
                            }
                        )
                        SettingsActionRow(
                            icon = Icons.Filled.Download,
                            iconBgColor = FeatureIconBlue,
                            iconContentDescription = "安装已下载的更新",
                            title = "安装已下载的更新",
                            subtitle = "若已下载新版本 APK，点击此处直接安装",
                            showTopDivider = true,
                            onClick = {
                                if (UpdateManager.hasDownloadedApk(context)) {
                                    UpdateManager.installUpdate(context)
                                } else {
                                    vm.updateStatus("暂无已下载的更新文件，请先检查更新")
                                }
                            }
                        )
                    }
                }

                // 分组 5：关于
                IosSectionWrapper(text = "关于") {
                    IosGroupedListCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            IosIconBadge(
                                icon = Icons.Filled.Apps,
                                iconBgColor = FeatureIconPurple,
                                contentDescription = "应用信息"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "体育教学助手",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "v1.0",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                        }
                        // 分隔线
                        Box(
                            modifier = Modifier
                                .padding(start = 60.dp)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            IosIconBadge(
                                icon = Icons.Filled.Info,
                                iconBgColor = FeatureIconBlue,
                                contentDescription = "功能简介"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "课堂实时记录 · 即时打分 · 课后小结",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Excel 同步桌面端",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xxl))
            }

            // 状态消息 Snackbar
            statusMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.lg),
                    action = {
                        TextButton(onClick = { vm.clearStatus() }) { Text("关闭") }
                    }
                ) {
                    Text(msg)
                }
            }

            // 教练编辑确认对话框
            if (showCoachDialog) {
                GlassAlertDialog(
                    onDismissRequest = { showCoachDialog = false },
                    title = "修改教练姓名",
                    content = {
                        OutlinedTextField(
                            value = coachInput,
                            onValueChange = { coachInput = it },
                            label = { Text("教练姓名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            val trimmed = coachInput.trim()
                            if (trimmed.isNotEmpty()) {
                                vm.setCoach(trimmed)
                            }
                            showCoachDialog = false
                        }) { Text("确认保存") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showCoachDialog = false }) { Text("取消") }
                    }
                )
            }
        }
    }
}

/**
 * iOS 分组包装：Section Header（活力蓝紫）+ 单张卡片。
 */
@Composable
private fun IosSectionWrapper(
    text: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,  // 活力蓝紫
            modifier = Modifier.padding(horizontal = 4.dp, vertical = Spacing.xs)
        )
        content()
    }
}

/**
 * iOS Inset Grouped 卡片：纯白 + 10pt 圆角 + 1.5dp 活力蓝紫渐变全包裹边框 + 顶部 4dp 装饰条。
 */
@Composable
private fun IosGroupedListCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(VitalBlueStart, VitalPurpleEnd)
                ),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        // 顶部 4dp 渐变装饰条（活力蓝紫）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(VitalBlueStart, VitalPurpleEnd)
                    )
                )
        )
        content()
    }
}

/**
 * iOS Settings 风格图标徽章：36×36 圆角方形彩色背景 + 白色图标。
 */
@Composable
private fun IosIconBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(iconBgColor, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * iOS Settings 风格操作行：彩色方形图标 + 标题/副标题 + 右箭头。
 *
 * 触碰目标整行 ≥44pt，行间细分隔线左缩进 60dp。
 */
@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    iconContentDescription: String,
    title: String,
    subtitle: String,
    showTopDivider: Boolean,
    onClick: () -> Unit
) {
    Column {
        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 60.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            IosIconBadge(
                icon = icon,
                iconBgColor = iconBgColor,
                contentDescription = iconContentDescription
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 统计项：数值 + 标签竖向叠。
 */
@Composable
private fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = Spacing.sm)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * 统计项分隔线：1dp 宽 + 40dp 高。
 */
@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    )
}
