package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 多租户模式配置（阶段二：Android 配置化）。
 *
 * 数据源：`assets/config/modes.json`，与桌面端 `student_sports_tool/config/modes.json`
 * **逐字节同源**（跨端锚定测试会比对，任一端改动未同步即失败）。
 *
 * 读取策略（与桌面端的"非法即拒绝启动"不同）：
 * assets 随 APK 固化，用户侧无法修复配置，因此**任何异常都回退内置默认**，
 * 绝不让用户打不开 App；配置错误属于发版前必须被测试拦住的问题。
 *
 * 兜底等级：
 * 1. assets 正常 → 用配置
 * 2. 读取失败 / JSON 损坏 / 缺少 modes / id·db_name 重复 → 用 [BUILTIN_MODES]
 *
 * ⚠️ [BUILTIN_MODES] 必须与 assets 里的 modes.json 内容一致（由单测锚定）。
 */
object ModeConfig {

    private const val TAG = "ModeConfig"

    /** assets 内的配置路径 */
    const val ASSET_PATH = "config/modes.json"

    /** 配置不可用时的默认模式 id */
    const val DEFAULT_MODE_ID = "coaching"

    /**
     * 单个工作模式定义。
     *
     * @param dbName Android 库文件名（**改动等于换库，必须与历史值逐字节一致**）
     * @param aliases 历史值 → 本模式 id（如 club → club_evolve）
     */
    data class Mode(
        val id: String,
        val displayName: String,
        val dbName: String,
        val archiveDir: String,
        val enabled: Boolean,
        val aliases: Map<String, String>,
        val tagline: String,
        val badge: String,
        val icon: String,
    )

    /** 内置兜底配置：与 assets/config/modes.json 同源（单测锚定二者一致）。 */
    val BUILTIN_MODES: List<Mode> = listOf(
        Mode(
            id = "coaching",
            displayName = "上门体育",
            dbName = "sports_coach_db",
            archiveDir = "学员档案",
            enabled = true,
            aliases = mapOf("primary" to "coaching", "coach" to "coaching", "上门体育" to "coaching"),
            tagline = "学员档案 · 课时排课 · 财务记账 · 数据中心",
            badge = "常用",
            icon = "stopwatch",
        ),
        Mode(
            id = "club_evolve",
            displayName = "EVOLVE 俱乐部",
            dbName = "sports_coach_club_db",
            archiveDir = "学员档案俱乐部",
            enabled = true,
            aliases = mapOf("club" to "club_evolve", "俱乐部" to "club_evolve", "evolve" to "club_evolve"),
            tagline = "EVOLVE 进化体育 · 独立数据空间，与上门体育完全隔离",
            badge = "NEW",
            icon = "bolt",
        ),
    )

    @Volatile
    private var cached: List<Mode>? = null

    @Volatile
    private var cachedDefaultId: String = DEFAULT_MODE_ID

    /** 生效配置（未加载时用内置兜底，保证任何调用点都不会拿到空列表）。 */
    private fun current(): List<Mode> = cached ?: BUILTIN_MODES

    /**
     * 加载配置（幂等；Application.onCreate 早期调用一次即可）。
     *
     * @return 生效的模式列表（配置异常时为 [BUILTIN_MODES]）
     */
    fun load(context: Context): List<Mode> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val parsed = runCatching { parse(readAsset(context)) }
                .onFailure { Log.w(TAG, "模式配置不可用，回退内置默认：${it.message}") }
                .getOrNull()
            val result = parsed ?: BUILTIN_MODES
            cached = result
            if (parsed == null) cachedDefaultId = DEFAULT_MODE_ID
            return result
        }
    }

    /** 清空缓存（测试用；生产代码不需要）。 */
    fun clearCache() {
        synchronized(this) {
            cached = null
            cachedDefaultId = DEFAULT_MODE_ID
        }
    }

    private fun readAsset(context: Context): String =
        context.applicationContext.assets.open(ASSET_PATH)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    /**
     * 解析配置文本（internal 以便单测直接喂非法 JSON 验证校验路径）。
     *
     * @throws IllegalArgumentException 结构非法、缺字段或 id/db_name 重复
     */
    internal fun parse(raw: String): List<Mode> {
        val root = JSONObject(raw)
        val arr = root.optJSONArray("modes")
            ?: throw IllegalArgumentException("缺少 modes 数组")
        if (arr.length() == 0) throw IllegalArgumentException("modes 为空")

        val list = ArrayList<Mode>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: throw IllegalArgumentException("modes[$i] 不是对象")
            val id = o.optString("id").trim()
            val dbName = o.optString("db_name").trim()
            if (id.isEmpty()) throw IllegalArgumentException("modes[$i] 缺少 id")
            if (dbName.isEmpty()) throw IllegalArgumentException("modes[$i] 缺少 db_name")
            list += Mode(
                id = id,
                displayName = o.optString("display_name").trim().ifBlank { id },
                dbName = dbName,
                archiveDir = o.optString("archive_dir").trim(),
                enabled = o.optBoolean("enabled", true),
                aliases = parseAliases(o.optJSONObject("aliases")),
                tagline = o.optString("tagline"),
                badge = o.optString("badge"),
                icon = o.optString("icon"),
            )
        }
        validate(list)
        cachedDefaultId = root.optString("default_mode").trim().ifBlank { list.first().id }
        return list
    }

    private fun parseAliases(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val out = LinkedHashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.optString(k).trim()
            if (k.isNotBlank() && v.isNotBlank()) out[k] = v
        }
        return out
    }

    /**
     * 配置校验：id 与 db_name 必须唯一。
     *
     * db_name 重复意味着两个机构读写同一个库 —— 这是串库级事故，
     * 必须在加载阶段拦下（此处抛异常 → 上层回退内置并记日志）。
     */
    internal fun validate(list: List<Mode>) {
        val dupId = list.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(dupId.isEmpty()) { "模式 id 重复：$dupId" }
        val dupDb = list.groupBy { it.dbName }.filterValues { it.size > 1 }.keys
        require(dupDb.isEmpty()) { "库文件名重复：$dupDb" }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 全部模式（默认只返回 enabled 的，供选择页展示）。 */
    fun all(includeDisabled: Boolean = false): List<Mode> =
        if (includeDisabled) current() else current().filter { it.enabled }

    /** 按 id 取模式定义。 */
    fun byId(modeId: String?): Mode? {
        val key = modeId?.trim().orEmpty()
        if (key.isEmpty()) return null
        return current().firstOrNull { it.id == key }
    }

    /**
     * 把任意历史值/别名解析为模式 id。
     * 命中顺序：id 精确匹配 → aliases 映射 → display_name 匹配；无法解析返回 null。
     */
    fun resolveAlias(value: String?): String? {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return null
        val modes = current()
        modes.firstOrNull { it.id == v }?.let { return it.id }
        modes.firstOrNull { it.aliases.containsKey(v) }?.let { return it.aliases.getValue(v) }
        modes.firstOrNull { it.displayName == v }?.let { return it.id }
        return null
    }

    /** 默认模式 id（default_mode 未配置时取第一个模式）。 */
    fun defaultModeId(): String = cachedDefaultId

    /**
     * 模式 id → 库文件名。
     *
     * 无法解析时回退到默认模式的库名（**绝不返回空串**：空库名会让 Room
     * 建出非预期文件，等同丢数据）。
     */
    fun dbNameFor(modeId: String?): String {
        val resolved = resolveAlias(modeId) ?: defaultModeId()
        return byId(resolved)?.dbName
            ?: byId(defaultModeId())?.dbName
            ?: BUILTIN_MODES.first().dbName
    }
}
