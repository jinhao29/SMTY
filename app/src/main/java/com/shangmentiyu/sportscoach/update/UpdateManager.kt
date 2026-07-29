package com.shangmentiyu.sportscoach.update

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.shangmentiyu.sportscoach.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * 更新管理器（协调层）。
 *
 * 职责：
 * 1. 注册 WorkManager 定期检查任务（每天一次，Wi-Fi 下执行）
 * 2. 提供即时检查入口（应用启动时或用户手动触发）
 * 3. 提供手动安装入口（用户点击通知或"检查更新"按钮后调用）
 * 4. 失败重试策略（v22 引入）：检查失败后分别在 1 小时、6 小时后台重试，
 *    超过最大重试次数后等待下次定期任务。
 *
 * 使用方式：
 * - Application.onCreate() 中调用 UpdateManager.schedulePeriodicCheck(context)
 * - Application.onCreate() 中调用 UpdateManager.checkNow(context) 进行首次即时检查
 * - 设置页"检查更新"按钮调用 UpdateManager.checkNowSync() 获取同步结果
 *
 * v2 修复（404 / 网络异常兜底）：
 * - 404（仓库未发布 Release）和网络异常现已在 [UpdateChecker] 内部
 *   统一降级为 [UpdateResult.UpToDate]，不再触发错误弹窗
 * - 本层仅做日志埋点，不重复处理异常
 *
 * v22 失败重试策略：
 * - 首次失败 → 1 小时后后台重试（[scheduleRetryIfNeeded] + retryCount=1）
 * - 二次失败 → 6 小时后后台重试（retryCount=2）
 * - 三次失败 → 不再重试，等待下次定期任务（24h 后自动触发）
 * - 成功或发现新版本 → 自动取消已排队的重试任务，避免重复执行
 */
object UpdateManager {

    // === 诊断统一 Tag：与 UpdateChecker 一致，Logcat 过滤 "AutoUpdate" 看全链路 ===
    private const val TAG = "AutoUpdate"

    /** 定期检查任务的唯一名称（用于去重，避免重复注册） */
    private const val PERIODIC_WORK_NAME = "smty_periodic_update_check"

    /** 即时检查任务的唯一名称 */
    private const val ONESHOT_WORK_NAME = "smty_oneshot_update_check"

    /** 失败重试任务的唯一名称（与即时检查区分，避免互相 REPLACE） */
    private const val RETRY_WORK_NAME = "smty_update_retry"

    /** Worker 输入数据 Key：当前重试次数（0=首次检查，1=第一次重试，2=第二次重试） */
    const val KEY_RETRY_COUNT = "retry_count"

    /** 最大重试次数：超过后等待下次定期任务（24h 后自动触发） */
    private const val MAX_RETRY_COUNT = 2

    /** 第一次重试延迟（小时）：检查失败后 1 小时再次尝试 */
    private const val RETRY_DELAY_FIRST_HOURS = 1L

    /** 第二次重试延迟（小时）：第一次重试失败后 6 小时再次尝试 */
    private const val RETRY_DELAY_SECOND_HOURS = 6L

    // === v33+：移除 -local 拦截逻辑 ===
    // 原先通过 isLocalDevBuild 一刀切拦截 -local 版本的网络请求，导致开发版
    // 无法触发更新弹窗，无法完整测试自动更新流程。
    // 现已改为：开发版（含 -local 或本地 fallback "0.0.1"）正常请求 GitHub 接口，
    // 若解析出最新版本号 > 本地 versionCode，依然弹出 AlertDialog 提示更新。
    // 这样开发者在手机上测试时，也能体验到完整的更新弹窗流程。

    /**
     * 注册定期更新检查任务（每天一次）。
     *
     * 使用 ExistingPeriodicWorkPolicy.KEEP 确保不重复注册。
     * 约束条件：仅在 Wi-Fi 下执行，避免消耗移动数据。
     *
     * @param context 上下文
     */
    fun schedulePeriodicCheck(context: Context) {
        // v33+：移除 -local 拦截，开发版也注册定期检查，方便测试完整更新流程
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)  // Wi-Fi 或不计量网络
                .build()

