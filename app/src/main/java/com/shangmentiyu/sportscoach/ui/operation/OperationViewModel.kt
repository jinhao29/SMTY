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
import com.shangmentiyu.sportscoach.core.LessonDateCalculator
import com.shangmentiyu.sportscoach.domain.scheduling.ScheduleQuotaExceededException
import com.shangmentiyu.sportscoach.domain.scheduling.ValidateScheduleUseCase
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
    // 排课校验唯一入口：isDateValid（购买日期）+ hasRemainingCapacity（额度）
    private val validateSchedule: ValidateScheduleUseCase
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

    // === 学员列表（用于新增排课/课程包时选择） ===
    // === v52 NPE 修复：声明必须位于 init 块之前 ===
    // init 中 viewModelScope.launch 使用 Dispatchers.Main.immediate，在构造（主线程）时
    // 会同步执行协程体直到首个挂起点；students.collect 在第一个挂起点之前就访问本字段，
    // 若声明在 init 之后，构造函数未完成时非空 val 字段在 JVM 层仍为 null，
    // getter 触发 Intrinsics 非空检查 → NullPointerException（堆栈指向 StateFlow.collect）。
    val students: StateFlow<List<Student>> = studentRepo.getActiveStudents()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // === v52 数据流加固：学员列表首帧加载标记 ===
    // 与 schedulesLoaded 同款机制：Room Flow 首帧异步到达，在此之前 StateFlow 持有
    // 初始值 emptyList()。若 UI（如 ScheduleEditDialog 学员下拉框）直接用空列表渲染，
    // 会在冷启动/ViewModel 重建窗口期出现"学员下拉框空白/消失"的假象。
    // 首帧真实 emission 到达后才置 true，UI 据此显示"正在加载学员…"占位而非空下拉。
    private val _studentsLoaded = MutableStateFlow(false)
    val studentsLoaded: StateFlow<Boolean> = _studentsLoaded.asStateFlow()

    init {
        // 直接用 StateFlow.collect（而非 first()）：first() 会立即返回 StateFlow 的初始值
        // emptyList()，导致 Room 首帧数据到达前就把 schedulesLoaded 置 true，
        // 屏幕短暂/错误地渲染"今日无排课"空态（日历红点已出但列表为空）。
        // collect 挂起至 Room 首帧真实 emission 后才置 loaded，并留痕每次数据量。
        viewModelScope.launch {
            schedules.collect { list ->
                android.util.Log.d("ScheduleDebug", "列表加载数量: ${list.size}")
                if (!_schedulesLoaded.value) _schedulesLoaded.value = true
            }
        }
        // v52：学员列表首帧标记（供 ScheduleEditDialog 下拉框占位使用）
        viewModelScope.launch {
            // === v52 NPE 防御：collect 前显式判空 ===
            // 声明顺序已修复（students / _studentsLoaded 位于 init 之前），此处仍保留
            // 判空兜底，杜绝任何注入/初始化异常场景下 StateFlow.collect 目标为 null 的崩溃。
            val safeStudents = students
            // 防御性判空：students 为非空类型，Kotlin 编译器视为恒真（SENSELESS_COMPARISON），
            // 此处保留兜底防止未来类型调整/注入异常导致 collect 目标为 null，故显式抑制该警告。
            @Suppress("SENSELESS_COMPARISON")
            if (safeStudents != null) {
                safeStudents.collect { list ->
                    android.util.Log.d("DataFlow", "学员列表首帧到达数量: ${list.size}")
                    android.util.Log.d("StudentPicker", "当前学员列表大小: ${list.size}")
                    if (!_studentsLoaded.value) _studentsLoaded.value = true
                }
            } else {
                android.util.Log.e("OperationVM", "students Flow is null!")
            }
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

    // === v52 声明顺序修复：students / _studentsLoaded / studentsLoaded 已上移至 init 块之前 ===
    // （见 class 头部，避免 init 中 Main.immediate 立即执行的 collect 访问未初始化字段）

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
     * - 异常落盘：通过 [com.shangmentiyu.sportscoach.app.framework.CrashHandler.writeLog] 同步写入 crash_logs/
     * - UI 反馈：通过 [_toast] 推送轻量提示，避免静默失败
     */
    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.app.framework.CoroutineExt.createAppExceptionHandler(
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
     * 排课弹窗打开时强制刷新学员课时包数据。
     * 一次性查询数据库并留痕 Logcat（tag=ScheduleQuota），同时触发 packages 上游
     * 重新订阅取最新数据，确保弹窗内展示/校验读到最新数据库状态而非旧缓存。
     */
    fun loadStudentPackages(studentId: String?) {
        safeLaunch {
            try {
                val fresh = withContext(Dispatchers.IO) { pkgRepo.getAllPackages().first() }
                val active = fresh.filter { it.status == "活跃" && !it.isExhausted && !it.isExpired }
                val ofStudent = fresh.filter { it.studentId == studentId || (studentId == null && it.studentName.isNotBlank()) }
                android.util.Log.d("ScheduleQuota",
                    "loadStudentPackages(studentId=$studentId): 全部${fresh.size}个(剩余合计=${fresh.sumOf { it.remainingLessons }}) | " +
                        "活跃${active.size}个(活跃剩余合计=${active.sumOf { it.remainingLessons }}) | " +
                        "该学员${ofStudent.size}个(剩余合计=${ofStudent.sumOf { it.remainingLessons }})")
            } catch (e: Exception) {
                android.util.Log.w("ScheduleQuota", "loadStudentPackages 失败: ${e.message}")
            }
        }
    }

    /**
     * 排课弹窗打开时强制刷新学员列表数据。
     * 一次性查询数据库并留痕 Logcat（tag=StudentPicker），对比数据库实际数量与
     * StateFlow 缓存数量，确保新添加学员能被下拉框识别。
     */
    fun loadStudents() {
        safeLaunch {
            try {
                val fresh = withContext(Dispatchers.IO) { studentRepo.getActiveStudents().first() }
                android.util.Log.d("StudentPicker",
                    "loadStudents: 数据库实际学员数=${fresh.size}, StateFlow缓存数=${students.value.size}")
            } catch (e: Exception) {
                android.util.Log.w("StudentPicker", "loadStudents 失败: ${e.message}")
            }
        }
    }

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
     * 保存前统一校验（dialog 与 saveSchedule 共用同一入口）：
     * 日期不得早于今天 / 早于购买日期 / 剩余可排课时为 0，任一不满足返回用户可读错误文案。
     *
     * === v49 彻底重构：额度校验统一走 ValidateScheduleUseCase 三要素公式 ===
     * 剩余可排课时 = 总课时(活跃包剩余之和) - 已消耗(已签退) - 待消耗(占位)，
     * 编辑/新建场景使用完全一致的校验口径。
     *
     * === v49 体验课：isTrial=true 跳过购买日期校验与余额校验，仅保留"生效日期不早于今天" ===
     */
    /** 学员是否有正式课时记录（isTrial=0），用于"首次自动体验课"判断 */
    suspend fun hasFormalLessons(studentId: String?, name: String): Boolean =
        opRepo.hasFormalLessonsDual(studentId, name)

    suspend fun validateScheduleForSave(form: ScheduleForm): String? {
        if (form.studentName.isBlank()) return null
        val zone = java.time.ZoneId.systemDefault()
        val weekStartLocal = _weekStart.value.toInstant().atZone(zone).toLocalDate()
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
        // 小班课：studentName 为空是正常的（多选场景），校验改为检查是否选了学员
        if (form.isGroupClass) {
            if (form.groupStudentNames.isEmpty()) { _toast.value = "请至少选择 2 名学员"; return }
        } else {
            if (form.studentName.isBlank()) { _toast.value = "请选择学员或填写体验课学员姓名"; return }
        }
        if (form.startTime.isBlank()) { _toast.value = "请填写上课时间"; return }
        safeLaunch {
            try {
                val editing = _editingSchedule.value
                val coachKey = form.coachName.ifBlank { "默认教练" }

                // 统一校验：ValidateScheduleUseCase（购买日期前置 / 额度封顶），失败直接拦截不入库
                val validationError = validateScheduleForSave(form)
                if (validationError != null) {
                    _toast.value = validationError
                    return@safeLaunch
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
                                    startDate = todayStr(),
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
                                    _toast.value = "保存失败：学员 $name 数据库写入异常"
                                    return@safeLaunch
                                }
                            }
                        }
                        val countText = if (targetDays.size > 1) "（${targetDays.size}天）" else ""
                        _toast.value = "小班课已添加（${form.groupStudentNames.size}名学员）$countText"
                        if (form.isLongTerm) ensureLongTermLessonsForWeek()
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
                            // v25 优化5：强制替换分支——先删除冲突排课再写入（日期已由 validateScheduleForSave 校验）。
                            // v49 体验课：studentId 强制 null（未注册学员）
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
                            // === v49 彻底重构：新建排课统一走 saveSchedule（Repository 层强制校验） ===
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
                                startDate = todayStr(),
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
                                _toast.value = "保存失败：数据库写入异常，请查看 Logcat (tag=DataFlow)"
                                return@safeLaunch
                            }
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
                    // === v49：保存长期排课后立即触发占位生成 ===
                    // 确保长期排课生成器能正确反映刚占用的课时（额度同步扣减，避免超额）
                    if (form.isLongTerm) ensureLongTermLessonsForWeek()
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
                        // v49 体验课：studentId 强制 null（未注册学员无软关联）
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
            } catch (e: ScheduleQuotaExceededException) {
                // === v49：额度已满业务异常（三要素公式）===
                // Repository 层 saveSchedule 上抛，直接显示明确文案，不走"保存失败"笼统提示
                android.util.Log.w("OperationVM",
                    "saveSchedule 额度校验拦截：${e.message}")
                _toast.value = e.message ?: "无法排课：该学员课时额度已满"
            } catch (e: IllegalArgumentException) {
                // === 排课日期早于购买日期的业务校验异常 ===
                // opRepo.saveSchedule 在事务前抛出，异常消息即用户可读文案，直接展示
                android.util.Log.w("OperationVM",
                    "saveSchedule 日期校验拦截：${e.message}")
                _toast.value = e.message ?: "无法排课：日期校验失败"
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
     * 长期排课自动生成未来课时记录：
     * 进入排课页/每日计划页时检查所有 isLongTerm=true 的排课，
     * 为未来日期（从当前周开始）自动生成 Lesson 占位记录。
     *
     * === v49 彻底重构：独立学员循环 + 逐日生成 + 额度封顶 ===
     * 生成核心已下沉到 [com.shangmentiyu.sportscoach.data.repo.ScheduleQueryRepository]
     * 的 [com.shangmentiyu.sportscoach.data.repo.ScheduleQueryRepository.generateLongTermLessonsForStudent]
     * （与历史修正 fixHistoricalScheduleErrors 共用同一实现）：
     *
     * 1. 使用一个独立循环处理每个学员
     * 2. 遍历未来日期（从当前周开始），检查每一天是否已经存在排课；
     *    若当天未排，则判断剩余可排课时（总-已消耗-待消耗，三要素公式）是否 > 0：
     *    - 是 → 按当天 dayOfWeek 命中的长期模板生成一条课时占位，额度减 1
     *    - 否 → 立即停止该学员后续所有排课生成
     * 3. 严格遵循学员排课偏好（周一至周五等）：周几无模板则跳过
     * 4. 模板为 schedules 表长期记录（手动排课时写入），生成器只追加 lessons 占位
     * 5. 过去日期（date < today）：不补排新记录（历史数据不回溯）
     *
     * 一旦剩余可排课时为 0，不再生成任何未来排课（根治「额度用完仍排课」）。
     */
    fun ensureLongTermLessonsForWeek() {
        safeLaunch {
            // 加锁：防止短时间多次触发导致并发写入同一学员同一天同一时间的 Lesson，
            // 进而引发 SQLite 写锁竞争/死锁。后续若已持锁会挂起等待，保证串行生成。
            longTermScheduleMutex.withLock {
                val allSchedules = scheduleRepo.getAllSchedulesOnce()
                val longTerm = allSchedules.filter { it.isActive && it.isLongTerm }
                if (longTerm.isEmpty()) return@withLock

                // 线程安全：使用 [LocalDate] + [DateTimeFormatter] 替代 [SimpleDateFormat]
                val zone = java.time.ZoneId.systemDefault()
                val weekStartStr = _weekStart.value.toInstant()
                    .atZone(zone).toLocalDate().format(dateFormatter)
                val todayStr = LocalDate.now().format(dateFormatter)

                // v24 优化2：收集余额不足警告，生成完成后一次性推送给 UI
                val warnings = mutableListOf<String>()

                // === v49：独立循环处理每个学员 ===
                // 每个学员的剩余可排课时独立计算（三要素公式），额度用尽只停止该学员，
                // 不影响其他学员继续生成
                val students = longTerm.map { it.studentName }.distinct()
                android.util.Log.d("ScheduleGen", "ensureLongTermLessonsForWeek: 学员数=${students.size}, today=$todayStr, weekStart=$weekStartStr")
                for (name in students) {
                    val available = validateSchedule.availableQuota(name, todayStr)
                    android.util.Log.d("ScheduleGen", "ensureLongTermLessonsForWeek: student=$name 可用额度=$available")
                    if (available <= 0) {
                        warnings.add("$name 课时额度已用完（剩余可排课时为 0），不再生成排课")
                        continue
                    }
                    val generated = opRepo.generateLongTermLessonsForStudent(name, weekStartStr, todayStr)
                    android.util.Log.d("ScheduleGen", "ensureLongTermLessonsForWeek: student=$name 本次生成=$generated")
                }

                // v53 修复：始终覆写 _noBalanceWarnings，生成成功时清空旧警告
                // 原逻辑仅在 warnings 非空时赋值，导致上一轮残留的余额不足警告在本次
                // 生成成功后仍展示（"排课生成成功但仍弹出余额不足"的根因）。
                _noBalanceWarnings.value = warnings.distinct()
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
     * 按课时包自动排课：用户指定开始日期、勾选上课日、总节数，
     * 纯内存计算所有排课日期后，在单个 Room 事务中批量插入课时记录（仅占位，不扣余额）。
     *
     * 业务规则：
     * - 从 startDate 起逐日后移，仅累计用户勾选的上课日，直到累计数等于 totalLessons
     * - 校验预计结束日期是否超过课时包截止日期（expireDate），超过则终止
     * - 事务内：insertAll 批量插入课时记录（不扣余额，不设 packageId）
     * - 排课与消课完全分离：余额扣减仅在实际消课（签退）时发生
     * - 仍创建 Schedule 记录（isLongTerm=true）供课表周历模板显示
     *
     * @param packageId 课时包 ID
     * @param coachName 教练姓名
     * @param daysOfWeek 选中的周几集合（1=周一 ... 7=周日），不可为空
     * @param startTime 上课时间 HH:mm
     * @param totalLessons 本次总节数（用户指定，必须 > 0）
     * @param startDateStr 开始排课日期（yyyy-MM-dd，空则使用课包购买日）
     * @param durationMinutes 单次时长（分钟）
     * @param location 上课地点
     * @param lessonType 课程类型
     */
    fun autoScheduleFromPackage(
        packageId: String,
        coachName: String,
        daysOfWeek: Set<Int>,
        startTime: String,
        totalLessons: Int,
        startDateStr: String = "",
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
        if (totalLessons <= 0) {
            _toast.value = "总节数必须大于 0"
            return
        }
        safeLaunch {
            try {
                val pkg = withContext(Dispatchers.IO) { pkgRepo.getPkgById(packageId) }
                if (pkg == null) {
                    _toast.value = "课时包不存在"
                    return@safeLaunch
                }
                if (pkg.status != "活跃") {
                    _toast.value = "课时包状态为「${pkg.status}」，无法排课"
                    return@safeLaunch
                }
                // === 排课节数不得超出该课时包剩余课时（排课只占位不扣费，但占位总数受余额封顶）===
                if (totalLessons > pkg.remainingLessons) {
                    _toast.value = "本次排课节数($totalLessons)超过课时包剩余课时(${pkg.remainingLessons})，无法排课"
                    return@safeLaunch
                }

                val today = LocalDate.now()
                val purchaseDate = try {
                    LocalDate.parse(pkg.purchaseDate, dateFormatter)
                } catch (_: Exception) {
                    today
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
                    _toast.value = "课时包已过期，无法排课"
                    return@safeLaunch
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
                    _toast.value = "排课失败：预计结束日期 ${calculatedEndDate.format(dateFormatter)}" +
                        " 超过课时包有效期 ${pkg.expireDate}"
                    return@safeLaunch
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
                    _toast.value = result.message
                    return@safeLaunch
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

                var scheduleCreated = 0
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
                        scheduleCreated++
                    } catch (e: CoachConflictException) {
                        _toast.value = "周${dow} ${startTime} 教练时段冲突，已跳过模板创建"
                    }
                }

                val endDateDisplay = calculatedEndDate.format(
                    DateTimeFormatter.ofPattern("M月d日", Locale.getDefault())
                )
                _toast.value = if (result.skippedCount > 0) {
                    "已为 ${pkg.studentName} 成功排课 ${result.createdCount} 节" +
                        "（跳过 ${result.skippedCount} 节重复）" +
                        "，预计结束日期 $endDateDisplay"
                } else {
                    "已为 ${pkg.studentName} 成功排课 ${result.createdCount} 节" +
                        "，预计结束日期 $endDateDisplay"
                }
            } catch (e: Exception) {
                _toast.value = "排课失败：${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /**
     * 小班课自动排课：与「按课时包排课」同款逻辑，但面向多名学员。
     *
     * 业务规则：
     * - 从 startDate 起逐日后移，累计用户勾选的上课日，直到累计数等于 totalLessons
     * - 事务内：为每名学员 × 每个日期批量插入课时占位（不扣余额，签退时统一扣费）
     * - 每个上课日的课时共享同一 sessionId，签退时识别当天同组统一消课（不跨天）
     * - 仍创建 Schedule 模板（isLongTerm=false）供课表周历模板显示
     *
     * @param groupStudentIds 小班课学员 studentId 集合（与 groupStudentNames 顺序一致）
     * @param groupStudentNames 小班课学员姓名列表
     * @param coachName 教练姓名
     * @param daysOfWeek 选中的周几集合（1=周一 ... 7=周日），不可为空
     * @param startTime 上课时间 HH:mm
     * @param totalLessons 本次总节数（用户指定，必须 > 0）
     * @param startDateStr 开始排课日期（yyyy-MM-dd，空则使用今天）
     */
    fun autoScheduleGroup(
        groupStudentIds: Set<String>,
        groupStudentNames: List<String>,
        coachName: String,
        daysOfWeek: Set<Int>,
        startTime: String,
        totalLessons: Int,
        startDateStr: String = "",
        durationMinutes: Int = 60,
        location: String = "",
        lessonType: String = "训练课"
    ) {
        if (groupStudentNames.size < 2) {
            _toast.value = "小班课至少需要选择 2 名学员"
            return
        }
        if (daysOfWeek.isEmpty()) {
            _toast.value = "请至少选择一个上课日"
            return
        }
        if (startTime.isBlank()) {
            _toast.value = "请填写上课时间"
            return
        }
        if (totalLessons <= 0) {
            _toast.value = "总节数必须大于 0"
            return
        }
        safeLaunch {
            try {
                val today = LocalDate.now()
                val startDate = if (startDateStr.isNotBlank()) {
                    try { LocalDate.parse(startDateStr, dateFormatter) } catch (_: Exception) { today }
                } else today

                // === 步骤0：计算每名学员剩余可排课时，按各自额度截断 ===
                // 额度少的学员排完即退出，剩余节数由额度充足的学员单独继续排。
                val quotaByName = mutableMapOf<String, Int>()
                for (name in groupStudentNames) {
                    val q = validateSchedule.availableQuota(name, todayStr())
                    quotaByName[name] = q
                    android.util.Log.d("GroupSchedule", "小班课学员「$name」剩余可排课时=$q")
                }
                val maxQuota = quotaByName.values.maxOrNull() ?: 0
                android.util.Log.d("GroupSchedule", "小班课最大学员额度=$maxQuota，请求节数=$totalLessons")
                if (maxQuota <= 0) {
                    _toast.value = "无法排课：所有学员剩余可排课时均为 0，请先核对课时包"
                    return@safeLaunch
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
                    _toast.value = result.message
                    return@safeLaunch
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
                val templates = mutableListOf<Schedule>()
                groupStudentNames.forEachIndexed { index, name ->
                    val maxLessons = memberQuotas.getOrNull(index) ?: 0
                    if (maxLessons <= 0) return@forEachIndexed
                    val memberDates = lessonDates.take(maxLessons)
                    val memberEndByDow = memberDates.groupBy { it.dayOfWeek.value }
                        .mapValues { it.value.last() }
                    for (dow in daysOfWeek.sorted()) {
                        val endD = memberEndByDow[dow] ?: continue
                        templates += Schedule(
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
                _toast.value = "已为 ${groupStudentNames.size} 名学员小班排课 $effectiveTotal 节" +
                    "$quotaHint，预计结束日期 $endDateDisplay"
            } catch (e: Exception) {
                _toast.value = "排课失败：${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /**
     * 计算预计结束日期（供 UI 实时显示，纯内存计算无副作用）。
     *
     * @param startDateStr 开始日期 yyyy-MM-dd
     * @param totalLessons 总节数
     * @param selectedDays 勾选的上课日集合（1=周一 ... 7=周日）
     * @return 预计结束日期字符串 yyyy-MM-dd，计算失败返回 null
     */
    fun calculateExpectedEndDate(
        startDateStr: String,
        totalLessons: Int,
        selectedDays: Set<Int>
    ): String? {
        if (startDateStr.isBlank() || totalLessons <= 0 || selectedDays.isEmpty()) return null
        return try {
            val startDate = LocalDate.parse(startDateStr, dateFormatter)
            val endDate = LessonDateCalculator.calculateEndDate(startDate, totalLessons, selectedDays)
            endDate.format(dateFormatter)
        } catch (_: Exception) {
            null
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
                    // v51 断链修复：课时包必须携带 studentId 软关联键，
                    // 否则按 ID 级联改名时该课时包不会更新 studentName，学员课时包列表断链
                    studentId = studentRepo.getByName(studentName)?.studentId,
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
                    // v51 断链修复：赠送包同样携带 studentId（软关联唯一键）
                    studentId = studentRepo.getByName(studentName)?.studentId,
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
