package com.shangmentiyu.sportscoach.ui.schedule

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appOnWarningContainer
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import com.shangmentiyu.sportscoach.ui.theme.appWarningContainer

/**
 * 课表页概览与警告组件（v53 拆分自 ScheduleScreen）：
 * 中央概览卡（今日总课时/已签退/剩余排课）与余额不足警告横幅。
 */

/**
 * 中央概览卡片（v40 新增）。
 *
 * 参照图2中间屏幕的仪表盘/概览卡片：
 * - 纯白背景 + 柔和阴影 + 圆角 16dp，无边框
 * - 三个核心数据并排展示：今日总课时 / 已签退 / 剩余排课
 * - 数字大号加粗居中，使用珊瑚橙 #FF6B47（突出主数据）或深黑 #1A1A1A
 * - 标签小号灰色 #6B6B6B
 *
 * @param totalToday 今日总课时
 * @param signedOut 已签退数
 * @param remaining 剩余排课数
 */
@Composable
internal fun OverviewCard(
    totalToday: Int,
    signedOut: Int,
    remaining: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(appSurface())
            .padding(vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 今日总课时（珊瑚橙突出）
            OverviewStatItem(
                value = totalToday.toString(),
                label = "今日总课时",
                valueColor = appPrimary()
            )
            // 分隔线
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 32.dp)
                    .background(appOutline().copy(alpha = 0.2f))
            )
            // 已签退（深黑色）
            OverviewStatItem(
                value = signedOut.toString(),
                label = "已签退",
                valueColor = appOnSurface()
            )
            // 分隔线
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 32.dp)
                    .background(appOutline().copy(alpha = 0.2f))
            )
            // 剩余排课（深黑色）
            OverviewStatItem(
                value = remaining.toString(),
                label = "剩余排课",
                valueColor = appOnSurface()
            )
        }
    }
}

/**
 * 概览卡片单个统计项：大号数字 + 小号标签，居中竖排。
 */
@Composable
private fun OverviewStatItem(
    value: String,
    label: String,
    valueColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            lineHeight = 34.sp,
            color = valueColor
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = appOnSurfaceVariant(),
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * v24 优化2：余额不足警告 Alert Banner。
 *
 * 浅橙色背景圆角卡片，显示本周排课时检测到的余额不足学员列表，
 * 提示教练及时为学员续费。点击关闭按钮可手动清除警告。
 *
 * 设计要点：
 * - 浅橙色背景 + 深橙色文字，符合 Material 警告色规范
 * - 圆角 12dp，与 IOSCard 风格一致
 * - 警告图标 + 标题 + 学员列表 + 关闭按钮
 * - 不改动现有 UI 布局，仅在周次信息卡片后新增可关闭的提示条
 *
 * @param warnings 余额不足警告文案列表（每条形如 "陈书楠 周五 余额不足"）
 * @param onDismiss 关闭回调
 */
@Composable
internal fun NoBalanceWarningBanner(
    warnings: List<String>,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH, vertical = Spacing.xs)
            .clip(RoundedCornerShape(12.dp))
            .background(appWarningContainer())
            .padding(Spacing.md)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = appOnWarningContainer(),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "余额不足提醒",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = appOnWarningContainer()
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = appOnWarningContainer(),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            warnings.forEach { warning ->
                Text(
                    text = "• $warning",
                    style = MaterialTheme.typography.bodySmall,
                    color = appOnWarningContainer(),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
