package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 工作模式管理器（v23.13 多租户·配置化）。
 *
 * 模式清单不再硬编码，改为读 `assets/config/modes.json`
 * （见 [ModeConfig]，与桌面端 config/modes.json 同源）。
 * 每个模式对应一个独立的 Room 数据库文件，物理隔离零串库。
 *
 * 设计要点：
 * - 物理隔离而非全表 club_id 过滤：零串库风险，DAO 层几百个查询零改动
 * - [activeMode] 是 [AppDatabase.activeDatabaseName] 的选库依据，必须在任何 DB
 *   访问前由 Application.onCreate 同步初始化（SharedPreferences 同步读，无竞态）
 * - 切换模式 = 写偏好 + 关闭当前 Room 单例 + 翻转 activeMode；
 *   已创建的 ViewModel/Repository 持有旧库 DAO，切换后需重启 App 生效
 * - **兼容旧值**：v23.12 及更早写入的 `club` 经 aliases 解析为 `club_evolve`，
 *   库文件名不变，因此升级后数据零迁移
 */
object ModeManager {

    const val MODE_COACHING = "coaching"

    /**
     * 俱乐部模式 id。
     *
     * v23.13 起由 `"club"` 改为 `"club_evolve"` —— 引用本常量的调用点自动跟随；
     * 历史值（偏好文件里的 `club`、PC 下发的 `club`）由 [resolveAlias] 归一化。
     */
    const val MODE_CLUB = "club_evolve"

    private const val PREFS_FILE = "mode_prefs"
    private const val KEY_MODE = "work_mode"

    /** 当前生效模式（选库依据；启动时 init 同步赋值） */
    @Volatile
    var activeMode: String = MODE_COACHING
        private set

    private val _mode = MutableStateFlow(MODE_COACHING)

    /** 当前模式流（设置页展示/切换直接订阅） */
    val mode: StateFlow<String> = _mode.asStateFlow()

    /**
     * 当前模式对应的数据库文件名（由配置动态解析）。
     *
     * ⚠️ 改动这里等于换库：解析结果必须与历史值逐字节一致
     * （coaching → sports_coach_db / club_evolve → sports_coach_club_db），
     * 由单测锚点守护。
     */
    val activeDbName: String
        get() = ModeConfig.dbNameFor(activeMode)

    /** Application.onCreate 最早期同步调用（必须在 startKoin / 首次 getDatabase 之前） */
    fun init(context: Context) {
        // 先加载模式配置（assets 异常时内部回退内置默认），再做旧值归一化
        ModeConfig.load(context)
        val sp = context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        activeMode = resolveAlias(sp.getString(KEY_MODE, MODE_COACHING))
        _mode.value = activeMode
    }

    /**
     * 切换工作模式：持久化 → 关闭当前 Room 实例 → 翻转 activeMode。
     * 之后首次 [AppDatabase.getDatabase] 会挂载新模式的独立数据库文件。
     *
     * @return 归一化后的模式值
     */
    fun setMode(context: Context, newMode: String): String {
        ModeConfig.load(context)
        val m = resolveAlias(newMode)
        // commit 同步落盘：调用方（设置页/启动页）紧随其后 killProcess 重启，
        // apply 异步写有丢偏好风险（重启后模式回退）
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, m).commit()
        if (m != activeMode) {
            // 先关旧实例（WAL 刷盘），再翻转选库依据——之后的 getDatabase 挂新库
            AppDatabase.closeAndResetInstance(context.applicationContext)
            activeMode = m
            _mode.value = m
        }
        return m
    }

    /**
     * 任意模式标识 → 规范模式 id（id / 历史值 / 别名 / 显示名均可）。
     * 无法解析时回退默认模式（[ModeConfig.defaultModeId]）。
     */
    fun resolveAlias(raw: String?): String =
        ModeConfig.resolveAlias(raw) ?: ModeConfig.defaultModeId()

    /**
     * 两个模式标识是否等价（跨版本比较用）。
     *
     * ⚠️ 防串库判断必须走本方法：PC 端在升级过渡期可能下发旧值 `club`，
     * 直接字符串比较会误判为"模式不一致"从而拒绝同步。
     */
    fun isSameMode(a: String?, b: String?): Boolean {
        val ra = ModeConfig.resolveAlias(a) ?: return false
        val rb = ModeConfig.resolveAlias(b) ?: return false
        return ra == rb
    }

    /** 模式显示名（供 UI 文案；解析失败时回退规范化 id）。 */
    fun displayName(modeId: String? = activeMode): String {
        val key = resolveAlias(modeId)
        return ModeConfig.byId(key)?.displayName ?: key
    }

    /** 全部可选模式（启动页 / 设置页卡片由配置生成，新增机构零代码）。 */
    fun allModes(): List<ModeConfig.Mode> = ModeConfig.all()

    /** 当前模式的完整定义。 */
    fun activeModeDef(): ModeConfig.Mode? = ModeConfig.byId(activeMode)
}
