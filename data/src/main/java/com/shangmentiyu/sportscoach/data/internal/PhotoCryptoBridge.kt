package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import java.io.File

/**
 * 照片加解密桥（v1.0.4）。
 *
 * 为什么需要它：
 * 签到照片的加解密实现（[com.shangmentiyu.sportscoach.app.framework.PhotoCrypto]）
 * 位于 `:app` 模块，而备份打包逻辑 [BackupManager] 位于 `:data` 模块——
 * 依赖方向是 `:app → :data`，`:data` **不能**反向引用 `:app`。
 *
 * 解法：在 `:data` 侧定义这个桥接口，由 `:app` 在启动时注入真实实现。
 * 这样 [BackupManager] 在打包备份时就能解出照片明文（用于换机迁移），
 * 同时保持模块依赖方向不变。
 *
 * 未注入时的降级：所有方法返回 null，[BackupManager] 会自动回退到
 * "照片原样打包"的旧行为——不会崩溃，也不丢照片，只是换机后无法解密。
 */
object PhotoCryptoBridge {

    /**
     * 照片加解密能力，由 `:app` 模块注入。
     *
     * @see install
     */
    interface Impl {
        /**
         * 解密照片文件为明文字节（用于备份时重新加密为可跨机格式）。
         *
         * @param file 本机存储的照片文件（EncryptedFile / 种子加密 / 明文三种格式均可）
         * @return 明文 jpg 字节；文件不存在或解密失败返回 null
         */
        fun decrypt(context: Context, file: File): ByteArray?

        /**
         * 用**本机密钥**加密明文照片字节（用于恢复时落盘）。
         *
         * ⚠️ 明文绝不写盘，与本 App 对生物特征数据的处理契约一致。
         *
         * @param plain 明文 jpg 字节
         * @return 本机加密格式的字节；失败返回 null
         */
        fun encryptToDeviceFormat(context: Context, plain: ByteArray): ByteArray?
    }

    @Volatile
    private var impl: Impl? = null

    /** 由 `:app` 在 Application.onCreate 中注入实现（幂等） */
    fun install(impl: Impl) {
        this.impl = impl
    }

    /** 是否已注入真实实现（未注入时 [BackupManager] 走旧路径） */
    val isInstalled: Boolean get() = impl != null

    /** 解密照片为明文；未注入实现或解密失败返回 null */
    fun decryptForBackup(context: Context, file: File): ByteArray? =
        impl?.decrypt(context, file)

    /** 用本机密钥加密明文照片；未注入实现或失败返回 null */
    fun encryptForDevice(context: Context, plain: ByteArray): ByteArray? =
        impl?.encryptToDeviceFormat(context, plain)
}
