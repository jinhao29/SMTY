package com.shangmentiyu.sportscoach.domain.model

/**
 * 小班课批量签到/签退操作结果（处理层纯数据，供 UI 组装提示文案）。
 *
 * 各计数的语义因操作类型而异：
 * - 批量签到：[successCount] = 待签到翻转为已签到；[skippedCount] = 已签到跳过；
 *   [checkedOutCount] = 已签退跳过
 * - 批量签退：[successCount] = 已签到执行签退消课；[skippedCount] = 待签到（未签到）跳过；
 *   [checkedOutCount] = 已签退跳过
 *
 * @param successCount 本次成功操作人数
 * @param skippedCount 因状态不符跳过的人数（见上方语义说明）
 * @param checkedOutCount 因"已签退"跳过的人数
 * @param failedCount 操作失败人数（含课时记录不存在、无可用课时包等）
 */
data class BatchSignResult(
    val successCount: Int,
    val skippedCount: Int,
    val checkedOutCount: Int,
    val failedCount: Int
) {
    /** 总处理人数（用于防御性校验：四项之和应等于传入的课时 ID 数） */
    val total: Int get() = successCount + skippedCount + checkedOutCount + failedCount
}
