package com.shangmentiyu.sportscoach.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.SettingsRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    private val db: AppDatabase,
    private val settingsRepo: SettingsRepository? = null,
    /**
     * === v5 新增：精彩瞬间上传器（手机→PC 双向传输） ===
     * 由 Koin 依赖注入（di/AppModule）提供。
     * null 时调用 [uploadMoment] 直接返回失败，不影响应用启动。
     */
    private val momentUploader: com.shangmentiyu.sportscoach.core.MomentUploader? = null
) : ViewModel() {

    // === 修复：将 _toast 与 appExceptionHandler 提前到 init 块之前 ===
    // 原因：Kotlin 按声明顺序初始化，init 块在第 48 行调用 safeLaunch 时，
    // appExceptionHandler（原第 195 行）尚未初始化，导致 viewModelScope.launch(null)
    // 触发 CoroutineContext.fold 的 NPE（"Attempt to invoke interface method
    // 'java.lang.Object kotlin.coroutines.CoroutineContext.fold(...)' on a null object reference"）。
    // 解决：把异常处理器与 toast sink 的声明移到 init 块之前，确保初始化时已有值。
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /**
     * === v24 优化4：全局协程异常捕获 ===
     *
     * 应用级异常处理器：拦截签到 / 消课 / 学员增删 / 数据库事务等过程中
     * 可能出现的 SQLite 异常、IO 异常，避免 App 闪退。
     * - 异常落盘：通过 [com.shangmentiyu.sportscoach.core.CrashHandler.writeLog]
     * - UI 反馈：通过 [_toast] 推送轻量提示
     */
    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.core.CoroutineExt.createAppExceptionHandler(
            toastSink = _toast,
            contextTag = "HomeViewModel"
        )

    /**
     * 安全启动协程：自动挂载 [appExceptionHandler]，未捕获异常不会导致 App 崩溃。
     * 闭包签名兼容 [viewModelScope.launch]，便于直接替换。
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

    init {
        // === v28 优化1：App 启动时自动触发冷热数据迁移检查 ===
        // 触发条件：lessons 表 > 2000 条 + 存在 365 天前的旧记录
        // 仅在后台异步执行，不阻塞 UI；失败仅写日志，不影响用户体验
        safeLaunch {
            try {
                val result = opRepo.maybeAutoArchiveIfNeeded()
                if (result.archivedCount > 0) {
                    android.util.Log.i("HomeViewModel",
                        "自动归档完成：${result.message}")
                    // 归档后刷新今日课时统计（todayCount / totalCount 自动回流）
                    toast("已自动归档 ${result.archivedCount} 条历史课时")
                }
            } catch (e: Exception) {
                android.util.Log.w("HomeViewModel",
                    "自动归档检查失败：${e.message}")
            }
        }
    }

    /**
     * 上传学员精彩瞬间照片到桌面端（手机→PC 双向传输）。
     *
     * 调用时机：PreClassTab 卡片"上传精彩瞬间"按钮 →
     * 通过 ActivityResultContracts.PickVisualMedia 选择照片后调用。
     *
     * @param photoUri 图库返回的照片 Uri
     * @param studentName 学员姓名（用于服务端命名）
     * @return 上传结果消息（成功 / 失败提示，UI 直接 Toast）
     */
    suspend fun uploadMoment(photoUri: android.net.Uri, studentName: String): String {
        val uploader = momentUploader
            ?: return "未启用桌面同步，请在设置中配置"
        return try {
            val res = uploader.upload(photoUri, studentName)
            if (res.success) {
                "已上传到 PC 端"
            } else {
                res.message
            }
        } catch (e: Exception) {
            "上传异常：${e.message ?: "未知错误"}"
        }
    }

    val students: StateFlow<List<Student>> = studentRepo.getAllStudents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === v48 终极打磨：学员列表首帧加载标记（骨架屏） ===
    // Room Flow 首帧异步到达，之前 UI 无法区分"加载中"与"无学员"，
    // 首帧到达后置 true，UI 据此从骨架屏切换到真实列表/空状态。
    private val _studentsLoaded = MutableStateFlow(false)
    val studentsLoaded: StateFlow<Boolean> = _studentsLoaded.asStateFlow()

    init {
        viewModelScope.launch(appExceptionHandler) {
            students.first()
            _studentsLoaded.value = true
        }
    }

    val todayCount: StateFlow<Int> = lessonRepo.getTodayCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCount: StateFlow<Int> = lessonRepo.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 续费提醒列表（按学员聚合） */
    val renewalAlerts: StateFlow<List<OperationRepository.RenewalAlert>> = opRepo.getRenewalAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * === v25 优化1：全局到期预警课时包列表 ===
     *
     * 监听 7 天内即将到期的活跃课时包，用于首页顶部横幅展示。
     * 列表按到期天数升序，最快过期的排最前。
     */
    val expiringPackages: StateFlow<List<com.shangmentiyu.sportscoach.data.model.LessonPackage>> =
        opRepo.getExpiringPackages(daysThreshold = 7)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * === v25 优化1：预警横幅文案（聚合首条最紧急的预警，用于顶部横幅） ===
     *
     * 取到期最近的一条课时包生成提醒文案，例：
     * "【提醒】陈书楠的「15次全能卡」将于 3 天后过期，剩余 5 节课，建议尽快安排！"
     *
     * 无预警时返回 null，UI 据此隐藏横幅。
     */
    val expiringBannerText: StateFlow<String?> = expiringPackages
        .map { list ->
            val pkg = list.firstOrNull() ?: return@map null
            val days = pkg.daysToExpiry()
            val daysText = when {
                days <= 0 -> "今日到期"
                days == 1 -> "明日到期"
                else -> "将于 ${days} 天后过期"
            }
            "【提醒】${pkg.studentName}的「${pkg.name}」$daysText，剩余 ${pkg.remainingLessons} 节课，建议尽快安排！"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 学员姓名 → 剩余课时总数（用于列表徽章显示） */
    val remainingMap: StateFlow<Map<String, Int>> = opRepo.getAllPackages()
        .map { list ->
            list.filter { !it.isExpired && it.status != "已退费" }
                .groupBy { it.studentName }
                .mapValues { (_, pkgs) -> pkgs.sumOf { it.remainingLessons } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 今日已签到课时列表（供课后反馈 Tab 使用） */
    // 优化：直接用 SQL WHERE date = today 查询，命中 idx_lessons_date 索引，
    // 避免加载全部历史课时再内存过滤（15000 条时可节省 50-150ms 主线程耗时）。
    val todayLessons: StateFlow<List<Lesson>> = lessonRepo.getTodayLessons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * === 所有签到签退课时记录（供课后反馈 Tab 按日期/学员展示全部历史） ===
     *
     * 数据来源：lessons 表全量，按 date DESC, time DESC 排序（DAO 已保证）。
     * 用途：课后反馈 Tab 从"仅今日"扩展为"全部历史记录"，
     * 每条记录对应学员与日期，支持按日期分组、按学员筛选。
     *
     * 性能：依赖 Room Flow 自动增量更新，仅数据库变更时回流，
     * LazyColumn 仅组合可见项，未可见项自动回收。
     */
    val allLessons: StateFlow<List<Lesson>> = lessonRepo.getAllLessons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * === v28 优化6：今日排课数（用于首页 Tab 红点/数字角标） ===
     *
     * 数据来源：schedules 表中 isActive=1 且 dayOfWeek 等于今日星期几的排课。
     * 由于排课是周期性（按周几重复），今日排课数即"今天本应上的课节数"。
     *
     * 与 [todayLessons] 区别：
     * - [todayScheduleCount] 表示"今日应到"（基于周期性排课）
     * - [todayLessons] 表示"今日已签到"（基于 lessons 表 date=today）
     * - 差值即为"未签到数"，用于 Tab 角标显示
     */
    val todayScheduleCount: StateFlow<Int> = opRepo.getSchedulesByDay(todayDayOfWeek())
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * === v28 优化6：今日未签到数（用于 Tab 角标数字显示） ===
     *
     * 计算公式：max(0, [todayScheduleCount] - [todayLessons].size)
     *
     * 含义：今天本应签到 N 节课，已签到 M 节，还有 N-M 节未签到。
     * 当该值 > 0 时，在首页 Tab 上显示红色数字角标，给教练强烈的心理提示。
     */
    val unsignedTodayCount: StateFlow<Int> =
        kotlinx.coroutines.flow.combine(todayScheduleCount, todayLessons) { schedCnt, lessons ->
            (schedCnt - lessons.size).coerceAtLeast(0)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * === v28 优化6：是否显示 Tab 红点（今日有排课即显示） ===
     *
     * 触发条件：[todayScheduleCount] > 0
     * 与 [unsignedTodayCount] 配合：
     * - 红点（小圆点）：表示"今日有排课"
     * - 数字角标：表示"今日未签到数"，未签到数为 0 时不显示数字
     */
    val hasTodayScheduleBadge: StateFlow<Boolean> = todayScheduleCount
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * 学员姓名 → 下一节课（含今日及未来，按日期/时间升序取首条）。
     *
     * 数据来源：[LessonRepository.getUpcomingFrom] 查询 date >= today 且未签退的全部课时。
     * 用途：学员列表"下一节课"显示与修改入口。
     *
     * 重要：过滤已签退课时（signOutTime 非空）——签退后的课时视为已完成，
     * 不应再作为"下一节课"显示给教练。修复了"课后反馈签退后仍显示下一节课"的问题。
     */
    val nextLessons: StateFlow<Map<String, Lesson>> = lessonRepo.getUpcomingFrom(todayStr())
        .map { lessons ->
            lessons.groupBy { it.studentName }
                .mapNotNull { (name, list) -> list.minByOrNull { "${it.date} ${it.time}" }?.let { name to it } }
                .toMap()
        }
        .distinctUntilChanged { old, new ->
            if (old.size != new.size) return@distinctUntilChanged false
            old.entries.all { (k, v) ->
                val nv = new[k]
                nv != null && nv.date == v.date && nv.time == v.time && nv.location == v.location
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ==================== v24 优化5：学员列表高级筛选与排序 ====================

    /**
     * 学员排序方式。
     *
     * - [Default]：按数据库原顺序（即插入顺序）
     * - [RemainingAsc]：剩余课时升序（余额少的排前面，便于教练优先关注续费学员）
     * - [UpcomingFirst]：今日/明日有课的学员排前面
     * - [NamePinyin]：按姓名拼音字典序（A → Z）
     */
    enum class StudentSortBy {
        Default, RemainingAsc, UpcomingFirst, NamePinyin
    }

    /**
     * 年级筛选枚举（13 级系统：小学 1-6 / 初中 7-9 / 高中 10-12 / 中考）。
     *
     * - [All]：不筛选（默认）
     * - [Primary]：小学 1-6 年级
     * - [Junior]：初中 7-9 年级
     * - [Senior]：高中 10-12 年级
     * - [ZhongKao]：中考（备考阶段）
     */
    enum class GradeFilter {
        All, Primary, Junior, Senior, ZhongKao
    }

    /** 当前排序方式 */
    private val _sortBy = MutableStateFlow(StudentSortBy.Default)
    val sortBy: StateFlow<StudentSortBy> = _sortBy.asStateFlow()

    /** 当前年级筛选 */
    private val _gradeFilter = MutableStateFlow(GradeFilter.All)
    val gradeFilter: StateFlow<GradeFilter> = _gradeFilter.asStateFlow()

    /** 姓名拼音/汉字模糊查询关键字 */
    private val _nameQuery = MutableStateFlow("")
    val nameQuery: StateFlow<String> = _nameQuery.asStateFlow()

    /**
     * 内部聚合的筛选状态（合并排序+年级+姓名三者为单一 StateFlow）。
     *
     * 设计目的：kotlinx.coroutines.flow.combine 仅支持 2-5 个 Flow 的直接重载，
     * 将三个筛选条件打包成 [FilterState] 后只需 combine 4 个流即可完成计算，
     * 避免使用 vararg 版本的反射式 transform。
     */
    private data class FilterState(
        val sortBy: StudentSortBy,
        val gradeFilter: GradeFilter,
        val nameQuery: String
    )

    /** 聚合筛选状态：任一子条件变更时整体发射新值 */
    private val filterState: StateFlow<FilterState> =
        kotlinx.coroutines.flow.combine(_sortBy, _gradeFilter, _nameQuery) { sort, grade, query ->
            FilterState(sort, grade, query)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
            FilterState(StudentSortBy.Default, GradeFilter.All, ""))

    fun setSortBy(sort: StudentSortBy) { _sortBy.value = sort }
    fun setGradeFilter(filter: GradeFilter) { _gradeFilter.value = filter }
    fun setNameQuery(q: String) { _nameQuery.value = q }

    /** 重置所有筛选条件 */
    fun resetFilters() {
        _sortBy.value = StudentSortBy.Default
        _gradeFilter.value = GradeFilter.All
        _nameQuery.value = ""
    }

    /**
     * 筛选+排序后的学员列表（与 [remainingMap] / [nextLessons] / [filterState] 联合计算）。
     *
     * 计算策略：
     * 1. 名称过滤：包含关键字（不区分大小写、忽略前后空格）
     * 2. 年级过滤：根据 [Student.grade] 字段映射到 [GradeFilter] 范畴
     * 3. 排序：依据 [StudentSortBy] 计算键值后排序（稳定排序，相同键保持原顺序）
     *
     * 性能：所有计算在 Flow.map 中进行，每秒最多触发一次（WhileSubscribed(5000)）。
     */
    val filteredStudents: StateFlow<List<Student>> =
        kotlinx.coroutines.flow.combine(
            students, remainingMap, nextLessons, filterState
        ) { list, remainMap, nextMap, filter ->
            applyFilterAndSort(list, remainMap, nextMap,
                filter.sortBy, filter.gradeFilter, filter.nameQuery)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 内部纯函数：应用筛选与排序（处理器层，便于单元测试） */
    private fun applyFilterAndSort(
        list: List<Student>,
        remainMap: Map<String, Int>,
        nextMap: Map<String, Lesson>,
        sortBy: StudentSortBy,
        gradeFilter: GradeFilter,
        query: String
    ): List<Student> {
        // 1. 名称过滤
        var filtered = if (query.isBlank()) {
            list
        } else {
            val key = query.trim().lowercase(java.util.Locale.getDefault())
            list.filter { it.name.lowercase(java.util.Locale.getDefault()).contains(key) }
        }

        // 2. 年级过滤
        if (gradeFilter != GradeFilter.All) {
            filtered = filtered.filter { matchGradeFilter(it.grade, gradeFilter) }
        }

        // 3. 排序
        val today = todayStr()
        val tomorrow = java.time.LocalDate.now().plusDays(1)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.getDefault()))
        return when (sortBy) {
            StudentSortBy.Default -> filtered
            StudentSortBy.RemainingAsc -> {
                // 余额少（包括 -1 未购课）的排前面，便于教练关注续费学员
                filtered.sortedBy { remainMap[it.name] ?: -1 }
            }
            StudentSortBy.UpcomingFirst -> {
                // 今日/明日有课的学员优先；其余按姓名原顺序
                filtered.sortedBy { s ->
                    val next = nextMap[s.name]
                    if (next != null && (next.date == today || next.date == tomorrow)) 0 else 1
                }
            }
            StudentSortBy.NamePinyin -> {
                // Collator 按拼音排序（中文场景常用）
                val collator = java.text.Collator.getInstance(java.util.Locale.CHINESE)
                filtered.sortedWith(compareBy(collator) { it.name })
            }
        }
    }

    /** 内部工具：将 [Student.grade] 字符串匹配到 [GradeFilter] 范畴 */
    private fun matchGradeFilter(grade: String, filter: GradeFilter): Boolean {
        if (grade.isBlank()) return false
        // 兼容历史：grade 可能是 "3"（小学 3 年级）、"8"（初中 8 年级）、"高一" 等
        val num = grade.toIntOrNull()
        return when (filter) {
            GradeFilter.All -> true
            GradeFilter.Primary -> num != null && num in 1..6
            GradeFilter.Junior -> num != null && num in 7..9
            GradeFilter.Senior -> num != null && num in 10..12
            GradeFilter.ZhongKao -> grade == "中考" || grade.contains("中考")
        }
    }

    fun clearToast() {
        _toast.value = null
    }

    /**
     * === v5 新增：暴露给 UI 层的 toast 触发方法 ===
     * 用于异步任务结果（如精彩瞬间上传）通过 Snackbar 反馈给用户。
     */
    fun showToast(msg: String) {
        _toast.value = msg
    }

    private fun toast(msg: String) {
        _toast.value = msg
    }

    /**
     * === v27 重构：签到不再扣减课时包，仅创建 status="已签到" 的 Lesson ===
     *
     * 新流程：
     * - 签到：仅创建 Lesson(status="已签到", packageId="")，不调用 [OperationRepository.consumeLesson]
     * - 签退：教练保存课后反馈时调用 [saveFeedbackAndCheckOut]，事务内扣减课时包 + 更新 status="已签退"
     *
     * 兼容旧数据：
     * - 老数据 Lesson.packageId 非空但 status 默认为"已签到"（v24 迁移默认值）
     * - 这些数据下次签退时会自动迁移为"已签退"，不影响历史报表
     */
    fun sign(studentName: String, onCreated: (SignResult) -> Unit) {
        safeLaunch {
            try {
                // 签到：仅创建 Lesson，不扣减课时包
                val lid = lessonRepo.createLesson(
                    studentName = studentName,
                    coach = "",
                    packageId = ""  // 签到时不扣减课时包，packageId 留空
                )

                onCreated(
                    SignResult(
                        lessonId = lid,
                        consumed = false,  // 签到不再消费课时
                        packageName = "",
                        remainingAfter = 0,
                        message = "签到成功（签退时再扣减课时）"
                    )
                )
            } catch (e: Exception) {
                onCreated(
                    SignResult(
                        lessonId = "",
                        consumed = false,
                        packageName = "",
                        remainingAfter = 0,
                        message = "签到失败：${e.message ?: "未知异常"}"
                    )
                )
            }
        }
    }

    fun addStudent(
        name: String, gender: String, grade: String, school: String, phone: String,
        age: Int = 0, heightCm: Int = 0, weightKg: Float = 0f, bmi: Float = 0f
    ) {
        safeLaunch {
            try {
                studentRepo.addStudent(name, gender, grade, school, phone, age, heightCm, weightKg, bmi)
            } catch (e: IllegalArgumentException) {
                // v22：输入边界校验失败 → 转 Toast 提示用户
                toast(e.message ?: "学员数据不合法")
            } catch (e: Exception) {
                toast("添加失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun addStudentWithPackage(
        name: String, gender: String, grade: String, school: String, phone: String,
        age: Int, heightCm: Int, weightKg: Float, bmi: Float,
        packageName: String, packageTotal: Int, price: Double,
        purchaseDate: String, expireDate: String,
        onDone: () -> Unit = {}
    ) {
        safeLaunch {
            try {
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
            } catch (e: IllegalArgumentException) {
                // v22：输入边界校验失败 → 转 Toast 提示，不调用 onDone（保持对话框开启）
                toast(e.message ?: "学员数据不合法")
            } catch (e: Exception) {
                toast("添加失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun updateStudent(
        original: Student, gender: String, grade: String, school: String, phone: String,
        age: Int, heightCm: Int, weightKg: Float, bmi: Float
    ) {
        safeLaunch {
            try {
                studentRepo.updateStudent(
                    original.copy(
                        gender = gender, grade = grade, school = school, phone = phone,
                        age = age, heightCm = heightCm, weightKg = weightKg, bmi = bmi,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: IllegalArgumentException) {
                // v22：输入边界校验失败 → 转 Toast 提示
                toast(e.message ?: "学员数据不合法")
            } catch (e: Exception) {
                toast("更新失败：${e.message ?: "未知错误"}")
            }
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
        safeLaunch {
            try {
                if (original.name != newName) {
                    db.withTransaction {
                        // 重名校验：确保新姓名尚未占用
                        if (db.studentDao().getByName(newName) != null) {
                            throw IllegalArgumentException("学员「$newName」已存在")
                        }
                        // 级联改名 7 张表（表访问顺序：students → lessons → schedules →
                        // lesson_packages → training_cycles → body_metric_history → parent_reports）
                        db.studentDao().renameStudent(original.name, newName)
                        db.lessonDao().renameStudent(original.name, newName)
                        db.scheduleDao().renameStudent(original.name, newName)
                        db.lessonPackageDao().renameStudent(original.name, newName)
                        db.trainingCycleDao().renameStudent(original.name, newName)
                        db.bodyMetricHistoryDao().renameStudent(original.name, newName)
                        db.parentReportDao().renameStudent(original.name, newName)
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
     * 删除学员（v20 软删除策略）：
     *
     * 仅将 students 表的 isActive 置为 0，保留学员行与全部历史数据，
     * 保证历史课时 / 排课 / 课时包 / 训练周期等子表数据仍能通过 studentName 关联，
     * 用于历史报表查询。
     *
     * 日常 UI 通过 [StudentDao.getAll] 自动过滤 isActive=0 的行，
     * 已删除的学员不会出现在学员管理、排课、签到等日常列表里。
     *
     * 如需物理删除（如整库恢复 / 清空场景），请使用 [StudentRepository.physicallyDeleteStudent]。
     *
     * @param name 学员姓名
     * @param onDone 删除完成回调（主线程）
     */
    fun deleteStudent(name: String, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        if (name.isBlank()) {
            onDone(false, "姓名不能为空")
            return
        }
        safeLaunch {
            try {
                studentRepo.deleteStudent(name)
                toast("已删除学员 $name（历史数据已保留）")
                onDone(true, "删除成功")
            } catch (e: Exception) {
                val msg = e.message ?: "删除失败"
                toast("删除失败：$msg")
                onDone(false, msg)
            }
        }
    }

    // === v22 冷热数据归档 ===

    /**
     * 归档一年前的课时记录（学员详情设置入口触发）。
     *
     * 将一年前（含）的全部 lessons 记录迁移到 archived_lessons 表，
     * 释放主表体积，加速首页/学员详情查询。
     *
     * - 自动计算一年前的日期边界（today - 365 天）
     * - 调用 [OperationRepository.archiveLessonsBefore] 在单事务内原子完成迁移+删除
     * - 通过 [toast] 反馈结果给用户
     *
     * @param onDone 完成回调（主线程），(成功, 消息)
     */
    fun archiveLessonsOlderThanOneYear(onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        safeLaunch {
            try {
                val boundary = todayStr().let { today ->
                    val date = java.time.LocalDate.parse(today)
                    date.minusDays(365).toString()
                }
                val result = opRepo.archiveLessonsBefore(boundary)
                toast(result.message)
                onDone(result.success, result.message)
            } catch (e: Exception) {
                val msg = e.message ?: "归档失败"
                toast("归档失败：$msg")
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
        safeLaunch {
            val lesson = lessonRepo.getById(lessonId) ?: return@safeLaunch
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
     * === v27：保存课后反馈并触发签退消课（事务原子操作） ===
     *
     * 教练在课后反馈 Tab 点击"保存反馈"按钮时调用：
     * 1. 更新 Lesson 的 coachComment / performance / attitude 字段
     * 2. 若该课时当前 status != "已签退"，调用 [OperationRepository.consumeLessonForCheckOut]
     *    执行事务化消课：扣减课时包 + 更新 status="已签退" + 写入 signOutTime + packageId
     * 3. 若已签退，仅更新反馈字段，不重复扣减课时
     *
     * UI 反馈：通过 [toast] 推送签退结果，包含课时包名与剩余课时数。
     *
     * @param lessonId 课时 ID
     * @param coachComment 教练寄语
     * @param performance 表现评分（1-10）
     * @param attitude 训练态度
     * @param onDone 完成回调（主线程），参数为是否成功
     */
    fun saveFeedbackAndCheckOut(
        lessonId: String,
        coachComment: String,
        performance: Int,
        attitude: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        safeLaunch {
            try {
                val lesson = lessonRepo.getById(lessonId)
                if (lesson == null) {
                    toast("课时不存在，可能已被删除")
                    onDone(false)
                    return@safeLaunch
                }

                // 1. 先更新反馈字段
                val updatedFeedback = lesson.copy(
                    coachComment = coachComment,
                    performance = performance,
                    attitude = attitude
                )
                lessonRepo.updateLesson(updatedFeedback)

                // 2. 若未签退，执行事务化消课
                if (lesson.status != "已签退") {
                    val result = opRepo.consumeLessonForCheckOut(updatedFeedback)
                    if (result.success) {
                        toast("签退成功，已扣减课时（${result.packageName}），剩余 ${result.remainingAfter} 节")
                        onDone(true)
                    } else {
                        toast("反馈已保存，但签退失败：${result.message}")
                        onDone(false)
                    }
                } else {
                    // 已签退，仅更新反馈
                    toast("反馈已保存（课时已签退，不重复扣减）")
                    onDone(true)
                }
            } catch (e: Exception) {
                toast("保存反馈失败：${e.message ?: "未知异常"}")
                onDone(false)
            }
        }
    }

    /**
     * === v27：查询学员在今日的排课记录（用于课后反馈自动填充） ===
     *
     * 教练在课后反馈 Tab 选中学员后，UI 调用本方法查询该学员今日的活跃排课。
     * 若查询到，自动将排课的 startTime / durationMinutes / location 预填充到反馈表单输入框；
     * 教练可手动覆盖，不强制锁定。
     *
     * @param studentName 学员姓名
     * @return 该学员今日的活跃排课列表（按开始时间升序），无则空列表
     */
    suspend fun findScheduleForStudentToday(
        studentName: String
    ): List<com.shangmentiyu.sportscoach.data.model.Schedule> {
        return opRepo.getTodayScheduleForStudent(studentName, todayStr())
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
        safeLaunch {
            val lesson = lessonRepo.getById(lessonId) ?: return@safeLaunch
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
        safeLaunch {
            val lesson = lessonRepo.getById(lessonId) ?: return@safeLaunch
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
        safeLaunch {
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
     * 修改"下一节课"的日期与时间（学员列表入口）。
     *
     * 仅更新 Lesson 表的 date / time 字段，不触发消课逻辑；
     * 数据库变更会通过 [nextLessons] Flow 自动回流到 UI。
     *
     * @param lessonId 课时 ID
     * @param date 新日期 YYYY-MM-DD
     * @param time 新时间 HH:mm
     * @param onDone 完成回调（主线程），参数为是否成功
     */
    fun updateNextLessonTime(
        lessonId: String,
        date: String,
        time: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        if (date.isBlank() || time.isBlank()) {
            onDone(false)
            return
        }
        safeLaunch {
            try {
                val lesson = lessonRepo.getById(lessonId)
                if (lesson == null) {
                    toast("课时不存在，可能已被删除")
                    onDone(false)
                    return@safeLaunch
                }
                lessonRepo.updateLesson(lesson.copy(date = date, time = time))
                toast("已调整下一节课时间为 $date $time")
                onDone(true)
            } catch (e: Exception) {
                toast("修改失败：${e.message ?: "未知错误"}")
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
        safeLaunch {
            try {
                db.withTransaction {
                    // 重名校验：确保新姓名尚未占用
                    if (db.studentDao().getByName(newName) != null) {
                        throw IllegalArgumentException("学员「$newName」已存在")
                    }
                    db.studentDao().renameStudent(oldName, newName)
                    db.lessonDao().renameStudent(oldName, newName)
                    db.scheduleDao().renameStudent(oldName, newName)
                    db.lessonPackageDao().renameStudent(oldName, newName)
                    db.trainingCycleDao().renameStudent(oldName, newName)
                    db.bodyMetricHistoryDao().renameStudent(oldName, newName)
                    db.parentReportDao().renameStudent(oldName, newName)
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
        safeLaunch {
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
        // 线程安全：[LocalDate.now] + [DateTimeFormatter] 不可变，无 Calendar 状态污染
        java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.getDefault()))

    /**
     * === v28 优化6：今日星期几（ISO：1=周一 ... 7=周日） ===
     *
     * 用于 [todayScheduleCount] 查询今日排课。
     * 使用 [java.time.LocalDate.getDayOfWeek] + [java.time.DayOfWeek.getValue]，
     * 线程安全，避免 [java.util.Calendar] 状态污染。
     */
    private fun todayDayOfWeek(): Int =
        java.time.LocalDate.now().getDayOfWeek().value  // Monday=1 ... Sunday=7

    /**
     * === v28 优化1：加载全部历史归档课时（用户点击"查看全部历史归档"按钮时调用） ===
     *
     * 默认所有 LazyColumn 只查 lessons 表（热数据）；
     * 仅当教练主动点击"查看全部历史归档"按钮时调用本方法，
     * 加载 archived_lessons 表（冷数据），用于归档列表展示。
     *
     * @param onDone 完成回调（主线程），参数为归档课时列表
     */
    fun loadAllArchivedLessons(onDone: (List<com.shangmentiyu.sportscoach.data.model.ArchivedLesson>) -> Unit = {}) {
        safeLaunch {
            val list = opRepo.getAllArchivedOnce()
            onDone(list)
        }
    }
}
