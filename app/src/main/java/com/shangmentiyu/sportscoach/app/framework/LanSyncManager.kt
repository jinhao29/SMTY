package com.shangmentiyu.sportscoach.app.framework

import android.content.Context
import android.util.Log
import com.shangmentiyu.sportscoach.data.repo.BackupRepository
import com.shangmentiyu.sportscoach.data.repo.SettingsRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import com.shangmentiyu.sportscoach.excel.ExcelSync
import com.shangmentiyu.sportscoach.excel.ImportStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 双端同步管理器（处理器层）：手机 ↔ 桌面端 数据互通。
 *
 * 设计目标（v23 双端同步）：
 * - 手机 → PC：把整库备份 zip 推送到 PC 同步服务（POST /upload），
 *   PC 端自动安全校验并合并到档案目录（恢复前自动备份，可回滚）
 * - PC → 手机：拉取 PC 端学员汇总 Excel（GET /sync/students.xlsx），
 *   经 [ExcelSync.importStudentsFromTabularExcel] 解析后按 UPDATE_PART 策略合并
 * - 与 [MomentUploader] 共用同一组 PC 端 IP/Port/Token 配置（[SettingsRepository]）
 * - 协议对齐桌面端 `data_center/sync_server.py`（详见 双端同步协议.md）
 * - 仅依赖 JDK [HttpURLConnection] + org.json，不引入第三方库
 * - 失败不抛异常，返回 [SyncResult]，调用方按需提示
 *
 * 连接方式：
 * - 局域网：syncHost 填 PC 局域网 IP
 * - USB：PC 运行 `adb reverse tcp:8765 tcp:8765` 后，syncHost 填 127.0.0.1
 *
 * 冲突策略（UPDATE_PART）：同名学员仅更新 身高/体重/BMI/性别/年级/学校/电话/年龄，
 * 保留手机端的 createdAt/studentId/isActive 与课时、排课等子表数据。
 * PC 端"未录入身高"（空单元格 → 0）的学员会被过滤，避免 0 覆盖手机端已录入身高。
 *
 * @param context 上下文（cacheDir 用于临时备份/下载文件）
 * @param settings 设置仓储（PC 端 IP/Port/Token）
 * @param backupRepo 备份仓储（backupToCache 生成推送用 zip）
 * @param studentRepo 学员仓储（UPDATE_PART 合并落库）
 */
class LanSyncManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val backupRepo: BackupRepository,
    private val studentRepo: StudentRepository,
    // v23.7：课时包仓储（PC→手机课时包同步）；缺省 null 兼容既有测试构造
    private val lessonPackageRepo: com.shangmentiyu.sportscoach.data.repo.LessonPackageRepository? = null
) {

    companion object {
        private const val TAG = "LanSyncManager"

        /** Connection timeout (ms): 5s for LAN */
        private const val CONNECT_TIMEOUT_MS = 5_000

        /** Read timeout (ms): 整库备份推送 60s（含照片时体积可达数十 MB） */
        private const val READ_TIMEOUT_MS = 60_000

        /** Stream upload/download buffer */
        private const val BUFFER_SIZE = 8 * 1024

        /** 合法局域网/回环 host（复用 MomentUploader 的判定，USB 回环 127.x 在白名单内） */
        fun isLocalNetworkHost(host: String): Boolean =
            MomentUploader.isLocalNetworkHost(host)

        /** 未配置 PC 地址时的统一错误文案 */
        private const val MSG_NO_ENDPOINT =
            "未配置 PC 端地址，请在设置中填写（USB 连接填 127.0.0.1）"
    }

    /**
     * 同步结果。
     *
     * @param success 是否成功
     * @param message 用户可读消息（UI 可直接展示）
     * @param httpCode HTTP 响应码（连接失败时为 0）
     */
    data class SyncResult(val success: Boolean, val message: String, val httpCode: Int = 0)

    // ============================================================
    // v23.6 PC 在线状态（自动识别 Wi-Fi / USB，UI 直接订阅展示）
    // ============================================================

    /**
     * PC 端连接状态。
     *
     * @param viaUsb true=USB（adb reverse 回环）连接；false=Wi-Fi 局域网连接
     * @param pcName PC 名称（心跳携带；USB 探测无名称时用通用文案）
     * @param host 实际连通的地址
     * @param port 实际连通的端口
     */
    data class DesktopLink(
        val viaUsb: Boolean,
        val pcName: String,
        val host: String,
        val port: String
    )

    private val _desktopOnline = MutableStateFlow<DesktopLink?>(null)

    /** PC 在线状态（null=未连接）。轮询刷新，首页横幅/设置页状态行直接订阅。 */
    val desktopOnline: StateFlow<DesktopLink?> = _desktopOnline.asStateFlow()

    private val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null

    /**
     * 启动 PC 在线状态轮询（App 启动时调用，进程生命周期常驻）。
     *
     * 判定优先级：
     * 1. Wi-Fi：收到 PC 心跳（UDP 9112）且 <60s → 在线（心跳 3s 一次，收到即服务存活，免 HTTP 探测）
     * 2. 通用：HTTP GET /health 探测 readEndpoint()（空 host 自动回退 127.0.0.1 → USB 即插即连）
     * 3. 均不可达 → null（UI 显示未连接）
     */
    fun startOnlineWatch() {
        if (watchJob?.isActive == true) return
        watchJob = watchScope.launch {
            while (true) {
                val link = runCatching { detectOnline() }.getOrNull()
                _desktopOnline.value = link
                // v23.6.1：探测到 PC 后主动回执设备指纹（USB/心跳被拦场景 PC 端也能感知在线）
                if (link != null) {
                    UdpDesktopDiscoveryService.sendDeviceHello(
                        context, link.host, link.port.toIntOrNull() ?: 8765)
                }
                delay(10_000L)
            }
        }
    }

    private suspend fun detectOnline(): DesktopLink? {
        // 1) Wi-Fi 心跳（PC 每秒级广播，收到即在线）
        val found = UdpDesktopDiscoveryService.getDiscoveredDesktop(context)
        if (found != null && System.currentTimeMillis() - found.lastSeenAtMs < 60_000L) {
            return DesktopLink(viaUsb = false, pcName = found.pcName.ifBlank { "电脑端" },
                               host = found.host, port = found.port.toString())
        }
        // 2) 历史发现 IP 的 /health 探测兜底（v23.6.1）：部分路由器丢弃 UDP 广播
        //    导致心跳收不到，但 PC 的 IP 很少变 —— 对最近一次发现的地址直连探测
        if (found != null && found.host.isNotBlank() &&
            isLocalNetworkHost(found.host) &&
            pingHealth(found.host, found.port.toString())) {
            return DesktopLink(viaUsb = false, pcName = found.pcName.ifBlank { "电脑端" },
                               host = found.host, port = found.port.toString())
        }
        // 3) /health 探测配置地址（覆盖 USB 回环与手动填写的场景）
        val (host, port, _) = readEndpoint() ?: return null
        return if (pingHealth(host, port)) {
            DesktopLink(viaUsb = host.startsWith("127."), pcName = "电脑端",
                        host = host, port = port)
        } else null
    }

    /**
     * 手动测试连接（设置页「测试连接」按钮）：/health 握手，返回延迟与结果文案。
     * 空 host 自动回退 127.0.0.1（USB 即插即测）。
     */
    suspend fun pingDesktop(): SyncResult = withContext(NonCancellable + Dispatchers.IO) {
        val (host, port, _) = readEndpoint()
            ?: return@withContext SyncResult(false, MSG_NO_ENDPOINT)
        val start = System.currentTimeMillis()
        return@withContext if (pingHealth(host, port)) {
            val label = if (host.startsWith("127.")) "USB 连接" else "Wi-Fi 连接"
            SyncResult(true, "连接成功：$label（$host:$port，${System.currentTimeMillis() - start}ms）")
        } else {
            SyncResult(false, "无法连接 $host:$port：请确认 PC 端同步服务已启动（同一 Wi-Fi 或 USB）")
        }
    }

    /** GET /health 探测（免鉴权），2.5s 超时。 */
    private fun pingHealth(host: String, port: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("http://$host:$port/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2_500
                readTimeout = 2_500
                setRequestProperty("Connection", "close")  // v23.6.1：绕开隧道 keep-alive 复用
            }
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 手机 → PC：整库备份推送（PC 自动合并）。
     *
     * 流程：backupToCache 生成临时 zip → POST /upload → 解析 JSON 响应 → 清理临时文件。
     */
    suspend fun pushBackup(): SyncResult = withContext(NonCancellable + Dispatchers.IO) {
        val (host, port, token) = readEndpoint()
            ?: return@withContext SyncResult(false, MSG_NO_ENDPOINT)

        // 1. 生成整库备份到 cacheDir（复用恢复前安全备份的同一打包链路）
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern(
            "yyyyMMdd_HHmmss", Locale.getDefault()))
        val backupFile = File(context.cacheDir, "smty_sync_$ts.smty_backup")
        try {
            val r = backupRepo.backupToCache(backupFile)
            if (!r.success || !backupFile.exists()) {
                return@withContext SyncResult(false, "备份生成失败：${r.message}")
            }

            // 2. POST /upload
            pushStream(backupFile.inputStream(), backupFile.length(), backupFile.name,
                       host, port, token)
        } finally {
            backupFile.delete()
        }
    }

    /**
     * 手机 → PC：推送一个已生成的备份文件（v23 自动备份钩子复用）。
     *
     * 挂起函数，内部切换到 Dispatchers.IO。
     */
    suspend fun pushBackupFile(file: File): SyncResult = withContext(NonCancellable + Dispatchers.IO) {
        val (host, port, token) = readEndpoint()
            ?: return@withContext SyncResult(false, MSG_NO_ENDPOINT)
        file.inputStream().use { input ->
            pushStream(input, file.length(), file.name, host, port, token)
        }
    }

    /**
     * 手机 → PC：推送 SAF Uri 备份文件（手动「一键备份」成功后按开关自动推送）。
     *
     * 挂起函数，内部切换到 Dispatchers.IO；Uri 不可读或大小为 0 时返回失败结果。
     */
    suspend fun pushBackupUri(context: Context, uri: android.net.Uri,
                              name: String): SyncResult = withContext(NonCancellable + Dispatchers.IO) {
        val size = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: 0L
        if (size <= 0) {
            return@withContext SyncResult(false, "备份文件不可读")
        }
        val (host, port, token) = readEndpoint()
            ?: return@withContext SyncResult(false, MSG_NO_ENDPOINT)
        context.contentResolver.openInputStream(uri)?.use { input ->
            pushStream(input, size, name, host, port, token)
        } ?: SyncResult(false, "备份文件流打开失败")
    }

    /**
     * 手机 → PC：推送字节流到 PC 同步服务（pushBackup / 自动钩子共用底层）。
     *
     * 阻塞实现，调用方须保证运行在 Dispatchers.IO。
     *
     * @param input 备份字节流（由调用方负责关闭）
     * @param size 字节流大小（Content-Length）
     * @param name X-Backup-Name（PC 端保存文件名，PC 端仅取文件名部分防穿越）
     */
    private fun pushStream(
        input: InputStream,
        size: Long,
        name: String,
        host: String,
        port: String,
        token: String
    ): SyncResult {
        val urlStr = "http://$host:$port/upload"
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                useCaches = false
                if (token.isNotEmpty()) setRequestProperty("X-Sync-Token", token)
                // v23.6.1：禁用 keep-alive 复用（adb reverse 隧道对连接复用不稳定，
                // POST 后立即 GET 曾 100% unexpected end of stream）
                setRequestProperty("Connection", "close")
                setRequestProperty("X-Backup-Name", name)
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Content-Length", size.toString())
            }
            conn.outputStream.use { out ->
                streamCopy(input, out)
                out.flush()
            }

            val code = conn.responseCode
            val body = (conn.errorStream ?: conn.inputStream)?.bufferedReader()
                ?.use { it.readText() } ?: ""
            if (code == 200) {
                val json = runCatching { JSONObject(body) }.getOrNull()
                if (json != null && json.optInt("code", 1) == 0) {
                    val restored = json.optInt("restored", 0)
                    Log.i(TAG, "备份推送成功：$name → PC 合并 $restored 个档案")
                    return SyncResult(true, "备份已推送到 PC（PC 端合并 $restored 个档案）", code)
                }
                return SyncResult(false, "PC 端合并失败：${json?.optString("message") ?: body}",
                                  code)
            }
            Log.w(TAG, "推送失败 HTTP $code: $body")
            return SyncResult(false, "PC 端响应异常 (HTTP $code)：$body", code)
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "连接失败：${e.message}")
            return SyncResult(false, "无法连接 PC 端，请确认已启动同步服务", 0)
        } catch (e: Exception) {
            Log.w(TAG, "推送异常：${e.message}")
            return SyncResult(false, "推送异常：${e.message ?: "未知错误"}", 0)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * PC → 手机：拉取学员汇总 Excel 并合并（UPDATE_PART）。
     *
     * v23.6.1：外层重试一次——旧版 PC 服务（HTTP/1.0）或连接复用边缘会抛
     * "unexpected end of stream"，新建连接重试即可成功。
     */
    suspend fun pullStudents(): SyncResult = withContext(NonCancellable + Dispatchers.IO) {
        val first = pullStudentsOnce()
        if (first.success) first else pullStudentsOnce()
    }

    private suspend fun pullStudentsOnce(): SyncResult = withContext(NonCancellable + Dispatchers.IO) {
        val cfg = readEndpoint() ?: return@withContext SyncResult(false, MSG_NO_ENDPOINT)
        val (host, port, token) = cfg

        val urlStr = "http://$host:$port/sync/students.xlsx"
        var conn: HttpURLConnection? = null
        val downloadFile = File(context.cacheDir, "students_sync.xlsx")
        // v23.6.1 诊断插桩：分步写 cache/smty_debug.log 定位取消点
        fun dbg(step: String) = runCatching {
            java.io.File(context.cacheDir, "smty_debug.log").appendText(
                "$step ${java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date())}\n")
        }
        try {
            dbg("pull enter host=$host port=$port")
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                if (token.isNotEmpty()) setRequestProperty("X-Sync-Token", token)
                // v23.6.1：禁用 keep-alive 复用（adb reverse 隧道对连接复用不稳定，
                // POST 后立即 GET 曾 100% unexpected end of stream）
                setRequestProperty("Connection", "close")
            }
            val code = conn.responseCode
            dbg("pull http code=$code")
            if (code != 200) {
                val body = (conn.errorStream ?: conn.inputStream)?.bufferedReader()
                    ?.use { it.readText() } ?: ""
                return@withContext SyncResult(false, "PC 端响应异常 (HTTP $code)：$body", code)
            }
            conn.inputStream.use { input ->
                FileOutputStream(downloadFile).use { out -> streamCopy(input, out) }
            }
            val fileBytes = downloadFile.length()
            dbg("pull downloaded $fileBytes bytes")

            // 解析 Excel（智能表头映射）→ UPDATE_PART 合并
            val students = downloadFile.inputStream().use { stream ->
                ExcelSync.importStudentsFromTabularExcel(listOf(stream))
            }
            dbg("pull parsed ${students.size} students")
            // v23.6.1：全量录入（不再因身高体重缺失拒绝整包）；
            // PC 端未录（<=0）的身高/体重/BMI 在 UPDATE_PART 合并时按「空值不覆盖」处理，
            // 不会破坏手机端已录数据（StudentRepository.UPDATE_PART）
            if (students.isEmpty()) {
                // v23.6.1 诊断：vivo ROM 屏蔽 logcat，Toast 是唯一可见诊断通道
                return@withContext SyncResult(
                    false,
                    "PC 端暂无可同步的学员（收到 $fileBytes bytes，解析 0 人）", code)
            }
            val result = try {
                // v23.6.1：阻塞 DAO 导入——Room 2.7 suspend DAO 会把外部取消传播进
                // 写库链（NonCancellable/独立 Job 均复现），改用阻塞 DAO 彻底绕开
                withContext(Dispatchers.IO) {
                    studentRepo.importStudentsBlockingUpdatePart(students)
                }
            } catch (e: Exception) {
                // v23.6.1 深度诊断：当前协程 Job 状态 + suppressed（取消点堆栈）
                val ctxJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                val sup = e.suppressed?.joinToString(" || ") { it.toString() } ?: ""
                runCatching {
                    java.io.File(context.cacheDir, "smty_debug.log").appendText(
                        "=== import fail ctxJob=$ctxJob active=${ctxJob?.isActive}\n" +
                        "suppressed=[$sup]\n" + e.stackTraceToString() + "\n")
                }
                throw e
            }
            dbg("pull merged: ${result.toUserMessage()}")
            Log.i(TAG, "学员拉取合并完成：${result.toUserMessage()}")
            SyncResult(true, "PC 端数据已同步：${result.toUserMessage()}", code)
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "连接失败：${e.message}")
            SyncResult(false, "无法连接 PC 端，请确认已启动同步服务", 0)
        } catch (e: Exception) {
            // v23.6.1 诊断：vivo 屏蔽 logcat，堆栈写文件（cache/smty_debug.log）
            runCatching {
                java.io.File(context.cacheDir, "smty_debug.log").appendText(
                    "=== pull fail ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}\n" +
                    e.stackTraceToString() + "\n")
            }
            Log.w(TAG, "拉取异常：${e.message}")
            SyncResult(false, "拉取异常：${e.message}", 0)
        } finally {
            conn?.disconnect()
            downloadFile.delete()
        }
    }

    /**
     * 一键双向同步：先推备份（手机→PC），再拉学员数据（PC→手机）。
     * 两个方向独立成败，消息聚合返回；任一方向成功即 success=true。
     */
    suspend fun syncNow(): SyncResult {
        val push = pushBackup()
        val pull = pullStudents()
        // v23.7：拉取学员成功后顺带拉课时包（PC 录入的课时/总量同步到手机）
        val pkgs = if (pull.success) pullPackages() else null
        val parts = mutableListOf<String>()
        if (push.success) parts.add(push.message) else parts.add("推送失败：${push.message}")
        if (pull.success) parts.add(pull.message) else parts.add("拉取失败：${pull.message}")
        if (pkgs != null && pkgs.message.isNotBlank()) parts.add(pkgs.message)
        val success = push.success || pull.success
        return SyncResult(success, parts.joinToString("\n"),
                          if (push.success) push.httpCode else pull.httpCode)
    }

    /**
     * v23.7：PC → 手机 课时包同步。
     *
     * 数据源：PC 端课时记录.xlsx 汇总（GET /sync/packages.json）。
     * 合并语义（与手机→PC 的 set_total_lessons 对称）：
     * - 学员手机端无课时包 → 新建一条「PC 同步」包（总量/已用取 PC 值）
     * - 已有课时包 → 取最近创建的一条，更新总课时与已用（PC 值为准），
     *   其余历史包不动（不覆盖价格/备注/状态等本地字段）
     */
    private suspend fun pullPackages(): SyncResult = withContext(NonCancellable + Dispatchers.IO) {
        val repo = lessonPackageRepo
            ?: return@withContext SyncResult(false, "课时包同步服务不可用", 0)
        val (host, port, token) = readEndpoint()
            ?: return@withContext SyncResult(false, MSG_NO_ENDPOINT, 0)
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("http://$host:$port/sync/packages.json").openConnection()
                    as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = 15_000
                if (token.isNotEmpty()) setRequestProperty("X-Sync-Token", token)
                setRequestProperty("Connection", "close")
            }
            val code = conn.responseCode
            if (code != 200) {
                return@withContext SyncResult(false, "", code)  // 静默：拉取主体已成功
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optInt("code", 1) != 0) {
                return@withContext SyncResult(false, "", code)
            }
            val arr = json.optJSONArray("packages") ?: org.json.JSONArray()
            val today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            var added = 0
            var updated = 0
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("studentName").trim()
                if (name.isBlank()) continue
                val total = obj.optInt("totalLessons", 0)
                val used = obj.optInt("usedLessons", 0)
                // v23.7.1 数据保护：PC 端无课时数据（total=0 且 used=0）时绝不动手机现值
                //（曾把手机真实课时包全部覆盖清零——PC 汇总的 0 不代表"清空"）
                if (total <= 0 && used <= 0) continue
                val existing = repo.getPackagesByStudentBlocking(name)
                if (existing.isEmpty()) {
                    // 新建：只取 PC 总量，已用从 0 起（手机端扣课事实自累计）
                    repo.addPackageBlocking(
                        com.shangmentiyu.sportscoach.data.model.LessonPackage(
                            studentName = name,
                            name = "PC 同步",
                            totalLessons = total,
                            usedLessons = 0,
                            purchaseDate = today,
                            note = "PC 端同步（课时记录汇总）"
                        )
                    )
                    added++
                } else {
                    // v23.7.2：只同步总课时。usedLessons 是手机端扣课事实（含无记录
                    // 的纯扣课），PC 端无法精确重建，绝不用 PC 值覆盖
                    val pkg = existing.first()
                    if (pkg.totalLessons != total) {
                        repo.updatePackageBlocking(pkg.copy(totalLessons = total))
                        updated++
                    }
                }
            }
            Log.i(TAG, "课时包同步完成：新增 $added，更新 $updated")
            SyncResult(
                true,
                if (added + updated > 0) "课时包同步：新增 $added 个，更新 $updated 个" else "",
                code
            )
        } catch (e: Exception) {
            Log.w(TAG, "课时包同步异常：${e.message}")
            SyncResult(false, "课时包同步异常：${e.message ?: "未知错误"}", 0)
        } finally {
            conn?.disconnect()
        }
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /** 读取并校验 PC 端连接配置；无效时返回 null（调用方返回错误结果） */
    private suspend fun readEndpoint(): Triple<String, String, String>? {
        val host = settings.syncHost.first().trim()
        val port = settings.syncPort.first().trim()
            .ifBlank { SettingsRepository.DEFAULT_SYNC_PORT }
        val token = settings.syncToken.first().trim()
        return when {
            // 已配置局域网地址
            host.isNotBlank() && isLocalNetworkHost(host) -> Triple(host, port, token)
            // v23.6.1：未配置地址时优先用最近一次发现的历史 PC IP（UDP 广播被路由器
            // 拦截时 Wi-Fi 仍可直连；PC 的 IP 很少变），否则回退 127.0.0.1（USB）
            host.isBlank() -> {
                val lastFound = UdpDesktopDiscoveryService.getDiscoveredDesktop(context)
                if (lastFound != null && isLocalNetworkHost(lastFound.host)) {
                    Triple(lastFound.host, lastFound.port.toString(),
                           lastFound.token.ifBlank { token })
                } else {
                    Triple("127.0.0.1", port, token)
                }
            }
            else -> null
        }
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
}
