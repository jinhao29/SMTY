package com.shangmentiyu.sportscoach.ui.growth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.core.AbilityAnalyzer
import com.shangmentiyu.sportscoach.data.model.BodyMetricHistory
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.BodyMetricRepository
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 成长档案 ViewModel：基于真实成绩数据提供统计与展示。
 *
 * 设计原则：数据驱动，不依赖五维齐全。即使学员只有 1-2 次成绩，
 * 也能展示有意义的"最近成绩"和"个人最佳"，避免数据稀疏时的失真分析。
 *
 * v22 新增：[archiveLessonsOlderThanOneYear] 冷热数据归档入口，
 * 由学员详情页 TopAppBar 的"设置入口"图标按钮触发。
 *
 * v28 优化2 新增：[generateGrowthReport] 在后台线程生成 PDF 报告，
 * 通过 [reportUri] 暴露给 UI 触发系统分享面板。
 */
class GrowthViewModel(
    private val studentRepo: StudentRepository,
    private val lessonRepo: LessonRepository,
    /** v22 新增：归档能力依赖，null 时归档按钮降级为提示未初始化 */
    private val opRepo: OperationRepository? = null,
    /** === v28 优化2：身体形态历史 Repository，用于 PDF 报告中的 BMI 折线图 === */
    private val bodyMetricRepo: BodyMetricRepository? = null
) : ViewModel() {

    private val _student = MutableStateFlow<Student?>(null)
    val student: StateFlow<Student?> = _student.asStateFlow()

    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons: StateFlow<List<Lesson>> = _lessons.asStateFlow()

    /** 全部成绩条目（按日期升序） */
    private val _scores = MutableStateFlow<List<AbilityAnalyzer.ScoreEntry>>(emptyList())
    val scores: StateFlow<List<AbilityAnalyzer.ScoreEntry>> = _scores.asStateFlow()

    /** 最近一次课堂的成绩条目 */
    private val _latestScores = MutableStateFlow<List<AbilityAnalyzer.ScoreEntry>>(emptyList())
    val latestScores: StateFlow<List<AbilityAnalyzer.ScoreEntry>> = _latestScores.asStateFlow()

    /** 各项目的个人最佳（按分数降序） */
    private val _personalBests = MutableStateFlow<List<AbilityAnalyzer.ScoreEntry>>(emptyList())
    val personalBests: StateFlow<List<AbilityAnalyzer.ScoreEntry>> = _personalBests.asStateFlow()

    /** === v10 终极架构：5 维能力雷达（速度/力量/耐力/柔韧/灵敏） ===
     * 由 [AbilityAnalyzer.computeRadar] 基于 scores 派生，UI 通过 [AbilityRadarCard] 渲染。 */
    private val _radar = MutableStateFlow(AbilityAnalyzer.AbilityRadar())
    val radar: StateFlow<AbilityAnalyzer.AbilityRadar> = _radar.asStateFlow()

    /** 统计信息 */
    private val _stats = MutableStateFlow(GrowthStats())
    val stats: StateFlow<GrowthStats> = _stats.asStateFlow()

    /** 归档结果 Toast 消息（null 表示无待显示消息） */
    private val _archiveToast = MutableStateFlow<String?>(null)
    val archiveToast: StateFlow<String?> = _archiveToast.asStateFlow()

    /** === v28 优化2：PDF 报告生成状态 === */
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** === v28 优化2：生成的 PDF Uri（非 null 时 UI 弹出分享面板） === */
    private val _reportUri = MutableStateFlow<Uri?>(null)
    val reportUri: StateFlow<Uri?> = _reportUri.asStateFlow()

    /** === v28 优化2：PDF 生成失败/成功 Toast 文案 === */
    private val _reportToast = MutableStateFlow<String?>(null)
    val reportToast: StateFlow<String?> = _reportToast.asStateFlow()

    /**
     * === v31 优化4：家长端加密 PDF 分享状态 ===
     *
     * - 非空时表示加密 PDF 已生成，UI 弹出"密码 + 一键微信发送"对话框
     * - 内含 4 位密码 + 已生成 PDF 文件 Uri + 分享说明文本
     * - UI 调用 [ParentShareHelper.shareToWeChat] 跳转微信分享
     */
    private val _parentShareResult = MutableStateFlow<ParentShareHelper.ShareResult?>(null)
    val parentShareResult: StateFlow<ParentShareHelper.ShareResult?> = _parentShareResult.asStateFlow()

    /** 清空家长分享结果（对话框关闭后调用，避免重复触发） */
    fun clearParentShareResult() {
        _parentShareResult.value = null
    }

    fun clearArchiveToast() {
        _archiveToast.value = null
    }

    fun clearReportToast() {
        _reportToast.value = null
    }

    /** 清空 PDF Uri（分享面板关闭后调用，避免重复触发） */
    fun clearReportUri() {
        _reportUri.value = null
    }

    /** 加载学员的全部数据 */
    fun load(studentName: String) {
        viewModelScope.launch {
            _student.value = studentRepo.getByName(studentName)
            lessonRepo.getLessonsByStudent(studentName).collect { lessonList ->
                _lessons.value = lessonList
                val scoreList = AbilityAnalyzer.extractScores(lessonList)
                _scores.value = scoreList

                // 最近一次课堂的成绩：取最新日期的所有成绩
                val latestDate = lessonList.maxByOrNull { it.date }?.date ?: ""
                _latestScores.value = if (latestDate.isEmpty()) emptyList()
                                      else scoreList.filter { it.date == latestDate }

                // 个人最佳：每个项目取分数最高的一次
                _personalBests.value = scoreList
                    .groupBy { it.projectName }
                    .mapNotNull { (_, entries) -> entries.maxByOrNull { it.score } }
                    .sortedByDescending { it.score }

                // === v10 终极架构：5 维能力雷达 ===
                // 基于全部成绩计算每维度的最近得分（0-100）
                _radar.value = AbilityAnalyzer.computeRadar(scoreList)

                // 统计
                _stats.value = GrowthStats(
                    totalLessons = lessonList.size,
                    totalMinutes = lessonList.sumOf { it.duration },
                    latestDate = latestDate,
                    onTimeRate = if (lessonList.isNotEmpty()) {
                        lessonList.count { it.attendance == "准时" }.toFloat() / lessonList.size
                    } else 0f
                )
            }
        }
    }

    /**
     * === v28 优化2：生成学员成长 PDF 报告 ===
     *
     * 在后台线程（IO Dispatcher）调用 [GrowthPdfGenerator.generateReport]，
     * 生成完成后通过 [reportUri] 暴露 PDF 文件路径给 UI 触发分享面板。
     *
     * 流程：
     * 1. 检查学员数据是否加载完成
     * 2. 异步加载学员身体形态历史（最近 6 条）
     * 3. 计算剩余课时（来自 opRepo.getAllPackages）
     * 4. 取最近 5 次训练记录（按日期降序）
     * 5. 调用 [GrowthPdfGenerator.generateReport] 生成 A4 PDF
     * 6. 通过 [reportUri] 暴露，UI 监听后调用 [Intent.ACTION_SEND] 分享
     *
     * @param context 应用上下文（用于 FileProvider 与缓存目录）
     */
    fun generateGrowthReport(context: Context) {
        val student = _student.value ?: run {
            _reportToast.value = "学员数据未加载，请稍后再试"
            return
        }
        if (_isGenerating.value) {
            // 防止重复点击
            _reportToast.value = "正在生成中，请稍候..."
            return
        }

        _isGenerating.value = true
        viewModelScope.launch {
            try {
                // 1. 加载身体形态历史（按日期升序）
                val bodyMetrics: List<BodyMetricHistory> = withContext(Dispatchers.IO) {
                    bodyMetricRepo?.getByStudentOnce(student.name) ?: emptyList()
                }

                // 2. 计算剩余课时（汇总所有活跃课时包）
                val remainingLessons: Int = withContext(Dispatchers.IO) {
                    val allPackages = opRepo?.getAllPackagesOnce() ?: emptyList()
                    allPackages
                        .filter { !it.isExpired && it.status != "已退费" && it.studentName == student.name }
                        .sumOf { it.remainingLessons }
                }

                // 3. 取最近 5 次训练记录（按日期降序）
                val recentLessons = _lessons.value
                    .sortedByDescending { "${it.date} ${it.time}" }
                    .take(5)

                // 4. 教练寄语：取最近一次有寄语的课时
                val coachComment = _lessons.value
                    .sortedByDescending { "${it.date} ${it.time}" }
                    .firstOrNull { it.coachComment.isNotBlank() }
                    ?.coachComment ?: ""

                // 5. 在后台线程生成 PDF
                val uri = withContext(Dispatchers.IO) {
                    GrowthPdfGenerator.generateReport(
                        context = context.applicationContext,
                        student = student,
                        bodyMetrics = bodyMetrics,
                        recentLessons = recentLessons,
                        remainingLessons = remainingLessons,
                        coachComment = coachComment
                    )
                }

                if (uri != null) {
                    _reportUri.value = uri
                    _reportToast.value = "成长报告已生成，可选择分享方式"
                } else {
                    _reportToast.value = "PDF 生成失败，请重试"
                }
            } catch (e: Exception) {
                _reportToast.value = "PDF 生成异常：${e.message ?: "未知错误"}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /**
     * === v31 优化4：生成家长端加密 PDF 并准备微信分享 ===
     *
     * 在原 [generateGrowthReport] 基础上，叠加家长专属保护：
     * - 调用 [ParentShareHelper.generateEncryptedReport] 生成"加密 PDF + 4 位密码"
     * - 通过 [parentShareResult] 暴露给 UI
     * - UI 弹出"密码 + 一键微信发送"对话框，教练可：
     *   1. 看到密码后口头告知家长
     *   2. 点击"发送到微信"按钮直接跳转微信分享面板
     *
     * 注意：本方法会先生成基础 PDF，再复制到家长分享文件名下，
     * 因此比 [generateGrowthReport] 多一次 IO，但分享体验更佳。
     *
     * @param context 应用上下文
     * @param parentName 家长称呼（如"张爸爸"）
     */
    fun generateParentEncryptedReport(context: Context, parentName: String) {
        val student = _student.value ?: run {
            _reportToast.value = "学员数据未加载，请稍后再试"
            return
        }
        if (parentName.isBlank()) {
            _reportToast.value = "请填写家长称呼"
            return
        }
        if (_isGenerating.value) {
            _reportToast.value = "正在生成中，请稍候..."
            return
        }

        _isGenerating.value = true
        viewModelScope.launch {
            try {
                // 1. 加载身体形态历史（按日期升序）
                val bodyMetrics: List<BodyMetricHistory> = withContext(Dispatchers.IO) {
                    bodyMetricRepo?.getByStudentOnce(student.name) ?: emptyList()
                }

                // 2. 计算剩余课时
                val remainingLessons: Int = withContext(Dispatchers.IO) {
                    val allPackages = opRepo?.getAllPackagesOnce() ?: emptyList()
                    allPackages
                        .filter { !it.isExpired && it.status != "已退费" && it.studentName == student.name }
                        .sumOf { it.remainingLessons }
                }

                // 3. 取最近 5 次训练记录
                val recentLessons = _lessons.value
                    .sortedByDescending { "${it.date} ${it.time}" }
                    .take(5)

                // 4. 教练寄语
                val coachComment = _lessons.value
                    .sortedByDescending { "${it.date} ${it.time}" }
                    .firstOrNull { it.coachComment.isNotBlank() }
                    ?.coachComment ?: ""

                // 5. 清理旧分享文件（避免 cacheDir 堆积）
                withContext(Dispatchers.IO) {
                    ParentShareHelper.cleanOldShareFiles(context)
                }

                // 6. 后台生成加密 PDF
                val result = withContext(Dispatchers.IO) {
                    ParentShareHelper.generateEncryptedReport(
                        context = context.applicationContext,
                        student = student,
                        parentName = parentName,
                        bodyMetrics = bodyMetrics,
                        recentLessons = recentLessons,
                        remainingLessons = remainingLessons,
                        coachComment = coachComment
                    )
                }

                if (result != null) {
                    _parentShareResult.value = result
                    _reportToast.value = "加密报告已生成，密码：${result.password}"
                } else {
                    _reportToast.value = "加密 PDF 生成失败，请重试"
                }
            } catch (e: Exception) {
                _reportToast.value = "加密 PDF 生成异常：${e.message ?: "未知错误"}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /**
     * 归档一年前的课时记录（学员详情设置入口触发）。
     *
     * 将一年前（today - 365 天）的全部 lessons 记录迁移到 archived_lessons 表，
     * 释放主表体积，加速学员详情查询。
     *
     * - 自动计算一年前的日期边界
     * - 在单事务内原子完成迁移+删除，失败自动回滚
     * - 通过 [archiveToast] 反馈结果给 UI
     *
     * @param onDone 完成回调（主线程），(成功, 消息)
     */
    fun archiveLessonsOlderThanOneYear(onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        val repo = opRepo ?: run {
            _archiveToast.value = "归档功能未初始化"
            onDone(false, "归档功能未初始化")
            return
        }
        viewModelScope.launch {
            try {
                val boundary = todayStr().let { today ->
                    val date = java.time.LocalDate.parse(today)
                    date.minusDays(365).toString()
                }
                val result = repo.archiveLessonsBefore(boundary)
                _archiveToast.value = result.message
                onDone(result.success, result.message)
            } catch (e: Exception) {
                val msg = e.message ?: "归档失败"
                _archiveToast.value = "归档失败：$msg"
                onDone(false, msg)
            }
        }
    }

    /** 返回今日日期 YYYY-MM-DD（与 HomeViewModel 保持一致） */
    private fun todayStr(): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return java.time.LocalDate.now().format(formatter)
    }
}

/**
 * 成长档案统计数据。
 *
 * @param totalLessons 累计课时数
 * @param totalMinutes 总训练时长（分钟）
 * @param latestDate 最近训练日期 YYYY-MM-DD
 * @param onTimeRate 出勤准时率（0-1）
 */
data class GrowthStats(
    val totalLessons: Int = 0,
    val totalMinutes: Int = 0,
    val latestDate: String = "",
    val onTimeRate: Float = 0f
) {
    /** 训练总时长（小时） */
    val totalHours: Float get() = totalMinutes / 60f

    /** 最近训练日期短格式（MM-DD） */
    val latestDateShort: String get() = if (latestDate.length >= 10) latestDate.takeLast(5) else latestDate
}
