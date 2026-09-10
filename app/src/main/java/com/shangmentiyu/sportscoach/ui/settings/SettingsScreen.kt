package com.shangmentiyu.sportscoach.ui.settings

import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.excel.ImportStrategy
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.util.CrashDumper
import org.koin.androidx.compose.koinViewModel

/**
 * 设置页：iOS Settings 风格。
 *
 * 设计要点（对标 iOS HIG）：
 * - 34pt Large Title "设置"，左对齐
 * - 教练设置：iOS 表单分组（纯白卡片 + 无外框输入框）
 * - 统计信息：纯白卡片 + 三栏数据（今日/总记录/教练）
 * - 数据同步：iOS Settings 列表项（彩色方形图标 + 标题 + 右箭头）
 * - 关于：纯文字信息卡片
 *
 * 结构：本文件仅保留状态管理（SAF 文件选择器）+ 导航 + 组装，
 * 各功能区块拆分为独立 @Composable：
 * - [ProfileSection]：外观 / 教练设置 / 统计信息
 * - [ToolsSection]：工具 / 教练管理 / 家长沟通（紧跟统计信息）
 * - [LessonManageSection]：数据同步（导出/导入/修正排课/清理课表）
 * - [DataManageSection]：备份恢复 / 存储空间
 * - [AboutSection]：更新 / 关于
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigate: (String) -> Unit = {}) {
    val context = LocalContext.current
    // === v46 架构层四：Koin 注入（由 AppModule 提供 SettingsViewModel）===
    val vm: SettingsViewModel = koinViewModel()

    // === 文件选择器（SAF 目录选择，直接传递 Uri 给 ViewModel）===
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

    // === 导入策略：由 SyncSection 选中后回调，launcher 回调取出使用 ===
    var pendingImportStrategy by remember { mutableStateOf<ImportStrategy?>(null) }
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

    // 备份：改为固定文件夹模式（v1.0.2+）。首次点备份时弹 SAF 目录选择器选一次，
    // 持久化授权后存 DataStore；此后备份直存该文件夹、恢复自动取最新、自动备份同步写入。
    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        try {
            if (uri == null) return@rememberLauncherForActivityResult
            vm.onBackupFolderPicked(uri)
        } catch (e: Exception) {
            CrashDumper.dumpBoth(context, "SettingsScreen.backupFolderLauncher", e)
            Toast.makeText(
                context,
                "设置备份文件夹失败，请重试",
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
        containerColor = appBackground(),
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // 【首要元素】"设置"大标题：紧贴状态栏。
                // 美化（v51）：displayMedium(45sp)+Bold 视觉过于厚重突兀，
                // 改 headlineLarge(32sp)+SemiBold 更柔和，跟 Material 3 Large Title 节奏一致。
                Text(
                    text = "设置",
                    modifier = Modifier.padding(horizontal = Spacing.screenH),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = appOnSurface()
                )
                Spacer(Modifier.height(4.dp))
                // 副标题：改用 bodyMedium 主题 typography（替代裸 fontSize=14.sp），
                // letterSpacing 微调让"·"分隔的节奏更舒展。
                Text(
                    text = "教练信息 · 外观 · 数据同步 · 关于",
                    modifier = Modifier.padding(horizontal = Spacing.screenH),
                    style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.3.sp),
                    color = appOnSurfaceVariant()
                )

                // v23.12 多租户：工作模式切换（上门体育 / 俱乐部，独立数据库）
                ModeSection()

                ProfileSection(vm)

                // 工具 / 教练管理 / 家长沟通（紧随"统计信息"下方）
                ToolsSection(onNavigate)

                // 分组 3：数据同步（iOS Settings 风格列表项）
                LessonManageSection(
                    vm = vm,
                    onExportToday = { exportDirLauncher.launch(null) },
                    onExportArchive = { exportArchiveLauncher.launch(null) },
                    onPickImport = { strategy ->
                        pendingImportStrategy = strategy
                        importDirLauncher.launch(null)
                    }
                )

                // 分组 4：数据备份与恢复（固定文件夹：手动/自动/恢复联动，自动取最新）
                DataManageSection(
                    vm = vm,
                    onRequestBackup = { backupFolderLauncher.launch(null) },
                    onRequestRestore = { restoreFileLauncher.launch(arrayOf("*/*")) }
                )

                // 分组 4.5：桌面同步（双端互通：推送备份到 PC + 拉取 PC 学员数据）
                DesktopSyncSection(vm)

                StorageSection(vm)
                CacheSection(vm)

                // 分组 5：应用更新 + 关于（工具/教练管理/家长沟通 已迁到 ToolsSection）
                AboutSection(vm, onNavigate)

                // === 底部避让：140dp 防止悬浮导航胶囊遮挡最后一项 ===
                Spacer(Modifier.height(140.dp))
            }

            // 状态消息 Snackbar（独立组件，订阅只重组自身）
            StatusBanner(vm)
        }
    }
}

/**
 * 状态消息 Snackbar：独立订阅 uiState.statusMessage，
 * 消息出现/消失不会重扫设置页主体区块。
 */
@Composable
private fun BoxScope.StatusBanner(vm: SettingsViewModel) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    uiState.statusMessage?.let { msg ->
        Snackbar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 160.dp),
            action = {
                TextButton(onClick = { vm.clearStatus() }) { Text("关闭") }
            }
        ) {
            Text(msg)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(onNavigate = {})
}
