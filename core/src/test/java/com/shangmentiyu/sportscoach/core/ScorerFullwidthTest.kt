package com.shangmentiyu.sportscoach.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Scorer 全角标点归一化测试。
 *
 * 真机来源（2026-09-13，vivo V2426A）：中文输入法在成绩输入框句点输出为全角「。」，
 * 成绩被判「格式错误」，用户无法录入。修复即在 parseValue 入口做全角→半角归一化。
 *
 * 锁定不变量：
 * 1. 全角句点/数字/冒号/引号 与对应半角输入**解析结果完全相同**
 * 2. 全角负号归一化后仍走负数拦截（不得因归一化放过负数）
 * 3. 半角输入行为零变化（回归）
 *
 * 跨端：PC `scorer.normalize_input` 使用同一张映射表，
 * 由 `test_standards_parity.py` 锁定两端规则一致。
 *
 * 运行方式：./gradlew :core:test --tests "com.shangmentiyu.sportscoach.core.ScorerFullwidthTest"
 */
class ScorerFullwidthTest {

    /** 50 米跑：时间越短越好（LESS 方向），单位秒——真机出问题的就是这一类 */
    private val std50m = Std(
        name = "50米跑",
        unit = "秒",
        direction = LESS,
        boysFull = 6.5,
        girlsFull = 7.5,
        boysPass = 9.5,
        girlsPass = 10.5
    )

    @Test
    fun `全角句点_秒单位_正常解析`() {
        // 真机原样：「7。5」
        assertThat(Scorer.parseValue("7。5", "秒")).isEqualTo(7.5)
    }

    @Test
    fun `全角数字与句点_正常解析`() {
        assertThat(Scorer.parseValue("７。５", "秒")).isEqualTo(7.5)
    }

    @Test
    fun `全角引号与数字_分秒_正常解析`() {
        // ４＇０５＂ = 4分05秒 = 245 秒
        assertThat(Scorer.parseValue("４＇０５＂", "分秒")).isEqualTo(245.0)
    }

    @Test
    fun `全角冒号_分秒_正常解析`() {
        // 冒号在分秒单位里是 分:秒（与既有 ScorerValidationTest `1:05 → 65.0` 同口径）
        assertThat(Scorer.parseValue("1：05", "分秒")).isEqualTo(65.0)
        assertThat(Scorer.parseValue("4：05", "分秒")).isEqualTo(245.0)
    }

    @Test
    fun `全角负号_归一化后仍被负数拦截`() {
        // 归一化不得成为绕过负数校验的后门
        val e = runCatching { Scorer.parseValue("－5", "秒") }.exceptionOrNull()
        assertThat(e).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(e?.message).contains("负数")

        val r = Scorer.calcScore(std50m, "男", "－5")
        assertThat(r.ok).isFalse()
        assertThat(r.msg).contains("负数")
    }

    @Test
    fun `半角输入_行为不变_且全角与半角评分一致`() {
        // 回归：正常半角输入不受归一化影响
        assertThat(Scorer.parseValue("7.5", "秒")).isEqualTo(7.5)
        assertThat(Scorer.parseValue("4'05\"", "分秒")).isEqualTo(245.0)

        val full = Scorer.calcScore(std50m, "男", "7。5")
        val half = Scorer.calcScore(std50m, "男", "7.5")
        assertThat(full.ok).isTrue()
        assertThat(half.ok).isTrue()
        assertThat(full.score).isEqualTo(half.score)
        assertThat(full.value).isEqualTo(half.value)
    }

    @Test
    fun `归一化_ASCII与其他字符零改动`() {
        assertThat(Scorer.normalizeInput("7.5")).isEqualTo("7.5")
        assertThat(Scorer.normalizeInput("abc")).isEqualTo("abc")
        assertThat(Scorer.normalizeInput("4'05\"")).isEqualTo("4'05\"")
        // 「分」「秒」中文单位不是全角 ASCII，不得被改写
        assertThat(Scorer.normalizeInput("4分05秒")).isEqualTo("4分05秒")
    }
}
