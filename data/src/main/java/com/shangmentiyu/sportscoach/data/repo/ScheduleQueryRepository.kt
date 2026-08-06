package com.shangmentiyu.sportscoach.data.repo

import android.util.Log
import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.core.AutoBackupScheduler
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.db.LessonDao
import com.shangmentiyu.sportscoach.data.db.LessonPackageDao
import com.shangmentiyu.sportscoach.data.db.ScheduleDao
import com.shangmentiyu.sportscoach.data.db.StudentDao
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.domain.scheduling.EffectiveRemainingCalculator
import com.shangmentiyu.sportscoach.domain.scheduling.ScheduleValidationSource
import com.shangmentiyu.sportscoach.domain.scheduling.ValidateScheduleUseCase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 排课查询/操作 Repository（管理层）。
 *
 * 从原 [OperationRepository] 拆分而来，承载排课域的写操作与长期排课逻辑：
 * - 排课事务写入（[addSchedule] / [updateSchedule] / [saveSchedule] / [deleteSchedule] / [deleteAllSchedules]）
 * - 今日排课查询（[getTodayScheduleForStudent]）
 * - 长期排课生成额度计算（[hasLessonForScheduleOnDate] / [canScheduleMoreLessons] /
 *   [countUnconsumedLessonsFrom] / [countLongTermPendingFrom] / [generateLongTermLesson]）
 * - 历史错误排课一键修正（[fixHistoricalScheduleErrors]）
 *
 * 说明：
 * - 跨域辅助方法（[resolveStudentId] / [earliestPurchaseDateOf] / [getEffectiveRemainingLessons] 等）
 *   为私有副本，与 [OperationRepository] 中的实现保持一致（避免子仓库反向依赖协调器造成循环依赖）。
 */
