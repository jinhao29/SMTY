package com.shangmentiyu.sportscoach

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.shangmentiyu.sportscoach.data.internal.AutoBackupScheduler
import com.shangmentiyu.sportscoach.app.framework.CrashHandler
import com.shangmentiyu.sportscoach.data.internal.DataRecoveryHelper
import com.shangmentiyu.sportscoach.data.internal.PreUpdateBackupManager
import com.shangmentiyu.sportscoach.app.framework.ScheduleReminderManager
import com.shangmentiyu.sportscoach.app.framework.UdpDesktopDiscoveryService
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import com.shangmentiyu.sportscoach.di.appModule
import com.shangmentiyu.sportscoach.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用入口，初始化全局组件。
 *
 * 启动优化（v16）：
 * 1. WorkManager.getInstance() 首次调用会触发内部 SQLite 初始化，主线程耗时 80-200ms，
 *    全部挪到 IO 线程，避免冷启动白屏。
 * 2. 即时更新检查延迟 3 秒执行，让首屏完全渲染后再发起网络请求，
 *    用户感知不到延迟但首帧时间大幅缩短。
 *
 * v20 引入：应用启动后后台回填学员 studentId（NULL → UUID），
 * 配合 v19→v20 的「软关联+改名事务」策略，保证旧数据平滑升级。
 *
 * v23 引入：[CrashHandler] 全局崩溃日志捕获。
 * - 必须在 super.onCreate 之后立即 install，覆盖后续所有子线程
 * - install 后检查是否存在历史崩溃日志，存在则 Logcat 提示（不弹 UI，避免阻塞启动）
 *
 * 保留 GitHub 自动更新网络代码与所有现有功能，仅优化初始化时机。
 */
class SportsCoachApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // === 终极防丢机制：启动前避风港备份 ===
        // 必须在所有其他初始化之前执行（包括 CrashHandler）。
        // 即使后续 CrashHandler install 失败、Room 打开数据库失败、App 闪退，
        // 也已有一份"启动前"的完整数据库文件可从 filesDir/PreUpdateBackup/ 恢复。
        //
        // 特点：
        // - 同步执行，确保备份完成后再进入其他初始化
        // - 失败仅记录日志，不抛异常，不阻塞 App 启动
        // - 保留最近 3 份，超出自动清理最旧文件夹
        runCatching { PreUpdateBackupManager.backupIfDbExists(this) }

        // === 急救备份数据验证：独立于 Room，用原生 SQLite 读取急救备份并验证数据完整性 ===
        // 若存在急救备份（EmergencyBackup），提取到 RecoveryTemp 临时目录，
        // 用原生 SQLite 只读打开并统计学员数量，确保数据完好可恢复。
        // 绝不修改原始数据库文件，失败仅记录日志不阻塞启动。
        runCatching {
            val count = DataRecoveryHelper.restoreFromEmergencyBackup(this)
            if (count >= 0) {
                android.util.Log.i("SportsCoachApp",
                    "急救备份数据验证完成，学员数量：$count 人")
            }
        }

        // === v46 架构层四：Koin 依赖注入容器初始化 ===
        // 在业务初始化之前启动；失败仅记录日志不阻塞启动（koinViewModel 调用方会有兜底）
        runCatching {
            startKoin {
                androidLogger()
                androidContext(this@SportsCoachApp)
                modules(appModule)
            }
        }

        // 1. 全局崩溃捕获：最早安装，覆盖后续所有线程
        //    - 同步落盘崩溃堆栈到 filesDir/crash_logs/
        //    - 透传给系统默认 Handler，不改变原有崩溃流程
        runCatching { CrashHandler.install(this) }

        // 2. 检查历史崩溃日志，存在则在 Logcat 提示（不阻塞启动）
        //    UI 层（如设置页）可按需读取该目录并提示用户反馈
        runCatching { CrashHandler.checkAndLogCrashFiles(this) }

        // v22 新增：标记 App 已被用户打开过
        // - 持久化到 SharedPreferences
        // - ScheduleReminderWorker 启动时检查此标记，未打开则静默成功
        // - 必须在 ScheduleReminderManager.scheduleDailyReminder 之前调用（虽然 Worker 异步执行）
        runCatching { ScheduleReminderManager.markAppOpened(this) }

        // === v46 修复：自动备份调度器提前同步初始化 ===
        // 原实现位于下方 ProcessLifecycleOwner 协程内（IO 线程延迟执行），
        // 若该协程因时序/异常未执行，notifyDataChange 会全部静默失效 → 自动备份"失效"。
        // init 仅做轻量赋值（注入 applicationContext + 置位标志），同步执行无性能风险，
        // 保证任何 Repository 首次数据变更前调度器必然已就绪。
        runCatching { AutoBackupScheduler.init(this) }

        // === v23 双端同步：自动备份成功后按「桌面同步」开关自动推送到 PC ===
        // - 静默执行：失败仅写 Log，不影响备份本身与用户操作
        // - 用户在设置页开启 syncEnabled + 填好 PC 地址后即生效（无需其他操作）
        runCatching {
            val koin = GlobalContext.get()
            val lanSync = koin.get<com.shangmentiyu.sportscoach.app.framework.LanSyncManager>()
            val settingsRepo = koin.get<com.shangmentiyu.sportscoach.data.repo.SettingsRepository>()
            AutoBackupScheduler.onBackupCompleted = { backupFile ->
                kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
                ).launch {
                    runCatching {
                        if (settingsRepo.syncEnabled.first()) {
                            val r = lanSync.pushBackupFile(backupFile)
                            android.util.Log.i("LanSync", "自动推送结果：${r.message}")
                        }
                    }.onFailure { e ->
                        android.util.Log.w("LanSync", "自动推送异常：${e.message}")
                    }
                }
            }
        }

        // 关键：使用 ProcessLifecycleOwner 的 lifecycleScope 在应用前台时延迟初始化
        // 避免在 Application.onCreate 主线程同步路径上阻塞首帧渲染
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            // 切到 IO 线程初始化 WorkManager（内部 SQLite 初始化不阻塞 UI）
            withContext(Dispatchers.IO) {
                // v46：自动备份调度器已在 onCreate 同步初始化（见上），此处不再重复调用
                runCatching {
                    UpdateManager.schedulePeriodicCheck(this@SportsCoachApp)
                }
                // v22 新增：注册每日排课提醒任务
                // === v28 优化4：触发时间从 21:00 调整为 7:30 ===
                // - 24h 周期触发
                // - 初始延迟到下一个 7:30 时刻
                // - 不限制网络与电量（核心业务提醒）
                runCatching {
                    ScheduleReminderManager.scheduleDailyReminder(this@SportsCoachApp)
                }
                // === v28 优化4：强制重新注册到 7:30（覆盖旧版本 21:00 任务）===
                // 业务背景：旧版本（v22）注册的 21:00 任务已通过 KEEP 策略保留，
                // 升级到 v28 后必须用 UPDATE 策略覆盖旧任务，否则仍然在 21:00 触发。
                // 本方法内部用 SharedPreferences 标记确保只执行一次。
                runCatching {
                    ScheduleReminderManager.forceRescheduleIfNeeded(this@SportsCoachApp)
                }
                // v32 优化3：启动 UDP 设备自动发现服务
                // - 监听桌面端心跳广播，写入 SharedPreferences 供 UI 顶部状态栏读取
                // - 教练打开 App 即可看到"已连接：电脑端 192.168.x.x"绿色指示灯
                runCatching {
                    UdpDesktopDiscoveryService.start(this@SportsCoachApp)
                }
                // v23.6：启动 PC 在线状态轮询（心跳优先，USB 回环 /health 探测兜底）
                // - 首页横幅与设置页「连接状态」订阅 desktopOnline，打开 App 即自动识别连接
                runCatching {
                    GlobalContext.get()
                        .get<com.shangmentiyu.sportscoach.app.framework.LanSyncManager>()
                        .startOnlineWatch()
                }
                // v23.1 双端同步：注册周期双向同步任务（每 30 分钟，仅 syncEnabled 开启时实际执行）
                runCatching {
                    com.shangmentiyu.sportscoach.app.framework.PeriodicSyncWorker.schedule(
                        this@SportsCoachApp
                    )
                }
                // v20：回填旧学员的 studentId（NULL → UUID），
                // 必须在数据库升级完成后执行；失败不阻塞启动。
                runCatching {
                    val db = AppDatabase.getDatabase(this@SportsCoachApp)
                    val repo = StudentRepository(db.studentDao(), db, db.studentFtsDao())
                    repo.backfillStudentIds()
                }
                // === v52 冷启动自动修正历史排课 + 重排（已禁用） ===
                // 用户反馈：每次启动自动修复排课导致课表被错误修改。
                // 已取消冷启动自动执行 fixHistoricalScheduleErrors，如需修正请手动触发。
                // runCatching {
                //     val opRepo = GlobalContext.get().get<OperationRepository>()
                //     val result = opRepo.fixHistoricalScheduleErrors()
                //     val totalCleaned = result.deletedSchedules + result.deletedPlaceholders
                //     android.util.Log.d("ScheduleFix",
                //         "历史排课修正完成，共清理 $totalCleaned 条错误排课")
                // }.onFailure { e ->
                //     android.util.Log.e("ScheduleFix",
                //         "历史排课修正失败：${e.message}", e)
                // }
            }
            // v23.9：订阅 PC 数据变更广播 → 真防抖（取消前一个延迟任务）后自动双向同步
            //（PC 端改学员/课时/收费后，手机同一 Wi-Fi 下自动跟上；连续广播合并成一次
            //  全量同步，syncMutex 再兜底防并发）
            runCatching {
                val lanSync = GlobalContext.get()
                    .get<com.shangmentiyu.sportscoach.app.framework.LanSyncManager>()
                var pendingSync: kotlinx.coroutines.Job? = null
                launch {
                    com.shangmentiyu.sportscoach.app.framework.UdpDesktopDiscoveryService
                        .dataChangedEvents.collect { _ ->
                            pendingSync?.cancel()
                            pendingSync = launch {
                                kotlinx.coroutines.delay(15_000)
                                runCatching { lanSync.syncNow() }
                            }
                        }
                }
            }
            // 首次启动延迟 3 秒再检查更新，让首屏完全渲染完
            delay(3000)
            runCatching {
                UpdateManager.checkNow(this@SportsCoachApp)
            }
        }
    }
}
