package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 悬浮避让型 SnackbarHost：自动避开底部悬浮导航栏。
 *
 * === 修复：悬浮导航栏遮挡全局弹窗 ===
 *
 * 业务背景：
 * - SportsApp 使用 Box 覆盖布局实现悬浮导航（非 Scaffold bottomBar），
 *   Scaffold 不知道底部有悬浮元素，默认 Snackbar 会钉在屏幕最底部，
 *   被白色胶囊导航栏 + 中间 FAB（约 140-160dp 高度）完全遮挡。
 *
 * 解决方案：
 * - 给 SnackbarHost 强制加底部 padding，刚好避开悬浮导航栏
 *   （悬浮导航栏含 FAB 实际占位约 70dp + 设备导航栏 ~48dp，100dp 已足够不重叠且不过度上移）
 * - 统一深黑色背景 #1C1C1E（与 [DarkToastCard] 保持视觉一致）
 * - 统一 12dp 大圆角，白色文字
 *
 * === 终极修复：Box 覆盖堆叠 ===
 * - 当 SnackbarHost 放在 Scaffold 的 snackbarHost slot 内时，Scaffold 的渲染层级
 *   低于外层 Box 中的 FloatingBottomBar，会被遮挡。
 * - 解决方案：将 SnackbarHost 移到 Scaffold 外层的 Box 中，使用 align(BottomCenter)，
 *   并通过 [bottomPadding] 参数（如 160.dp）让弹窗位于悬浮导航栏上方。
 *
 * 使用场景：
 * - 所有屏幕级 Scaffold 的 snackbarHost 参数应使用本组件，
 *   替代直接使用 `SnackbarHost(hostState)`，确保弹窗不被悬浮导航栏遮挡。
 * - 当需要把 SnackbarHost 放在 Scaffold 外层 Box 时，传入更大的 [bottomPadding]。
 *
 * @param hostState Snackbar 状态容器
 * @param modifier 外部修饰符（如 ScoreInputTab 中需要 align(Alignment.BottomCenter)）
 * @param bottomPadding 底部避让 padding（默认 100.dp，Box 覆盖法场景传 160.dp）
 */
@Composable
fun FloatingSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 100.dp
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(bottom = bottomPadding)
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = NightGlassSurface,
            contentColor = Color.White,
            shape = RoundedCornerShape(12.dp)
        )
    }
}
