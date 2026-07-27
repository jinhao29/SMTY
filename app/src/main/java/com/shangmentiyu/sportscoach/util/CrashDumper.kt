package com.shangmentiyu.sportscoach.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃堆栈强制输出工具（v34 紧急排查 NPE 专用）。

 * 背景：
 * SettingsViewModel 中的 safeLaunch + appExceptionHandler 已经捕获了所有协程内异常，
 * 但 UI 层看到的是 "(NullPointerException)" 这种短提示，真实堆栈被吞掉。
 * CrashHandler.writeLog 虽然会落盘，但写入目录路径隐蔽，用户无法快速查看。
 *
 * 此工具提供：
 * 1. [dumpToLogcat]：把完整堆栈通过 Log.e 强制输出到 Logcat（adb logcat 可见）
 * 2. [dumpToLogFile]：把完整堆栈写入 crash_logs 文件夹，路径明确（filesDir/crash_logs/）
 * 3. [dumpBoth]：同时输出到 Logcat + 文件，用于关键场景
 *
 * 调用方应在 catch 块中调用，传入异常对象与调用上下文信息：
 * ```kotlin
 * try {
 *     riskyIO()
 * } catch (e: Exception) {
 *     CrashDumper.dumpBoth(context, "SettingsScreen.exportDirLauncher", e)
 *     Toast.makeText(context, "导出路径异常，请检查存储空间", Toast.LENGTH_SHORT).show()
 * }
 * ```
 */
object CrashDumper {

    private const val TAG = "CrashDumper"
    private const val CRASH_DIR_NAME = "crash_logs"

    /**
     * 把异常堆栈强制输出到 Logcat（adb logcat 可见）。
     *
     * @param tag 日志标签，建议格式：模块名.函数名（如 "SettingsScreen.exportDirLauncher"）
     * @param e 异常对象
     * @param extraContext 额外上下文信息（如参数值、状态等），可选
     */
    fun dumpToLogcat(tag: String, e: Throwable, extraContext: String? = null) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(Date())
            val log = buildString {
                append("════════════════════════════════════════\n")
                append("时间: $timestamp\n")
                append("标签: $tag\n")
                if (extraContext != null) {
                    append("上下文: $extraContext\n")
                }
                append("异常类型: ${e.javaClass.name}\n")
                append("异常消息: ${e.message ?: "(null)"}\n")
                append("线程: ${Thread.currentThread().name}\n")
                append("堆栈:\n")
                append(stackTrace)
                append("════════════════════════════════════════\n")
            }
            // 强制输出到 Logcat（adb logcat -s CrashDumper:* 可过滤查看）
            Log.e(TAG, log)
        } catch (_: Exception) {
            // 工具自身异常不阻塞调用方
        }
    }

    /**
     * 把异常堆栈写入 crash_logs 文件夹。
     *
     * 文件路径：filesDir/crash_logs/crash_<时间戳>.txt
     * 用户可通过 adb pull / data / data / <pkg> / files / crash_logs / 拉取查看
     *
     * @param context 上下文（用于获取 filesDir）
     * @param tag 日志标签
     * @param e 异常对象
     * @param extraContext 额外上下文信息，可选
     * @return 写入的日志文件绝对路径；失败时返回 null
     */
    fun dumpToLogFile(
        context: Context,
        tag: String,
        e: Throwable,
        extraContext: String? = null
    ): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(Date())
            val fileTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
                .format(Date())

            val crashDir = File(context.filesDir, CRASH_DIR_NAME)
            if (!crashDir.exists() && !crashDir.mkdirs()) {
                Log.e(TAG, "创建 crash_logs 目录失败")
                return null
            }

            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val log = buildString {
                append("════════════════════════════════════════\n")
                append("时间: $timestamp\n")
                append("标签: $tag\n")
                if (extraContext != null) {
                    append("上下文: $extraContext\n")
                }
                append("异常类型: ${e.javaClass.name}\n")
                append("异常消息: ${e.message ?: "(null)"}\n")
                append("线程: ${Thread.currentThread().name}\n")
                append("堆栈:\n")
                append(sw.toString())
                append("════════════════════════════════════════\n")
            }

            val logFile = File(crashDir, "crash_$fileTimestamp.txt")
            logFile.writeText(log)
            Log.i(TAG, "崩溃日志已写入: ${logFile.absolutePath}")
            logFile.absolutePath
        } catch (writeEx: Exception) {
            Log.e(TAG, "写入崩溃日志失败: ${writeEx.message}", writeEx)
            null
        }
    }

    /**
     * 同时输出到 Logcat + 写入 crash_logs 文件（推荐用法）。
     *
     * 在关键 IO 操作的 catch 块中调用此方法，确保异常堆栈被完整记录。
     *
     * @param context 上下文
     * @param tag 日志标签
     * @param e 异常对象
     * @param extraContext 额外上下文信息，可选
     */
    fun dumpBoth(
        context: Context,
        tag: String,
        e: Throwable,
        extraContext: String? = null
    ) {
        dumpToLogcat(tag, e, extraContext)
        dumpToLogFile(context, tag, e, extraContext)
    }
}
