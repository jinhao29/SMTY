package com.shangmentiyu.sportscoach.data.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [BackupCrypto] 单测：覆盖加解密往返、错误口令拒绝、篡改检测与向后兼容判定。
 *
 * 用低迭代次数（1000）跑测试，避免 600k 迭代拖慢 CI；迭代次数是参数，
 * 不影响逻辑正确性验证——生产默认值由 [BackupCrypto.PBKDF2_ITERATIONS] 保证。
 */
class BackupCryptoTest {

    private val fastIterations = 1_000

    private fun key(passphrase: String, salt: String) =
        BackupCrypto.deriveKey(passphrase, salt, fastIterations)

    @Test
    fun `加密后解密应还原原文`() {
        val salt = BackupCrypto.generateSaltHex()
        val k = key("my-secret-key", salt)
        val plain = "学员张三,13800000000,广州市天河区".toByteArray(Charsets.UTF_8)

        val encrypted = BackupCrypto.encrypt(plain, k)
        val decrypted = BackupCrypto.decrypt(encrypted, k)

        assertThat(decrypted).isNotNull()
        assertThat(String(decrypted!!, Charsets.UTF_8)).isEqualTo("学员张三,13800000000,广州市天河区")
    }

    @Test
    fun `加密结果不应包含明文`() {
        val salt = BackupCrypto.generateSaltHex()
        val k = key("pw", salt)
        val plain = "敏感手机号13800000000".toByteArray(Charsets.UTF_8)

        val encrypted = BackupCrypto.encrypt(plain, k)

        // 密文中不应出现明文的任何连续片段
        val haystack = String(encrypted, Charsets.ISO_8859_1)
        assertThat(haystack).doesNotContain("13800000000")
    }

    @Test
    fun `加密载荷应带魔数且可被识别`() {
        val salt = BackupCrypto.generateSaltHex()
        val encrypted = BackupCrypto.encrypt("x".toByteArray(), key("pw", salt))

        assertThat(BackupCrypto.isEncrypted(encrypted)).isTrue()
        // 明文不应被误判
        assertThat(BackupCrypto.isEncrypted("SQLite format 3\u0000".toByteArray())).isFalse()
    }

    @Test
    fun `相同盐下错误口令应解密失败`() {
        val salt = BackupCrypto.generateSaltHex()
        val encrypted = BackupCrypto.encrypt("payload".toByteArray(), key("correct", salt))

        val wrong = BackupCrypto.decrypt(encrypted, key("wrong", salt))

        assertThat(wrong).isNull()
    }

    @Test
    fun `不同盐派生密钥不同_应解密失败`() {
        val encrypted = BackupCrypto.encrypt("payload".toByteArray(), key("pw", BackupCrypto.generateSaltHex()))

        val wrong = BackupCrypto.decrypt(encrypted, key("pw", BackupCrypto.generateSaltHex()))

        assertThat(wrong).isNull()
    }

    @Test
    fun `篡改密文应被GCM校验拒绝`() {
        val salt = BackupCrypto.generateSaltHex()
        val k = key("pw", salt)
        val encrypted = BackupCrypto.encrypt("payload-需要完整性保护".toByteArray(), k)

        // 翻转最后一字节（GCM 标签区）
        val tampered = encrypted.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xFF).toByte()

        assertThat(BackupCrypto.decrypt(tampered, k)).isNull()
    }

    @Test
    fun `每次加密IV随机_相同明文密文不同`() {
        val salt = BackupCrypto.generateSaltHex()
        val k = key("pw", salt)
        val plain = "同样的内容".toByteArray()

        val a = BackupCrypto.encrypt(plain, k)
        val b = BackupCrypto.encrypt(plain, k)

        // IV 随机 => 密文必不相同（防止相同明文被识别）
        assertThat(a).isNotEqualTo(b)
        assertThat(BackupCrypto.decrypt(a, k)).isEqualTo(plain)
        assertThat(BackupCrypto.decrypt(b, k)).isEqualTo(plain)
    }

    @Test
    fun `生成盐应每次不同且为偶数长度Hex`() {
        val s1 = BackupCrypto.generateSaltHex()
        val s2 = BackupCrypto.generateSaltHex()

        assertThat(s1).isNotEqualTo(s2)
        assertThat(s1.length).isEqualTo(BackupCrypto.SALT_BYTES * 2)
        assertThat(s1).matches("[0-9a-f]+")
    }

    @Test
    fun `非加密载荷与过短载荷解密应返回null`() {
        val k = key("pw", BackupCrypto.generateSaltHex())

        assertThat(BackupCrypto.decrypt("not-encrypted-at-all".toByteArray(), k)).isNull()
        assertThat(BackupCrypto.decrypt(ByteArray(5), k)).isNull()
        assertThat(BackupCrypto.decrypt(ByteArray(0), k)).isNull()
    }
}
