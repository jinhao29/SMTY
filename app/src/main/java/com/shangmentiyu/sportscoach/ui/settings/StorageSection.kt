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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.util.CrashDumper

/**
 * 存储空间分组（签到照片占用 + 清理一年前照片），确认框在本区块内部。
 */
@Composable
internal fun StorageSection(vm: SettingsViewModel) {
    val signPhotosSize by vm.signPhotosSize.collectAsStateWithLifecycle()
    val signPhotosCount by vm.signPhotosCount.collectAsStateWithLifecycle()
    val cleanableCount by vm.cleanableCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // === v34：包裹 try-catch，扫描失败也不弹 NPE 黑色提示 ===
    LaunchedEffect(Unit) {
        try {
            vm.scanSignPhotos()
        } catch (e: Exception) {
            CrashDumper.dumpBoth(context, "SettingsScreen.LaunchedEffect.scanSignPhotos", e)
        }
    }
    var showCleanPhotosConfirm by remember { mutableStateOf(false) }

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
}
