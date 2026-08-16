package com.shangmentiyu.sportscoach.data.repo

import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.data.internal.AutoBackupScheduler
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.db.ArchivedLessonDao
import com.shangmentiyu.sportscoach.data.db.CoachDao
import com.shangmentiyu.sportscoach.data.db.LessonDao
import com.shangmentiyu.sportscoach.data.db.LessonPackageDao
import com.shangmentiyu.sportscoach.data.db.SignInDao
import com.shangmentiyu.sportscoach.data.db.StudentDao
import com.shangmentiyu.sportscoach.data.model.ArchivedLesson
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.model.SignInRecord
import com.shangmentiyu.sportscoach.data.model.TrainingCycle
import com.shangmentiyu.sportscoach.domain.scheduling.EffectiveRemainingCalculator
import com.shangmentiyu.sportscoach.domain.scheduling.LongTermSchedulePlanner
import com.shangmentiyu.sportscoach.domain.scheduling.ScheduleValidationSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 运营管理 Repository（协调器 / Facade）。
 *
 * 统一封装 LessonPackage / Coach / Schedule / TrainingCycle 四类实体的数据访问，
 * 对上层提供业务语义清晰的方法，并包含阶段性总结的聚合计算。
 *
 * 拆分说明（v48 + v53）：
 * - 具体业务逻辑按领域下沉到独立 Repository，本类仅做方法委托（Facade），
 *   保持对外 API 不变，老调用方（ViewModel / UseCase）无需改动：
 *   - 训练周期 → [TrainingCycleRepository]
 *   - 阶段汇总计算 → [StageSummaryRepository]
 *   - 排课写操作 / 长期排课 / 历史修正 → [ScheduleQueryRepository]
 *   - 排课简单查询 → [ScheduleRepository]（复用已存在的排课仓库）
 *   - 签到 / 签退消课 / 撤销签到 → [LessonConsumptionRepository]（v53）
 *   - 批量自动排课 / 余额修复 → [BatchScheduleRepository]（v53）
 *   - 冷热数据归档 → [LessonArchiveRepository]（v53）
 *   - 课时包 CRUD / 提醒 / 教练 / 余额查询 等逻辑仍留在本类
 */
