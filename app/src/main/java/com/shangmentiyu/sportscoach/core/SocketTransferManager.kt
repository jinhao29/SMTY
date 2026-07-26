package com.shangmentiyu.sportscoach.core

import android.content.Context
import android.util.Log
import com.shangmentiyu.sportscoach.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Socket 传输管理器（协调层）：Android ↔ 桌面端局域网 Socket 直连通道。
 *
 * 设计目标（第3轮新增）：
 * - 替代 HTTP POST 单向推送，支持双向通信（请求-响应）
 * - 桌面端可主动拉取学员档案 / 课时记录 / 备份包
 * - Android 端作为 Socket 服务端监听（端口 17890）
 * - 鉴权：MD5(token + timestamp) 防止局域网误投递
 * - 协议：JSON 行式帧（每行一个完整请求/响应）
 *
 * 协议示例：
 * 请求：{"action":"fetch_backup","token":"xxx","ts":1722000000,"args":{"name":"张三"}}
 * 响应：{"code":0,"msg":"OK","data":{"file":"/path/to/backup.zip","size":10240}}
 *
 * 与 [SyncManager] 关系：
 * - [SyncManager] 负责 HTTP 主动推送（备份完成后）
 * - [SocketTransferManager] 负责被动响应桌面端拉取请求
 * - 两者可共存，端口不冲突（HTTP:8080 / Socket:17890）
 */
class SocketTransferManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val port: Int = DEFAULT_PORT
) {
    companion object {
        private const val TAG = "SocketTransfer"
        const val DEFAULT_PORT = 17890
        private const val AUTH_SKEW_MS = 5 * 60 * 1000L  // 5 分钟时钟偏移容忍
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_FRAME_SIZE = 64 * 1024      // 单帧最大 64KB（控制信令）
        private const val FILE_CHUNK_SIZE = 8 * 1024      // 文件传输块大小
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    /**
     * 启动 Socket 服务端监听。
     * 重复调用安全，已启动时直接返回。
     */
    fun start() {
        if (!running.compareAndSet(false, true)) {
            Log.i(TAG, "Socket 服务已在运行")
            return
        }
        acceptJob = scope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                Log.i(TAG, "Socket 服务启动，监听端口 $port")
                while (running.get()) {
                    val client = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        if (running.get()) Log.e(TAG, "accept 失败: ${e.message}")
                        break
                    }
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "服务启动失败: ${e.message}", e)
            } finally {
                running.set(false)
            }
        }
    }

    /**
     * 停止监听并释放资源。
     */
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        acceptJob?.cancel()
        Log.i(TAG, "Socket 服务已停止")
    }

    /**
     * 释放所有资源（在 Application onTerminate 或 MainActivity onDestroy 调用）。
     */
    fun release() {
        stop()
        scope.cancel()
    }

    // ============================================================
    // 客户端处理
    // ============================================================

    private suspend fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = READ_TIMEOUT_MS
            val input = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
            val output = s.getOutputStream()
            try {
                while (running.get()) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) continue
                    if (line.length > MAX_FRAME_SIZE) {
                        writeJson(output, errorResponse(400, "帧过大"))
                        break
                    }
                    val response = processRequest(line)
                    writeJson(output, response)
                    if (response.contains("\"action\":\"bye\"")) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "客户端连接异常: ${e.message}")
            }
        }
    }

    private fun writeJson(output: OutputStream, json: String) {
        val bytes = (json + "\n").toByteArray(StandardCharsets.UTF_8)
        output.write(bytes)
        output.flush()
    }

    // ============================================================
    // 请求分发
    // ============================================================

    private suspend fun processRequest(raw: String): String {
        val req = try {
            parseRequest(raw)
        } catch (e: Exception) {
            return errorResponse(400, "请求解析失败: ${e.message}")
        }

        // 鉴权
        if (!verifyAuth(req)) {
            return errorResponse(401, "鉴权失败")
        }

        return when (req.action) {
            "ping" -> """{"code":0,"msg":"pong","data":{"ts":${System.currentTimeMillis()}}}"""
            "fetch_backup" -> handleFetchBackup(req)
            "fetch_student_list" -> handleFetchStudentList(req)
            "send_file" -> handleReceiveFile(req, raw)
            "bye" -> """{"code":0,"action":"bye","msg":"再见"}"""
            else -> errorResponse(404, "未知 action: ${req.action}")
        }
    }

    /**
     * === 修复：用 [org.json.JSONObject] 手动解析 SocketRequest ===
     *
     * 原 JsonSafe.fromJson 不存在（项目未集成 Gson/Moshi），
     * 改用 org.json.JSONObject 逐字段解析，与项目其他 JSON 处理保持一致风格。
     */
    private fun parseRequest(raw: String): SocketRequest {
        val obj = org.json.JSONObject(raw)
        val argsObj = obj.optJSONObject("args")
        val argsMap: Map<String, String>? = argsObj?.let { a ->
            val keys = a.keys()
            val map = HashMap<String, String>()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = a.optString(k, "")
            }
            map
        }
        return SocketRequest(
            action = obj.optString("action", ""),
            token = obj.optString("token", ""),
            ts = obj.optLong("ts", 0L),
            args = argsMap
        )
    }

    private suspend fun verifyAuth(req: SocketRequest): Boolean {
        val token = settingsRepository.getSyncTokenBlocking() ?: return false
        if (token.isEmpty()) return true  // 未设置 token 时跳过校验
        val expected = md5("$token|${req.ts}")
        return expected.equals(req.token, ignoreCase = true) &&
               Math.abs(System.currentTimeMillis() - req.ts) < AUTH_SKEW_MS
    }

    // ============================================================
    // 业务处理器
    // ============================================================

    private suspend fun handleFetchBackup(req: SocketRequest): String = withContext(Dispatchers.IO) {
        val studentName = req.args?.get("name") ?: ""
        if (studentName.isEmpty()) {
            return@withContext errorResponse(400, "缺少 name 参数")
        }
        val backupDir = File(context.filesDir, "backups")
        val target = backupDir.listFiles()?.firstOrNull {
            it.name.contains(studentName) && it.name.endsWith(".zip")
        }
        if (target == null || !target.exists()) {
            return@withContext errorResponse(404, "未找到 $studentName 的备份")
        }
        """{"code":0,"msg":"OK","data":{"file":"${target.absolutePath}","size":${target.length()}}}"""
    }

    private suspend fun handleFetchStudentList(req: SocketRequest): String = withContext(Dispatchers.IO) {
        val backupDir = File(context.filesDir, "backups")
        val names = backupDir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }
            ?.map { it.name }
            ?: emptyList()
        val dataJson = names.joinToString(",") { "\"$it\"" }
        """{"code":0,"msg":"OK","data":{"count":${names.size},"files":[$dataJson]}}"""
    }

    private suspend fun handleReceiveFile(req: SocketRequest, raw: String): String = withContext(Dispatchers.IO) {
        val fileName = req.args?.get("filename") ?: "received_${System.currentTimeMillis()}.zip"
        val saveDir = File(context.filesDir, "received").apply { mkdirs() }
        val target = File(saveDir, fileName)
        // 实际文件流由调用方在握手后另开 Socket 通道传输，此处仅返回就绪信号
        """{"code":0,"msg":"ready","data":{"save_to":"${target.absolutePath}"}}"""
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun errorResponse(code: Int, msg: String): String {
        return """{"code":$code,"msg":"${msg.replace("\"", "\\\"")}"}"""
    }

    // ============================================================
    // 数据类
    // ============================================================

    data class SocketRequest(
        val action: String = "",
        val token: String = "",
        val ts: Long = 0L,
        val args: Map<String, String>? = null
    )
}

/**
 * SettingsRepository 扩展：阻塞式获取同步 token（避免在 Socket 工作线程引入 Flow）。
 */
suspend fun SettingsRepository.getSyncTokenBlocking(): String? {
    return try {
        syncToken.first()
    } catch (e: Exception) {
        null
    }
}
