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
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/**
 * 数据备份与恢复分组：整包二进制备份 + 自动备份开关 + 悬浮窗开关。
 *
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
    var showRestoreConfirm by remember { mutableStateOf(false) }

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
                        onRequestBackup()
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
                    onRequestRestore()
                }) { Text("我已知晓，选择备份文件") }
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
}