class OperationRepository(
    private val pkgDao: LessonPackageDao,
    private val coachDao: CoachDao,
    private val lessonDao: LessonDao,
    /** v45：学员 DAO，用于"修正历史错误排课"获取活跃学员列表 */
    private val studentDao: StudentDao,
    /** v22 新增：归档 DAO，冷热数据归档时使用 */
    private val archivedLessonDao: ArchivedLessonDao? = null,
    /** v22 新增：数据库实例，用于归档事务 */
    private val db: AppDatabase? = null,
    // === v48 拆分：子 Repository 注入 ===
    private val scheduleRepo: ScheduleRepository,
    private val scheduleQueryRepo: ScheduleQueryRepository,
    private val trainingCycleRepo: TrainingCycleRepository,
    private val stageSummaryRepo: StageSummaryRepository,
    /** v32：签到记录 DAO（排课与签到分离 + 防重） */
    private val signInDao: SignInDao,
    // === v53 拆分：域 Repository 注入 ===
    private val consumptionRepo: LessonConsumptionRepository,
    private val batchScheduleRepo: BatchScheduleRepository,
    private val archiveRepo: LessonArchiveRepository
) : ScheduleValidationSource {

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

    // === 阶段性总结（v48 拆分：数据类保留在本协调器以兼容 OperationRepository.StageSummary 旧引用，
    //    计算逻辑已下沉到 StageSummaryRepository） ===

    /**
     * 阶段总结数据：聚合指定学员在指定时间范围内的所有课时记录。
     */
    data class StageSummary(
        val studentName: String,
        val startDate: String,
        val endDate: String,
        val totalLessons: Int,
        val attendedLessons: Int,          // 实到（非请假非旷课）
        val attendanceRate: Float,         // 出勤率 0-1
        val avgPerformance: Float,         // 平均表现评分 1-10
        val avgDuration: Int,              // 平均课时时长
        val attitudeDistribution: Map<String, Int>,  // 态度分布
        val completedExerciseRate: Float,  // 训练动作完成率 0-1
        val scoreProgress: List<ScoreProgressItem>,  // 各项成绩的进步对比
        val firstLessonDate: String,
        val lastLessonDate: String,
        val summaryText: String            // 自动生成的总结文字
    )

    data class ScoreProgressItem(
        val name: String,
        val firstScore: Float,
        val lastScore: Float,
        val delta: Float,
        val samples: Int
    )

    // === 课程包 ===
    fun getAllPackages(): Flow<List<LessonPackage>> = pkgDao.getAll()
    fun getPackagesByStudent(name: String): Flow<List<LessonPackage>> = pkgDao.getByStudent(name)
    fun getActivePackages(): Flow<List<LessonPackage>> = pkgDao.getActive()
    fun countActivePackages(): Flow<Int> = pkgDao.countActive()
    suspend fun getPkgById(id: String): LessonPackage? = pkgDao.getById(id)

    /** v30：新增课时包属于核心数据变更，触发自动备份防抖 */
    suspend fun addPackage(pkg: LessonPackage) {
        pkgDao.insert(pkg)
        AutoBackupScheduler.notifyDataChange()
    }

    /** v30：更新课时包属于核心数据变更，触发自动备份防抖 */
    suspend fun updatePackage(pkg: LessonPackage) {
        pkgDao.update(pkg)
        AutoBackupScheduler.notifyDataChange()
    }

    /** v30：删除课时包属于核心数据变更，触发自动备份防抖 */
    suspend fun deletePackage(id: String) {
        pkgDao.deleteById(id)
        AutoBackupScheduler.notifyDataChange()
    }

    /** v32：清理无效课表（委托 ScheduleRepository，仅设置页手动触发），返回清理数量 */
    suspend fun clearExpiredUnsignedLessons(): Int = scheduleRepo.clearExpiredUnsignedLessons()

    // === 消课域（v53 拆分：委托 LessonConsumptionRepository） ===

    /** 教练手动签到（排课与签到分离 + 应用层/数据库层双防线防重），委托 [LessonConsumptionRepository.signIn] */
    suspend fun signIn(
        studentName: String,
        studentId: String?,
        operator: String = ""
    ): LessonConsumptionRepository.SignInResult =
        consumptionRepo.signIn(studentName, studentId, operator)

    /** 签退时消耗课时（事务内扣减课时包 + 更新 Lesson 为已签退），委托 [LessonConsumptionRepository.consumeLessonForCheckOut] */
    suspend fun consumeLessonForCheckOut(lesson: Lesson): LessonConsumptionRepository.ConsumeResult =
        consumptionRepo.consumeLessonForCheckOut(lesson)

    /**
     * 获取学员剩余课时汇总（按所有活跃包累加）。
     *
     * === v49 体验课：体验课不消耗课时包余额，不参与本汇总 ===
     */
    suspend fun getRemainingSummary(studentName: String): RemainingSummary {
        val packages = pkgDao.getByStudent(studentName).first()
        val active = packages.filter { it.status == "活跃" && !it.isExhausted && !it.isExpired }
        val total = active.sumOf { it.remainingLessons }
        val activeName = active.minByOrNull { it.purchaseDate }?.name ?: ""
        return RemainingSummary(studentName, total, activeName)
    }

    /** 撤销签到（单事务删除 Lesson + 恢复课时包 usedLessons），委托 [LessonConsumptionRepository.undoCheckIn] */
    suspend fun undoCheckIn(lessonId: String, studentName: String): LessonConsumptionRepository.UndoResult =
        consumptionRepo.undoCheckIn(lessonId, studentName)

    // === 批量排课域（v53 拆分：委托 BatchScheduleRepository） ===

    /** 批量自动排课（仅占位不扣余额，去重后事务内批量插入），委托 [BatchScheduleRepository.batchAutoSchedule] */
    suspend fun batchAutoSchedule(
        lessonDates: List<LocalDate>,
        studentName: String,
        studentId: String?,
        coachName: String,
        startTime: String,
        durationMinutes: Int,
        location: String,
        lessonType: String
    ): BatchScheduleRepository.BatchScheduleResult =
        batchScheduleRepo.batchAutoSchedule(
            lessonDates, studentName, studentId, coachName,
            startTime, durationMinutes, location, lessonType
        )

    /** 小班课批量排课（每名学员按额度截断，每个上课日独立 sessionGroupId），委托 [BatchScheduleRepository.batchAutoScheduleGroup] */
    suspend fun batchAutoScheduleGroup(
        lessonDates: List<LocalDate>,
        groupStudentIds: Set<String>,
        groupStudentNames: List<String>,
        memberQuotas: List<Int>,
        coachName: String,
        startTime: String,
        durationMinutes: Int,
        location: String,
        lessonType: String
    ): BatchScheduleRepository.BatchScheduleResult =
        batchScheduleRepo.batchAutoScheduleGroup(
            lessonDates, groupStudentIds, groupStudentNames, memberQuotas,
            coachName, startTime, durationMinutes, location, lessonType
        )

    /** 一次性修复脚本：回退排课阶段错误扣减的课时包余额，委托 [BatchScheduleRepository.fixPrematureBalanceDeduction] */
    suspend fun fixPrematureBalanceDeduction(): BatchScheduleRepository.BalanceFixResult =
        batchScheduleRepo.fixPrematureBalanceDeduction()
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
     * === v25 优化1：智能课时包到期预警（全局防遗忘） ===
     *
     * 查询所有有效期在 [daysThreshold] 天内到期的活跃课时包，
     * 用于在首页顶部展示动态提醒横幅，避免教练因仅能从详情页查看而过期遗漏。
     *
     * 过滤规则（同时满足）：
     * - status == "活跃"：仅关注仍可使用的课时包
     * - !isExhausted：排除已用完的包（已用完的包由 getRenewalAlerts 提醒）
     * - expireDate 非空 且 0 ≤ daysToExpiry ≤ daysThreshold：在阈值天数内即将过期
     *
     * 排序：按到期天数升序（最快过期的排最前），便于教练优先处理最紧急的项。
     *
     * @param daysThreshold 到期阈值天数，默认 7 天
     * @return 即将到期的课时包列表 Flow（按到期天数升序）
     */
    fun getExpiringPackages(daysThreshold: Int = 7): Flow<List<LessonPackage>> {
        return pkgDao.getActive().map { packages ->
            packages.filter { pkg ->
                pkg.status == "活跃" &&
                    !pkg.isExhausted &&
                    pkg.expireDate.isNotBlank() &&
                    pkg.daysToExpiry().let { it in 0..daysThreshold }
            }.sortedBy { it.daysToExpiry() }
        }
    }

    // === 教练 ===
    fun getActiveCoaches(): Flow<List<Coach>> = coachDao.getActive()
    fun getAllCoaches(): Flow<List<Coach>> = coachDao.getAll()
    suspend fun getCoachByName(name: String): Coach? = coachDao.getByName(name)
    suspend fun upsertCoach(coach: Coach) = coachDao.upsert(coach)
    suspend fun deleteCoach(name: String) = coachDao.deleteByName(name)

    // === 排课（委托 ScheduleRepository / ScheduleQueryRepository） ===
    fun getActiveSchedules(): Flow<List<Schedule>> = scheduleRepo.getActiveSchedules()
    fun getAllSchedules(): Flow<List<Schedule>> = scheduleRepo.getAllSchedules()
    fun getSchedulesByStudent(name: String): Flow<List<Schedule>> = scheduleRepo.getSchedulesByStudent(name)
    fun getSchedulesByCoach(name: String): Flow<List<Schedule>> = scheduleRepo.getSchedulesByCoach(name)
    fun getSchedulesByDay(dayOfWeek: Int): Flow<List<Schedule>> = scheduleRepo.getSchedulesByDay(dayOfWeek)
    suspend fun getScheduleById(id: String): Schedule? = scheduleRepo.getById(id)

    /** 小班课：查询同 groupScheduleId 的所有排课记录 */
    suspend fun getSchedulesByGroupScheduleId(groupScheduleId: String): List<Schedule> =
        scheduleRepo.getByGroupScheduleId(groupScheduleId)

    /**
     * 新增排课（事务写入 + 自动备份防抖）。
     *
     * 委托 [ScheduleQueryRepository.addSchedule]。
     *
     * @return true 表示写入成功；false 表示事务内出现异常（schedule 未落库）
     */
    suspend fun addSchedule(schedule: Schedule): Boolean = scheduleQueryRepo.addSchedule(schedule)

    /**
     * 更新排课（事务写入 + 自动备份防抖）。
     *
     * 委托 [ScheduleQueryRepository.updateSchedule]。
     *
     * @return true 表示更新成功；false 表示事务内出现异常（schedule 未变更）
     */
    suspend fun updateSchedule(schedule: Schedule): Boolean = scheduleQueryRepo.updateSchedule(schedule)

    /** v30：删除排课属于核心数据变更，触发自动备份防抖 */
    suspend fun deleteSchedule(id: String) = scheduleQueryRepo.deleteSchedule(id)

    /** 清空所有排课记录（课表管理"清空全部"功能） */
    suspend fun deleteAllSchedules() = scheduleQueryRepo.deleteAllSchedules()

    /**
     * 排课保存防重与异常抛出（v33 数据流加固）。
     *
     * 委托 [ScheduleQueryRepository.saveSchedule]。
     *
     * @return true=保存成功；false=保存失败（异常已记录到 Logcat）
     */
    suspend fun saveSchedule(schedule: Schedule): Boolean = scheduleQueryRepo.saveSchedule(schedule)

    /**
     * 查询学员在指定日期的排课（用于课后反馈自动填充）。
     *
     * 委托 [ScheduleQueryRepository.getTodayScheduleForStudent]。
     */
    suspend fun getTodayScheduleForStudent(
        studentName: String,
        dateStr: String
    ): List<Schedule> = scheduleQueryRepo.getTodayScheduleForStudent(studentName, dateStr)

    /**
     * 查重：指定学员+日期+时间是否已有课时记录（长期排课自动生成时调用）。
     *
     * 委托 [ScheduleQueryRepository.hasLessonForScheduleOnDate]。
     */
    suspend fun hasLessonForScheduleOnDate(studentName: String, date: String, time: String): Boolean =
        scheduleQueryRepo.hasLessonForScheduleOnDate(studentName, date, time)

    /**
     * 检查学员是否还能排课：剩余课时包余额 > 未来未消课课时数。
     *
     * 委托 [ScheduleQueryRepository.canScheduleMoreLessons]。
     */
    suspend fun canScheduleMoreLessons(studentName: String, fromDate: String): Boolean =
        scheduleQueryRepo.canScheduleMoreLessons(studentName, fromDate)

    /**
     * === v49 三要素公式：已签退课时数（「已消耗」） ===
     *
     * 剩余可排课时 = 总课时(活跃包剩余之和) - 已消耗(已签退) - 待消耗(占位)。
     * 委托 [ScheduleQueryRepository.countCheckedOutLessons]。
     */
    override suspend fun countCheckedOutLessons(studentName: String): Int =
        scheduleQueryRepo.countCheckedOutLessons(studentName)

    /**
     * === v49 三要素公式：待消耗占位课时数（「待消耗」） ===
     *
     * 统计长期自动生成 + 未签退的占位课时数量，用于剩余可排课时计算。
     * 委托 [ScheduleQueryRepository.countPendingPlaceholderLessons]。
     */
    override suspend fun countPendingPlaceholderLessons(studentName: String, fromDate: String): Int =
        scheduleQueryRepo.countPendingPlaceholderLessons(studentName, fromDate)

    /** 根治口径：已排但未签退（signOutTime 为空、非体验课、今天及未来）的待消耗课时数，委托子仓库 */
    override suspend fun countUncheckedOutLessons(studentName: String, today: String): Int =
        scheduleQueryRepo.countUncheckedOutLessons(studentName, today)

    /**
     * === v49 长期排课统一生成入口（独立学员循环 + 逐日 + 额度封顶） ===
     *
     * 与 [com.shangmentiyu.sportscoach.ui.operation.OperationViewModel.ensureLongTermLessonsForWeek]
     * 共用同一生成策略：从 [weekStart] 起遍历未来日期，当天未排且剩余可排课时
     * （总-已消耗-待消耗）> 0 才生成一条占位并减 1；额度用尽立即停止该学员后续生成。
     *
     * 委托 [ScheduleQueryRepository.generateLongTermLessonsForStudent]。
     *
     * @return 本次新生成的课时数
     */
    suspend fun generateLongTermLessonsForStudent(
        studentName: String,
        weekStart: String,
        today: String,
        windowDays: Int = LongTermSchedulePlanner.DEFAULT_WINDOW_DAYS
    ): Int = scheduleQueryRepo.generateLongTermLessonsForStudent(studentName, weekStart, today, windowDays)

    /**
     * 根据长期排课 Schedule 生成一条课时记录（Lesson）。
     *
     * 委托 [ScheduleQueryRepository.generateLongTermLesson]。
     */
    suspend fun generateLongTermLesson(sched: Schedule, dateStr: String) =
        scheduleQueryRepo.generateLongTermLesson(sched, dateStr)

    /**
     * 一键修正历史错误排课（设置页入口，全量清理 + 重排）。
     *
     * 委托 [ScheduleQueryRepository.fixHistoricalScheduleErrors]。
     *
     * @return [ScheduleQueryRepository.ScheduleFixResult] 清理/重排统计
     */
    suspend fun fixHistoricalScheduleErrors(): ScheduleQueryRepository.ScheduleFixResult =
        scheduleQueryRepo.fixHistoricalScheduleErrors()

    // === v46：双通道辅助（studentId 优先、studentName 回退，兼容旧数据） ===

    /**
     * v46：解析学员姓名对应的 studentId（软关联外键，可能为 NULL）。
     *
     * 双通道查询用：studentId 优先、studentName 回退，兼容 v20 前的旧数据。
     * 查询失败（学员不存在等）返回 null，退化为按姓名匹配，不阻塞排课。
     */
    private suspend fun resolveStudentId(studentName: String): String? {
        return try {
            studentDao.getByName(studentName)?.studentId
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getActivePackagesByStudent(studentName: String): List<LessonPackage> {
        // v46：双通道查询（studentId 优先、studentName 回退），杜绝改名断链
        val sid = resolveStudentId(studentName)
        var packages = pkgDao.getByStudentDual(sid, studentName).first()
        // 回退：双通道查询在 studentId 与课时包不一致（旧数据回填遗漏）时可能漏查，
        // 降级为纯姓名查询兜底，确保有课时包的学员一定能算到剩余额度
        if (packages.isEmpty()) {
            packages = pkgDao.getByStudent(studentName).first()
        }
        return packages
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

    /**
     * 获取学员最早购买课时包的日期（含已过期/已耗尽课时包，"首次购买"是历史事实）。
     *
     * Bug 1 修复用：排课生效日 / 实际生成日早于首次购买日期时禁止排课。
     * 无任何课时包或查询失败时返回 null（调用方跳过校验，向后兼容旧数据）。
     *
     * @param studentName 学员姓名
     * @return 最早 purchaseDate（YYYY-MM-DD），无则 null
     */
    override suspend fun earliestPurchaseDateOf(studentName: String): String? {
        return try {
            // v46：双通道查询（studentId 优先、studentName 回退）
            val sid = resolveStudentId(studentName)
            pkgDao.getByStudentDual(sid, studentName).first()
                .map { it.purchaseDate }
                .filter { it.isNotBlank() }
                .minOrNull()
        } catch (_: Exception) {
            null
        }
    }

    // === 冷热数据归档域（v53 拆分：委托 LessonArchiveRepository） ===

    /** 归档指定日期前的课时记录（单事务迁移），委托 [LessonArchiveRepository.archiveLessonsBefore] */
    suspend fun archiveLessonsBefore(date: String): LessonArchiveRepository.ArchiveResult =
        archiveRepo.archiveLessonsBefore(date)

    /** 归档记录总数（诊断与统计） */
    fun getArchivedCount(): Flow<Int> = archiveRepo.getArchivedCount()

    /** 按学员查询归档记录（历史报表场景） */
    fun getArchivedByStudent(name: String): Flow<List<ArchivedLesson>> =
        archiveRepo.getArchivedByStudent(name)

    /** 启动时自动归档检查（超阈值静默归档一年前记录），委托 [LessonArchiveRepository.maybeAutoArchiveIfNeeded] */
    suspend fun maybeAutoArchiveIfNeeded(
        threshold: Int = 2000,
        archiveDaysOld: Long = 365L
    ): LessonArchiveRepository.ArchiveResult =
        archiveRepo.maybeAutoArchiveIfNeeded(threshold, archiveDaysOld)

    /** 一次性获取全部归档记录（按日期降序），委托 [LessonArchiveRepository.getAllArchivedOnce] */
    suspend fun getAllArchivedOnce(): List<ArchivedLesson> = archiveRepo.getAllArchivedOnce()
    /**
     * === v28：一次性获取全部课时包（非 Flow） ===
     *
     * 用于"学员成长 PDF 报告"等离线生成场景：避免订阅 Flow 后需要手动取消订阅的开销，
     * 一次性查询后立即返回快照数据。
     *
     * 调用方典型场景：
     * - [com.shangmentiyu.sportscoach.ui.growth.GrowthViewModel.generateGrowthReport]
     *   在后台线程汇总活跃课时包剩余总课时
     */
    suspend fun getAllPackagesOnce(): List<LessonPackage> = pkgDao.getAll().first()

    /**
     * === v28：一次性获取学员全部 lessons 记录（非 Flow，按日期升序） ===
     *
     * 用于"智能训练内容推荐"：
     * - 取学员最近一次体测成绩，识别弱项并生成推荐训练内容
     * - 由 [com.shangmentiyu.sportscoach.data.internal.TrainingContentRecommender] 调用
     *
     * 按日期升序返回，便于调用方使用 `lastOrNull()` 取最近一次记录。
     *
     * @param studentName 学员姓名
     * @return 该学员的全部 lessons 记录（按日期升序、时间升序）
     */
    suspend fun getLessonsByStudentOnce(studentName: String): List<Lesson> {
        // v46：双通道查询（studentId 优先、studentName 回退，兼容旧数据）
        val sid = resolveStudentId(studentName)
        return lessonDao.getByStudentDualOnce(sid, studentName).sortedBy { "${it.date} ${it.time}" }
    }

    /** 学员是否有正式课时记录（isTrial=0），用于"首次自动体验课"判断 */
    suspend fun hasFormalLessonsDual(studentId: String?, name: String): Boolean =
        lessonDao.countFormalLessonsDual(studentId, name) > 0

    /**
     * === v28：智能训练内容推荐（基于体测弱项） ===
     *
     * 业务背景：
     * - 教练在"添加排课"时往往从空白开始填写训练内容，缺乏科学依据
     * - 本方法基于学员最近一次体测成绩，自动识别弱项（50米跑、BMI等），
     *   生成一套"弱项纠正训练"默认文本供教练参考
     *
     * 数据来源：
     * - 学员最近一次体测成绩（从 lessons 表的 scores JSON 字段提取）
     * - 学员当前 BMI 值（从 Student 实体的 bmi 字段传入，避免新 DAO 依赖）
     *
     * 推荐策略：
     * - 取最近一次成绩中等级为"及格"或"不及格"的项目
     * - 按维度（速度/力量/耐力/柔韧/灵敏）匹配预设训练模板
     * - BMI ≥ 24（超重）时附加燃脂训练模板
     *
     * @param studentName 学员姓名
     * @param latestBmi 学员最近一次 BMI 值（0 表示无数据，跳过 BMI 推荐）
     * @return 推荐的训练内容 [ExerciseItem] 列表（最多 6 项，避免过长）
     *         若学员无体测成绩或无弱项，返回空列表
     */
    suspend fun recommendTrainingContent(
        studentName: String,
        latestBmi: Float = 0f
    ): List<com.shangmentiyu.sportscoach.data.model.ExerciseItem> {
        return try {
            // 1. 取学员最近 10 条 lessons（已按日期升序）
            val lessons = getLessonsByStudentOnce(studentName).takeLast(10)
            // 2. 提取所有成绩条目
            val scores = com.shangmentiyu.sportscoach.data.internal.AbilityAnalyzer.extractScores(lessons)
            // 3. 调用推荐器生成训练内容
            com.shangmentiyu.sportscoach.data.internal.TrainingContentRecommender.recommend(
                scores = scores,
                latestBmi = latestBmi
            )
        } catch (e: Exception) {
            android.util.Log.w("TrainingRec", "推荐失败：${e.message}")
            emptyList()
        }
    }

    // === 训练周期（委托 TrainingCycleRepository） ===
    fun getAllCycles(): Flow<List<TrainingCycle>> = trainingCycleRepo.getAllCycles()
    fun getActiveCycles(): Flow<List<TrainingCycle>> = trainingCycleRepo.getActiveCycles()
    fun getCyclesByStudent(name: String): Flow<List<TrainingCycle>> = trainingCycleRepo.getCyclesByStudent(name)
    suspend fun getCycleById(id: String): TrainingCycle? = trainingCycleRepo.getCycleById(id)
    suspend fun addCycle(cycle: TrainingCycle) = trainingCycleRepo.addCycle(cycle)
    suspend fun updateCycle(cycle: TrainingCycle) = trainingCycleRepo.updateCycle(cycle)
    suspend fun deleteCycle(id: String) = trainingCycleRepo.deleteCycle(id)

    /**
     * 创建周期并自动生成空的周计划列表。
     *
     * 委托 [TrainingCycleRepository.createCycle]。
     */
    suspend fun createCycle(
        studentName: String,
        name: String,
        goal: String,
        totalWeeks: Int,
        startDate: String
    ): String = trainingCycleRepo.createCycle(studentName, name, goal, totalWeeks, startDate)

    // === 阶段性总结（委托 StageSummaryRepository） ===

    /**
     * 计算学员的阶段总结。
     *
     * 委托 [StageSummaryRepository.computeStageSummary]。
     *
     * @param studentName 学员姓名
     * @param startDate 起始日期 YYYY-MM-DD（含）
     * @param endDate 结束日期 YYYY-MM-DD（含）
     */
    suspend fun computeStageSummary(
        studentName: String,
        startDate: String,
        endDate: String,
        allLessons: List<Lesson>
    ): StageSummary = stageSummaryRepo.computeStageSummary(studentName, startDate, endDate, allLessons)
}
