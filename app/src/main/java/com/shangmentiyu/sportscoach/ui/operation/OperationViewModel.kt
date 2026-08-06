package com.shangmentiyu.sportscoach.ui.operation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.ExerciseItem
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.model.ScheduleMemory
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.CoachConflictException
import com.shangmentiyu.sportscoach.data.repo.CoachRepository
import com.shangmentiyu.sportscoach.data.repo.LessonPackageRepository
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.ScheduleMemoryRepository
import com.shangmentiyu.sportscoach.data.repo.ScheduleRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import com.shangmentiyu.sportscoach.domain.scheduling.CanScheduleLessonsUseCase
import com.shangmentiyu.sportscoach.domain.scheduling.SchedulingDecision
import com.shangmentiyu.sportscoach.ui.schedule.ScheduleForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 运营管理 ViewModel（协调层）。
 *
 * 统一管理四个子领域（v21 拆分后采用独立 Repository 注入）：
 * - 排课（Schedule）：通过 [scheduleRepo] 提供 CRUD + 训练内容 JSON 解析
 * - 课程包（LessonPackage）：通过 [pkgRepo] 提供余额追踪、续费提醒、消课
 * - 教练（Coach）：通过 [coachRepo] 提供增删改查
 * - 训练周期 & 阶段总结：通过 [opRepo] 提供跨模块聚合计算
 *
 * 重构说明（v21）：
 * - 课时包相关方法改用 [pkgRepo]，教练相关方法改用 [coachRepo]
 * - [opRepo] 保留 TrainingCycle CRUD / StageSummary / 长期排课辅助方法
 *   （这些跨模块业务逻辑仍归属 OperationRepository）
 * - 老调用方（HomeViewModel / StageSummaryViewModel 等）保持对 [OperationRepository] 的引用
 *
 * 排课功能合并自原 ScheduleViewModel，通过 [scheduleRepo] 提供
 * 训练内容 JSON 解析与完整 CRUD，使运营管理成为唯一的排课入口。
 */
