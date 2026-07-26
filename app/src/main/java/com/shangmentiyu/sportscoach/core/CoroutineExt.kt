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
 * 设计要点：
 * - 通过 [CoroutineExceptionHandler] 捕获协程内未处理的异常
 * - 异常落盘到 [CrashHandler] 的崩溃日志目录，便于事后排查
 * - 通过 [toastMessage] StateFlow 向 UI 层抛出轻量 Toast 提示
 * - 绝不吞掉异常而不记录：每条异常都写入 Logcat + 本地日志文件
 *
 * 使用方式（在 ViewModel 中）：
 * ```
 * private val _toast = MutableStateFlow<String?>(null)
 * val toast: StateFlow<String?> = _toast.asStateFlow()
 *
 * private val exceptionHandler = createAppExceptionHandler(_toast)
 *
 * fun someAction() {
 *     viewModelScope.launch(exceptionHandler) {
 *         // 业务逻辑，异常会被自动捕获
 *     }
 * }
 * ```
 *
 * 或使用扩展函数：
 * ```
 * fun someAction() = safeLaunch(_toast) {
 *     // 业务逻辑
 * }
 * ```
 */
object CoroutineExt {

    /** Toast 提示的统一文案，避免硬编码分散在各处 */
    private const val DEFAULT_ERROR_TOAST = "操作遇到异常，请重试"

    /**
     * 构造带异常类型提示的 Toast 文案。
     *
     * - 主文案保留 [DEFAULT_ERROR_TOAST] 便于用户识别
     * - 末尾追加异常类型简称，让用户/开发者能在 UI 直接看到异常源头
     *   （如 "操作遇到异常，请重试（GeneralSecurityException）"）
     * - 仅暴露类型，不暴露 message（避免泄漏敏感细节）
     *
     * @param throwable 协程未捕获异常
     * @return 组合后的 Toast 文案
     */
    private fun buildToastMessage(throwable: Throwable): String {
        val type = throwable.javaClass.simpleName
        return if (type.isBlank()) DEFAULT_ERROR_TOAST else "$DEFAULT_ERROR_TOAST（$type）"
    }

    /**
     * 创建应用级协程异常处理器。
     *
     * 捕获异常后的处理流程：
     * 1. 通过 Logcat 输出错误日志（含堆栈）
     * 2. 落盘到 CrashHandler 日志目录（异步，不阻塞协程）
     * 3. 向 [toastSink] 推送轻量 Toast 提示（含异常类型，便于排查）
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
            // 1. Logcat 输出
            android.util.Log.e(contextTag,
                "协程未捕获异常：${throwable.javaClass.simpleName} - ${throwable.message}", throwable)

            // 2. 落盘到崩溃日志（异步，不阻塞当前协程）
            try {
                // CrashHandler 提供 logException 方法将异常写入文件
                // 此处直接复用其日志写入能力，不依赖 Application Context
                val message = buildString {
                    append("[$contextTag] 协程异常\n")
                    append("Type: ${throwable.javaClass.name}\n")
                    append("Message: ${throwable.message}\n")
                    append("Thread: ${Thread.currentThread().name}\n")
                    append("Time: ${java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
                        .format(java.util.Date())}\n")
                    append("Stacktrace:\n")
                    append(throwable.stackTraceToString())
                }
                // 写入到 CrashHandler 的日志目录（通过静态方法，无需 Context）
                CrashHandler.writeLog(message)
            } catch (e: Exception) {
                android.util.Log.e(contextTag, "异常日志落盘失败：${e.message}", e)
            }

            // 3. 向 UI 推送 Toast 提示（含异常类型，便于排查）
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
