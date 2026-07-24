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
 * 职责：
 * 1. 调用 UpdateChecker 检查最新版本
 * 2. 若有新版本，下载 APK 并发送通知
 * 3. 下载完成后通过通知引导用户安装
 *
 * 由 UpdateManager 注册为 PeriodicWorkRequest，每天执行一次。
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "update_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "应用更新"
        private const val NOTIFICATION_ID_DOWNLOADING = 1001
        private const val NOTIFICATION_ID_READY = 1002
    }

    override suspend fun doWork(): Result {
        return try {
            // 1. 检查更新
            val updateResult = UpdateChecker.checkForUpdate()

            when (updateResult) {
                is UpdateResult.UpToDate -> {
                    // 已是最新版本，无需操作
                    Result.success()
                }
                is UpdateResult.NewVersionAvailable -> {
                    // 2. 有新版本，发送下载中通知
                    createNotificationChannel()
                    showDownloadingNotification(updateResult.tagName)

                    // 3. 下载 APK（带进度回调）
                    val apkFile = UpdateInstaller.getApkFile(applicationContext)
                    val success = UpdateChecker.downloadApk(
                        downloadUrl = updateResult.downloadUrl,
                        destFile = apkFile,
                        onProgress = { progress ->
                            updateDownloadingProgress(progress)
                        }
                    )

                    if (success) {
                        // 4. 下载完成，发送可安装通知
                        showReadyNotification(updateResult.tagName)
                        Result.success()
                    } else {
                        // 下载失败
                        showFailedNotification()
                        Result.retry()
                    }
                }
                is UpdateResult.Error -> {
                    // 检查失败，稍后重试
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** 创建通知渠道（Android 8.0+ 要求） */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "应用更新检测与下载通知"
            }
            val manager = applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /** 显示下载中通知 */
    private fun showDownloadingNotification(version: String) {
        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载新版本 $version")
            .setContentText("下载中...")
            .setProgress(100, 0, false)
            .setOngoing(true)

        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        manager.notify(NOTIFICATION_ID_DOWNLOADING, builder.build())
    }

    /** 更新下载进度 */
    private fun updateDownloadingProgress(progress: Int) {
        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载新版本")
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)

        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        manager.notify(NOTIFICATION_ID_DOWNLOADING, builder.build())
    }

    /** 下载完成通知（点击触发安装） */
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

    /** 下载失败通知 */
    private fun showFailedNotification() {
        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        manager.cancel(NOTIFICATION_ID_DOWNLOADING)

        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("更新下载失败")
            .setContentText("将在下次自动重试")
            .setAutoCancel(true)

        manager.notify(NOTIFICATION_ID_READY, builder.build())
    }
}
