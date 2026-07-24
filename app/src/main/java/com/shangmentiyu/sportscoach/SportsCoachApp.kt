package com.shangmentiyu.sportscoach

import android.app.Application
import com.shangmentiyu.sportscoach.update.UpdateManager

/**
 * 应用入口，初始化全局组件。
 *
 * 自动更新初始化：
 * 1. 注册 WorkManager 定期检查任务（每天一次，Wi-Fi 下执行）
 * 2. 应用启动时立即执行一次即时检查
 */
class SportsCoachApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 注册每日定期更新检查
        UpdateManager.schedulePeriodicCheck(this)
        // 应用启动时立即检查一次更新
        UpdateManager.checkNow(this)
    }
}
