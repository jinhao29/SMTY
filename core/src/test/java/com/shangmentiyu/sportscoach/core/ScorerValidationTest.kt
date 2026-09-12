package com.shangmentiyu.sportscoach.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Scorer 成绩校验测试（v50 负数成绩拦截）。
 *
 * 锁定不变量：负数成绩必须被拒绝（ok=false），不得被 scoreLess 误判为满分
 * 或由 coerceIn(0,100) 静默吸收后入库。
 *
 * 2026-08-28 自 :app/src/test 迁入 :core（被测类 Scorer 属于 :core 模块）。
 *
 * 运行方式：./gradlew :core:test --tests "com.shangmentiyu.sportscoach.core.ScorerValidationTest"
 */
class ScorerValidationTest {

    /** 50 米跑：时间越短越好（LESS 方向） */
    private val std50m = Std(
        name = "50米跑",
        unit = "秒",
        direction = LESS,
        boysFull = 6.5,
        girlsFull = 7.5,
        boysPass = 9.5,
        girlsPass = 10.5
    )

    /** 立定跳远：距离越远越好（MORE 方向） */
    private val stdJump = Std(
        name = "立定跳远",
        unit = "cm",
        direction = MORE,
        boysFull = 230.0,
        girlsFull = 200.0,
        boysPass = 170.0,
        girlsPass = 150.0
    )

    @Test
    fun `负数成绩_解析失败_ok为false`() {
        val less = Scorer.calcScore(std50m, "男", "-5")
        assertThat(less.ok).isFalse()
        assertThat(less.msg).contains("负数")

        val more = Scorer.calcScore(stdJump, "男", "-20")
        assertThat(more.ok).isFalse()
        assertThat(more.msg).contains("负数")
    }

    @Test
    fun `parseValue_负数_抛IllegalArgumentException`() {
        val e = runCatching { Scorer.parseValue("-5", "秒") }.exceptionOrNull()
        assertThat(e).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(e?.message).contains("负数")
    }

    @Test
    fun `分秒负数_纯秒数输入_被拒绝`() {
        // v50 补丁回归："-5" 秒走 parseTime 纯秒数路径，曾绕过负数拦截得满分
        val e = runCatching { Scorer.parseValue("-5", "分秒") }.exceptionOrNull()
        assertThat(e).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(e?.message).contains("负数")

        val r = Scorer.calcScore(std50m.copy(unit = "分秒"), "男", "-5")
        assertThat(r.ok).isFalse()
        assertThat(r.msg).contains("负数")
    }

    @Test
    fun `分秒负数_带分格式输入_被拒绝`() {
        val e = runCatching { Scorer.parseValue("-1:05", "分秒") }.exceptionOrNull()
        assertThat(e).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(e?.message).contains("负数")
    }

    @Test
    fun `非分秒负数_拦截不回归`() {
        assertThat(Scorer.calcScore(stdJump, "男", "-20").ok).isFalse()
    }

    @Test
    fun `分秒正数_正常解析不受影响`() {
        assertThat(Scorer.parseValue("1:05", "分秒")).isEqualTo(65.0)
        assertThat(Scorer.parseValue("4'05\"", "分秒")).isEqualTo(245.0)
        assertThat(Scorer.parseValue("12.5", "分秒")).isEqualTo(12.5)
    }

    @Test
    fun `正数成绩_正常解析与评分`() {
        assertThat(Scorer.calcScore(std50m, "男", "7.5").ok).isTrue()
        assertThat(Scorer.calcScore(stdJump, "男", "210").ok).isTrue()
        assertThat(Scorer.calcScore(std50m, "男", "abc").ok).isFalse() // 非法字符仍拒绝
    }
}
