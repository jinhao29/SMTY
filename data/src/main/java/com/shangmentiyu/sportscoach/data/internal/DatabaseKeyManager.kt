package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * SQLCipher 数据库密钥管理器（v1.0.5）。
 *
 * ## 口令形态：十六进制字符串（**不是**原始字节）
 * SQLCipher 的口令有两条入口，必须用同一种表示才能得到同一把密钥：
 * 1. Java API：`openDatabase(path, byte[] password, ...)` —— 对**字节**做 PBKDF2
 * 2. SQL 层：`ATTACH DATABASE 'f' AS a KEY 'passphrase'` —— 对**字符串的 UTF-8 字节**做 PBKDF2
 *
 * 若用原始随机字节当口令，经 SQL 字符串往返时会产生编码歧义（0x80 以上字节在
 * UTF-8 下变两字节），两端派生出**不同**的密钥 → 跨库导出/导入必然失败。
 * 因此统一用 **64 位十六进制小写字符串**（仅 [0-9a-f]，UTF-8 下 1:1，且无需 SQL 转义）。
 *
 * ## 密钥来源：Android Keystore（不可导出）
 * - Keystore 生成 AES-256 密钥（别名 [KEY_ALIAS]），原始字节由 TEE 持有，应用无法导出
 * - 用它对固定种子做 AES-GCM 加密，**只把密文**持久化到 SharedPreferences
 * - 解密 → SHA-256 → 16 进制 → 作为 SQLCipher 口令
 * - Keystore 不可用时**直接抛异常**，绝不静默降级为硬编码弱口令
 *
 * ## ⚠️ 换机/卸载重装的后果
 * Keystore 密钥随卸载/恢复出厂销毁，**本地加密库无法在新设备直接打开**。
 * 跨设备迁移唯一通道是应用备份（备份包内数据库以**用户备份口令**重新加密，
 * 与 Keystore 无关，见 [BackupManager.exportPortableDb]）。
 */
object DatabaseKeyManager {

    private const val TAG = "DatabaseKeyManager"

    /** Keystore 别名（固定，不随版本变化——变更别名会导致旧库无法打开） */
    private const val KEY_ALIAS = "smty_db_key"

    /** 口令密文的持久化文件（仅存密文，明文口令不落盘） */
    private const val PREFS_NAME = "smty_db_key_store"
    private const val PREF_CIPHERTEXT = "db_key_ciphertext"
    private const val PREF_IV = "db_key_iv"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    /**
     * 用于派生口令的固定明文种子。
     *
     * 这段明文**不是秘密**，它的价值在于"必须配合 Keystore 密钥"才能还原口令。
     * 真正的秘密是 Keystore 中不可导出的密钥本身。
     */
    private val SEED = "smty-sportscoach-db-passphrase-v1".toByteArray(Charsets.UTF_8)

    /** 口令来源候选（恢复备份时按顺序尝试） */
    enum class PassphraseCandidate {
        /** 本机 Keystore 口令 —— 同机恢复（最常见场景） */
        Device,

        /** 用户设置的备份口令 —— 换机恢复时，备份包内数据库用的是它 */
        Backup
    }

    /** Keystore 是否可用（供 UI 友好提示；**不做降级**） */
    fun isAvailable(): Boolean = runCatching { loadOrCreateKeystoreKey() }.isSuccess

    /**
     * 取本机数据库口令（首次调用则创建）。
     *
     * 必须在 Room 构建实例之前调用。
     *
     * @return 64 位十六进制口令字符串
     * @throws IllegalStateException Keystore 不可用或密文损坏时抛出，**不降级**
     */
    @Synchronized
    fun getOrCreatePassphrase(context: Context): String {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = loadOrCreateKeystoreKey()

        val cipherHex = prefs.getString(PREF_CIPHERTEXT, null)
        val ivHex = prefs.getString(PREF_IV, null)
        if (cipherHex != null && ivHex != null) {
            val existing = runCatching {
                decryptSeed(key, hexToBytes(cipherHex), hexToBytes(ivHex))
            }.getOrNull()
            if (existing != null) return seedToPassphrase(existing)

            // 密文存在但解不开：Keystore 密钥被销毁（换机/清数据/系统重置）。
            // 绝不能生成新口令覆盖——那会让已加密的旧库彻底打不开。
            val msg = "数据库密钥已失效（可能更换设备或清除过系统数据）。请从备份文件恢复数据。"
            Log.e(TAG, msg)
            throw IllegalStateException(msg)
        }

        // 首次：用 Keystore 密钥加密种子
        val seed = runCatching {
            val c = javax.crypto.Cipher.getInstance(TRANSFORMATION)
            c.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
            val cipher = c.doFinal(SEED)
            prefs.edit()
                .putString(PREF_IV, bytesToHex(c.iv))
                .putString(PREF_CIPHERTEXT, bytesToHex(cipher))
                .apply()
            decryptSeed(key, cipher, c.iv)
        }.getOrElse { e ->
            val msg = "数据库密钥初始化失败：${e.message ?: e.javaClass.simpleName}"
            Log.e(TAG, msg, e)
            throw IllegalStateException(msg, e)
        }
        Log.i(TAG, "数据库口令已初始化（Keystore 托管）")
        return seedToPassphrase(seed)
    }

    /**
     * 取口令的字节形态（供 SQLCipher Java API 使用）。
     *
     * 注意：传入的字节会被 SQLCipher 当作**口令**做 PBKDF2，
     * 与 SQL 层 `KEY '<同一字符串>'` 派生结果一致（见类注释）。
     */
    fun getOrCreatePassphraseBytes(context: Context): ByteArray =
        getOrCreatePassphrase(context).toByteArray(Charsets.UTF_8)

    /**
     * 解析指定来源的口令；不可用时返回 null（调用方继续尝试下一个候选）。
     */
    fun resolvePassphrase(context: Context, source: PassphraseCandidate): String? =
        when (source) {
            PassphraseCandidate.Device ->
                runCatching { getOrCreatePassphrase(context.applicationContext) }.getOrNull()

            PassphraseCandidate.Backup -> {
                val pass = runCatching {
                    com.shangmentiyu.sportscoach.data.repo.SettingsRepository(
                        context.applicationContext
                    ).getBackupPassphraseBlocking()
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
                // 与 BackupManager 中对备份内数据库加密时使用的派生规则一致
                backupPassToDbPassphrase(pass)
            }
        }

    /** 备份口令 → 数据库口令（64 位十六进制）。两端必须一致，勿单独改动。 */
    fun backupPassToDbPassphrase(backupPassphrase: String): String =
        bytesToHex(MessageDigest.getInstance("SHA-256")
            .digest(backupPassphrase.toByteArray(Charsets.UTF_8)))

    /**
     * 清空口令密文（仅用于"彻底弃用加密库、重新开始"的极端场景）。
     * 删除后旧加密库将永久无法打开 —— 不可逆，不在常规流程中调用。
     */
    fun reset(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private fun seedToPassphrase(seed: ByteArray): String =
        bytesToHex(MessageDigest.getInstance("SHA-256").digest(seed))

    private fun loadOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // 数据库要在开机后、解锁前后台都能打开：不要求用户认证
            .setUserAuthenticationRequired(false)

        // 同一密钥重复用于多次 GCM 加密（每次显式指定 IV）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setRandomizedEncryptionRequired(false)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun decryptSeed(key: SecretKey, cipherBytes: ByteArray, iv: ByteArray): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            key,
            javax.crypto.spec.GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return cipher.doFinal(cipherBytes)
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
