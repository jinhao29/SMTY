package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.launch

/**
 * 全屏可缩放图片查看器（UI 层）。
 *
 * 业务背景：
 * - 排课详情与学员记录中的签到照片原仅显示缩略图，无法放大查看细节
 * - 教练需查看签到照片中的学员姿态、动作细节或环境信息以核实上课情况
 * - 缩略图分辨率低，必须放大到原尺寸才能识别有用信息
 *
 * 交互设计：
 * - 触发：点击任意 [SafeAsyncImage]（Coil 加载的图片）→ 弹出全屏查看器
 * - 背景：半透明黑色 [Color.Black.copy(alpha = 0.85f)]，让图片浮现在屏幕上
 * - 缩放：双指捏合 zoom in/out，缩放范围 1.0x ~ 5.0x
 * - 拖拽：单指/双指平移 Pan，仅在 scale > 1.0 时生效；
 *   带硬边界回弹，图片不会滑出屏幕（offset 被 clamp 在 ±((scale-1) * size / 2)）
 * - 双击：第一次双击放大到 2x，第二次双击重置为 1:1 居中（带 220ms 动画过渡）
 * - 关闭：顶部右上角 × 按钮（最稳定的方式，避免与缩放手势冲突）
 *
 * 视觉风格：
 * - 沿用「珊瑚橙、无边框、极简」主题
 * - 关闭按钮采用圆形半透明背景 + 珊瑚橙 Icons.Filled.Close
 * - Loading 指示器使用珊瑚橙 CircularProgressIndicator
 * - 通过 [DialogProperties.usePlatformDefaultWidth = false] 让 Dialog 占满全屏
 *
 * 实现说明：
 * - 完全使用 Compose 原生手势 API（[detectTransformGestures] + [detectTapGestures]）
 *   不引入第三方库，避免依赖网络拉取失败（skydoves:zoomable 在 JitPack 401）
 * - 双击重置/放大使用 [Animatable] + [tween] 动画，过渡平滑
 *
 * @param model 图片数据源（ByteArray / File / String URL 都支持，与 SafeAsyncImage 一致）
 * @param onDismiss 关闭查看器回调
 */
@Composable
fun ZoomableImageViewer(
    model: Any?,
    onDismiss: () -> Unit
) {
    // === 全屏 Dialog：usePlatformDefaultWidth=false 让 Dialog 占满整个屏幕 ===
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false  // 自行处理点击空白关闭，避免与缩放手势冲突
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
        ) {
            // === 图片主体：原生手势缩放/平移/双击重置 ===
            ZoomableImageContent(
                model = model,
                modifier = Modifier.fillMaxSize()
            )

            // === 顶部右上角关闭按钮：圆形半透明背景 + 珊瑚橙 × 图标 ===
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 12.dp, top = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = appPrimary(),  // 珊瑚橙，与主题主色一致
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 图片主体内容：Coil 异步加载 + 自定义手势缩放/平移/双击重置。
 *
 * 手势处理：
 * - [detectTransformGestures]：双指缩放 + 单指/双指拖拽平移
 *   - 缩放范围：1.0x ~ 5.0x，超出范围会被 coerceIn 截断
 *   - 平移仅在 scale > 1.0 时生效，避免 1:1 状态下图片被拖动
 *   - 平移 offset 被 clamp 在 ±((scale-1) * boxSize / 2)，图片不会滑出屏幕
 * - [detectTapGestures]：双击放大/重置
 *   - scale == 1f 时双击 → 放大到 2x
 *   - scale > 1f 时双击 → 重置为 1:1 居中
 *   - 使用 [Animatable] + [tween] 动画平滑过渡（220ms）
 *
 * 状态管理：
 * - 拖拽期间用 mutableStateOf 同步赋值（避免协程延迟导致卡顿）
 * - 双击时进入动画模式，禁用 detectTransformGestures（避免动画过程中被手势打断）
 * - 动画结束后将 Animatable 的最终值同步回 mutableStateOf
 *
 * @param model 图片数据源
 * @param modifier 外部样式
 */
@Composable
private fun ZoomableImageContent(
    model: Any?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var imageState by remember(model) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }

    // 带 crossfade 的 ImageRequest，提升加载体验
    val request = remember(model) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    // === 缩放与平移状态 ===
    // 拖拽时同步赋值；双击动画时由 Animatable 接管
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    // 双击动画用的 Animatable
    val animScale = remember { Animatable(1f) }
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    var isAnimating by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it },
        contentAlignment = Alignment.Center
    ) {
        // 动画进行中时显示 Animatable 的值，否则显示同步状态
        val effectiveScale = if (isAnimating) animScale.value else scale
        val effectiveOffsetX = if (isAnimating) animOffsetX.value else offsetX
        val effectiveOffsetY = if (isAnimating) animOffsetY.value else offsetY

        AsyncImage(
            model = request,
            contentDescription = "全屏图片",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = effectiveScale,
                    scaleY = effectiveScale,
                    translationX = effectiveOffsetX,
                    translationY = effectiveOffsetY
                )
                // === 双指缩放 + 单指/双指拖拽平移 ===
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // 动画进行中时禁用手势，避免冲突
                        if (isAnimating) return@detectTransformGestures
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        if (newScale > 1f && boxSize != IntSize.Zero) {
                            // 边界回弹：clamp 在 ±((scale-1) * boxSize / 2)
                            val mx = boxSize.width * (newScale - 1f) / 2f
                            val my = boxSize.height * (newScale - 1f) / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-mx, mx)
                            offsetY = (offsetY + pan.y).coerceIn(-my, my)
                        } else {
                            // 缩放回 1f 时，重置 offset
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                // === 双击放大/重置（带 220ms 动画过渡）===
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (isAnimating) return@detectTapGestures
                            scope.launch {
                                isAnimating = true
                                // 从当前状态开始动画
                                animScale.snapTo(scale)
                                animOffsetX.snapTo(offsetX)
                                animOffsetY.snapTo(offsetY)
                                if (scale > 1f) {
                                    // 已放大 → 重置 1:1
                                    animScale.animateTo(1f, tween(220))
                                    animOffsetX.animateTo(0f, tween(220))
                                    animOffsetY.animateTo(0f, tween(220))
                                } else {
                                    // 1:1 → 放大到 2x
                                    animScale.animateTo(2f, tween(220))
                                }
                                // 动画结束后同步回 mutableStateOf
                                scale = animScale.value
                                offsetX = animOffsetX.value
                                offsetY = animOffsetY.value
                                isAnimating = false
                            }
                        }
                    )
                },
            onState = { imageState = it }
        )

        when (imageState) {
            is AsyncImagePainter.State.Loading -> {
                // === 加载中：珊瑚橙 CircularProgressIndicator ===
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = appPrimary(),  // 珊瑚橙
                    strokeWidth = 2.dp
                )
            }
            is AsyncImagePainter.State.Error,
            is AsyncImagePainter.State.Empty -> {
                // === 加载失败 / 数据为空：提示 + 关闭引导 ===
                // 不直接调用 onDismiss 自动关闭，避免用户来不及看到失败原因
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "图片加载失败",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "点击右上角 × 关闭",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
            is AsyncImagePainter.State.Success -> {
                // 加载成功：AsyncImage 自动渲染，无需额外处理
            }
        }
    }
}
