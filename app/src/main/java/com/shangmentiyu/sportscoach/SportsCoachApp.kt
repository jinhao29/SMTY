package com.shangmentiyu.sportscoach

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.shangmentiyu.sportscoach.update.UpdateManager
import kotlinx.coroutines.Dispatchers
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
 * 保留 GitHub 自动更新网络代码与所有现有功能，仅优化初始化时机。
 */
class SportsCoachApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 关键：使用 ProcessLifecycleOwner 的 lifecycleScope 在应用前台时延迟初始化
        // 避免在 Application.onCreate 主线程同步路径上阻塞首帧渲染
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            // 切到 IO 线程初始化 WorkManager（内部 SQLite 初始化不阻塞 UI）
            withContext(Dispatchers.IO) {
                runCatching {
                    UpdateManager.schedulePeriodicCheck(this@SportsCoachApp)
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
