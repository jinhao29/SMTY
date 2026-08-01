package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 珊瑚橙主题统一对话框组件（v45 重构：参考级精致弹窗）。
 *
 * 设计要点（参考"标准弹窗"参考图风格）：
 * - 纯白大圆角卡片（20dp 圆角）+ 柔和阴影，悬浮感更强
 * - 右上角线框式 X 关闭图标（替代原顶部渐变装饰条）
 * - 标题使用 appOnSurface() 深灰加粗（#1A1A1A）
 * - 底部右侧两个圆角胶囊按钮：
 *   - 取消按钮：浅灰背景 #F2F2F5 + 深灰文字
 *   - 确认按钮：珊瑚橙背景 #FF6B47 + 纯白文字
 * - 兼容深色模式（暗色背景 #1C1C1E + 对应调整）
 *
 * === 高内容适配（修复：内容过多时底部按钮溢出屏幕外不可点击）===
 * - Surface 限制最大高度为屏幕 85%，防止内容过长导致按钮被推出可视区域
 * - 正文区使用 weight(1f, fill=false)：
 *   - 内容少时：不撑高，保持紧凑（不影响原有小弹窗视觉）
 *   - 内容多时：自动收缩至剩余空间，配合调用方传入的 verticalScroll 实现滚动
 * - 底部按钮区始终固定在卡片底部，确保可点击
 *
 * 替代 Material3 默认 AlertDialog，使所有对话框视觉风格统一为
 * 参考图所示的现代圆角胶囊样式。
 *
 * @param onDismissRequest 点击对话框外部或返回键时的回调
 * @param title 标题文本（必填）
 * @param confirmButton 确认按钮（通常为 TextButton，会包裹在珊瑚橙胶囊中）
 * @param dismissButton 取消按钮（可选，会包裹在浅灰胶囊中）
 * @param content 正文内容
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val containerColor = appSurface()
    // 限制对话框最大高度为屏幕 85%，留出顶部状态栏和底部导航空间
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight),
            shape = RoundedCornerShape(20.dp),
            color = containerColor,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // === 顶部栏：标题 + 右上角 X 关闭图标 ===
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = appOnSurface(),
                        modifier = Modifier.weight(1f)
                    )
                    // 右上角线框式 X 关闭图标（24dp 点击区域，16dp 图标）
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onDismissRequest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = appOnSurfaceVariant(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // === 正文区（weight=1f, fill=false）===
                // 内容少时：不强制撑满，保持弹窗紧凑（与原行为一致）
                // 内容多时：自动收缩到剩余空间，调用方传入的 verticalScroll 负责滚动
                // 这样底部按钮区始终固定可见、可点击
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    content()
                }

                // === 底部按钮区（右对齐）===
                // 保留调用方传入的 TextButton 原样，仅调整外层布局
                // 调用方负责按钮内文字颜色（确认按钮用白色，取消按钮用次级灰）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismissButton?.invoke()
                    if (dismissButton != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    confirmButton()
                }
            }
        }
    }
}
