package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 深色通知卡片组件（v45 新增：参考"黑色通知卡片"参考图）。
 *
 * 设计要点：
 * - 深黑背景（#1C1C1E）+ 16dp 大圆角，悬浮在屏幕顶部或底部
 * - 右上角线框式 X 关闭按钮（24dp 点击区域，18dp 图标）
 * - 主标题：纯白 #FFFFFF 加粗（16sp）
 * - 描述文本：浅灰 #A6A8AB（14sp）
 * - 底部可选交互文字：珊瑚橙 #FF6B47（可点击）
 *
 * 适用场景：
 * - 需要强提醒的系统通知（如：订单状态更新、同步完成、错误提示）
 * - 不适合用普通 Snackbar 的关键操作反馈
 * - 需要用户主动关闭的持久性提醒
 *
 * @param title 主标题（必填，加粗白色）
 * @param description 描述文本（可选，浅灰色）
 * @param actionText 底部交互文字（可选，珊瑚橙）
 * @param onActionClick 交互文字点击回调
 * @param onDismiss 关闭按钮点击回调
 * @param modifier 外部修饰符
 */
@Composable
fun DarkToastCard(
    title: String,
    description: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1E))
            .padding(16.dp)
    ) {
        Column {
            // === 顶部：标题 + 右上角 X 关闭按钮 ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // 右上角线框式 X 关闭按钮
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = Color(0xFFA6A8AB),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // === 描述文本（可选） ===
            if (description != null) {
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = description,
                    color = Color(0xFFA6A8AB),
                    fontSize = 14.sp
                )
            }

            // === 底部交互文字（可选，珊瑚橙） ===
            if (actionText != null && onActionClick != null) {
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = actionText,
                    color = appPrimary(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onActionClick)
                )
            }
        }
    }
}

/**
 * 深色 Toast 卡片 + 进入/退出动画包装器。
 *
 * 用于需要显示/隐藏切换的场景，带从顶部滑入/滑出动画。
 *
 * @param visible 是否显示
 * @param title 主标题
 * @param description 描述文本（可选）
 * @param actionText 交互文字（可选）
 * @param onActionClick 交互回调（可选）
 * @param onDismiss 关闭回调
 */
@Composable
fun AnimatedDarkToastCard(
    visible: Boolean,
    title: String,
    description: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        DarkToastCard(
            title = title,
            description = description,
            actionText = actionText,
            onActionClick = onActionClick,
            onDismiss = onDismiss,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
