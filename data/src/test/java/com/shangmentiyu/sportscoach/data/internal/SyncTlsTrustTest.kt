package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * 同步通道 TLS 信任测试（TOFU）。
 *
 * 跨端锚定向量：[ANCHOR_CERT_PEM] / [ANCHOR_FINGERPRINT] 由 PC 端算法
 * `base64.b64encode(sha256(PEM_cert_to_DER_cert(pem)))` 生成（RSA 2048，
 * CN=shangmentiyu-sync-anchor），PC 端 pytest 将加载同一向量锚定，
 * 断言 Android 端（MessageDigest + Base64.NO_WRAP）输出完全一致的字符串。
 *
 * 运行：./gradlew :data:testDebugUnitTest
 *       --tests "com.shangmentiyu.sportscoach.data.internal.SyncTlsTrustTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncTlsTrustTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SyncTlsTrust.clearFingerprint(context)
    }

    // === 信任存取 ===

    @Test
    fun `未存指纹时返回 null`() {
        assertThat(SyncTlsTrust.trustedFingerprint(context)).isNull()
    }

    @Test
    fun `trust 后可读 clear 后为 null`() {
        SyncTlsTrust.trustFingerprint(context, ANCHOR_FINGERPRINT)
        assertThat(SyncTlsTrust.trustedFingerprint(context)).isEqualTo(ANCHOR_FINGERPRINT)
        SyncTlsTrust.clearFingerprint(context)
        assertThat(SyncTlsTrust.trustedFingerprint(context)).isNull()
    }

    // === 跨端锚定向量（指纹算法与 PC 端一致） ===

    @Test
    fun `锚定证书指纹与 PC 端算法一致`() {
        val cert = certificateFromPem(ANCHOR_CERT_PEM)
        assertThat(SyncTlsTrust.fingerprintOf(cert)).isEqualTo(ANCHOR_FINGERPRINT)
    }

    // === TOFU 决策（纯函数） ===

    @Test
    fun `tofu 首连无存储指纹时采信实际指纹`() {
        assertThat(SyncTlsTrust.tofuDecision(null, ANCHOR_FINGERPRINT))
            .isEqualTo(ANCHOR_FINGERPRINT)
    }

    @Test
    fun `tofu 指纹一致放行`() {
        assertThat(SyncTlsTrust.tofuDecision(ANCHOR_FINGERPRINT, ANCHOR_FINGERPRINT))
            .isEqualTo(ANCHOR_FINGERPRINT)
    }

    @Test
    fun `tofu 指纹不一致抛证书变更异常`() {
        var thrown: CertificateException? = null
        try {
            SyncTlsTrust.tofuDecision("other-fingerprint=", ANCHOR_FINGERPRINT)
        } catch (e: CertificateException) {
            thrown = e
        }
        assertThat(thrown).isNotNull()
        assertThat(thrown!!.message).contains("证书已变更")
    }

    // === TrustManager 决策（TOFU 首连存储 + 拒绝路径） ===

    @Test
    fun `trustManager 首连存储指纹并放行`() {
        val cert = certificateFromPem(ANCHOR_CERT_PEM)
        SyncTlsTrust.tofuTrustManager(context).checkServerTrusted(arrayOf(cert), "RSA")
        assertThat(SyncTlsTrust.trustedFingerprint(context)).isEqualTo(ANCHOR_FINGERPRINT)
    }

    @Test
    fun `trustManager 指纹一致放行`() {
        SyncTlsTrust.trustFingerprint(context, ANCHOR_FINGERPRINT)
        val cert = certificateFromPem(ANCHOR_CERT_PEM)
        SyncTlsTrust.tofuTrustManager(context).checkServerTrusted(arrayOf(cert), "RSA")
    }

    @Test
    fun `trustManager 指纹不一致拒绝且不覆盖已存指纹`() {
        SyncTlsTrust.trustFingerprint(context, "other-fingerprint=")
        val cert = certificateFromPem(ANCHOR_CERT_PEM)
        var thrown: CertificateException? = null
        try {
            SyncTlsTrust.tofuTrustManager(context).checkServerTrusted(arrayOf(cert), "RSA")
        } catch (e: CertificateException) {
            thrown = e
        }
        assertThat(thrown).isNotNull()
        assertThat(SyncTlsTrust.trustedFingerprint(context)).isEqualTo("other-fingerprint=")
    }

    private fun certificateFromPem(pem: String): X509Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(pem.toByteArray(Charsets.US_ASCII)))
            as X509Certificate

    private companion object {
        const val ANCHOR_FINGERPRINT = "1cdEvoqG4f3VLytC+4QNGBO5Fl/j+me7F36O6EjomwA="

        const val ANCHOR_CERT_PEM = """-----BEGIN CERTIFICATE-----
MIIC0jCCAbqgAwIBAgIUFZF9F4QkioPLlaC2R48Oc1//V/owDQYJKoZIhvcNAQEL
BQAwIzEhMB8GA1UEAwwYc2hhbmdtZW50aXl1LXN5bmMtYW5jaG9yMB4XDTI2MDkx
MjA2MDQ1M1oXDTM2MDkxMDA2MDQ1M1owIzEhMB8GA1UEAwwYc2hhbmdtZW50aXl1
LXN5bmMtYW5jaG9yMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAo2uC
dr8GgxfpHe0RLxSI9+jWgRshcZlf3bja88VmSILQNAd7WDvKRps7p9FKkC9AzA0X
bfhjaO5krgvLH9Kc7loN0GKxEpgTxhJ5WWR1yKfbrlU9ZJRy6oiHICf17MMaCc9S
fYKtf9S99mLPf7hV7DcRVDhVMnGkbD/n+v6naBRILYFqefVVDcwpzwuqOIFsSAuv
V4V+RZi7jlQu2EO7mrHJGO3EHpqEF9rWAkPK6YkyPst/NttsKpWgcdo2NNLWZuJz
WtCca6hxD5sZpTOu00GQK9pHtTNyQkrhbOz3VVO9dhhjYgBJYttFO7eHEIlI3VaN
8q3DWv7/7uXwOP515wIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQAozL4WcadWT/UQ
aDBR7D0dJvF/VkMmqBSel9uESBCvz4OPLiZynIY3pW/SXGrClvvqe9V6ENIBqgDt
sdhWkRHZsZ2RXFkn0RBNRAgssMF4igI6GJrCJBfPvOhMR/cwOkMJ+bwR1V1v4033
D4BkzmNTQxQ3S244dFKw3tdMLySC2XmWL3zWh3NFnoehLl8aLWU9/47hUCQYxTn7
o3McYJ6NUXDaKfNZg/tFiPBK93HCtqLYW6UMbNXk6tMZUz3mHxGbSLwdFTAKD365
gqv3HLj8SK6T066yUSpqO0Tg3JS7FA552cCy76Mj8SYZ8d8bd1XV0SyyX3sFH7TA
9YSMj++f
-----END CERTIFICATE-----"""
    }
}
