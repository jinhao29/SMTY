package com.shangmentiyu.sportscoach.data.internal

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份载荷加密器（纯 JVM，无 Android 依赖，可独立单测）。
 *
 * 为什么需要它：
 * 备份 ZIP 内的 `sports_coach_db` 是明文 SQLite，包含学员姓名/电话/住址/家长信息。
 * 一旦备份文件从手机导出（微信发送、网盘、U 盘），即等于全量隐私泄露。
 * 签到照片虽已 AES-GCM 加密，但主数据库没有任何保护。
 *
 * 加密方案（信封式，兼顾安全性与跨端互通）：
 * - 口令来源：用户在「设置」中配置的私钥种子（与照片加密同一把，用户只需记一个）
 * - 口令 → [PBKDF2WithHmacSHA256]（随机盐 + 迭代）→ AES-256 密钥
 * - 载荷 → AES/GCM/NoPadding 加密，每文件独立随机 IV，GCM 标签提供完整性校验
 * - 随机盐与 IV 写入 ZIP 内的明文 manifest，供 PC 端解密时取用
 *
 * 与照片加密（[com.shangmentiyu.sportscoach.app.framework.PhotoCrypto]）的区别：
 * - 照片使用**固定盐**（跨设备复现密钥，无需携带盐）；本类使用**随机盐**并随包携带，
 *   对抗预计算攻击更强，代价是 manifest 必须与备份同存
 * - 照片格式固定为「魔数+IV+密文」单文件；本类为「盐+IV+密文」多文件信封
 *
 * 向后兼容：
 * - 未设置口令时不调用本类，备份格式与旧版完全一致（PC 端旧逻辑照常工作）
 * - 已加密的备份带 manifest 标记，PC 端据此识别并走解密路径
 * - 无 manifest 或 manifest 未标记加密 => 按明文旧格式处理
 *
 * 文件格式（加密后的单文件载荷）：
 * - 字节 0-15：[MAGIC] 4B + GCM IV 12B
 * - 字节 16-末尾：密文 + GCM 认证标签（16B）
 */
object BackupCrypto {

    /** 加密载荷魔数，用于在校验前快速判定文件是否为本类加密 */
    val MAGIC = byteArrayOf(
        'S'.code.toByte(), 'M'.code.toByte(), 'T'.code.toByte(), 'B'.code.toByte()
    )

    private const val IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val KEY_SIZE_BITS = 256
    private const val SALT_LENGTH = 16

    /**
     * PBKDF2 迭代次数（OWASP 推荐 ≥ 600000 for HMAC-SHA256，2023）。
     *
     * 注意：备份是低频操作（手动/自动 10min 防抖），单次派生 ~200-400ms 可接受，
     * 换取的是离线暴力破解成本的显著提高。此值与盐一同写入 manifest，
     * PC 端解密时读取该值，未来上调不影响旧备份可解密。
     */
    const val PBKDF2_ITERATIONS = 600_000

    /** 随机盐长度（字节），随 manifest 携带 */
    const val SALT_BYTES = SALT_LENGTH

    /**
     * 生成随机盐（Hex 字符串，便于写入 JSON manifest）。
     */
    fun generateSaltHex(): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    /**
     * 由口令与盐派生 AES-256 密钥。
     *
     * @param passphrase 用户私钥种子（非空）
     * @param saltHex 盐的 Hex 表示（须与加密时一致）
     * @param iterations PBKDF2 迭代次数（须与加密时一致）
     */
    fun deriveKey(passphrase: String, saltHex: String, iterations: Int = PBKDF2_ITERATIONS): SecretKey {
        val salt = hexToBytes(saltHex)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_SIZE_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    /**
     * 加密载荷。
     *
     * @param plain 明文字节
     * @param key 已派生密钥
     * @return [魔数 4B][IV 12B][密文+GCM标签]
     */
    fun encrypt(plain: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plain)

        val out = ByteArrayOutputStream(MAGIC.size + IV_LENGTH + ciphertext.size)
        out.write(MAGIC)
        out.write(iv)
        out.write(ciphertext)
        return out.toByteArray()
    }

    /**
     * 解密载荷（严格校验魔数，非本类加密的文件返回 null）。
     *
     * @return 明文；魔数不匹配、密钥错误或 GCM 校验失败返回 null
     */
    fun decrypt(encrypted: ByteArray, key: SecretKey): ByteArray? {
        if (encrypted.size < MAGIC.size + IV_LENGTH + 16) return null
        for (i in MAGIC.indices) {
            if (encrypted[i] != MAGIC[i]) return null
        }
        return try {
            val iv = encrypted.copyOfRange(MAGIC.size, MAGIC.size + IV_LENGTH)
            val ciphertext = encrypted.copyOfRange(MAGIC.size + IV_LENGTH, encrypted.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            // 密钥错误 / 数据被篡改 / 格式损坏统一返回 null，由调用方决定降级策略
            null
        }
    }

    /** 判断字节流是否为[本类]加密的载荷（仅查魔数，不解密） */
    fun isEncrypted(data: ByteArray): Boolean {
        if (data.size < MAGIC.size) return false
        for (i in MAGIC.indices) {
            if (data[i] != MAGIC[i]) return false
        }
        return true
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Hex 长度必须为偶数: $clean" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
