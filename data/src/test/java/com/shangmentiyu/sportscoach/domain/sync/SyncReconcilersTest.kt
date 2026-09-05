package com.shangmentiyu.sportscoach.domain.sync

import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import org.junit.Test

/**
 * 双端对账纯计算器测试（v35 数据真统一 + 安全锁）。
 *
 * 运行：./gradlew :data:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.domain.sync.SyncReconcilersTest"
 */
class SyncReconcilersTest {

    private fun pkg(
        total: Int,
        used: Int = 0,
        status: String = "活跃",
        createdAt: Long = 0
    ) = LessonPackage(
        studentName = "张三",
        name = "测试包",
        totalLessons = total,
        usedLessons = used,
        purchaseDate = "2026-01-01",
        status = status,
        createdAt = createdAt
    )

    // === PackageReconciler：PC 空数据保护 ===

    @Test
    fun `pc 空数据绝不动手机现值`() {
        val r = PackageReconciler.reconcile("张三", 0, 0, listOf(pkg(20, 5)), "2026-09-06")
        assertThat(r.setTotals).isEmpty()
        assertThat(r.creates).isEmpty()
    }

    @Test
    fun `手机无包且 pc 有总量则新建`() {
        val r = PackageReconciler.reconcile("张三", 30, 3, emptyList(), "2026-09-06")
        assertThat(r.creates).hasSize(1)
        assertThat(r.creates.first().total).isEqualTo(30)
    }

    // === PackageReconciler：单包 ===

    @Test
    fun `单包 pc 总量不同则更新（加购与修正均可）`() {
        val r = PackageReconciler.reconcile("张三", 40, 5, listOf(pkg(30, 5)), "2026-09-06")
        assertThat(r.setTotals).hasSize(1)
        assertThat(r.setTotals.first().newTotal).isEqualTo(40)

        val r2 = PackageReconciler.reconcile("张三", 25, 5, listOf(pkg(30, 5)), "2026-09-06")
        assertThat(r2.setTotals.first().newTotal).isEqualTo(25)  // 修正下调允许
    }

    @Test
    fun `单包 pc 总量低于已用则安全锁拒绝`() {
        val r = PackageReconciler.reconcile("张三", 3, 3, listOf(pkg(20, 8)), "2026-09-06")
        assertThat(r.setTotals).isEmpty()
        assertThat(r.skipped).isNotEmpty()
    }

    // === PackageReconciler：多包 ===

    @Test
    fun `多包正差值新建 PC 同步包`() {
        val r = PackageReconciler.reconcile(
            "张三", 45, 0, listOf(pkg(20), pkg(20, createdAt = 2)), "2026-09-06")
        assertThat(r.setTotals).isEmpty()
        assertThat(r.creates.first().total).isEqualTo(5)  // 45 - (20+20)
    }

    @Test
    fun `多包负差值安全锁拒绝缩减`() {
        val r = PackageReconciler.reconcile(
            "张三", 30, 0, listOf(pkg(20), pkg(20)), "2026-09-06")
        assertThat(r.creates).isEmpty()
        assertThat(r.setTotals).isEmpty()
        assertThat(r.skipped).isNotEmpty()
    }

    @Test
    fun `已退费包不计入对账总量`() {
        // 手机：活跃20 + 已退费20 → 排除退费后剩单包，PC 总量 25 落到活跃包
        val r = PackageReconciler.reconcile(
            "张三", 25, 0, listOf(pkg(20), pkg(20, status = "已退费")), "2026-09-06")
        assertThat(r.setTotals).hasSize(1)
        assertThat(r.setTotals.first().newTotal).isEqualTo(25)
    }

    @Test
    fun `多包零差值无操作`() {
        val r = PackageReconciler.reconcile(
            "张三", 40, 0, listOf(pkg(20), pkg(20)), "2026-09-06")
        assertThat(r.setTotals).isEmpty()
        assertThat(r.creates).isEmpty()
        assertThat(r.skipped).isEmpty()
    }

    // === ConsumptionReconciler ===

    @Test
    fun `pc 独录消课按差值折算`() {
        // PC 已上 8，手机课时行 3（全部已上）→ PC 独录 5 节
        val r = ConsumptionReconciler.reconcile(8, 3, 0)
        assertThat(r.unitsToApply).isEqualTo(5)
        assertThat(r.newApplied).isEqualTo(5)
    }

    @Test
    fun `重复同步幂等不重复折算`() {
        val first = ConsumptionReconciler.reconcile(8, 3, 0)
        val second = ConsumptionReconciler.reconcile(8, 3, first.newApplied)
        assertThat(second.unitsToApply).isEqualTo(0)
    }

    @Test
    fun `pc 已上课时倒退不回退已折算值`() {
        val r = ConsumptionReconciler.reconcile(5, 3, 5)
        assertThat(r.unitsToApply).isEqualTo(0)
        assertThat(r.newApplied).isEqualTo(5)  // 单调：保持已折算值
    }

    @Test
    fun `手机课时多于 pc 时不产生负折算`() {
        val r = ConsumptionReconciler.reconcile(3, 10, 0)
        assertThat(r.unitsToApply).isEqualTo(0)
        assertThat(r.newApplied).isEqualTo(0)
    }

    @Test
    fun `pc 新增消课后差值继续补齐`() {
        // 已折算 5；PC 消课 8→10，手机课时行不变 → 再补 2
        val r = ConsumptionReconciler.reconcile(10, 3, 5)
        assertThat(r.unitsToApply).isEqualTo(2)
        assertThat(r.newApplied).isEqualTo(7)
    }
}
