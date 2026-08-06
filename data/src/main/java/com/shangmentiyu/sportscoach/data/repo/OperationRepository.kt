package com.shangmentiyu.sportscoach.data.repo

import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.core.AutoBackupScheduler
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.db.ArchivedLessonDao
import com.shangmentiyu.sportscoach.data.db.CoachDao
import com.shangmentiyu.sportscoach.data.db.LessonDao
import com.shangmentiyu.sportscoach.data.db.LessonPackageDao
import com.shangmentiyu.sportscoach.data.db.StudentDao
import com.shangmentiyu.sportscoach.data.model.ArchivedLesson
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.model.TrainingCycle
import com.shangmentiyu.sportscoach.domain.scheduling.EffectiveRemainingCalculator
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
 * 拆分说明（v48）：
 * - 具体业务逻辑按领域下沉到独立 Repository，本类仅做方法委托（Facade），
 *   保持对外 API 不变，老调用方（ViewModel / UseCase）无需改动：
 *   - 训练周期 → [TrainingCycleRepository]
 *   - 阶段汇总计算 → [StageSummaryRepository]
 *   - 排课写操作 / 长期排课 / 历史修正 → [ScheduleQueryRepository]
 *   - 排课简单查询 → [ScheduleRepository]（复用已存在的排课仓库）
 *   - 课时包 / 消课 / 教练 / 归档 等逻辑仍留在本类
 *
 * v22 新增：冷热数据归档能力 [archiveLessonsBefore]，依赖 [db] 与 [archivedLessonDao]。
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
    private val stageSummaryRepo: StageSummaryRepository
) {

    /**
     * 消课结果：携带扣减的课时包信息，供上层记录到 Lesson 表与 UI 反馈。
     */
    data class ConsumeResult(
        val success: Boolean,
        val packageId: String = "",
        val packageName: String = "",
        val remainingAfter: Int = 0,
        val message: String = ""
    )

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

    /**
     * 消课并发保护锁：确保读 + 写在同一临界区内完成，
     * 避免并发签到时多协程读到相同余额并各自扣减，导致同一课时被扣多次。
     */
    private val consumeMutex = Mutex()

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

    /**
     * === v27：签退时消耗课时（重构签到消课逻辑） ===
     *
     * 签退时执行的事务化消课：在单事务内完成"扣减课时包 + 更新 Lesson 状态为已签退"，
     * 任一步失败整体回滚，保证数据绝对不会半途出错。
     *
     * 与旧逻辑（v27 前）的区别：
     * - 旧逻辑：签到时直接扣减，签到成功即视为消课完成（该方法已于 v47 移除，
     *   统一走本方法：签到时仅创建 status="已签到" 的 Lesson，不扣减课时包；
     *   签退时（教练保存课后反馈时）才执行本方法，扣减课时包并更新 Lesson.status="已签退"）
     *
     * 执行流程（@Transaction 原子操作）：
     * 1. 调用 [doConsumeLessonInternal] 找到最早购买的活跃课时包并 usedLessons + 1
     * 2. 更新 Lesson：status="已签退"，signOutTime=当前时间，packageId=扣减的课时包ID
     * 3. 任一步失败整体回滚
     *
     * 并发保护：复用 [consumeMutex]，与 [undoCheckIn] 共享锁，
     * 避免签退与撤销并发执行时余额计算错乱。
     *
     * @param lesson 待签退的课时记录（必须已存在，包含学员名、ID 等信息）
     * @return ConsumeResult.success=true 表示签退成功；
     *         false 表示无可用课时包或扣减失败（事务回滚，Lesson 状态不变）
     */
    suspend fun consumeLessonForCheckOut(lesson: Lesson): ConsumeResult = consumeMutex.withLock {
        val database = db ?: return@withLock ConsumeResult(
            success = false,
            message = "签退失败：数据库未初始化"
        )

        try {
            database.withTransaction {
                // 1. 先更新 Lesson：status="已签退" + signOutTime（表访问顺序：lessons 先于 lesson_packages）
                val nowTime = java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                )
                val updatedLesson = lesson.copy(
                    status = "已签退",
                    signOutTime = nowTime,
                    packageId = ""
                )
                val affected = lessonDao.update(updatedLesson)
                if (affected != 1) {
                    throw RuntimeException("Lesson 更新未生效（affected=$affected）")
                }

                // 2. 调用核心消课逻辑扣减课时包
                val consume = doConsumeLessonInternal(lesson.studentName)
                if (!consume.success) {
                    // 抛异常触发事务回滚，Lesson 状态保持"已签到"
                    throw RuntimeException("课时包扣减失败：${consume.message}")
                }

                // 3. 回填扣减的课时包 ID
                lessonDao.update(updatedLesson.copy(packageId = consume.packageId))

                android.util.Log.i("CheckOut",
                    "签退成功：${lesson.studentName} lessonId=${lesson.id} " +
                        "pkg=${consume.packageName} remaining=${consume.remainingAfter}")

                // v30：签退扣课时属于核心数据变更，触发自动备份防抖
                AutoBackupScheduler.notifyDataChange()

                consume
            }
        } catch (e: Exception) {
            android.util.Log.e("CheckOut", "签退失败：${e.message}", e)
            ConsumeResult(
                success = false,
                message = "签退失败：${e.message ?: "未知异常"}"
            )
        }
    }

    /**
     * 内部消课实现：不持锁，由 [consumeLessonForCheckOut] 在事务内调用。
     *
     * 本方法是全项目消课的唯一实现（v47 起移除各仓库重复拷贝）；
     * 不获取 [consumeMutex]（已由外层调用方持有），避免重入死锁。
     * 直接执行读 + 写 + 校验三步。
     */
    private suspend fun doConsumeLessonInternal(studentName: String): ConsumeResult {
        val packages = pkgDao.getByStudent(studentName).first()
        android.util.Log.d("ConsumeLesson",
            "学员=$studentName 查询到课时包${packages.size}个: ${packages.map { "${it.name}(status=${it.status},used=${it.usedLessons}/${it.totalLessons},expire=${it.expireDate})" }}")
        val active = packages.filter { it.status == "活跃" && !it.isExhausted && !it.isExpired }
        android.util.Log.d("ConsumeLesson", "过滤后活跃包${active.size}个")
        val target = active.minByOrNull { it.purchaseDate }
            ?: return ConsumeResult(success = false, message = "无可用课时包")

        val newUsed = (target.usedLessons + 1).coerceAtMost(target.totalLessons)
        val updated = if (newUsed >= target.totalLessons) {
            target.copy(usedLessons = target.totalLessons, status = "已用完")
        } else {
            target.copy(usedLessons = newUsed)
        }
        val affected = pkgDao.update(updated)

        if (affected != 1) {
            android.util.Log.e("ConsumeLesson",
                "update 受影响行数=$affected（预期1），扣减未落库！target.id=${target.id}")
            return ConsumeResult(
                success = false,
                message = "课时扣减失败（更新未生效）"
            )
        }

        val recheck = pkgDao.getById(target.id)
        if (recheck == null || recheck.usedLessons != updated.usedLessons) {
            android.util.Log.e("ConsumeLesson",
                "re-query 校验失败：期望used=${updated.usedLessons}，实际used=${recheck?.usedLessons}")
            return ConsumeResult(
                success = false,
                message = "课时扣减失败（校验不一致）"
            )
        }

        android.util.Log.d("ConsumeLesson",
            "扣减成功：${target.name} used ${target.usedLessons}->${updated.usedLessons} 剩余${updated.remainingLessons}")
        return ConsumeResult(
            success = true,
            packageId = target.id,
            packageName = target.name,
            remainingAfter = updated.remainingLessons,
            message = "已扣减课时（${target.name}）"
        )
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

    // === v23 撤销签到与恢复课时（容错机制） ===

    /**
     * 撤销签到结果：携带操作统计供 UI 反馈。
     *
     * @param success 是否成功
     * @param restoredPackageId 恢复的课时包 ID（无则空串）
     * @param restoredPackageName 恢复的课时包名（用于 Toast 显示）
     * @param remainingAfter 恢复后该课时包的剩余课时数
     * @param message 用户可读消息
     */
    data class UndoResult(
        val success: Boolean,
        val restoredPackageId: String = "",
        val restoredPackageName: String = "",
        val remainingAfter: Int = 0,
        val message: String
    )

    /**
     * 撤销签到：在单事务内删除 Lesson 记录并恢复对应课时包的 usedLessons。
     *
     * 适用场景：教练误触"签到"按钮后，可通过撤销操作回滚本次签到，
     * 避免手工修改课时包 usedLessons 的二次操作成本。
     *
     * 执行流程（单事务原子操作）：
     * 1. 通过 lessonId 查询 Lesson 记录，获取 packageId / studentName / date 等信息
     * 2. 物理删除该 Lesson 记录（从 lessons 表）
     * 3. 若 Lesson 关联了课时包（packageId 非空）：
     *    - 查询该课时包，校验 usedLessons > 0
     *    - usedLessons - 1，若原状态为"已用完"则恢复为"活跃"
     * 4. 任意一步失败则整体回滚，保证数据一致性
     *
     * 设计要点：
     * - 使用 [consumeMutex] 互斥锁保护读 + 写临界区，
     *   避免"撤销"与"签到/签退"并发执行时出现余额计算错乱
     * - 不允许 usedLessons 减为负数（coerceAtLeast(0)）
     * - 长期自动生成的课时（packageId = ""）仅删除 Lesson，不涉及课时包恢复
     *
     * @param lessonId 待撤销的 Lesson ID
     * @param studentName 学员姓名（用于日志与兜底校验，与 Lesson.studentName 必须一致）
     * @return [UndoResult] 携带操作结果
     */
    suspend fun undoCheckIn(lessonId: String, studentName: String): UndoResult = consumeMutex.withLock {
        val database = db ?: return@withLock UndoResult(
            success = false,
            message = "撤销失败：数据库未初始化"
        )

        try {
            database.withTransaction {
                // 1. 查询待撤销的 Lesson 记录
                val lesson = lessonDao.getById(lessonId)
                    ?: return@withTransaction UndoResult(
                        success = false,
                        message = "撤销失败：课时记录不存在（可能已被删除）"
                    )

                // 兜底校验：Lesson 学员名与传入学员名一致
                if (lesson.studentName != studentName) {
                    return@withTransaction UndoResult(
                        success = false,
                        message = "撤销失败：学员不匹配（${lesson.studentName} ≠ $studentName）"
                    )
                }

                // 2. 物理删除 Lesson 记录
                lessonDao.deleteById(lesson.id)

                // 3. 若关联了课时包，恢复 usedLessons
                val pkgId = lesson.packageId
                if (pkgId.isBlank()) {
                    // 长期自动生成的课时，无关联课时包，仅删除 Lesson
                    return@withTransaction UndoResult(
                        success = true,
                        message = "已撤销签到（未扣减课时，无需恢复）"
                    )
                }

                val pkg = pkgDao.getById(pkgId)
                    ?: return@withTransaction UndoResult(
                        success = true,
                        restoredPackageId = pkgId,
                        message = "已撤销签到，但课时包不存在（可能已被删除）"
                    )

                // 校验 usedLessons > 0，避免恢复后变为负数
                if (pkg.usedLessons <= 0) {
                    return@withTransaction UndoResult(
                        success = true,
                        restoredPackageId = pkg.id,
                        restoredPackageName = pkg.name,
                        remainingAfter = pkg.remainingLessons,
                        message = "已撤销签到，课时包已用数为 0，无需恢复"
                    )
                }

                // 恢复 usedLessons - 1；若原状态为"已用完"，恢复为"活跃"
                val newUsed = pkg.usedLessons - 1
                val newStatus = if (pkg.status == "已用完") "活跃" else pkg.status
                val updated = pkg.copy(usedLessons = newUsed, status = newStatus)
                val affected = pkgDao.update(updated)

                if (affected != 1) {
                    android.util.Log.e("UndoCheckIn",
                        "课时包恢复失败：affected=$affected, pkgId=${pkg.id}")
                    // 即使课时包恢复失败，Lesson 已删除，回滚由 withTransaction 保证一致性
                    throw RuntimeException("课时包恢复未生效（affected=$affected）")
                }

                android.util.Log.i("UndoCheckIn",
                    "撤销成功：${pkg.name} used ${pkg.usedLessons}->${newUsed} 剩余${updated.remainingLessons}")

                // v30：撤销签到恢复课时不属于核心数据变更，但仍影响课时余额，触发防抖备份
                AutoBackupScheduler.notifyDataChange()

                UndoResult(
                    success = true,
                    restoredPackageId = pkg.id,
                    restoredPackageName = pkg.name,
                    remainingAfter = updated.remainingLessons,
                    message = "已撤销签到，恢复 1 节课时（${pkg.name}）"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("UndoCheckIn", "撤销签到异常：${e.message}", e)
            UndoResult(
                success = false,
                message = "撤销失败：${e.message ?: "未知异常"}"
            )
        }
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
     * 统计学员从指定日期起未消课的课时数量。
     *
     * 委托 [ScheduleQueryRepository.countUnconsumedLessonsFrom]。
     */
    suspend fun countUnconsumedLessonsFrom(studentName: String, fromDate: String): Int =
        scheduleQueryRepo.countUnconsumedLessonsFrom(studentName, fromDate)

    /**
     * 统计学员从指定日期起"长期自动未签退"的课时数量。
     *
     * 委托 [ScheduleQueryRepository.countLongTermPendingFrom]。
     */
    suspend fun countLongTermPendingFrom(studentName: String, fromDate: String): Int =
        scheduleQueryRepo.countLongTermPendingFrom(studentName, fromDate)

    /** v46：双通道统计长期自动未签退课时（studentId 优先、studentName 回退） */
    suspend fun countLongTermPendingFromDual(studentId: String?, name: String, fromDate: String): Int =
        scheduleQueryRepo.countLongTermPendingFromDual(studentId, name, fromDate)

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

    suspend fun getActivePackagesByStudent(studentName: String): List<LessonPackage> {
        // v46：双通道查询（studentId 优先、studentName 回退），杜绝改名断链
        val sid = resolveStudentId(studentName)
        return pkgDao.getByStudentDual(sid, studentName).first()
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
    suspend fun earliestPurchaseDateOf(studentName: String): String? {
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

    // === v22 冷热数据归档 ===

    /**
     * 归档结果：携带归档统计信息供 UI 反馈。
     *
     * @param success 是否成功
     * @param archivedCount 实际归档的记录数
     * @param message 用户可读消息
     */
    data class ArchiveResult(
        val success: Boolean,
        val archivedCount: Int,
        val message: String
    )

    /**
     * 将指定日期之前的课时记录从 lessons 表迁移到 archived_lessons 表（冷热归档）。
     *
     * 执行流程（单事务原子操作）：
     * 1. INSERT INTO archived_lessons SELECT ... FROM lessons WHERE date < :date
     * 2. DELETE FROM lessons WHERE date < :date
     * 3. 任意一步失败则整体回滚，保证数据不丢失
     *
     * 设计要点：
     * - 使用 SQLite 的 INSERT...SELECT 在数据库层一次性完成数据迁移，避免 Kotlin 层循环
     * - 迁移与删除在同一事务内原子完成，杜绝部分迁移导致的数据不一致
     * - archived_lessons 表字段与 lessons 完全一致，仅多一个 archivedAt 字段记录归档时间
     *
     * 使用场景：
     * - 学员详情设置入口"归档一年前记录"按钮触发
     * - 建议每年执行一次，保持主表 lessons 在合理体量（<5000 条）
     *
     * @param date 边界日期 YYYY-MM-DD（严格小于该日期的记录将被归档）
     * @return [ArchiveResult] 携带归档统计信息
     */
    suspend fun archiveLessonsBefore(date: String): ArchiveResult {
        val archiveDao = archivedLessonDao ?: return ArchiveResult(
            success = false,
            archivedCount = 0,
            message = "归档功能未初始化（archivedLessonDao 为空）"
        )
        val database = db ?: return ArchiveResult(
            success = false,
            archivedCount = 0,
            message = "归档功能未初始化（db 为空）"
        )

        return try {
            database.withTransaction {
                val archivedAt = System.currentTimeMillis()
                // 1. 迁移：将旧数据 INSERT INTO archived_lessons SELECT FROM lessons
                archiveDao.copyLessonsBeforeToDate(date, archivedAt)
                // 2. 删除：清理主表 lessons 中的旧数据
                val deleted = lessonDao.deleteBefore(date)
                android.util.Log.i("ArchiveLessons",
                    "归档完成：边界=$date 归档记录数=$deleted")

                // v30：归档属于大规模数据迁移，触发自动备份防抖
                AutoBackupScheduler.notifyDataChange()

                ArchiveResult(
                    success = true,
                    archivedCount = deleted,
                    message = "已归档 $deleted 条一年前的记录"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ArchiveLessons", "归档失败：${e.message}", e)
            ArchiveResult(
                success = false,
                archivedCount = 0,
                message = "归档失败：${e.message ?: "未知错误"}"
            )
        }
    }

    /**
     * 获取归档记录总数（用于诊断与统计）。
     */
    fun getArchivedCount(): Flow<Int> =
        archivedLessonDao?.count() ?: kotlinx.coroutines.flow.flowOf(0)

    /**
     * 按学员查询归档记录（历史报表场景）。
     */
    fun getArchivedByStudent(name: String): Flow<List<ArchivedLesson>> =
        archivedLessonDao?.getByStudent(name) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    /**
     * === v28：智能冷热数据自动迁移（App 启动时触发） ===
     *
     * 业务背景：
     * - v4 已实现手动归档入口（学员详情设置"归档一年前记录"按钮）
     * - 但教练很少主动触发，导致 lessons 表数据量持续膨胀（>2000 条后查询明显变慢）
     * - 本方法在 App 启动时自动检查，超过阈值时静默执行归档，保持主表体积可控
     *
     * 触发条件（同时满足）：
     * 1. lessons 表记录数 > [threshold]（默认 2000 条）
     * 2. 存在超过 [archiveDaysOld] 天（默认 365 天）的旧记录
     *
     * 执行流程：
     * 1. [LessonDao.countAllOnce] 一次性查询 lessons 表总数（非 Flow，避免订阅开销）
     * 2. 总数 ≤ 阈值 → 直接返回（无操作）
     * 3. 总数 > 阈值 → 计算归档边界日期（today - 365 天），调用 [archiveLessonsBefore]
     * 4. 整个迁移在事务内原子完成，失败不影响 App 启动
     *
     * 调用时机：
     * - [com.shangmentiyu.sportscoach.ui.home.HomeViewModel] init 块中调用
     * - 静默执行，无 UI 反馈（除非归档失败，通过返回值的 message 字段记录日志）
     *
     * 性能考虑：
     * - 仅一次 COUNT 查询 + 可能的一次事务，开销极低
     * - 归档操作使用 SQLite INSERT...SELECT 在数据库层完成，避免 Kotlin 层循环
     * - 即使 lessons 表 5000+ 条，归档耗时 < 500ms，不阻塞 UI
     *
     * @param threshold 触发阈值，默认 2000 条
     * @param archiveDaysOld 归档边界天数，默认 365 天
     * @return [ArchiveResult] 携带归档统计信息（未触发时 archivedCount=0）
     */
    suspend fun maybeAutoArchiveIfNeeded(
        threshold: Int = 2000,
        archiveDaysOld: Long = 365L
    ): ArchiveResult {
        val archiveDao = archivedLessonDao ?: return ArchiveResult(
            success = false, archivedCount = 0,
            message = "归档功能未初始化（archivedLessonDao 为空）"
        )
        return try {
            // 1. 一次性查询主表总数
            val totalCount = lessonDao.countAllOnce()
            if (totalCount <= threshold) {
                // 未超过阈值，无需归档
                return ArchiveResult(
                    success = true, archivedCount = 0,
                    message = "未触发自动归档（$totalCount ≤ $threshold）"
                )
            }

            // 2. 计算归档边界日期（today - 365 天）
            val today = LocalDate.now()
            val boundary = today.minusDays(archiveDaysOld)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))

            android.util.Log.i("AutoArchive",
                "触发自动归档：lessons 表共 $totalCount 条 > 阈值 $threshold，边界日期=$boundary")

            // 3. 执行归档（事务原子操作）
            val result = archiveLessonsBefore(boundary)
            android.util.Log.i("AutoArchive",
                "自动归档完成：${result.message}（lessons 表剩余 ${totalCount - result.archivedCount} 条）")
            result
        } catch (e: Exception) {
            android.util.Log.e("AutoArchive", "自动归档失败：${e.message}", e)
            ArchiveResult(
                success = false, archivedCount = 0,
                message = "自动归档失败：${e.message ?: "未知错误"}"
            )
        }
    }

    /**
     * === v28：一次性获取全部归档记录（非 Flow，用于"查看全部历史归档"列表） ===
     *
     * UI 调用时机：教练在课时管理 Tab 点击"查看全部历史归档"按钮后调用。
     * 默认所有 LazyColumn 列表查询只查 lessons 表（热数据），
     * 仅在用户主动点击时才查询 archived_lessons 表（冷数据），保持日常列表流畅。
     *
     * @return 全部归档记录列表（按日期降序、时间降序）
     */
    suspend fun getAllArchivedOnce(): List<ArchivedLesson> {
        return archivedLessonDao?.getAllOnce() ?: emptyList()
    }

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
     * - 由 [com.shangmentiyu.sportscoach.core.TrainingContentRecommender] 调用
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
            val scores = com.shangmentiyu.sportscoach.core.AbilityAnalyzer.extractScores(lessons)
            // 3. 调用推荐器生成训练内容
            com.shangmentiyu.sportscoach.core.TrainingContentRecommender.recommend(
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
