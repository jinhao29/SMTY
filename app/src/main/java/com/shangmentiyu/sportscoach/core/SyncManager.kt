package com.shangmentiyu.sportscoach.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.shangmentiyu.sportscoach.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 桌面同步管理器（处理器层）：将备份 ZIP 推送到 PC 端接收服务。
 *
 * 设计目标（v21 引入）：
 * - 在局域网内通过 HTTP POST 把备份 ZIP 推送到 PC 端轻量接收服务，
 *   替代手动导出 + 手动拷贝的两步流程；
 * - 仅依赖 JDK [HttpURLConnection]，无需引入 OkHttp / Sardine / jcifs 等第三方库；
 * - 客户端与桌面端通过简单 token 鉴权，避免局域网内被误投递；
 * - 同步失败不影响主流程，备份文件仍保留在 App 沙盒，可手动重试。
 *
 * 与 [BackupManager] 的协作：
 * - [BackupManager.backup] 完成后，调用方将备份 ZIP 写入临时文件，
 *   再调用 [pushBackupFile] 推送到 PC 端；
 * - 推送成功后由调用方决定是否删除本地临时备份。
 *
 * 协议约定（与桌面端 [backup_receiver.py] 配合）：
 * - 方法：POST
 * - URL：http://{host}:{port}/upload
 * - Header：
 *     X-Sync-Token: {token}           // 鉴权（两端均为空时跳过校验）
 *     X-Backup-Name: {fileName}       // 备份文件名（含扩展名）
 *     Content-Type: application/octet-stream
 * - Body：备份 ZIP 原始字节流
 * - 响应：HTTP 200 + 文本 "OK" 表示成功；其他状态码或异常表示失败。
 *
 * WebDAV / SMB 替代方案说明：
 * - 若希望直接走标准协议（替换为本实现），可考虑：
 *   - WebDAV：使用 sardine-android（`com.github.lookout:android-sardine`），
 *             优点是标准协议，PC 端可挂载为网络磁盘；缺点是依赖大、配置复杂。
 *   - SMB：使用 jcifs-ng（`org.codelibs:jcifs`），
 *          优点是 PC 端无需启动额外服务（直接访问 Windows 共享文件夹）；
 *          缺点是 SMB 协议在 Android 上耗电高，且 Windows 防火墙常拦截。
 * - 当前实现选用 HTTP 自定义协议，是为了在保持轻量与可控的前提下，
 *   兼顾跨平台（PC 端 Python 一键启动）与无第三方依赖。后续如需切换协议，
 *   仅需替换本类的 pushBackupFile 内部实现，调用方不受影响。
 *
 * @param context 上下文（用于访问 DataStore 设置）
 * @param settings 设置仓储（注入，避免循环依赖）
 */
