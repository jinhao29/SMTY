package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 同步通道 TLS 信任（管理层）：TOFU 指纹信任 + 先 HTTPS 后明文的连接路由。
 *
 * 与 PC 端 `data_center/tls_cert.py` 成对实现（勿单端改动指纹算法）：
 * - 指纹 = SHA-256(证书 DER) 的 Base64 标准编码（44 字符，含 padding）
 * - 自签证书无可信 CA，hostname 校验关闭，以「首连固定指纹」替代（安全边界）：
 *   首次 TLS 握手成功 → 存指纹放行；之后指纹一致放行；不一致抛 [CertificateException]
 *   （证书更换或中间人），由设置页「清除信任」重置
 * - 连接路由按进程内记忆选路，心跳/HTTP 协议/token 鉴权全部不变，只换传输层
 */
object SyncTlsTrust {

    private const val TAG = "SyncTlsTrust"

    /** 指纹持久化（跟随 [PairingKeyStore] 的 SharedPreferences 模式；指纹非机密，明文存储） */
    private const val PREFS_NAME = "smty_sync_tls_trust"
    private const val PREF_FINGERPRINT = "sync_tls_fingerprint"

    /**
     * ponytail: 进程内路由记忆 TTL 10 分钟 —— PC 切换 HTTPS 开关后最迟 10 分钟重新探测
     * 选路，避免单边开关切换后永久走错传输层（对齐 LanSyncManager.staleHost 的 TTL 模式）。
     * 升级路径：心跳报文增加传输层字段（协议变更，暂不做）。
     */
    private const val ROUTE_MEMORY_TTL_MS = 10 * 60_000L

    /** 「明文可用」路由记忆：host:port → 记忆过期时刻（直接走 http，省一次 TLS 探测失败） */
    private val plaintextOkUntil = ConcurrentHashMap<String, Long>()

    /** 「TLS 可用」路由记忆：TLS 握手成功后记录，期间直接走 https（不探测） */
    private val tlsOkUntil = ConcurrentHashMap<String, Long>()

    // ============================================================
    // TOFU 指纹信任存取
    // ============================================================

    /** 读取已信任的服务端证书指纹；未信任返回 null。 */
    fun trustedFingerprint(context: Context): String? =
        prefs(context).getString(PREF_FINGERPRINT, null)

    /** 保存信任指纹（TOFU 首连成功或人工重新信任入口）。 */
    fun trustFingerprint(context: Context, fingerprint: String) {
        prefs(context).edit().putString(PREF_FINGERPRINT, fingerprint).apply()
    }

    /** 清除信任（证书更换后重新建立 TOFU 的入口）。 */
    fun clearFingerprint(context: Context) {
        prefs(context).edit().remove(PREF_FINGERPRINT).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ============================================================
    // 指纹算法与 TOFU 判定（纯逻辑，可独立单测）
    // ============================================================

    /** SHA-256(证书 DER) → Base64 标准编码；必须与 PC 端 cert_fingerprint 输出一致。 */
    fun fingerprintOf(cert: X509Certificate): String =
        Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(cert.encoded),
            Base64.NO_WRAP
        )

    /**
     * TOFU 三分支判定：无存储指纹（首连）→ 采信 [actual]；一致 → 放行；
     * 不一致 → 抛 CertificateException（用户可读「证书已变更，请重新信任」语义）。
     */
    fun tofuDecision(stored: String?, actual: String): String {
        if (stored.isNullOrBlank()) return actual
        if (stored == actual) return stored
        throw CertificateException(
            "桌面端证书已变更（指纹不一致），已中止连接以防数据被窃听。" +
                "请在设置 → 桌面同步中清除 HTTPS 信任后重新连接"
        )
    }

    /**
     * TOFU TrustManager：checkServerTrusted 提取服务端证书链首证 → [tofuDecision] →
     * 首连成功即存储指纹。供 [openSyncConnection] 与单元测试共用。
     */
    internal fun tofuTrustManager(context: Context): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
            // 手机端始终为 TLS 客户端（PC 不校验客户端证书），此回调不会触发
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
            val cert = chain?.firstOrNull() ?: throw CertificateException("服务端未提供证书")
            val actual = fingerprintOf(cert)
            val stored = trustedFingerprint(context)
            tofuDecision(stored, actual)
            if (stored.isNullOrBlank()) trustFingerprint(context, actual)  // TOFU 首连固定
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    // ============================================================
    // 连接工厂：先 HTTPS 后 HTTP 回退
    // ============================================================

