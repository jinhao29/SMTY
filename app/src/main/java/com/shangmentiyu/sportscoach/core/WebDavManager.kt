package com.shangmentiyu.sportscoach.core

import android.content.Context
import android.util.Base64
import android.util.Log
import com.shangmentiyu.sportscoach.data.repo.WebDavConfig
import com.shangmentiyu.sportscoach.data.repo.WebDavCredentialsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WebDAV 云盘上传管理器（处理器层）。
 *
 * 设计目标（v32 优化1 新增）：
 * - 在局域网/本地之外提供"终极异地灾备"通道，将备份 ZIP 静默推送至教练私人网盘
 * - 仅依赖 JDK [HttpURLConnection]，无需引入 Sardine 等第三方 WebDAV 库
 * - 支持标准 WebDAV 协议的服务商：坚果云 / Nextcloud / 阿里云盘 WebDAV 等
 * - 账号密码通过 [WebDavCredentialsStore]（EncryptedSharedPreferences）加密存储
 *
 * 协议约定（标准 WebDAV PUT）：
 * - 方法：PUT
 * - URL：{baseUrl}/{remoteDir}/{fileName}
 * - Header：
 *     Authorization: Basic {base64(user:pass)}   // HTTP Basic Auth
 *     Content-Type: application/zip
 *     Overwrite: T                                 // 允许覆盖同名文件
 * - Body：备份 ZIP 原始字节流
 * - 响应：HTTP 200/201/204 表示成功
 *
 * 容错策略：
 * - 远程目录不存在时自动创建（MKCOL）
 * - 上传失败不影响主备份流程，仅记录日志
 * - 文件名带时间戳，避免覆盖历史备份
 *
 * @param context 上下文（用于访问加密存储）
 * @param credentialsStore WebDAV 凭证加密存储
 */
