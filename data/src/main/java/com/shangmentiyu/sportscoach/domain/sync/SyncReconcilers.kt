package com.shangmentiyu.sportscoach.domain.sync

import com.shangmentiyu.sportscoach.data.model.LessonPackage

/**
 * 双端对账纯计算器（v35，无 IO / 无状态，可独立单元测试）。
 *
 * 背景与语义（详见 desktop_sync/双端同步协议.md「数据真统一与安全锁」）：
 * - PC 端是「单汇总模型」：每学员一个总课时（课时记录.xlsx 汇总表）+ 从明细推导的已上课时；
 * - 手机端是「多包模型」：一学员可有多张课时包，usedLessons 是手机端扣课事实；
 * - 同步必须让两端的「已购总量 / 消课数」收敛到同一业务事实，且**绝不破坏任何一端数据**。
 *
 * 安全锁（单调性护栏，两端同源）：
 * 1. PC 空数据（total<=0 且 attended<=0）绝不动手机现值；
 * 2. 手机课时包总量经同步后单调不减（PC 较旧/纠正性缩减不自动传播，退款需人工两端处理）；
 * 3. 任何包的 total 不得低于其 usedLessons（已消课事实不可被抹掉）；
 * 4. PC 消课差值只增不减（appliedPcLessons 单调），PC 删除消课不回退手机已折算值。
 */

/**
 * 课时包总量对账：手机现有多包 + PC 汇总 → 手机端应执行的操作集。
 */
object PackageReconciler {

    /** 调整现有包总量 */
    data class SetTotal(val pkg: LessonPackage, val newTotal: Int)

    /** 新建「PC 同步」包（PC 端新购 / 多包场景的正差值） */
    data class CreateNew(val studentName: String, val total: Int)

    data class Result(
        val setTotals: List<SetTotal> = emptyList(),
        val creates: List<CreateNew> = emptyList(),
        /** 被安全锁拒绝或无需操作的原因（供同步日志/消息展示） */
        val skipped: List<String> = emptyList()
    )

    /**
     * @param studentName 学员姓名（新建包归属）
     * @param pcTotal PC 端已购总课时（课时记录汇总）
     * @param pcAttended PC 端已上课时（仅用于空数据判定，不覆盖手机已用）
     * @param packages 手机端该学员全部课时包（含非活跃）
     * @param today 新建包的购买日期（YYYY-MM-DD）
     */
    fun reconcile(
        studentName: String,
        pcTotal: Int,
        pcAttended: Int,
        packages: List<LessonPackage>,
        today: String
    ): Result {
        // 锁 1：PC 端无课时数据 ≠「清空」，绝不动手机现值（v23.7.1 原则延续）
        if (pcTotal <= 0 && pcAttended <= 0) return Result()

        val nonRefunded = packages.filter { it.status != "已退费" }
        if (nonRefunded.isEmpty()) {
            return if (pcTotal > 0) {
                Result(creates = listOf(CreateNew(studentName, pcTotal)))
            } else {
                Result(skipped = listOf("$studentName：PC 总课时为 0，手机无包，跳过"))
            }
        }

        if (nonRefunded.size == 1) {
            val pkg = nonRefunded.first()
            if (pcTotal == pkg.totalLessons) return Result()
            // 锁 3：总课时不得低于已用（已消课事实不可抹掉）
            if (pcTotal < pkg.usedLessons) {
                return Result(skipped = listOf(
                    "$studentName：PC 总课时($pcTotal)低于手机已用(${pkg.usedLessons})，安全锁拒绝覆盖"))
            }
            // 单包：PC 值可增可减（教练在 PC 的修正与加购都能落到唯一包上）
            return Result(setTotals = listOf(SetTotal(pkg, pcTotal)))
        }

        // 多包：以「未退费包总量之和」对账
        val phoneSum = nonRefunded.sumOf { it.totalLessons }
        val delta = pcTotal - phoneSum
        return when {
            delta == 0 -> Result()
            delta > 0 -> Result(creates = listOf(CreateNew(studentName, delta)))
            else -> Result(skipped = listOf(
                "$studentName：多包总量($phoneSum)高于 PC($pcTotal)，安全锁拒绝缩减"))
        }
    }
}

/**
 * PC 消课差值对账：PC 已上课时中「手机端没有记录的部分」（PC 独录的消课）
 * 折算进手机课时包已用，保证两端消课口径一致。
 *
 * 口径对齐：PC 已上课时 = 手机备份携带的全部课时行（含待签到占位/体验课——
 * 体验课两端同增相消）+ PC 独录明细；因此手机侧对齐基线 = 手机端全部课时行数
 * （lessons + archived_lessons，由调用方统计，与 PC 的 meta 导出口径一致）。
 */
object ConsumptionReconciler {

    data class Result(
        /** 本次应折算进课时包已用的节数（调用方按剩余课时截断后实加） */
        val unitsToApply: Int,
        /** 折算后的对账进度（写入 pc_sync_state.appliedPcLessons） */
        val newApplied: Int
    )

    /**
     * @param pcAttended PC 端该学员已上课时（明细推导）
     * @param phoneLessonCount 手机端该学员全部课时行数（与 PC meta 导出口径一致）
     * @param appliedPcLessons 已折算的累计值（pc_sync_state）
     */
    fun reconcile(pcAttended: Int, phoneLessonCount: Int, appliedPcLessons: Int): Result {
        val target = (pcAttended - phoneLessonCount).coerceAtLeast(0)
        // 锁 4：单调不减——PC 删除消课/旧备份不回退手机已折算值
        if (target <= appliedPcLessons) return Result(0, appliedPcLessons)
        return Result(target - appliedPcLessons, target)
    }
}
