package com.shangmentiyu.sportscoach.ui.lesson

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.shangmentiyu.sportscoach.R
import com.shangmentiyu.sportscoach.core.PhotoCrypto
import com.shangmentiyu.sportscoach.ui.theme.GlassCard
import com.shangmentiyu.sportscoach.ui.theme.PrimaryButton
import com.shangmentiyu.sportscoach.ui.theme.SafeAsyncImage
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import java.io.File

/**
 * 拍照签到/签退卡片（可复用）。
 *
 * 功能：
 * - 显示当前已拍摄的照片（缩略图）
 * - 提供"拍摄"按钮调用系统相机
 * - 提供"重新拍摄"按钮替换照片
 * - 照片保存至应用内部存储 filesDir/sign_photos/，路径回写至 Lesson.photoPath / signOutPhotoPath
 *
 * 隐私合规（PIPL）：
 * - 签到/签退照片含学员人脸生物特征，使用 [PhotoCrypto] 进行 AES-GCM 加密存储
 * - 文件名随机化，不含学员姓名等可识别信息
 * - 存储目录含 .nomedia，防止系统相册扫描泄露
 * - 显示时从加密文件解密为 ByteArray 加载到内存，不写回明文文件
 *
 * 权限：CAMERA 为危险权限，点击拍照按钮时先检查并请求运行时权限，
 * 授权后才启动系统相机，避免未授权直接 launch 导致 SecurityException 闪退。
 *
 * === v25 优化3 ===：所有界面文案统一迁移到 strings.xml，
 * 通过 [stringResource] 引用，支持多语言扩展与代码精简。
 *
 * @param photoPath 当前照片路径（空=未拍照）
 * @param onPhotoCaptured 照片拍摄成功回调，参数为新照片的绝对路径
 * @param title 卡片标题（默认调用 stringResource(R.string.photo_pre_class_title)）
 * @param emptyHint 未拍照时的占位提示文案
 * @param captureBtnText 拍摄按钮文案
 */
@Composable
fun SignPhotoCard(
    photoPath: String,
    onPhotoCaptured: (String) -> Unit,
    title: String = stringResource(R.string.photo_pre_class_title),
    emptyHint: String = stringResource(R.string.photo_empty_hint_pre),
    captureBtnText: String = stringResource(R.string.photo_capture_btn_pre)
) {
    val context = LocalContext.current
    // 暂存拍照目标：Uri 用于启动系统相机，临时明文文件路径用于加密前回写
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingTmpPath by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    // 相机启动器：拍摄后系统会把照片写入 pendingUri 指向的临时文件
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // 拍照成功：将临时明文文件加密保存到正式路径，删除临时文件
            pendingTmpPath?.let { tmpPath ->
                val tmpFile = File(tmpPath)
                if (tmpFile.exists()) {
                    // 加密保存：PhotoCrypto 内部确保目录存在 + .nomedia
                    val encrypted = PhotoCrypto.encryptFrom(context, tmpFile)
                    // 删除临时明文文件，避免明文残留
                    tmpFile.delete()
                    onPhotoCaptured(encrypted.absolutePath)
                }
            }
        }
        pendingUri = null
        pendingTmpPath = null
    }

    // 相机权限请求器：用户授权后启动相机，拒绝则置标记显示提示
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            launchCamera(context, cameraLauncher) { uri, path ->
                pendingUri = uri
                pendingTmpPath = path
            }
        } else {
            permissionDenied = true
        }
    }

    /**
     * 统一拍照入口：先检查 CAMERA 权限，
     * 已授权直接启动相机，未授权则发起请求。
     */
    fun startCapture() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCamera(context, cameraLauncher) { uri, path ->
                pendingUri = uri
                pendingTmpPath = path
            }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 异步解密加载照片字节：避免阻塞主线程
    // produceState 会随 photoPath 变化重新启动协程
    val photoBytes by produceState<ByteArray?>(initialValue = null, photoPath) {
        if (photoPath.isBlank()) {
            value = null
            return@produceState
        }
        val file = File(photoPath)
        if (!file.exists()) {
            value = null
            return@produceState
        }
        // 在 IO 线程解密读取
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            PhotoCrypto.readPhoto(context, file)
        }
    }

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.height(8.dp))

        if (photoPath.isNotBlank() && File(photoPath).exists()) {
            // 已有照片：显示缩略图（从加密字节加载）+ 重新拍摄按钮
            if (photoBytes != null) {
                // === v28 优化5：使用 SafeAsyncImage 提供 Coil 容错兜底 ===
                // === v34：启用全屏预览，教练可双指缩放查看签到照片细节 ===
                // 防止照片损坏或解密失败导致 Compose 渲染崩溃
                SafeAsyncImage(
                    model = photoBytes,
                    contentDescription = "签到照片",
                    contentScale = ContentScale.Crop,
                    cornerRadius = 8.dp,
                    enableZoomPreview = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
            } else {
                // 解密中占位
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.photo_load_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.photo_recorded_encrypted),
                    style = MaterialTheme.typography.bodySmall,
                    color = ScoreExcellent,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { startCapture() }) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.photo_recapture), color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            // 无照片：占位区 + 拍摄按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        emptyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            if (permissionDenied) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.photo_permission_denied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = captureBtnText,
                onClick = { startCapture() },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Outlined.CameraAlt
            )
        }
    }
}

/**
 * 启动系统相机：在 cacheDir 创建临时明文文件 → 获取 FileProvider Uri → 启动 TakePicture。
 *
 * 拍照成功后由上层 [SignPhotoCard] 的回调将临时文件加密保存到正式路径并删除临时文件。
 *
 * 调用方必须先确保已获得 CAMERA 运行时权限，否则系统相机会抛 SecurityException。
 *
 * @param onReady 创建 Uri 和文件路径后回调（Uri 用于 launcher.launch，路径用于加密前回写）
 */
private fun launchCamera(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Uri>,
    onReady: (uri: Uri, filePath: String) -> Unit
) {
    // 临时明文文件放在 cacheDir，拍照成功后会被加密并删除
    val tmpDir = File(context.cacheDir, "sign_photos_tmp").apply { if (!exists()) mkdirs() }
    val file = File(tmpDir, "tmp_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
    // 同时回传 Uri 与文件绝对路径
    onReady(uri, file.absolutePath)
    launcher.launch(uri)
}
