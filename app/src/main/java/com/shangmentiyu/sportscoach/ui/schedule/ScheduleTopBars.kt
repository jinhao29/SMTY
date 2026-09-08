package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.shangmentiyu.sportscoach.ui.theme.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors

/**
 * 课表页顶栏与多选操作栏（v53 拆分自 ScheduleScreen）：
 * 常规模式 TopBar、多选模式 TopBar、多选模式底部操作栏、右上角功能按钮。
 */

/**
 * 常规模式 TopAppBar：返回 + 标题"课表" + 右上角功能按钮组。
 * 右上角按钮：【图标 + 下方小字】垂直组合，替代纯图标按钮；
 * "多选"/"清空" 仅在有排课时显示。
 *
 * @param onBack 返回回调
 * @param hasSchedules 当前是否存在排课（控制"多选"/"清空"按钮显隐）
 * @param onAutoSchedule 按课时包自动排课入口
 * @param onDeleteByStudent 按学员删除排课入口
 * @param onEnterMultiSelect 进入多选模式入口
 * @param onClearAll 清空全部课表入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleTopBar(
    onBack: () -> Unit,
    showBack: Boolean = true,
    hasSchedules: Boolean,
    onAutoSchedule: () -> Unit,
    onDeleteByStudent: () -> Unit,
    onEnterMultiSelect: () -> Unit,
    onClearAll: () -> Unit
) {
    AppTopBar(
        title = { Text("课表", fontWeight = FontWeight.Bold) },
        colors = glassTopAppBarColors(),
        navigationIcon = {
            // 俱乐部模式作为底部 Tab 时隐藏返回箭头（showBack=false），避免"点了没反应"
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            }
        },
        shareLabel = "课表",
        actions = {
            // 颜色规范：珊瑚橙 #FF6B47（appPrimary）/ 深灰 #6B6B6B（appOnSurfaceVariant）
            ScheduleActionButton(
                icon = Icons.Outlined.EventRepeat,
                label = "排课",
                tint = appPrimary(),
                onClick = onAutoSchedule
            )
            ScheduleActionButton(
                icon = Icons.Outlined.PersonRemove,
                label = "学员",
                tint = appOnSurfaceVariant(),
                onClick = onDeleteByStudent
            )
            if (hasSchedules) {
                ScheduleActionButton(
                    icon = Icons.Outlined.DeleteSweep,
                    label = "多选",
                    tint = appPrimary(),
                    onClick = onEnterMultiSelect
                )
                ScheduleActionButton(
                    icon = Icons.Outlined.CleaningServices,
                    label = "清空",
                    tint = appPrimary(),
                    onClick = onClearAll
                )
            }
        }
    )
}

/**
 * 多选模式 TopAppBar：关闭按钮 + 已选数量 + 全选当天。
 *
 * @param selectedCount 已选中的排课数量
 * @param hasDaySchedules 当天是否存在课程（控制"全选当天"按钮显隐）
 * @param isAllDaySelected 当天是否已全选（控制按钮文案切换）
 * @param onClose 退出多选模式回调
 * @param onToggleAllDay 全选/取消全选当天回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MultiSelectTopBar(
    selectedCount: Int,
    hasDaySchedules: Boolean,
    isAllDaySelected: Boolean,
    onClose: () -> Unit,
    onToggleAllDay: () -> Unit
) {
    AppTopBar(
        title = { Text("已选 $selectedCount 条", fontWeight = FontWeight.Bold) },
        colors = glassTopAppBarColors(),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "退出多选")
            }
        },
        actions = {
            if (hasDaySchedules) {
                TextButton(onClick = onToggleAllDay) {
                    Text(
                        if (isAllDaySelected) "取消全选" else "全选当天",
                        color = appPrimary(),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    )
}

/**
 * 多选模式底部操作栏：悬浮白色大圆角胶囊 + 删除选中按钮。
 *
 * 设计要点（与底部悬浮导航栏风格一致）：
 * - 纯白底色大圆角胶囊（RoundedCornerShape(24.dp)）
 * - 悬浮投影（shadowElevation=8.dp），外层透明容器包裹
 * - windowInsetsPadding 避让系统导航栏
 * - 左侧显示已选数量，右侧珊瑚橙"删除选中"按钮
 * - 选中数量为 0 时按钮置灰，不可点击
 *
 * @param selectedCount 已选中的排课数量
 * @param onDelete 点击删除按钮的回调
 */
@Composable
internal fun MultiSelectBottomBar(
    selectedCount: Int,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Spacing.screenH, vertical = Spacing.sm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.06f),
                    spotColor = Color.Black.copy(alpha = 0.10f)
                )
                .clip(RoundedCornerShape(24.dp))
                .background(appSurface())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "已选 $selectedCount 条",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = appOnSurface()
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selectedCount > 0) MaterialTheme.colorScheme.error
                        else appOutline().copy(alpha = 0.3f)
                    )
                    .clickable(enabled = selectedCount > 0, onClick = onDelete)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "删除选中",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * 课表页右上角功能按钮：【图标 + 下方小字】垂直组合。
 *
 * 颜色规范：珊瑚橙 #FF6B47（appPrimary）或 深灰 #6B6B6B（appOnSurfaceVariant），
 * 由调用方通过 tint 指定。onClick 与原纯图标按钮完全一致，不改变任何功能。
 */
@Composable
private fun ScheduleActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}
