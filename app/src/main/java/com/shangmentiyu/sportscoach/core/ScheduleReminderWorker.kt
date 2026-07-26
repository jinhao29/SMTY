package com.shangmentiyu.sportscoach.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shangmentiyu.sportscoach.MainActivity
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 每日排课提醒 Worker（后台任务处理层）。
 *
 * 职责：
 * - 每日 7:30 由 WorkManager 触发
 * - 仅当用户「已打开过 App」时才真正执行后续逻辑（避免刚安装未启动就被打扰）
 * - 查询今日的排课记录，若有则通过 NotificationManager 发送本地通知
 * - 通知点击后打开 App 并跳转到今日排课页面
 *
 * 与 [ScheduleReminderManager] 配合：
 * - [ScheduleReminderManager.scheduleDailyReminder] 注册 24h 周期任务
 * - [ScheduleReminderManager.markAppOpened] 在 Application.onCreate 中标记 App 已打开
 *
 * === v28 优化4：触发时间与通知内容调整 ===
 * - 触发时间从 21:00 调整为 7:30（教练早晨起床时段更符合业务直觉）
 * - 通知内容由"明天有 N 节课"改为"今日有 N 节课"
 * - 添加通知点击 PendingIntent，点击后打开 MainActivity 并传递
 *   ACTION_VIEW_TODAY_SCHEDULE action + "navigate_to" extra
 * - MainActivity 接收到该 Intent 后由 SportsApp 读取 extra 自动跳转到今日排课页
 *
 * 设计权衡：
 * - 不在 Worker 内做前台判断（isAppInForeground）：7:30 时用户大概率未打开 App，
 *   但仍希望推送"今日有课"的提醒，因此不限制当前是否前台
 * - 「已打开过」标记持久化在 SharedPreferences，跨重启持久
 *
 * 通知渠道：
 * - 渠道 ID：schedule_reminder_channel
 * - 渠道名：排课提醒
 * - 重要性：IMPORTANCE_DEFAULT（发声但不打断）
 */
class ScheduleReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        /** 通知渠道 ID */
        private const val CHANNEL_ID = "schedule_reminder_channel"

        /** 通知渠道名称（用户在系统设置中可见） */
        private const val CHANNEL_NAME = "排课提醒"

        /** 通知 ID（固定值，便于覆盖更新） */
        private const val NOTIFICATION_ID = 2001

        /** === v29 优化1：未上课预警通知 ID（独立 ID，避免覆盖今日排课通知） === */
        private const val NOTIFICATION_ID_INACTIVE = 2002

        /** SharedPreferences 文件名（与 [ScheduleReminderManager] 一致） */
        private const val PREFS_NAME = "schedule_reminder_prefs"

        /** 「App 已打开过」标记的 Key */
        private const val KEY_APP_OPENED = "app_opened"

        /** === v28 优化4：通知点击跳转今日排课页的自定义 Action === */
        const val ACTION_VIEW_TODAY_SCHEDULE =
            "com.shangmentiyu.sportscoach.ACTION_VIEW_TODAY_SCHEDULE"

        /** === v28 优化4：通知点击跳转目标的 Extra Key ===
         *  SportsApp 读取此 extra 后自动导航到 Routes.OPERATION 页 */
        const val EXTRA_NAVIGATE_TO = "navigate_to"

        /** === v28 优化4：通知点击跳转目标的 Extra 值 ===
         *  对应 [com.shangmentiyu.sportscoach.ui.Routes.OPERATION] */
        const val EXTRA_VALUE_OPERATION = "operation"
    }

    override suspend fun doWork(): Result {
        // 1. 检查 App 是否已被用户打开过（避免安装后未启动就被打扰）
        if (!isAppOpened()) {
            return Result.success()
        }

        return try {
            // === v28 优化4：查询今日的排课（原为明天） ===
            // 业务背景：7:30 推送"今日有课"更符合教练早晨准备的需求
            val today = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))

            // 3. 查询今日的排课记录（一次性查询，非 Flow）
            val db = AppDatabase.getDatabase(applicationContext)
            val lessons = db.lessonDao().getByDateOnce(today)

            // === 第3轮优化：调用统一预警聚合器，输出长文本推文 ===
            // 取代旧版仅"长期未上课"的单一维度，新增课时包余额/即将过期/今日冲突
            val digest = AlertNotifier.buildDailyAlertDigest(db)

            createNotificationChannel()

            // 4. 今日有排课 → 发送今日排课通知（含预警汇总作为补充段）
            if (lessons.isNotEmpty()) {
                showScheduleNotification(today, lessons, digest)
            }

            // 5. 今日无课但有预警 → 单独发送一条预警汇总通知
            //    若今日已有排课通知，预警信息已合并到该通知的 BigText 中，
            //    此处仅在"今日无课但有预警"时独立推送，避免通知冗余
            //    免打扰时段（22:00-7:30）跳过独立预警，避免夜间打扰
            if (lessons.isEmpty() && digest.totalAlertCount > 0 && !AlertNotifier.isQuietHour()) {
                showInactiveStudentsNotification(digest)
            }

            Result.success()
        } catch (e: Exception) {
            // 出错时返回 success 避免 WorkManager 无限重试；
            // 明天会再次触发，不需要 retry
            Result.success()
        }
    }

    /**
     * 读取「App 已打开过」标记。
     * - 标记由 [ScheduleReminderManager.markAppOpened] 在 Application.onCreate 中设置
     * - 跨重启持久
     */
    private fun isAppOpened(): Boolean {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_APP_OPENED, false)
    }

    /**
     * 创建通知渠道（Android 8.0+ 要求）。
     * 幂等：重复调用不会报错。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "每日 7:30 提醒今日的排课记录"
                enableVibration(true)
                enableLights(false)
            }
            val manager = applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * === 第3轮优化：发送今日排课提醒通知（带点击跳转 + 多维预警汇总） ===
     *
     * 文案格式：
     * - 标题：今日（YYYY-MM-DD）共 N 节课
     * - 内容：第一节课时间 + 学员姓名；其余省略
     * - 详细文本：所有课程时间 + 学员姓名列表（展开通知栏可见）
     *             若存在预警，在末尾追加 AlertNotifier 生成的推文段
     *
     * 点击行为：
     * - 构建 PendingIntent 启动 MainActivity，并传递
     *   [ACTION_VIEW_TODAY_SCHEDULE] action + [EXTRA_NAVIGATE_TO]="operation"
     * - SportsApp 在启动时读取 Intent extra，自动导航到 Routes.OPERATION
     * - 使用 FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT 保证安全性与最新性
     */
    private fun showScheduleNotification(
        todayDate: String,
        lessons: List<com.shangmentiyu.sportscoach.data.model.Lesson>,
        digest: AlertNotifier.AlertDigest
    ) {
        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        // 构建简要标题（今日有 N 节课）
        val title = "今日（$todayDate）共有 ${lessons.size} 节课"

        // 构建内容摘要：第一节课的时间 + 学员
        val firstLesson = lessons.first()
        val contentText = if (lessons.size == 1) {
            "首节：${firstLesson.time} ${firstLesson.studentName}"
        } else {
            "首节：${firstLesson.time} ${firstLesson.studentName} 等 ${lessons.size} 节"
        }

        // 构建详细文本：所有课程时间 + 学员姓名（多行）
        val lessonsText = lessons.joinToString("\n") { lesson ->
            "• ${lesson.time} ${lesson.studentName}" +
                if (lesson.lessonType.isNotBlank()) "（${lesson.lessonType}）" else ""
        }

        // === 第3轮优化：在 BigText 末尾追加完整预警汇总推文 ===
        val detailText = buildString {
            append(lessonsText)
            if (digest.totalAlertCount > 0) {
                append("\n\n")
                append(digest.digestText)
            }
        }

        // === v28 优化4：构建通知点击 PendingIntent，跳转今日排课页 ===
        val clickIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = ACTION_VIEW_TODAY_SCHEDULE
            // EXTRA_NAVIGATE_TO 用于 SportsApp 启动时读取并跳转
            putExtra(EXTRA_NAVIGATE_TO, EXTRA_VALUE_OPERATION)
            // 清除栈顶 Activity，确保从主页开始跳转
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // FLAG_IMMUTABLE：Android 12+ 强制要求 PendingIntent 不可变
        // FLAG_UPDATE_CURRENT：复用相同 PendingIntent 时更新 extras
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            clickIntent,
            pendingFlags
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            // === v28 优化4：绑定点击意图，点击通知后跳转今日排课页 ===
            .setContentIntent(contentIntent)

        // Android 13+ (API 33+) 需要运行时申请 POST_NOTIFICATIONS 权限
        // 此处仅发送通知；权限由 MainActivity 在 UI 层申请
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * === 第3轮优化：发送独立预警汇总通知（多维预警合并） ===
     *
     * 触发条件：今日无排课且 [AlertNotifier.AlertDigest.totalAlertCount] > 0
     *
     * 文案格式：
     * - 标题：今日共 N 项预警
     * - 内容摘要：第一类预警 + 数量
     * - 详细文本：AlertNotifier 生成的完整推文
     *
     * 点击行为：与今日排课通知一致，跳转到今日排课页
     */
    private fun showInactiveStudentsNotification(digest: AlertNotifier.AlertDigest) {
        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val title = "⚠ 今日共 ${digest.totalAlertCount} 项预警"
        val contentText = buildString {
            if (digest.inactiveStudents.isNotEmpty()) {
                append("未上课 ${digest.inactiveStudents.size} 人")
            }
            if (digest.lowBalanceStudents.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("余额低 ${digest.lowBalanceStudents.size} 人")
            }
            if (digest.expiringPackages.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("即将过期 ${digest.expiringPackages.size} 个")
            }
            if (digest.conflicts.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("冲突 ${digest.conflicts.size} 处")
            }
            if (isEmpty()) append("暂无具体预警")
        }

        val clickIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = ACTION_VIEW_TODAY_SCHEDULE
            putExtra(EXTRA_NAVIGATE_TO, EXTRA_VALUE_OPERATION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID_INACTIVE,
            clickIntent,
            pendingFlags
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(digest.digestText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        manager.notify(NOTIFICATION_ID_INACTIVE, builder.build())
    }
}
