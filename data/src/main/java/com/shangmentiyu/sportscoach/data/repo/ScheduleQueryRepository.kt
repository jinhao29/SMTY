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
import com.shangmentiyu.sportscoach.domain.scheduling.LongTermSchedulePlanner
import com.shangmentiyu.sportscoach.domain.scheduling.ScheduleQuotaExceededException
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
 *   [countCheckedOutLessons] / [countPendingPlaceholderLessons] / [generateLongTermLesson] /
 *   [generateLongTermLessonsForStudent]）
 * - 历史错误排课一键修正（[fixHistoricalScheduleErrors]）
 *
 * === v49 彻底重构 ===
 * 校验引擎统一为 [ValidateScheduleUseCase]（三要素公式）：
 * 剩余可排课时 = 总课时(活跃包剩余之和) - 已消耗(已签退) - 待消耗(占位)；
 * 任何排课 startDate 不得早于首次购买日期；额度用尽后不再生成任何未来排课。
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
     * === v49 彻底重构：校验引擎统一 + 业务异常上抛 ===
     * 1. 核心校验 1（日期前置）：startDate < 首次购买日期 → [IllegalArgumentException] 直接上抛，
     *    UI 层捕获并显示明确错误信息（不被内部 catch 吞掉）。
     * 2. 核心校验 2（额度封顶）：新增场景剩余可排课时 = 总课时 - 已消耗 - 待消耗 <= 0
     *    → [ScheduleQuotaExceededException] 直接上抛，UI 层捕获显示。
     * 3. 技术异常（主键冲突 / 约束违反 / 序列化失败）仍返回 false 并 Logcat 留痕。
     *
     * @param schedule 待保存的排课对象（id 必须正确传递，编辑模式由 UI 层填入原 id）
     * @return true=保存成功；false=保存失败（技术异常，已记录到 Logcat）
     * @throws IllegalArgumentException 排课日期早于学员首次购买日期
     * @throws ScheduleQuotaExceededException 学员剩余可排课时为 0
     */
    suspend fun saveSchedule(schedule: Schedule): Boolean {
        // === 核心校验 1（try 之前执行）：排课生效日期不得早于学员首次购买课时包日期 ===
        // 业务校验异常向上抛给 ViewModel 层转为用户提示，不被下方 try-catch 吞掉。
        // 体验课（isTrial=true）无购买日期约束，自动跳过
        validate.validateStartDateOrThrow(schedule.studentName, schedule.startDate, schedule.isTrial)

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
                        // 不存在 → 新增。核心校验 2：新增前强制额度校验（三要素公式），
                        // 剩余可排课时 <= 0 直接抛业务异常，绝不落库；体验课不消耗课时包，跳过额度校验
                        if (!validate.hasRemainingCapacity(
                                schedule.studentName,
                                schedule.startDate.ifBlank { todayStr() },
                                schedule.isTrial
                            )
                        ) {
                            throw ScheduleQuotaExceededException(
                                "无法排课：该学员课时额度已满（剩余可排课时为 0）"
                            )
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
                                schedule.startDate.ifBlank { todayStr() },
                                schedule.isTrial
                            )
                        ) {
                            throw ScheduleQuotaExceededException(
                                "无法排课：该学员课时额度已满（剩余可排课时为 0）"
                            )
                        }
                        scheduleDao.insert(schedule)
                        Log.i("DataFlow",
                            "saveSchedule 新增成功（无事务降级）：id=${schedule.id}")
                    }
                }
            // 保存成功后触发自动备份
            AutoBackupScheduler.notifyDataChange()
            true
        } catch (e: ScheduleQuotaExceededException) {
            // 业务校验异常：上抛由 UI 显示明确文案，不走"保存失败"笼统提示
            Log.w("DataFlow",
                "saveSchedule 额度校验拦截：id=${schedule.id}, student=${schedule.studentName}, ${e.message}")
            throw e
        } catch (e: Exception) {
            // 所有技术异常通过 Log.e 输出到 Logcat，tag=DataFlow，便于过滤定位
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
     * === v49 三要素公式：已签退课时数（「已消耗」） ===
     *
     * 剩余可排课时 = 总课时(活跃包剩余之和) - 已消耗(已签退) - 待消耗(占位)。
     * 本方法统计已签退（signOutTime 非空）的课时数量。
     *
     * @param studentName 学员姓名
     * @return 已签退课时数
     */
    override suspend fun countCheckedOutLessons(studentName: String): Int {
        val sid = resolveStudentId(studentName)
        return lessonDao.countCheckedOutLessonsDual(sid, studentName)
    }

    /**
     * === v49 三要素公式：待消耗占位课时数（「待消耗」） ===
     *
     * 统计学员从指定日期起"长期自动生成 + 未签退"的占位课时数量。
     * 长期自动课时识别：lessonType 包含"(长期自动)"。
     *
     * 用于长期排课生成时计算剩余可排课时 = 总课时 - 已消耗 - 待消耗，
     * 额度用完后立即停止生成，根治「额度用完仍排课」。
     *
     * @param studentName 学员姓名
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     * @return 待消耗占位课时数
     */
    override suspend fun countPendingPlaceholderLessons(studentName: String, fromDate: String): Int {
        val sid = resolveStudentId(studentName)
        return lessonDao.countPendingPlaceholderLessonsDual(sid, studentName, fromDate)
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
        // === 体验课不参与长期自动生成（防御：生成器已过滤，此处兜底） ===
        if (sched.isTrial) return
        // === 核心校验 1：生成日 / 生效日不得早于学员首次购买课时包日期 ===
        // 兜底校验：正常路径下长期排课生成器已按模板 startDate（写入时校验 >= 首次购买日）
        // 过滤，此处为数据边界防御，违规直接抛异常阻止写入
        validate.validateStartDateOrThrow(sched.studentName, dateStr)
        validate.validateStartDateOrThrow(sched.studentName, sched.startDate)
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
            packageId = "",
            isTrial = sched.isTrial
        )
        lessonDao.insert(lesson)
    }

    /**
     * === v49 彻底重构：长期排课统一生成入口（独立学员循环 + 额度封顶） ===
     *
     * 供 [com.shangmentiyu.sportscoach.ui.operation.OperationViewModel.ensureLongTermLessonsForWeek]
     * 与 [fixHistoricalScheduleErrors] 共用，保证两处生成策略完全一致：
     *
     * 1. 独立循环处理每个学员（本方法即为单个学员的一次独立处理）
     * 2. 遍历未来日期（从 [weekStart] 所在周开始，共 [windowDays] 天）
     * 3. 检查每一天是否已存在排课（当天已有课时记录则跳过）
     * 4. 若当天未排：判断剩余可排课时（三要素公式）是否 > 0：
     *    - 是 → 按当天 dayOfWeek 命中的长期模板生成一条 lessons 占位，额度减 1
     *    - 否 → 立即停止该学员后续所有排课生成
     * 5. 严格遵循学员排课偏好（周一至周五等）：周几无模板则跳过
     * 6. 模板本身为 schedules 表长期记录（手动排课时写入），本方法只追加 lessons 占位
     * 7. 体验课（isTrial=true）不参与长期自动生成，一律排除
     *
     * @param studentName 学员姓名
     * @param weekStart 当前周起始日期 YYYY-MM-DD（周一）
     * @param today 今天 YYYY-MM-DD（早于今天的日期不生成）
     * @param windowDays 未来生成窗口天数，默认 28（约 4 周）
     * @return 本次新生成的课时数
     */
    suspend fun generateLongTermLessonsForStudent(
        studentName: String,
        weekStart: String,
        today: String,
        windowDays: Int = LongTermSchedulePlanner.DEFAULT_WINDOW_DAYS
    ): Int {
        val sid = resolveStudentId(studentName)
        val longTerm = scheduleDao.getAllOnce()
            .filter { it.isActive && it.isLongTerm && !it.isTrial && it.studentName == studentName }
        if (longTerm.isEmpty()) return 0
        // 剩余可排课时 = 总课时 - 已消耗 - 待消耗（三要素公式），<= 0 直接不生成
        val available = validate.availableQuota(studentName, today)
        if (available <= 0) return 0
        // 该学员已有课时记录的日期集合（当天已排则跳过）
        val bookedDates = lessonDao.getByStudentDualOnce(sid, studentName)
            .map { it.date }
            .toSet()
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = longTerm,
            weekStart = weekStart,
            today = today,
            availableQuota = available,
            alreadyBookedDates = bookedDates,
            windowDays = windowDays
        )
        var generated = 0
        for (plan in plans) {
            generateLongTermLesson(plan.schedule, plan.date)
            generated++
        }
        return generated
    }

    /**
     * === v49 彻底重构：一键修正历史错误排课（设置页入口，全量清理 + 重排）===
     *
     * 背景：历史版本排课 Bug 已修复，但数据库中已积压大量错误排课：
     * - 早于学员首次购买日期的长期排课模板（schedules.startDate < 最早 purchaseDate）
     * - 超过课时包剩余总额度的长期排课模板 / 未来占位课时
     *
     * 执行流程（db 可用时整体包裹在单事务内，任一学员失败整体回滚）：
     * A. 遍历所有活跃学员（students.isActive = 1）
     * B. 通道1：DELETE 该学员所有 startDate < 首次购买日的无效排课
     * C. 通道2：DELETE 超出课时包总额的多余长排（仅保留按 startDate 升序的前 totalQuota 条），
     *    额度严格使用 [totalRemainingQuota]（所有活跃包剩余总额），不按"当天生效额度"过滤
     * D. 占位课时超额清理：未来占位课时（长期自动 + 未签退 + date >= today）超过
     *    （总课时 - 已消耗）时删除多余占位，优先删除日期靠后的
     * E. 清理后为每个受影响学员重新调用 [generateLongTermLessonsForStudent] 重排
     *    （与 ensureLongTermLessonsForWeek 完全相同的生成策略：独立学员循环 + 逐日 + 额度封顶）
     *
     * 绝对安全边界：
     * - 仅删除 schedules 表的错误长期排课模板；仅删除 lessons 表中
     *   "未来（date >= today）+ 长期自动 + 未签退"的未占用占位符
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
                var touched = false

                // 通道1：删除所有 startDate 早于首次购买日期的无效排课
                if (earliestPurchase != null) {
                    val c1 = scheduleDao.deleteSchedulesBeforeDateDual(sid, name, earliestPurchase)
                    if (c1 > 0) {
                        deletedSchedules += c1
                        touched = true
                    }
                }

                // 通道2：删除超出课时包总额的多余长排（仅保留按 startDate 升序的前 totalQuota 条）。
                // keep 严格取"所有活跃包剩余总额"（不按今天是否生效过滤），避免未来才生效的课时包
                // 被当作额度=0 误删全部排课；无任何活跃包时跳过（额度语义未定义，不粗暴清空模板）
                val totalQuota = totalRemainingQuota(name, sid)
                if (totalQuota > 0) {
                    val c2 = scheduleDao.deleteLongTermSchedulesBeyondQuotaDual(sid, name, totalQuota)
                    if (c2 > 0) {
                        deletedSchedules += c2
                        touched = true
                    }
                }

                // 占位课时超额清理：仅"长期自动 + 未签退 + date >= today"，优先删除日期靠后的。
                // 占位可占用额度上限 = 总课时 - 已消耗（已签退），不按当天生效额度过滤。
                // 体验课占位（isTrial=true）不占用课时包额度，一律排除
                val placeholders = getLessonsByStudentOnce(name)
                    .filter {
                        !it.isTrial &&
                            it.date >= todayStr &&
                            it.lessonType.contains("(长期自动)") &&
                            it.status != "已签退"
                    }
                    .sortedBy { it.date }
                if (placeholders.isNotEmpty()) {
                    val consumed = countCheckedOutLessons(name)
                    val quotaForPlaceholders = (totalQuota - consumed).coerceAtLeast(0)
                    val excess = placeholders.size - quotaForPlaceholders
                    if (excess > 0) {
                        placeholders.takeLast(excess).forEach {
                            lessonDao.deleteById(it.id)
                            deletedPlaceholders++
                            touched = true
                        }
                    }
                }

                // 清理后在剩余额度内重新生成正确排课（用户可立即看到正确结果）。
                // 与 ensureLongTermLessonsForWeek 共用统一生成入口（独立学员循环 + 逐日 + 额度封顶）
                val generated = generateLongTermLessonsForStudent(name, todayStr, todayStr)
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