class ScheduleQueryRepository(
    private val scheduleDao: ScheduleDao,
    private val lessonDao: LessonDao,
    private val pkgDao: LessonPackageDao,
    private val studentDao: StudentDao,
    private val db: AppDatabase?
) : ScheduleValidationSource {

    /** 集中校验：手动保存 / 长期生成统一走 ValidateScheduleUseCase */
    private val validate = ValidateScheduleUseCase(this)

    private fun todayStr(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))

    /**
     * === v45：一键修正历史错误排课 ===
     *
     * 修正结果：携带清理/重排统计供 UI 反馈。
     */
    data class ScheduleFixResult(
        val fixedStudents: Int = 0,
        val deletedSchedules: Int = 0,
        val deletedPlaceholders: Int = 0,
        val regeneratedLessons: Int = 0
    )

    /**
     * v30：新增排课属于核心数据变更，触发自动备份防抖
     *
     * v38 修复：用 [db.withTransaction] 包裹写入，确保主键冲突 / 约束违反 / JSON 序列化异常
     * 时 [return@withTransaction] 能正确返回 false 失败状态，避免黑盒吞掉错误。
     *
     * 事务边界说明：
     * - 当前仅 [scheduleDao.insert] 一条写入操作；若失败则数据未落库，return false 即可。
     * - schedule_memory 表的写入由 ViewModel 层 [com.shangmentiyu.sportscoach.ui.operation.OperationViewModel]
     *   通过 memoryRepo 独立完成。若未来需要将 schedules + schedule_memory 纳入同一事务，
     *   应在本方法事务块内扩展 memoryDao 写入，并在失败分支 throw 异常以触发整体回滚。
     *
     * @return true 表示写入成功；false 表示事务内出现异常（schedule 未落库）
     */
    suspend fun addSchedule(schedule: Schedule): Boolean {
        val database = db ?: run {
            // db 未注入（单元测试或旧路径）：降级到非事务写入，仍捕获异常返回状态
            return try {
                scheduleDao.insert(schedule)
                AutoBackupScheduler.notifyDataChange()
                true
            } catch (e: Exception) {
                android.util.Log.e("OperationRepo",
                    "addSchedule（无事务降级）失败：${e.message}", e)
                false
            }
        }
        val success: Boolean = try {
            database.withTransaction {
                try {
                    scheduleDao.insert(schedule)
                    true
                } catch (e: Exception) {
                    // 主键冲突 / 约束违反 / 序列化异常等
                    // 由于 insert 失败时无数据被修改，return false 即可（无需 throw 触发回滚）
                    android.util.Log.e("OperationRepo",
                        "addSchedule 事务内异常：${e.message}", e)
                    return@withTransaction false
                }
            }
        } catch (e: Exception) {
            // withTransaction 自身异常（如死锁、磁盘满）
            android.util.Log.e("OperationRepo",
                "addSchedule 事务失败：${e.message}", e)
            false
        }
        if (success) AutoBackupScheduler.notifyDataChange()
        return success
    }

    /**
     * v30：更新排课属于核心数据变更，触发自动备份防抖
     *
     * v38 修复：用 [db.withTransaction] 包裹写入，确保主键冲突 / 约束违反 / JSON 序列化异常
     * 时 [return@withTransaction] 能正确返回 false 失败状态，避免黑盒吞掉错误。
     *
     * @return true 表示更新成功；false 表示事务内出现异常（schedule 未变更）
     */
    suspend fun updateSchedule(schedule: Schedule): Boolean {
        val database = db ?: run {
            // db 未注入（单元测试或旧路径）：降级到非事务写入，仍捕获异常返回状态
            return try {
                scheduleDao.update(schedule)
                AutoBackupScheduler.notifyDataChange()
                true
            } catch (e: Exception) {
                android.util.Log.e("OperationRepo",
                    "updateSchedule（无事务降级）失败：${e.message}", e)
                false
            }
        }
        val success: Boolean = try {
            database.withTransaction {
                try {
                    scheduleDao.update(schedule)
                    true
                } catch (e: Exception) {
                    android.util.Log.e("OperationRepo",
                        "updateSchedule 事务内异常：${e.message}", e)
                    return@withTransaction false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OperationRepo",
                "updateSchedule 事务失败：${e.message}", e)
            false
        }
        if (success) AutoBackupScheduler.notifyDataChange()
        return success
    }

    /** v30：删除排课属于核心数据变更，触发自动备份防抖 */
    suspend fun deleteSchedule(id: String) {
        scheduleDao.deleteById(id)
        AutoBackupScheduler.notifyDataChange()
    }

    /**
     * === v33 数据流加固（模块 2 核心）：排课保存防重与异常抛出 ===
     *
     * 解决用户痛点："排课修改保存失败但没有报错"。
     *
     * 现有 [addSchedule] / [updateSchedule] 已分别处理新增 / 更新场景，
     * 但调用方需先判断 schedule.id 是否存在再决定调用哪个方法。
     * 如果调用方误判（如编辑模式 id 丢失走 [addSchedule]），
     * 会触发主键冲突被静默吞掉，用户感知为"保存失败无报错"。
     *
     * 本方法封装"智能判断 + 异常归集"逻辑：
     * 1. 通过 [scheduleDao.getById] 查询是否已存在同 id 的排课
     * 2. 存在 → [scheduleDao.update]；不存在 → [scheduleDao.insert]
     * 3. 整个流程包裹 try-catch，所有异常通过 [Log.e] 输出到 Logcat（tag=DataFlow）
     * 4. 返回 true=保存成功；false=保存失败（调用方可读 Logcat 排查）
     *
     * 与 [addSchedule] / [updateSchedule] 的区别：
     * - 自动判断新增/更新，调用方无需关心 schedule.id 是否为空
     * - 失败时返回 false 而非吞掉异常，调用方据此向用户 Toast 提示
     * - 异常全部通过 [Log.e]（tag=DataFlow）输出，便于 Logcat 过滤定位
     *
     * 事务边界说明：
     * - 包裹在 [db.withTransaction] 内，确保查询+写入原子完成
     * - 若 db 未注入（单元测试/旧路径），降级为非事务模式但仍捕获异常
     *
     * @param schedule 待保存的排课对象（id 必须正确传递，编辑模式由 UI 层填入原 id）
     * @return true=保存成功；false=保存失败（异常已记录到 Logcat）
     */
    suspend fun saveSchedule(schedule: Schedule): Boolean {
        // === Bug 1 修复：排课生效日期不得早于学员首次购买课时包日期 ===
        // 校验放在事务与写入之前，异常向上抛给 ViewModel 层转为用户提示，
        // 避免被下方 try-catch 吞掉（否则 UI 只会收到笼统的"保存失败"）。
        if (schedule.startDate.isNotBlank() &&
            !validate.isDateValid(schedule.studentName, schedule.startDate)
        ) {
            throw IllegalArgumentException("无法排课：所选日期早于该学员首次购买课时包的日期")
        }
        val database = db
        return try {
            if (database != null) {
                // 有事务支持：查询+写入原子完成，避免并发保存期间出现"判断时不存在、写入时已存在"的竞态
                database.withTransaction {
                    val existing = scheduleDao.getById(schedule.id)
                    if (existing != null) {
                        // 已存在 → 更新
                        val affected = scheduleDao.update(schedule)
                        if (affected == 0) {
                            // update 返回 0 表示记录在事务内被并发删除，回滚并返回 false
                            throw IllegalStateException(
                                "update 未生效（affected=0），schedule.id=${schedule.id} 可能已被删除"
                            )
                        }
                        Log.i("DataFlow",
                            "saveSchedule 更新成功：id=${schedule.id}, student=${schedule.studentName}")
                    } else {
                        // 不存在 → 新增。集中校验（步骤一）：新增前强制额度校验，
                        // 额度已满直接拦截，绝不落库
                        if (!validate.hasRemainingCapacity(
                                schedule.studentName,
                                schedule.startDate.ifBlank { todayStr() }
                            )
                        ) {
                            throw IllegalStateException("无法排课：该学员课时额度已满")
                        }
                        scheduleDao.insert(schedule)
                        Log.i("DataFlow",
                            "saveSchedule 新增成功：id=${schedule.id}, student=${schedule.studentName}")
                    }
                }
                } else {
                    // 降级：无事务支持，仍执行智能判断 + 集中校验
                    val existing = scheduleDao.getById(schedule.id)
                    if (existing != null) {
                        scheduleDao.update(schedule)
                        Log.i("DataFlow",
                            "saveSchedule 更新成功（无事务降级）：id=${schedule.id}")
                    } else {
                        if (!validate.hasRemainingCapacity(
                                schedule.studentName,
                                schedule.startDate.ifBlank { todayStr() }
                            )
                        ) {
                            throw IllegalStateException("无法排课：该学员课时额度已满")
                        }
                        scheduleDao.insert(schedule)
                        Log.i("DataFlow",
                            "saveSchedule 新增成功（无事务降级）：id=${schedule.id}")
                    }
                }
            // 保存成功后触发自动备份
            AutoBackupScheduler.notifyDataChange()
            true
        } catch (e: Exception) {
            // 所有异常通过 Log.e 输出到 Logcat，tag=DataFlow，便于过滤定位
            // 常见异常：
            // - SQLitePrimaryKeyConstraintException：主键冲突（insert 时 id 已存在）
            // - SQLiteConstraintException：外键/唯一约束违反
            // - IllegalStateException：update 未生效（affected=0）
            // - JSONException：content 字段 JSON 序列化异常（由 Converters 抛出）
            Log.e("DataFlow",
                "保存失败：schedule.id=${schedule.id}, student=${schedule.studentName}, " +
                    "dayOfWeek=${schedule.dayOfWeek}, startTime=${schedule.startTime}, " +
                    "contentLen=${schedule.content.length}, ${e.message}", e)
            false
        }
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
        // v46：双通道查重（studentId 优先、studentName 回退，兼容旧数据）
        val sid = resolveStudentId(studentName)
        return lessonDao.countByStudentDateTimeDual(sid, studentName, date, time) > 0
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
    suspend fun canScheduleMoreLessons(studentName: String, fromDate: String): Boolean =
        // 集中校验：与手动保存 / 长期生成统一走 ValidateScheduleUseCase，
        // 消除原先分散在仓库内的余额计算歧义（getRemainingSummary + lessonDao.countUnconsumedFrom）
        validate.hasRemainingCapacity(studentName, fromDate)

    /**
     * 统计学员从指定日期起未消课的课时数量。
     * 用于长期排课生成时计算可用额度 = 课时包余额 - 未消课课时数。
     *
     * @param studentName 学员姓名
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     * @return 未消课的课时数量
     */
    override suspend fun countUnconsumedLessonsFrom(studentName: String, fromDate: String): Int {
        // v46：双通道统计（studentId 优先、studentName 回退，兼容旧数据）
        val sid = resolveStudentId(studentName)
        return lessonDao.countUnconsumedFromDual(sid, studentName, fromDate)
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
    override suspend fun countLongTermPendingFrom(studentName: String, fromDate: String): Int {
        return lessonDao.countLongTermPendingFrom(studentName, fromDate)
    }

    /** v46：双通道统计长期自动未签退课时（studentId 优先、studentName 回退） */
    suspend fun countLongTermPendingFromDual(studentId: String?, name: String, fromDate: String): Int {
        return lessonDao.countLongTermPendingFromDual(studentId, name, fromDate)
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
        // === Bug 1 修复：生成日 / 生效日不得早于学员首次购买课时包日期 ===
        // 兜底校验：正常路径下 ensureLongTermLessonsForWeek 已按 startDate /
        // 有效课时包过滤，此处为数据边界防御，违规直接抛异常阻止写入。
        if (!validate.isDateValid(sched.studentName, dateStr) ||
            (sched.startDate.isNotBlank() && !validate.isDateValid(sched.studentName, sched.startDate))
        ) {
            throw IllegalArgumentException("无法排课：所选日期早于该学员首次购买课时包的日期")
        }
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

    /**
     * === v45：一键修正历史错误排课（设置页入口，全量清理 + 重排）===
     *
     * 背景：历史版本排课 Bug 已修复，但数据库中已积压大量错误排课：
     * - 早于学员首次购买日期的长期排课模板（schedules.startDate < 最早 purchaseDate）
     * - 超过课时包剩余额度的未来占位课时（lessons 占位数量 > 有效剩余）
     *
     * 执行流程（db 可用时整体包裹在单事务内，任一学员失败整体回滚）：
     * A. 遍历所有活跃学员（students.isActive = 1）
     * B. 双通道 SQL 清理：
     *    通道1：DELETE 该学员所有 startDate < 首次购买日的无效排课
     *    通道2：DELETE 超出课时包总额的多余长排（仅保留按 startDate 升序的前 totalQuota 条）
     * C. 按当天有效课时包计算未来最大可排节数（[getEffectiveRemainingLessons]），
     *    未来占位课时（长期自动 + 未签退 + date >= today）超过额度时删除多余占位，优先删除日期靠后的
     * D. 清理后在剩余额度内自动重新生成未来两周排课（[regenerateLessonsForStudent]，
     *    等价于 ensureLongTermLessonsForWeek 的按周生成），用户可立即看到正确结果
     *
     * 绝对安全边界：
     * - 仅删除 schedules 表的错误长期排课模板；仅删除 lessons 表中
     *   "未来（date >= today）+ 长期自动 + 未签退（status != 已签退）"的未占用占位符
     * - 已签退、已实际发生的课时记录与非长期排课一律保留，绝不影响历史数据
     *
     * @return [ScheduleFixResult] 清理/重排统计
     */
    suspend fun fixHistoricalScheduleErrors(): ScheduleFixResult {
        var fixedStudents = 0
        var deletedSchedules = 0
        var deletedPlaceholders = 0
        var regeneratedLessons = 0

        val run: suspend () -> Unit = {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
            val todayStr = LocalDate.now().format(formatter)
            val activeStudents = studentDao.getAll().first()
            for (student in activeStudents) {
                val name = student.name
                val sid = student.studentId
                val earliestPurchase = earliestPurchaseDateOf(name)
                val totalRemaining = getEffectiveRemainingLessons(name, todayStr)
                var touched = false

                // 占位课时超额清理：仅"长期自动 + 未签退 + date >= today"，优先删除日期靠后的
                val placeholders = getLessonsByStudentOnce(name)
                    .filter {
                        it.date >= todayStr &&
                            it.lessonType.contains("(长期自动)") &&
                            it.status != "已签退"
                    }
                    .sortedBy { it.date }
                if (placeholders.isNotEmpty()) {
                    val excess = placeholders.size - totalRemaining
                    if (excess > 0) {
                        placeholders.takeLast(excess).forEach {
                            lessonDao.deleteById(it.id)
                            deletedPlaceholders++
                            touched = true
                        }
                    }
                }

                // 通道1：删除所有 startDate 早于首次购买日期的无效排课
                if (earliestPurchase != null) {
                    val c1 = scheduleDao.deleteSchedulesBeforeDateDual(sid, name, earliestPurchase)
                    if (c1 > 0) {
                        deletedSchedules += c1
                        touched = true
                    }
                }

                // 通道2：删除超出课时包总额的多余长排（仅保留按 startDate 升序的前 totalQuota 条）。
                // keep 取"所有活跃包剩余总额"（不按今天是否生效过滤），避免未来才生效的课时包
                // 被当作额度=0 误删全部排课；无任何活跃包时跳过（额度语义未定义，不粗暴清空模板）
                val totalQuota = totalRemainingQuota(name, sid)
                if (totalQuota > 0) {
                    val c2 = scheduleDao.deleteLongTermSchedulesBeyondQuotaDual(sid, name, totalQuota)
                    if (c2 > 0) {
                        deletedSchedules += c2
                        touched = true
                    }
                }

                // 清理后在剩余额度内重新生成正确排课（用户可立即看到正确结果）
                val remainingLongTerm = scheduleDao.getAllOnce()
                    .filter { it.isActive && it.isLongTerm && it.studentName == name }
                val generated = regenerateLessonsForStudent(name, todayStr, remainingLongTerm)
                regeneratedLessons += generated
                if (generated > 0 || touched) fixedStudents++
            }
        }

        val database = db
        if (database != null) {
            database.withTransaction { run() }
        } else {
            // 无 db 注入（单元测试/旧路径）：降级为非事务执行，逻辑不变
            run()
        }
        return ScheduleFixResult(
            fixedStudents = fixedStudents,
            deletedSchedules = deletedSchedules,
            deletedPlaceholders = deletedPlaceholders,
            regeneratedLessons = regeneratedLessons
        )
    }

    /**
     * 重排：在剩余额度内为学员自动生成未来窗口内的长期课时。
     *
     * 与 [com.shangmentiyu.sportscoach.ui.operation.OperationViewModel.ensureLongTermLessonsForWeek]
     * 的额度机制一致（Bug 2 修复后语义）：
     * - totalRemaining = 当天有效课时包剩余（purchaseDate <= dateStr <= expireDate 才计入）
     * - pending = 从 fromDate 起已生成未消课课时（含清理后剩余的占位）
     * - pending >= totalRemaining 时不再生成；每生成一节 pending + 1
     *
     * @param studentName 学员姓名
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     * @param activeLongTermSchedules 清理后剩余的活跃长期排课
     * @return 本次新生成的课时数
     */
    private suspend fun regenerateLessonsForStudent(
        studentName: String,
        fromDate: String,
        activeLongTermSchedules: List<Schedule>
    ): Int {
        if (activeLongTermSchedules.isEmpty()) return 0
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
        val from = LocalDate.parse(fromDate, formatter)
        // 已占用的额度：清理后仍存在的未来未消课课时
        var pending = countUnconsumedLessonsFrom(studentName, fromDate)
        var generated = 0
        // 未来 14 天窗口（约两周），受剩余额度控制，额度用完即自动停止
        for (offset in 0 until 14) {
            val date = from.plusDays(offset.toLong())
            val dateStr = date.format(formatter)
            val dow = date.dayOfWeek.value // 1=周一 ... 7=周日（与 Schedule.dayOfWeek 一致）
            val totalRemaining = getEffectiveRemainingLessons(studentName, dateStr)
            if (pending >= totalRemaining) continue // 额度已用完，该天及后续均不再生成
            activeLongTermSchedules.filter { it.dayOfWeek == dow }.forEach { sched ->
                // startDate / endDate 边界
                if (sched.startDate.isNotBlank() && dateStr < sched.startDate) return@forEach
                if (sched.endDate.isNotBlank() && dateStr > sched.endDate) return@forEach
                // 查重：同一学员+日期+时间已有记录则跳过
                if (hasLessonForScheduleOnDate(studentName, dateStr, sched.startTime)) return@forEach
                // 额度边界（同日多时间段排课场景）
                if (pending >= totalRemaining) return@forEach
                generateLongTermLesson(sched, dateStr)
                pending++
                generated++
            }
        }
        return generated
    }

    // === 跨域辅助（私有副本，与 OperationRepository 实现保持一致） ===

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

    /**
     * 一次性获取学员所有活跃课时包（非 Flow，用于长期排课批量计算）。
     *
     * 活跃判定：status == "活跃" && !isExhausted && !isExpired
     *
     * @param studentName 学员姓名
     * @return 活跃课时包列表（按购买日期升序）
     */
    override suspend fun getActivePackagesByStudent(studentName: String): List<LessonPackage> {
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
     * @param studentName 学员姓名
     * @param dateStr 待排课日期 YYYY-MM-DD
     * @return 该日期有效课时包的剩余总课时
     */
    private suspend fun getEffectiveRemainingLessons(studentName: String, dateStr: String): Int {
        // v46 架构层二：纯计算委托 domain 计算器，与 CalculateRemainingLessonsUseCase 共享唯一实现
        return EffectiveRemainingCalculator.calculate(getActivePackagesByStudent(studentName), dateStr)
    }

    /**
     * 学员所有活跃课时包剩余总额（不按日期生效过滤，用于修正历史排课通道2的 keep 值）。
     * 与"今天生效额度"的区别：未来才生效的课时包同样计入总额度，
     * 避免其已排好的长期模板被通道2误删。
     */
    private suspend fun totalRemainingQuota(studentName: String, studentId: String?): Int {
        return pkgDao.getByStudentDual(studentId, studentName).first()
            .filter { it.status == "活跃" && !it.isExhausted && !it.isExpired }
            .sumOf { it.remainingLessons }
    }

    /**
     * 一次性获取学员全部 lessons 记录（非 Flow，按日期升序）。
     *
     * @param studentName 学员姓名
     * @return 该学员的全部 lessons 记录（按日期升序、时间升序）
     */
    private suspend fun getLessonsByStudentOnce(studentName: String): List<Lesson> {
        // v46：双通道查询（studentId 优先、studentName 回退，兼容旧数据）
        val sid = resolveStudentId(studentName)
        return lessonDao.getByStudentDualOnce(sid, studentName).sortedBy { "${it.date} ${it.time}" }
    }
}