class WebDavManager(
    private val context: Context,
    private val credentialsStore: WebDavCredentialsStore
) {

    companion object {
        private const val TAG = "WebDavManager"

        /** 连接超时（毫秒）：网盘服务可能跨地域，10 秒兜底 */
        private const val CONNECT_TIMEOUT_MS = 10_000

        /** 读取超时（毫秒）：大备份 ZIP 上传可能较慢，120 秒兜底 */
        private const val READ_TIMEOUT_MS = 120_000

        /** 上传缓冲区大小（8KB） */
        private const val BUFFER_SIZE = 8 * 1024
    }

    /**
     * 上传结果。
     *
     * @param success 是否成功
     * @param message 用户可读消息
     * @param remotePath 远程路径（成功时填充）
     */
    data class UploadResult(
        val success: Boolean,
        val message: String,
        val remotePath: String = ""
    )

    /**
     * 推送备份 ZIP 到 WebDAV 网盘。
     *
     * 流程：
     * 1. 读取加密存储的 WebDAV 配置（baseUrl / user / pass / remoteDir）
     * 2. 若未启用或配置不全，返回 success=false 但不视为错误
     * 3. 自动创建远程目录（MKCOL，已存在则忽略 405/409）
     * 4. 通过 PUT 上传 ZIP 文件，文件名带时间戳防覆盖
     *
     * @param backupFile 本地备份 ZIP 文件
     * @return [UploadResult]
     */
    suspend fun pushBackup(backupFile: File): UploadResult = withContext(Dispatchers.IO) {
        val cfg = loadConfig()
        if (!cfg.enabled) {
            return@withContext UploadResult(false, "WebDAV 未启用")
        }
        if (cfg.baseUrl.isBlank() || cfg.username.isBlank() || cfg.password.isBlank()) {
            return@withContext UploadResult(false, "WebDAV 配置不完整")
        }
        if (!backupFile.exists() || backupFile.length() == 0L) {
            return@withContext UploadResult(false, "本地备份文件不存在或为空")
        }

        // 远程文件名：_backup_yyyyMMdd_HHmmss.zip
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())
        val remoteName = "smty_backup_${timestamp}.zip"
        val remotePath = buildRemotePath(cfg.remoteDir, remoteName)

        // 1. 确保远程目录存在（MKCOL，已存在忽略）
        ensureRemoteDir(cfg, cfg.remoteDir)

        // 2. PUT 上传
        var conn: HttpURLConnection? = null
        try {
            val url = URL(remotePath)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                useCaches = false
                setRequestProperty("Authorization", buildBasicAuth(cfg.username, cfg.password))
                setRequestProperty("Content-Type", "application/zip")
                setRequestProperty("Overwrite", "T")
                setRequestProperty("Content-Length", backupFile.length().toString())
            }

            FileInputStream(backupFile).use { fis ->
                conn.outputStream.use { os ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var len = fis.read(buffer)
                    while (len > 0) {
                        os.write(buffer, 0, len)
                        len = fis.read(buffer)
                    }
                    os.flush()
                }
            }

            val code = conn.responseCode
            if (code in 200..299) {
                Log.i(TAG, "WebDAV 上传成功：$remotePath")
                UploadResult(true, "已推送到 WebDAV 网盘", remotePath)
            } else {
                val body = (conn.errorStream ?: conn.inputStream)?.bufferedReader()
                    ?.use { it.readText() } ?: ""
                Log.w(TAG, "WebDAV 上传失败 HTTP $code: $body")
                UploadResult(false, "WebDAV 响应异常 (HTTP $code)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV 上传异常", e)
            UploadResult(false, "WebDAV 异常：${e.message ?: "未知错误"}")
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 测试 WebDAV 连接（PROPFIND 深度 0）。
     *
     * 调用时机：设置页"测试连接"按钮。
     *
     * @return true=连接成功；false=不可达或认证失败
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        val cfg = loadConfig()
        if (cfg.baseUrl.isBlank() || cfg.username.isBlank()) return@withContext false

        var conn: HttpURLConnection? = null
        try {
            val url = URL(cfg.baseUrl.trimEnd('/'))
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PROPFIND"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Authorization", buildBasicAuth(cfg.username, cfg.password))
                setRequestProperty("Depth", "0")
            }
            val code = conn.responseCode
            // 207 Multi-Status 是 WebDAV PROPFIND 成功响应
            code in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "WebDAV 测试连接失败：${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 创建远程目录（MKCOL）。
     * 已存在时服务端返回 405 Method Not Allowed，视为成功。
     */
    private fun ensureRemoteDir(cfg: WebDavConfig, remoteDir: String) {
        if (remoteDir.isBlank()) return
        val dirUrl = buildRemotePath(cfg.baseUrl, remoteDir)
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(dirUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "MKCOL"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Authorization", buildBasicAuth(cfg.username, cfg.password))
            }
            val code = conn.responseCode
            Log.i(TAG, "MKCOL $remoteDir → HTTP $code")
            // 201 Created / 405 Method Not Allowed（已存在）均视为成功
        } catch (e: Exception) {
            Log.w(TAG, "MKCOL 异常：${e.message}")
        } finally {
            conn?.disconnect()
        }
    }

    /** 拼接远程路径，自动 URL 编码文件名（避免中文/空格导致 400） */
    private fun buildRemotePath(base: String, segments: String): String {
        val cleanBase = base.trimEnd('/')
        val encoded = segments.split('/').joinToString("/") { seg ->
            URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        }
        return "$cleanBase/$encoded"
    }

    /** 构建 HTTP Basic Auth header 值 */
    private fun buildBasicAuth(user: String, pass: String): String {
        val raw = "$user:$pass"
        val encoded = Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    /** 一次性读取 WebDAV 配置（包含加密凭证） */
    private suspend fun loadConfig(): WebDavConfig {
        val cfg = credentialsStore.config.first()
        val user = credentialsStore.getUsername()
        val pass = credentialsStore.getPassword()
        return cfg.copy(username = user, password = pass)
    }
}
