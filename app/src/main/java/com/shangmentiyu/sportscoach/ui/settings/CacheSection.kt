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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ImageNotSupported
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
import com.shangmentiyu.sportscoach.ui.settings.components.BadgePill
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
 * 缓存管理分组（孤立照片 + 临时缓存清理），三个确认对话框均在本区块内部。
 */
@Composable
internal fun CacheSection(vm: SettingsViewModel) {
    val orphanPhotoCount by vm.orphanPhotoCount.collectAsStateWithLifecycle()
    val orphanPhotoSize by vm.orphanPhotoSize.collectAsStateWithLifecycle()
    val cacheSize by vm.cacheSize.collectAsStateWithLifecycle()
    val cacheFileCount by vm.cacheFileCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // === v34：包裹 try-catch，扫描失败也不弹 NPE 黑色提示 ===
    LaunchedEffect(Unit) {
        try {
            vm.scanOrphanPhotos()
            vm.scanCacheSize()
        } catch (e: Exception) {
            CrashDumper.dumpBoth(context, "SettingsScreen.LaunchedEffect.scanOrphanCache", e)
        }
    }
    var showCleanOrphanPhotosConfirm by remember { mutableStateOf(false) }
    var showCleanCacheConfirm by remember { mutableStateOf(false) }

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
                    BadgePill(text = "可清理", color = LightPrimary)
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
                    BadgePill(text = "可清理", color = LightSecondary)
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

    // === v29 优化3：清理孤立照片二次确认对话框 ===
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
