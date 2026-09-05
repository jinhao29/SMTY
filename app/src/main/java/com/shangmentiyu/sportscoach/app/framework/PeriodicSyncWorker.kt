package com.shangmentiyu.sportscoach.app.framework

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * 周期双端同步 Worker（v23.1 自动对齐）。
 *
 * 触发条件：
 * - 用户在设置页开启「自动同步」（syncEnabled）且已填 PC 端地址
 * - 每 [SYNC_INTERVAL_MINUTES] 分钟一次，要求有网络连接（局域网/USB 反代均走 HTTP）
 *
 * 行为：
 * - 静默执行 [LanSyncManager.syncNow]（推送备份到 PC 合并 + 拉取 PC 学员数据）
 * - 关闭开关 / 未配置地址时直接成功返回（保留任务，不重复注册）
 * - 失败不重试排队（下次周期自然重试），保证不打扰用户
 *
 * 合并安全性：推送方向由 PC 端 do_restore 默认策略保证（同名保留 PC 档案、合并课时）；
 * 拉取方向 UPDATE_PART + LWW（PC 端数据更旧时自动跳过），详见 双端同步协议.md。
 */
class PeriodicSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val koin = GlobalContext.get()
        val settings = koin.get<com.shangmentiyu.sportscoach.data.repo.SettingsRepository>()

        // 开关关闭：静默跳过（任务保留，用户开启后自动生效）
        val enabled = settings.syncEnabled.first()
        // v23.6 USB 自动同步修复：空地址不再跳过，与 LanSyncManager.readEndpoint
        // 一致回退 127.0.0.1（PC 端 usb-watch 已自动 adb reverse，插入即连；
        // 未插 USB 时探测失败静默返回，无副作用）
        if (!enabled) {
            Result.success()
        } else {
            val mgr = koin.get<LanSyncManager>()
            val r = mgr.syncNow()
            Log.i(TAG, "周期同步完成：${r.message.replace("\n", " | ")}")
            Result.success()
        }
    } catch (e: Exception) {
        // Koin 未就绪 / 网络异常等：下次周期自然重试
        Log.w(TAG, "周期同步异常：${e.message}")
        Result.success()
    }

    companion object {
        private const val TAG = "PeriodicSync"

        /** 唯一任务名（KEEP 策略避免重复注册） */
        private const val WORK_NAME = "smty_periodic_sync"

        /** 同步周期（分钟） */
        private const val SYNC_INTERVAL_MINUTES = 30L

        /**
         * 注册周期同步任务（幂等：KEEP 策略，重复调用无副作用）。
         * 由 SportsCoachApp 启动时调用。
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
                SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
