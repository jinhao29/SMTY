package com.shangmentiyu.sportscoach.domain.scheduling

import com.shangmentiyu.sportscoach.data.repo.OperationRepository

/**
 * 计算学员在指定日期的有效剩余课时（架构层二，v46）。
 *
 * === 设计目的 ===
 * 无论排课弹窗、主页还是长期排课，都通过本 UseCase 获取余额，
 * 底层数学逻辑唯一，杜绝"UI 传参不同导致计算结果不一致"。
 *
 * 数据获取走 [OperationRepository.getActivePackagesByStudent]（studentId 双通道查询），
 * 纯计算委托 [EffectiveRemainingCalculator]，两部分均可独立测试。
 *
 * @param operationRepository 运营数据仓库（课时包/课时/排课）
 */
class CalculateRemainingLessonsUseCase(
    private val operationRepository: OperationRepository
) {

    /**
     * @param studentName 学员姓名（软关联，双通道查询内部解析 studentId）
     * @param dateStr 待排课日期 YYYY-MM-DD
     * @return 该日期有效课时包的剩余总课时
     */
    suspend operator fun invoke(studentName: String, dateStr: String): Int {
        return EffectiveRemainingCalculator.calculate(
            packages = operationRepository.getActivePackagesByStudent(studentName),
            dateStr = dateStr
        )
    }
}
