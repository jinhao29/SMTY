package com.shangmentiyu.sportscoach.ui.settings.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.excel.ImportStrategy
import com.shangmentiyu.sportscoach.ui.theme.ShadowTokens
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface

/**
 * 设置页复用的 iOS Settings 风格基础组件（无状态、无 ViewModel 依赖）。
 *
 * 供 [com.shangmentiyu.sportscoach.ui.settings.SettingsScreen] 及其各功能区块组合使用：
 * 分组包装、分组卡片、图标徽章、操作行、徽标胶囊、导入策略行、统计项与分隔线。
 */

/**
 * iOS 分组包装：Section Header（珊瑚橙）+ 单张卡片。
 */
@Composable
internal fun IosSectionWrapper(
    text: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = appPrimary(),  // v45 统一：活性蓝紫 → 珊瑚橙 #FF6B47
            modifier = Modifier.padding(horizontal = 4.dp, vertical = Spacing.xs)
        )
        content()
    }
}

/**
 * iOS Inset Grouped 卡片：纯白 + 24dp 大圆角 + iOS 风格柔和弥散阴影。
 */
@Composable
internal fun IosGroupedListCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = ShadowTokens.softAmbient,
                spotColor = ShadowTokens.softSpot
            )
            .background(appSurface(), RoundedCornerShape(24.dp))
    ) {
        // v45：移除顶部 4dp 渐变装饰条，保持卡片简洁统一
        content()
    }
}

/**
 * iOS Settings 风格图标徽章：36×36 圆角方形彩色背景 + 白色图标。
 */
@Composable
internal fun IosIconBadge(
    icon: ImageVector,
    iconBgColor: Color,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(iconBgColor, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * iOS Settings 风格操作行：彩色方形图标 + 标题/副标题 + 右箭头。
 */
@Composable
internal fun SettingsActionRow(
    icon: ImageVector,
    iconBgColor: Color,
    iconContentDescription: String,
    title: String,
    subtitle: String,
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
                iconBgColor = iconBgColor,
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
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant()
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = appOnSurfaceVariant(),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 小徽标：珊瑚橙/浅灰胶囊（可清理数量等）。 */
@Composable
internal fun BadgePill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 导入策略单行：标题 + 说明，点击回调选中的策略。 */
@Composable
internal fun ImportStrategyRow(
    title: String,
    desc: String,
    @Suppress("UNUSED_PARAMETER") strategy: ImportStrategy,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = appOnSurface()
        )
        Spacer(Modifier.height(2.dp))
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = appOnSurfaceVariant()
        )
    }
}

/**
 * 统计项：数值 + 标签竖向叠。
 */
@Composable
internal fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = Spacing.sm)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * 统计项分隔线：0.5dp 宽 + 40dp 高。
 */
@Composable
internal fun StatDivider() {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(40.dp)
            .background(appDividerColor())
    )
}
