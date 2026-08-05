package com.shangmentiyu.sportscoach

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.shangmentiyu.sportscoach.core.AutoBackupScheduler
import com.shangmentiyu.sportscoach.core.CrashHandler
import com.shangmentiyu.sportscoach.core.PreUpdateBackupManager
import com.shangmentiyu.sportscoach.core.ScheduleReminderManager
import com.shangmentiyu.sportscoach.core.UdpDesktopDiscoveryService
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import com.shangmentiyu.sportscoach.di.appModule
import com.shangmentiyu.sportscoach.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
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
                // v20：回填旧学员的 studentId（NULL → UUID），
                // 必须在数据库升级完成后执行；失败不阻塞启动。
                runCatching {
                    val db = AppDatabase.getDatabase(this@SportsCoachApp)
                    val repo = StudentRepository(db.studentDao(), db, db.studentFtsDao())
                    repo.backfillStudentIds()
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
