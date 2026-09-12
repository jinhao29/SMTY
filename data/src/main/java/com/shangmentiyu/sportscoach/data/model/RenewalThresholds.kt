package com.shangmentiyu.sportscoach.data.model

/**
 * 续费预警阈值（⚠️ 单一真源）。
 *
 * 历史问题：本应用与桌面端各自硬编码阈值，导致同一学员两端"是否需要续费"的判定不同
 * （Android 剩余≤3，桌面端默认≤5）。现收敛为命名常量，并由桌面端
 * `test_renewal_thresholds_parity.py` 解析本文件做跨端锚定 ——
 * **改这里必须同步改桌面端 `data_center/renewal_processor.py` 的同名常量。**
 */
object RenewalThresholds {

    /** 剩余课时 ≤ 此值视为「即将用完」，触发续费提醒（对应 PC: DEFAULT_RENEWAL_THRESHOLD） */
    const val LOW_BALANCE_REMAINING = 3

    /** 课时包到期前 N 天内视为「临近过期」（PC 尚未实现该维度预警） */
    const val NEAR_EXPIRY_DAYS = 30
}
