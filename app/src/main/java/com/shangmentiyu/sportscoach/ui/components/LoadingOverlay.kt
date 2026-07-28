package com.shangmentiyu.sportscoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * v37 任务5：全局加载蒙层组件。
 *
 * 设计要点：
 * - 占满半屏（高度自适应，最大 200dp），避免完全遮挡屏幕
 * - 深色半透明背景 + 圆角卡片，视觉柔和
 * - CircularProgressIndicator 旋转动画 + 加载文案
 * - 通过 [visible] 控制，与 ViewModel 的 isLoading 状态绑定
 * - 拦截外部点击事件（DialogProperties），防止用户多次点击触发重复操作
 *
 * 使用方式：
 * ```
 * val isLoading by vm.isLoading.collectAsState()
 * LoadingOverlay(visible = isLoading, message = "正在导出...")
 * ```
 *
 * @param visible 是否显示
 * @param message 加载文案（可选，默认"加载中..."）
 */
@Composable
fun LoadingOverlay(
    visible: Boolean,
    message: String = "加载中..."
) {
    if (!visible) return

    Dialog(
        onDismissRequest = { /* 拦截外部点击，防止误操作 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x80000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
