package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest

/**
 * === v28 优化5：Coil 签到照片容错兜底 Composable ===
 *
 * 业务背景：
 * - 课程记录里加载签到照片用到了 Coil 的 AsyncImage
 * - 如果用户删除了手机的 filesDir/SignPhotos，或者照片损坏，
 *   AsyncImage 默认显示空白，会让用户疑惑"是不是 App 卡了"
 * - 极端情况下解码失败可能抛异常，导致 Compose 渲染崩溃
 *
 * 设计目标：
 * - 加载中：显示浅色背景 + 加载指示器
 * - 加载失败（ByteArray 损坏 / Bitmap 解码异常）：显示浅色背景 + ImageNotSupported 图标
 * - 数据为空（fallback）：与加载失败同样处理，保证 UI 一致
 * - 不改变任何视觉风格：浅色背景沿用 appOnSurface().copy(alpha = 0.05f)
 *
 * === v34 新增：全屏图片查看器支持 ===
 * - [enableZoomPreview]=true 时，点击缩略图弹出 [ZoomableImageViewer] 全屏查看器
 * - 支持 双指捏合缩放 / 单指拖拽 / 双击重置 / 顶部 × 关闭
 * - 默认 false：保持向后兼容，仅在显式启用时绑定点击事件
 * - 点击事件仅在加载成功状态下触发，避免点击"加载失败"占位也弹查看器
 *
 * 用法：
 * ```kotlin
 * // 不启用全屏预览（向后兼容）
 * SafeAsyncImage(
 *     model = photoBytes,
 *     contentDescription = "签到照片",
 *     modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(10.dp))
 * )
 *
 * // 启用全屏预览
 * SafeAsyncImage(
 *     model = photoBytes,
 *     contentDescription = "签到照片",
 *     enableZoomPreview = true,
 *     modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f)
 * )
 * ```
 *
 * 替代了原本直接使用 `AsyncImage(model = photoBytes, ...)` 的写法，
 * 强制增加 .error/.fallback 兜底，确保哪怕没有照片，界面依然保持干净，不会崩溃。
 *
 * @param model 图片数据源（ByteArray / File / String URL / ImageRequest 都支持）
 * @param contentDescription 无障碍描述
 * @param modifier 外部样式（尺寸、圆角、剪裁等）
 * @param contentScale 内容缩放模式，默认 [ContentScale.Crop]
 * @param cornerRadius 圆角半径，默认 10dp（与 IOSCard 保持一致）
 * @param enableZoomPreview 是否启用点击全屏预览，默认 false 保持向后兼容
 */
@Composable
fun SafeAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: androidx.compose.ui.unit.Dp = 10.dp,
    enableZoomPreview: Boolean = false
) {
    val context = LocalContext.current
    var imageState by remember(model) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    // === v34：全屏预览状态 ===
    var showFullScreen by remember { mutableStateOf(false) }

    // 构造带 crossfade 的 ImageRequest，提升加载体验
    // v37 任务3：强制限制图片大小 + 启用内存/磁盘缓存
    //   - size(200, 200)：避免加载原始大图占用过多内存（签到照片通常 1080p+）
    //   - memoryCachePolicy(ENABLED)：解码后 Bitmap 缓存到内存，滑动列表复用
    //   - diskCachePolicy(ENABLED)：磁盘缓存原始字节，避免重复解密 AES-GCM
    val request = remember(model) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .size(200, 200)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .build()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                // 仅在启用预览 + 加载成功 + 用户主动开启时附加点击事件
                // 避免给禁用预览的旧调用方增加不必要的点击响应
                if (enableZoomPreview && imageState is AsyncImagePainter.State.Success) {
                    Modifier.clickable { showFullScreen = true }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            onState = { imageState = it }
        )

        when (imageState) {
            is AsyncImagePainter.State.Loading -> {
                // 加载中：浅色背景 + 小尺寸进度指示器
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appOnSurface().copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            is AsyncImagePainter.State.Error,
            is AsyncImagePainter.State.Empty -> {
                // === v28 优化5：兜底显示 ImageNotSupported 图标 ===
                // 触发场景：
                // 1. photoBytes 为 null / 空 ByteArray
                // 2. ByteArray 损坏，BitmapFactory.decodeByteArray 返回 null
                // 3. 内存不足 OOM
                // 4. 用户删除了 filesDir/SignPhotos 目录
                // 显示一个统一带浅色背景的 ImageNotSupported 图标
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appOnSurface().copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ImageNotSupported,
                        contentDescription = "照片加载失败",
                        tint = appOnSurface().copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            is AsyncImagePainter.State.Success -> {
                // 加载成功：AsyncImage 自动渲染，无需额外处理
            }
        }
    }

    // === v34：全屏预览弹窗 ===
    if (showFullScreen) {
        ZoomableImageViewer(
            model = model,
            onDismiss = { showFullScreen = false }
        )
    }
}
