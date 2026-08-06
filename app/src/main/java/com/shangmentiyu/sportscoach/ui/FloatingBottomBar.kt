package com.shangmentiyu.sportscoach.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface

/**
 * 药丸高亮式悬浮底部导航栏（v45 重构）。
 *
 * 设计要点：
 * - 纯白大圆角胶囊（24dp 圆角）+ 柔和阴影悬浮在屏幕底部
 * - 选中项：浅珊瑚橙药丸背景（20% 透明度 #33FF6B47）+ 珊瑚橙图标与文字（水平排列）
 * - 未选中项：透明背景 + 深灰 #6B6B6B 线框图标 + 深灰文字（水平排列）
 * - 中央 FAB（添加学员）保留为珊瑚橙圆形按钮，与导航项并排
 * - 外层 Box 背景强制透明，胶囊两侧透出主页面底色（#FAFAFA），无灰边
 *
 * 布局：[Tab1] [Tab2] [FAB] [Tab3] ...
 * - 所有导航项与 FAB 并排在 Row 中
 * - FAB 用 48dp 圆形按钮，与药丸导航项视觉对比形成层次
 *
 * @param items 导航项列表
 * @param currentRoute 当前路由
 * @param onNavigate 点击导航回调
 * @param onFabClick FAB 点击回调
 * @param fabInsertIndex FAB 在 items 中的插入位置（默认第 1 位，即第 2 个元素之后）
 * @param badgeForRoute 返回每个 route 对应的角标内容（空 lambda 表示无角标）
 */
@Composable
fun FloatingBottomBar(
    items: List<BottomItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onFabClick: () -> Unit,
    fabInsertIndex: Int = 1,
    badgeForRoute: @Composable (String) -> Unit = {}
) {
    // 外层 Box 背景强制透明：确保胶囊两侧透出主页面底色（#FAFAFA），无灰边
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        // 胶囊容器：纯白 + 24dp 大圆角 + 柔和投影
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.04f),
                    spotColor = Color.Black.copy(alpha = 0.06f)
                ),
            shape = RoundedCornerShape(24.dp),
            color = appSurface()  // 纯白实底（暗色模式自动跟随表面令牌）
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 按 fabInsertIndex 拆分 items，在中间插入 FAB
                items.take(fabInsertIndex).forEach { item ->
                    NavTabItem(
                        item = item,
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        badgeContent = { badgeForRoute(item.route) }
                    )
                }

                // === 中央 FAB：珊瑚橙圆形 + 白色加号 ===
                // 保留独立圆形按钮，与药丸导航项形成视觉层次对比
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(
                            elevation = 3.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.06f),
                            spotColor = appPrimary().copy(alpha = 0.20f)
                        )
                        .clip(CircleShape)
                        .background(appPrimary())
                        .clickable(onClick = onFabClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "添加",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                items.drop(fabInsertIndex).forEach { item ->
                    NavTabItem(
                        item = item,
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        badgeContent = { badgeForRoute(item.route) }
                    )
                }
            }
        }
    }
}

/**
 * 单个导航项：药丸高亮选中样式。
 *
 * - 选中：浅珊瑚橙药丸背景（Color(0x33FF6B47)）+ 珊瑚橙图标与文字（水平排列）
 * - 未选中：透明背景 + 深灰图标与文字（水平排列）
 *
 * 文字与图标水平排列，选中时文字可见，未选中时仅显示图标（参考图风格）
 *
 * === 性能优化说明 ===
 * 1. 颜色用 `derivedStateOf` 缓存：避免 AnimatedVisibility 动画过程中（约 60 帧/秒）
 *    重复创建 Color 实例
 * 2. 抽出 [NavTabLabel] 独立 @Composable：将 AnimatedVisibility 动画的重组范围
 *    严格隔离在 NavTabLabel 内部，避免连累 Icon / BadgedBox / Row 重组
 *
 * @param item 导航项数据
 * @param selected 是否选中
 * @param onClick 点击回调
 * @param badgeContent 角标内容（默认空，不显示角标）
 */
@Composable
private fun NavTabItem(
    item: BottomItem,
    selected: Boolean,
    onClick: () -> Unit,
    badgeContent: @Composable () -> Unit = {}
) {
    // === 性能优化：用 derivedStateOf 缓存目标颜色，避免动画过程中重复计算 ===
    // appPrimary() 是 @Composable 函数，先取出主色，再用 derivedStateOf 包裹依赖 selected 的派生颜色
    val primaryColor = appPrimary()
    val onSurfaceVariant = appOnSurfaceVariant()
    val targetContentColor by remember(selected, primaryColor) {
        derivedStateOf { if (selected) primaryColor else onSurfaceVariant }
    }
    val targetBgColor by remember(selected) {
        derivedStateOf { if (selected) primaryColor.copy(alpha = 0.2f) else Color.Transparent }
    }

    // === 动画优化：导航项颜色切换用 animateColorAsState 平滑过渡 ===
    // 原硬切换在选中/未选中切换瞬间有突兀感，220ms tween 让药丸背景与图标/文字色协调过渡
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
        label = "nav_content"
    )
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
        label = "nav_bg"
    )

    // 药丸形状：50% 圆角（完全圆角）
    val pillShape = RoundedCornerShape(50)

    // 选中时内边距加大，让药丸背景更显眼；未选中时紧凑排列
    val horizontalPadding = if (selected) 16.dp else 12.dp

    Row(
        modifier = Modifier
            .clip(pillShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BadgedBox(
            badge = { badgeContent() }
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        // === 性能优化：抽出独立 NavTabLabel，隔离 AnimatedVisibility 动画重组 ===
        // 原 AnimatedVisibility 在动画过程中每帧触发重组，会连累整个 NavTabItem（Icon/Row）重组。
        // 抽成独立 @Composable 后，动画重组仅限 NavTabLabel 内部，Icon 保持不动。
        NavTabLabel(
            label = item.label,
            color = contentColor,
            visible = selected
        )
    }
}

/**
 * 导航项文字标签（独立 @Composable，隔离 AnimatedVisibility 动画重组）。
 *
 * === 性能优化说明 ===
 * AnimatedVisibility 在动画过程中（约 220ms，~13 帧）会持续触发本组件重组。
 * 抽成独立 @Composable 后，这些重组不会连累父组件 [NavTabItem] 中的 Icon / BadgedBox，
 * 仅本函数内的 Text 重新绘制，CPU 开销大幅降低。
 *
 * @param label 文本
 * @param color 文本颜色
 * @param visible 是否可见（选中时显示，未选中时隐藏）
 */
@Composable
private fun NavTabLabel(
    label: String,
    color: Color,
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally()
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
