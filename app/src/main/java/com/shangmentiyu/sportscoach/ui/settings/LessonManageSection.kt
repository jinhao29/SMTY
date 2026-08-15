package com.shangmentiyu.sportscoach.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.excel.ImportStrategy
import com.shangmentiyu.sportscoach.ui.settings.components.ImportStrategyRow
import com.shangmentiyu.sportscoach.ui.settings.components.IosGroupedListCard
import com.shangmentiyu.sportscoach.ui.settings.components.IosSectionWrapper
import com.shangmentiyu.sportscoach.ui.settings.components.SettingsActionRow
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightSecondary
import com.shangmentiyu.sportscoach.ui.theme.Spacing

/**
 * 课时与数据同步分组：导出/导入学员、修正历史排课、清理无效课表。
 *
 * 导入策略选择对话框、修正排课确认框与清理确认框的状态均在本区块内部。
 */
@Composable
internal fun LessonManageSection(
    vm: SettingsViewModel,
    onExportToday: () -> Unit,
    onExportArchive: () -> Unit,
    onPickImport: (ImportStrategy) -> Unit
) {
    var showImportStrategyDialog by remember { mutableStateOf(false) }
    var showFixScheduleConfirm by remember { mutableStateOf(false) }
    var showCleanupConfirm by remember { mutableStateOf(false) }
    // === v32：清理无效课表结果（null=无，非负=清理数量） ===
    val cleanupResult by vm.cleanupResult.collectAsStateWithLifecycle()

    IosSectionWrapper(text = "数据同步") {
        IosGroupedListCard {
            SettingsActionRow(
                icon = Icons.Outlined.Download,
                iconBgColor = LightPrimary,
                iconContentDescription = "导出今日记录",
                title = "导出今日课堂记录",
                subtitle = "将今日签到记录导出到指定目录",
                showTopDivider = false,
                onClick = onExportToday
            )
            SettingsActionRow(
                icon = Icons.Outlined.CloudUpload,
                iconBgColor = LightPrimary,
                iconContentDescription = "导出成绩档案",
                title = "导出成绩到档案",
                subtitle = "同步成绩到桌面端 Excel 档案",
                showTopDivider = true,
                onClick = onExportArchive
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
            // === v45：一键修正历史错误排课（清理 + 重排）===
            SettingsActionRow(
                icon = Icons.Outlined.CleaningServices,
                iconBgColor = LightSecondary,
                iconContentDescription = "修正历史排课",
                title = "修正历史排课数据",
                subtitle = "清理早于购买日/超额的错误排课并重新生成",
                showTopDivider = true,
                onClick = { showFixScheduleConfirm = true }
            )
            // === v32：清理无效课表（仅删除过期且未签到的占位课时） ===
            SettingsActionRow(
                icon = Icons.Outlined.DeleteSweep,
                iconBgColor = LightSecondary,
                iconContentDescription = "清理无效课表",
                title = "清理无效课表",
                subtitle = "删除已过期且从未签到的占位课时",
                showTopDivider = true,
                onClick = { showCleanupConfirm = true }
            )
        }
    }

    // 导入策略选择对话框（先选策略再启动目录选择器）
    if (showImportStrategyDialog) {
        GlassAlertDialog(
            onDismissRequest = { showImportStrategyDialog = false },
            title = "选择导入策略",
            confirmButton = {
                TextButton(onClick = { showImportStrategyDialog = false }) { Text("取消") }
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ImportStrategyRow(
                        title = "追加导入",
                        desc = "保留现有数据，档案学员作为新学员追加",
                        strategy = ImportStrategy.APPEND,
                        onClick = {
                            onPickImport(ImportStrategy.APPEND)
                            showImportStrategyDialog = false
                        }
                    )
                    ImportStrategyRow(
                        title = "覆盖导入",
                        desc = "清空现有学员，以档案为准重建",
                        strategy = ImportStrategy.OVERWRITE,
                        onClick = {
                            onPickImport(ImportStrategy.OVERWRITE)
                            showImportStrategyDialog = false
                        }
                    )
                    ImportStrategyRow(
                        title = "仅更新学员",
                        desc = "只更新姓名匹配学员的资料，不新增不删除",
                        strategy = ImportStrategy.UPDATE_PART,
                        onClick = {
                            onPickImport(ImportStrategy.UPDATE_PART)
                            showImportStrategyDialog = false
                        }
                    )
                }
            }
        )
    }

    // === v45：修正历史排课前二次确认对话框 ===
    if (showFixScheduleConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showFixScheduleConfirm = false },
            title = "修正历史排课数据",
            content = {
                Column {
                    Text(
                        "将会清理所有未来未签退的错误占位排课，并根据当前课时包重新生成正确的排课计划，确定继续吗？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "安全提示：仅清理未签退的占位排课，已签退的历史课时数据会完整保留。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showFixScheduleConfirm = false
                    vm.fixHistoricalScheduleErrors()
                }) { Text("确定修正") }
            },
            dismissButton = {
                TextButton(onClick = { showFixScheduleConfirm = false }) { Text("取消") }
            }
        )
    }

    // === v32：清理无效课表确认框 ===
    if (showCleanupConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showCleanupConfirm = false },
            title = "清理无效课表",
            content = {
                Text(
                    "将删除所有已过期（早于今天）且从未签到的占位课时，确定继续吗？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(onClick = {
                    showCleanupConfirm = false
                    vm.cleanupInvalidLessons()
                }) { Text("确定清理") }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupConfirm = false }) { Text("取消") }
            }
        )
    }

    // === v32：清理结果弹窗（显示清理数量） ===
    cleanupResult?.let { count ->
        GlassAlertDialog(
            onDismissRequest = { vm.clearCleanupResult() },
            title = "清理完成",
            content = {
                Text(
                    if (count > 0) "已清理 $count 条无效课表。" else "没有需要清理的无效课表。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(onClick = { vm.clearCleanupResult() }) { Text("知道了") }
            }
        )
    }
}
