package com.shangmentiyu.sportscoach.ui.operation

import com.shangmentiyu.sportscoach.core.LessonDateCalculator
import com.shangmentiyu.sportscoach.data.repo.CoachConflictException
import com.shangmentiyu.sportscoach.data.repo.LessonPackageRepository
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.ScheduleMemoryRepository
import com.shangmentiyu.sportscoach.data.repo.ScheduleRepository
import com.shangmentiyu.sportscoach.domain.scheduling.ValidateScheduleUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 自动排课协调器（v53 从 OperationViewModel 拆出）。
 *
 * 承载「按课时包自动排课」与「小班课自动排课」两大业务流程：
 * 纯内存日期计算 → 前置校验 → 事务内批量插入占位 → 创建周历模板 → 保存记忆。
 * 逻辑逐字搬迁自 OperationViewModel，行为与拆分前完全一致。
 */
internal class AutoScheduleCoordinator(
    private val opRepo: OperationRepository,
    private val scheduleRepo: ScheduleRepository,
    private val memoryRepo: ScheduleMemoryRepository,
    private val pkgRepo: LessonPackageRepository,
    private val validateSchedule: ValidateScheduleUseCase
) {

    /**
     * 按课时包自动排课：用户指定开始日期、勾选上课日、总节数，
     * 纯内存计算所有排课日期后，在单个 Room 事务中批量插入课时记录（仅占位，不扣余额）。
     *
     * 业务规则：
     * - 从 startDate 起逐日后移，仅累计用户勾选的上课日，直到累计数等于 totalLessons
     * - 校验预计结束日期是否超过课时包截止日期（expireDate），超过则终止
     * - 事务内：insertAll 批量插入课时记录（不扣余额，不设 packageId）
     * - 排课与消课完全分离：余额扣减仅在实际消课（签退）时发生
     * - 仍创建 Schedule 记录（isLongTerm=true）供课表周历模板显示
     */
    suspend fun autoScheduleFromPackage(
        packageId: String,
        coachName: String,
        daysOfWeek: Set<Int>,
        startTime: String,
        totalLessons: Int,
        startDateStr: String = "",
        durationMinutes: Int = 60,
        location: String = "",
        lessonType: String = "训练课",
        dateFormatter: DateTimeFormatter,
        today: String,
        onToast: (String) -> Unit
    ) {
        val pkg = withContext(Dispatchers.IO) { pkgRepo.getPkgById(packageId) }
        if (pkg == null) {
            onToast("课时包不存在")
            return
        }
        if (pkg.status != "活跃") {
            onToast("课时包状态为「${pkg.status}」，无法排课")
            return
        }
        // === 排课节数不得超出该课时包剩余课时（排课只占位不扣费，但占位总数受余额封顶）===
        if (totalLessons > pkg.remainingLessons) {
            onToast("本次排课节数($totalLessons)超过课时包剩余课时(${pkg.remainingLessons})，无法排课")
            return
        }

        val todayDate = LocalDate.now()
        val purchaseDate = try {
            LocalDate.parse(pkg.purchaseDate, dateFormatter)
        } catch (_: Exception) {
            todayDate
        }

        // 开始日期：优先使用用户输入，为空则回退到课包购买日
        val startDate = if (startDateStr.isNotBlank()) {
            try { LocalDate.parse(startDateStr, dateFormatter) } catch (_: Exception) { purchaseDate }
        } else purchaseDate

        // 课包过期日（可空）
        val expireDate = if (pkg.expireDate.isNotBlank()) {
            try { LocalDate.parse(pkg.expireDate, dateFormatter) } catch (_: Exception) { null }
        } else null
        if (expireDate != null && startDate.isAfter(expireDate)) {
            onToast("课时包已过期，无法排课")
            return
        }

        // === 步骤1：纯内存计算所有排课日期（无数据库访问） ===
        val lessonDates = LessonDateCalculator.calculateLessonDates(
            startDate = startDate,
            totalLessons = totalLessons,
            selectedDays = daysOfWeek
        )
        val calculatedEndDate = lessonDates.last()

        // === 步骤2：前置校验 - 预计结束日期不超过课时包有效期 ===
        if (expireDate != null && calculatedEndDate.isAfter(expireDate)) {
            onToast("排课失败：预计结束日期 ${calculatedEndDate.format(dateFormatter)}" +
                " 超过课时包有效期 ${pkg.expireDate}")
            return
        }

        // === 步骤3：事务内批量插入课时记录（仅占位，不扣余额；签退时统一扣费） ===
        val result = withContext(Dispatchers.IO) {
            opRepo.batchAutoSchedule(
                lessonDates = lessonDates,
                studentName = pkg.studentName,
                studentId = pkg.studentId,
                coachName = coachName,
                startTime = startTime,
                durationMinutes = durationMinutes,
                location = location,
                lessonType = lessonType
            )
        }

        if (!result.success) {
            onToast(result.message)
            return
        }

        // === 步骤4：创建 Schedule 记录（供课表周历模板显示） ===
        // endDate 按周几分组取最后一天，确保模板显示正确的排课结束日期
        val endDatesByDow = lessonDates.groupBy { it.dayOfWeek.value }
            .mapValues { it.value.last() }

        val coachKey = coachName.ifBlank { "默认教练" }
        memoryRepo.saveMemory(coachKey, "time", startTime.trim())
        if (location.isNotBlank()) {
            memoryRepo.saveMemory(coachKey, "location", location.trim())
        }
        daysOfWeek.sorted().forEach { dow ->
            memoryRepo.saveMemory(coachKey, "dayOfWeek", dow.toString())
        }

        for (dow in daysOfWeek.sorted()) {
            val endD = endDatesByDow[dow] ?: continue
            try {
                scheduleRepo.addSchedule(
                    studentName = pkg.studentName,
                    studentId = pkg.studentId,
                    coachName = coachName,
                    dayOfWeek = dow,
                    startTime = startTime,
                    durationMinutes = durationMinutes,
                    location = location,
                    lessonType = lessonType,
                    // 课时已由 batchAutoSchedule 按具体日期直接生成，模板仅用于周历展示，
                    // 不再标记长期排课，避免触发 ensureLongTermLessonsForWeek 冗余生成与「余额不足」误报。
                    isLongTerm = false,
                    endDate = endD.format(dateFormatter),
                    startDate = startDate.format(dateFormatter),
                    // 按课时包排课允许同一时间段排多个学员（如 9-10 点 A 学员、9-10 点 B 学员）
                    skipConflictCheck = true
                )
            } catch (e: CoachConflictException) {
                onToast("周${dow} ${startTime} 教练时段冲突，已跳过模板创建")
            }
        }

        val endDateDisplay = calculatedEndDate.format(
            DateTimeFormatter.ofPattern("M月d日", Locale.getDefault())
        )
        onToast(if (result.skippedCount > 0) {
            "已为 ${pkg.studentName} 成功排课 ${result.createdCount} 节" +
                "（跳过 ${result.skippedCount} 节重复）" +
                "，预计结束日期 $endDateDisplay"
        } else {
            "已为 ${pkg.studentName} 成功排课 ${result.createdCount} 节" +
                "，预计结束日期 $endDateDisplay"
        })
    }

    /**
     * 小班课自动排课：与「按课时包排课」同款逻辑，但面向多名学员。
     *
     * 业务规则：
     * - 从 startDate 起逐日后移，累计用户勾选的上课日，直到累计数等于 totalLessons
     * - 事务内：为每名学员 × 每个日期批量插入课时占位（不扣余额，签退时统一扣费）
     * - 每个上课日的课时共享同一 sessionId，签退时识别当天同组统一消课（不跨天）
     * - 仍创建 Schedule 模板（isLongTerm=false）供课表周历模板显示
     */
    suspend fun autoScheduleGroup(
        groupStudentIds: Set<String>,
        groupStudentNames: List<String>,
        coachName: String,
        daysOfWeek: Set<Int>,
        startTime: String,
        totalLessons: Int,
        startDateStr: String = "",
        durationMinutes: Int = 60,
        location: String = "",
        lessonType: String = "训练课",
        dateFormatter: DateTimeFormatter,
        today: String,
        onToast: (String) -> Unit
    ) {
        val todayDate = LocalDate.now()
        val startDate = if (startDateStr.isNotBlank()) {
            try { LocalDate.parse(startDateStr, dateFormatter) } catch (_: Exception) { todayDate }
        } else todayDate

        // === 步骤0：计算每名学员剩余可排课时，按各自额度截断 ===
        // 额度少的学员排完即退出，剩余节数由额度充足的学员单独继续排。
        val quotaByName = mutableMapOf<String, Int>()
        for (name in groupStudentNames) {
            val q = validateSchedule.availableQuota(name, today)
            quotaByName[name] = q
            android.util.Log.d("GroupSchedule", "小班课学员「$name」剩余可排课时=$q")
        }
        val maxQuota = quotaByName.values.maxOrNull() ?: 0
        android.util.Log.d("GroupSchedule", "小班课最大学员额度=$maxQuota，请求节数=$totalLessons")
        if (maxQuota <= 0) {
            onToast("无法排课：所有学员剩余可排课时均为 0，请先核对课时包")
            return
        }
        // 小班课总节数受「剩余课时最多的学员」封顶（不超过任何学员可上的最高节数）
        val effectiveTotal = minOf(totalLessons, maxQuota)
        // 每名学员本次实际排课节数：额度内截断，且不超过总节数
        val memberQuotas = groupStudentNames.map { name ->
            minOf(quotaByName[name] ?: 0, effectiveTotal)
        }
        val reducedNames = groupStudentNames.filterIndexed { i, _ ->
            (memberQuotas.getOrNull(i) ?: 0) < effectiveTotal
        }

        // === 步骤1：纯内存计算所有排课日期 ===
        val lessonDates = LessonDateCalculator.calculateLessonDates(
            startDate = startDate,
            totalLessons = effectiveTotal,
            selectedDays = daysOfWeek
        )
        val calculatedEndDate = lessonDates.last()
        val groupScheduleId = java.util.UUID.randomUUID().toString().take(8)

        // === 步骤2：事务内批量插入课时占位（不扣余额） ===
        val result = withContext(Dispatchers.IO) {
            opRepo.batchAutoScheduleGroup(
                lessonDates = lessonDates,
                groupStudentIds = groupStudentIds,
                groupStudentNames = groupStudentNames,
                memberQuotas = memberQuotas,
                coachName = coachName,
                startTime = startTime,
                durationMinutes = durationMinutes,
                location = location,
                lessonType = lessonType
            )
        }
        if (!result.success) {
            onToast(result.message)
            return
        }

        // === 步骤3：保存记忆 ===
        val coachKey = coachName.ifBlank { "默认教练" }
        memoryRepo.saveMemory(coachKey, "time", startTime.trim())
        if (location.isNotBlank()) {
            memoryRepo.saveMemory(coachKey, "location", location.trim())
        }
        daysOfWeek.sorted().forEach { dow ->
            memoryRepo.saveMemory(coachKey, "dayOfWeek", dow.toString())
        }

        // === 步骤4：创建 Schedule 模板（每名学员按其额度截断 endDate） ===
        val studentIds = groupStudentIds.toList()
        val templates = mutableListOf<com.shangmentiyu.sportscoach.data.model.Schedule>()
        groupStudentNames.forEachIndexed { index, name ->
            val maxLessons = memberQuotas.getOrNull(index) ?: 0
            if (maxLessons <= 0) return@forEachIndexed
            val memberDates = lessonDates.take(maxLessons)
            val memberEndByDow = memberDates.groupBy { it.dayOfWeek.value }
                .mapValues { it.value.last() }
            for (dow in daysOfWeek.sorted()) {
                val endD = memberEndByDow[dow] ?: continue
                templates += com.shangmentiyu.sportscoach.data.model.Schedule(
                    studentName = name,
                    studentId = studentIds.getOrNull(index),
                    coachName = coachName,
                    dayOfWeek = dow,
                    startTime = startTime,
                    durationMinutes = durationMinutes,
                    location = location,
                    lessonType = lessonType,
                    startDate = startDate.format(dateFormatter),
                    endDate = endD.format(dateFormatter),
                    isLongTerm = false,
                    isTrial = false,
                    groupScheduleId = groupScheduleId
                )
            }
        }
        scheduleRepo.insertGroupScheduleTemplates(templates)

        val endDateDisplay = calculatedEndDate.format(
            DateTimeFormatter.ofPattern("M月d日", Locale.getDefault())
        )
        val quotaHint = if (reducedNames.isNotEmpty()) {
            "（${reducedNames.joinToString("、")} 课时不足，已按各自额度排课）"
        } else ""
        onToast("已为 ${groupStudentNames.size} 名学员小班排课 $effectiveTotal 节" +
            "$quotaHint，预计结束日期 $endDateDisplay")
    }
}
