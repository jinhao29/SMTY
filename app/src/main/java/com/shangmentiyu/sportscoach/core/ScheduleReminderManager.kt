package com.shangmentiyu.sportscoach.core

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 排课提醒管理器（协调层）。
 *
 * 职责：
 * 1. 注册每日 7:30 触发的 WorkManager 周期任务
 * 2. 标记「App 已被用户打开过」状态（持久化于 SharedPreferences）
 *
 * 与 [ScheduleReminderWorker] 配合：
 * - 本类负责调度，Worker 负责执行
 * - Worker 在 doWork() 中读取「已打开过」标记，未打开则静默成功
 *
 * 注册时机：
 * - Application.onCreate() 中调用 [scheduleDailyReminder]
 * - Application.onCreate() 中调用 [markAppOpened]
 *
 * 调度策略：
 * - 使用 PeriodicWorkRequest，每 24 小时触发一次
 * - 初始延迟到「下一个 7:30」时刻（今天已过 7:30 则延迟到明天 7:30）
 * - 使用 ExistingPeriodicWorkPolicy.KEEP 避免重复注册
 *
 * === v28 优化4：触发时间从 21:00 调整为 7:30 ===
 * - 业务背景：教练早晨出门前需要知道今天有哪些课，21:00 提醒"明天有课"反而不利于当日准备
 * - 7:30 是教练日常起床后查看手机的高频时段，更符合业务直觉
 * - 同时通知内容由"明天有课"改为"今日有课"，点击后跳转今日排课页面
 *
 * 约束条件：
 * - 不限制网络：通知不依赖网络
 * - 不限制电量：7:30 通常是用户日常活跃时段
 */
object ScheduleReminderManager {

    private const val TAG = "ScheduleReminderManager"

    /** WorkManager 周期任务唯一名称 */
    private const val PERIODIC_WORK_NAME = "smty_daily_schedule_reminder"

    /** SharedPreferences 文件名 */
    private const val PREFS_NAME = "schedule_reminder_prefs"

    /** 「App 已打开过」标记的 Key */
    private const val KEY_APP_OPENED = "app_opened"

    /** === v28 优化4：任务触发时刻调整为 7:30（原 21:00） === */
    private const val TRIGGER_HOUR = 7

    /** === v28 优化4：任务触发分钟调整为 30（原 00） === */
    private const val TRIGGER_MINUTE = 30

    /**
     * 注册每日 7:30 触发的排课提醒任务。
     *
     * - 使用 KEEP 策略确保不重复注册
     * - 初始延迟精确到下一个 7:30
     * - 失败不阻塞调用方
     *
     * @param context 上下文
     */
    fun scheduleDailyReminder(context: Context) {
        try {
            val initialDelayMinutes = calculateMinutesToNextTrigger()

            val constraints = Constraints.Builder()
                // 不限制网络与电量：7:30 通知是核心业务提醒
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<ScheduleReminderWorker>(
                24, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.i(TAG, "每日排课提醒任务已注册：7:30 触发，初始延迟 $initialDelayMinutes 分钟")
        } catch (e: Exception) {
            Log.e(TAG, "注册每日排课提醒任务失败：${e.message}", e)
        }
    }

    /**
     * === v28 优化4：强制重新注册每日 7:30 提醒任务 ===
     *
     * 业务背景：旧版本（v22）注册的 21:00 任务已通过 KEEP 策略保留，
     * 升级到 v28 后必须用 REPLACE 策略覆盖旧任务，否则仍然在 21:00 触发。
     *
     * 调用时机：App 首次启动到 v28 版本时调用一次（通过 SharedPreferences 标记 v28 已迁移）
     */
    fun forceRescheduleIfNeeded(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val keyRescheduled = "v28_rescheduled_to_730"
            if (prefs.getBoolean(keyRescheduled, false)) {
                // 已经迁移过，跳过
                return
            }

            // 用 REPLACE 策略覆盖旧任务（无论是 21:00 还是其他时间）
            val initialDelayMinutes = calculateMinutesToNextTrigger()

            val constraints = Constraints.Builder()
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<ScheduleReminderWorker>(
                24, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )

            // 标记迁移完成，避免重复执行
            prefs.edit().putBoolean(keyRescheduled, true).apply()

            Log.i(TAG, "v28 升级：已将每日排课提醒强制重新注册为 7:30 触发")
        } catch (e: Exception) {
            Log.e(TAG, "v28 升级重新注册排课提醒任务失败：${e.message}", e)
        }
    }

    /**
     * 标记 App 已被用户打开过。
     *
     * - 在 Application.onCreate() 中调用
     * - 持久化到 SharedPreferences，跨重启持久
     * - 标记为 true 后不会被重置为 false（除非用户清空数据或卸载）
     *
     * 用途：[ScheduleReminderWorker] 启动时检查此标记，避免安装后未启动被通知打扰。
     *
     * @param context 上下文
     */
    fun markAppOpened(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_APP_OPENED, true).apply()
        } catch (e: Exception) {
            Log.w(TAG, "标记 App 已打开失败：${e.message}")
        }
    }

    /**
     * 检查 App 是否已被用户打开过。
     *
     * 主要用于测试与调试，业务逻辑由 [ScheduleReminderWorker.isAppOpened] 内部读取。
     */
    fun isAppOpened(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_APP_OPENED, false)
    }

    /**
     * === v28 优化4：计算从当前时刻到下一个 7:30 的分钟数 ===
     *
     * - 若当前时间早于 7:30：返回今天 7:30 与当前的差值
     * - 若当前时间晚于或等于 7:30：返回明天 7:30 与当前的差值
     *
     * 注意：WorkManager 最小 PeriodicWorkRequest 周期为 15 分钟，
     * 初始延迟也按 15 分钟对齐（由 WorkManager 内部处理，无需此处取整）。
     */
    private fun calculateMinutesToNextTrigger(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, TRIGGER_HOUR)
            set(Calendar.MINUTE, TRIGGER_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // 如果今天已过 7:30，目标改为明天 7:30
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val diffMillis = target.timeInMillis - now.timeInMillis
        // 转分钟，向上取整确保至少为 1 分钟
        return (diffMillis / 60_000L).coerceAtLeast(1L)
    }
}
