package com.shangmentiyu.sportscoach.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.shangmentiyu.sportscoach.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 精彩瞬间上传器（处理器层）：将学员运动照片推送到桌面端 HTTP 服务。
 *
 * 设计目标（第 10 轮 C 项：双向截图传输）：
 * - 与 [SyncManager] 共用同一组 PC 端 IP/Port 配置（[SettingsRepository.syncHost]/[syncPort]）
 * - 协议对齐桌面端 [lan_plan_sender.py] 的 POST /upload_moment 端点
 * - 仅依赖 JDK [HttpURLConnection]，不引入第三方库
 * - 失败不抛异常，仅返回 [UploadResult]，调用方按需提示
 *
 * 协议：
 * - 方法：POST
 * - URL：http://{host}:{port}/upload_moment?student={name}&date={YYYYMMDD}
 * - Header：
 *     X-Sync-Token: {token}              // 鉴权（两端均为空时跳过校验）
 *     Content-Type: image/jpeg 或 image/png
 *     Content-Length: {字节数}
 * - Body：原始图片字节流
 * - 响应：HTTP 200 + JSON {"code":0,"msg":"上传成功","data":{...}} 表示成功
 *
 * 与 [LanImageReceiver] 区别：
 * - [LanImageReceiver]：PC 端 → 手机端（下载训练计划截图）
 * - [MomentUploader]：手机端 → PC 端（上传精彩瞬间照片）
 *
 * @param context 上下文（用于读取 ContentResolver 流）
 * @param settings 设置仓储（注入，复用 PC 端 IP/Port/Token）
 */
