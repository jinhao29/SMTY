package com.shangmentiyu.sportscoach.core

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 全局协程异常处理扩展（处理器层）。
 *
 * v24 优化4 引入：为各 ViewModel 的 viewModelScope.launch 提供统一的异常兜底，
 * 避免数据库死锁、IO 异常、JSON 解析错误等未捕获异常直接导致 App 崩溃。
 *
 * === v34 紧急排查 NPE 优化 ===
 * 之前问题：
 * - NPE 被 catch 后只弹 "(NullPointerException)" 黑色 Toast，真实堆栈被吞
 * - Log.e 只输出 throwable.message（NPE message 通常为 null），看不到堆栈
 * - 用户/开发者无法定位到底是哪一行代码的哪个变量为 null
 *
 * 修复策略：
 * - NPE 做特殊处理：Toast 文案改为"已记录到日志，请连接 adb logcat 查看 CrashDumper 标签"
 * - Logcat 输出使用 Log.e(tag, msg, throwable) 完整堆栈（ throwable 参数会被 Android Studio Logcat 自动展开）
 * - 同步调用 CrashDumper.dumpToLogFile 写入 crash_logs 文件夹，路径明确
 * - 对 NPE 额外输出导致问题的调用栈顶 5 帧，便于快速定位
 *
 * 设计要点：
 * - 通过 [CoroutineExceptionHandler] 捕获协程内未处理的异常
 * - 异常落盘到 [CrashHandler] 的崩溃日志目录，便于事后排查
 * - 通过 [toastMessage] StateFlow 向 UI 层抛出轻量 Toast 提示
 * - 绝不吞掉异常而不记录：每条异常都写入 Logcat + 本地日志文件
 */
object CoroutineExt {

    /** Toast 提示的统一文案，避免硬编码分散在各处 */
    private const val DEFAULT_ERROR_TOAST = "操作遇到异常，请重试"

    /**
     * 构造 Toast 文案（v34：对 NPE 做特殊处理）。
     *
     * - 非 NPE 异常：保留原行为 "操作遇到异常，请重试（异常类型）"
     * - NPE：改为 "已记录到日志，请连接 adb logcat 查看 CrashDumper 标签"
     *   原因：NPE 的 "（NullPointerException）" 文案对用户毫无意义，
     *   反而让用户误以为 App 坏了。改为引导开发者查看日志，让用户继续操作不受影响。
     *
     * @param throwable 协程未捕获异常
     * @return 组合后的 Toast 文案
     */
    private fun buildToastMessage(throwable: Throwable): String {
        val type = throwable.javaClass.simpleName
        // v34：NPE 特殊文案，引导开发者查看 Logcat
        if (throwable is NullPointerException) {
            return "操作异常已记录到日志，请连接 adb logcat 查看 CrashDumper 标签"
        }
        return if (type.isBlank()) DEFAULT_ERROR_TOAST else "$DEFAULT_ERROR_TOAST（$type）"
    }

    /**
     * 创建应用级协程异常处理器。
     *
     * 捕获异常后的处理流程（v34 强化）：
     * 1. **Logcat 完整堆栈输出**：使用 `Log.e(tag, msg, throwable)` 三参数重载，
     *    Android Studio Logcat 会自动展开 throwable 的完整堆栈，便于直接定位
     * 2. **crash_logs 文件落盘**：调用 CrashDumper.dumpToLogFile 同步写入
     *    `filesDir/crash_logs/crash_<时间戳>.txt`，路径明确便于 adb pull
     * 3. **NPE 特殊处理**：额外输出调用栈顶 5 帧，让开发者在 Logcat 直接看到
     *    到底是哪一行代码的哪个变量为 null
     * 4. **UI 提示**：NPE 时引导开发者查看日志，非 NPE 保留原异常类型提示
     *
     * @param toastSink Toast 消息接收 StateFlow（通常为 ViewModel 的 _toast）
     * @param contextTag 日志标签，用于区分异常来源
     * @return CoroutineExceptionHandler 实例
     */
    fun createAppExceptionHandler(
        toastSink: MutableStateFlow<String?>,
        contextTag: String = "ViewModel"
    ): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            // === 1. Logcat 完整堆栈输出（v34 强化） ===
            // 关键：使用三参数 Log.e(tag, msg, throwable)，
            // Android Studio Logcat 会自动展开 throwable 的完整堆栈
            val logTag = "CrashDumper"  // 统一使用 CrashDumper 标签，便于 adb logcat -s CrashDumper:* 过滤
            val timestamp = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()
            ).format(java.util.Date())

            // 构造完整日志消息（含上下文信息，便于定位）
            val logMessage = buildString {
                append("[$contextTag] 协程未捕获异常\n")
                append("时间: $timestamp\n")
                append("异常类型: ${throwable.javaClass.name}\n")
                append("异常消息: ${throwable.message ?: "(null)"}\n")
                append("线程: ${Thread.currentThread().name}")
            }

            // 使用三参数 Log.e：throwable 会被 Android Studio Logcat 自动展开完整堆栈
            android.util.Log.e(logTag, logMessage, throwable)

            // === 2. NPE 特殊处理：输出调用栈顶 5 帧，让开发者直接看到哪行代码 NPE ===
            if (throwable is NullPointerException) {
                val topFrames = throwable.stackTrace.take(5).joinToString("\n") { frame ->
                    "  at ${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})"
                }
                android.util.Log.e(
                    logTag,
                    "════════════════════════════════════════\n" +
                        "NullPointerException 调用栈顶 5 帧（定位用）：\n" +
                        "$topFrames\n" +
                        "提示：检查上述文件中对应行的变量是否为 null\n" +
                        "════════════════════════════════════════"
                )
            }

            // === 3. 落盘到 crash_logs 文件夹（v34：使用 CrashDumper 统一工具） ===
            try {
                // 构造完整日志内容（含完整堆栈）
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                val fullLog = buildString {
                    append("════════════════════════════════════════\n")
                    append("时间: $timestamp\n")
                    append("标签: $contextTag\n")
                    append("异常类型: ${throwable.javaClass.name}\n")
                    append("异常消息: ${throwable.message ?: "(null)"}\n")
                    append("线程: ${Thread.currentThread().name}\n")
                    append("堆栈:\n")
                    append(sw.toString())
                    append("════════════════════════════════════════\n")
                }
                // 写入 crash_logs 文件夹（CrashHandler.writeLog 内部已 synchronized）
                CrashHandler.writeLog(fullLog)
            } catch (e: Exception) {
                android.util.Log.e(logTag, "异常日志落盘失败：${e.message}", e)
            }

            // === 4. 向 UI 推送 Toast 提示（v34：NPE 引导查看日志） ===
            toastSink.value = buildToastMessage(throwable)
        }
    }
}

/**
 * ViewModel 安全启动扩展函数。
 *
 * 自动挂载 [CoroutineExt.createAppExceptionHandler] 到 viewModelScope，
 * 使协程内的未捕获异常被统一处理，避免 App 崩溃。
 *
 * @param toastSink Toast 消息接收 StateFlow
 * @param contextTag 日志标签
 * @param block 协程体
 */
fun ViewModel.safeLaunch(
    toastSink: MutableStateFlow<String?>,
    contextTag: String = this.javaClass.simpleName,
    block: suspend () -> Unit
) {
    val handler = CoroutineExt.createAppExceptionHandler(toastSink, contextTag)
    viewModelScope.launch(handler) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消不应视为异常，直接重新抛出
            throw e
        }
    }
}
