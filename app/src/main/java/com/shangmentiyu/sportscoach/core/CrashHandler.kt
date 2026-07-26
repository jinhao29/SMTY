package com.shangmentiyu.sportscoach.core

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃日志捕获器（处理器层）。
 *
 * 职责：
 * - 实现 [Thread.UncaughtExceptionHandler]，捕获所有未处理异常
 * - 将崩溃堆栈写入 `context.filesDir/crash_logs/crash_${timestamp}.txt`
 * - 透传给系统默认 Handler，保证进程按原有流程终止（不阻断默认崩溃对话框）
 *
 * 使用方式：
 * - 在 [android.app.Application.onCreate] 起始处调用 [install]
 * - 在 [android.app.Application.onCreate] 中调用 [checkAndLogCrashFiles] 提示用户
 *
 * 设计说明（v23 引入）：
 * - 仅在主线程安装一次，重复 install 会幂等返回
 * - 日志文件按"崩溃时间戳"命名，避免覆盖
 * - 写文件操作在 IO 临界区同步执行（崩溃路径必须同步落盘，否则进程退出会丢失缓冲）
 * - 与 [android.util.Log] 输出双写：Logcat 便于开发期调试，文件便于用户反馈
 */
class CrashHandler private constructor(
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"

        /** 崩溃日志目录名（位于 filesDir 下） */
        private const val CRASH_DIR_NAME = "crash_logs"

        /** 崩溃日志文件名前缀 */
        private const val CRASH_FILE_PREFIX = "crash_"

        /** 崩溃日志文件扩展名 */
        private const val CRASH_FILE_SUFFIX = ".txt"

        /** 单例引用，避免被 GC 后重新 install 覆盖系统默认 Handler */
        @Volatile
        private var instance: CrashHandler? = null

        /**
         * 安装全局崩溃捕获器（幂等）。
         *
         * 必须在 Application.onCreate 早期调用，确保后续所有线程异常都能被捕获。
         * 重复调用安全：已安装时直接返回，不会覆盖系统默认 Handler 引用。
         *
         * @param context 应用级 Context（使用 applicationContext 避免内存泄漏）
         */
        fun install(context: Context) {
            if (instance != null) {
                Log.d(TAG, "CrashHandler 已安装，跳过重复 install")
                return
            }
            val app = context.applicationContext
            val handler = CrashHandler(app)
            // 保存系统默认 Handler，用于异常透传（保证进程正常终止 & 默认对话框弹出）
            handler.defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(handler)
            instance = handler
            Log.i(TAG, "CrashHandler 已安装，崩溃日志将写入 ${app.filesDir}/${CRASH_DIR_NAME}/")
        }

        /**
         * 启动时检查是否存在崩溃日志文件，如有则在 Logcat 提示。
         *
         * 调用时机：Application.onCreate 中（install 之后）。
         * 输出形式：
         * - Logcat W 级日志，列出最近一次崩溃日志路径与总文件数
         * - 不在 UI 层弹出对话框（避免阻塞启动；UI 层提示由设置页或对话框按需读取）
         *
         * @param context 应用级 Context
         * @return 最近一次崩溃日志文件（无则返回 null）
         */
        fun checkAndLogCrashFiles(context: Context): File? {
            val crashDir = File(context.applicationContext.filesDir, CRASH_DIR_NAME)
            if (!crashDir.exists()) return null
            val files = crashDir.listFiles { f ->
                f.isFile && f.name.startsWith(CRASH_FILE_PREFIX) && f.name.endsWith(CRASH_FILE_SUFFIX)
            }?.sortedByDescending { it.lastModified() } ?: return null
            if (files.isEmpty()) return null

            val latest = files.first()
            Log.w(TAG, "检测到 ${files.size} 个崩溃日志，最近一次：${latest.absolutePath}")
            Log.w(TAG, "如需排查可读取该文件；清理后此提示将不再出现")
            return latest
        }

        /**
         * 将一段日志文本写入崩溃日志目录（供协程异常处理器等非崩溃路径使用）。
         *
         * 与 [uncaughtException] 不同，此方法不会触发进程终止，仅做日志落盘：
         * - 文件名前缀改为 `log_`（区分于崩溃日志 `crash_`）
         * - 写入失败时仅 Logcat 输出，不抛异常
         * - 线程安全：内部 synchronized 防止并发写入文件名冲突
         *
         * v24 优化4 引入：被 [CoroutineExt.createAppExceptionHandler] 调用，
         * 将协程内未捕获异常的堆栈同步落盘，便于事后排查。
         *
         * @param content 日志内容（含堆栈、时间戳等）
         */
        @Synchronized
        fun writeLog(content: String) {
            try {
                // 使用 Application Context 获取 filesDir
                // instance 持有 applicationContext，单例已安装即可使用
                val ctx = instance?.context ?: return
                val crashDir = File(ctx.filesDir, CRASH_DIR_NAME)
                if (!crashDir.exists()) {
                    if (!crashDir.mkdirs()) {
                        Log.w(TAG, "writeLog: 创建日志目录失败：${crashDir.absolutePath}")
                        return
                    }
                }
                val timestamp = System.currentTimeMillis()
                val fileName = "log_${FILE_DATE_FORMAT.format(Date(timestamp))}.txt"
                val logFile = File(crashDir, fileName)
                logFile.writeText(content)
                Log.i(TAG, "协程异常日志已写入：${logFile.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "writeLog 写入失败：${e.message}")
            }
        }

        /**
         * 删除所有崩溃日志文件（供设置页"清理崩溃日志"功能调用）。
         *
         * @param context 应用级 Context
         * @return 已删除文件数；目录不存在返回 0
         */
        fun clearAllCrashLogs(context: Context): Int {
            val crashDir = File(context.applicationContext.filesDir, CRASH_DIR_NAME)
            if (!crashDir.exists()) return 0
            val files = crashDir.listFiles { f ->
                f.isFile && f.name.startsWith(CRASH_FILE_PREFIX) && f.name.endsWith(CRASH_FILE_SUFFIX)
            } ?: return 0
            var deleted = 0
            for (f in files) {
                if (f.delete()) deleted++
            }
            Log.i(TAG, "已清理 $deleted 个崩溃日志文件")
            return deleted
        }

        /** 时间戳格式：用于崩溃日志文件名（精确到秒，避免毫秒级冲突难以辨认） */
        private val FILE_DATE_FORMAT =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

        /** 时间戳格式：用于崩溃日志内容首行（人类可读） */
        private val LOG_DATE_FORMAT =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    }

    /** 系统默认 UncaughtExceptionHandler，捕获后透传，保证进程按原流程终止 */
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * 捕获未处理异常。
     *
     * 流程：
     * 1. 同步写入崩溃日志文件（必须同步，否则进程退出会丢失缓冲）
     * 2. Logcat W 级输出，便于开发期调试
     * 3. 透传给系统默认 Handler（让进程正常终止 & 默认对话框弹出）
     *
     * 异常兜底：写文件过程本身抛异常时，仅 Logcat 输出，不阻断透传。
     *
     * @param t 发生异常的线程
     * @param e 未捕获的异常
     */
    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val timestamp = System.currentTimeMillis()
            val logContent = buildLogContent(t, e, timestamp)
            // 同步落盘：崩溃路径必须立即写入，否则进程退出会丢失缓冲数据
            writeLogToFile(timestamp, logContent)
            // 同步 Logcat 输出：开发期可立即查看
            Log.e(TAG, "未捕获异常（线程：${t.name}）\n$logContent", e)
        } catch (_: Throwable) {
            // 写文件 / Logcat 失败时不能阻断透传给默认 Handler
            // 否则进程无法正常终止，用户只能强杀
        }
        // 透传给系统默认 Handler，保证默认崩溃对话框与进程终止流程不变
        defaultHandler?.uncaughtException(t, e)
    }

    /**
     * 构建崩溃日志文本内容。
     *
     * 包含字段：
     * - 崩溃时间（人类可读）
     * - 进程 / 线程信息
     * - 设备型号 / SDK 版本（便于环境复现）
     * - App 版本（从 PackageInfo 读取）
     * - 完整堆栈
     *
     * @param t 发生异常的线程
     * @param e 异常
     * @param timestamp 崩溃时间戳（毫秒）
     * @return 完整日志文本
     */
    private fun buildLogContent(t: Thread, e: Throwable, timestamp: Long): String {
        val timeStr = LOG_DATE_FORMAT.format(Date(timestamp))
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val appVersion = try {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageInfo(context.packageName, 0)
            "${pkgInfo.versionName}(${pkgInfo.longVersionCode})"
        } catch (_: Throwable) {
            "unknown"
        }

        return buildString {
            appendLine("===== Crash Report =====")
            appendLine("Time: $timeStr")
            appendLine("Thread: ${t.name} (id=${t.id})")
            appendLine("Process: ${android.os.Process.myPid()}")
            appendLine("AppVersion: $appVersion")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(",")}")
            appendLine("----- Stack Trace -----")
            appendLine(stackTrace)
            appendLine("===== End of Report =====")
        }
    }

    /**
     * 将崩溃日志写入文件（同步）。
     *
     * 路径：`context.filesDir/crash_logs/crash_${yyyyMMdd_HHmmss}.txt`
     *
     * 异常处理：
     * - 目录创建失败：仅 Logcat 输出，不阻断透传
     * - 文件写入失败：仅 Logcat 输出，不阻断透传
     *
     * @param timestamp 崩溃时间戳（毫秒）
     * @param content 日志内容
     */
    private fun writeLogToFile(timestamp: Long, content: String) {
        val crashDir = File(context.filesDir, CRASH_DIR_NAME)
        if (!crashDir.exists()) {
            val created = crashDir.mkdirs()
            if (!created) {
                Log.w(TAG, "创建崩溃日志目录失败：${crashDir.absolutePath}")
                return
            }
        }
        val fileName = "$CRASH_FILE_PREFIX${FILE_DATE_FORMAT.format(Date(timestamp))}$CRASH_FILE_SUFFIX"
        val logFile = File(crashDir, fileName)
        try {
            // 使用 writeText 默认 UTF-8 编码（与 C++ 通用规则一致，避免乱码）
            logFile.writeText(content)
            Log.i(TAG, "崩溃日志已写入：${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "写入崩溃日志失败：${e.message}")
        }
    }
}
