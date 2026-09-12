package com.shangmentiyu.sportscoach.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.data.internal.MiniprogramExporter
import com.shangmentiyu.sportscoach.ui.settings.components.IosGroupedListCard
import com.shangmentiyu.sportscoach.ui.settings.components.IosIconBadge
import com.shangmentiyu.sportscoach.ui.settings.components.IosSectionWrapper
import com.shangmentiyu.sportscoach.ui.settings.components.SettingsActionRow
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightSecondary
import com.shangmentiyu.sportscoach.ui.theme.LightTertiary
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary

/**
 * 数据备份与恢复分组：固定文件夹一键备份 + 自动恢复最新备份 + 自动备份开关 + 悬浮窗开关。
 *
 * v1.0.2+ 联动：固定文件夹（SAF 选一次持久化）→ 手动备份直存、恢复自动取最新、
 * 自动备份同步写入同一文件夹（卸载应用备份不丢）。
 * 恢复二次确认与恢复成功重启确认框均在本区块内部。
 */
@Composable
internal fun DataManageSection(
    vm: SettingsViewModel,
    onRequestBackup: () -> Unit,
    onRequestRestore: () -> Unit
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val backupInProgress = uiState.backupInProgress
    val backupProgress = uiState.backupProgress
    val autoBackupEnabled = uiState.autoBackupEnabled
    val floatingWindowEnabled = uiState.floatingWindowEnabled
    val needRestart = uiState.needRestart
    val backupFolderLabel by vm.backupFolderLabel.collectAsStateWithLifecycle()
    val latestBackup by vm.latestBackup.collectAsStateWithLifecycle()
    val folderSet = backupFolderLabel.isNotBlank()
    var showRestoreConfirm by remember { mutableStateOf(false) }
    // v1.0.3 备份加密口令
    val backupPassphrase by vm.backupPassphrase.collectAsStateWithLifecycle()
    var showPassphraseDialog by remember { mutableStateOf(false) }

    // 阶段五互通：选择小程序备份 JSON 导入（文件由微信"用其他应用打开"/文件管理器提供）
    val mpImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importMiniprogramData(uri)
    }

    // 阶段五遗留 #1：导出小程序数据（先选目标模式，再选保存位置）
    var pendingMpExportMode by remember { mutableStateOf<String?>(null) }
    var showMpExportModeDialog by remember { mutableStateOf(false) }
    val mpExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val mode = pendingMpExportMode
        if (uri != null && mode != null) vm.exportMiniprogramData(mode, uri)
        pendingMpExportMode = null
    }

    // 最新备份的展示时间（文件夹内最新一份；无备份时提示首次备份）
    val latestText = latestBackup?.let { (name, ms) ->
        val time = java.time.Instant.ofEpochMilli(ms)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        "$name（$time）"
    }

    IosSectionWrapper(text = "数据备份与恢复") {
        IosGroupedListCard {
            SettingsActionRow(
                icon = Icons.Outlined.SaveAlt,
                iconBgColor = LightPrimary,
                iconContentDescription = "备份数据",
                title = "一键备份所有数据",
                subtitle = if (folderSet)
                    "备份到「$backupFolderLabel」，固定文件夹卸载不丢"
                else
                    "首次使用：选择固定备份文件夹（建议「下载/Download」）",
                showTopDivider = false,
                onClick = {
                    // 备份进行中时禁用，避免重复点击
                    if (!backupInProgress) {
                        if (folderSet) vm.backupToDefaultFolder() else onRequestBackup()
                    }
                }
            )
            SettingsActionRow(
                icon = Icons.Outlined.Restore,
                iconBgColor = LightPrimary,
                iconContentDescription = "恢复数据",
                title = "恢复最新备份",
                subtitle = if (folderSet)
                    latestText?.let { "自动选取最新：$it" } ?: "文件夹里还没有备份，先备份一次"
                else
                    "未设置备份文件夹，点击手动选择备份文件",
                showTopDivider = true,
                onClick = {
                    // 恢复会覆盖当前数据，弹出二次确认对话框
                    if (!backupInProgress) {
                        if (folderSet && latestText != null) showRestoreConfirm = true
                        else onRequestRestore()
                    }
                }
            )
            // 阶段五互通：导入微信小程序本地数据（backup_{mode}_{日期}.json）
            SettingsActionRow(
                icon = Icons.Outlined.Smartphone,
                iconBgColor = LightSecondary,
                iconContentDescription = "导入小程序数据",
                title = "导入小程序数据",
                subtitle = "选择小程序导出的备份 JSON，学员与课时包按姓名去重合并",
                showTopDivider = true,
                onClick = {
                    if (!backupInProgress) {
                        mpImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                }
            )
            // 阶段五遗留 #1：导出小程序数据（先选目标模式，微信发送即可互通）
            SettingsActionRow(
                icon = Icons.Outlined.Smartphone,
                iconBgColor = LightSecondary,
                iconContentDescription = "导出小程序数据",
                title = "导出小程序数据",
                subtitle = "生成小程序可导入的 JSON，微信发送给电脑或其他设备",
                showTopDivider = true,
                onClick = { if (!backupInProgress) showMpExportModeDialog = true }
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
            // === v1.0.3 备份加密口令 ===
            // 设置后备份 ZIP 内的数据库与元数据走 AES-256-GCM 加密，
            // 备份文件即使被导出（微信/网盘/U盘）也无法直接读出学员隐私。
            SettingsActionRow(
                icon = Icons.Outlined.Lock,
                iconBgColor = LightSecondary,
                iconContentDescription = "备份加密口令",
                title = "备份加密口令",
                subtitle = if (backupPassphrase.isNotBlank())
                    "已启用，备份将加密存储（恢复时需输入同一口令）"
                else
                    "未设置，备份为明文；建议设置以避免学员隐私泄露",
                showTopDivider = true,
                onClick = { showPassphraseDialog = true }
            )
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
                            if (folderSet)
                                "数据变更后 10 分钟静默备份到「$backupFolderLabel」，保留最近 5 份"
                            else
                                "数据变更后 10 分钟静默备份，保留最近 5 份（设置备份文件夹后卸载不丢）"
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
            // 分隔线
            Box(
                modifier = Modifier
                    .padding(start = 60.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(appDividerColor())
            )
            // === v34 悬浮窗开关（默认关闭） ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                IosIconBadge(
                    icon = Icons.Outlined.Apps,
                    iconBgColor = LightSecondary,
                    contentDescription = "悬浮窗"
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "悬浮窗",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = appOnSurface()
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (floatingWindowEnabled)
                            "已开启，桌面显示话术快捷复制"
                        else
                            "已关闭",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (floatingWindowEnabled)
                            LightTertiary
                        else
                            appOnSurfaceVariant()
                    )
                }
                Switch(
                    checked = floatingWindowEnabled,
                    onCheckedChange = { vm.setFloatingWindowEnabled(it) }
                )
            }
        }
    }

    // 恢复前二次确认对话框：恢复会覆盖当前所有学员/课时/签到数据
    // v1.0.3 备份加密口令设置对话框
    // v1.0.4：补二次确认输入 + 显隐切换。口令打错一个字就会把备份永久锁死，
    //         单输入框无法自查——必须两次输入一致才允许保存。
    if (showPassphraseDialog) {
        var draft by remember(backupPassphrase) { mutableStateOf(backupPassphrase) }
        var confirmDraft by remember(backupPassphrase) { mutableStateOf(backupPassphrase) }
        var showPlain by remember { mutableStateOf(false) }
        // 仅当"修改了口令"时才校验一致性；未改动（如只是查看）直接放行
        val changed = draft != backupPassphrase
        val mismatch = changed && draft.isNotBlank() && draft != confirmDraft
        val tooShort = draft.isNotBlank() && draft.length < 8
        val canSave = !mismatch && !tooShort &&
            (!changed || draft.isBlank() || draft == confirmDraft)

        GlassAlertDialog(
            onDismissRequest = { showPassphraseDialog = false },
            title = "备份加密口令",
            content = {
                Column {
                    Text(
                        "设置后，备份文件中的数据库与学员数据将加密存储。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        visualTransformation = if (showPlain) androidx.compose.ui.text.input.VisualTransformation.None
                            else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showPlain = !showPlain }) {
                                Text(if (showPlain) "隐藏" else "显示",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        label = { Text("加密口令（至少 8 位）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (changed && draft.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.sm))
                        OutlinedTextField(
                            value = confirmDraft,
                            onValueChange = { confirmDraft = it },
                            singleLine = true,
                            isError = mismatch,
                            visualTransformation = if (showPlain) androidx.compose.ui.text.input.VisualTransformation.None
                                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            label = { Text("再次输入确认") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "⚠️ 口令不会上传到任何服务器。请务必自行记牢——" +
                            "口令遗失后，已加密的备份将无法恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "数据库已加密（密钥由本机安全芯片保管，无法导出）。" +
                            "换手机时，本地数据不会自动跟随，请先用备份功能导出" +
                            "并牢记口令，在新设备上恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (tooShort) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "口令至少 8 位",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (mismatch) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "两次输入的口令不一致",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = canSave,
                    onClick = {
                        vm.setBackupPassphrase(draft)
                        showPassphraseDialog = false
                    }
                ) { Text(if (draft.isBlank()) "清除口令" else "保存") }
            },
            dismissButton = {
                TextButton(onClick = { showPassphraseDialog = false }) { Text("取消") }
            }
        )
    }

    if (showRestoreConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = "确认恢复数据？",
            content = {
                Column {
                    Text(
                        "将从「$backupFolderLabel」恢复最新备份：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        latestText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = appPrimary()
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "恢复操作将覆盖当前所有学员、课时包、排课、签到记录与照片。恢复成功后应用将自动重启。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showRestoreConfirm = false
                    vm.restoreLatestFromFolder()
                }) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") }
            }
        )
    }

    // 恢复成功后重启确认对话框：数据已恢复但旧 ViewModel 失效，必须重启
    if (needRestart) {
        GlassAlertDialog(
            onDismissRequest = {
                // 不允许点外部关闭：必须用户主动确认重启，否则数据已恢复但 App 仍持有旧 ViewModel
            },
            // v46 修复：禁止系统返回键关闭，恢复后必须重启才能继续使用
            dismissOnBackPress = false,
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

    // 阶段五遗留 #1：导出小程序数据——先选目标模式（小程序与 App 模式 id 不同源）
    if (showMpExportModeDialog) {
        val defaultMode = MiniprogramExporter.miniprogramModeId(
            com.shangmentiyu.sportscoach.data.internal.ModeManager.activeMode
        ) ?: "shangmen"
        var selected by remember { mutableStateOf(defaultMode) }
        GlassAlertDialog(
            onDismissRequest = { showMpExportModeDialog = false },
            title = "导出到哪个小程序模式？",
            content = {
                Column {
                    Text(
                        "小程序分「上门体育 / 俱乐部」两个独立数据空间，导出文件只能导入对应模式。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    MiniprogramExporter.allMiniprogramModes().forEach { mode ->
                        val label = if (mode == "shangmen") "上门体育（shangmen）" else "俱乐部（club）"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == mode,
                                onClick = { selected = mode }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showMpExportModeDialog = false
                    pendingMpExportMode = selected
                    val date = java.time.LocalDate.now().toString()
                    mpExportLauncher.launch("backup_${selected}_$date.json")
                }) { Text("选择保存位置") }
            },
            dismissButton = {
                TextButton(onClick = { showMpExportModeDialog = false }) { Text("取消") }
            }
        )
    }
}
