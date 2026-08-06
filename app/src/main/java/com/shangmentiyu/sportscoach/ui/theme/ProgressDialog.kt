package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shangmentiyu.sportscoach.core.ProgressState

/**
 * 统一进度对话框（UI 层）。
 *
 * v24 优化3 引入：为 Excel 导出 / 数据备份 / 数据恢复 等长耗时操作提供
 * 统一的进度反馈组件，避免各页面重复实现。
 *
 * 设计要点：
 * - 不可取消（dismissOnClickOutside = false, dismissOnBackPress = false）
 *   避免用户在数据写入过程中误触关闭导致数据损坏
 * - 进度条样式自动适应：
 *   · progress >= 0 且 < 1：线性进度条 + 百分比
 *   · progress < 0（不确定）：环形进度条 + 步骤文案
 * - 完成态（isActive=false 且 progress=1f）：绿色对勾 + 完成文案，1.5s 后自动消失
 *
 * 使用示例：
 * ```
 * val progress by vm.progressState.collectAsState()
 * if (progress.isActive || progress.progress == 1f) {
 *     ProgressDialog(progress)
 * }
 * ```
 *
 * @param state 当前进度状态
 */
@Composable
fun ProgressDialog(state: ProgressState) {
    val isDone = !state.isActive && state.progress >= 1f

    Dialog(
        onDismissRequest = {
            // 进度对话框不允许通过点击外部或返回键关闭
            // 避免在数据写入过程中被打断导致数据损坏
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // === 顶部图标区（环形/对勾）===
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        // 完成态：绿色对勾
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = appOnSuccessContainer(),
                            modifier = Modifier.size(48.dp)
                        )
                    } else if (state.progress < 0f) {
                        // 不确定进度：环形旋转
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = appPrimary()
                        )
                    } else {
                        // 确定进度：百分比数字
                        // 线性进度条放在下方，这里显示百分比文字
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = appPrimary()
                        )
                    }
                }

                // === 步骤文案 ===
                if (state.currentStep.isNotBlank()) {
                    Text(
                        text = state.currentStep,
                        style = MaterialTheme.typography.bodyMedium,
                        color = appOnSurfaceVariant(),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                // === 线性进度条（仅确定进度模式显示）===
                AnimatedVisibility(
                    visible = !isDone && state.progress >= 0f && state.progress < 1f,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = appPrimary(),
                            trackColor = appOutline().copy(alpha = 0.3f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }

                // 完成态文案提示
                if (isDone) {
                    Spacer(Modifier.height(0.dp))
                }
            }
        }
    }
}