class SyncManager(
    private val context: Context,
    private val settings: SettingsRepository
) {

    companion object {
        private const val TAG = "SyncManager"

        /** 连接超时（毫秒）：局域网内应快速，5 秒未连上视为 PC 端未启动 */
        private const val CONNECT_TIMEOUT_MS = 5_000

        /** 读取超时（毫秒）：大备份 ZIP 上传可能较慢，60 秒兜底 */
        private const val READ_TIMEOUT_MS = 60_000

        /** 推送成功后 PC 端返回的固定响应体 */
        private const val EXPECTED_RESPONSE = "OK"
    }

    /**
     * 推送结果：携带成功标志 + 可读消息 + HTTP 状态码。
     *
     * @param success 是否推送成功（HTTP 200 + 响应体为 OK）
     * @param message 用户可读消息，UI 可直接展示
     * @param httpCode HTTP 响应码（连接失败时为 0）
     */
    data class PushResult(
        val success: Boolean,
        val message: String,
        val httpCode: Int = 0
    )

    /**
     * 同步配置快照（从 [SettingsRepository] 一次性读取，避免在协程中多次 collect）。
     *
     * @param enabled 是否启用同步
     * @param host PC 端 IP 地址
     * @param port PC 端接收服务端口
     * @param token 鉴权 token
     */
    private data class SyncConfig(
        val enabled: Boolean,
        val host: String,
        val port: String,
        val token: String
    )

    /**
     * 推送备份文件到 PC 端。
     *
     * 调用时机：[BackupManager.backup] 完成后，将 ZIP 文件路径传入本方法。
     * 内部在 [Dispatchers.IO] 执行 HTTP 上传，避免阻塞主线程。
     *
     * 跳过推送的场景（返回 success=false 但不视为错误）：
     * - 同步未启用（[SettingsRepository.syncEnabled] = false）
     * - PC 端 IP 未配置
     * - 本地备份文件不存在或为空
     *
     * 失败场景（返回 success=false）：
     * - PC 端服务未启动 / 端口未开放（ConnectException）
     * - 网络超时（SocketTimeoutException）
     * - 鉴权失败（HTTP 401）
     * - 其他 IO 异常
     *
     * @param backupFile 本地备份 ZIP 文件
     * @return [PushResult]，调用方按需提示用户
     */
    suspend fun pushBackupFile(backupFile: File): PushResult = withContext(Dispatchers.IO) {
        val cfg = loadConfig()
        if (!cfg.enabled) {
            return@withContext PushResult(false, "未启用桌面同步")
        }
        if (cfg.host.isBlank()) {
            return@withContext PushResult(false, "未配置 PC 端 IP，请在设置中填写")
        }
        if (!backupFile.exists() || backupFile.length() == 0L) {
            return@withContext PushResult(false, "本地备份文件不存在或为空")
        }

        val urlStr = buildUrl(cfg.host, cfg.port)
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                useCaches = false
                // 鉴权 header（两端均为空时跳过校验）
                if (cfg.token.isNotEmpty()) {
                    setRequestProperty("X-Sync-Token", cfg.token)
                }
                // 备份文件名（PC 端用于命名存储文件）
                setRequestProperty("X-Backup-Name", backupFile.name)
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Content-Length", backupFile.length().toString())
            }

            // 上传文件流（8KB 缓冲，避免大备份 OOM）
            FileInputStream(backupFile).use { fis ->
                conn.outputStream.use { os ->
                    val buffer = ByteArray(8 * 1024)
                    var len = fis.read(buffer)
                    while (len > 0) {
                        os.write(buffer, 0, len)
                        len = fis.read(buffer)
                    }
                    os.flush()
                }
            }

            val code = conn.responseCode
            val body = (conn.errorStream ?: conn.inputStream)?.bufferedReader()
                ?.use { it.readText() } ?: ""
            if (code == 200 && body.trim() == EXPECTED_RESPONSE) {
                Log.i(TAG, "推送成功：${backupFile.name} → $urlStr")
                PushResult(true, "已推送到 PC 端", code)
            } else {
                Log.w(TAG, "推送失败 HTTP $code: $body")
                PushResult(
                    false,
                    "PC 端响应异常 (HTTP $code)：${body.ifBlank { "未知错误" }}",
                    code
                )
            }
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "连接失败：${e.message}")
            PushResult(false, "无法连接 PC 端，请确认接收服务已启动且 IP/端口正确")
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "超时：${e.message}")
            PushResult(false, "连接 PC 端超时，请检查网络或更换端口")
        } catch (e: Exception) {
            Log.e(TAG, "推送异常", e)
            PushResult(false, "推送异常：${e.message ?: "未知错误"}")
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 通过 SAF Uri 推送备份文件（用于用户手动选定的备份文件）。
     *
     * 将 [BackupManager.openBackupInputStream] 返回的 InputStream 复制到临时文件，
     * 再调用 [pushBackupFile] 上传。临时文件在推送完成后自动删除。
     *
     * @param backupUri 备份文件 Uri
     * @return [PushResult]
     */
    suspend fun pushBackupUri(backupUri: Uri): PushResult = withContext(Dispatchers.IO) {
        val cfg = loadConfig()
        if (!cfg.enabled || cfg.host.isBlank()) {
            return@withContext PushResult(false, "未启用桌面同步或未配置 PC 端 IP")
        }

        // 复制到临时文件（避免流式上传时无法指定 Content-Length）
        // 线程安全：使用 [LocalDateTime] + [DateTimeFormatter] 替代 [SimpleDateFormat]
        val timestampFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.getDefault())
        val tempFile = File(
            context.cacheDir,
            "smty_sync_${LocalDateTime.now().format(timestampFmt)}.tmp"
        )
        try {
            context.contentResolver.openInputStream(backupUri).use { input ->
                if (input == null) {
                    return@withContext PushResult(false, "无法读取备份文件")
                }
                tempFile.outputStream().use { output ->
                    input.copyTo(output, 8 * 1024)
                }
            }
            pushBackupFile(tempFile)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    /**
     * 探测 PC 端接收服务是否在线（GET /health）。
     *
     * 调用时机：设置页"测试连接"按钮，验证 IP/端口是否可达。
     *
     * @return true=PC 端接收服务在线；false=不可达或未启动
     */
    suspend fun pingDesktop(): Boolean = withContext(Dispatchers.IO) {
        val cfg = loadConfig()
        if (cfg.host.isBlank()) return@withContext false

        val urlStr = "http://${cfg.host}:${cfg.port}/health"
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3_000
                readTimeout = 3_000
            }
            conn.responseCode == 200
        } catch (e: Exception) {
            Log.w(TAG, "ping 失败：${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    /** 一次性从 DataStore 读取同步配置，避免在协程中多次 collect Flow */
    private suspend fun loadConfig(): SyncConfig = SyncConfig(
        enabled = settings.syncEnabled.first(),
        host = settings.syncHost.first().trim(),
        port = settings.syncPort.first().trim().ifBlank { SettingsRepository.DEFAULT_SYNC_PORT },
        token = settings.syncToken.first().trim()
    )

    /** 拼接上传 URL，自动去除 host 末尾的斜杠 */
    private fun buildUrl(host: String, port: String): String {
        val cleanHost = host.trimEnd('/')
        return "http://$cleanHost:$port/upload"
    }
}
