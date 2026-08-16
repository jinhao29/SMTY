package com.shangmentiyu.sportscoach.data.repo

import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.db.LessonDao
import com.shangmentiyu.sportscoach.data.db.LessonPackageDao
import com.shangmentiyu.sportscoach.data.internal.AutoBackupScheduler
import com.shangmentiyu.sportscoach.data.model.Lesson
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 批量排课域 Repository（v53 从 [OperationRepository] 拆出）：批量自动排课 + 历史余额修复。
 *
 * 核心原则：排课只排课程表（占位），不扣余额；余额扣减仅在实际消课（签退）时
 * 由 [LessonConsumptionRepository.consumeLessonForCheckOut] 执行，实现排课与消课完全分离。
 */
class BatchScheduleRepository(
    private val lessonDao: LessonDao,
    private val pkgDao: LessonPackageDao,
    private val db: AppDatabase?
) {

    /**
     * 批量自动排课结果。
     *
     * 排课只排课程表（占位），不扣余额；
     * 余额扣减仅在实际消课（签退）时发生。
     */
    data class BatchScheduleResult(
        val success: Boolean,
        val createdCount: Int = 0,
        val skippedCount: Int = 0,
        val message: String = ""
    )

    /**
     * 一次性修复脚本结果：修复自动排课阶段错误扣减的课时包余额。
     */
    data class BalanceFixResult(
        val success: Boolean,
        val fixedLessonCount: Int = 0,
        val fixedPackageCount: Int = 0,
        val details: String = ""
    )

    /**
     * 批量自动排课：在 Room 事务内批量插入课时记录（仅占位，不扣余额）。
     *
     * 核心原则：
     * - 排课只排课程表（占位），不扣余额；扣余额仅在实际消课（签退）时发生
     * - 所有日期计算在事务外完成（由 LessonDateCalculator 纯内存计算）
     * - 事务前预查已有排课，过滤重复日期，避免 UNIQUE(studentName, date, time) 冲突
     * - 事务内仅执行 insertAll，保证批量插入原子性
     * - 任一步失败整体回滚，数据保持原样
     *
     * 去重逻辑：
     * - 查询学员在日期范围内、指定时间点已存在的排课日期
     * - 过滤掉已存在的日期，只插入新增日期
     * - 若全部重复，返回 success=true + skippedCount，不报错
     *
     * 课时记录的 packageId 留空，签退时由 [LessonConsumptionRepository.consumeLessonForCheckOut]
     * 统一执行课时包扣减，实现排课与消课完全分离。
     *
     * @param lessonDates 排课日期列表（已由 LessonDateCalculator 计算）
     * @param studentName 学员姓名
     * @param studentId 学员唯一 ID
     * @param coachName 教练姓名
     * @param startTime 上课时间 HH:mm
     * @param durationMinutes 单次课时时长（分钟）
     * @param location 上课地点
     * @param lessonType 课程类型
     * @return BatchScheduleResult 携带操作结果（含实际插入数和跳过数）
     */
    suspend fun batchAutoSchedule(
        lessonDates: List<LocalDate>,
        studentName: String,
        studentId: String?,
        coachName: String,
        startTime: String,
        durationMinutes: Int,
        location: String,
        lessonType: String
    ): BatchScheduleResult {
        val database = db ?: return BatchScheduleResult(
            success = false,
            message = "排课失败：数据库未初始化"
        )

        val totalLessons = lessonDates.size
        if (totalLessons == 0) {
            return BatchScheduleResult(success = false, message = "排课日期列表为空")
        }

        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

        // === 去重：查询学员在目标日期范围内、指定时间点已有的排课日期 ===
        val fromDateStr = lessonDates.min().format(dateFormatter)
        val toDateStr = lessonDates.max().format(dateFormatter)
        val existingDates = lessonDao.getExistingDatesByStudentAndTime(
            studentId, studentName, fromDateStr, toDateStr, startTime
        ).toSet()

        val newDates = lessonDates.filter { it.format(dateFormatter) !in existingDates }
        val skippedCount = totalLessons - newDates.size

        // 全部重复：不报错，提示无需重复添加
        if (newDates.isEmpty()) {
            return BatchScheduleResult(
                success = true,
                createdCount = 0,
                skippedCount = skippedCount,
                message = "所选日期均已排课，无需重复添加（跳过 $skippedCount 节）"
            )
        }

        val newCount = newDates.size

        return try {
            database.withTransaction {
                // 1. 在内存中构建所有 Lesson 对象（packageId 留空，签退时统一扣费）
                val lessons = newDates.map { date ->
                    Lesson(
                        id = java.util.UUID.randomUUID().toString().take(8),
                        date = date.format(dateFormatter),
                        time = startTime,
                        studentName = studentName,
                        studentId = studentId,
                        duration = durationMinutes,
                        coach = coachName,
                        location = location,
                        lessonType = lessonType,
                        packageId = "",
                        status = "待签到",
                        isTrial = false
                    )
                }

                // 2. 批量插入课时记录（一次性写入，事务保证原子性）
                lessonDao.insertAll(lessons)

                AutoBackupScheduler.notifyDataChange()

                val msg = if (skippedCount > 0) {
                    "新增 $newCount 节，跳过 $skippedCount 节重复"
                } else {
                    "已排 $newCount 节课"
                }

                BatchScheduleResult(
                    success = true,
                    createdCount = newCount,
                    skippedCount = skippedCount,
                    message = msg
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("BatchAutoSchedule", "批量排课失败：${e.message}", e)
            BatchScheduleResult(
                success = false,
                message = "排课失败：${e.message ?: "未知异常"}"
            )
        }
    }

    /**
     * 小班课批量排课：为多名学员在同一组日期上批量插入课时占位（仅占位，不扣余额）。
     *
     * 与 [batchAutoSchedule] 的区别：为每个学员 × 每个日期都创建一条 Lesson。
     * 每名学员按其剩余额度（memberQuotas）各自截断节数：额度少的学员排完即退出，
     * 剩余节数由额度充足的学员单独继续排。每个上课日生成独立的 sessionGroupId，
     * 签退时由 [LessonConsumptionRepository.consumeLessonForCheckOut] 识别同一天同组学员统一消课（不会跨天误消）。
     *
     * @param lessonDates 排课日期列表（已由 LessonDateCalculator 计算，共 N 节）
     * @param groupStudentIds 小班课学员 studentId 集合（与 groupStudentNames 顺序一致）
     * @param groupStudentNames 小班课学员姓名列表
     * @param memberQuotas 每名学员本次可排的节数（与 groupStudentNames 顺序一致，≤ lessonDates.size）
     */
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
    ): BatchScheduleResult {
        val database = db ?: return BatchScheduleResult(
            success = false,
            message = "排课失败：数据库未初始化"
        )
        if (lessonDates.isEmpty()) {
            return BatchScheduleResult(success = false, message = "排课日期列表为空")
        }
        if (groupStudentNames.isEmpty()) {
            return BatchScheduleResult(success = false, message = "请选择学员")
        }

        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
        val fromDateStr = lessonDates.min().format(dateFormatter)
        val toDateStr = lessonDates.max().format(dateFormatter)
        val studentIds = groupStudentIds.toList()
        // 每个上课日一个 sessionId：签退时按当天组员统一消课，避免同组跨天误消
        val sessionIds = lessonDates.associateWith { java.util.UUID.randomUUID().toString().take(8) }

        val lessons = mutableListOf<Lesson>()
        var skipped = 0
        groupStudentNames.forEachIndexed { index, name ->
            val sid = studentIds.getOrNull(index)
            val maxLessons = memberQuotas.getOrNull(index) ?: 0
            if (maxLessons <= 0) return@forEachIndexed
            val existingDates = lessonDao.getExistingDatesByStudentAndTime(
                sid, name, fromDateStr, toDateStr, startTime
            ).toSet()
            for (date in lessonDates.take(maxLessons)) {
                val dateStr = date.format(dateFormatter)
                if (dateStr in existingDates) { skipped++; continue }
                lessons += Lesson(
                    id = java.util.UUID.randomUUID().toString().take(8),
                    date = dateStr,
                    time = startTime,
                    studentName = name,
                    studentId = sid,
                    duration = durationMinutes,
                    coach = coachName,
                    location = location,
                    lessonType = lessonType,
                    packageId = "",
                    status = "待签到",
                    isTrial = false,
                    groupScheduleId = sessionIds.getValue(date)
                )
            }
        }

        if (lessons.isEmpty()) {
            return BatchScheduleResult(
                success = true,
                createdCount = 0,
                skippedCount = skipped,
                message = "所选日期均已排课，无需重复添加（跳过 $skipped 节）"
            )
        }

        return try {
            database.withTransaction {
                lessonDao.insertAll(lessons)
                AutoBackupScheduler.notifyDataChange()
                BatchScheduleResult(
                    success = true,
                    createdCount = lessons.size,
                    skippedCount = skipped,
                    message = if (skipped > 0) {
                        "新增 ${lessons.size} 节，跳过 $skipped 节重复"
                    } else {
                        "已排 ${lessons.size} 节课"
                    }
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("BatchAutoScheduleGroup", "小班课批量排课失败：${e.message}", e)
            BatchScheduleResult(
                success = false,
                message = "排课失败：${e.message ?: "未知异常"}"
            )
        }
    }

    /**
     * 一次性修复脚本：修复自动排课阶段错误扣减的课时包余额。
     *
     * 背景：旧版自动排课在排课阶段即扣减课时包余额（增加 usedLessons + 设置 Lesson.packageId），
     * 导致排课与消课未分离。签退时检测到 packageId 非空会跳过扣费，
     * 但未签退的排课记录已错误扣减了余额。
     *
     * 修复逻辑（单事务原子操作）：
     * 1. 查询所有 packageId 非空且 status != '已签退' 的课时记录（排课阶段错误扣费的遗留）
     * 2. 按 packageId 分组统计每个课时包被错误扣减的节数
     * 3. 回退每个课时包的 usedLessons（减去错误扣减数，不低于 0）
     * 4. 恢复课时包状态：若 usedLessons 回退后 < totalLessons 且原状态为"已用完"，恢复为"活跃"
     * 5. 清除这些课时记录的 packageId（恢复为待消课状态，签退时统一扣费）
     *
     * 已签退的课时记录不受影响（packageId 保留作为扣费归属记录，usedLessons 计数正确）。
     *
     * @return BalanceFixResult 携带修复结果
     */
    suspend fun fixPrematureBalanceDeduction(): BalanceFixResult {
        val database = db ?: return BalanceFixResult(
            success = false,
            details = "数据库未初始化"
        )

        return try {
            database.withTransaction {
                // 1. 查询所有未签退但已关联课时包的课时记录
                val unconsumedLessons = lessonDao.getUnconsumedWithPackageId()

                if (unconsumedLessons.isEmpty()) {
                    return@withTransaction BalanceFixResult(
                        success = true,
                        fixedLessonCount = 0,
                        fixedPackageCount = 0,
                        details = "无需修复：没有发现排课阶段错误扣费的遗留数据"
                    )
                }

                // 2. 按 packageId 分组统计错误扣减数
                val deductionByPkg = unconsumedLessons
                    .groupBy { it.packageId }
                    .mapValues { it.value.size }

                // 3. 逐个回退课时包 usedLessons
                var fixedPkgCount = 0
                val fixDetails = StringBuilder()
                for ((pkgId, overDeductedCount) in deductionByPkg) {
                    val pkg = pkgDao.getById(pkgId) ?: continue
                    val newUsed = maxOf(0, pkg.usedLessons - overDeductedCount)
                    val newStatus = when {
                        newUsed >= pkg.totalLessons -> "已用完"
                        pkg.status == "已用完" -> "活跃"
                        else -> pkg.status
                    }
                    val updatedPkg = pkg.copy(usedLessons = newUsed, status = newStatus)
                    pkgDao.update(updatedPkg)
                    fixedPkgCount++
                    fixDetails.append("课时包「${pkg.name}」(${pkg.studentName})" +
                        " usedLessons: ${pkg.usedLessons} -> $newUsed" +
                        " status: ${pkg.status} -> $newStatus\n")
                }

                // 4. 批量清除未签退课时的 packageId
                val clearedCount = lessonDao.clearPackageIdForUnconsumed()

                AutoBackupScheduler.notifyDataChange()

                BalanceFixResult(
                    success = true,
                    fixedLessonCount = clearedCount,
                    fixedPackageCount = fixedPkgCount,
                    details = "已修复 $fixedPkgCount 个课时包，清除 $clearedCount 条课时记录的 packageId\n${fixDetails}"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("BalanceFix", "修复余额扣减失败：${e.message}", e)
            BalanceFixResult(
                success = false,
                details = "修复失败：${e.message ?: "未知异常"}"
            )
        }
    }
}
