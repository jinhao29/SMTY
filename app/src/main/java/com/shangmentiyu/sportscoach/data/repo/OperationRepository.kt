package com.shangmentiyu.sportscoach.data.repo

import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.core.AutoBackupScheduler
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.db.ArchivedLessonDao
import com.shangmentiyu.sportscoach.data.db.CoachDao
import com.shangmentiyu.sportscoach.data.db.LessonDao
import com.shangmentiyu.sportscoach.data.db.LessonPackageDao
import com.shangmentiyu.sportscoach.data.db.ScheduleDao
import com.shangmentiyu.sportscoach.data.db.TrainingCycleDao
import com.shangmentiyu.sportscoach.data.model.ArchivedLesson
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.model.TrainingCycle
import com.shangmentiyu.sportscoach.data.model.WeeklyPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 运营管理 Repository（管理层）。
 *
 * 统一封装 LessonPackage / Coach / Schedule / TrainingCycle 四类实体的数据访问，
 * 对上层提供业务语义清晰的方法，并包含阶段性总结的聚合计算。
 *
 * v22 新增：冷热数据归档能力 [archiveLessonsBefore]，依赖 [db] 与 [archivedLessonDao]。
 */
class OperationRepository(
    private val pkgDao: LessonPackageDao,
    private val coachDao: CoachDao,
    private val scheduleDao: ScheduleDao,
    private val cycleDao: TrainingCycleDao,
    private val lessonDao: LessonDao,
    /** v22 新增：归档 DAO，冷热数据归档时使用 */
    private val archivedLessonDao: ArchivedLessonDao? = null,
    /** v22 新增：数据库实例，用于归档事务 */
    private val db: AppDatabase? = null
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
     * 与 [consumeLesson] 的区别：
     * - 旧逻辑 [consumeLesson]：签到时直接扣减，签到成功即视为消课完成
     * - 新逻辑 [consumeLessonForCheckOut]：签到时仅创建 status="已签到" 的 Lesson，不扣减课时包；
     *   签退时（教练保存课后反馈时）才执行本方法，扣减课时包并更新 Lesson.status="已签退"
     *
     * 执行流程（@Transaction 原子操作）：
     * 1. 调用 [consumeLesson] 找到最早购买的活跃课时包并 usedLessons + 1
     * 2. 更新 Lesson：status="已签退"，signOutTime=当前时间，packageId=扣减的课时包ID
     * 3. 任一步失败整体回滚
     *
     * 并发保护：复用 [consumeMutex]，与 [consumeLesson] / [undoCheckIn] 共享锁，
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
                // 1. 调用核心消课逻辑扣减课时包
                val consume = doConsumeLessonInternal(lesson.studentName)
                if (!consume.success) {
                    // 抛异常触发事务回滚，Lesson 状态保持"已签到"
                    throw RuntimeException("课时包扣减失败：${consume.message}")
                }

                // 2. 更新 Lesson：status="已签退" + signOutTime + packageId
                val nowTime = java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                )
                val updatedLesson = lesson.copy(
                    status = "已签退",
                    signOutTime = nowTime,
                    packageId = consume.packageId
                )
                val affected = lessonDao.update(updatedLesson)
                if (affected != 1) {
                    throw RuntimeException("Lesson 更新未生效（affected=$affected）")
                }

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
     * 与 [consumeLesson] 的区别：本方法不获取 [consumeMutex]（已由外层调用方持有），
     * 避免重入死锁。直接执行读 + 写 + 校验三步。
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
     * 消耗一次课时：找到该学员最早购买且仍有余额的活跃课程包，usedLessons + 1。
     *
     * 使用 [consumeMutex] 互斥锁保护读 + 写临界区，防止并发签到时
     * 多个协程同时读到相同余额并各自扣减，导致同一课时被扣多次或扣错包。
     *
     * 写库后通过 [pkgDao.update] 返回的受影响行数 + 重新查询双重校验，
     * 确保扣减真正落库；任一校验失败均返回 success=false，避免"签到成功但课时未减"。
     *
     * v27 起本方法仅供旧路径调用；新签退流程请使用 [consumeLessonForCheckOut]。
     *
     * @return ConsumeResult.success=true 表示成功扣减；false 表示无可用课时包或扣减失败
     */
    suspend fun consumeLesson(studentName: String): ConsumeResult = consumeMutex.withLock {
        val packages = pkgDao.getByStudent(studentName).first()
        android.util.Log.d("ConsumeLesson",
            "学员=$studentName 查询到课时包${packages.size}个: ${packages.map { "${it.name}(status=${it.status},used=${it.usedLessons}/${it.totalLessons},expire=${it.expireDate})" }}")
        val active = packages.filter { it.status == "活跃" && !it.isExhausted && !it.isExpired }
        android.util.Log.d("ConsumeLesson", "过滤后活跃包${active.size}个")
        val target = active.minByOrNull { it.purchaseDate }
            ?: return@withLock ConsumeResult(success = false, message = "无可用课时包")

        val newUsed = (target.usedLessons + 1).coerceAtMost(target.totalLessons)
        val updated = if (newUsed >= target.totalLessons) {
            target.copy(usedLessons = target.totalLessons, status = "已用完")
        } else {
            target.copy(usedLessons = newUsed)
        }
        val affected = pkgDao.update(updated)

        // 校验1：受影响行数必须为1，否则 update 未生效
        if (affected != 1) {
            android.util.Log.e("ConsumeLesson",
                "update 受影响行数=$affected（预期1），扣减未落库！target.id=${target.id}")
            return@withLock ConsumeResult(
                success = false,
                message = "课时扣减失败（更新未生效）"
            )
        }

        // 校验2：重新查询验证 usedLessons 已更新
        val recheck = pkgDao.getById(target.id)
        if (recheck == null || recheck.usedLessons != updated.usedLessons) {
            android.util.Log.e("ConsumeLesson",
                "re-query 校验失败：期望used=${updated.usedLessons}，实际used=${recheck?.usedLessons}")
            return@withLock ConsumeResult(
                success = false,
                message = "课时扣减失败（校验不一致）"
            )
        }

        android.util.Log.d("ConsumeLesson",
            "扣减成功：${target.name} used ${target.usedLessons}->${updated.usedLessons} 剩余${updated.remainingLessons}")
        ConsumeResult(
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
     * - 使用 [consumeMutex] 互斥锁保护读 + 写临界区，与 [consumeLesson] 共享锁，
     *   避免"撤销"与"签到"并发执行时出现余额计算错乱
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

    // === 排课 ===
    fun getActiveSchedules(): Flow<List<Schedule>> = scheduleDao.getActive()
    fun getAllSchedules(): Flow<List<Schedule>> = scheduleDao.getAll()
    fun getSchedulesByStudent(name: String): Flow<List<Schedule>> = scheduleDao.getByStudent(name)
    fun getSchedulesByCoach(name: String): Flow<List<Schedule>> = scheduleDao.getByCoach(name)
    fun getSchedulesByDay(dayOfWeek: Int): Flow<List<Schedule>> = scheduleDao.getByDay(dayOfWeek)
    suspend fun getScheduleById(id: String): Schedule? = scheduleDao.getById(id)

    /** v30：新增排课属于核心数据变更，触发自动备份防抖 */
    suspend fun addSchedule(schedule: Schedule) {
        scheduleDao.insert(schedule)
        AutoBackupScheduler.notifyDataChange()
    }

    /** v30：更新排课属于核心数据变更，触发自动备份防抖 */
    suspend fun updateSchedule(schedule: Schedule) {
        scheduleDao.update(schedule)
        AutoBackupScheduler.notifyDataChange()
    }

    /** v30：删除排课属于核心数据变更，触发自动备份防抖 */
    suspend fun deleteSchedule(id: String) {
        scheduleDao.deleteById(id)
        AutoBackupScheduler.notifyDataChange()
    }

    /** 清空所有排课记录（课表管理"清空全部"功能） */
    suspend fun deleteAllSchedules() {
        scheduleDao.deleteAll()
        AutoBackupScheduler.notifyDataChange()
    }

    /**
     * === v27：查询学员在指定日期的排课（用于课后反馈自动填充） ===
     *
     * 排课按星期几（dayOfWeek）排期，无具体日期。
     * 本方法将日期字符串转为周几后调用 [ScheduleDao.getByDay]，再过滤学员。
     *
     * 使用场景：教练在"课后反馈"选中学员后，自动调用本方法查询该学员今日的排课，
     * 将 Schedule.startTime / durationMinutes / location 预填充到反馈表单输入框。
     *
     * @param studentName 学员姓名
     * @param dateStr 日期 YYYY-MM-DD
     * @return 该学员在该日期对应周几的活跃排课列表（按开始时间升序），无则空列表
     */
    suspend fun getTodayScheduleForStudent(
        studentName: String,
        dateStr: String
    ): List<Schedule> {
        return try {
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))
            // Calendar.DAY_OFOfWeek: 周日=1, 周六=7 → 转为 周一=1, 周日=7
            val calendarDayOfWeek = date.dayOfWeek.value  // 1=Monday, 7=Sunday
            val all = scheduleDao.getAllOnce()
            all.filter { it.isActive && it.studentName == studentName && it.dayOfWeek == calendarDayOfWeek }
                .sortedBy { it.startTime }
        } catch (e: Exception) {
            android.util.Log.e("OpRepo", "查询今日排课失败：${e.message}", e)
            emptyList()
        }
    }

    // === 长期排课：自动生成本周课时记录 ===

    /**
     * 查重：指定学员+日期+时间是否已有课时记录。
     * 长期排课自动生成时调用，避免重复插入。
     */
    suspend fun hasLessonForScheduleOnDate(studentName: String, date: String, time: String): Boolean {
        return lessonDao.countByStudentDateTime(studentName, date, time) > 0
    }

    /**
     * 检查学员是否还能排课：剩余课时包余额 > 未来未消课课时数时才允许继续排课。
     *
     * 长期排课生成时调用，确保排课精确到最后一节课，避免超额排课。
     *
     * @param studentName 学员姓名
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     * @return true=仍有余额可排课；false=余额已用完
     */
    suspend fun canScheduleMoreLessons(studentName: String, fromDate: String): Boolean {
        val summary = getRemainingSummary(studentName)
        val pendingCount = lessonDao.countUnconsumedFrom(studentName, fromDate)
        return summary.totalRemaining > pendingCount
    }

    /**
     * 统计学员从指定日期起未消课的课时数量。
     * 用于长期排课生成时计算可用额度 = 课时包余额 - 未消课课时数。
     *
     * @param studentName 学员姓名
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     * @return 未消课的课时数量
     */
    suspend fun countUnconsumedLessonsFrom(studentName: String, fromDate: String): Int {
        return lessonDao.countUnconsumedFrom(studentName, fromDate)
    }

    /**
     * === v27：统计学员从指定日期起"长期自动未签退"的课时数量 ===
     *
     * 用户需求："签退后消耗课时"重构后，余额计算应只统计：
     * - status="已签退"（已消耗课时包）
     * 或保留原 [countUnconsumedLessonsFrom] 逻辑，但需确保"已签到未签退"的课时
     * 不被错误计入"未消费"。
     *
     * 本方法只统计长期自动生成的、尚未签退的课时（status != "已签退" 且 lessonType 含"(长期自动)"）。
     *
     * 用于 [com.shangmentiyu.sportscoach.ui.operation.OperationViewModel.ensureLongTermLessonsForWeek]
     * 计算可用余额 = 有效课时包剩余 - 长期自动未签退课时数。
     *
     * @param studentName 学员姓名
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     * @return 长期自动生成且未签退的课时数量
     */
    suspend fun countLongTermPendingFrom(studentName: String, fromDate: String): Int {
        return lessonDao.countLongTermPendingFrom(studentName, fromDate)
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
        return getActivePackagesByStudent(studentName)
            .filter { pkg ->
                pkg.purchaseDate <= dateStr &&
                    (pkg.expireDate.isBlank() || pkg.expireDate >= dateStr)
            }
            .sumOf { it.remainingLessons }
    }

    /**
     * 根据长期排课 Schedule 生成一条课时记录（Lesson）。
     *
     * 约定：
     * - 自动生成的 Lesson 用 lessonType 标记为 "长期自动"
     * - attendance = "准时"，但 packageId = "" 表示未扣减课时
     * - content / contentImages 直接从 Schedule 复制，便于上课时引用
     * - 学员可在课后反馈中编辑此 Lesson，结算时再扣减课时
     *
     * @param sched 长期排课实体
     * @param dateStr 本周对应日期 YYYY-MM-DD
     */
    suspend fun generateLongTermLesson(sched: Schedule, dateStr: String) {
        val lesson = Lesson(
            id = java.util.UUID.randomUUID().toString().take(8),
            date = dateStr,
            time = sched.startTime,
            studentName = sched.studentName,
            content = sched.content,
            duration = sched.durationMinutes,
            coach = sched.coachName,
            location = sched.location,
            lessonType = "${sched.lessonType}(长期自动)",
            attendance = "准时",
            packageId = ""
        )
        lessonDao.insert(lesson)
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
    suspend fun getLessonsByStudentOnce(studentName: String): List<Lesson> =
        lessonDao.getByStudent(studentName).first().sortedBy { "${it.date} ${it.time}" }

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

    // === 训练周期 ===
    fun getAllCycles(): Flow<List<TrainingCycle>> = cycleDao.getAll()
    fun getActiveCycles(): Flow<List<TrainingCycle>> = cycleDao.getActive()
    fun getCyclesByStudent(name: String): Flow<List<TrainingCycle>> = cycleDao.getByStudent(name)
    suspend fun getCycleById(id: String): TrainingCycle? = cycleDao.getById(id)
    suspend fun addCycle(cycle: TrainingCycle) = cycleDao.insert(cycle)
    suspend fun updateCycle(cycle: TrainingCycle) = cycleDao.update(cycle)
    suspend fun deleteCycle(id: String) = cycleDao.deleteById(id)

    /**
     * 创建周期并自动生成空的周计划列表。
     */
    suspend fun createCycle(
        studentName: String,
        name: String,
        goal: String,
        totalWeeks: Int,
        startDate: String
    ): String {
        val cycle = TrainingCycle(
            studentName = studentName,
            name = name,
            goal = goal,
            totalWeeks = totalWeeks,
            startDate = startDate,
            endDate = calcEndDate(startDate, totalWeeks)
        ).withWeeklyPlans(emptyWeeklyPlans(totalWeeks))
        cycleDao.insert(cycle)
        return cycle.id
    }

    /** 生成空的周计划列表 */
    private fun emptyWeeklyPlans(totalWeeks: Int): List<WeeklyPlan> =
        (1..totalWeeks).map { i ->
            WeeklyPlan(
                weekIndex = i,
                title = "第$i 周",
                goal = "",
                focus = "",
                exercisesJson = "[]"
            )
        }

    /** 计算周期结束日期（线程安全：基于 [LocalDate] 不可变对象，无 Calendar 状态污染） */
    private fun calcEndDate(startDate: String, weeks: Int): String {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
            val start = LocalDate.parse(startDate, formatter)
            // +weeks 周 -1 天 = 周期最后一天
            val end = start.plusWeeks(weeks.toLong()).minusDays(1)
            end.format(formatter)
        } catch (_: Exception) { "" }
    }

    // === 阶段性总结 ===

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

    /**
     * 计算学员的阶段总结。
     * @param studentName 学员姓名
     * @param startDate 起始日期 YYYY-MM-DD（含）
     * @param endDate 结束日期 YYYY-MM-DD（含）
     */
    suspend fun computeStageSummary(
        studentName: String,
        startDate: String,
        endDate: String,
        allLessons: List<Lesson>
    ): StageSummary {
        val inRange = allLessons.filter { l ->
            l.studentName == studentName && l.date in startDate..endDate
        }.sortedBy { it.date }

        val total = inRange.size
        val attended = inRange.count { it.attendance !in listOf("请假", "旷课") }
        val attendanceRate = if (total > 0) attended.toFloat() / total else 0f
        val avgPerf = if (total > 0) inRange.map { it.performance }.average().toFloat() else 0f
        val avgDur = if (total > 0) inRange.map { it.duration }.average().toInt() else 0
        val attitudeDist = inRange.groupingBy { it.attitude }.eachCount()
        val allExercises = inRange.flatMap { parseExercisesForStats(it.content) }
        val doneEx = allExercises.count { it.first }
        val totalEx = allExercises.size
        val completionRate = if (totalEx > 0) doneEx.toFloat() / totalEx else 0f

        // 成绩进步对比（首末对比）
        val scoreProgress = computeScoreProgress(inRange)

        val firstDate = inRange.firstOrNull()?.date ?: ""
        val lastDate = inRange.lastOrNull()?.date ?: ""
        val summaryText = buildSummaryText(
            studentName, startDate, endDate, total, attended, attendanceRate,
            avgPerf, avgDur, completionRate, scoreProgress
        )

        return StageSummary(
            studentName = studentName,
            startDate = startDate,
            endDate = endDate,
            totalLessons = total,
            attendedLessons = attended,
            attendanceRate = attendanceRate,
            avgPerformance = avgPerf,
            avgDuration = avgDur,
            attitudeDistribution = attitudeDist,
            completedExerciseRate = completionRate,
            scoreProgress = scoreProgress,
            firstLessonDate = firstDate,
            lastLessonDate = lastDate,
            summaryText = summaryText
        )
    }

    /** 解析课时训练内容为 (done, name) 列表 */
    private fun parseExercisesForStats(json: String): List<Pair<Boolean, String>> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                obj.optBoolean("done", false) to obj.optString("name")
            }
        } catch (_: Exception) { emptyList() }
    }

    /** 计算各项成绩的首末对比 */
    private fun computeScoreProgress(lessons: List<Lesson>): List<ScoreProgressItem> {
        val scoreMap = mutableMapOf<String, MutableList<Float>>()
        for (l in lessons) {
            if (l.scores.isBlank() || l.scores == "{}") continue
            try {
                val obj = JSONObject(l.scores)
                obj.keys().forEach { key ->
                    val score = obj.optJSONObject(key)?.optDouble("score", 0.0) ?: 0.0
                    scoreMap.getOrPut(key) { mutableListOf() }.add(score.toFloat())
                }
            } catch (_: Exception) { }
        }
        return scoreMap.map { (name, scores) ->
            val first = scores.firstOrNull() ?: 0f
            val last = scores.lastOrNull() ?: 0f
            ScoreProgressItem(name, first, last, last - first, scores.size)
        }.sortedByDescending { it.samples }
    }

    /** 生成阶段性总结文字 */
    private fun buildSummaryText(
        studentName: String, startDate: String, endDate: String,
        total: Int, attended: Int, attendanceRate: Float,
        avgPerf: Float, avgDur: Int, completionRate: Float,
        scoreProgress: List<ScoreProgressItem>
    ): String {
        val sb = StringBuilder()
        sb.append("【$studentName 阶段总结 $startDate ~ $endDate】\n\n")
        sb.append("本阶段共安排 $total 节课，实到 $attended 节，")
        sb.append("出勤率 ${"%.0f".format(attendanceRate * 100)}%。\n")
        sb.append("平均课时时长 ${avgDur} 分钟，整体表现评分 ${"%.1f".format(avgPerf)}/10，")
        sb.append("训练动作完成率 ${"%.0f".format(completionRate * 100)}%。\n\n")
        if (scoreProgress.isNotEmpty()) {
            sb.append("成绩进步：\n")
            for (p in scoreProgress) {
                val arrow = if (p.delta > 0) "↑" else if (p.delta < 0) "↓" else "→"
                sb.append("· ${p.name}: ${"%.1f".format(p.firstScore)} → ${"%.1f".format(p.lastScore)} $arrow ${"%.1f".format(Math.abs(p.delta))}\n")
            }
        }
        return sb.toString()
    }
}
