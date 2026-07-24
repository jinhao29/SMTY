package com.shangmentiyu.sportscoach.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 更新管理器（协调层）。
 *
 * 职责：
 * 1. 注册 WorkManager 定期检查任务（每天一次，Wi-Fi 下执行）
 * 2. 提供即时检查入口（应用启动时或用户手动触发）
 * 3. 提供手动安装入口（用户点击通知或"检查更新"按钮后调用）
 *
 * 使用方式：
 * - Application.onCreate() 中调用 UpdateManager.schedulePeriodicCheck(context)
 * - Application.onCreate() 中调用 UpdateManager.checkNow(context) 进行首次即时检查
 * - 设置页"检查更新"按钮调用 UpdateManager.checkNow(context)
 */
object UpdateManager {

    /** 定期检查任务的唯一名称（用于去重，避免重复注册） */
    private const val PERIODIC_WORK_NAME = "smty_periodic_update_check"

    /** 即时检查任务的唯一名称 */
    private const val ONESHOT_WORK_NAME = "smty_oneshot_update_check"

    /**
     * 注册定期更新检查任务（每天一次）。
     *
     * 使用 ExistingPeriodicWorkPolicy.KEEP 确保不重复注册。
     * 约束条件：仅在 Wi-Fi 下执行，避免消耗移动数据。
     *
     * @param context 上下文
     */
    fun schedulePeriodicCheck(context: Context) {
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
    }

    /**
     * 立即执行一次更新检查（后台异步，无返回值）。
     *
     * 适用场景：
     * - 应用首次启动
     * - 用户点击"检查更新"按钮（旧版，无反馈）
     *
     * @param context 上下文
     */
    fun checkNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)  // 任何可用网络
            .build()

        val oneShotRequest = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONESHOT_WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            oneShotRequest
        )
    }

    /**
     * 同步检查更新（挂起函数，直接返回检查结果）。
     *
     * 适用场景：
     * - 设置页"检查更新"按钮：用户点击后立即看到结果（成功/失败/已是最新）
     * - 失败时携带详细错误信息（HTTP 状态码、网络异常等），便于诊断
     *
     * 与 [checkNow] 区别：
     * - checkNow 投递到 WorkManager 后台执行，无返回值，仅通过通知反馈
     * - checkNowSync 直接在调用协程中执行，立即返回 UpdateResult
     *
     * @return 更新检查结果
     */
    suspend fun checkNowSync(): UpdateResult = UpdateChecker.checkForUpdate()

    /**
     * 手动触发安装（用户点击"检查更新"且 APK 已下载时调用）。
     *
     * @param context 上下文
     * @return true 成功启动安装界面；false 需要先开启未知来源权限
     */
    fun installUpdate(context: Context): Boolean {
        return UpdateInstaller.installApk(context)
    }

    /**
     * 检查本地是否已存在下载好的 APK 文件。
     */
    fun hasDownloadedApk(context: Context): Boolean {
        return UpdateInstaller.getApkFile(context).exists()
    }
}
