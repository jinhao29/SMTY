package com.shangmentiyu.sportscoach.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.app.framework.LanSyncManager
import com.shangmentiyu.sportscoach.app.framework.UdpDesktopDiscoveryService
import com.shangmentiyu.sportscoach.ui.settings.components.IosGroupedListCard
import com.shangmentiyu.sportscoach.ui.settings.components.IosIconBadge
import com.shangmentiyu.sportscoach.ui.settings.components.IosSectionWrapper
import com.shangmentiyu.sportscoach.ui.settings.components.SettingsActionRow
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightSecondary
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext

/**
 * 桌面同步分组（v23 双端同步）。
 *
 * - 连接状态（v23.6）：实时轮询 PC 在线状态，自动识别 Wi-Fi / USB，无需手动配置
 * - 立即同步：推送整库备份到 PC（自动合并）+ 拉取 PC 学员汇总（UPDATE_PART 合并）
 * - 测试连接（v23.6）：/health 握手，反馈连接方式与延迟
 * - PC 端地址 / 端口 / token：与 PC 端 sync_server 配置对应
 * - USB 连接：PC 自动 adb reverse（usb-watch），地址可不填（自动回退 127.0.0.1）
 */
@Composable
internal fun DesktopSyncSection(vm: SettingsViewModel) {
    val syncHost by vm.syncHost.collectAsStateWithLifecycle()
    val syncPort by vm.syncPort.collectAsStateWithLifecycle()
    val syncToken by vm.syncToken.collectAsStateWithLifecycle()
    val syncInProgress by vm.syncInProgress.collectAsStateWithLifecycle()
    val syncEnabled by vm.syncEnabled.collectAsStateWithLifecycle()

    // v23.6 PC 在线状态（LanSyncManager 单例轮询：Wi-Fi 心跳优先，/health 探测兜底）
    val lanSync = remember { GlobalContext.get().get<LanSyncManager>() }
    val desktopLink: LanSyncManager.DesktopLink? by lanSync.desktopOnline.collectAsStateWithLifecycle()

    var showHostDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var discoveryHint by remember { mutableStateOf("") }
    val context = LocalContext.current

    // v23.6 自动连接：进入设置页时未配置地址且心跳新鲜 → 自动填充并提示（零配置）
    LaunchedEffect(Unit) {
        if (syncHost.isBlank()) {
            val found = UdpDesktopDiscoveryService.getDiscoveredDesktop(context)
            if (found != null && System.currentTimeMillis() - found.lastSeenAtMs < 120_000L) {
                vm.setSyncHost(found.host)
                vm.setSyncPort(found.port.toString())
                if (found.token.isNotEmpty() && syncToken.isBlank()) {
                    vm.setSyncToken(found.token)
                }
                vm.updateStatus(
                    "已自动连接到 PC：${found.pcName.ifBlank { found.host }}（${found.host}:${found.port}）"
                )
            }
        }
    }

    IosSectionWrapper(text = "桌面同步") {
        IosGroupedListCard {
            // === v23.6 连接状态行（实时：自动识别 Wi-Fi / USB） ===
            val link = desktopLink
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                IosIconBadge(
                    icon = Icons.Outlined.Computer,
                    iconBgColor = if (link != null) Color(0xFFDCFCE7) else LightSecondary,
                    contentDescription = "连接状态"
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (link != null)
                            "已连接：${link.pcName}"
                        else "未连接 PC",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = appOnSurface()
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (link != null)
                            "${link.host}:${link.port} · " +
                                (if (link.viaUsb) "USB" else "Wi-Fi") + " · 自动识别"
                        else "PC 端打开后自动识别（同一 Wi-Fi 或 USB 插入）",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (link != null) Color(0xFF15803D) else appOnSurfaceVariant()
                    )
                }
                // 在线指示点
                androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = if (link != null) Color(0xFF34D399) else Color(0xFFD4D4D4))
                }
            }

            // 自动同步开关（v23.1）：开启后备份完成自动推送 + 每 30 分钟周期双向同步
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                IosIconBadge(
                    icon = Icons.Outlined.Autorenew,
                    iconBgColor = LightPrimary,
                    contentDescription = "自动同步"
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "自动同步",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = appOnSurface()
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (syncEnabled)
                            "已开启：数据变更后自动推送到 PC，并每 30 分钟双向对齐"
                        else
                            "已关闭，仅手动点「立即同步」时交换数据",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (syncEnabled) LightPrimary else appOnSurfaceVariant()
                    )
                }
                Switch(
                    checked = syncEnabled,
                    onCheckedChange = { vm.setSyncEnabled(it) }
                )
            }

            // 立即同步（双向：推送备份 + 拉取学员数据）
            SettingsActionRow(
                icon = Icons.Outlined.Sync,
                iconBgColor = LightPrimary,
                iconContentDescription = "立即同步",
                title = if (syncInProgress) "正在同步…" else "立即同步",
                subtitle = if (syncInProgress) "正在与 PC 端交换数据，请稍候"
                else "推送备份到 PC 并拉取 PC 端学员数据",
                showTopDivider = false
            ) { vm.syncNow() }

            // 测试连接（v23.6：/health 握手，空地址自动回退 127.0.0.1 → USB 即插即测）
            SettingsActionRow(
                icon = Icons.Outlined.NetworkCheck,
                iconBgColor = LightSecondary,
                iconContentDescription = "测试连接",
                title = "测试连接",
                subtitle = "检测 PC 端同步服务是否可达（反馈连接方式与延迟）",
                showTopDivider = true
            ) { vm.testConnection() }

            // 自动发现 PC（读取 UdpDesktopDiscoveryService 的最新心跳）
            SettingsActionRow(
                icon = Icons.Outlined.Wifi,
                iconBgColor = LightSecondary,
                iconContentDescription = "自动发现 PC",
                title = "自动发现 PC",
                subtitle = discoveryHint.ifBlank {
                    "PC 端服务在线时自动填充；也可点击手动刷新"
                },
                showTopDivider = true
            ) {
                val found = UdpDesktopDiscoveryService.getDiscoveredDesktop(context)
                if (found == null) {
                    vm.updateStatus("未发现 PC：请确认 PC 端已启动同步服务且连接同一网络")
                } else {
                    vm.setSyncHost(found.host)
                    vm.setSyncPort(found.port.toString())
                    // v23.5 零配置：PC 心跳携带 token，手机端为空时自动填充
                    if (found.token.isNotEmpty() && syncToken.isBlank()) {
                        vm.setSyncToken(found.token)
                    }
                    val ageSec = (System.currentTimeMillis() - found.lastSeenAtMs) / 1000
                    val freshness = if (ageSec < 15) "在线" else "${ageSec / 60} 分钟前在线"
                    val pcLabel = found.pcName.ifBlank { found.host }
                    vm.updateStatus("已自动填充：$pcLabel（${found.host}:${found.port}，$freshness）")
                    discoveryHint = "上次发现：$pcLabel（${found.host}:${found.port}）"
                }
            }

            SettingsActionRow(
                icon = Icons.Outlined.Lan,
                iconBgColor = LightSecondary,
                iconContentDescription = "PC 端地址",
                title = "PC 端地址",
                subtitle = syncHost.ifBlank { "未设置（局域网填 PC 的 IP，USB 填 127.0.0.1）" },
                showTopDivider = true
            ) { showHostDialog = true }

            SettingsActionRow(
                icon = Icons.Outlined.Computer,
                iconBgColor = LightSecondary,
                iconContentDescription = "同步端口",
                title = "同步端口",
                subtitle = syncPort,
                showTopDivider = true
            ) { showPortDialog = true }

            SettingsActionRow(
                icon = Icons.Outlined.Key,
                iconBgColor = LightSecondary,
                iconContentDescription = "同步 token",
                title = "鉴权 token",
                subtitle = if (syncToken.isBlank()) "未设置（两端都为空时跳过校验）" else "已设置",
                showTopDivider = true
            ) { showTokenDialog = true }
        }
    }

    if (showHostDialog) {
        TextEditDialog(
            title = "PC 端地址",
            initial = syncHost,
            placeholder = "如 192.168.1.100（USB 连接填 127.0.0.1）"
        ) { input ->
            vm.setSyncHost(input)
            showHostDialog = false
        }
    }
    if (showPortDialog) {
        TextEditDialog(
            title = "同步端口",
            initial = syncPort,
            placeholder = "默认 8765"
        ) { input ->
            vm.setSyncPort(input)
            showPortDialog = false
        }
    }
    if (showTokenDialog) {
        TextEditDialog(
            title = "鉴权 token",
            initial = syncToken,
            placeholder = "需与 PC 端一致，两端都为空时跳过校验"
        ) { input ->
            vm.setSyncToken(input)
            showTokenDialog = false
        }
    }
}

/** 单行文本编辑对话框（地址/端口/token 共用）。 */
@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    placeholder: String,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    GlassAlertDialog(
        onDismissRequest = { onConfirm(initial) },
        title = title,
        content = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "USB 连接：PC 运行 desktop_sync/usb_sync.bat 后填 127.0.0.1",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.trim()) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(initial) }) { Text("取消") }
        }
    )
}
