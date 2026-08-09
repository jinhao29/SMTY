package com.shangmentiyu.sportscoach.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Scorer 成绩校验测试（v50 负数成绩拦截）。
 *
 * 锁定不变量：负数成绩必须被拒绝（ok=false），不得被 scoreLess 误判为满分
 * 或由 coerceIn(0,100) 静默吸收后入库。
 *
 * 运行方式：./gradlew :app:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.core.ScorerValidationTest"
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
    fun `正数成绩_正常解析与评分`() {
        assertThat(Scorer.calcScore(std50m, "男", "7.5").ok).isTrue()
        assertThat(Scorer.calcScore(stdJump, "男", "210").ok).isTrue()
        assertThat(Scorer.calcScore(std50m, "男", "abc").ok).isFalse() // 非法字符仍拒绝
    }
}
