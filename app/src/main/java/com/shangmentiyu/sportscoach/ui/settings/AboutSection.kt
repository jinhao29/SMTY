package com.shangmentiyu.sportscoach.ui.settings

import android.widget.Toast
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
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.BuildConfig
import com.shangmentiyu.sportscoach.ui.settings.components.IosGroupedListCard
import com.shangmentiyu.sportscoach.ui.settings.components.IosIconBadge
import com.shangmentiyu.sportscoach.ui.settings.components.IosSectionWrapper
import com.shangmentiyu.sportscoach.ui.settings.components.SettingsActionRow
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.update.UpdateManager
import com.shangmentiyu.sportscoach.update.UpdateResult
import com.shangmentiyu.sportscoach.util.CrashDumper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 关于与更多区块：应用更新 + 关于。
 *
 * 历史说明：原"工具 / 教练管理 / 家长沟通"三个分组已迁移到 [ToolsSection]
 * （紧随 [ProfileSection] 的"统计信息"下方），本区块仅保留更新与关于信息。
 */
@Composable
internal fun AboutSection(
    vm: SettingsViewModel,
    onNavigate: (String) -> Unit
) {
    UpdateSection(vm, LocalContext.current)

    // 关于：应用信息
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
                        // 显示真实版本号（此前写死 "v1.0" 与实际 versionName 不符）
                        "v${BuildConfig.VERSION_NAME}",
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
}

/**
 * 应用更新分组：检查更新 + 安装已下载更新（独立协程作用域）。
 */
@Composable
private fun UpdateSection(vm: SettingsViewModel, context: android.content.Context) {
    val checkScope = rememberCoroutineScope()
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
                    vm.updateStatus("正在检查更新…")
                    // === v34：包裹 try-catch，避免 checkScope.launch 内异常
                    // 被 appExceptionHandler 捕获后弹 NPE 黑色提示 ===
                    // === v50：显式切 Dispatchers.IO 发起 OkHttp 请求（双保险：
                    // UpdateChecker 内部虽已 withContext(IO)，再次声明避免请求占用主线程）===
                    checkScope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                UpdateManager.checkNowSync()
                            }
                            val msg = when (result) {
                                is UpdateResult.UpToDate ->
                                    "已是最新版本 (v${BuildConfig.VERSION_NAME})"
                                is UpdateResult.NewVersionAvailable ->
                                    "发现新版本 ${result.tagName}，请选择是否更新"
                                is UpdateResult.Error ->
                                    "检查失败：${result.message}"
                            }
                            vm.updateStatus(msg)
                            // === v51：发现新版本先弹确认框，用户选"是"才下载 ===
                            // 手动检查不受拒绝记录限制（用户主动检查，应尊重并再次提示）
                            if (result is UpdateResult.NewVersionAvailable) {
                                UpdateManager.promptNewVersion(
                                    context,
                                    result,
                                    honorRejection = false
                                )
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
}
