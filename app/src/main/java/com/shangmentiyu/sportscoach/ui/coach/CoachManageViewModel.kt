package com.shangmentiyu.sportscoach.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.CoachPayout
import com.shangmentiyu.sportscoach.data.model.CoachPayoutRequest
import com.shangmentiyu.sportscoach.data.model.CoachSchedule
import com.shangmentiyu.sportscoach.data.model.CoachStudentBinding
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.CoachPayrollRepository
import com.shangmentiyu.sportscoach.data.repo.CoachRepository
import com.shangmentiyu.sportscoach.data.repo.CoachScheduleRepository
import com.shangmentiyu.sportscoach.data.repo.CoachStudentRepository
import com.shangmentiyu.sportscoach.data.repo.CoachTreeNode
import com.shangmentiyu.sportscoach.data.repo.ScheduleRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 教练管理模块 ViewModel：档案 / 学员排课 / 排班与工作量 / 团队 / 薪资 共用。
 *
 * 状态来源全部为 Room Flow（数据驱动），写操作后 Room 自动推送刷新。
 */
class CoachManageViewModel(
    private val coachRepo: CoachRepository,
    private val scheduleRepo: CoachScheduleRepository,
    private val payrollRepo: CoachPayrollRepository,
    private val bindingRepo: CoachStudentRepository,
    private val lessonScheduleRepo: ScheduleRepository,
    private val studentRepo: StudentRepository
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // ---------- 档案 ----------

    val allCoaches: StateFlow<List<Coach>> = coachRepo.getAllCoaches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCoaches: StateFlow<List<Coach>> = coachRepo.getActiveCoaches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _roleFilter = MutableStateFlow("")
    val roleFilter: StateFlow<String> = _roleFilter.asStateFlow()

    private val _statusFilter = MutableStateFlow("在职")
    val statusFilter: StateFlow<String> = _statusFilter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 档案列表（角色 + 状态 + 姓名/专长搜索 组合过滤） */
    val filteredCoaches: StateFlow<List<Coach>> = combine(
        allCoaches, _roleFilter, _statusFilter, _query
    ) { coaches, role, status, q ->
        coaches.filter { c ->
            (role.isBlank() || c.role == role) &&
                (status.isBlank() || c.status == status) &&
                (q.isBlank() || c.name.contains(q) || c.specialty.contains(q) || c.phone.contains(q))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setRoleFilter(role: String) { _roleFilter.value = role }
    fun setStatusFilter(status: String) { _statusFilter.value = status }
    fun setQuery(q: String) { _query.value = q }

    /** 新增/更新教练，异常（重名校验/上级非法）转为 toast 文案 */
    fun saveCoach(coach: Coach, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                if (coach.name.isBlank()) throw IllegalArgumentException("姓名不能为空")
                coachRepo.upsert(coach)
                true
            } catch (e: IllegalArgumentException) {
                _toast.value = e.message
                false
            }
            onDone(result)
        }
    }

    /** 设置教练状态（在职 / 休假 / 离职）。离职前若名下仍有成员，阻止并提示先转让 */
    fun setStatus(coach: Coach, newStatus: String) {
        if (newStatus == coach.status) return
        viewModelScope.launch {
            if (newStatus == "离职") {
                val members = coachRepo.getCoachesBySuperior(coach.name).first()
                if (members.isNotEmpty()) {
                    _toast.value = "「${coach.name}」名下还有 ${members.size} 名成员，请先转让团队"
                    return@launch
                }
            }
            coachRepo.upsert(coach.copy(status = newStatus))
            _toast.value = "「${coach.name}」已设为${newStatus}" +
                if (newStatus != "在职") "（档案列表默认只显示在职，可切换状态筛选查看）" else ""
        }
    }

    /** 删除教练（名下有成员时阻止；同步清理学员绑定） */
    fun deleteCoach(coach: Coach) {
        viewModelScope.launch {
            val members = coachRepo.getCoachesBySuperior(coach.name).first()
            if (members.isNotEmpty()) {
                _toast.value = "「${coach.name}」名下还有 ${members.size} 名成员，请先转让团队"
                return@launch
            }
            coachRepo.delete(coach.name)
            bindingRepo.deleteByCoach(coach.name)
            _toast.value = "已删除「${coach.name}」"
        }
    }

    // ---------- 绑定学员（v34） ----------

    /** 全部活跃学员（绑定弹窗候选列表） */
    val students: StateFlow<List<Student>> = studentRepo.getActiveStudents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 全部绑定关系（UI 按 coachName 过滤派生各教练的学员名单） */
    val allBindings: StateFlow<List<CoachStudentBinding>> = bindingRepo.getAllBindings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 绑定学员 */
    fun bindStudent(coachName: String, student: Student) {
        viewModelScope.launch {
            bindingRepo.bind(coachName, student.name, student.studentId)
        }
    }

    /** 解绑学员 */
    fun unbindStudent(coachName: String, studentName: String) {
        viewModelScope.launch {
            bindingRepo.unbind(coachName, studentName)
        }
    }

    // ---------- 团队 ----------

    private val _teamTree = MutableStateFlow<List<CoachTreeNode>>(emptyList())
    val teamTree: StateFlow<List<CoachTreeNode>> = _teamTree.asStateFlow()

    init { refreshTeamTree() }

    fun refreshTeamTree() {
        viewModelScope.launch { _teamTree.value = coachRepo.buildTeamTree() }
    }

    /** 转让成员（原上级名下全部下属 → 新上级） */
    fun transferMembers(fromName: String, toName: String) {
        viewModelScope.launch {
            try {
                coachRepo.transferMembers(fromName, toName)
                _toast.value = "已将「$fromName」名下成员转让给「$toName」"
            } catch (e: IllegalArgumentException) {
                _toast.value = e.message
            }
        }
    }

    // ---------- 排班与工作量 ----------

    private val _selectedCoach = MutableStateFlow("")
    val selectedCoach: StateFlow<String> = _selectedCoach.asStateFlow()

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    val availability: StateFlow<List<CoachSchedule>> = _selectedCoach
        .flatMapLatest { name -> if (name.isBlank()) MutableStateFlow(emptyList()) else scheduleRepo.getByCoach(name) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 各教练当前活跃排课数（主讲） */
    val scheduledCounts: StateFlow<Map<String, Int>> = scheduleRepo.scheduledCountByCoach()
        .map { rows -> rows.associate { it.name to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 各教练助教排课数 */
    val assistCounts: StateFlow<Map<String, Int>> = scheduleRepo.assistCountByCoach()
        .map { rows -> rows.associate { it.name to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun selectCoach(name: String) { _selectedCoach.value = name }

    // ---------- 学员排课周视图（v34，依赖上方 _selectedCoach） ----------

    /** 选中教练的全部活跃排课（schedules 表，按 dayOfWeek+startTime 排序） */
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    val coachLessons: StateFlow<List<Schedule>> = _selectedCoach
        .flatMapLatest { name ->
            if (name.isBlank()) flowOf(emptyList()) else lessonScheduleRepo.getSchedulesByCoach(name)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 选中教练的绑定学员（排课时的学员候选；无绑定时 UI 回退展示全部学员） */
    val boundStudents: StateFlow<List<Student>> = combine(_selectedCoach, allBindings, students) { coach, bindings, all ->
        if (coach.isBlank()) emptyList()
        else {
            val names = bindings.filter { it.coachName == coach }.map { it.studentName }.toSet()
            all.filter { it.name in names }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 新建/编辑教练排课。
     *
     * 新建走 [ScheduleRepository.addSchedule]（含教练同日同时段冲突检测），
     * 默认 isLongTerm=true（每周重复，与主流排课 App 语义一致，长期课由既有机制自动生成课时记录）；
     * 编辑保留原 startDate/endDate/isLongTerm 等字段，仅更新排课要素。
     *
     * @param scheduleId null=新建；非空=编辑既有排课
     * @param onDone 结果回调（true=保存成功）
     */
    fun saveCoachLesson(
        scheduleId: String?,
        coachName: String,
        student: Student,
        dayOfWeek: Int,
        startTime: String,
        durationMinutes: Int,
        location: String,
        lessonType: String,
        note: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                if (scheduleId == null) {
                    lessonScheduleRepo.addSchedule(
                        studentName = student.name,
                        studentId = student.studentId,
                        coachName = coachName,
                        dayOfWeek = dayOfWeek,
                        startTime = startTime.trim(),
                        durationMinutes = durationMinutes,
                        location = location.trim(),
                        lessonType = lessonType.trim().ifBlank { "训练课" },
                        note = note.trim(),
                        isLongTerm = true
                    )
                } else {
                    val existing = lessonScheduleRepo.getById(scheduleId)
                        ?: throw NoSuchElementException("排课记录不存在或已被删除")
                    lessonScheduleRepo.updateSchedule(
                        existing.copy(
                            studentName = student.name,
                            studentId = student.studentId,
                            dayOfWeek = dayOfWeek,
                            startTime = startTime.trim(),
                            durationMinutes = durationMinutes,
                            location = location.trim(),
                            lessonType = lessonType.trim().ifBlank { existing.lessonType },
                            note = note.trim()
                        )
                    )
                }
                onDone(true)
            } catch (e: Exception) {
                _toast.value = when (e) {
                    is com.shangmentiyu.sportscoach.data.repo.CoachConflictException -> e.userMessage
                    is NoSuchElementException -> e.message
                    else -> "保存失败：${e.message}"
                }
                onDone(false)
            }
        }
    }

    /** 删除教练排课 */
    fun deleteCoachLesson(scheduleId: String) {
        viewModelScope.launch {
            runCatching { lessonScheduleRepo.deleteSchedule(scheduleId) }
                .onFailure { _toast.value = "删除失败：${it.message}" }
        }
    }

    /** 保存可上课时段（重叠冲突 → toast） */
    fun saveSlot(slot: CoachSchedule) {
        viewModelScope.launch {
            try {
                scheduleRepo.upsert(slot)
            } catch (e: IllegalArgumentException) {
                _toast.value = e.message
            }
        }
    }

    fun deleteSlot(id: Long) {
        viewModelScope.launch { scheduleRepo.delete(id) }
    }

    /** 结算期各教练消课数（已签退） */
    suspend fun consumedCounts(start: String, end: String): Map<String, Int> =
        scheduleRepo.consumedCountByCoach(start, end).associate { it.name to it.count }

    // ---------- 薪资 ----------

    private val _selectedMonth = MutableStateFlow(YearMonth.now().toString())
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    /** 结算期 [start, end]（整月） */
    fun periodOf(month: String): Pair<String, String> {
        val ym = try { YearMonth.parse(month) } catch (_: Exception) { YearMonth.now() }
        return ym.atDay(1).format(dateFormatter) to ym.atEndOfMonth().format(dateFormatter)
    }

    fun shiftMonth(delta: Long) {
        val ym = try { YearMonth.parse(_selectedMonth.value) } catch (_: Exception) { YearMonth.now() }
        _selectedMonth.value = ym.plusMonths(delta).toString()
    }

    val payouts: StateFlow<List<CoachPayout>> = payrollRepo.getAllPayouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val requests: StateFlow<List<CoachPayoutRequest>> = payrollRepo.getRequests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 生成当月结算 */
    fun settle(orgNetProfit: Double) {
        viewModelScope.launch {
            val (start, end) = periodOf(_selectedMonth.value)
            val count = try {
                payrollRepo.settlePeriod(start, end, orgNetProfit)
            } catch (e: Exception) {
                _toast.value = "结算失败：${e.message}"
                return@launch
            }
            _toast.value = if (count == 0) "没有可结算的教练（在职/休假）" else "已生成 $count 位教练的结算单"
        }
    }

    fun markPayoutStatus(id: Long, status: String) {
        viewModelScope.launch { payrollRepo.updatePayoutStatus(id, status) }
    }

    fun submitWithdraw(coachName: String, amount: Double, note: String) {
        viewModelScope.launch {
            try {
                payrollRepo.submitRequest(coachName, amount, note)
                _toast.value = "提现申请已提交，待审核"
            } catch (e: IllegalArgumentException) {
                _toast.value = e.message
            }
        }
    }

    fun reviewRequest(id: Long, approved: Boolean) {
        viewModelScope.launch { payrollRepo.reviewRequest(id, approved) }
    }

    /** 生成报表文本（UI 层调用系统分享） */
    suspend fun buildReport(month: String): String {
        val (start, end) = periodOf(month)
        return payrollRepo.buildReportText(start, end)
    }

    // ---------- 提示 ----------

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun clearToast() { _toast.value = null }

    companion object {
        fun monthLabel(month: String): String = try {
            val ym = YearMonth.parse(month)
            "${ym.year}年${ym.monthValue}月"
        } catch (_: Exception) { month }

        fun todayString(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }
}
