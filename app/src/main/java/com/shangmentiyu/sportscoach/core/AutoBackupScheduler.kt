package com.shangmentiyu.sportscoach.core

import android.content.Context
import android.util.Log
import com.shangmentiyu.sportscoach.data.repo.BackupRepository
import com.shangmentiyu.sportscoach.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 全自动无感备份调度器（协调层）。
 *
 * 设计目标：
 * - 监听核心数据变更（签退消课、学员编辑、排课增删）→ 防抖 10 分钟 → 静默执行备份
 * - 备份完全隐蔽：Dispatchers.IO 后台执行，不弹 Toast，不阻塞 UI
 * - 滚动备份：保留最近 [MAX_BACKUPS] 份自动备份，超出自动清理最旧文件
 * - 失败容忍：磁盘满等异常仅写本地 Log，不影响用户操作
 *
 * 使用方式：
 * 1. [SportsCoachApp.onCreate] 调用 [init] 完成初始化（注入 Application 上下文）
 * 2. 各 Repository 在写操作末尾调用 [notifyDataChange] 触发防抖计时
 * 3. 设置页开关切换时调用 [reloadSettings] 重新读取启用状态
 *
 * 与 [BackupManager] 的关系：
 * - [BackupManager] 是无状态处理器，负责打包/解包 ZIP
 * - 本调度器是协调层，负责「何时备份、备份到哪、清理哪些」的策略决策
 *
 * 与 [com.shangmentiyu.sportscoach.core.ScheduleReminderManager] 的区别：
 * - ScheduleReminderManager 基于 WorkManager 周期任务（每日固定时刻触发）
 * - 本调度器基于内存协程 + 防抖（数据变更后 10 分钟触发），更精准且省电
 *
 * 线程安全：
 * - [notifyDataChange] 可在任意线程调用，内部通过 [debounceMutex] 串行化
 * - 备份执行使用独立 SupervisorJob，与调用方协程隔离，避免父协程取消导致备份中断
 */
object AutoBackupScheduler {

    private const val TAG = "AutoBackupScheduler"

    /** 自动备份存放目录名（filesDir 子目录） */
    private const val BACKUP_DIR_NAME = "AutoBackups"

    /** 自动备份文件名前缀（与手动备份 smty_backup_ 区分，便于清理识别） */
    private const val FILE_PREFIX = "AutoBackup"

    /** 防抖延迟：自最后一次数据变更起等待 10 分钟后执行备份 */
    private const val DEBOUNCE_DELAY_MS = 10L * 60 * 1000  // 10 分钟

    /** 滚动备份保留份数：超过则删除最旧文件 */
    private const val MAX_BACKUPS = 5

    /** 单次备份最小间隔：避免短时间内反复触发（如批量导入）造成磁盘压力 */
    private const val MIN_INTERVAL_MS = 5L * 60 * 1000  // 5 分钟

    /** 应用上下文（由 [init] 注入，全局共享） */
    @Volatile
    private var appContext: Context? = null

    /** 备份执行协程作用域（独立 SupervisorJob，与调用方解耦） */
    private val backupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 当前防抖 Job：每次 [notifyDataChange] 取消上一个，启动新的 */
    @Volatile
    private var debounceJob: Job? = null

    /** 防抖操作串行化锁：避免并发 notify 造成 debounceJob 引用混乱 */
    private val debounceMutex = Mutex()

    /** 备份执行互斥锁：避免与「用户手动备份」并发触发 closeAndResetInstance */
    private val backupMutex = Mutex()

    /** 上次成功备份的时间戳（毫秒），用于 [MIN_INTERVAL_MS] 节流 */
    @Volatile
    private var lastBackupAtMs: Long = 0L

    /** 是否已初始化完成（[init] 调用后置 true） */
    @Volatile
    private var initialized: Boolean = false

