package com.shangmentiyu.sportscoach.ui.operation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.ExerciseItem
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.model.ScheduleMemory
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.ScheduleMemoryRepository
import com.shangmentiyu.sportscoach.data.repo.ScheduleRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import com.shangmentiyu.sportscoach.ui.schedule.ScheduleForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 运营管理 ViewModel（协调层）。
 *
 * 统一管理三个子领域：
 * - 排课（Schedule）：周视图、新增/编辑/启停，支持训练内容/颜色/课前任务完整编辑
 * - 课程包（LessonPackage）：余额追踪、续费提醒、扣减
 * - 教练（Coach）：增删改查
 *
 * 排课功能合并自原 ScheduleViewModel，通过 [scheduleRepo] 提供
 * 训练内容 JSON 解析与完整 CRUD，使运营管理成为唯一的排课入口。
 */
class OperationViewModel(
    private val opRepo: OperationRepository,
    private val studentRepo: StudentRepository,
    private val scheduleRepo: ScheduleRepository,
    private val memoryRepo: ScheduleMemoryRepository
) : ViewModel() {

    /**
     * 日期格式化工具：[SimpleDateFormat] 非线程安全，禁止作为成员变量持有，
     * 每次调用新建实例，避免多协程并发解析时 Calendar 状态污染。
     */
    private fun todayStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    // === 排课 ===
    val schedules: StateFlow<List<Schedule>> = opRepo.getActiveSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前选中的周几（1-7），默认今天 */
    private val _selectedDay = MutableStateFlow(getTodayDayOfWeek())
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    /** 当前周的起始日期（周一） */
    private val _weekStart = MutableStateFlow(getWeekStart())
    val weekStart: StateFlow<Date> = _weekStart.asStateFlow()

    // === 课程包 ===
    val packages: StateFlow<List<LessonPackage>> = opRepo.getActivePackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 需要续费提醒的课程包 */
    val renewalAlerts: StateFlow<List<LessonPackage>> = packages.map { list ->
        list.filter { it.needsRenewal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === 教练 ===
    val coaches: StateFlow<List<Coach>> = opRepo.getActiveCoaches()
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

    // === 操作结果提示 ===
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun showToast(msg: String) { _toast.value = msg }
    fun clearToast() { _toast.value = null }

    // === 排课操作 ===
    fun selectDay(day: Int) { _selectedDay.value = day }

    /** 切换到上一周/下一周 */
    fun shiftWeek(days: Int) {
        val cal = Calendar.getInstance()
        cal.time = _weekStart.value
        cal.add(Calendar.DATE, days)
        _weekStart.value = cal.time
    }

    /** 当前编辑中的排课（null=新建模式） */
    private val _editingSchedule = MutableStateFlow<Schedule?>(null)
    val editingSchedule: StateFlow<Schedule?> = _editingSchedule.asStateFlow()

    /** 进入新建模式 */
    fun startCreate() { _editingSchedule.value = null }

    /** 进入编辑模式：异步加载原数据 */
    fun startEdit(id: String) {
        viewModelScope.launch { _editingSchedule.value = scheduleRepo.getById(id) }
    }

    /** 退出编辑 */
    fun cancelEdit() { _editingSchedule.value = null }

    /**
     * 保存排课（新建或更新），支持训练内容/颜色/上课器材完整字段。
     *
     * 新增功能：
     * - [ScheduleForm.isLongTerm]：勾选长期后，每周自动生成对应时间的课时记录
     * - 上课时间/地点会自动保存到 schedule_memory 表，供下次排课下拉选择
     */
    fun saveSchedule(form: ScheduleForm) {
        if (form.studentName.isBlank()) { _toast.value = "请选择学员"; return }
        if (form.startTime.isBlank()) { _toast.value = "请填写上课时间"; return }
        viewModelScope.launch {
            val editing = _editingSchedule.value
            val coachKey = form.coachName.ifBlank { "默认教练" }
            // 保存时间/地点记忆（重复则更新 updatedAt）
            memoryRepo.saveMemory(coachKey, "time", form.startTime.trim())
            if (form.location.isNotBlank()) {
                memoryRepo.saveMemory(coachKey, "location", form.location.trim())
            }

            if (editing == null) {
                scheduleRepo.addSchedule(
                    studentName = form.studentName,
                    coachName = form.coachName,
                    dayOfWeek = form.dayOfWeek,
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
                _toast.value = if (form.isLongTerm) "长期课程已添加（每周自动生成课记录）" else "课程已添加"
            } else {
                scheduleRepo.updateSchedule(
                    editing.copy(
                        studentName = form.studentName,
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
                )
                _toast.value = "课程已更新"
            }
            _editingSchedule.value = null
        }
    }

    /**
     * 长期排课自动生成本周课记录：
     * 进入排课页/每日计划页时检查所有 isLongTerm=true 的排课，
     * 为本周对应 dayOfWeek 自动生成一条 Lesson 记录（若当天尚无对应记录）。
     *
     * 约束：
     * - 仅对本周（_weekStart 起算 7 天内）的每个长期排课生成一次
     * - 同一学员+同一日期+同一 startTime 已存在 Lesson 时跳过，避免重复
     * - 自动生成的 Lesson 标记 lessonType = "长期自动"，便于区分手动签到
     */
    fun ensureLongTermLessonsForWeek() {
        viewModelScope.launch {
            val allSchedules = scheduleRepo.getAllSchedulesOnce()
            val longTerm = allSchedules.filter { it.isActive && it.isLongTerm }
            if (longTerm.isEmpty()) return@launch
            val cal = Calendar.getInstance()
            val weekStartCal = Calendar.getInstance().apply {
                time = _weekStart.value
                set(Calendar.HOUR_OF_DAY, 0)
                clear(Calendar.MINUTE); clear(Calendar.SECOND); clear(Calendar.MILLISECOND)
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            // 遍历本周 7 天
            for (offset in 0..6) {
                val dayCal = Calendar.getInstance().apply {
                    time = weekStartCal.time
                    add(Calendar.DATE, offset)
                }
                val dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK).let { if (it == 1) 7 else it - 1 }
                val dateStr = sdf.format(dayCal.time)
                longTerm.filter { it.dayOfWeek == dayOfWeek }.forEach { sched ->
                    if (!opRepo.hasLessonForScheduleOnDate(sched.studentName, dateStr, sched.startTime)) {
                        opRepo.generateLongTermLesson(sched, dateStr)
                    }
                }
            }
        }
    }

    /** 解析训练内容 JSON */
    fun parseContent(json: String): List<ExerciseItem> = scheduleRepo.parseContent(json)

    /** 解析上课器材 JSON */
    fun parseEquipment(json: String): List<String> = scheduleRepo.parseEquipment(json)

    /** 解析训练内容图片路径 JSON */
    fun parseImages(json: String): List<String> = scheduleRepo.parseImages(json)

    fun toggleScheduleActive(schedule: Schedule) {
        viewModelScope.launch {
            opRepo.updateSchedule(schedule.copy(isActive = !schedule.isActive))
            _toast.value = if (schedule.isActive) "已暂停" else "已启用"
        }
    }

    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            opRepo.deleteSchedule(id)
            _toast.value = "已删除"
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
        viewModelScope.launch {
            opRepo.addPackage(
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
        viewModelScope.launch {
            opRepo.deletePackage(id)
            _toast.value = "已删除"
        }
    }

    /**
     * 调整课时包课时数（正数增添，负数减少）。
     * 同步修改 totalLessons 与 remainingLessons，保持已用课时数不变。
     */
    fun adjustPackage(packageId: String, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            val pkg = packages.value.firstOrNull { it.id == packageId } ?: return@launch
            // remainingLessons 是计算属性 = totalLessons - usedLessons
            // 增添：totalLessons += delta，usedLessons 不变
            // 减少：totalLessons -= delta，但不低于 usedLessons
            val newTotal = if (delta > 0) {
                pkg.totalLessons + delta
            } else {
                (pkg.totalLessons + delta).coerceAtLeast(pkg.usedLessons)
            }
            opRepo.updatePackage(pkg.copy(totalLessons = newTotal))
            _toast.value = if (delta > 0) "已增添 $delta 课时" else "已减少 ${-delta} 课时"
        }
    }

    /**
     * 额外赠送课时：为学员创建一个独立的赠送课时包。
     * 不影响原套餐数据，单独追踪赠送课时的使用情况。
     */
    fun giftLessons(studentName: String, count: Int) {
        if (count <= 0) return
        viewModelScope.launch {
            opRepo.addPackage(
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
        viewModelScope.launch {
            opRepo.updatePackage(pkg)
            _toast.value = "课时包已更新"
        }
    }

    // === 教练操作 ===
    fun addCoach(name: String, phone: String, specialty: String) {
        viewModelScope.launch {
            if (opRepo.getCoachByName(name) != null) {
                _toast.value = "教练已存在"
                return@launch
            }
            opRepo.upsertCoach(
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
        viewModelScope.launch {
            opRepo.deleteCoach(name)
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
