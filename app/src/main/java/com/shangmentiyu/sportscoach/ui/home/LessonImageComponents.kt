package com.shangmentiyu.sportscoach.ui.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 课后反馈训练内容图片组件集（v53 从 PostClassTab.kt 拆出，逻辑逐字保留）。
 *
 * 包含：图片导入区（PhotoPicker 多选）、缩略图（点击放大/可删除）、
 * 以及 Uri 复制与图片解码两个文件级工具函数。
 */

/**
 * 课后反馈图片导入区：支持从相册多选图片，用于反馈给家长。
 *
 * 功能：
 * - 点击"添加图片"按钮打开系统 PhotoPicker，支持多选
 * - 选中的图片复制到应用内部存储（filesDir/lesson_images/），持久化保存
 * - 以网格缩略图展示已导入图片，右上角带删除按钮
 *
 * @param images 图片路径列表（应用内部存储绝对路径）
 * @param onAddImages 新增图片路径列表回调
 * @param onRemoveImage 删除指定索引图片回调
 */
@Composable
internal fun LessonImagesImportSection(
    images: List<String>,
    onAddImages: (List<String>) -> Unit,
    onRemoveImage: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // PhotoPicker：支持多选图片
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val savedPaths = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri -> copyUriToInternal(context, uri) }
                }
                if (savedPaths.isNotEmpty()) {
                    onAddImages(savedPaths)
                }
            }
        }
    }

    Text("训练内容图片（反馈给家长）",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))

    // 图片网格（每行2张）
    if (images.isNotEmpty()) {
        images.chunked(2).forEachIndexed { rowIdx, rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                rowImages.forEachIndexed { colIdx, path ->
                    val absoluteIdx = rowIdx * 2 + colIdx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(appGroupedBackground())
                    ) {
                        Image(
                            bitmap = loadImageBitmapFromFile(path),
                            contentDescription = "训练内容图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // 右上角删除按钮
                        IconButton(
                            onClick = { onRemoveImage(absoluteIdx) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(appSurface().copy(alpha = 0.8f))
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "删除图片",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                // 不足2张时填充空白保持对齐
                if (rowImages.size < 2) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    } else {
        Text("暂无图片，可添加课堂训练照片反馈给家长",
            style = MaterialTheme.typography.bodySmall,
            color = appOutline())
        Spacer(Modifier.height(Spacing.sm))
    }

    // 添加图片按钮（浅橙填充胶囊，呼应"反馈给家长"语义）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .clickable {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.AddPhotoAlternate,
            contentDescription = "添加图片",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            "添加图片（支持多选）",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 课后反馈图片缩略图：从内部存储路径加载显示。
 * 点击可放大查看（全屏 Dialog），展开编辑时可删除。
 */
@Composable
internal fun LessonImageThumb(
    path: String,
    canDelete: Boolean = false,
    onDelete: () -> Unit = {}
) {
    var fullscreen by remember { mutableStateOf(false) }
    val bitmap = remember(path) {
        try {
            val file = File(path)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (_: Exception) { null }
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .clickable { fullscreen = true }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "训练内容图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // 删除按钮（仅展开编辑时显示）
        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(appSurface().copy(alpha = 0.85f))
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "删除图片",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }

    // 全屏查看：复用 PreClassTab 的 ZoomableImageDialog（如有则直接调用）
    if (fullscreen && bitmap != null) {
        ZoomableImageDialog(bitmap = bitmap, onDismiss = { fullscreen = false })
    }
}

/**
 * 将 Uri 图片复制到应用内部存储目录（filesDir/lesson_images/）。
 * 返回保存后的文件绝对路径，失败返回 null。
 */
private fun copyUriToInternal(context: Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "lesson_images").apply { if (!exists()) mkdirs() }
        val fileName = "img_${System.currentTimeMillis()}_${uri.lastPathSegment?.hashCode() ?: 0}.jpg"
        val destFile = File(dir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        destFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

/**
 * 从文件路径加载 ImageBitmap（同步，适用于小图缩略图）。
 * 文件不存在或解码失败返回空透明图。
 */
private fun loadImageBitmapFromFile(path: String): ImageBitmap {
    return try {
        val file = File(path)
        if (file.exists()) {
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            bitmap?.asImageBitmap() ?: ImageBitmap(1, 1)
        } else {
            ImageBitmap(1, 1)
        }
    } catch (e: Exception) {
        ImageBitmap(1, 1)
    }
}
