package com.shangmentiyu.sportscoach.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

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
        private const val NOTIFICATION_CHANNEL_ID = "update_channel_v2"
        private const val NOTIFICATION_CHANNEL_NAME = "应用更新"
        private const val NOTIFICATION_ID_DOWNLOADING = 1001
        private const val NOTIFICATION_ID_READY = 1002
    }

    /**
     * 当前重试次数（从 inputData 读取，0=首次检查，1=第一次重试，2=第二次重试）。
     */
    private val currentRetryCount: Int
        get() = inputData.getInt(UpdateManager.KEY_RETRY_COUNT, 0)

    override suspend fun doWork(): Result {
        return try {
            // 1. 检查更新
            val updateResult = UpdateChecker.checkForUpdate()

            when (updateResult) {
                is UpdateResult.UpToDate -> {
                    // 已是最新版本：取消可能存在的失败重试链，本次检查链自然终止
                    UpdateManager.cancelRetryChain(applicationContext)
                    Result.success()
                }
                is UpdateResult.NewVersionAvailable -> {
                    // 2. 有新版本，发送下载中通知（可点击打开 App）
                    createNotificationChannel()
                    showDownloadingNotification(updateResult.tagName)
                    // 同步推送 UI 进度总线：App 在前台时立即显示下载进度浮层
                    UpdateProgressBus.emit(
                        UpdateProgressBus.UpdateProgress.Downloading(0, updateResult.tagName)
                    )

                    // 3. 下载 APK（带进度回调 + 断点续传）
                    // === v33 优化：downloadApk 现在会抛 DownloadException ===
                    // 用一个标志变量记录是否下载失败，避免在 try 中 return（Kotlin 不允许 return@try）
                    var downloadFailedMessage: String? = null
                    var success = false
                    try {
                        val apkFile = UpdateInstaller.getApkFile(applicationContext)
                        success = UpdateChecker.downloadApk(
                            downloadUrl = updateResult.downloadUrl,
                            destFile = apkFile,
                            onProgress = { progress ->
                                updateDownloadingProgress(progress)
                                // 同步 UI 进度总线：每次进度回调都推送，UI 实时刷新
                                UpdateProgressBus.emit(
                                    UpdateProgressBus.UpdateProgress.Downloading(
                                        progress, updateResult.tagName
                                    )
                                )
                            }
                        )
                    } catch (e: UpdateChecker.DownloadException) {
                        // === 功能 4：网络不稳定 / 下载失败友好提示 ===
                        // userMessage 已是面向用户的文案，直接展示到通知与 UI
                        downloadFailedMessage = e.userMessage
                        success = false
                    }

                    if (success) {
                        // 4. 下载完成，发送可安装通知（点击直接跳转安装界面）
                        showReadyNotification(updateResult.tagName)
                        // 同步 UI 进度总线：UI 据此隐藏进度浮层并触发安装
                        UpdateProgressBus.emit(
                            UpdateProgressBus.UpdateProgress.Done(updateResult.tagName)
                        )
                        // 成功路径：取消可能存在的失败重试链
                        UpdateManager.cancelRetryChain(applicationContext)
                        Result.success()
                    } else {
                        // 下载失败：发送失败通知 + 排程 1h/6h 后台重试
                        // 断点续传会在下次重试时继续下载，不重新开始
                        val failMsg = downloadFailedMessage
                            ?: "下载失败，将在 1 小时后自动重试"
                        showFailedNotification(failMsg)
                        UpdateProgressBus.emit(
                            UpdateProgressBus.UpdateProgress.Failed(failMsg)
                        )
                        UpdateManager.scheduleRetryIfNeeded(applicationContext, currentRetryCount)
                        Result.failure()
                    }
                }
                is UpdateResult.Error -> {
                    // 检查失败：排程 1h/6h 后台重试
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
            UpdateManager.scheduleRetryIfNeeded(applicationContext, currentRetryCount)
            UpdateProgressBus.emit(
                UpdateProgressBus.UpdateProgress.Failed(e.message ?: "更新检查异常，将在 1 小时后自动重试")
            )
            Result.failure()
        }
    }

    /** 创建通知渠道（Android 8.0+ 要求） */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "应用更新检测与下载通知"
                enableVibration(true)
            }
            val manager = applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            manager.createNotificationChannel(channel)
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
     * 下载完成通知（功能 2：点击直接跳转安装界面）。
     *
     * - 取消下载中通知
     * - setContentIntent 指向 APK 安装 Intent
     * - setAutoCancel(true)：点击后自动消失
     */
    private fun showReadyNotification(version: String) {
        // 先取消下载中通知
        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        manager.cancel(NOTIFICATION_ID_DOWNLOADING)

        val installIntent = UpdateInstaller.createInstallPendingIntent(applicationContext)
        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("新版本 $version 已下载完成")
            .setContentText("点击安装更新")
            .setAutoCancel(true)
            .setContentIntent(installIntent)

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
