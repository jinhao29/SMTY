package com.shangmentiyu.sportscoach.ui.operation

import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.repo.CoachConflictException
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.ScheduleMemoryRepository
import com.shangmentiyu.sportscoach.data.repo.ScheduleRepository
import com.shangmentiyu.sportscoach.domain.scheduling.ScheduleQuotaExceededException
import com.shangmentiyu.sportscoach.domain.scheduling.ValidateScheduleUseCase
import com.shangmentiyu.sportscoach.ui.schedule.ScheduleForm
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * 排课保存协调器（v53 从 OperationViewModel 拆出）。
 *
 * 承载 saveSchedule（新建/编辑/小班课/强制替换）与 validateScheduleForSave
 * 的完整业务流程，ViewModel 只负责状态快照与事件转发（[SaveContext]）。
 * 逻辑逐字搬迁自 OperationViewModel，行为与拆分前完全一致。
 */
internal class ScheduleSaveCoordinator(
    private val opRepo: OperationRepository,
    private val scheduleRepo: ScheduleRepository,
    private val memoryRepo: ScheduleMemoryRepository,
    private val validateSchedule: ValidateScheduleUseCase
) {

    /**
     * 保存上下文：ViewModel 状态快照 + 事件回调。
     *
     * @param weekStart 当前周起始日期（周一），用于多选周几 → 具体日期换算
     * @param editing 当前编辑中的排课（null=新建模式）
     * @param today 今天日期字符串 yyyy-MM-dd
     * @param dateFormatter yyyy-MM-dd 格式化器
     * @param onToast 用户提示回调（对应原 _toast.value）
     * @param onConflict 教练时间冲突事件回调（对应原 _coachConflictEvent.tryEmit）
     * @param onSaved 保存成功事件回调（对应原 _saveSuccessEvent.tryEmit + 清空编辑态）
     * @param onLongTermEnsure 长期排课生成触发回调（对应原 ensureLongTermLessonsForWeek）
     */
    class SaveContext(
        val weekStart: Date,
        val editing: Schedule?,
        val today: String,
        val dateFormatter: DateTimeFormatter,
        val onToast: (String) -> Unit,
        val onConflict: (CoachConflictException) -> Unit,
        val onSaved: () -> Unit,
        val onLongTermEnsure: () -> Unit
    )

    /**
     * 保存前统一校验（dialog 与 save 共用同一入口）：
     * 日期不得早于购买日期 / 剩余可排课时为 0，任一不满足返回用户可读错误文案。
     *
     * 额度校验统一走 ValidateScheduleUseCase 三要素公式：
     * 剩余可排课时 = 总课时(活跃包剩余之和) - 已消耗(已签退) - 待消耗(占位)。
     * isTrial=true 跳过购买日期校验与余额校验，仅保留"生效日期不早于今天"。
     */
    suspend fun validate(form: ScheduleForm, weekStart: Date, dateFormatter: DateTimeFormatter): String? {
        if (form.studentName.isBlank()) return null
        val zone = ZoneId.systemDefault()
        val weekStartLocal = weekStart.toInstant().atZone(zone).toLocalDate()
        val days = if (form.daysOfWeek.isNotEmpty()) form.daysOfWeek.sorted() else listOf(form.dayOfWeek)
        for ((index, dow) in days.withIndex()) {
            val dateStr = weekStartLocal.plusDays((dow - 1).toLong()).format(dateFormatter)
            // 允许排课日期早于今天（支持补录/恢复历史排课），不再拦截过去日期
            // 首次自动体验课：第一天跳过校验（不消耗课时包），其余天正常校验
            val isDayTrial = if (form.isFirstLessonAutoTrial) index == 0 else form.isTrial
            if (isDayTrial) continue
            // 核心校验 1：排课日期不得早于首次购买日期（按生成当天的实际日期校验）
            if (!validateSchedule.isDateValid(form.studentName, dateStr)) {
                return "无法排课：所选日期早于购买日期"
            }
            // 核心校验 2：剩余可排课时（剩余课时 - 待消耗）> 0 才允许排课；额度用尽时附带明细
            val breakdown = validateSchedule.quotaBreakdown(form.studentName, dateStr)
            if (breakdown.available <= 0) {
                return "无法排课：该学员课时额度已用完（剩余可排课时为 0）。\n" +
                    "剩余课时=${breakdown.totalRemaining} 待消耗=${breakdown.pending}\n" +
                    "请先核对该学员的课时包使用情况，或检查是否有尚未签退的占位课程。"
            }
        }
        return null
    }

    /**
     * 保存排课（新建或更新），支持训练内容/颜色/上课器材完整字段。
     *
     * 多选周几支持（新建模式）：
     * - [ScheduleForm.daysOfWeek] 非空时，按所选的多个周几循环创建多条 Schedule
     * - 例如用户选了周一/三/五，会创建 3 条 Schedule 记录，避免重复添加相同课程
     * - 编辑模式仅编辑单条记录的 [ScheduleForm.dayOfWeek]，忽略 daysOfWeek
     *
     * forceReplace 强制替换：
     * - forceReplace=false（默认）：捕获 [CoachConflictException] 时通过 [SaveContext.onConflict]
     *   向 UI 推送冲突事件，由 UI 弹出"强制替换"确认框
     * - forceReplace=true：先删除冲突排课再写入，不再触发冲突检测
     */
    suspend fun save(form: ScheduleForm, forceReplace: Boolean, ctx: SaveContext) {
        val onToast = ctx.onToast
        // 小班课：studentName 为空是正常的（多选场景），校验改为检查是否选了学员
        if (form.isGroupClass) {
            if (form.groupStudentNames.isEmpty()) { onToast("请至少选择 2 名学员"); return }
        } else {
            if (form.studentName.isBlank()) { onToast("请选择学员或填写体验课学员姓名"); return }
        }
        if (form.startTime.isBlank()) { onToast("请填写上课时间"); return }
        try {
            val editing = ctx.editing
            val coachKey = form.coachName.ifBlank { "默认教练" }

            // 统一校验：ValidateScheduleUseCase（购买日期前置 / 额度封顶），失败直接拦截不入库
            val validationError = validate(form, ctx.weekStart, ctx.dateFormatter)
            if (validationError != null) {
                onToast(validationError)
                return
            }

            // 保存时间/地点记忆（重复则更新 updatedAt）
            memoryRepo.saveMemory(coachKey, "time", form.startTime.trim())
            if (form.location.isNotBlank()) {
                memoryRepo.saveMemory(coachKey, "location", form.location.trim())
            }
            // 保存最近操作的上课周几到记忆，下次新建排课时默认选中
            val targetDaysForMemory = if (form.daysOfWeek.isNotEmpty()) {
                form.daysOfWeek.sorted()
            } else {
                listOf(form.dayOfWeek)
            }
            targetDaysForMemory.forEach { dow ->
                memoryRepo.saveMemory(coachKey, "dayOfWeek", dow.toString())
            }

            if (editing == null) {
                // === 小班课：批量创建同 groupScheduleId 的排课记录 ===
                if (form.isGroupClass) {
                    val groupScheduleId = java.util.UUID.randomUUID().toString().take(8)
                    val targetDays = if (form.daysOfWeek.isNotEmpty()) {
                        form.daysOfWeek.sorted()
                    } else {
                        listOf(form.dayOfWeek)
                    }
                    for (dayOfWeek in targetDays) {
                        for ((index, name) in form.groupStudentNames.withIndex()) {
                            val sid = form.groupStudentIds.toList().getOrNull(index)
                            val schedule = Schedule(
                                studentName = name,
                                studentId = sid,
                                coachName = form.coachName,
                                dayOfWeek = dayOfWeek,
                                startTime = form.startTime,
                                durationMinutes = form.durationMinutes,
                                location = form.location,
                                lessonType = form.lessonType,
                                startDate = ctx.today,
                                endDate = "",
                                isLongTerm = form.isLongTerm,
                                isTrial = false,
                                content = scheduleRepo.contentToJson(form.content),
                                contentImages = scheduleRepo.imagesToJson(form.contentImages),
                                color = form.color,
                                note = form.note,
                                equipment = scheduleRepo.equipmentToJson(form.equipment),
                                groupScheduleId = groupScheduleId
                            )
                            val ok = opRepo.saveSchedule(schedule)
                            if (!ok) {
                                onToast("保存失败：学员 $name 数据库写入异常")
                                return
                            }
                        }
                    }
                    val countText = if (targetDays.size > 1) "（${targetDays.size}天）" else ""
                    onToast("小班课已添加（${form.groupStudentNames.size}名学员）$countText")
                    if (form.isLongTerm) ctx.onLongTermEnsure()
                } else {
                    // 新建模式：多选周几时循环创建多条 Schedule，避免重复添加
                    val targetDays = if (form.daysOfWeek.isNotEmpty()) {
                        form.daysOfWeek.sorted()
                    } else {
                        listOf(form.dayOfWeek)
                    }
                    // 首次自动体验课：检查学员是否确无正式课记录，有则降级为普通排课
                    val autoTrialActive = form.isFirstLessonAutoTrial &&
                        !opRepo.hasFormalLessonsDual(form.studentId, form.studentName)
                    for ((dayIndex, dayOfWeek) in targetDays.withIndex()) {
                        // 首次自动体验课：第一天 isTrial=true（保留 studentId），其余天 isTrial=false
                        val dayIsTrial = if (autoTrialActive) dayIndex == 0 else form.isTrial
                        // 常规体验课（form.isTrial）studentId 置 null；自动体验课保留学员关联
                        val dayStudentId = if (form.isTrial) null else form.studentId
                        if (forceReplace) {
                            // 强制替换分支——先删除冲突排课再写入（日期已由 validate 校验）。
                            // 体验课：studentId 强制 null（未注册学员）
                            scheduleRepo.addScheduleForce(
                                studentName = form.studentName,
                                studentId = dayStudentId,
                                coachName = form.coachName,
                                dayOfWeek = dayOfWeek,
                                startTime = form.startTime,
                                durationMinutes = form.durationMinutes,
                                location = form.location,
                                lessonType = form.lessonType,
                                isLongTerm = form.isLongTerm,
                                isTrial = dayIsTrial,
                                content = form.content,
                                contentImages = form.contentImages,
                                color = form.color,
                                note = form.note,
                                equipment = form.equipment
                            )
                        } else {
                            // === 新建排课统一走 saveSchedule（Repository 层强制校验） ===
                            // 与编辑分支共用同一入口：日期不得早于首次购买（IllegalArgumentException）、
                            // 剩余可排课时（三要素公式）> 0（ScheduleQuotaExceededException），
                            // 业务异常直接上抛由 UI 显示明确文案，杜绝"额度用完仍排课"。
                            // 体验课（form.isTrial）：studentId 强制 null（未注册学员），跳过校验
                            val schedule = Schedule(
                                studentName = form.studentName,
                                studentId = dayStudentId,
                                coachName = form.coachName,
                                dayOfWeek = dayOfWeek,
                                startTime = form.startTime,
                                durationMinutes = form.durationMinutes,
                                location = form.location,
                                lessonType = form.lessonType,
                                // 手动排课生效日默认今天（与 ScheduleRepository.addSchedule 原行为一致）
                                startDate = ctx.today,
                                endDate = "",
                                isLongTerm = form.isLongTerm,
                                isTrial = dayIsTrial,
                                content = scheduleRepo.contentToJson(form.content),
                                contentImages = scheduleRepo.imagesToJson(form.contentImages),
                                color = form.color,
                                note = form.note,
                                equipment = scheduleRepo.equipmentToJson(form.equipment)
                            )
                            val ok = opRepo.saveSchedule(schedule)
                            if (!ok) {
                                onToast("保存失败：数据库写入异常，请查看 Logcat (tag=DataFlow)")
                                return
                            }
                        }
                    }
                    val countText = if (targetDays.size > 1) "（${targetDays.size}天）" else ""
                    onToast(if (forceReplace) {
                        "已强制替换冲突排课并添加$countText"
                    } else if (form.isLongTerm) {
                        "长期课程已添加$countText（每周自动生成课记录）"
                    } else {
                        "课程已添加$countText"
                    })
                    // === 保存长期排课后立即触发占位生成 ===
                    // 确保长期排课生成器能正确反映刚占用的课时（额度同步扣减，避免超额）
                    if (form.isLongTerm) ctx.onLongTermEnsure()
                }
            } else {
                // === 优先使用 form.id 作为更新主键 ===
                // 双重保险：editing.id 来自 startEdit 异步加载，理论上不丢失；
                // 但 form.id 来自 ScheduleEditDialog 的 buildForm，是 UI 层显式传入的
                val effectiveId = form.id.ifBlank { editing.id }
                val updated = editing.copy(
                    id = effectiveId,
                    studentName = form.studentName,
                    // 编辑模式未重选学员时保留原 studentId，避免被 null 覆盖清空
                    // 体验课：studentId 强制 null（未注册学员无软关联）
                    studentId = if (form.isTrial) null else (form.studentId ?: editing.studentId),
                    coachName = form.coachName,
                    dayOfWeek = form.dayOfWeek,
                    startTime = form.startTime,
                    durationMinutes = form.durationMinutes,
                    location = form.location,
                    lessonType = form.lessonType,
                    isLongTerm = form.isLongTerm,
                    isTrial = form.isTrial,
                    content = scheduleRepo.contentToJson(form.content),
                    contentImages = scheduleRepo.imagesToJson(form.contentImages),
                    color = form.color,
                    note = form.note,
                    equipment = scheduleRepo.equipmentToJson(form.equipment)
                )
                if (forceReplace) {
                    // 强制更新分支——先删除冲突排课再写入
                    scheduleRepo.updateScheduleForce(updated)
                    onToast("已强制替换冲突排课并更新")
                } else {
                    // === 调用 OperationRepository.saveSchedule 走智能判断 ===
                    // 存在则 update，不存在则 insert，避免静默失败
                    try {
                        val ok = opRepo.saveSchedule(updated)
                        if (!ok) {
                            onToast("保存失败：数据库写入异常，请查看 Logcat (tag=DataFlow)")
                            android.util.Log.e("DataFlow",
                                "saveSchedule 返回 false：id=${updated.id}, " +
                                    "student=${updated.studentName}, " +
                                    "contentLen=${updated.content.length}")
                            return
                        }
                    } catch (e: IllegalArgumentException) {
                        // === 排课日期早于购买日期的业务校验异常 ===
                        // opRepo.saveSchedule 在事务前抛出，异常消息即用户可读文案，
                        // 直接展示，避免被"保存失败："前缀污染
                        android.util.Log.w("DataFlow",
                            "saveSchedule 业务校验拦截：id=${updated.id}, " +
                                "student=${updated.studentName}, ${e.message}")
                        onToast(e.message ?: "无法排课：日期校验失败")
                        return
                    } catch (e: Exception) {
                        // 二次防护：opRepo.saveSchedule 内部已 try-catch 返回 false，
                        // 但仍兜底捕获以防 NPE / IllegalState 等 RuntimeException 逃逸
                        android.util.Log.e("DataFlow",
                            "saveSchedule 抛出异常：id=${updated.id}, " +
                                "student=${updated.studentName}", e)
                        onToast("保存失败：${e.message ?: e.javaClass.simpleName}")
                        return
                    }
                    onToast("课程已更新")
                }
            }
            // 保存成功后向 UI 推送事件，由 UI 调用 onSaved() 关闭弹窗
            ctx.onSaved()
        } catch (e: CoachConflictException) {
            // 教练时间冲突：根据 forceReplace 决定走"提示用户"还是"已被强制度过"分支
            // 理论上 forceReplace=true 不会再抛此异常，但兜底处理以防万一
            if (forceReplace) {
                onToast("强制替换失败：${e.userMessage}")
            } else {
                // 向 UI 推送冲突事件，由 UI 弹出"强制替换"确认框
                // 不关闭编辑弹窗，保留用户已填表单
                ctx.onConflict(e)
            }
        } catch (e: ScheduleQuotaExceededException) {
            // === 额度已满业务异常（三要素公式）===
            // Repository 层 saveSchedule 上抛，直接显示明确文案，不走"保存失败"笼统提示
            android.util.Log.w("OperationVM",
                "saveSchedule 额度校验拦截：${e.message}")
            onToast(e.message ?: "无法排课：该学员课时额度已满")
        } catch (e: IllegalArgumentException) {
            // === 排课日期早于购买日期的业务校验异常 ===
            // opRepo.saveSchedule 在事务前抛出，异常消息即用户可读文案，直接展示
            android.util.Log.w("OperationVM",
                "saveSchedule 日期校验拦截：${e.message}")
            onToast(e.message ?: "无法排课：日期校验失败")
        } catch (e: Exception) {
            // === 不再黑盒吞掉异常，向用户显示具体失败原因 ===
            android.util.Log.e("OperationVM",
                "saveSchedule 失败：${e.message}", e)
            onToast("保存失败：${e.message ?: e.javaClass.simpleName}")
        }
    }
}