class OperationViewModel(
    private val opRepo: OperationRepository,
    private val studentRepo: StudentRepository,
    private val scheduleRepo: ScheduleRepository,
    private val memoryRepo: ScheduleMemoryRepository,
    private val pkgRepo: LessonPackageRepository,
    private val coachRepo: CoachRepository,
    // v46 架构层二：排课三段硬决策 UseCase（从 ViewModel 抽离的领域逻辑）
    private val schedulingDecision: CanScheduleLessonsUseCase
) : ViewModel() {

    /**
     * 日期格式化工具：[DateTimeFormatter] 不可变且线程安全，可作为成员变量共享。
     */
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

    private fun todayStr(): String = LocalDate.now().format(dateFormatter)

    // === 排课 ===
    val schedules: StateFlow<List<Schedule>> = opRepo.getActiveSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === v48 终极打磨：排课列表首帧加载标记（骨架屏） ===
    private val _schedulesLoaded = MutableStateFlow(false)
    val schedulesLoaded: StateFlow<Boolean> = _schedulesLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            schedules.first()
            _schedulesLoaded.value = true
        }
    }

    /** 当前选中的周几（1-7），默认今天 */
    private val _selectedDay = MutableStateFlow(getTodayDayOfWeek())
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    /** 当前周的起始日期（周一） */
    private val _weekStart = MutableStateFlow(getWeekStart())
    val weekStart: StateFlow<Date> = _weekStart.asStateFlow()

    // === 课程包 ===
    // === v26 优化6：显示所有状态的课时包（活跃/已过期/已退费/已用完）===
    // 原 getActivePackages() 只显示"活跃"状态的包，导致教练不清楚为什么某学员"没课了"。
    // 改为 getAllPackages() 显示全部，通过 PackageCard 的状态角标区分。
    // renewalAlerts 仍然只过滤活跃包，避免已退费包被错误提醒续费。
    val packages: StateFlow<List<LessonPackage>> = pkgRepo.getAllPackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 需要续费提醒的课程包 */
    // === v26 优化6：renewalAlerts 只对"活跃"状态包做提醒，避免已退费/已过期包误触发 ===
    val renewalAlerts: StateFlow<List<LessonPackage>> = packages.map { list ->
        list.filter { it.status == "活跃" && it.needsRenewal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === 教练 ===
    val coaches: StateFlow<List<Coach>> = coachRepo.getActiveCoaches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === 学员列表（用于新增排课/课程包时选择） ===
    val students: StateFlow<List<Student>> = studentRepo.getAllStudents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === 排课记忆（时间/地点历史下拉选择） ===
    /** 全局最近用过的上课时间记忆（不限教练，按 updatedAt 降序），最多 20 条 */
    val timeMemories: StateFlow<List<ScheduleMemory>> = memoryRepo.getRecentMemories("time", 20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 全局最近用过的上课地点记忆（不限教练，按 updatedAt 降序），最多 20 条 */
    val locationMemories: StateFlow<List<ScheduleMemory>> = memoryRepo.getRecentMemories("location", 20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === v24 优化6：最近操作的上课日期（周几）记忆 ===
    /** 全局最近用过的上课周几记忆（value = "1"~"7"，按 updatedAt 降序），最多 5 条 */
    val dayOfWeekMemories: StateFlow<List<ScheduleMemory>> = memoryRepo.getRecentMemories("dayOfWeek", 5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === v24 优化2：余额不足主动 UI 提示 ===
    /** 余额不足警告列表（每条形如 "陈书楠 周五 余额不足"），供 ScheduleScreen 顶部 Alert Banner 显示 */
    private val _noBalanceWarnings = MutableStateFlow<List<String>>(emptyList())
    val noBalanceWarnings: StateFlow<List<String>> = _noBalanceWarnings.asStateFlow()

    /** 清空余额不足警告（UI 消费后调用） */
    fun clearNoBalanceWarnings() {
        _noBalanceWarnings.value = emptyList()
    }

    // === 操作结果提示 ===
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /**
     * === v25 优化5：教练时间冲突事件流（一次性事件，用于 UI 弹出"强制替换"确认框）===
     *
     * 设计目的：
     * - 使用 SharedFlow 而非 StateFlow，因为冲突事件是"一次性"的，
     *   不需要保留最新状态（StateFlow 会缓存最新值，新订阅者会立即收到旧事件）
     * - extraBufferCapacity = 1 防止在 UI 未订阅时丢失事件
     * - replay = 0：新订阅者不接收历史事件，仅接收订阅后产生的新事件
     *
     * 触发时机：[saveSchedule] 在非强制模式下捕获 [CoachConflictException] 时 emit
     * 消费方：[com.shangmentiyu.sportscoach.ui.schedule.ScheduleEditDialog]
     *         收到后弹出 GlassAlertDialog 询问是否强制替换
     */
    private val _coachConflictEvent =
        MutableSharedFlow<CoachConflictException>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
    val coachConflictEvent: SharedFlow<CoachConflictException> =
        _coachConflictEvent.asSharedFlow()

    /**
     * === v25 优化5：保存成功事件流（一次性事件，用于 UI 关闭编辑弹窗）===
     *
     * 设计目的：
     * - 替代原 [saveSchedule] 后立即 onSaved() 的同步关闭行为
     * - 让"成功才关闭、冲突弹框、失败保持打开"三种分支能在 UI 层清晰区分
     *
     * 触发时机：[saveSchedule] 完成写入（含强制替换分支）后 emit Unit
     * 消费方：[com.shangmentiyu.sportscoach.ui.schedule.ScheduleEditDialog]
     *         收到后调用 onSaved() 关闭弹窗
     */
    private val _saveSuccessEvent =
        MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
    val saveSuccessEvent: SharedFlow<Unit> =
        _saveSuccessEvent.asSharedFlow()

    /**
     * === v24 优化4：全局协程异常捕获 ===
     *
     * 应用级异常处理器，所有 viewModelScope.launch 均自动挂载此 Handler，
     * 拦截数据库死锁、IO 异常、JSON 解析错误等，避免 App 闪退。
     * - 异常落盘：通过 [com.shangmentiyu.sportscoach.core.CrashHandler.writeLog] 同步写入 crash_logs/
     * - UI 反馈：通过 [_toast] 推送轻量提示，避免静默失败
     */
    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.core.CoroutineExt.createAppExceptionHandler(
            toastSink = _toast,
            contextTag = "OperationViewModel"
        )

    /**
     * 安全启动协程：自动挂载 [appExceptionHandler]，未捕获异常不会导致 App 崩溃。
     *
     * 与 viewModelScope.launch 区别：
     * - 自动捕获异常 → Toast 提示 + 落盘日志
     * - CancellationException 不视为异常，正常透传
     *
     * 闭包签名兼容 [viewModelScope.launch]：lambda 接收 CoroutineScope，
     * 可在其中调用 `coroutineContext` / `launch` 等，便于直接替换原 launch 调用。
     */
    private fun safeLaunch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        viewModelScope.launch(appExceptionHandler) {
            try {
                block(this)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            }
        }
    }

    /**
     * 长期排课生成互斥锁：防止短时间内多次调用 ensureLongTermLessonsForWeek
     * （如反复切周/进入排课页）导致多条 Lesson 并发写入同一学员同一天同一时间，
     * 引发 SQLite 写锁竞争甚至死锁。
     *
     * 与 OperationRepository.consumeMutex 不同：本锁保护"生成阶段"，
     * Repository 内的 consumeMutex 保护"消课阶段"，两者互不干扰。
     */
    private val longTermScheduleMutex = Mutex()

    fun showToast(msg: String) { _toast.value = msg }
    fun clearToast() { _toast.value = null }

    /**
     * 删除一条排课记忆（时间/地点历史下拉项，UI 长按触发）。
     * 删除后 timeMemories/locationMemories 通过 Room Flow 自动刷新。
     */
    fun deleteMemory(mem: ScheduleMemory) {
        safeLaunch {
            try {
                memoryRepo.deleteMemory(mem.coachName, mem.field, mem.value)
                _toast.value = "已删除历史记忆「${mem.value}」"
            } catch (e: Exception) {
                _toast.value = "删除记忆失败：${e.message ?: "未知异常"}"
            }
        }
    }

    // === 排课操作 ===
    fun selectDay(day: Int) { _selectedDay.value = day }

    /** 切换到上一周/下一周 */
    fun shiftWeek(days: Int) {
        val cal = Calendar.getInstance()
        cal.time = _weekStart.value
        cal.add(Calendar.DATE, days)
        _weekStart.value = cal.time
    }

    /**
     * 回到本周（重置 weekStart 为本周周一）。
     * 用于"今天"按钮快速回到当前周。
     */
    fun resetToThisWeek() {
        _weekStart.value = getWeekStart()
    }

    /** 当前编辑中的排课（null=新建模式） */
    private val _editingSchedule = MutableStateFlow<Schedule?>(null)
    val editingSchedule: StateFlow<Schedule?> = _editingSchedule.asStateFlow()

    /** 进入新建模式 */
    fun startCreate() { _editingSchedule.value = null }

    /**
     * 进入编辑模式：异步加载原数据。
     *
     * - 明确切换到 [Dispatchers.IO] 执行数据库查询，避免阻塞主线程
     * - 查询返回 null（记录不存在/已损坏）时通过 toast 提示用户，不更新 editingSchedule
     * - 查询抛异常时捕获并提示，避免协程崩溃导致 UI 一直卡在"加载中"
     */
    fun startEdit(id: String) {
        safeLaunch {
            try {
                val schedule = withContext(Dispatchers.IO) { scheduleRepo.getById(id) }
                if (schedule == null) {
                    _toast.value = "课程数据不存在或已损坏"
                } else {
                    _editingSchedule.value = schedule
                }
            } catch (e: Exception) {
                _toast.value = "加载课程失败：${e.message}"
            }
        }
    }

    /** 退出编辑 */
    fun cancelEdit() { _editingSchedule.value = null }

    /**
     * 保存排课（新建或更新），支持训练内容/颜色/上课器材完整字段。
     *
     * 多选周几支持（新建模式）：
     * - [ScheduleForm.daysOfWeek] 非空时，按所选的多个周几循环创建多条 Schedule
     * - 例如用户选了周一/三/五，会创建 3 条 Schedule 记录，避免重复添加相同课程
     * - 编辑模式仅编辑单条记录的 [ScheduleForm.dayOfWeek]，忽略 daysOfWeek
     *
     * 新增功能：
     * - [ScheduleForm.isLongTerm]：勾选长期后，每周自动生成对应时间的课时记录
     * - 上课时间/地点会自动保存到 schedule_memory 表，供下次排课下拉选择
     *
     * === v25 优化5：forceReplace 强制替换 ===
     *
     * - forceReplace=false（默认）：捕获 [CoachConflictException] 时通过 [_coachConflictEvent]
     *   向 UI 推送冲突事件，由 UI 弹出"强制替换"确认框；用户取消则放弃保存，确认则用
     *   forceReplace=true 重新调用本方法走强制分支
     * - forceReplace=true：直接调用 [ScheduleRepository.addScheduleForce] /
     *   [ScheduleRepository.updateScheduleForce]，先删除冲突排课再写入，不再触发冲突检测
     *
     * @param form 表单数据
     * @param forceReplace 是否强制替换已有冲突排课（用户在确认框中选择"确认替换"时传 true）
     */
    fun saveSchedule(form: ScheduleForm, forceReplace: Boolean = false) {
        if (form.studentName.isBlank()) { _toast.value = "请选择学员"; return }
        if (form.startTime.isBlank()) { _toast.value = "请填写上课时间"; return }
        safeLaunch {
            try {
                val editing = _editingSchedule.value
                val coachKey = form.coachName.ifBlank { "默认教练" }

                // === Bug 1 修复：排课日期校验（从源头杜绝过去日期的排课）===
                // 用户明确要求：若用户选择的 startDate 小于今天，直接拦截并提示
                // "排课生效日期不能早于今天，请在未来的日期排课。"
                //
                // 与原"自动顺延到下周"逻辑相比，本实现改为"直接拦截"：
                // - 原逻辑：本周日期已过则顺延到下周同 dayOfWeek（隐性纠正用户输入）
                // - 新逻辑：本周日期已过则直接拒绝，由用户主动切到未来周再排
                //   → 避免用户在历史周查看时误排过去日期
                //
                // 同时保留课时包生效日期校验：
                // 学员在排课日期必须拥有有效课时包（purchaseDate <= dateStr && 未过期 && 剩余 > 0）
                val targetDaysForCheck = if (form.daysOfWeek.isNotEmpty()) {
                    form.daysOfWeek.sorted()
                } else {
                    listOf(form.dayOfWeek)
                }
                val todayLocal = LocalDate.now()
                val zone = java.time.ZoneId.systemDefault()
                val weekStartLocal = _weekStart.value.toInstant().atZone(zone).toLocalDate()
                for (dow in targetDaysForCheck) {
                    // weekStart 是周一，dayOfWeek 1=周一 ... 7=周日
                    val scheduleDate = weekStartLocal.plusDays((dow - 1).toLong())
                    val dateStr = scheduleDate.format(dateFormatter)
                    // === 强制边界校验：排课生效日期不能早于今天 ===
                    if (scheduleDate.isBefore(todayLocal)) {
                        _toast.value = "排课生效日期不能早于今天，请在未来的日期排课。"
                        return@safeLaunch
                    }
                    // === v46 综合整治：购买日期前置拦截 ===
                    // 排课日期早于学员最早购买课时包日期 → 直接中断保存，绝不入库
                    val earliestPurchase = withContext(Dispatchers.IO) {
                        opRepo.earliestPurchaseDateOf(form.studentName)
                    }
                    if (earliestPurchase != null && dateStr < earliestPurchase) {
                        _toast.value = "无法排课：该日期早于购买日期"
                        return@safeLaunch
                    }
                    val effectiveRemaining = withContext(Dispatchers.IO) {
                        pkgRepo.getEffectiveRemainingLessons(form.studentName, dateStr)
                    }
                    if (effectiveRemaining <= 0) {
                        _toast.value = "无法排课：该学员在排课日期前尚未拥有有效课时包。"
                        return@safeLaunch
                    }
                }

                // 保存时间/地点记忆（重复则更新 updatedAt）
                memoryRepo.saveMemory(coachKey, "time", form.startTime.trim())
                if (form.location.isNotBlank()) {
                    memoryRepo.saveMemory(coachKey, "location", form.location.trim())
                }
                // v24 优化6：保存最近操作的上课周几到记忆，下次新建排课时默认选中
                val targetDaysForMemory = if (form.daysOfWeek.isNotEmpty()) {
                    form.daysOfWeek.sorted()
                } else {
                    listOf(form.dayOfWeek)
                }
                targetDaysForMemory.forEach { dow ->
                    memoryRepo.saveMemory(coachKey, "dayOfWeek", dow.toString())
                }

                if (editing == null) {
                    // 新建模式：多选周几时循环创建多条 Schedule，避免重复添加
                    val targetDays = if (form.daysOfWeek.isNotEmpty()) {
                        form.daysOfWeek.sorted()
                    } else {
                        listOf(form.dayOfWeek)
                    }
                    for (dayOfWeek in targetDays) {
                        if (forceReplace) {
                            // v25 优化5：强制替换分支——先删除冲突排课再写入
                            scheduleRepo.addScheduleForce(
                                studentName = form.studentName,
                                studentId = form.studentId,
                                coachName = form.coachName,
                                dayOfWeek = dayOfWeek,
                                startTime = form.startTime,
                                durationMinutes = form.durationMinutes,
                                location = form.location,
                                lessonType = form.lessonType,
                                isLongTerm = form.isLongTerm,
                                content = form.content,
                                contentImages = form.contentImages,
                                color = form.color,
                                note = form.note,
                                equipment = form.equipment
                            )
                        } else {
                            scheduleRepo.addSchedule(
                                studentName = form.studentName,
                                studentId = form.studentId,
                                coachName = form.coachName,
                                dayOfWeek = dayOfWeek,
                                startTime = form.startTime,
                                durationMinutes = form.durationMinutes,
                                location = form.location,
                                lessonType = form.lessonType,
                                isLongTerm = form.isLongTerm,
                                content = form.content,
                                contentImages = form.contentImages,
                                color = form.color,
                                note = form.note,
                                equipment = form.equipment
                            )
                        }
                    }
                    val countText = if (targetDays.size > 1) "（${targetDays.size}天）" else ""
                    _toast.value = if (forceReplace) {
                        "已强制替换冲突排课并添加$countText"
                    } else if (form.isLongTerm) {
                        "长期课程已添加$countText（每周自动生成课记录）"
                    } else {
                        "课程已添加$countText"
                    }
                } else {
                    // === v33 数据流加固：优先使用 form.id 作为更新主键 ===
                    // 双重保险：editing.id 来自 startEdit 异步加载，理论上不丢失；
                    // 但 form.id 来自 ScheduleEditDialog 的 buildForm，是 UI 层显式传入的，
                    // 即使 editing 因协程时序问题为 null 也可走更新路径（外层 else 分支保证 editing 非 null，
                    // 这里 form.id 主要用于"显式优于隐式"的可观测性，便于 Logcat 追踪）
                    val effectiveId = form.id.ifBlank { editing.id }
                    val updated = editing.copy(
                        id = effectiveId,
                        studentName = form.studentName,
                        // v46：编辑模式未重选学员时保留原 studentId，避免被 null 覆盖清空
                        studentId = form.studentId ?: editing.studentId,
                        coachName = form.coachName,
                        dayOfWeek = form.dayOfWeek,
                        startTime = form.startTime,
                        durationMinutes = form.durationMinutes,
                        location = form.location,
                        lessonType = form.lessonType,
                        isLongTerm = form.isLongTerm,
                        content = scheduleRepo.contentToJson(form.content),
                        contentImages = scheduleRepo.imagesToJson(form.contentImages),
                        color = form.color,
                        note = form.note,
                        equipment = scheduleRepo.equipmentToJson(form.equipment)
                    )
                    if (forceReplace) {
                        // v25 优化5：强制更新分支——先删除冲突排课再写入
                        scheduleRepo.updateScheduleForce(updated)
                        _toast.value = "已强制替换冲突排课并更新"
                    } else {
                        // === v33 数据流加固：调用 OperationRepository.saveSchedule 走智能判断 ===
                        // 原直接调用 scheduleRepo.updateSchedule，若 updated.id 在数据库中不存在
                        // 会返回 0（affected rows = 0），但用户无感知
                        // 改用 opRepo.saveSchedule 内部判断：存在则 update，不存在则 insert
                        // 并通过 Log.e("DataFlow") 输出异常堆栈，避免静默失败
                        try {
                            val ok = opRepo.saveSchedule(updated)
                            if (!ok) {
                                _toast.value = "保存失败：数据库写入异常，请查看 Logcat (tag=DataFlow)"
                                android.util.Log.e("DataFlow",
                                    "saveSchedule 返回 false：id=${updated.id}, " +
                                        "student=${updated.studentName}, " +
                                        "contentLen=${updated.content.length}")
                                return@safeLaunch
                            }
                        } catch (e: IllegalArgumentException) {
                            // === Bug 1 修复：排课日期早于购买日期的业务校验异常 ===
                            // opRepo.saveSchedule 在事务前抛出，异常消息即用户可读文案，
                            // 直接展示，避免被"保存失败："前缀污染
                            android.util.Log.w("DataFlow",
                                "saveSchedule 业务校验拦截：id=${updated.id}, " +
                                    "student=${updated.studentName}, ${e.message}")
                            _toast.value = e.message ?: "无法排课：日期校验失败"
                            return@safeLaunch
                        } catch (e: Exception) {
                            // 二次防护：opRepo.saveSchedule 内部已 try-catch 返回 false，
                            // 但仍兜底捕获以防 NPE / IllegalState 等 RuntimeException 逃逸
                            android.util.Log.e("DataFlow",
                                "saveSchedule 抛出异常：id=${updated.id}, " +
                                    "student=${updated.studentName}", e)
                            _toast.value = "保存失败：${e.message ?: e.javaClass.simpleName}"
                            return@safeLaunch
                        }
                        _toast.value = "课程已更新"
                    }
                }
                _editingSchedule.value = null
                // v25 优化5：保存成功后向 UI 推送事件，由 UI 调用 onSaved() 关闭弹窗
                _saveSuccessEvent.tryEmit(Unit)
            } catch (e: CoachConflictException) {
                // 教练时间冲突：根据 forceReplace 决定走"提示用户"还是"已被强制度过"分支
                // 理论上 forceReplace=true 不会再抛此异常，但兜底处理以防万一
                if (forceReplace) {
                    _toast.value = "强制替换失败：${e.userMessage}"
                } else {
                    // v25 优化5：向 UI 推送冲突事件，由 UI 弹出"强制替换"确认框
                    // 不关闭编辑弹窗，保留用户已填表单
                    _coachConflictEvent.tryEmit(e)
                }
            } catch (e: Exception) {
                // === Bug 修复：不再黑盒吞掉异常，向用户显示具体失败原因 ===
                // 原代码只 catch CoachConflictException，其他异常（主键冲突 / 约束违反 /
                // JSON 序列化异常 / SQLite 异常等）会逃逸到 appExceptionHandler，
                // 用户只看到"操作异常已记录到日志（XXX）"，完全不知失败原因。
                // 现在此处显式 catch 并通过 toast 显示 e.message，便于用户排查。
                android.util.Log.e("OperationVM",
                    "saveSchedule 失败：${e.message}", e)
                _toast.value = "保存失败：${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /**
     * 长期排课自动生成本周课记录：
     * 进入排课页/每日计划页时检查所有 isLongTerm=true 的排课，
     * 为本周对应 dayOfWeek 自动生成一条 Lesson 记录（若当天尚无对应记录）。
     *
     * 约束（数据流正确性核心）：
     * - 仅对本周（_weekStart 起算 7 天内）的每个长期排课生成一次
     * - 同一学员+同一日期+同一 startTime 已存在 Lesson 时跳过，避免重复
     * - 自动生成的 Lesson 标记 lessonType = "长期自动"，便于区分手动签到
     * - 过去日期（dateStr < today）：不补排新记录，已生成的保留不删除（历史数据不回溯）
     * - 今天及以后日期（dateStr >= today）：必须同时满足以下条件才生成：
     *   1. 学员在 dateStr 当天有"有效课时包"
     *      （purchaseDate <= dateStr 且 expireDate 为空或 expireDate >= dateStr）
     *      → 修正：避免学员 7.24 买的课被排到 7.20
     *   2. 有效课时包剩余 > 从 dateStr 起长期自动未签退的课时数
     *      → v27 重构：原 [OperationRepository.countUnconsumedLessonsFrom] 按 packageId 为空判断，
     *        但新流程下签到时 packageId 必为空（签到不扣课时），会导致待签退课时被错误计入"未消费"
     *      → 改用 [OperationRepository.countLongTermPendingFrom]：只统计长期自动生成且 status != "已签退" 的课时
     *      → 确保排课精确到最后一节课，余额用完后停止生成
     */
    fun ensureLongTermLessonsForWeek() {
        safeLaunch {
            // 加锁：防止短时间多次触发导致并发写入同一学员同一天同一时间的 Lesson，
            // 进而引发 SQLite 写锁竞争/死锁。后续若已持锁会挂起等待，保证串行生成。
            longTermScheduleMutex.withLock {
                val allSchedules = scheduleRepo.getAllSchedulesOnce()
                val longTerm = allSchedules.filter { it.isActive && it.isLongTerm }
                if (longTerm.isEmpty()) return@withLock
                val cal = Calendar.getInstance()
                val weekStartCal = Calendar.getInstance().apply {
                    time = _weekStart.value
                    set(Calendar.HOUR_OF_DAY, 0)
                    clear(Calendar.MINUTE); clear(Calendar.SECOND); clear(Calendar.MILLISECOND)
                }
                // 线程安全：使用 [LocalDate] + [DateTimeFormatter] 替代 [SimpleDateFormat]
                val todayStr = LocalDate.now().format(dateFormatter)
                val zone = java.time.ZoneId.systemDefault()

                // v24 优化2：收集本周余额不足警告，生成完成后一次性推送给 UI
                val warnings = mutableListOf<String>()
                // 周几中文映射，用于生成可读警告文案
                val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

                // === v46 综合整治：三段绝对硬逻辑（余额 / 额度封顶 / 购买日前置） ===
                // 每步实时查询（studentId 双通道），不再依赖跨周累计缓存：
                // - 有 endDate 的排课由 endDate 边界控制，不参与额度跟踪
                // - 无 endDate 的永久长期排课走下方三段硬校验，杜绝超额排课

                // 遍历本周 7 天
                for (offset in 0..6) {
                    val dayCal = Calendar.getInstance().apply {
                        time = weekStartCal.time
                        add(Calendar.DATE, offset)
                    }
                    val dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK).let { if (it == 1) 7 else it - 1 }
                    // 修复：原代码使用未定义的 sdf，改为用 dateFormatter 格式化 LocalDate
                    val dateStr = dayCal.toInstant().atZone(zone).toLocalDate().format(dateFormatter)
                    // === 强制绝对边界校验（用户明确要求）===
                    // 第一行必须是日期边界过滤：绝对禁止为任何已过去的日期生成或重生成 Lesson 记录。
                    // 此处用 continue 跳过整个过去日期，比原 forEach 内 return@forEach 更早地截断流程，
                    // 避免对过去日期执行 hasLessonForScheduleOnDate 查重 / 余额查询 / 警告收集等无效操作。
                    if (dateStr < todayStr) continue

                    // === Bug 2 修复（防御性）===
                    // longTerm 列表虽已过滤 isLongTerm=true，但为防止未来重构时
                    // 误把非长期排课纳入循环（导致"不勾选长期排课也生成课时"的 Bug 复现），
                    // 在循环第一行强制判断 isLongTerm，非长期排课直接 continue。
                    // 同时这也是用户明确要求的修复点。
                    longTerm.filter { it.dayOfWeek == dayOfWeek }.forEach { sched ->
                        if (!sched.isLongTerm) return@forEach  // Bug 2 核心修复点

                        // === endDate 边界检查（按课时包排课的终止日期）===
                        // AutoScheduleFromPackage 创建的 Schedule 携带 endDate 字段，
                        // 表示"排到课时上完那一周为止"。超过 endDate 后必须停止生成新课时，
                        // 否则课时包已用尽但排课无限继续（用户报告的"排课依旧无限次数"Bug）。
                        // endDate 为空表示永久长期排课（原行为，不受影响）。
                        if (sched.endDate.isNotBlank() && dateStr > sched.endDate) {
                            return@forEach
                        }

                        // === startDate 购买日检查（购买当日之前不允许排课）===
                        // AutoScheduleFromPackage 创建的 Schedule 携带 startDate 字段（=购买日），
                        // 购买日之前的日期不生成 Lesson，确保"购买当日之前所有日期不允许排课"。
                        // startDate 为空表示旧数据或手动排课，不受此约束（向后兼容）。
                        if (sched.startDate.isNotBlank() && dateStr < sched.startDate) {
                            return@forEach
                        }

                        // 查重：同一学员+日期+时间已有记录则跳过
                        if (opRepo.hasLessonForScheduleOnDate(sched.studentName, dateStr, sched.startTime)) {
                            return@forEach
                        }

                        // === 有 endDate 的 Schedule：跳过余额检查，直接生成 ===
                        // AutoScheduleFromPackage 创建的 Schedule 携带 endDate 字段，
                        // endDate 已通过 AutoScheduleCalculator 基于剩余课时精确计算，
                        // endDate 之前生成的 Lesson 数量 = 创建时的 remainingLessons。
                        // 跳过余额检查的原因：
                        //   1. 余额检查中的 activePkgs 是缓存值，签退后不更新，
                        //      可能错误阻止 endDate 之前生成 Lesson
                        //   2. endDate 已保证数量正确，无需余额检查二次限制
                        //   3. "每排一天自动少一节课"由 endDate 边界检查实现：
                        //      超过 endDate 后不再生成 = 课时用完
                        // 无 endDate 的 Schedule（永久长期排课）继续使用余额检查。
                        if (sched.endDate.isNotBlank()) {
                            opRepo.generateLongTermLesson(sched, dateStr)
                            return@forEach
                        }

                        // === 无 endDate 的永久长期排课：领域 UseCase 三段决策（v46 架构层二）===
                        // 三段硬逻辑已抽离到 domain/scheduling/CanScheduleLessonsUseCase，
                        // ViewModel 只依据决策结果生成课时或提示文案，不再持有计算细节
                        val dayLabel = dayNames.getOrElse(dayOfWeek - 1) { "周$dayOfWeek" }
                        when (val decision = schedulingDecision.invoke(sched.studentName, dateStr, todayStr)) {
                            is SchedulingDecision.Allowed ->
                                opRepo.generateLongTermLesson(sched, dateStr)
                            is SchedulingDecision.NoPackage ->
                                if (dateStr >= todayStr) warnings.add("${sched.studentName} $dayLabel 无有效课时包")
                            is SchedulingDecision.QuotaExhausted ->
                                if (dateStr >= todayStr) warnings.add("${sched.studentName} $dayLabel 余额不足（剩余${decision.totalRemaining} 节）")
                            is SchedulingDecision.BeforePurchase ->
                                Unit // 购买日前置：静默跳过，不生成
                        }
                    }
                }

                // v24 优化2：将收集到的余额不足警告推送给 UI
                if (warnings.isNotEmpty()) {
                    // 去重：同一学员同一天可能因多个时间段的排课触发多次
                    _noBalanceWarnings.value = warnings.distinct()
                }
            }
        }
    }

    // === v24 优化1：撤销签到与恢复课时 ===
    /**
     * 撤销签到：删除误签的 Lesson 记录并恢复对应课时包的 usedLessons。
     *
     * 包装 [OperationRepository.undoCheckIn]，将结果通过 toast 反馈给 UI。
     * 事务原子性、并发安全、余额校验均在 Repository 层保证。
     *
     * @param lessonId 待撤销的课时 ID
     * @param studentName 学员姓名（兜底校验）
     * @param onDone 完成回调（主线程），参数为是否成功
     */
    fun undoCheckIn(
        lessonId: String,
        studentName: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        safeLaunch {
            try {
                val result = opRepo.undoCheckIn(lessonId, studentName)
                _toast.value = result.message
                onDone(result.success)
            } catch (e: Exception) {
                _toast.value = "撤销失败：${e.message ?: "未知异常"}"
                onDone(false)
            }
        }
    }

    /** 解析训练内容 JSON */
    fun parseContent(json: String): List<ExerciseItem> = scheduleRepo.parseContent(json)

    /** 解析上课器材 JSON */
    fun parseEquipment(json: String): List<String> = scheduleRepo.parseEquipment(json)

    /** 解析训练内容图片路径 JSON */
    fun parseImages(json: String): List<String> = scheduleRepo.parseImages(json)

    /**
     * === v28 优化3：异步获取学员体测弱项推荐的训练内容 ===
     *
     * 用于 [com.shangmentiyu.sportscoach.ui.schedule.ScheduleEditDialog]
     * 在新建模式下选中学员后自动预填训练内容。
     *
     * 业务逻辑：
     * - 取学员最近 10 条 lessons 中的体测成绩
     * - 识别弱项（"及格"或"不及格"的项目）
     * - 按维度（速度/力量/耐力/柔韧/灵敏）匹配预设训练模板
     * - BMI ≥ 24 时附加燃脂训练模板
     *
     * 调用方应在 IO 线程调用本方法（内部已有 try-catch 兜底，失败返回空列表）。
     *
     * @param studentName 学员姓名
     * @param latestBmi 学员当前 BMI 值（0 表示无数据）
     * @return 推荐的训练动作列表（最多 6 项）
     */
    suspend fun recommendTrainingContent(
        studentName: String,
        latestBmi: Float = 0f
    ): List<ExerciseItem> = opRepo.recommendTrainingContent(studentName, latestBmi)

    /**
     * === v29 优化2：异步获取该学员上一次有训练内容的课时记录 ===
     *
     * 用于 [com.shangmentiyu.sportscoach.ui.schedule.ScheduleEditDialog]
     * 中的"复制上次训练内容"按钮：教练选中学员后，点击按钮可一键拉取上次
     * 已录入的训练内容（content JSON）填充到当前表单，避免每天重复打字。
     *
     * 业务逻辑：
     * - 取该学员全部 lessons，按日期降序遍历
     * - 找到第一条 content 非空且非 "[]" 的记录即返回其解析后的列表
     * - 全部为空则返回空列表（UI 层据此显示"暂无可复用的训练内容"）
     *
     * 性能：使用 [opRepo.getLessonsByStudentOnce] 一次性查询 + 内存过滤，
     * 避免多次数据库访问。
     *
     * @param studentName 学员姓名
     * @return 上一次有训练内容的 ExerciseItem 列表；不存在则返回空列表
     */
    suspend fun fetchLastTrainingContent(studentName: String): List<ExerciseItem> {
        return try {
            val lessons = withContext(Dispatchers.IO) {
                opRepo.getLessonsByStudentOnce(studentName)
            }
            // 按日期降序查找第一条有 content 的记录
            val latest = lessons
                .sortedByDescending { "${it.date} ${it.time}" }
                .firstOrNull { it.content.isNotBlank() && it.content != "[]" && it.content != "null" }
                ?: return emptyList()
            parseContent(latest.content)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun toggleScheduleActive(schedule: Schedule) {
        safeLaunch {
            opRepo.updateSchedule(schedule.copy(isActive = !schedule.isActive))
            _toast.value = if (schedule.isActive) "已暂停" else "已启用"
        }
    }

    fun deleteSchedule(id: String) {
        safeLaunch {
            opRepo.deleteSchedule(id)
            _toast.value = "已删除"
        }
    }

    /**
     * 批量删除多条排课（多选模式批量删除使用）。
     *
     * 委托 [ScheduleRepository.deleteSchedules] 在单条 SQL 内完成删除，
     * 仅触发一次自动备份防抖。删除完成后通过 [_toast] 反馈删除数量。
     *
     * @param ids 待删除的排课 ID 列表
     */
    fun deleteSchedules(ids: List<String>) {
        if (ids.isEmpty()) {
            _toast.value = "未选择任何课程"
            return
        }
        safeLaunch {
            val count = scheduleRepo.deleteSchedules(ids)
            _toast.value = "已删除 $count 条排课"
        }
    }

    /**
     * 按学员删除所有排课记录（不影响课时包数据）。
     * 仅删除 Schedule 表记录，不删除 LessonPackage。
     *
     * @param studentName 学员姓名
     */
    fun deleteAllSchedulesByStudent(studentName: String) {
        safeLaunch {
            try {
                val deleted = scheduleRepo.deleteAllSchedulesByStudent(studentName)
                _toast.value = if (deleted > 0) {
                    "已删除 ${studentName} 的 $deleted 条排课记录"
                } else {
                    "学员 ${studentName} 暂无排课记录"
                }
            } catch (e: Exception) {
                _toast.value = "删除排课失败：${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /**
     * 按课时包自动排课：根据课包剩余课时与选中的多个周几，批量创建多条长期排课记录。
     *
     * 业务规则：
     * - 排课起始日期 = max(今天, 课包购买日)（保证不排过去日期，且尊重课包购买时间）
     * - 每个选中的周几创建一条 Schedule 记录，isLongTerm=true
     * - 每条 Schedule 的 endDate = 该周几在课时包用完那一周内的最后一次上课日期
     * - 若课包有 expireDate 且计算出的某 endDate 超过 expireDate，则拒绝本次排课并提示
     *
     * 课时消耗机制：
     * - Schedule.isLongTerm=true 触发 [ensureLongTermLessonsForWeek] 每周自动生成 Lesson
     * - Lesson 签退时通过 [com.shangmentiyu.sportscoach.data.repo.OperationRepository.consumeLessonForCheckOut] 扣减课时
     * - 余额耗尽后 [ensureLongTermLessonsForWeek] 自动停止生成（依赖 countLongTermPendingFrom 余额检查）
     * - endDate 字段作为额外保险，未来可用于 UI 标识"排课结束日期"
     *
     * @param packageId 课时包 ID
     * @param coachName 教练姓名
     * @param daysOfWeek 选中的周几集合（1=周一 ... 7=周日），不可为空
     * @param startTime 上课时间 HH:mm
     * @param durationMinutes 单次时长（分钟）
     * @param location 上课地点
     * @param lessonType 课程类型
     */
    fun autoScheduleFromPackage(
        packageId: String,
        coachName: String,
        daysOfWeek: Set<Int>,
        startTime: String,
        durationMinutes: Int = 60,
        location: String = "",
        lessonType: String = "训练课"
    ) {
        if (daysOfWeek.isEmpty()) {
            _toast.value = "请至少选择一个上课日"
            return
        }
        if (startTime.isBlank()) {
            _toast.value = "请填写上课时间"
            return
        }
        safeLaunch {
            try {
                val pkg = withContext(Dispatchers.IO) { pkgRepo.getPkgById(packageId) }
                if (pkg == null) {
                    _toast.value = "课时包不存在"
                    return@safeLaunch
                }
                if (pkg.remainingLessons <= 0) {
                    _toast.value = "课时包已无剩余课时"
                    return@safeLaunch
                }
                if (pkg.status != "活跃") {
                    _toast.value = "课时包状态为「${pkg.status}」，无法排课"
                    return@safeLaunch
                }

                // 排课起始日期：取今天与课包购买日的较大值
                val today = LocalDate.now()
                val purchaseDate = try {
                    LocalDate.parse(pkg.purchaseDate, dateFormatter)
                } catch (_: Exception) {
                    today
                }
                val startDate = if (today.isAfter(purchaseDate)) today else purchaseDate

                // 课包过期日（可空）
                val expireDate = if (pkg.expireDate.isNotBlank()) {
                    try { LocalDate.parse(pkg.expireDate, dateFormatter) } catch (_: Exception) { null }
                } else null
                if (expireDate != null && startDate.isAfter(expireDate)) {
                    _toast.value = "课时包已过期，无法排课"
                    return@safeLaunch
                }

                // === 自动对齐数据：清理购买日之前的排课记录 ===
                // 课时包 7.24 购买，则 7.24 之前的排课视为无效，自动清理。
                // 这确保"购买当日之前所有日期不允许排课"的约束。
                val deletedCount = scheduleRepo.deleteSchedulesBeforeDate(
                    pkg.studentName, purchaseDate.format(dateFormatter)
                )

                // 调用纯逻辑计算器：计算每个周几的最后一次上课日期
                val endDates = com.shangmentiyu.sportscoach.core.AutoScheduleCalculator
                    .calculateEndDates(startDate, pkg.remainingLessons, daysOfWeek)

                if (endDates.isEmpty()) {
                    _toast.value = "排课计算失败：剩余课时或参数异常"
                    return@safeLaunch
                }

                // 过期日校验：任一 endDate 超过 expireDate 则拒绝整批排课（避免课包部分上完后无课可扣）
                if (expireDate != null) {
                    val overflow = endDates.values.firstOrNull { it.isAfter(expireDate) }
                    if (overflow != null) {
                        _toast.value = "剩余 ${pkg.remainingLessons} 节课时无法在有效期内排完（到期日 ${pkg.expireDate}）"
                        return@safeLaunch
                    }
                }

                // 按周几顺序逐条创建 Schedule 记录
                // 教练时间冲突由 addSchedule 内部的 checkCoachConflict 检测：
                // - 同教练 + 同 dayOfWeek + 同 startTime 的旧排课会触发 CoachConflictException
                // - 多个 dayOfWeek 之间互不冲突（同教练不同时段视为合法）
                val coachKey = coachName.ifBlank { "默认教练" }
                memoryRepo.saveMemory(coachKey, "time", startTime.trim())
                if (location.isNotBlank()) {
                    memoryRepo.saveMemory(coachKey, "location", location.trim())
                }
                daysOfWeek.sorted().forEach { dow ->
                    memoryRepo.saveMemory(coachKey, "dayOfWeek", dow.toString())
                }

                var createdCount = 0
                for (dow in daysOfWeek.sorted()) {
                    val endDate = endDates[dow] ?: continue
                    try {
                        scheduleRepo.addSchedule(
                            studentName = pkg.studentName,
                            coachName = coachName,
                            dayOfWeek = dow,
                            startTime = startTime,
                            durationMinutes = durationMinutes,
                            location = location,
                            lessonType = lessonType,
                            isLongTerm = true,
                            endDate = endDate.format(dateFormatter),
                            // startDate = 购买日，确保购买日之前不生成 Lesson
                            startDate = purchaseDate.format(dateFormatter)
                        )
                        createdCount++
                    } catch (e: CoachConflictException) {
                        // 单个周几冲突时跳过，继续创建其他周几
                        _toast.value = "周${dow} ${startTime} 教练时段冲突，已跳过"
                    }
                }

                _toast.value = if (createdCount == 0) {
                    "全部周几排课均失败，请检查教练时段冲突"
                } else {
                    val cleanHint = if (deletedCount > 0) "，已清理 $deletedCount 条购买日前旧排课" else ""
                    "已为 ${pkg.studentName} 创建 $createdCount 条排课（共 ${pkg.remainingLessons} 节课时）$cleanHint"
                }
            } catch (e: Exception) {
                _toast.value = "排课失败：${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /**
     * 清空所有排课记录（课表管理"清空全部"功能）。
     * 删除 schedules 表全部数据，不影响已签到的课时记录（lessons 表）。
     *
     * === Bug 修复：删除后同步重置 weekStart 到本周 ===
     * 原代码只清 schedules 表，未重置 [_weekStart]。若用户在历史周触发"清空全部"，
     * weekStart 仍指向过去日期，之后点 FAB 新建会被 saveSchedule 中的
     * "排课生效日期不能早于今天" 拦截，用户看到 toast 后不知所以。
     * 现同步重置到本周，并清理 editingSchedule（避免走 update 分支命中已删除记录）。
     */
    fun deleteAllSchedules() {
        safeLaunch {
            opRepo.deleteAllSchedules()
            // 重置到本周，避免历史周卡住新建
            _weekStart.value = getWeekStart()
            // 清理编辑态：editingSchedule 指向的记录已被删除，走 update 会命中 0 行
            _editingSchedule.value = null
            _toast.value = "已清空全部课表"
        }
    }

    /**
     * === Bug 修复2：清理过去未完成的长期排课记录（历史废弃占位排课） ===
     *
     * 委托 [ScheduleRepository.clearUnfinishedPastLongTermLessons] 执行物理删除：
     * - 删除 lessons 表中 status != '已签退' 且 date < 今天 且 lessonType LIKE '%(长期自动)%' 的记录
     * - 保留已签退的历史真实记录（学员已实际消课）
     *
     * 触发时机：
     * 1. ScheduleScreen 启动时自动调用一次（[cleanupOnEnter]），静默清理避免污染历史周历
     * 2. 排课页顶部"清理过去无效排课"按钮手动触发（[cleanupPastLessonsManually]），
     *    通过 toast 反馈清理数量，便于用户确认清理结果
     *
     * @param silent 是否静默模式（true：无清理不提示；false：始终提示清理结果）
     */
    fun cleanupPastLessons(silent: Boolean = false) {
        safeLaunch {
            val deleted = withContext(Dispatchers.IO) {
                scheduleRepo.clearUnfinishedPastLongTermLessons()
            }
            if (!silent) {
                _toast.value = if (deleted > 0) {
                    "已清理 $deleted 条过去无效排课"
                } else {
                    "无过去无效排课需要清理"
                }
            }
        }
    }

    /**
     * ScheduleScreen 启动时自动清理：静默模式，无清理不提示。
     *
     * 与 [cleanupPastLessons] 区别：
     * - silent=true：仅在确有清理时通过 toast 提示，避免每次进入页面都弹"无清理"提示
     * - 调用时机：ScheduleScreen LaunchedEffect(Unit) 中调用
     */
    fun cleanupOnEnter() {
        safeLaunch {
            val deleted = withContext(Dispatchers.IO) {
                scheduleRepo.clearUnfinishedPastLongTermLessons()
            }
            if (deleted > 0) {
                _toast.value = "已自动清理 $deleted 条过去无效排课"
            }
        }
    }

    /**
     * 排课页顶部"清理过去无效排课"按钮回调：非静默模式，始终反馈清理结果。
     */
    fun cleanupPastLessonsManually() {
        cleanupPastLessons(silent = false)
    }

    // === 课程包操作 ===
    fun addPackage(
        studentName: String,
        name: String,
        totalLessons: Int,
        price: Double,
        purchaseDate: String,
        expireDate: String
    ) {
        safeLaunch {
            pkgRepo.addPackage(
                LessonPackage(
                    studentName = studentName,
                    name = name,
                    totalLessons = totalLessons,
                    price = price,
                    purchaseDate = purchaseDate,
                    expireDate = expireDate
                )
            )
            _toast.value = "课程包已添加"
        }
    }

    fun deletePackage(id: String) {
        safeLaunch {
            pkgRepo.deletePackage(id)
            _toast.value = "已删除课时包，关联排课已同步清除"
        }
    }

    /**
     * 调整课时包课时数（正数增添，负数减少）。
     * 同步修改 totalLessons 与 remainingLessons，保持已用课时数不变。
     */
    fun adjustPackage(packageId: String, delta: Int) {
        if (delta == 0) return
        safeLaunch {
            val pkg = packages.value.firstOrNull { it.id == packageId } ?: return@safeLaunch
            // remainingLessons 是计算属性 = totalLessons - usedLessons
            // 增添：totalLessons += delta，usedLessons 不变
            // 减少：totalLessons -= delta，但不低于 usedLessons
            val newTotal = if (delta > 0) {
                pkg.totalLessons + delta
            } else {
                (pkg.totalLessons + delta).coerceAtLeast(pkg.usedLessons)
            }
            pkgRepo.updatePackage(pkg.copy(totalLessons = newTotal))
            _toast.value = if (delta > 0) "已增添 $delta 课时" else "已减少 ${-delta} 课时"
        }
    }

    /**
     * 额外赠送课时：为学员创建一个独立的赠送课时包。
     * 不影响原套餐数据，单独追踪赠送课时的使用情况。
     */
    fun giftLessons(studentName: String, count: Int) {
        if (count <= 0) return
        safeLaunch {
            pkgRepo.addPackage(
                LessonPackage(
                    studentName = studentName,
                    name = "赠送${count}课时",
                    totalLessons = count,
                    usedLessons = 0,
                    price = 0.0,
                    purchaseDate = todayStr(),
                    expireDate = "",
                    note = "额外赠送"
                )
            )
            _toast.value = "已为 $studentName 赠送 $count 课时"
        }
    }

    /**
     * 更新课时包全部信息（学员姓名、套餐名、总/已用课时、价格、日期、状态、备注）。
     * 用于课时余额页面的编辑功能。
     */
    fun updatePackage(pkg: LessonPackage) {
        safeLaunch {
            pkgRepo.updatePackage(pkg)
            _toast.value = "课时包已更新"
        }
    }

    // === 教练操作 ===
    fun addCoach(name: String, phone: String, specialty: String) {
        safeLaunch {
            if (coachRepo.getByName(name) != null) {
                _toast.value = "教练已存在"
                return@safeLaunch
            }
            coachRepo.upsert(
                Coach(
                    name = name,
                    phone = phone,
                    specialty = specialty,
                    hireDate = todayStr()
                )
            )
            _toast.value = "教练已添加"
        }
    }

    fun deleteCoach(name: String) {
        safeLaunch {
            coachRepo.delete(name)
            _toast.value = "教练已删除"
        }
    }

    // === 辅助方法 ===
    /** 获取今天的周几（1=周一 ... 7=周日） */
    private fun getTodayDayOfWeek(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.DAY_OF_WEEK).let { if (it == 1) 7 else it - 1 }
    }

    /** 获取本周周一的日期 */
    private fun getWeekStart(): Date {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.clear(Calendar.MINUTE)
        cal.clear(Calendar.SECOND)
        cal.clear(Calendar.MILLISECOND)
        return cal.time
    }
}