    /**
     * 初始化调度器（由 [com.shangmentiyu.sportscoach.SportsCoachApp.onCreate] 调用）。
     *
     * - 注入应用上下文，用于访问 filesDir 与 DataStore
     * - 读取 [SettingsRepository.autoBackupEnabled] 决定是否启用
     * - 重复调用安全：仅首次调用生效
     *
     * @param context 应用上下文（建议传 applicationContext）
     */
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true
        Log.i(TAG, "自动备份调度器已初始化，等待数据变更触发")
    }

    /**
     * 通知数据发生变更（触发防抖计时）。
     *
     * 调用时机：
     * - [com.shangmentiyu.sportscoach.data.repo.OperationRepository.consumeLessonForCheckOut] 签退扣课时
     * - [com.shangmentiyu.sportscoach.data.repo.OperationRepository.undoCheckIn] 撤销签到
     * - [com.shangmentiyu.sportscoach.data.repo.StudentRepository.addStudent] / [updateStudent] / [renameStudent] / [deleteStudent]
     * - [com.shangmentiyu.sportscoach.data.repo.ScheduleRepository.addSchedule] / [deleteSchedule]
     *
     * 行为：
     * 1. 取消上一个未触发的防抖 Job
     * 2. 启动新的 10 分钟延迟 Job
     * 3. 10 分钟内若再次调用，重置计时
     *
     * 静默原则：本方法内部不抛异常，所有错误被吞掉，确保不影响业务流程。
     */
    fun notifyDataChange() {
        if (!initialized) return
        backupScope.launch {
            debounceMutex.withLock {
                // 取消上一个防抖任务（无论是否在运行）
                debounceJob?.cancel()
                // 启动新的防抖任务
                debounceJob = backupScope.launch {
                    try {
                        delay(DEBOUNCE_DELAY_MS)
                        // 延迟结束仍无新变更，执行备份
                        performAutoBackupIfNeeded()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // 被新的 notifyDataChange 取消，正常流程，不处理
                        throw e
                    } catch (e: Exception) {
                        // 防抖期间异常仅记录，不传播
                        Log.w(TAG, "防抖期间异常：${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 重新读取设置（用户在设置页切换开关后调用）。
     *
     * - 关闭开关时：取消当前防抖 Job，停止等待触发
     * - 开启开关时：无需主动触发，等待下次数据变更自然触发
     */
    suspend fun reloadSettings() {
        if (!initialized) return
        val enabled = readEnabledSetting()
        if (!enabled) {
            debounceMutex.withLock {
                debounceJob?.cancel()
                debounceJob = null
            }
            Log.i(TAG, "自动备份已关闭，取消待执行的备份任务")
        }
    }

    /**
     * 立即取消所有待执行任务（仅用于测试或应用退出）。
     */
    fun cancelAll() {
        backupScope.launch {
            debounceMutex.withLock {
                debounceJob?.cancel()
                debounceJob = null
            }
        }
    }

    /**
     * 释放资源（仅用于单元测试，应用运行时不应调用）。
     */
    fun shutdown() {
        cancelAll()
        backupScope.cancel()
        initialized = false
        appContext = null
    }

    // ===== 内部实现 =====

    /**
     * 执行自动备份（如已启用 + 满足最小间隔）。
     *
     * 流程：
     * 1. 校验启用状态与最小间隔
     * 2. 加 [backupMutex] 互斥锁，避免与手动备份并发
     * 3. 创建备份文件（[FILE_PREFIX]_YYYYMMDD_HHmmss.zip）
     * 4. 调用 [BackupRepository.backupToCache] 执行打包
     * 5. 清理旧备份，仅保留 [MAX_BACKUPS] 份
     * 6. 更新 [lastBackupAtMs]
     *
     * 全程静默：失败仅写 Log，不抛异常，不弹 Toast。
     */
    private suspend fun performAutoBackupIfNeeded() {
        val context = appContext ?: return

        // 1. 检查启用状态
        if (!readEnabledSetting()) {
            Log.d(TAG, "自动备份已关闭，跳过本次触发")
            return
        }

        // 2. 检查最小间隔节流
        val now = System.currentTimeMillis()
        if (now - lastBackupAtMs < MIN_INTERVAL_MS) {
            Log.d(TAG, "距上次备份不足 ${MIN_INTERVAL_MS / 60000} 分钟，跳过本次触发")
            return
        }

        // 3. 加互斥锁执行备份
        backupMutex.withLock {
            try {
                val backupDir = File(context.filesDir, BACKUP_DIR_NAME).apply {
                    if (!exists()) mkdirs()
                }

                // 3.1 生成备份文件名
                val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.getDefault())
                val fileName = "${FILE_PREFIX}_${LocalDateTime.now().format(formatter)}.zip"
                val backupFile = File(backupDir, fileName)

                // 3.2 调用 BackupRepository 执行实际打包
                // v32 优化1：注入 WebDavManager，备份成功后自动静默推送到教练私人网盘
                // 凭证通过 EncryptedSharedPreferences 加密存储，无明文落盘风险
                val webDavStore = com.shangmentiyu.sportscoach.data.repo.WebDavCredentialsStore(context)
                val webDavManager = WebDavManager(context, webDavStore)
                val backupRepo = BackupRepository(context, syncManager = null, webDavManager = webDavManager)
                val result = backupRepo.backupToCache(backupFile)

                if (result.success) {
                    lastBackupAtMs = System.currentTimeMillis()
                    Log.i(TAG, "自动备份成功：${backupFile.name}（${backupFile.length() / 1024} KB）")
                    // 3.3 v32 优化1：静默推送到 WebDAV 云盘（失败不影响主流程）
                    // 用户在设置页配置好 WebDAV 后，每次自动备份都会同步一份到教练私人网盘
                    // 实现"手机摔坏/电脑硬盘报废"场景下的终极异地灾备
                    try {
                        val pushResult = webDavManager.pushBackup(backupFile)
                        if (pushResult.success) {
                            Log.i(TAG, "WebDAV 云盘推送成功：${pushResult.remotePath}")
                        } else if (pushResult.message != "WebDAV 未启用") {
                            // 仅在已启用但失败时记录警告（未启用属于正常情况）
                            Log.w(TAG, "WebDAV 云盘推送跳过/失败：${pushResult.message}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "WebDAV 云盘推送异常：${e.message}")
                    }
                    // 3.4 清理旧备份
                    cleanupOldBackups(backupDir)
                } else {
                    // 备份失败：删除可能生成的不完整文件
                    if (backupFile.exists()) {
                        backupFile.delete()
                    }
                    Log.w(TAG, "自动备份失败：${result.message}")
                }
            } catch (e: Exception) {
                // 全静默：磁盘满、IO 异常等仅记录日志
                Log.e(TAG, "自动备份异常：${e.message}", e)
            }
        }
    }

    /**
     * 清理旧备份文件，仅保留最新 [MAX_BACKUPS] 份。
     *
     * 策略：
     * - 列出 [BACKUP_DIR_NAME] 目录下所有 [FILE_PREFIX]_*.zip 文件
     * - 按最后修改时间降序排序（最新在前）
     * - 保留前 [MAX_BACKUPS] 个，删除其余
     *
     * 失败容忍：清理异常仅记录日志，不影响本次备份的成功状态。
     */
    private fun cleanupOldBackups(backupDir: File) {
        try {
            val backups = backupDir.listFiles { file ->
                file.isFile &&
                    file.name.startsWith(FILE_PREFIX) &&
                    file.name.endsWith(".zip")
            }?.sortedByDescending { it.lastModified() } ?: return

            if (backups.size <= MAX_BACKUPS) return

            val toDelete = backups.drop(MAX_BACKUPS)
            var deletedCount = 0
            for (file in toDelete) {
                if (file.delete()) {
                    deletedCount++
                } else {
                    Log.w(TAG, "清理旧备份失败：${file.name}")
                }
            }
            if (deletedCount > 0) {
                Log.i(TAG, "已清理 $deletedCount 份旧自动备份（保留最新 $MAX_BACKUPS 份）")
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理旧备份异常：${e.message}")
        }
    }

    /**
     * 读取自动备份启用设置。
     *
     * 默认开启：[SettingsRepository.autoBackupEnabled] 未配置时返回 true。
     * 读取失败时返回 false，避免异常导致反复触发无效备份。
     */
    private suspend fun readEnabledSetting(): Boolean {
        val context = appContext ?: return false
        return try {
            val repo = SettingsRepository(context)
            repo.autoBackupEnabled.first()
        } catch (e: Exception) {
            Log.w(TAG, "读取自动备份设置失败，默认按关闭处理：${e.message}")
            false
        }
    }
}
