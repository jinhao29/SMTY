package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 工作模式管理器（v23.12 多租户·物理隔离）。
 *
 * 两个模式对应两个独立的 Room 数据库文件：
 * - [MODE_COACHING] 上门体育 → sports_coach_db（既有库，历史数据全在这里）
 * - [MODE_CLUB]     俱乐部   → sports_coach_club_db（独立空库）
 *
 * 设计要点：
 * - 物理隔离而非全表 club_id 过滤：零串库风险，DAO 层几百个查询零改动
 * - [activeMode] 是 AppDatabase.getDatabase 选库依据，必须在任何 DB 访问前
 *   由 Application.onCreate 同步初始化（SharedPreferences 同步读，无竞态）
 * - 切换模式 = 写偏好 + 关闭当前 Room 单例 + 翻转 activeMode；
 *   已创建的 ViewModel/Repository 持有旧库 DAO，切换后需重启 App 生效
 *   （与既有"恢复数据后必须重启 App"语义一致）
 */
object ModeManager {

    const val MODE_COACHING = "coaching"
    const val MODE_CLUB = "club"

    private const val PREFS_FILE = "mode_prefs"
    private const val KEY_MODE = "work_mode"

    /** 当前生效模式（AppDatabase.getDatabase 按此选库；启动时 init 同步赋值） */
    @Volatile
    var activeMode: String = MODE_COACHING
        private set

    private val _mode = MutableStateFlow(MODE_COACHING)

    /** 当前模式流（设置页展示/切换直接订阅） */
    val mode: StateFlow<String> = _mode.asStateFlow()

    /** Application.onCreate 最早期同步调用（必须在 startKoin / 首次 getDatabase 之前） */
    fun init(context: Context) {
        val sp = context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        activeMode = normalize(sp.getString(KEY_MODE, MODE_COACHING))
        _mode.value = activeMode
    }

    /**
     * 切换工作模式：持久化 → 关闭当前 Room 实例 → 翻转 activeMode。
     * 之后首次 [AppDatabase.getDatabase] 会挂载新模式的独立数据库文件。
     *
     * @return 归一化后的模式值
     */
    fun setMode(context: Context, newMode: String): String {
        val m = normalize(newMode)
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, m).apply()
        if (m != activeMode) {
            // 先关旧实例（WAL 刷盘），再翻转选库依据——之后的 getDatabase 挂新库
            AppDatabase.closeAndResetInstance(context.applicationContext)
            activeMode = m
            _mode.value = m
        }
        return m
    }

    private fun normalize(raw: String?): String =
        if (raw == MODE_CLUB) MODE_CLUB else MODE_COACHING
}
