package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * UDP 心跳 HMAC 配对密钥存储（v1.0.6 安全加固，管理层）。
 *
 * 与 [DatabaseKeyManager] 同一套"Keystore 托管"模式：
 * - Keystore 生成 AES-256 密钥（别名 [KEY_ALIAS]），原始字节不可导出
 * - 配对码明文**永不落盘**，只存 Keystore 加密后的密文（AES-GCM）到 SharedPreferences
 * - 配对码由 PC 端同步面板生成，用户手动粘贴到手机端完成配对
 * - Keystore 被销毁（卸载/清数据）时密文解不开 → 视为未配对，自动降级无签名心跳
 *   （与数据库口令不同，配对码可随时在 PC 端重新生成，无需用户数据恢复）
 */
object PairingKeyStore {

    private const val TAG = "PairingKeyStore"

    /** Keystore 别名（与数据库密钥 [DatabaseKeyManager] 区分） */
    private const val KEY_ALIAS = "smty_pairing_key"

    /** 密文持久化文件（仅存密文，明文配对码不落盘） */
    private const val PREFS_NAME = "smty_pairing_key_store"
    private const val PREF_CIPHERTEXT = "pairing_key_ciphertext"
    private const val PREF_IV = "pairing_key_iv"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    /**
     * 读取已配对的配对码（base64）。
     *
     * 供 [com.shangmentiyu.sportscoach.app.framework.UdpDesktopDiscoveryService]
     * 在心跳接收线程中同步调用。未配对或密文解不开时返回 null（降级为无签名兼容）。
     */
    fun get(context: Context): String? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cipherHex = prefs.getString(PREF_CIPHERTEXT, null) ?: return null
        val ivHex = prefs.getString(PREF_IV, null) ?: return null
        return runCatching {
            val key = loadOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE, key,
                GCMParameterSpec(GCM_TAG_BITS, hexToBytes(ivHex))
            )
            String(cipher.doFinal(hexToBytes(cipherHex)), Charsets.UTF_8)
        }.getOrElse { e ->
            Log.w(TAG, "配对码解密失败（Keystore 失效？），按未配对处理：${e.message}")
            null
        }
    }

    /** 保存配对码（Keystore 加密后落盘）。成功返回 true。 */
    fun set(context: Context, pairingCode: String): Boolean = runCatching {
        val key = loadOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(pairingCode.trim().toByteArray(Charsets.UTF_8))
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_IV, bytesToHex(cipher.iv))
            .putString(PREF_CIPHERTEXT, bytesToHex(encrypted))
            .apply()
        true
    }.getOrElse { e ->
        Log.e(TAG, "配对码保存失败：${e.message}", e)
        false
    }

    /** 清除配对（重新配对入口）。 */
    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun loadOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
