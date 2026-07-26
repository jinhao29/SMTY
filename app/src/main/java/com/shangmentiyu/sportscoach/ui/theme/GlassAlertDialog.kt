package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 蓝边包裹风格统一对话框组件。
 *
 * 设计要点（与主页 GlassCard 蓝边样式保持一致）：
 * - 蓝色描边（1dp，primary 色 15% 透明度）—— 与 GlassCard(glow=true) 一致
 * - 16dp 圆角（比卡片 10dp 略大，对话框视觉更柔和）
 * - 顶部 4dp 蓝紫渐变装饰条 —— 与 GlassCard 一致
 * - 白底卡片 + 无阴影 —— 与 GlassCard 视觉风格一致
 * - 标题使用 primary 色加粗
 * - 底部按钮区右对齐，水平排列
 *
 * 替代 Material3 默认 AlertDialog，使所有对话框视觉风格统一为
 * 主页 GlassCard 的蓝边包裹样式。
 *
 * @param onDismissRequest 点击对话框外部或返回键时的回调
 * @param title 标题文本（必填）
 * @param confirmButton 确认按钮（通常为 TextButton）
 * @param dismissButton 取消按钮（可选，通常为 TextButton）
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
    val containerColor = MaterialTheme.colorScheme.surface

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
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            shadowElevation = 4.dp
        ) {
            Column {
                // 顶部 4dp 蓝紫渐变装饰条（与 GlassCard 一致）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(VitalBlueStart, VitalPurpleEnd)
                            )
                        )
                )

                // 标题区
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
                )

                // 正文区
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    content()
                }

                // 底部按钮区（右对齐）
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