            // 每 24 小时检查一次（WorkManager 最短周期为 15 分钟）
            val periodicRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                24, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.i(TAG, "定期更新检查任务已注册（24h 周期，Wi-Fi 约束）")
        } catch (e: Exception) {
            // WorkManager 初始化失败不阻塞应用启动
            Log.e(TAG, "注册定期检查任务失败：${e.message}", e)
        }
    }

    /**
     * 立即执行一次更新检查（后台异步，无返回值）。
     *
     * 适用场景：
     * - 应用首次启动
     * - 用户点击"检查更新"按钮（旧版，无反馈）
     *
     * 网络约束（v23 强化）：
     * - 改为 [NetworkType.UNMETERED]（Wi-Fi 或不计量网络）
     * - 与 [schedulePeriodicCheck] 一致，确保"下载 APK 步骤"仅在 Wi-Fi 下执行
     * - 设置页"检查更新"按钮同步检查版本（[checkNowSync]，不走 WorkManager）
     *   仍可在任何网络下立即返回版本信息，仅"下载 APK"步骤受 Wi-Fi 约束
     *
     * 异常兜底：WorkManager.enqueue 失败不阻塞调用方。
     * 同时取消已排队的失败重试任务（用户主动触发视为新一轮检查）。
     *
     * @param context 上下文
     */
    fun checkNow(context: Context) {
        // v33+：移除 -local 拦截，开发版也立即触发更新检查，方便测试完整更新流程
        try {
            // 用户主动触发时取消已排队的失败重试任务，避免重复执行
            cancelRetryChain(context)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)  // 仅 Wi-Fi / 不计量网络
                .build()

            val oneShotRequest = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_RETRY_COUNT to 0))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONESHOT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneShotRequest
            )
            Log.d(TAG, "已投递即时更新检查任务")
        } catch (e: Exception) {
            Log.e(TAG, "投递即时检查任务失败：${e.message}", e)
        }
    }

    /**
     * 失败重试调度（v22 引入）。
     *
     * 调用时机：[UpdateCheckWorker] 收到 [UpdateResult.Error] 或捕获异常时调用。
     *
     * 重试策略：
     * - retryCount = 0（首次失败） → 1 小时后重试（retryCount=1）
     * - retryCount = 1（第一次重试失败） → 6 小时后重试（retryCount=2）
     * - retryCount >= 2（已重试 2 次） → 不再重试，等待下次定期任务
     *
     * 约束：v23 改为 [NetworkType.UNMETERED]，与 [checkNow] / [schedulePeriodicCheck] 一致，
     * 确保"下载 APK 步骤"仅在 Wi-Fi 下执行，避免重试链持续消耗移动数据。
     * 去重：使用 RETRY_WORK_NAME + ExistingWorkPolicy.REPLACE，避免多次失败堆积重试任务。
     *
     * @param context 上下文
     * @param currentRetryCount 当前重试次数（首次失败传 0，第一次重试失败传 1）
     */
    internal fun scheduleRetryIfNeeded(context: Context, currentRetryCount: Int) {
        if (currentRetryCount >= MAX_RETRY_COUNT) {
            Log.i(TAG, "已达最大重试次数 ($MAX_RETRY_COUNT)，等待下次定期任务自动触发")
            return
        }

        val nextRetryCount = currentRetryCount + 1
        val delayHours = if (nextRetryCount == 1) RETRY_DELAY_FIRST_HOURS else RETRY_DELAY_SECOND_HOURS

        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

            val retryRequest = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delayHours, TimeUnit.HOURS)
                .setInputData(workDataOf(KEY_RETRY_COUNT to nextRetryCount))
                .addTag(RETRY_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                RETRY_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                retryRequest
            )
            Log.i(TAG, "已排程失败重试：第 $nextRetryCount 次重试将在 ${delayHours}h 后执行")
        } catch (e: Exception) {
            Log.e(TAG, "排程失败重试任务异常：${e.message}", e)
        }
    }

    /**
     * 取消已排队的失败重试任务。
     *
     * 调用时机：
     * - 用户主动 [checkNow]：视为新一轮检查，旧的重试任务不再有意义
     * - [UpdateCheckWorker] 成功或发现新版本：重试链自然终止
     */
    internal fun cancelRetryChain(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(RETRY_WORK_NAME)
        } catch (e: Exception) {
            Log.w(TAG, "取消重试链异常（可忽略）：${e.message}")
        }
    }

    /**
     * 同步检查更新（挂起函数，直接返回检查结果）。
     *
     * 适用场景：
     * - 设置页"检查更新"按钮：用户点击后立即看到结果（成功/失败/已是最新）
     * - 失败时携带详细错误信息（HTTP 状态码、网络异常等），便于诊断
     *
     * 异常兜底说明：
     * - 404（仓库未发布 Release）和网络异常已在 [UpdateChecker] 内部
     *   降级为 [UpdateResult.UpToDate]，因此本方法不会因网络问题返回 Error
     * - 仅 JSON 解析失败、HTTP 5xx 等严重错误才返回 [UpdateResult.Error]
     *
     * 与 [checkNow] 区别：
     * - checkNow 投递到 WorkManager 后台执行，无返回值，仅通过通知反馈
     * - checkNowSync 直接在调用协程中执行，立即返回 UpdateResult
     *
     * @return 更新检查结果
     */
    suspend fun checkNowSync(): UpdateResult {
        // v33+：移除 -local 拦截，开发版也正常请求 GitHub 接口
        // 若远端 versionCode > 本地 versionCode，依然返回 NewVersionAvailable 触发弹窗
        Log.d(TAG, ">> checkNowSync 入口：开始同步检查更新")
        return try {
            val result = UpdateChecker.checkForUpdate()
            when (result) {
                is UpdateResult.UpToDate ->
                    Log.i(TAG, "<< checkNowSync 返回：当前已是最新版本")
                is UpdateResult.NewVersionAvailable ->
                    Log.i(TAG, "<< checkNowSync 返回：发现新版本 ${result.tagName}，URL=${result.downloadUrl}")
                is UpdateResult.Error ->
                    Log.w(TAG, "<< checkNowSync 返回：检查失败 - ${result.message}")
            }
            result
        } catch (e: Exception) {
            // 兜底：理论上 UpdateChecker 已 try-catch，这里再兜一层防止崩溃
            Log.e(TAG, "<< checkNowSync 未捕获异常：${e.javaClass.simpleName}: ${e.message}", e)
            UpdateResult.UpToDate
        }
    }

    /**
     * 手动触发安装（用户点击"检查更新"且 APK 已下载时调用）。
     *
     * @param context 上下文
     * @return true 成功启动安装界面；false 需要先开启未知来源权限
     */
    fun installUpdate(context: Context): Boolean {
        return try {
            UpdateInstaller.installApk(context)
        } catch (e: Exception) {
            Log.e(TAG, "启动安装失败：${e.message}", e)
            false
        }
    }

    /**
     * 检查本地是否已存在下载好的 APK 文件。
     */
    fun hasDownloadedApk(context: Context): Boolean {
        return try {
            UpdateInstaller.getApkFile(context).exists()
        } catch (e: Exception) {
            Log.e(TAG, "检查本地 APK 失败：${e.message}", e)
            false
        }
    }

    // === v34：持久化"等待安装"标志 ===
    // 解决场景：App 在后台或被杀进程时，Worker 完成下载并发出通知
    // 用户点击通知或下次进入 App 时，需要触发 AlertDialog 询问是否安装
    // 不持久化的话，进程重启后 UpdateProgressBus 状态丢失，弹窗永远不会出现

    /** SharedPreferences 文件名 */
    private const val PREFS_NAME = "smty_update_prefs"

    /** "等待安装"版本号 Key（值 = 新版本 tagName，如 "v21"） */
    private const val KEY_PENDING_INSTALL_VERSION = "pending_install_version"

    /**
     * 标记"已有下载好的更新待安装"。
     *
     * 调用时机：[UpdateCheckWorker] 下载完成后立即调用，写入待安装版本号。
     * 后续 App 启动时通过 [consumeUpdateReady] 读取并触发 AlertDialog。
     *
     * @param context 上下文
     * @param version 待安装版本号（如 "v21"）
     */
    fun markUpdateReady(context: Context, version: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_PENDING_INSTALL_VERSION, version).apply()
            Log.i(TAG, "已标记更新待安装：$version")
        } catch (e: Exception) {
            Log.e(TAG, "写入待安装标志失败：${e.message}", e)
        }
    }

    /**
     * 消费"等待安装"标志（读取后立即清除）。
     *
     * 调用时机：SportsApp 启动时检查，若返回非 null 则触发 AlertDialog。
     * 读取后立即清除，避免每次进入 App 都弹窗。
     *
     * @return 待安装版本号（如 "v21"）；null 表示无待安装更新或 APK 已被清理
     */
    fun consumeUpdateReady(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val version = prefs.getString(KEY_PENDING_INSTALL_VERSION, null)
            if (version != null) {
                // 二次确认：APK 文件是否还存在（用户可能手动清理了缓存）
                val apkExists = UpdateInstaller.getApkFile(context).exists()
                if (apkExists) {
                    Log.i(TAG, "检测到待安装更新：$version，将触发确认弹窗")
                    // 清除标志，避免下次进入 App 重复弹窗
                    prefs.edit().remove(KEY_PENDING_INSTALL_VERSION).apply()
                    version
                } else {
                    // APK 已被清理（如清理缓存）：清除标志，避免无效弹窗
                    Log.w(TAG, "待安装版本 $version 的 APK 已被清理，清除标志")
                    prefs.edit().remove(KEY_PENDING_INSTALL_VERSION).apply()
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取待安装标志失败：${e.message}", e)
            null
        }
    }

    /**
     * 清除"等待安装"标志。
     *
     * 调用时机：
     * - 用户在 AlertDialog 中点击"稍后"后调用（避免下次进入 App 又弹）
     * - 用户主动取消安装时调用
     */
    fun clearUpdateReady(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_PENDING_INSTALL_VERSION).apply()
            Log.d(TAG, "已清除待安装标志")
        } catch (e: Exception) {
            Log.e(TAG, "清除待安装标志失败：${e.message}", e)
        }
    }
}
