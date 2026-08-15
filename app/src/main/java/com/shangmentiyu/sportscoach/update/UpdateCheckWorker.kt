package com.shangmentiyu.sportscoach.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shangmentiyu.sportscoach.app.framework.NotificationUtils

/**
 * 后台更新检查 Worker（WorkManager 协程 Worker）。
 *
 * === v33 优化（GitHub 自动更新稳定化） ===
 * 1. 下载阶段捕获 [UpdateChecker.DownloadException]，将 userMessage 直接展示到失败通知
 * 2. 下载中通知也支持点击打开 App 主界面（避免死按钮）
 * 3. 失败通知文案区分"网络不稳定"与"普通失败"两种场景
 *
 * 职责：
 * 1. 调用 UpdateChecker 检查最新版本
 * 2. 若有新版本，下载 APK 并发送通知（带进度条，可点击）
 * 3. 下载完成后通过通知引导用户安装（点击直接跳转安装界面）
 * 4. 失败时通过 [UpdateManager.scheduleRetryIfNeeded] 排程 1h/6h 后台重试
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        // === 诊断统一 Tag：与 UpdateChecker/UpdateManager 一致 ===
        private const val TAG = "AutoUpdate"
        private const val NOTIFICATION_CHANNEL_ID = "update_channel_v2"
        private const val NOTIFICATION_CHANNEL_NAME = "应用更新"
        private const val NOTIFICATION_ID_DOWNLOADING = 1001
        private const val NOTIFICATION_ID_READY = 1002
        private const val NOTIFICATION_ID_NEW_VERSION = 1003
    }

    /**
     * 当前重试次数（从 inputData 读取，0=首次检查，1=第一次重试，2=第二次重试）。
     */
    private val currentRetryCount: Int
        get() = inputData.getInt(UpdateManager.KEY_RETRY_COUNT, 0)

    override suspend fun doWork(): Result {
        Log.d(TAG, ">> UpdateCheckWorker.doWork 入口：retryCount=$currentRetryCount")

        // === v51：下载模式 ===
        // 传入 KEY_DOWNLOAD_URL 时跳过版本检查，直接下载（用户确认更新后触发）
        val downloadUrl = inputData.getString(UpdateManager.KEY_DOWNLOAD_URL)
        if (!downloadUrl.isNullOrBlank()) {
            val downloadVersion = inputData.getString(UpdateManager.KEY_DOWNLOAD_VERSION)
                ?: "新版本"
            Log.d(TAG, ">> 下载模式：version=$downloadVersion")
            return downloadUpdate(downloadUrl, downloadVersion)
        }

        // === 检查模式：只检查 + 提示，不再自动下载 ===
        return try {
            // 1. 检查更新
            val updateResult = UpdateChecker.checkForUpdate()
            Log.d(TAG, "Worker 收到检查结果: ${updateResult.javaClass.simpleName}")

            when (updateResult) {
                is UpdateResult.UpToDate -> {
                    // 已是最新版本：取消可能存在的失败重试链，本次检查链自然终止
                    Log.d(TAG, "Worker: UpToDate，取消重试链，任务结束")
                    UpdateManager.cancelRetryChain(applicationContext)
                    Result.success()
                }
                is UpdateResult.NewVersionAvailable -> {
                    // === v51：发现新版本不再自动下载，先弹窗询问用户 ===
                    // 1. 持久化待确认更新 + 推送 UI 总线事件（App 在前台立即弹确认框）
                    // 2. 发送"发现新版本"通知（App 在后台时提醒，点击打开 App 后弹确认框）
                    Log.d(TAG, "Worker: 发现新版本 ${updateResult.tagName}，先询问用户是否更新")
                    UpdateManager.promptNewVersion(
                        applicationContext,
                        updateResult,
                        honorRejection = true
                    )
                    showNewVersionNotification(updateResult.tagName)
                    // 自动检查链路到此结束（是否下载由用户决定），无需重试
                    UpdateManager.cancelRetryChain(applicationContext)
                    Result.success()
                }
                is UpdateResult.Error -> {
                    // 检查失败：排程 1h/6h 后台重试
                    Log.w(TAG, "Worker: 检查失败 Error: ${updateResult.message}，排程重试")
                    UpdateManager.scheduleRetryIfNeeded(applicationContext, currentRetryCount)
                    // 同步 UI 进度总线（仅在用户主动触发的即时检查场景下可见）
                    UpdateProgressBus.emit(
                        UpdateProgressBus.UpdateProgress.Failed(
                            "更新检查失败：${updateResult.message}，将在 1 小时后自动重试"
                        )
                    )
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            // 未捕获异常：同样排程重试，避免异常导致重试链断裂
            Log.e(TAG, "Worker: doWork 未捕获异常: ${e.javaClass.simpleName}: ${e.message}", e)
            UpdateManager.scheduleRetryIfNeeded(applicationContext, currentRetryCount)
            UpdateProgressBus.emit(
                UpdateProgressBus.UpdateProgress.Failed(e.message ?: "更新检查异常，将在 1 小时后自动重试")
            )
            Result.failure()
        }
    }

    /**
     * 下载并安装新版本（v51 抽出的下载模式核心逻辑，供检查模式与下载模式复用）。
     *
     * @param downloadUrl APK 下载直链
     * @param version 目标版本号
     */
    private suspend fun downloadUpdate(downloadUrl: String, version: String): Result {
        return try {
            Log.d(TAG, "Worker: 开始下载 APK $version")
            NotificationUtils.createChannel(applicationContext, NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, "应用更新检测与下载通知", enableVibration = true)
            showDownloadingNotification(version)
            // 同步推送 UI 进度总线：App 在前台时立即显示下载进度浮层
            UpdateProgressBus.emit(
                UpdateProgressBus.UpdateProgress.Downloading(0, version)
            )

            // 下载 APK（带进度回调 + 断点续传）
            var downloadFailedMessage: String? = null
            var success = false
            try {
                val apkFile = UpdateInstaller.getApkFile(applicationContext)
                Log.d(TAG, "Worker: APK 目标路径 = ${apkFile.absolutePath}")
                success = UpdateChecker.downloadApk(
                    downloadUrl = downloadUrl,
                    destFile = apkFile,
                    onProgress = { progress ->
                        updateDownloadingProgress(progress)
                        // 同步 UI 进度总线：每次进度回调都推送，UI 实时刷新
                        UpdateProgressBus.emit(
                            UpdateProgressBus.UpdateProgress.Downloading(
                                progress, version
                            )
                        )
                    }
                )
            } catch (e: UpdateChecker.DownloadException) {
                // 网络不稳定 / 下载失败友好提示：userMessage 已是面向用户的文案
                Log.e(TAG, "Worker: 下载失败 DownloadException: ${e.userMessage}", e)
                downloadFailedMessage = e.userMessage
                success = false
            }

            if (success) {
                // 下载完成，发送可安装通知（点击打开 App，由 App 内弹窗确认安装）
                Log.d(TAG, "Worker: 下载完成，发送可安装通知")
                showReadyNotification(version)
                // 同步 UI 进度总线：UI 据此隐藏进度浮层并触发安装确认弹窗
                UpdateProgressBus.emit(
                    UpdateProgressBus.UpdateProgress.Done(version)
                )
                // 成功路径：取消可能存在的失败重试链
                UpdateManager.cancelRetryChain(applicationContext)
                Result.success()
            } else {
                // 下载失败：发送失败通知 + 排程 1h/6h 后台重试
                // 断点续传会在下次重试时继续下载，不重新开始
                val failMsg = downloadFailedMessage
                    ?: "下载失败，将在 1 小时后自动重试"
                Log.w(TAG, "Worker: 下载失败，排程重试。failMsg=$failMsg")
                showFailedNotification(failMsg)
                UpdateProgressBus.emit(
                    UpdateProgressBus.UpdateProgress.Failed(failMsg)
                )
                UpdateManager.scheduleRetryIfNeeded(applicationContext, currentRetryCount)
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker: downloadUpdate 未捕获异常: ${e.javaClass.simpleName}: ${e.message}", e)
            UpdateManager.scheduleRetryIfNeeded(applicationContext, currentRetryCount)
            UpdateProgressBus.emit(
                UpdateProgressBus.UpdateProgress.Failed(e.message ?: "更新下载异常，将在 1 小时后自动重试")
            )
            Result.failure()
        }
    }

    /**
     * 显示下载中通知（功能 2：可交互进度条）。
     *
     * - setProgress(100, 0, false)：确定进度条，初始 0%
     * - setOngoing(true)：下载中不可滑掉，避免误操作丢失通知
     * - setContentIntent：点击通知打开 App 主界面（避免死按钮）
     */
    private fun showDownloadingNotification(version: String) {
        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载新版本 $version")
            .setContentText("下载中... 0%")
            .setProgress(100, 0, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)  // 仅首次弹出提醒，后续更新静默
            .setContentIntent(UpdateInstaller.createOpenAppPendingIntent(applicationContext))

        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        manager.notify(NOTIFICATION_ID_DOWNLOADING, builder.build())
    }

    /**
     * 更新下载进度（功能 2：实时刷新通知栏）。
     *
     * - setOnlyAlertOnce：避免每次进度更新都震动 / 铃声
     * - setProgress(100, progress, false)：确定进度条，0-100
     */
    private fun updateDownloadingProgress(progress: Int) {
        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载新版本")
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(UpdateInstaller.createOpenAppPendingIntent(applicationContext))

        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        manager.notify(NOTIFICATION_ID_DOWNLOADING, builder.build())
    }

    /**
     * "发现新版本"通知（v51 新增）。
     *
     * 场景：App 在后台时 Worker 检查到新版本，无法直接弹窗，
     * 发送通知提醒用户；点击通知打开 App，App 内弹出"是否更新"确认框。
     */
    private fun showNewVersionNotification(version: String) {
        NotificationUtils.createChannel(applicationContext, NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, "应用更新检测与下载通知", enableVibration = true)
        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        // 若已有同版本通知，避免重复叠加
        manager.cancel(NOTIFICATION_ID_NEW_VERSION)

        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("发现新版本 $version")
            .setContentText("点击查看更新详情")
            .setAutoCancel(true)
            .setContentIntent(UpdateInstaller.createOpenAppPendingIntent(applicationContext))

        manager.notify(NOTIFICATION_ID_NEW_VERSION, builder.build())
    }

    /**
     * 下载完成通知（v34：点击打开 App，由 App 内弹窗确认是否安装）。
     *
     * 设计变更（v34）：
     * - 旧版：setContentIntent = createInstallPendingIntent → 点击直接跳转系统安装器
     *   问题：绕过 App 内 AlertDialog 确认，用户被直接推进安装流程
     * - 新版：setContentIntent = createOpenAppPendingIntent → 点击打开 App MainActivity
     *   App 启动时检查本地 APK + 持久化标志，自动触发 AlertDialog 让用户确认
     *
     * - 取消下载中通知
     * - setAutoCancel(true)：点击后自动消失
     */
    private fun showReadyNotification(version: String) {
        // 先取消下载中通知
        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        manager.cancel(NOTIFICATION_ID_DOWNLOADING)

        // 写入持久化标志：App 启动时读取此标志触发安装确认弹窗
        // 避免用户杀进程后状态丢失，确保下次进入 App 时仍能弹出确认
        UpdateManager.markUpdateReady(applicationContext, version)

        // 通知点击改为打开 App（不直接安装），由 App 内 AlertDialog 让用户确认
        val openAppIntent = UpdateInstaller.createOpenAppPendingIntent(applicationContext)
        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("新版本 $version 已下载完成")
            .setContentText("点击打开应用查看更新")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)

        manager.notify(NOTIFICATION_ID_READY, builder.build())
    }

    /**
     * 下载失败通知（功能 4：展示用户友好提示）。
     *
     * @param userMessage 来自 [UpdateChecker.DownloadException.userMessage]，
     *                    如"网络不稳定，下载失败，请连接更稳定的 WiFi 重试"
     */
    private fun showFailedNotification(userMessage: String) {
        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        manager.cancel(NOTIFICATION_ID_DOWNLOADING)

        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("更新下载失败")
            .setContentText(userMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(userMessage))  // 长文本完整展示
            .setAutoCancel(true)

        manager.notify(NOTIFICATION_ID_READY, builder.build())
    }
}
