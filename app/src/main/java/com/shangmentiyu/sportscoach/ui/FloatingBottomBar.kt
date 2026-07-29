package com.shangmentiyu.sportscoach.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import com.shangmentiyu.sportscoach.ui.theme.appPrimary

/**
 * 胶囊式悬浮底部导航栏。
 *
 * 设计要点：
 * - 大圆角（30dp）胶囊形状，纯白背景，四周柔和阴影
 * - 与页面底部保持 12dp 留白，形成悬浮感
 * - 选中项：珊瑚橙 #FF6B47 图标 + 文字
 * - 未选中项：浅灰 #A6A8AB 图标 + 文字
 * - 中央凸出 FAB：珊瑚橙圆形，白色加号图标，向外凸出 20dp
 *
 * 布局：左 N 个 Tab + 中央 FAB + 右 M 个 Tab
 * 当 items.size=3 时：左 1 + FAB + 右 2
 *
 * @param items 导航项列表
 * @param currentRoute 当前路由
 * @param onNavigate 点击导航回调
 * @param onFabClick FAB 点击回调
 * @param badgeForRoute 返回每个 route 对应的角标内容（空 lambda 表示无角标）
 */
@Composable
fun FloatingBottomBar(
    items: List<BottomItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onFabClick: () -> Unit,
    badgeForRoute: @Composable (String) -> Unit = {}
) {
    // 左侧 Tab 数量：3 项时左 1 + FAB + 右 2；否则对半分
    val halfIndex = if (items.size >= 3) 1 else items.size / 2

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        // === 胶囊式导航面板（v40 微调：阴影淡化 8dp→4dp）===
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(30.dp),
                    ambientColor = Color.Black.copy(alpha = 0.04f),
                    spotColor = Color.Black.copy(alpha = 0.06f)
                ),
            shape = RoundedCornerShape(30.dp),
            color = Color.White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧 Tabs
                items.take(halfIndex).forEach { item ->
                    NavTabItem(
                        item = item,
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        badgeContent = { badgeForRoute(item.route) }
                    )
                }

                // === 中央 FAB（v40 微调：放弃凸出，放入胶囊内部）===
                // 原方案 FAB 凸出 20dp 视觉尴尬；改为内置在胶囊 Row 中，48dp 圆形
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

                // 右侧 Tabs
                items.drop(halfIndex).forEach { item ->
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
 * 单个导航项：图标在上 + 文字在下。
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
    // v40 任务3：选中 = 珊瑚橙 #FF6B47；未选中 = 深灰 #6B6B6B
    val color = if (selected) appPrimary() else Color(0xFF6B6B6B)

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BadgedBox(
            badge = { badgeContent() }
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.label,
            color = color,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1
        )
    }
}
