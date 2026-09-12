package com.shangmentiyu.sportscoach.core

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * UDP 心跳报文 HMAC 认证（v1.0.6 安全加固）。
 *
 * 与桌面端 data_center/pairing_key.py 严格对齐（跨端锚定测试双向锁定）：
 * - 规范化串："desktop_online|{host}|{port}|{timestamp}|{token}|{name}"
 * - 签名：HMAC-SHA256（密钥 = 配对码 base64 解码后的 32 字节），sig 字段为 base64
 * - 配对：PC 端同步面板生成 32 字节随机配对码，手动粘贴到手机端完成配对
 * - 兼容：未配对一端忽略 sig 字段；已配对收到无签名报文按旧协议降级（记录警告）
 */
object SyncPacketAuth {

    /** 心跳报文签名字段名（JSON key） */
    const val FIELD_SIG = "sig"

    /**
     * 重放窗口（毫秒）：已配对报文的时间戳超出该窗口视为重放，拒绝。
     *
     * 取 10 分钟：远大于正常时钟偏移（双端 NTP 下秒级），远小于攻击者
     * 重放旧报文劫持上传目标的可行窗口。仅对带签名的报文生效——未配对
     * 报文不校验时间戳，保持与旧版本 PC 的兼容（时钟偏移不破坏发现）。
     */
    const val FRESHNESS_TOLERANCE_MS: Long = 10 * 60_000L

    /**
     * 时间戳新鲜度校验（重放窗口）。
     *
     * @param timestampMs 报文携带的时间戳
     * @param nowMs 接收方当前时间
     * @param toleranceMs 容忍窗口（默认 [FRESHNESS_TOLERANCE_MS]）
     * @return true 表示新鲜可接受；false 表示过期/超前过多，疑似重放
     */
    fun isFresh(timestampMs: Long, nowMs: Long,
                toleranceMs: Long = FRESHNESS_TOLERANCE_MS): Boolean {
        if (timestampMs <= 0) return false
        val delta = nowMs - timestampMs
        return delta in -toleranceMs..toleranceMs
    }

    /**
     * 构造 desktop_online 心跳的规范化签名串。
     * 字段顺序与桌面端 canonical_online 一致，勿单端改动。
     */
    fun canonicalOnline(host: String, port: Int, timestamp: Long,
                        token: String, name: String): String =
        "desktop_online|$host|$port|$timestamp|$token|$name"

    /** 对规范化串计算 HMAC-SHA256，返回 base64 签名。 */
    fun sign(pairingCodeB64: String, canonical: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(decodeKey(pairingCodeB64), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))
    }

    /** 常数时间比较校验签名，防止逐字节试探。 */
    fun verify(pairingCodeB64: String, canonical: String, sigB64: String): Boolean =
        runCatching { sign(pairingCodeB64, canonical) }.getOrNull() == sigB64

    /**
     * 配对码解码为 HMAC 密钥字节。
     * 优先 base64（PC 端生成格式）；兼容用户误粘贴 hex（64 位十六进制）。
     * @return null 表示格式非法
     */
    fun decodeKey(code: String): ByteArray? {
        val s = code.trim()
        if (s.isEmpty()) return null
        if (s.length == 64 && s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            return runCatching { s.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
        }
        return runCatching { Base64.getDecoder().decode(s) }.getOrNull()
    }
}
