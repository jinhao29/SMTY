package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 多租户配置化测试（阶段二）。
 *
 * 覆盖：
 * 1. 从 assets 正常加载模式清单
 * 2. ★ 锚点：库名解析结果 == 历史硬编码值（改错等于换库，用户会以为数据丢了）
 * 3. 别名解析：旧值 club → club_evolve
 * 4. ★ 内置兜底与 assets 配置一致（防两份清单各自漂移）
 * 5. 配置校验：id / db_name 重复、缺字段一律拒绝
 * 6. ModeManager：旧 SP 值归一化、isSameMode 跨版本比较（窗口期防误拒）
 *
 * 运行：./gradlew :data:testDebugUnitTest --tests "*ModeConfigTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModeConfigTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ModeConfig.clearCache()
    }

    @After
    fun tearDown() {
        ModeConfig.clearCache()
    }

    // ------------------------------------------------------------------
    // 1. 正常加载
    // ------------------------------------------------------------------

    @Test
    fun `loads modes from assets config`() {
        val modes = ModeConfig.load(context)
        assertThat(modes.map { it.id }).containsExactly("coaching", "club_evolve").inOrder()

        val club = ModeConfig.byId("club_evolve")
        assertThat(club).isNotNull()
        assertThat(club!!.displayName).isEqualTo("EVOLVE 俱乐部")
        assertThat(club.archiveDir).isEqualTo("学员档案俱乐部")

        val coaching = ModeConfig.byId("coaching")
        assertThat(coaching!!.displayName).isEqualTo("上门体育")
    }

    @Test
    fun `all excludes disabled modes by default`() {
        ModeConfig.load(context)
        // 当前配置两者都启用
        assertThat(ModeConfig.all().map { it.id }).containsExactly("coaching", "club_evolve")
        assertThat(ModeConfig.all(includeDisabled = true).map { it.id })
            .containsExactly("coaching", "club_evolve")
    }

    // ------------------------------------------------------------------
    // 2. ★ 库名锚点（最关键：解析结果必须等于历史硬编码值）
    // ------------------------------------------------------------------

    @Test
    fun `db names match legacy hardcoded constants`() {
        ModeConfig.load(context)

        assertThat(ModeConfig.dbNameFor("coaching")).isEqualTo("sports_coach_db")
        assertThat(ModeConfig.dbNameFor("club_evolve")).isEqualTo("sports_coach_club_db")

        // 与 AppDatabase 保留的历史常量逐字节一致 —— 这是"零迁移"的守护断言
        assertThat(ModeConfig.dbNameFor("coaching")).isEqualTo(AppDatabase.DATABASE_NAME)
        assertThat(ModeConfig.dbNameFor("club_evolve")).isEqualTo(AppDatabase.CLUB_DATABASE_NAME)

        // 旧值也必须落到同一个库文件，否则升级后用户会"看不到数据"
        assertThat(ModeConfig.dbNameFor("club")).isEqualTo("sports_coach_club_db")
    }

    @Test
    fun `dbNameFor never returns blank for unknown mode`() {
        ModeConfig.load(context)
        // 无法解析时回退默认模式的库名，绝不返回空串（空库名会让 Room 建出非预期文件）
        assertThat(ModeConfig.dbNameFor("不存在的模式")).isEqualTo("sports_coach_db")
        assertThat(ModeConfig.dbNameFor(null)).isEqualTo("sports_coach_db")
        assertThat(ModeConfig.dbNameFor("")).isEqualTo("sports_coach_db")
    }

    // ------------------------------------------------------------------
    // 3. 别名解析
    // ------------------------------------------------------------------

    @Test
    fun `resolveAlias maps ids aliases and display names`() {
        ModeConfig.load(context)
        assertThat(ModeConfig.resolveAlias("coaching")).isEqualTo("coaching")
        assertThat(ModeConfig.resolveAlias("club_evolve")).isEqualTo("club_evolve")
        assertThat(ModeConfig.resolveAlias("club")).isEqualTo("club_evolve")
        assertThat(ModeConfig.resolveAlias("俱乐部")).isEqualTo("club_evolve")
        assertThat(ModeConfig.resolveAlias("primary")).isEqualTo("coaching")
        assertThat(ModeConfig.resolveAlias("EVOLVE 俱乐部")).isEqualTo("club_evolve")
        assertThat(ModeConfig.resolveAlias("  club  ")).isEqualTo("club_evolve")
        assertThat(ModeConfig.resolveAlias("garbage")).isNull()
        assertThat(ModeConfig.resolveAlias("")).isNull()
        assertThat(ModeConfig.resolveAlias(null)).isNull()
    }

    // ------------------------------------------------------------------
    // 4. ★ 兜底：内置清单与 assets 一致 + 未加载时可用
    // ------------------------------------------------------------------

    @Test
    fun `builtin modes match assets config`() {
        val fromAssets = ModeConfig.load(context)
        val fingerprint: (ModeConfig.Mode) -> List<String> =
            { listOf(it.id, it.displayName, it.dbName, it.archiveDir, it.tagline, it.badge, it.icon) }

        assertThat(fromAssets.map(fingerprint)).isEqualTo(ModeConfig.BUILTIN_MODES.map(fingerprint))
    }

    @Test
    fun `falls back to builtin before load`() {
        // 未调用 load（模拟配置不可用）：仍必须返回可用清单，而不是空列表
        ModeConfig.clearCache()
        assertThat(ModeConfig.all().map { it.id }).containsExactly("coaching", "club_evolve")
        assertThat(ModeConfig.defaultModeId()).isEqualTo("coaching")
        assertThat(ModeConfig.dbNameFor("club_evolve")).isEqualTo("sports_coach_club_db")
    }

    // ------------------------------------------------------------------
    // 5. 配置校验
    // ------------------------------------------------------------------

    @Test
    fun `duplicate db_name is rejected`() {
        val bad = """{"modes":[
            {"id":"a","db_name":"same_db"},
            {"id":"b","db_name":"same_db"}]}"""
        val error = runCatching { ModeConfig.parse(bad) }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error!!.message).contains("库文件名重复")
    }

    @Test
    fun `duplicate id is rejected`() {
        val bad = """{"modes":[
            {"id":"dup","db_name":"db_a"},
            {"id":"dup","db_name":"db_b"}]}"""
        val error = runCatching { ModeConfig.parse(bad) }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error!!.message).contains("模式 id 重复")
    }

    @Test
    fun `missing required fields are rejected`() {
        assertThat(runCatching { ModeConfig.parse("""{"modes":[{"id":"a"}]}""") }.isFailure).isTrue()
        assertThat(runCatching { ModeConfig.parse("""{"modes":[{"db_name":"a"}]}""") }.isFailure).isTrue()
        assertThat(runCatching { ModeConfig.parse("""{"version":1}""") }.isFailure).isTrue()
        assertThat(runCatching { ModeConfig.parse("""{"modes":[]}""") }.isFailure).isTrue()
        assertThat(runCatching { ModeConfig.parse("not json at all") }.isFailure).isTrue()
    }

    @Test
    fun `new tenant needs no code change`() {
        // ★ 阶段二核心目标：加一段配置即可用
        val cfg = """{
            "version": 1,
            "default_mode": "coaching",
            "modes": [
                {"id":"coaching","db_name":"sports_coach_db","archive_dir":"学员档案"},
                {"id":"club_beta","display_name":"BETA 俱乐部","db_name":"sports_coach_beta_db",
                 "archive_dir":"学员档案Beta","aliases":{"beta":"club_beta"}}
            ]}"""
        val modes = ModeConfig.parse(cfg)
        assertThat(modes.map { it.id }).containsExactly("coaching", "club_beta")
        // 未填的展示字段有合理默认
        assertThat(modes[1].displayName).isEqualTo("BETA 俱乐部")
        assertThat(modes[1].enabled).isTrue()
    }

    // ------------------------------------------------------------------
    // 6. ModeManager（偏好归一化 + 跨版本模式比较）
    // ------------------------------------------------------------------

    @Test
    fun `legacy pref value is normalized on init`() {
        // 模拟升级前写入的旧值
        context.getSharedPreferences("mode_prefs", Context.MODE_PRIVATE)
            .edit().putString("work_mode", "club").commit()

        ModeManager.init(context)

        assertThat(ModeManager.activeMode).isEqualTo("club_evolve")
        assertThat(ModeManager.activeDbName).isEqualTo("sports_coach_club_db")
    }

    @Test
    fun `activeDbName follows mode switch`() {
        ModeManager.init(context)                       // 偏好为空 → 默认 coaching
        assertThat(ModeManager.activeMode).isEqualTo("coaching")
        assertThat(ModeManager.activeDbName).isEqualTo("sports_coach_db")

        ModeManager.setMode(context, "club_evolve")
        assertThat(ModeManager.activeDbName).isEqualTo("sports_coach_club_db")

        // 再次用旧值切换，也要落到同一个库（零迁移）
        ModeManager.setMode(context, "club")
        assertThat(ModeManager.activeMode).isEqualTo("club_evolve")
        assertThat(ModeManager.activeDbName).isEqualTo("sports_coach_club_db")

        ModeManager.setMode(context, "coaching")
        assertThat(ModeManager.activeDbName).isEqualTo("sports_coach_db")
    }

    @Test
    fun `isSameMode tolerates legacy and new id`() {
        ModeConfig.load(context)
        // ★ 窗口期防误拒：PC 可能下发旧值 club，与本地 club_evolve 是同一模式
        assertThat(ModeManager.isSameMode("club", "club_evolve")).isTrue()
        assertThat(ModeManager.isSameMode("club_evolve", "club")).isTrue()
        assertThat(ModeManager.isSameMode("coaching", "coaching")).isTrue()
        // 不同模式、无法识别的值一律判不同（防串库宁可拒绝）
        assertThat(ModeManager.isSameMode("club", "coaching")).isFalse()
        assertThat(ModeManager.isSameMode("garbage", "coaching")).isFalse()
        assertThat(ModeManager.isSameMode(null, "coaching")).isFalse()
        assertThat(ModeManager.isSameMode("coaching", null)).isFalse()
    }

    @Test
    fun `displayName resolves ids and legacy values`() {
        ModeConfig.load(context)
        assertThat(ModeManager.displayName("coaching")).isEqualTo("上门体育")
        assertThat(ModeManager.displayName("club_evolve")).isEqualTo("EVOLVE 俱乐部")
        assertThat(ModeManager.displayName("club")).isEqualTo("EVOLVE 俱乐部")
    }

    @Test
    fun `allModes exposes configured modes to UI`() {
        ModeConfig.load(context)
        assertThat(ModeManager.allModes().map { it.id }).containsExactly("coaching", "club_evolve")
    }
}
