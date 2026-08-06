package com.shangmentiyu.sportscoach.data.repo

import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.core.AutoBackupScheduler
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.db.LessonPackageDao
import com.shangmentiyu.sportscoach.data.db.ScheduleDao
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.domain.scheduling.EffectiveRemainingCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 课时包 Repository（管理层）。
 *
 * 从原 [OperationRepository] 拆分而来，单一职责只管理课时包数据。
 *
 * 职责：
 * - 课时包 CRUD
 * - 续费提醒流（剩余不足 / 即将过期 / 已用完 / 已过期）
 * - 余额汇总与"按日期有效课时"计算（长期排课使用）
 *
 * 设计要点：
 * - 消课（扣减课时）统一收敛在 [OperationRepository.consumeLessonForCheckOut]，
 *   本仓库不持有消课逻辑（v47 起移除重复拷贝与私有 consumeMutex，
 *   避免绕过 OperationRepository 的调用方与它并发扣减同一课时包时互不互斥）。
 * - "有效课时包"按日期过滤：purchaseDate <= dateStr <= expireDate，
 *   修复了"7.24 买的课被排到 7.20"的数据流错误。
 */
class LessonPackageRepository(
    private val pkgDao: LessonPackageDao,
    // v44：可选注入 db 与 scheduleDao，启用删除课时包时级联清理排课
    // 未注入时（兼容旧调用方）仅删除课时包本身，不清理排课
    private val db: AppDatabase? = null,
    private val scheduleDao: ScheduleDao? = null
) {

    /**
     * 续费提醒：聚合单个学员单个课时包的提醒信息。
     */
    data class RenewalAlert(
        val studentName: String,
        val packageName: String,
        val remaining: Int,
        val daysToExpiry: Int,
        val reason: String            // "剩余不足" / "即将过期" / "已用完"
    )

    /**
     * 学员剩余课时汇总。
     */
    data class RemainingSummary(
        val studentName: String,
        val totalRemaining: Int,
        val activePackageName: String  // 最早购买的活跃包名（用于卡片显示）
    )

    // === 基本 CRUD ===
    fun getAllPackages(): Flow<List<LessonPackage>> = pkgDao.getAll()
    fun getPackagesByStudent(name: String): Flow<List<LessonPackage>> = pkgDao.getByStudent(name)
    fun getActivePackages(): Flow<List<LessonPackage>> = pkgDao.getActive()
    fun countActivePackages(): Flow<Int> = pkgDao.countActive()
    suspend fun getPkgById(id: String): LessonPackage? = pkgDao.getById(id)
    suspend fun addPackage(pkg: LessonPackage) = pkgDao.insert(pkg)
    suspend fun updatePackage(pkg: LessonPackage) = pkgDao.update(pkg)

    /**
     * 删除课时包：事务级联清理该学员名下所有排课记录。
     *
     * v44 修复：原实现仅删除课时包本身，导致学员"不上课了"后排课表仍有残留，
     * 今日排课数 / 未签到角标仍会统计到该学员。
     *
     * 级联策略：
     * 1. 先查询课时包拿到 studentName（删除后无法再查）
     * 2. 在单事务内删除课时包 + 按 studentName 物理删除排课
     * 3. 未注入 db/scheduleDao 时降级为仅删除课时包（兼容旧调用方）
     *
     * 注意：这里删除的是该学员所有排课，而非仅与该课时包关联的排课。
     * 原因：Schedule 表与 LessonPackage 是软关联（无外键），
     * 一个学员通常只有一个活跃课时包，删包即代表该学员不再上课。
     */
    suspend fun deletePackage(id: String) {
        // 先查到 studentName 用于级联清理（删后无法再查）
        val pkg = pkgDao.getById(id)
        val database = db
        val scheduleDaoLocal = scheduleDao
        if (database == null || scheduleDaoLocal == null) {
            // 兼容旧调用方：仅删除课时包本身
            pkgDao.deleteById(id)
            AutoBackupScheduler.notifyDataChange()
            return
        }
        database.withTransaction {
            // 表访问顺序：schedules 先于 lesson_packages
            pkg?.studentName?.takeIf { it.isNotBlank() }?.let { studentName ->
                scheduleDaoLocal.deleteByStudent(studentName)
            }
            pkgDao.deleteById(id)
        }
        AutoBackupScheduler.notifyDataChange()
    }

    /**
     * 获取学员剩余课时汇总（按所有活跃包累加）。
     */
    suspend fun getRemainingSummary(studentName: String): RemainingSummary {
        val packages = pkgDao.getByStudent(studentName).first()
        val active = packages.filter { it.status == "活跃" && !it.isExhausted && !it.isExpired }
        val total = active.sumOf { it.remainingLessons }
        val activeName = active.minByOrNull { it.purchaseDate }?.name ?: ""
        return RemainingSummary(studentName, total, activeName)
    }

    /**
     * 续费提醒流：观察所有课时包，过滤出需要续费的项。
     * 触发条件：剩余≤3 / 30天内过期 / 已用完但仍标记活跃。
     */
    fun getRenewalAlerts(): Flow<List<RenewalAlert>> {
        return pkgDao.getAll().map { list ->
            list.filter { pkg ->
                pkg.status == "活跃" && (
                    pkg.isLowBalance ||
                    pkg.isNearExpiry() ||
                    pkg.isExhausted ||
                    pkg.isExpired
                )
            }.map { pkg ->
                val reason = when {
                    pkg.isExhausted -> "已用完"
                    pkg.isExpired -> "已过期"
                    pkg.isLowBalance -> "剩余不足"
                    pkg.isNearExpiry() -> "即将过期"
                    else -> "需关注"
                }
                RenewalAlert(
                    studentName = pkg.studentName,
                    packageName = pkg.name,
                    remaining = pkg.remainingLessons,
                    daysToExpiry = pkg.daysToExpiry(),
                    reason = reason
                )
            }
        }
    }

    /**
     * 一次性获取学员所有活跃课时包（非 Flow，用于长期排课批量计算）。
     *
     * 活跃判定：status == "活跃" && !isExhausted && !isExpired
     * 注意：此处不过滤 purchaseDate / expireDate，由调用方按日期判断是否生效，
     * 因为同一学员可能在排课周内中途新增课时包（如周一买的课周五才生效）。
     *
     * @param studentName 学员姓名
     * @return 活跃课时包列表（按购买日期升序）
     */
    suspend fun getActivePackagesByStudent(studentName: String): List<LessonPackage> {
        return pkgDao.getByStudent(studentName).first()
            .filter { it.status == "活跃" && !it.isExhausted && !it.isExpired }
            .sortedBy { it.purchaseDate }
    }

    /**
     * 计算学员在指定日期"有效"的课时包剩余总课时。
     *
     * 有效判定（同时满足）：
     * - status == "活跃" && !isExhausted && !isExpired
     * - purchaseDate <= dateStr（购买日期不晚于排课日期）
     * - expireDate 为空 OR expireDate >= dateStr（未过期）
     *
     * 这是长期排课"按课时包日期范围排课"的核心：
     * 学员 24 号买的课，21 号排课时 effectiveRemaining = 0，自动跳过；
     * 25 号排课时 effectiveRemaining = 课时包剩余，正常生成。
     *
     * @param studentName 学员姓名
     * @param dateStr 待排课日期 YYYY-MM-DD
     * @return 该日期有效课时包的剩余总课时
     */
    suspend fun getEffectiveRemainingLessons(studentName: String, dateStr: String): Int {
        // v46 架构层二：纯计算委托 domain 计算器，与 CalculateRemainingLessonsUseCase 共享唯一实现
        return EffectiveRemainingCalculator.calculate(getActivePackagesByStudent(studentName), dateStr)
    }
}