    /**
     * 打开到 PC 同步服务的连接（协议/请求头/超时由调用方设置，全部不变，只换传输层）。
     *
     * @param path 含查询串的路径，如 "/sync/version"、"/upload_moment?student=x&date=y"
     *
     * 路由：
     * - 记忆「明文可用」→ 直接 http
     * - 记忆「TLS 可用」→ 直接 https
     * - 均未知 → SSLSocket 探测：握手成功选 https；网络层失败回退 http 重试一次；
     *   TOFU 指纹不匹配（CertificateException）绝不回退明文，原样上抛
     *
     * 注：返回的连接未 connect（调用方须在 connect 前设置请求头），TLS 探测在独立的
     * 抛弃型 SSLSocket 上完成；两次握手共享同一 SSLContext，第二次为会话恢复，开销小。
     */
    fun openSyncConnection(
        context: Context,
        host: String,
        port: String,
        path: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        val routeKey = "$host:$port"
        val now = System.currentTimeMillis()
        return when {
            (plaintextOkUntil[routeKey] ?: 0L) > now ->
                newHttpConnection(host, port, path, connectTimeoutMs, readTimeoutMs)
            (tlsOkUntil[routeKey] ?: 0L) > now ->
                newHttpsConnection(host, port, path, connectTimeoutMs, readTimeoutMs,
                                   tlsSocketFactory(context))
            else -> probeAndRoute(context, routeKey, host, port, path,
                                  connectTimeoutMs, readTimeoutMs)
        }
    }

    private fun probeAndRoute(
        context: Context,
        routeKey: String,
        host: String,
        port: String,
        path: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        val factory = tlsSocketFactory(context)
        try {
            (factory.createSocket() as SSLSocket).use { probe ->
                probe.connect(InetSocketAddress(host, port.toInt()), connectTimeoutMs)
                probe.soTimeout = connectTimeoutMs
                probe.startHandshake()  // 触发 TrustManager：TOFU 判定 + 首连存储
            }
            tlsOkUntil[routeKey] = System.currentTimeMillis() + ROUTE_MEMORY_TTL_MS
            plaintextOkUntil.remove(routeKey)
            return newHttpsConnection(host, port, path, connectTimeoutMs, readTimeoutMs, factory)
        } catch (e: IOException) {
            // 指纹不匹配（证书更换/中间人）必须向上抛，不得静默降级明文
            findCertificateException(e)?.let { throw it }
            Log.w(TAG, "TLS 探测失败，回退明文 http：${e.message}")
            // 明文回退重试一次：TCP 连通即选路明文（HTTP 语义由调用方请求时验证）
            Socket().use { it.connect(InetSocketAddress(host, port.toInt()), connectTimeoutMs) }
            // TLS 近期可用时不记忆明文：防 HTTPS 服务瞬时抖动把路由钉死在明文
            if ((tlsOkUntil[routeKey] ?: 0L) <= System.currentTimeMillis()) {
                plaintextOkUntil[routeKey] = System.currentTimeMillis() + ROUTE_MEMORY_TTL_MS
            }
            return newHttpConnection(host, port, path, connectTimeoutMs, readTimeoutMs)
        }
    }

    private fun tlsSocketFactory(context: Context): SSLSocketFactory =
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(tofuTrustManager(context)), null)
        }.socketFactory

    private fun newHttpsConnection(
        host: String, port: String, path: String,
        connectTimeoutMs: Int, readTimeoutMs: Int, factory: SSLSocketFactory
    ): HttpsURLConnection =
        (URL("https://$host:$port$path").openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = factory
            // 自签证书：hostname 校验关闭，安全边界 = TOFU 指纹（见 [tofuTrustManager]）
            hostnameVerifier = HostnameVerifier { _, _ -> true }
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
        }

    private fun newHttpConnection(
        host: String, port: String, path: String,
        connectTimeoutMs: Int, readTimeoutMs: Int
    ): HttpURLConnection =
        (URL("http://$host:$port$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
        }

    /** 在异常 cause 链中查找 CertificateException（JSSE 会把 TrustManager 异常包进 SSLHandshakeException）。 */
    private fun findCertificateException(e: Throwable): CertificateException? {
        var t: Throwable? = e
        while (t != null) {
            if (t is CertificateException) return t
            t = t.cause
        }
        return null
    }
}
