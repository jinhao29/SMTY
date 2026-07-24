package com.shangmentiyu.sportscoach.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 签到结果：携带消课状态供 UI 反馈。
 */
data class SignResult(
    val lessonId: String,
    val consumed: Boolean,
    val packageName: String,
    val remainingAfter: Int,
    val message: String
)

/**
 * 首页 ViewModel：管理学员列表、课程统计、签到消课流程与续费提醒。
 *
 * 协调 StudentRepository / LessonRepository / OperationRepository 完成：
 * - 学员列表与课程统计展示
 * - 签到流程：消课 + 写课时记录在同一个 DB 事务中，保证原子性
 * - 续费提醒：观察所有课时包，过滤需续费项
 * - 学员剩余课时映射：用于列表卡片徽章显示
 * - 今日已签到课时列表：供课后反馈 Tab 使用
 */
class HomeViewModel(
    private val studentRepo: StudentRepository,
    private val lessonRepo: LessonRepository,
    private val opRepo: OperationRepository,
    private val db: AppDatabase
) : ViewModel() {

    val students: StateFlow<List<Student>> = studentRepo.getAllStudents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayCount: StateFlow<Int> = lessonRepo.getTodayCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCount: StateFlow<Int> = lessonRepo.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 续费提醒列表（按学员聚合） */
    val renewalAlerts: StateFlow<List<OperationRepository.RenewalAlert>> = opRepo.getRenewalAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 学员姓名 → 剩余课时总数（用于列表徽章显示） */
    val remainingMap: StateFlow<Map<String, Int>> = opRepo.getAllPackages()
        .map { list ->
            list.filter { !it.isExpired && it.status != "已退费" }
                .groupBy { it.studentName }
                .mapValues { (_, pkgs) -> pkgs.sumOf { it.remainingLessons } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 今日已签到课时列表（供课后反馈 Tab 使用） */
    val todayLessons: StateFlow<List<Lesson>> = lessonRepo.getAllLessons()
        .map { all -> all.filter { it.date == todayStr() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun clearToast() {
        _toast.value = null
    }

    private fun toast(msg: String) {
        _toast.value = msg
    }

    /**
     * 签到：消课 + 写课时记录在同一个 DB 事务中执行，保证原子性。
     */
    fun sign(studentName: String, onCreated: (SignResult) -> Unit) {
        viewModelScope.launch {
            val (consume, lessonId) = db.withTransaction {
                val consume = opRepo.consumeLesson(studentName)
                if (!consume.success) {
                    return@withTransaction consume to null
                }
                val lid = lessonRepo.createLesson(
                    studentName = studentName,
                    coach = "",
                    packageId = consume.packageId
                )
                consume to lid
            }
            onCreated(
                SignResult(
                    lessonId = lessonId ?: "",
                    consumed = consume.success,
                    packageName = consume.packageName,
                    remainingAfter = consume.remainingAfter,
                    message = if (consume.success) consume.message
                              else "签到失败：${consume.message}（请确认学员已购买课时包）"
                )
            )
        }
    }

    fun addStudent(
        name: String, gender: String, grade: String, school: String, phone: String,
        age: Int = 0, heightCm: Int = 0, weightKg: Float = 0f, bmi: Float = 0f
    ) {
        viewModelScope.launch {
            studentRepo.addStudent(name, gender, grade, school, phone, age, heightCm, weightKg, bmi)
        }
    }

    fun addStudentWithPackage(
        name: String, gender: String, grade: String, school: String, phone: String,
        age: Int, heightCm: Int, weightKg: Float, bmi: Float,
        packageName: String, packageTotal: Int, price: Double,
        purchaseDate: String, expireDate: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            studentRepo.addStudent(name, gender, grade, school, phone, age, heightCm, weightKg, bmi)
            if (packageTotal > 0) {
                val pkg = com.shangmentiyu.sportscoach.data.model.LessonPackage(
                    studentName = name,
                    name = packageName.ifBlank { "${packageTotal}次卡" },
                    totalLessons = packageTotal,
                    price = price,
                    purchaseDate = purchaseDate,
                    expireDate = expireDate
                )
                opRepo.addPackage(pkg)
            }
            onDone()
        }
    }

    fun updateStudent(
        original: Student, gender: String, grade: String, school: String, phone: String,
        age: Int, heightCm: Int, weightKg: Float, bmi: Float
    ) {
        viewModelScope.launch {
            studentRepo.updateStudent(
                original.copy(
                    gender = gender, grade = grade, school = school, phone = phone,
                    age = age, heightCm = heightCm, weightKg = weightKg, bmi = bmi,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * 更新学员全部信息（含姓名）：在同一 DB 事务中级联改名 + 更新其他字段。
     *
     * - 姓名变化时：先重名校验，再级联更新 7 张表的 studentName，最后更新 students 表其他字段
     * - 姓名未变时：等价于 [updateStudent]
     *
     * @param original 原学员对象
     * @param newName 新姓名（可与原姓名相同）
     * @param onDone 完成回调（主线程），(成功, 消息)
     */
    fun updateStudentFull(
        original: Student,
        newName: String,
        gender: String, grade: String, school: String, phone: String,
        age: Int, heightCm: Int, weightKg: Float, bmi: Float,
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (newName.isBlank()) {
            onDone(false, "姓名不能为空")
            return
        }
        viewModelScope.launch {
            try {
                if (original.name != newName) {
                    db.withTransaction {
                        // 重名校验：确保新姓名尚未占用
                        if (db.studentDao().getByName(newName) != null) {
                            throw IllegalArgumentException("学员「$newName」已存在")
                        }
                        // 级联改名 7 张表
                        db.studentDao().renameStudent(original.name, newName)
                        db.lessonDao().renameStudent(original.name, newName)
                        db.lessonPackageDao().renameStudent(original.name, newName)
                        db.scheduleDao().renameStudent(original.name, newName)
                        db.bodyMetricHistoryDao().renameStudent(original.name, newName)
                        db.parentReportDao().renameStudent(original.name, newName)
                        db.trainingCycleDao().renameStudent(original.name, newName)
                        // 更新 students 表其他字段（此时姓名已是 newName）
                        db.studentDao().update(
                            original.copy(
                                name = newName,
                                gender = gender, grade = grade, school = school, phone = phone,
                                age = age, heightCm = heightCm, weightKg = weightKg, bmi = bmi,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    toast("已更新「$newName」的全部信息，全局数据已同步")
                } else {
                    // 姓名未变，直接更新其他字段
                    studentRepo.updateStudent(
                        original.copy(
                            gender = gender, grade = grade, school = school, phone = phone,
                            age = age, heightCm = heightCm, weightKg = weightKg, bmi = bmi,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    toast("已更新「$newName」的信息")
                }
                onDone(true, "更新成功")
            } catch (e: Exception) {
                val msg = e.message ?: "更新失败"
                toast("更新失败：$msg")
                onDone(false, msg)
            }
        }
    }

    /**
     * 删除学员：在同一个 DB 事务中级联删除该学员的全部相关数据，
     * 保证全局数据一致，避免遗留孤立记录。
     *
     * 涉及表：students / lessons / lesson_packages / schedules /
     *         body_metric_history / parent_reports / training_cycles
     *
     * @param name 学员姓名
     * @param onDone 删除完成回调（主线程）
     */
    fun deleteStudent(name: String, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        if (name.isBlank()) {
            onDone(false, "姓名不能为空")
            return
        }
        viewModelScope.launch {
            try {
                db.withTransaction {
                    db.studentDao().deleteByName(name)
                    db.lessonDao().deleteByStudent(name)
                    db.lessonPackageDao().deleteByStudent(name)
                    db.scheduleDao().deleteByStudent(name)
                    db.bodyMetricHistoryDao().deleteByStudent(name)
                    db.parentReportDao().deleteByStudent(name)
                    db.trainingCycleDao().deleteByStudent(name)
                }
                toast("已删除学员 $name 及其全部数据")
                onDone(true, "删除成功")
            } catch (e: Exception) {
                val msg = e.message ?: "删除失败"
                toast("删除失败：$msg")
                onDone(false, msg)
            }
        }
    }

    /**
     * 更新课时反馈信息（教练寄语、表现评分、训练态度）。
     *
     * 用于课后反馈 Tab 的内联评分：教练在列表中直接编辑寄语与评分，
     * 通过此方法持久化到 Lesson 表。今日课时列表基于 [todayLessons] Flow，
     * 数据库变更会自动回流到 UI，无需手动刷新。
     *
     * @param lessonId 课时 ID
     * @param coachComment 教练寄语
     * @param performance 表现评分（1-10）
     * @param attitude 训练态度
     */
    fun updateLessonFeedback(
        lessonId: String,
        coachComment: String,
        performance: Int,
        attitude: String
    ) {
        viewModelScope.launch {
            val lesson = lessonRepo.getById(lessonId) ?: return@launch
            lessonRepo.updateLesson(
                lesson.copy(
                    coachComment = coachComment,
                    performance = performance,
                    attitude = attitude
                )
            )
        }
    }

    /**
     * 更新课时详细信息（课后反馈 Tab 内联编辑使用）。
     *
     * 支持 Coach 在课后反馈 Tab 直接修改以下字段：
     * - 课时类型（lessonType）
     * - 教练（coach）
     * - 课时时长（duration）
     * - 地点（location）
     * - 出勤状态（attendance）
     *
     * 数据库变更会通过 [todayLessons] Flow 自动回流到 UI。
     *
     * @param lessonId 课时 ID
     * @param lessonType 课时类型
     * @param coach 教练姓名
     * @param duration 课时时长（分钟）
     * @param location 上课地点
     * @param attendance 出勤状态
     */
    fun updateLessonDetail(
        lessonId: String,
        lessonType: String,
        coach: String,
        duration: Int,
        location: String,
        attendance: String
    ) {
        viewModelScope.launch {
            val lesson = lessonRepo.getById(lessonId) ?: return@launch
            lessonRepo.updateLesson(
                lesson.copy(
                    lessonType = lessonType,
                    coach = coach,
                    duration = duration,
                    location = location,
                    attendance = attendance
                )
            )
        }
    }

    /**
     * 更新课后反馈训练内容图片列表。
     *
     * 用户需求：课后反馈的训练内容可以添加图片便于反馈给家长。
     * 图片路径以 JSON 字符串数组形式存储在 Lesson.contentImages 字段。
     *
     * @param lessonId 课时 ID
     * @param imagePaths 图片路径列表（应用内部存储绝对路径）
     */
    fun updateLessonImages(lessonId: String, imagePaths: List<String>) {
        viewModelScope.launch {
            val lesson = lessonRepo.getById(lessonId) ?: return@launch
            val json = org.json.JSONArray().apply {
                imagePaths.forEach { put(it) }
            }.toString()
            lessonRepo.updateLesson(lesson.copy(contentImages = json))
        }
    }

    /** 解析 Lesson.contentImages JSON 为图片路径列表 */
    fun parseLessonImages(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        val arr = com.shangmentiyu.sportscoach.core.JsonSafe.parseArray(json) ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val path = arr.optString(i)
            if (path.isNotBlank()) result.add(path)
        }
        return result
    }

    /**
     * 删除课时记录（课后反馈 Tab 删除按钮使用）。
     *
     * 注意：此操作仅删除 Lesson 表中的课时记录，不会退还已扣减的课时包次数。
     * 若需退还课时，请在课包管理中手动调整。
     *
     * @param lessonId 课时 ID
     * @param onDone 删除完成回调（主线程），参数为是否成功
     */
    fun deleteLesson(lessonId: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                lessonRepo.deleteLesson(lessonId)
                toast("课时记录已删除")
                onDone(true)
            } catch (e: Exception) {
                toast("删除失败：${e.message ?: "未知错误"}")
                onDone(false)
            }
        }
    }

    /**
     * 学员改名：在同一个 DB 事务中级联更新 7 张表的 studentName 字段，
     * 保证学员全局数据统一。
     *
     * 涉及表：students / lessons / lesson_packages / schedules /
     *         body_metric_history / parent_reports / training_cycles
     *
     * @param oldName 原姓名
     * @param newName 新姓名
     * @param onDone 改名完成回调（主线程）
     */
    fun renameStudent(oldName: String, newName: String, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        if (oldName.isBlank() || newName.isBlank()) {
            onDone(false, "姓名不能为空")
            return
        }
        if (oldName == newName) {
            onDone(false, "新姓名与原姓名相同")
            return
        }
        viewModelScope.launch {
            try {
                db.withTransaction {
                    // 重名校验：确保新姓名尚未占用
                    if (db.studentDao().getByName(newName) != null) {
                        throw IllegalArgumentException("学员「$newName」已存在")
                    }
                    db.studentDao().renameStudent(oldName, newName)
                    db.lessonDao().renameStudent(oldName, newName)
                    db.lessonPackageDao().renameStudent(oldName, newName)
                    db.scheduleDao().renameStudent(oldName, newName)
                    db.bodyMetricHistoryDao().renameStudent(oldName, newName)
                    db.parentReportDao().renameStudent(oldName, newName)
                    db.trainingCycleDao().renameStudent(oldName, newName)
                }
                toast("已将「$oldName」改名为「$newName」，全局数据已同步")
                onDone(true, "改名成功")
            } catch (e: Exception) {
                val msg = e.message ?: "改名失败"
                toast("改名失败：$msg")
                onDone(false, msg)
            }
        }
    }

    fun addPackage(
        studentName: String, packageName: String, totalLessons: Int,
        price: Double, purchaseDate: String, expireDate: String
    ) {
        viewModelScope.launch {
            val pkg = com.shangmentiyu.sportscoach.data.model.LessonPackage(
                studentName = studentName,
                name = packageName.ifBlank { "${totalLessons}次卡" },
                totalLessons = totalLessons,
                price = price,
                purchaseDate = purchaseDate,
                expireDate = expireDate
            )
            opRepo.addPackage(pkg)
            toast("已为 $studentName 添加 ${totalLessons} 课时")
        }
    }

    private fun todayStr(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
}