class MomentUploader(
    private val context: Context,
    private val settings: SettingsRepository
) {

    companion object {
        private const val TAG = "MomentUploader"

        /** 连接超时（毫秒）：局域网内 5 秒兜底 */
        private const val CONNECT_TIMEOUT_MS = 5_000

        /** 读取超时（毫秒）：单张照片通常 < 5MB，30 秒足够 */
        private const val READ_TIMEOUT_MS = 30_000

        /** 单张照片上限：10MB（与桌面端 MAX_UPLOAD_SIZE 对齐） */
        private const val MAX_FILE_SIZE = 10L * 1024 * 1024

        /** 流式上传缓冲区 */
        private const val BUFFER_SIZE = 8 * 1024
    }

    /**
     * 上传结果：携带成功标志 + 可读消息 + 服务端返回的文件名（成功时）。
     *
     * @param success 是否上传成功
     * @param message 用户可读消息（UI 可直接展示）
     * @param remoteFilename 服务端保存的文件名（成功时非空）
     * @param httpCode HTTP 响应码（连接失败时为 0）
     */
    data class UploadResult(
        val success: Boolean,
        val message: String,
        val remoteFilename: String? = null,
        val httpCode: Int = 0
    )

    /**
     * 通过 SAF Uri 上传一张精彩瞬间照片到桌面端。
     *
     * 调用时机：
     * - 教练在 PreClassTab 卡片上点击"上传精彩瞬间"按钮
     * - 通过 ActivityResultContracts.PickVisualMedia 选择照片后调用
     *
     * 失败场景（返回 success=false 但不抛异常）：
     * - PC 端 IP 未配置
     * - 图片无法读取 / 文件过大（>10MB）
     * - PC 端服务未启动 / 超时 / 鉴权失败
     *
     * @param photoUri 图库返回的照片 Uri
     * @param studentName 学员姓名（用于服务端命名与服务端日志）
     * @param dateStr 日期字符串 YYYYMMDD（可选，默认今天）
     * @return [UploadResult]
     */
    suspend fun upload(
        photoUri: Uri,
        studentName: String,
        dateStr: String? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        // 1. 读取同步配置
        val host = settings.syncHost.first().trim()
        val port = settings.syncPort.first().trim()
            .ifBlank { SettingsRepository.DEFAULT_SYNC_PORT }
        val token = settings.syncToken.first().trim()

        if (host.isBlank()) {
            return@withContext UploadResult(false, "未配置 PC 端 IP，请在设置中填写")
        }
        if (studentName.isBlank()) {
            return@withContext UploadResult(false, "学员姓名为空")
        }

        // 2. 探测 ContentResolver 可用性 + 文件大小校验
        val resolver = context.contentResolver
        val mime = resolver.getType(photoUri) ?: "image/jpeg"
        val size = try {
            resolver.openAssetFileDescriptor(photoUri, "r")?.use { it.length }
        } catch (_: Exception) {
            null
        }
        if (size != null && size > MAX_FILE_SIZE) {
            return@withContext UploadResult(
                false,
                "图片过大（${size / 1024 / 1024}MB > 10MB 上限），请压缩后重试"
            )
        }

        // 3. 构造上传 URL（含 student/date 查询参数）
        val date = dateStr ?: LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.getDefault()))
        val safeName = studentName.trim()
        val urlStr = buildUploadUrl(host, port, safeName, date)

        // 4. 上传
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                useCaches = false
                if (token.isNotEmpty()) {
                    setRequestProperty("X-Sync-Token", token)
                }
                setRequestProperty("Content-Type", mime)
                if (size != null && size > 0) {
                    setRequestProperty("Content-Length", size.toString())
                }
            }

            // 流式写入 body（避免一次性 OOM）
            resolver.openInputStream(photoUri).use { input ->
                if (input == null) {
                    return@withContext UploadResult(false, "无法读取图片")
                }
                conn.outputStream.use { out ->
                    streamCopy(input, out)
                    out.flush()
                }
            }

            // 5. 解析响应
            val code = conn.responseCode
            val body = (conn.errorStream ?: conn.inputStream)?.bufferedReader()
                ?.use { it.readText() } ?: ""
            if (code == 200 && body.contains("\"code\":0")) {
                val remoteName = parseFilenameFromResponse(body)
                Log.i(TAG, "精彩瞬间上传成功：$safeName → $urlStr ($remoteName)")
                UploadResult(true, "已上传到 PC 端", remoteFilename = remoteName, httpCode = code)
            } else {
                Log.w(TAG, "上传失败 HTTP $code: $body")
                UploadResult(
                    false,
                    "PC 端响应异常 (HTTP $code)：${body.ifBlank { "未知错误" }}",
                    httpCode = code
                )
            }
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "连接失败：${e.message}")
            UploadResult(false, "无法连接 PC 端，请确认接收服务已启动")
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "超时：${e.message}")
            UploadResult(false, "连接 PC 端超时，请检查网络")
        } catch (e: Exception) {
            Log.e(TAG, "上传异常", e)
            UploadResult(false, "上传异常：${e.message ?: "未知错误"}")
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 探测 PC 端 HTTP 服务是否在线（复用 LanPlanSender 的 /health 端点）。
     *
     * 调用时机：HomeScreen 顶部"同步横幅"定时握手探测。
     *
     * @return true=PC 端接收服务在线；false=不可达
     */
    suspend fun pingDesktop(): Boolean = withContext(Dispatchers.IO) {
        val host = settings.syncHost.first().trim()
        if (host.isBlank()) return@withContext false
        val port = settings.syncPort.first().trim()
            .ifBlank { SettingsRepository.DEFAULT_SYNC_PORT }

        val urlStr = "http://$host:$port/health"
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2_500
                readTimeout = 2_500
            }
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /** 拼接上传 URL，含 student/date 查询参数 */
    private fun buildUploadUrl(host: String, port: String, student: String, date: String): String {
        val cleanHost = host.trimEnd('/')
        // 学员名 URL 编码（保留中文，避免特殊字符截断）
        val encodedName = java.net.URLEncoder.encode(student, "UTF-8")
        return "http://$cleanHost:$port/upload_moment?student=$encodedName&date=$date"
    }

    /** 流式拷贝 InputStream → OutputStream */
    private fun streamCopy(input: InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        var len = input.read(buffer)
        while (len > 0) {
            output.write(buffer, 0, len)
            len = input.read(buffer)
        }
    }

    /**
     * 从桌面端响应 JSON 中提取 filename 字段。
     *
     * 响应示例：
     * {"code":0,"msg":"上传成功","data":{"filename":"X_20260726_moment_120000.jpg","path":"...","size":10240}}
     *
     * 简单字符串解析，避免引入 org.json 之外的 JSON 库。
     */
    private fun parseFilenameFromResponse(body: String): String? {
        val key = "\"filename\":\""
        val start = body.indexOf(key)
        if (start < 0) return null
        val valueStart = start + key.length
        val end = body.indexOf('"', valueStart)
        if (end < 0) return null
        return body.substring(valueStart, end)
    }
}
