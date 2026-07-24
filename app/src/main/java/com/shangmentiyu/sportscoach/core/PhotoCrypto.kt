package com.shangmentiyu.sportscoach.core

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * 签到照片加密存储处理器（数据层）。
 *
 * 职责：
 * - 使用 Jetpack Security 的 [EncryptedFile] 对签到照片进行 AES-GCM 加密存储
 * - 加密后的文件即使被 root 设备或备份软件提取，也无法直接解码为人脸图像
 * - 兼容旧版明文照片：[readPhoto] 会自动探测文件是否加密，
 *   若为旧版明文 jpg 则直接读取（保证升级前的数据仍可显示）
 *
 * 隐私合规（PIPL）：
 * - 签到照片含学员人脸生物特征，必须加密存储
 * - 文件名使用随机串，不含学员姓名等可识别信息
 * - 存储目录添加 .nomedia，防止系统相册扫描泄露
 *
 * 线程安全：[EncryptedFile] 每次调用新建实例，无共享状态。
 */
object PhotoCrypto {

    /** 加密照片存储目录名（位于 filesDir 下） */
    private const val PHOTO_DIR = "sign_photos"

    /**
     * 获取照片存储目录，并确保存在 .nomedia 防止系统相册扫描。
     * 幂等：目录已存在时直接返回，.nomedia 已存在时不重复写入。
     */
    fun ensurePhotoDir(context: Context): File {
        val dir = File(context.filesDir, PHOTO_DIR)
        if (!dir.exists()) dir.mkdirs()
        // .nomedia 文件存在时，系统相册和媒体扫描器会跳过该目录
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) nomedia.createNewFile()
        return dir
    }

    /**
     * 生成不含学员信息的随机照片文件名。
     * @return 形如 photo_a1b2c3d4e5f6.jpg 的随机名
     */
    fun generatePhotoName(): String =
        "photo_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}.enc.jpg"

    /**
     * 构建 [EncryptedFile] 实例。
     *
     * @param file 目标加密文件（不存在时由 EncryptedFile 在写入时创建）
     */
    private fun buildEncryptedFile(context: Context, file: File): EncryptedFile {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    /**
     * 加密写入照片。
     *
     * @param context 上下文
     * @param sourceFile 相机拍摄的原始明文 jpg 文件
     * @return 加密后的目标文件（路径回写到 Lesson.photoPath）
     */
    fun encryptFrom(context: Context, sourceFile: File): File {
        val dir = ensurePhotoDir(context)
        val target = File(dir, generatePhotoName())
        val encryptedFile = buildEncryptedFile(context, target)
        encryptedFile.openFileOutput().use { out ->
            sourceFile.inputStream().use { it.copyTo(out) }
        }
        return target
    }

    /**
     * 读取照片为字节数组，自动兼容加密与旧版明文格式。
     *
     * 兼容逻辑：
     * 1. 优先尝试用 [EncryptedFile] 解密读取
     * 2. 若解密失败（[IOException] / [GeneralSecurityException]），说明是旧版明文文件，直接 readBytes
     *
     * @return 照片字节内容，文件不存在或读取失败时返回 null
     */
    fun readPhoto(context: Context, file: File): ByteArray? {
        if (!file.exists()) return null
        // 先尝试加密读取
        try {
            val encryptedFile = buildEncryptedFile(context, file)
            return encryptedFile.openFileInput().use { it.readBytes() }
        } catch (_: IOException) {
            // 解密失败 → 可能是旧版明文
        } catch (_: GeneralSecurityException) {
            // 解密失败 → 可能是旧版明文
        } catch (_: Exception) {
            // 其他异常也兜底
        }
        // 兼容旧版明文
        return runCatching { file.readBytes() }.getOrNull()
    }

    /**
     * 删除照片文件（删除学员或重新拍摄时调用）。
     * 文件不存在视为成功。
     */
    fun deletePhoto(file: File): Boolean {
        if (!file.exists()) return true
        return runCatching { file.delete() }.getOrDefault(false)
    }
}
