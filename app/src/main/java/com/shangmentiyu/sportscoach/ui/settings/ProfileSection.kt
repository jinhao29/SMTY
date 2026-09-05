package com.shangmentiyu.sportscoach.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.shangmentiyu.sportscoach.ui.Routes
import com.shangmentiyu.sportscoach.ui.settings.components.IosGroupedListCard
import com.shangmentiyu.sportscoach.ui.settings.components.IosIconBadge
import com.shangmentiyu.sportscoach.ui.settings.components.IosSectionWrapper
import com.shangmentiyu.sportscoach.ui.settings.components.SettingsActionRow
import com.shangmentiyu.sportscoach.ui.settings.components.StatDivider
import com.shangmentiyu.sportscoach.ui.settings.components.StatItem
import com.shangmentiyu.sportscoach.ui.theme.AppTextField
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.ThemeSwitch
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant

/**
 * 个人资料与概览区块：外观（深色模式）、教练设置（教练姓名）、统计信息（今日/总记录/教练）。
 *
 * 编辑对话框状态（打开/输入）收敛在本区块内，输入击键只重组本区块，不会整页重扫。
 */

/**
 * 外观（深色模式开关）。三态语义：开关开 = 强制深色；开关关 = 跟随系统。
 */
@Composable
internal fun AppearanceSection(vm: SettingsViewModel) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    IosSectionWrapper(text = "外观") {
        IosGroupedListCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                IosIconBadge(
                    icon = Icons.Outlined.DarkMode,
                    iconBgColor = LightPrimary,
                    contentDescription = "深色模式"
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "深色模式",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = appOnSurface()
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (uiState.darkTheme == true)
                            "已开启，全天使用深色外观"
                        else
                            "跟随系统深色外观",
                        style = MaterialTheme.typography.labelSmall,
                        color = appOnSurfaceVariant()
                    )
                }
                ThemeSwitch(
                    checked = uiState.darkTheme == true,
                    onCheckedChange = { vm.setDarkTheme(it) },
                    modifier = Modifier.width(180.dp)
                )
            }
        }
    }
}

/**
 * 教练设置：编辑对话框状态（打开/输入）收敛在本区块内，
 * 输入击键只重组本区块，不会整页重扫。
 */
@Composable
internal fun CoachSection(vm: SettingsViewModel) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val coach = uiState.coach
    var showDialog by remember { mutableStateOf(false) }
    var coachInput by remember { mutableStateOf("") }

    IosSectionWrapper(text = "教练设置") {
        IosGroupedListCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        coachInput = coach  // 预填当前值，便于修改
                        showDialog = true
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

    if (showDialog) {
        GlassAlertDialog(
            onDismissRequest = { showDialog = false },
            title = "修改教练姓名",
            content = {
                AppTextField(
                    value = coachInput,
                    onValueChange = { coachInput = it },
                    label = { Text("教练姓名") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val trimmed = coachInput.trim()
                    if (trimmed.isNotEmpty()) {
                        vm.setCoach(trimmed)
                    }
                    showDialog = false
                }) { Text("确认保存") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 统计信息：三栏数据（今日/总记录/教练），纯静态展示。
 */
@Composable
internal fun StatsSection(vm: SettingsViewModel) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    IosSectionWrapper(text = "统计信息") {
        IosGroupedListCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "今日课程", value = uiState.todayCount.toString())
                StatDivider()
                StatItem(label = "总记录数", value = uiState.totalCount.toString())
                StatDivider()
                StatItem(label = "教练", value = if (uiState.coach.isBlank()) "未设置" else uiState.coach)
            }
        }
    }
}

/**
 * 个人资料与概览组装：外观 + 教练设置 + 统计信息。
 */
@Composable
internal fun ProfileSection(vm: SettingsViewModel) {
    AppearanceSection(vm)
    CoachSection(vm)
    StatsSection(vm)
}

/**
 * 工具与扩展功能：BMI 计算器、教练管理、话术管理（家长沟通）。
 *
 * 设计意图：与「统计信息」紧邻，作为教练日常高频入口的快捷组；
 * 视觉上沿用 iOS 分组卡片（每个分组单独卡片），不堆叠成大块。
 */
@Composable
internal fun ToolsSection(onNavigate: (String) -> Unit) {
    // 工具（独立计算器，不依赖学员数据）
    IosSectionWrapper(text = "工具") {
        IosGroupedListCard {
            SettingsActionRow(
                icon = Icons.Outlined.Calculate,
                iconBgColor = LightPrimary,
                iconContentDescription = "BMI 计算器",
                title = "BMI 计算器",
                subtitle = "输入身高体重快速计算 BMI",
                showTopDivider = false,
                onClick = { onNavigate(Routes.BMI_CALCULATOR) }
            )
        }
    }

    // === 教练管理：档案 / 排班工作量 / 合伙人团队 / 薪资分成 ===
    IosSectionWrapper(text = "教练管理") {
        IosGroupedListCard {
            SettingsActionRow(
                icon = Icons.Outlined.Groups,
                iconBgColor = LightPrimary,
                iconContentDescription = "教练管理",
                title = "教练管理",
                subtitle = "教练档案 · 排班工作量 · 合伙人团队 · 薪资分成",
                showTopDivider = false,
                onClick = { onNavigate(Routes.COACH_MANAGE) }
            )
        }
    }

    // === 话术管理：自定义家长沟通话术，复制后粘贴给家长 ===
    IosSectionWrapper(text = "家长沟通") {
        IosGroupedListCard {
            SettingsActionRow(
                icon = Icons.AutoMirrored.Outlined.List,
                iconBgColor = LightPrimary,
                iconContentDescription = "话术管理",
                title = "话术管理",
                subtitle = "自定义话术项目，复制后粘贴给家长",
                showTopDivider = false,
                onClick = { onNavigate(Routes.SCRIPT_LIST) }
            )
        }
    }
}
