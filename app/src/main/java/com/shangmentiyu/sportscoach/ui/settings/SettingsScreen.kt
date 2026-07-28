package com.shangmentiyu.sportscoach.ui.settings

import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.ProgressDialog
import com.shangmentiyu.sportscoach.excel.ImportStrategy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.update.UpdateManager
import com.shangmentiyu.sportscoach.update.UpdateResult
import com.shangmentiyu.sportscoach.BuildConfig
import com.shangmentiyu.sportscoach.util.CrashDumper
import kotlinx.coroutines.launch
import com.shangmentiyu.sportscoach.ui.theme.LightSecondary
import com.shangmentiyu.sportscoach.ui.theme.LightTertiary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientEnd
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientStart
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appSurface

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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val backupInProgress by vm.backupInProgress.collectAsState()
    val backupProgress by vm.backupProgress.collectAsState()
    val needRestart by vm.needRestart.collectAsState()
    // === v30 全自动无感备份开关状态 ===
    val autoBackupEnabled by vm.autoBackupEnabled.collectAsState()

    // === v25 新增：电脑端截图同步状态订阅 ===
    // pendingLanPlan：UdpPlanListenerService 收到广播后写入 SharedPreferences，进入设置页时由 ViewModel 读取
    // lanPlanSyncing：是否正在下载图片，控制按钮禁用与加载动画显示
    val pendingLanPlan by vm.pendingLanPlan.collectAsState()
    val lanPlanSyncing by vm.lanPlanSyncing.collectAsState()
    // 进入设置页时主动刷新一次，确保从通知点击进入时立即显示待同步信息
    // === v34：包裹 try-catch，避免初始化阶段抛 NPE ===
    LaunchedEffect(Unit) {
        try {
            vm.refreshPendingLanPlan()
        } catch (e: Exception) {
            CrashDumper.dumpBoth(context, "SettingsScreen.LaunchedEffect.refreshPendingLanPlan", e)
        }
    }

    // === v24 优化3：统一进度对话框（导出/备份/恢复全程显示）===
    val progressState by vm.progressState.collectAsState()
    // 完成态自动延迟 1.5s 关闭，让用户看到"完成"反馈
    LaunchedEffect(progressState) {
        if (!progressState.isActive && progressState.progress >= 1f && progressState.currentStep.isNotBlank()) {
            kotlinx.coroutines.delay(1500L)
            vm.consumeProgressState()
        }
    }
    if (progressState.isActive || (!progressState.isActive && progressState.progress >= 1f && progressState.currentStep.isNotBlank())) {
        ProgressDialog(progressState)
    }

    // 签到照片存储空间信息（进入设置页时自动扫描一次）
    val signPhotosSize by vm.signPhotosSize.collectAsState()
    val signPhotosCount by vm.signPhotosCount.collectAsState()
    val cleanableCount by vm.cleanableCount.collectAsState()
    // === v34：包裹 try-catch，扫描失败也不弹 NPE 黑色提示 ===
    LaunchedEffect(Unit) {
        try {
            vm.scanSignPhotos()
        } catch (e: Exception) {
            CrashDumper.dumpBoth(context, "SettingsScreen.LaunchedEffect.scanSignPhotos", e)
        }
    }

    // === v29 优化3：缓存管理（孤立照片 + 临时缓存） ===
    // 进入设置页时自动扫描一次孤立照片与缓存目录占用，让用户直观感知可清理空间
    val orphanPhotoCount by vm.orphanPhotoCount.collectAsState()
    val orphanPhotoSize by vm.orphanPhotoSize.collectAsState()
    val cacheSize by vm.cacheSize.collectAsState()
    val cacheFileCount by vm.cacheFileCount.collectAsState()
    // === v34：包裹 try-catch，扫描失败也不弹 NPE 黑色提示 ===
    LaunchedEffect(Unit) {
        try {
            vm.scanOrphanPhotos()
            vm.scanCacheSize()
        } catch (e: Exception) {
            CrashDumper.dumpBoth(context, "SettingsScreen.LaunchedEffect.scanOrphanCache", e)
        }
    }

    // 检查更新协程作用域：用于同步检查更新的协程启动
    val checkScope = androidx.compose.runtime.rememberCoroutineScope()

    // 教练编辑对话框状态
    var showCoachDialog by remember { mutableStateOf(false) }
    var coachInput by remember { mutableStateOf("") }

    // 恢复确认对话框：恢复会覆盖当前所有数据，需用户二次确认
    var showRestoreConfirm by remember { mutableStateOf(false) }

    // 清理一年前签到照片确认对话框：删除照片不可恢复，需用户二次确认
    var showCleanPhotosConfirm by remember { mutableStateOf(false) }

    // === v29 优化3：缓存管理两个二次确认对话框 ===
    // 清理孤立照片与清理缓存均不可恢复，需用户二次确认
    var showCleanOrphanPhotosConfirm by remember { mutableStateOf(false) }
    var showCleanCacheConfirm by remember { mutableStateOf(false) }

    // === v25 优化4：Excel 导入策略选择对话框 ===
    // 用户点击"从档案导入学员"时先弹出此对话框，选择策略后再启动目录选择器
    var showImportStrategyDialog by remember { mutableStateOf(false) }
    // 暂存用户选择的策略，目录选择器回调时取出使用
    var pendingImportStrategy by remember { mutableStateOf<ImportStrategy?>(null) }

    // 文件选择器（SAF 目录选择，直接传递 Uri 给 ViewModel）
    // === v34：所有 launcher 回调加 try-catch 兜底，防止 NPE 直接弹"操作遇到异常" ===
    // 真实堆栈通过 CrashDumper 输出到 Logcat + crash_logs 文件夹
    val exportDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        try {
            if (uri == null) {
                // 用户取消选择，静默处理
                return@rememberLauncherForActivityResult
            }
            // 持久化读写权限，避免下次选择目录后丢失访问权
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.exportTodayRecords(uri)
        } catch (e: Exception) {
            CrashDumper.dumpBoth(context, "SettingsScreen.exportDirLauncher", e)
            Toast.makeText(
                context,
                "导出路径异常，请检查存储空间权限",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val exportArchiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        try {
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.exportScoresArchive(uri)
        } catch (e: Exception) {
            CrashDumper.dumpBoth(context, "SettingsScreen.exportArchiveLauncher", e)
            Toast.makeText(
                context,
                "导出档案异常，请检查存储权限",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val importDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        try {
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            // v25 优化4：按用户已选择的策略导入，未选则默认 APPEND（向后兼容）
            val strategy = pendingImportStrategy ?: ImportStrategy.APPEND
            vm.importStudentsWithStrategy(uri, strategy)
            // 用完即清，避免下次误用旧选择
            pendingImportStrategy = null
        } catch (e: Exception) {
            CrashDumper.dumpBoth(context, "SettingsScreen.importDirLauncher", e)
            Toast.makeText(
                context,
                "导入学员异常，请检查档案文件是否可读",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // 备份文件创建器：使用 SAF CreateDocument 让用户选择保存位置与文件名
    // 默认文件名带时间戳，避免覆盖旧备份
    val backupFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        try {
            if (uri == null) return@rememberLauncherForActivityResult
            vm.backupData(uri)
        } catch (e: Exception) {
            CrashDumper.dumpBoth(context, "SettingsScreen.backupFileLauncher", e)
            Toast.makeText(
                context,
                "备份失败，请检查存储空间是否充足",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // 恢复文件选择器：使用 SAF OpenDocument 让用户选择 .smty_backup 备份文件
    // 仅允许选择备份文件类型，避免误选其他文件导致恢复失败
    val restoreFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        try {
            if (uri == null) return@rememberLauncherForActivityResult
            // 持久化读权限，避免下次选择时丢失访问权
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            vm.restoreData(uri)
        } catch (e: Exception) {
            CrashDumper.dumpBoth(context, "SettingsScreen.restoreFileLauncher", e)
            Toast.makeText(
                context,
                "恢复文件异常，请检查备份文件是否损坏",
                Toast.LENGTH_LONG
            ).show()
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
                        color = appOnSurfaceVariant()
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
                                icon = Icons.Outlined.Person,
                                iconBgColor = LightPrimary,
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
                                Icons.Outlined.ChevronRight,
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
                            icon = Icons.Outlined.Download,
                            iconBgColor = LightPrimary,
                            iconContentDescription = "导出今日记录",
                            title = "导出今日课堂记录",
                            subtitle = "将今日签到记录导出到指定目录",
                            showTopDivider = false,
                            onClick = { exportDirLauncher.launch(null) }
                        )
                        SettingsActionRow(
                            icon = Icons.Outlined.CloudUpload,
                            iconBgColor = LightPrimary,
                            iconContentDescription = "导出成绩档案",
                            title = "导出成绩到档案",
                            subtitle = "同步成绩到桌面端 Excel 档案",
                            showTopDivider = true,
                            onClick = { exportArchiveLauncher.launch(null) }
                        )
                        SettingsActionRow(
                            icon = Icons.Outlined.FolderOpen,
                            iconBgColor = LightPrimary,
                            iconContentDescription = "导入学员",
                            title = "从档案导入学员",
                            subtitle = "从桌面端 Excel 档案批量导入学员",
                            showTopDivider = true,
                            onClick = { showImportStrategyDialog = true }
                        )
                    }
                }

                // === v25 新增：分组 3.5 跨端同步（电脑端截图 → 手机端）===
                // 局域网纯离线通信：电脑端 PySide6 截图 + HTTP 服务 + UDP 广播
                // 手机端 UdpPlanListenerService 前台 Service 监听广播 → 通知栏提示
                // 用户点击"同步电脑端截图"按钮后通过 OkHttp 下载图片并自动关联学员
                IosSectionWrapper(text = "跨端同步") {
                    IosGroupedListCard {
                        // 信息展示行：待同步截图信息（无则提示"暂无待同步"）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            IosIconBadge(
                                icon = Icons.Outlined.PhoneAndroid,
                                iconBgColor = LightPrimary,
                                contentDescription = "电脑端截图同步"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "电脑端训练计划截图",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = appOnSurface()
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    when {
                                        lanPlanSyncing -> "正在同步，请稍候…"
                                        pendingLanPlan != null -> {
                                            val p = pendingLanPlan!!
                                            "收到 ${p.studentName} 的训练计划（${p.date}）"
                                        }
                                        else -> "暂无待同步的截图"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        lanPlanSyncing || pendingLanPlan != null ->
                                            LightPrimary
                                        else -> appOnSurfaceVariant()
                                    }
                                )
                            }
                            // 待同步徽标
                            if (pendingLanPlan != null && !lanPlanSyncing) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = LightPrimary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "待同步",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LightPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        // 分隔线
                        Box(
                            modifier = Modifier
                                .padding(start = 60.dp)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(appDividerColor())
                        )
                        // 操作行：同步电脑端截图
                        SettingsActionRow(
                            icon = Icons.Outlined.SyncAlt,
                            iconBgColor = LightPrimary,
                            iconContentDescription = "同步电脑端截图",
                            title = "同步电脑端截图",
                            subtitle = when {
                                lanPlanSyncing -> "正在下载并关联学员…"
                                pendingLanPlan != null -> "点击下载并自动关联到对应学员"
                                else -> "电脑端发送截图后此处会显示提示"
                            },
                            showTopDivider = false,
                            onClick = {
                                // === v34：包裹 try-catch，避免同步过程中 NPE 弹黑色提示 ===
                                try {
                                    if (!lanPlanSyncing) {
                                        if (pendingLanPlan != null) {
                                            vm.syncLanPlanImage()
                                        } else {
                                            vm.updateStatus("暂无待同步的电脑端截图，请先在电脑端点击\"截图发送到手机\"")
                                        }
                                    }
                                } catch (e: Exception) {
                                    CrashDumper.dumpBoth(context, "SettingsScreen.syncLanPlan", e)
                                    Toast.makeText(
                                        context,
                                        "同步电脑端截图失败，请检查网络与电脑端服务",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                        // 同步进行中时显示加载动画
                        if (lanPlanSyncing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = LightPrimary
                                )
                                Text(
                                    "正在从局域网下载训练计划截图…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = appOnSurfaceVariant()
                                )
                            }
                        }
                    }
                }

                // === v32 优化2：训练动作积木库（自定义常用动作）===
                // 教练在排课弹窗点击胶囊按钮即可快速追加训练动作到内容文本框
                // 此处提供管理入口：查看现有积木 / 添加新动作 / 删除已存在动作
                // 数据持久化：WebDavCredentialsStore（普通 SharedPreferences，JSON 数组）
                IosSectionWrapper(text = "训练动作积木库") {
                    IosGroupedListCard {
                        val exerciseBlocks by vm.exerciseBlocks.collectAsState()
                    // === v34：包裹 try-catch，加载积木库失败也不弹 NPE 黑色提示 ===
                    LaunchedEffect(Unit) {
                        try {
                            vm.loadExerciseBlocks()
                        } catch (e: Exception) {
                            CrashDumper.dumpBoth(context, "SettingsScreen.LaunchedEffect.loadExerciseBlocks", e)
                        }
                    }

                        // 信息展示行：积木库说明与当前数量
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            IosIconBadge(
                                icon = Icons.Outlined.FitnessCenter,
                                iconBgColor = LightPrimary,
                                contentDescription = "训练动作积木库"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "常用训练动作",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = appOnSurface()
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "排课时点击胶囊即可快速追加，共 ${exerciseBlocks.size} 个动作",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = appOnSurfaceVariant()
                                )
                            }
                        }
                        // 分隔线
                        Box(
                            modifier = Modifier
                                .padding(start = 60.dp)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(appDividerColor())
                        )
                        // 积木库胶囊列表（FlowRow 自动换行）
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                exerciseBlocks.forEach { block ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(LightPrimary.copy(alpha = 0.08f))
                                            .padding(horizontal = Spacing.sm, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            block,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = LightPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "删除 $block",
                                            tint = LightPrimary.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { vm.removeExerciseBlock(block) }
                                        )
                                    }
                                }
                            }
                        }
                        // 分隔线
                        Box(
                            modifier = Modifier
                                .padding(start = 60.dp)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(appDividerColor())
                        )
                        // 添加新动作输入行
                        var newBlockInput by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            OutlinedTextField(
                                value = newBlockInput,
                                onValueChange = { newBlockInput = it },
                                label = { Text("新动作名称") },
                                placeholder = { Text("如：引体向上") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                leadingIcon = {
                                    Icon(Icons.Outlined.Add, contentDescription = null)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Button(
                                onClick = {
                                    if (newBlockInput.isNotBlank()) {
                                        vm.addExerciseBlock(newBlockInput)
                                        newBlockInput = ""
                                    }
                                },
                                enabled = newBlockInput.isNotBlank()
                            ) {
                                Text("添加")
                            }
                        }
                    }
                }

                // 分组 4：数据备份与恢复（整库二进制备份，纯本地，不依赖网络）
                // 备份内容：数据库文件（学员/课时包/排课/签到/训练周期等）+ 签到照片
                // 备份格式：ZIP 压缩包，可保存到手机存储或网盘
                IosSectionWrapper(text = "数据备份与恢复") {
                    IosGroupedListCard {
                        SettingsActionRow(
                            icon = Icons.Outlined.SaveAlt,
                            iconBgColor = LightPrimary,
                            iconContentDescription = "备份数据",
                            title = "一键备份所有数据",
                            subtitle = "将学员/课时/签到/照片打包备份到手机或网盘",
                            showTopDivider = false,
                            onClick = {
                                // 备份进行中时禁用，避免重复点击
                                if (!backupInProgress) {
                                    backupFileLauncher.launch(vm.generateBackupFileName())
                                }
                            }
                        )
                        SettingsActionRow(
                            icon = Icons.Outlined.Restore,
                            iconBgColor = LightPrimary,
                            iconContentDescription = "恢复数据",
                            title = "从备份文件恢复",
                            subtitle = "覆盖当前所有数据，恢复前请先备份",
                            showTopDivider = true,
                            onClick = {
                                // 恢复会覆盖当前数据，弹出二次确认对话框
                                if (!backupInProgress) {
                                    showRestoreConfirm = true
                                }
                            }
                        )
                        // 备份/恢复进行中时显示加载动画与具体进度文案
                        if (backupInProgress) {
                            val progressMsg = when (val p = backupProgress) {
                                is SettingsViewModel.BackupProgress.Working -> {
                                    if (p.total > 0) "${p.message}（${p.current}/${p.total}）"
                                    else p.message
                                }
                                else -> "正在处理，请稍候…"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    progressMsg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        // 分隔线
                        Box(
                            modifier = Modifier
                                .padding(start = 60.dp)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(appDividerColor())
                        )
                        // === v30 全自动无感备份开关 ===
                        // 监听核心数据变更 → 10 分钟防抖 → 静默备份到 filesDir/AutoBackups/
                        // 滚动保留最近 5 份，超出自动清理最旧文件
                        // 备份完全隐蔽：Dispatchers.IO 后台执行，不弹 Toast，不阻塞 UI
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            IosIconBadge(
                                icon = Icons.Outlined.Storage,
                                iconBgColor = LightSecondary,
                                contentDescription = "自动备份"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "自动备份",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = appOnSurface()
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (autoBackupEnabled)
                                        "数据变更后 10 分钟静默备份，保留最近 5 份"
                                    else
                                        "已关闭，仅手动备份生效",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (autoBackupEnabled)
                                        LightTertiary
                                    else
                                        appOnSurfaceVariant()
                                )
                            }
                            Switch(
                                checked = autoBackupEnabled,
                                onCheckedChange = { vm.setAutoBackupEnabled(it) }
                            )
                        }
                    }
                }

                // === v32 优化1：WebDAV 云盘备份（终极异地灾备）===
                // 教练可绑定自己的坚果云 / Nextcloud / 阿里云盘 WebDAV
                // 每次本地自动备份时，同时静默推送 Zip 到私人网盘指定目录
                // 账号密码通过 EncryptedSharedPreferences 加密存储，绝不明文落盘
                IosSectionWrapper(text = "WebDAV 云盘备份") {
                    IosGroupedListCard {
                        val webDavConfig by vm.webDavConfig.collectAsState()
                        val webDavTesting by vm.webDavTesting.collectAsState()

                        // 启用开关行
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            IosIconBadge(
                                icon = Icons.Outlined.Cloud,
                                iconBgColor = LightSecondary,
                                contentDescription = "WebDAV 云盘备份"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "启用 WebDAV 云盘推送",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = appOnSurface()
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (webDavConfig.enabled)
                                        "已启用：每次本地备份将同步推送到私人网盘"
                                    else
                                        "关闭：仅本地备份，不推送云端",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (webDavConfig.enabled)
                                        LightTertiary
                                    else
                                        appOnSurfaceVariant()
                                )
                            }
                            Switch(
                                checked = webDavConfig.enabled,
                                onCheckedChange = { newVal ->
                                    // 仅切换启用状态，不传账号密码避免覆盖加密存储中的真实凭证
                                    vm.setWebDavEnabled(newVal)
                                }
                            )
                        }

                        // 详细配置表单（启用或已配置过时显示）
                        if (webDavConfig.enabled || webDavConfig.baseUrl.isNotEmpty()) {
                            // 分隔线
                            Box(
                                modifier = Modifier
                                    .padding(start = 60.dp)
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(appDividerColor())
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                var baseUrl by remember(webDavConfig.baseUrl) {
                                    mutableStateOf(webDavConfig.baseUrl)
                                }
                                var remoteDir by remember(webDavConfig.remoteDir) {
                                    mutableStateOf(webDavConfig.remoteDir)
                                }
                                var username by remember(webDavConfig.username) {
                                    mutableStateOf(webDavConfig.username)
                                }
                                // 密码字段不回显（安全考虑），用户重新输入后才提交
                                var password by remember { mutableStateOf("") }
                                var passwordVisible by remember { mutableStateOf(false) }

                                OutlinedTextField(
                                    value = baseUrl,
                                    onValueChange = { baseUrl = it },
                                    label = { Text("WebDAV 服务器地址") },
                                    placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Cloud, contentDescription = null)
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                OutlinedTextField(
                                    value = remoteDir,
                                    onValueChange = { remoteDir = it },
                                    label = { Text("远程存放目录") },
                                    placeholder = { Text("shangmentiyu/backup") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = {
                                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("账号 / 应用专用密码") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Person, contentDescription = null)
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("密码（应用级专用密码）") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Lock, contentDescription = null)
                                    },
                                    visualTransformation = if (passwordVisible)
                                        VisualTransformation.None
                                    else
                                        PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            passwordVisible = !passwordVisible
                                        }) {
                                            Icon(
                                                if (passwordVisible)
                                                    Icons.Outlined.VisibilityOff
                                                else
                                                    Icons.Outlined.Visibility,
                                                contentDescription = if (passwordVisible)
                                                    "隐藏密码"
                                                else
                                                    "显示密码"
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    Button(
                                        onClick = {
                                            vm.testWebDavConnection(
                                                baseUrl, remoteDir, username, password
                                            )
                                        },
                                        enabled = !webDavTesting &&
                                            baseUrl.isNotBlank() &&
                                            username.isNotBlank(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (webDavTesting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Text("测试连接")
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            vm.saveWebDavConfig(
                                                enabled = true,
                                                baseUrl = baseUrl,
                                                remoteDir = remoteDir,
                                                username = username,
                                                password = password
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("保存配置")
                                    }
                                }

                                TextButton(
                                    onClick = { vm.clearWebDavConfig() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "清除配置",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                // 分组 5：存储空间（签到照片占用 + 清理一年前照片）
                IosSectionWrapper(text = "存储空间") {
                    IosGroupedListCard {
                        // 信息展示行：签到照片目录大小与数量
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            IosIconBadge(
                                icon = Icons.Outlined.Storage,
                                iconBgColor = LightSecondary,
                                contentDescription = "签到照片存储"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "签到照片占用",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = appOnSurface()
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${vm.formatBytes(signPhotosSize)} · 共 ${signPhotosCount} 张照片",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = appOnSurfaceVariant()
                                )
                            }
                            // 一年前可清理数量徽标（无则不显示）
                            if (cleanableCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = LightPrimary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "${cleanableCount} 张可清理",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LightPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        // 分隔线
                        Box(
                            modifier = Modifier
                                .padding(start = 60.dp)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(appDividerColor())
                        )
                        // 操作行：清理一年前签到照片（带二次确认）
                        SettingsActionRow(
                            icon = Icons.Outlined.DeleteSweep,
                            iconBgColor = LightPrimary,
                            iconContentDescription = "清理照片",
                            title = "清理一年前签到照片",
                            subtitle = if (cleanableCount > 0)
                                "可清理 ${cleanableCount} 张一年前的签到照片，释放空间"
                            else
                                "暂无可清理的旧照片",
                            showTopDivider = false,
                            onClick = {
                                if (cleanableCount > 0) {
                                    showCleanPhotosConfirm = true
                                } else {
                                    vm.updateStatus("暂无可清理的旧照片")
                                }
                            }
                        )
                    }
                }

                // === 分组 5.5：缓存管理（v29 优化3） ===
                // 集成"孤立照片"与"临时缓存"清理，一键释放手机存储空间
                // 孤立照片：filesDir/SignPhotos/ 中未被 lessons 表索引且超过 6 个月的文件
                // 临时缓存：app.cacheDir 下的临时文件（系统/SDK 会自动重建，可放心清理）
                IosSectionWrapper(text = "缓存管理") {
                    IosGroupedListCard {
                        // 信息展示行：孤立照片占用情况
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            IosIconBadge(
                                icon = Icons.Outlined.ImageNotSupported,
                                iconBgColor = LightPrimary,
                                contentDescription = "孤立照片"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "孤立照片",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = appOnSurface()
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (orphanPhotoCount > 0)
                                        "${orphanPhotoCount} 个 · 可释放 ${vm.formatBytes(orphanPhotoSize)}"
                                    else
                                        "暂无孤立照片",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = appOnSurfaceVariant()
                                )
                            }
                            if (orphanPhotoCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = LightPrimary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "可清理",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LightPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        // 分隔线
                        Box(
                            modifier = Modifier
                                .padding(start = 60.dp)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(appDividerColor())
                        )
                        // 操作行：清理孤立照片（带二次确认）
                        SettingsActionRow(
                            icon = Icons.Outlined.DeleteSweep,
                            iconBgColor = LightPrimary,
                            iconContentDescription = "清理孤立照片",
                            title = "清理孤立照片",
                            subtitle = if (orphanPhotoCount > 0)
                                "清理未被任何课时引用且超过 6 个月的照片"
                            else
                                "暂无孤立照片可清理",
                            showTopDivider = false,
                            onClick = {
                                if (orphanPhotoCount > 0) {
                                    showCleanOrphanPhotosConfirm = true
                                } else {
                                    vm.updateStatus("暂无孤立照片可清理")
                                }
                            }
                        )

                        // 分隔线（缓存占用信息与孤立照片之间）
                        Box(
                            modifier = Modifier
                                .padding(start = 60.dp)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(appDividerColor())
                        )
                        // 信息展示行：缓存目录占用情况
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            IosIconBadge(
                                icon = Icons.Outlined.Storage,
                                iconBgColor = LightSecondary,
                                contentDescription = "应用缓存"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "应用缓存",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = appOnSurface()
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (cacheFileCount > 0)
                                        "${vm.formatBytes(cacheSize)} · 共 ${cacheFileCount} 个临时文件"
                                    else
                                        "缓存目录为空",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = appOnSurfaceVariant()
                                )
                            }
                            if (cacheFileCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = LightSecondary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "可清理",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LightSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        // 分隔线
                        Box(
                            modifier = Modifier
                                .padding(start = 60.dp)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(appDividerColor())
                        )
                        // 操作行：清理应用缓存（带二次确认）
                        SettingsActionRow(
                            icon = Icons.Outlined.CleaningServices,
                            iconBgColor = LightSecondary,
                            iconContentDescription = "清理缓存",
                            title = "清理应用缓存",
                            subtitle = if (cacheFileCount > 0)
                                "清理临时文件，不影响学员数据与照片"
                            else
                                "缓存目录为空",
                            showTopDivider = false,
                            onClick = {
                                if (cacheFileCount > 0) {
                                    showCleanCacheConfirm = true
                                } else {
                                    vm.updateStatus("缓存目录为空")
                                }
                            }
                        )
                    }
                }

                // 分组 6：应用更新（检查新版本 + 安装已下载的更新）
                IosSectionWrapper(text = "应用更新") {
                    IosGroupedListCard {
                        SettingsActionRow(
                            icon = Icons.Outlined.Refresh,
                            iconBgColor = LightPrimary,
                            iconContentDescription = "检查更新",
                            title = "检查更新",
                            subtitle = "当前版本 v${BuildConfig.VERSION_NAME} · 点击立即检查",
                            showTopDivider = false,
                            onClick = {
                                // === v33 功能 3：手动检查时立即弹 Toast ===
                                // 让教练在户外点击后立即得到反馈，避免误以为按钮无响应
                                Toast.makeText(context, "正在检查更新...", Toast.LENGTH_SHORT).show()
                                // 同步检查：直接调用 UpdateChecker，立即显示结果，
                                // 失败时携带 HTTP 状态码等错误信息，便于诊断
                                // （如私有仓库 404、网络异常等）
                                vm.updateStatus("正在检查更新…")
                                // === v34：包裹 try-catch，避免 checkScope.launch 内异常
                                // 被 appExceptionHandler 捕获后弹"操作遇到异常 (NullPointerException)" ===
                                checkScope.launch {
                                    try {
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
                                    } catch (e: Exception) {
                                        CrashDumper.dumpBoth(
                                            context,
                                            "SettingsScreen.checkUpdate",
                                            e,
                                            extraContext = "versionName=${BuildConfig.VERSION_NAME}"
                                        )
                                        vm.updateStatus("检查更新失败：${e.message ?: e.javaClass.simpleName}")
                                    }
                                }
                            }
                        )
                        SettingsActionRow(
                            icon = Icons.Outlined.Download,
                            iconBgColor = LightPrimary,
                            iconContentDescription = "安装已下载的更新",
                            title = "安装已下载的更新",
                            subtitle = "若已下载新版本 APK，点击此处直接安装",
                            showTopDivider = true,
                            onClick = {
                                // === v34：包裹 try-catch，避免 installApk 异常导致弹 NPE 黑色提示 ===
                                try {
                                    if (UpdateManager.hasDownloadedApk(context)) {
                                        UpdateManager.installUpdate(context)
                                    } else {
                                        vm.updateStatus("暂无已下载的更新文件，请先检查更新")
                                    }
                                } catch (e: Exception) {
                                    CrashDumper.dumpBoth(context, "SettingsScreen.installUpdate", e)
                                    Toast.makeText(
                                        context,
                                        "安装失败：APK 文件可能已损坏，请重新下载",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    }
                }

                // 分组 7：关于
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
                                icon = Icons.Outlined.Apps,
                                iconBgColor = LightPrimary,
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
                                .background(appDividerColor())
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            IosIconBadge(
                                icon = Icons.Outlined.Info,
                                iconBgColor = LightPrimary,
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
                        TextButton(onClick = { showCoachDialog = false }) { Text("取消") }
                    }
                )
            }

            // 恢复前二次确认对话框：恢复会覆盖当前所有学员/课时/签到数据
            // 必须让用户明确知晓风险，避免误操作导致数据丢失
            if (showRestoreConfirm) {
                GlassAlertDialog(
                    onDismissRequest = { showRestoreConfirm = false },
                    title = "确认恢复数据？",
                    content = {
                        Column {
                            Text(
                                "恢复操作将覆盖当前所有学员、课时包、排课、签到记录与照片。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "建议：恢复前请先点击\"一键备份所有数据\"创建当前数据的备份，以防万一。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "恢复成功后应用将自动重启以加载新数据。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            showRestoreConfirm = false
                            // 用户确认后弹出文件选择器
                            // 使用 arrayOf("*/*") 让用户可选择任意位置（网盘/本地）的备份文件
                            restoreFileLauncher.launch(arrayOf("*/*"))
                        }) { Text("我已知晓，选择备份文件") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") }
                    }
                )
            }

            // 恢复成功后重启确认对话框：让用户主动确认重启，避免突然杀进程导致用户困惑
            if (needRestart) {
                GlassAlertDialog(
                    onDismissRequest = {
                        // 不允许点外部关闭：必须用户主动确认重启，否则数据已恢复但 App 仍持有旧 ViewModel
                    },
                    title = "恢复成功",
                    content = {
                        Text(
                            "数据已成功恢复，需要重启应用以加载新数据。点击\"立即重启\"将关闭并重新打开应用。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            vm.consumeNeedRestart()
                            vm.restartApp()
                        }) { Text("立即重启") }
                    },
                    dismissButton = {
                        // 不提供取消按钮：数据已覆盖，旧 ViewModel 已失效，必须重启
                    }
                )
            }

            // 清理一年前签到照片二次确认对话框：删除不可恢复，需用户明确确认
            if (showCleanPhotosConfirm) {
                GlassAlertDialog(
                    onDismissRequest = { showCleanPhotosConfirm = false },
                    title = "确认清理一年前签到照片？",
                    content = {
                        Column {
                            Text(
                                "将永久删除 ${cleanableCount} 张一年前的签到照片，此操作不可撤销。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "建议：清理前请先点击\"一键备份所有数据\"创建当前数据的备份，以防万一。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "一年内的签到照片不受影响。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            showCleanPhotosConfirm = false
                            vm.cleanOldSignPhotos()
                        }) { Text("确认清理") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCleanPhotosConfirm = false }) { Text("取消") }
                    }
                )
            }

            // === v29 优化3：清理孤立照片二次确认对话框 ===
            // 删除未被任何课时引用且超过 6 个月的照片，不可恢复，需用户明确确认
            if (showCleanOrphanPhotosConfirm) {
                GlassAlertDialog(
                    onDismissRequest = { showCleanOrphanPhotosConfirm = false },
                    title = "确认清理孤立照片？",
                    content = {
                        Column {
                            Text(
                                "将永久删除 ${orphanPhotoCount} 个孤立照片（未被任何课时引用且超过 6 个月），释放 ${vm.formatBytes(orphanPhotoSize)}。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "此操作不可撤销。被课时记录引用的照片不会受影响。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "建议：清理前请先点击\"一键备份所有数据\"创建当前数据的备份，以防万一。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            showCleanOrphanPhotosConfirm = false
                            vm.cleanOrphanPhotos()
                        }) { Text("确认清理") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCleanOrphanPhotosConfirm = false }) { Text("取消") }
                    }
                )
            }

            // === v29 优化3：清理应用缓存二次确认对话框 ===
            // 删除 app.cacheDir 下的临时文件，系统/SDK 会自动重建，不影响业务数据
            if (showCleanCacheConfirm) {
                GlassAlertDialog(
                    onDismissRequest = { showCleanCacheConfirm = false },
                    title = "确认清理应用缓存？",
                    content = {
                        Column {
                            Text(
                                "将清理 ${cacheFileCount} 个临时缓存文件，释放 ${vm.formatBytes(cacheSize)}。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "缓存清理后系统会按需自动重建，不影响学员数据、签到照片与排课记录。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "注意：若上次\"从备份恢复\"失败，缓存中的安全备份文件也会被清理，将无法手动回滚。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            showCleanCacheConfirm = false
                            vm.cleanCacheFiles()
                        }) { Text("确认清理") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCleanCacheConfirm = false }) { Text("取消") }
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
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
            )
            .background(Color.White, RoundedCornerShape(10.dp))
    ) {
        // 顶部 4dp 渐变装饰条（活力蓝紫）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(BrandGradientStart, BrandGradientEnd)
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
                    .background(appDividerColor())
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
                    color = appOnSurface()
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant()
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = appOnSurfaceVariant(),
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
 * 统计项分隔线：0.5dp 宽 + 40dp 高。
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
