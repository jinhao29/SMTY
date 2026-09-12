package com.shangmentiyu.sportscoach.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.data.internal.ModeManager
import com.shangmentiyu.sportscoach.ui.settings.components.IosGroupedListCard
import com.shangmentiyu.sportscoach.ui.settings.components.IosIconBadge
import com.shangmentiyu.sportscoach.ui.settings.components.IosSectionWrapper
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import kotlinx.coroutines.launch

/**
 * 工作模式切换（v23.12 多租户·物理隔离）。
 *
 * 上门体育 / 俱乐部 各自独立数据库（sports_coach_db / sports_coach_club_db），
 * 切换后重启 App 生效（已创建的 ViewModel 持有旧库 DAO，热切不安全）。
 *
 * 视觉：与设置页其余区块统一 —— IosSectionWrapper（珊瑚橙小节标题）
 * + IosGroupedListCard（iOS 表单分组卡）+ IosIconBadge 图标行，
 * 选中态用 iOS 风格右侧对勾（替代 Material RadioButton）。
 */
@Composable
fun ModeSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by ModeManager.mode.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<String?>(null) }
    // v23.13：模式清单来自 assets/config/modes.json（新增机构零代码）
    val modes = remember { ModeManager.allModes() }

    IosSectionWrapper(text = "工作模式") {
        IosGroupedListCard {
            // v23.13：选项由配置生成（含显示名/说明/库名），顺序 = 配置顺序
            modes.forEachIndexed { index, m ->
                ModeOption(
                    icon = iconForMode(m.icon, index),
                    iconContentDescription = m.displayName,
                    title = m.displayName,
                    desc = "${m.tagline}（${m.dbName}）",
                    selected = ModeManager.isSameMode(pending ?: current, m.id),
                    showTopDivider = index > 0,
                    onClick = {
                        if (!ModeManager.isSameMode(current, m.id)) {
                            pending = m.id
                            scope.launch {
                                ModeManager.setMode(context, m.id)
                                restartApp(context)
                            }
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "切换模式后数据完全独立；同步时只会连接对应模式的数据。",
            style = MaterialTheme.typography.labelSmall,
            color = appOnSurfaceVariant(),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/**
 * 工作模式单行：图标徽章 + 标题/说明 + 右侧选中对勾（iOS Settings 选择行风格）。
 */
@Composable
private fun ModeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconContentDescription: String,
    title: String,
    desc: String,
    selected: Boolean,
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
                iconBgColor = LightPrimary,
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
                    desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant()
                )
            }
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "已选中",
                    tint = appPrimary(),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 配置里的 icon 键 → Material 图标（与启动页同一套映射约定）。
 * 未知键按位置回退，保证新机构不填 icon 也有合理图标。
 */
private fun iconForMode(key: String, index: Int): androidx.compose.ui.graphics.vector.ImageVector =
    when (key) {
        "stopwatch", "home" -> Icons.Outlined.FitnessCenter
        "bolt", "emoji_events" -> Icons.Outlined.Workspaces
        else -> if (index == 0) Icons.Outlined.FitnessCenter else Icons.Outlined.Workspaces
    }

/** 切换模式后重启应用（重新创建入口 Activity，Application 重新初始化挂新库） */
private fun restartApp(context: android.content.Context) {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    android.os.Process.killProcess(android.os.Process.myPid())
}
