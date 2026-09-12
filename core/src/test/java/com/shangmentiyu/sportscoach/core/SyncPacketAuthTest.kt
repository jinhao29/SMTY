package com.shangmentiyu.sportscoach.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * UDP 心跳 HMAC 认证测试（v1.0.6）。
 *
 * 锚定向量与桌面端 test_sync_receiver.py::test_beacon_hmac_signature 共用，
 * 锁定双端规范化串与 HMAC-SHA256 实现跨端一致 —— 任一端改动协议，测试即失败。
 */
class SyncPacketAuthTest {

    companion object {
        // 跨端锚定向量（勿改：桌面端测试同步锁定）
        private const val KEY_B64 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
        private const val CANONICAL =
            "desktop_online|192.168.1.100|8765|1726118400000|pair_token|PC"
        private const val EXPECTED_SIG = "vPntvArsTdXF7aN4U7c1mjAfTyaqdV01ywn2lyjSl2Q="
    }

    @Test
    fun `签名与跨端锚定向量一致`() {
        assertThat(SyncPacketAuth.sign(KEY_B64, CANONICAL)).isEqualTo(EXPECTED_SIG)
        assertThat(SyncPacketAuth.verify(KEY_B64, CANONICAL, EXPECTED_SIG)).isTrue()
    }

    @Test
    fun `规范化串逐字段锚定`() {
        assertThat(
            SyncPacketAuth.canonicalOnline(
                "192.168.1.100", 8765, 1726118400000L, "pair_token", "PC"
            )
        ).isEqualTo(CANONICAL)
    }

    @Test
    fun `篡改任一字段验证失败`() {
        val tampered = "desktop_online|10.0.0.1|8765|1726118400000|pair_token|PC"
        assertThat(SyncPacketAuth.verify(KEY_B64, tampered, EXPECTED_SIG)).isFalse()

        val wrongKey = "AQECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
        assertThat(SyncPacketAuth.verify(wrongKey, CANONICAL, EXPECTED_SIG)).isFalse()
    }

    @Test
    fun `非法配对码验证失败不崩溃`() {
        assertThat(SyncPacketAuth.verify("!!!非法!!!", CANONICAL, EXPECTED_SIG)).isFalse()
        assertThat(SyncPacketAuth.verify("", CANONICAL, EXPECTED_SIG)).isFalse()
        assertThat(SyncPacketAuth.decodeKey("!!!")).isNull()
    }

    @Test
    fun `配对码兼容 hex 格式`() {
        val bytes = SyncPacketAuth.decodeKey("00".repeat(32))
        assertThat(bytes).isNotNull()
        assertThat(bytes!!.size).isEqualTo(32)
    }
}
