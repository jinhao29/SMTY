package com.shangmentiyu.sportscoach.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.core.AutoBackupScheduler
import com.shangmentiyu.sportscoach.core.BackupManager
import com.shangmentiyu.sportscoach.core.LanImageReceiver
import com.shangmentiyu.sportscoach.core.ProgressState
import com.shangmentiyu.sportscoach.core.UdpPlanListenerService
import com.shangmentiyu.sportscoach.data.repo.BackupRepository
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.PlanImageRepository
import com.shangmentiyu.sportscoach.data.repo.SettingsRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import com.shangmentiyu.sportscoach.excel.ExcelSync
import com.shangmentiyu.sportscoach.excel.ImportStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale

class SettingsViewModel(
    private val app: Application,
    private val lessonRepo: LessonRepository,
    private val studentRepo: StudentRepository,
    private val settingsRepo: SettingsRepository,
    private val backupRepo: BackupRepository,
    // v26 优化1：操作日志 Repository（审计溯源）
    private val auditLogRepo: com.shangmentiyu.sportscoach.data.repo.AuditLogRepository? = null,
    // v25 新增：训练计划图片 Repository（电脑端截图同步）
    private val planImageRepo: PlanImageRepository? = null
) : ViewModel() {

    /**
     * === v25 新增：待同步的电脑端截图信息 ===
     *
     * 数据来源：UdpPlanListenerService 收到 UDP 广播后写入 SharedPreferences
     * 字段：host / port / filename / student_name / date / received_at
     *
     * UI 使用：
     * - 设置页"同步电脑端截图"按钮根据此信息显示提示文案（如"收到 陈书楠 的训练计划"）
     * - 点击按钮后调用 [syncLanPlanImage] 触发下载
     */
    private val _pendingLanPlan = MutableStateFlow<PendingLanPlan?>(null)
    val pendingLanPlan: StateFlow<PendingLanPlan?> = _pendingLanPlan.asStateFlow()

    /** 待同步截图信息数据类 */
    data class PendingLanPlan(
        val host: String,
        val port: Int,
        val filename: String,
        val studentName: String,
        val date: String,
        val receivedAt: Long
    )

    /** 是否正在同步电脑端截图（用于禁用按钮 + 显示加载） */
    private val _lanPlanSyncing = MutableStateFlow(false)
    val lanPlanSyncing: StateFlow<Boolean> = _lanPlanSyncing.asStateFlow()

    init {
        // 启动 UDP 监听 Service（前台 Service，长期监听电脑端广播）
        runCatching {
            UdpPlanListenerService.start(app)
        }
        // 读取待同步信息（用户从通知点击进入设置页时立即显示）
        refreshPendingLanPlan()
    }

    /**
     * 从 SharedPreferences 读取待同步的电脑端截图信息。
     *
     * 调用时机：
     * - 设置页进入时（init）
     * - 用户从通知点击进入设置页时
     * - 同步完成后清空
     */
    fun refreshPendingLanPlan() {
        val prefs = app.getSharedPreferences("lan_plan_pending", Context.MODE_PRIVATE)
        val host = prefs.getString("host", "") ?: ""
        if (host.isBlank()) {
            _pendingLanPlan.value = null
            return
        }
        _pendingLanPlan.value = PendingLanPlan(
            host = host,
            port = prefs.getInt("port", 8080),
            filename = prefs.getString("filename", "") ?: "",
            studentName = prefs.getString("student_name", "") ?: "",
            date = prefs.getString("date", "") ?: "",
            receivedAt = prefs.getLong("received_at", 0L)
        )
    }

    /**
     * === v25 新增：同步电脑端截图到本地 ===
     *
     * 流程：
     * 1. 读取 SharedPreferences 中 UdpPlanListenerService 保存的广播信息
     * 2. 调用 LanImageReceiver.downloadAndImport 下载图片
     * 3. 解析文件名 → 关联学员 → 写入数据库
     * 4. 清空 pending 信息，刷新 UI
     *
     * 失败处理：
     * - HTTP 连接失败：提示"电脑端未开启服务或不在同一局域网"
     * - 学员未找到：仍保存图片，提示用户手动关联
     */
    fun syncLanPlanImage() {
        val repo = planImageRepo ?: run {
            updateStatus("训练计划图片仓储未初始化")
            return
        }
        val pending = _pendingLanPlan.value ?: run {
            updateStatus("暂无待同步的电脑端截图")
            return
        }
        if (_lanPlanSyncing.value) return  // 防重复点击

        safeLaunch {
            _lanPlanSyncing.value = true
            updateStatus("正在从 ${pending.host} 下载训练计划截图…")
            try {
                val result = LanImageReceiver.downloadAndImport(
                    context = app,
                    host = pending.host,
                    port = pending.port,
                    filename = pending.filename,
                    planImageRepo = repo,
                    studentRepo = studentRepo
                )
                if (result.success) {
                    updateStatus("同步成功：${result.message}")
                    // 清空 pending 信息
                    app.getSharedPreferences("lan_plan_pending", Context.MODE_PRIVATE)
                        .edit().clear().apply()
                    _pendingLanPlan.value = null
                } else {
                    updateStatus("同步失败：${result.message}")
                }
            } catch (e: Exception) {
                updateStatus("同步失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                _lanPlanSyncing.value = false
            }
        }
    }

    val coach: StateFlow<String> = settingsRepo.coach
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // === v30 全自动无感备份开关 ===
    // 默认开启：DataStore 中未配置时返回 true
    // 用户在设置页切换后，调用 setAutoBackupEnabled 立即触发 AutoBackupScheduler.reloadSettings
    val autoBackupEnabled: StateFlow<Boolean> = settingsRepo.autoBackupEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /**
     * 设置自动备份开关。
     * - 立即写入 DataStore 持久化
     * - 调用 [AutoBackupScheduler.reloadSettings] 使设置立即生效
     *   - 关闭时取消所有 pending 防抖任务
     *   - 开启时等待下次数据变更自然触发
     */
    fun setAutoBackupEnabled(enabled: Boolean) {
        safeLaunch {
            settingsRepo.setAutoBackupEnabled(enabled)
            AutoBackupScheduler.reloadSettings()
            _statusMessage.value = if (enabled) "已开启自动备份" else "已关闭自动备份"
        }
    }

    val todayCount = lessonRepo.getTodayCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val totalCount = lessonRepo.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /**
     * === v24 优化4：全局协程异常捕获 ===
     *
     * 应用级异常处理器：拦截 Excel 导出 / 备份 / 恢复过程中可能出现的
     * IO 异常、JSON 解析异常、SQLiteDatabaseLockedException 等，避免 App 闪退。
     * - 异常落盘：通过 [com.shangmentiyu.sportscoach.core.CrashHandler.writeLog]
     * - UI 反馈：通过 [_statusMessage] 推送轻量提示
     */
    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.core.CoroutineExt.createAppExceptionHandler(
            toastSink = _statusMessage,
            contextTag = "SettingsViewModel"
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

    /** 备份/恢复操作是否进行中（UI 据此禁用按钮、显示加载动画） */
    private val _backupInProgress = MutableStateFlow(false)
    val backupInProgress: StateFlow<Boolean> = _backupInProgress.asStateFlow()

    /**
     * 备份/恢复进度状态（UI 据此显示进度文案与百分比）。
     *
     * BackupManager.onProgress 在 IO 线程被调用，这里通过 emit 安全切到 StateFlow。
     */
    sealed class BackupProgress {
        data object Idle : BackupProgress()
        data class Working(val phase: String, val current: Int, val total: Int, val message: String) : BackupProgress()
        data class Done(val message: String) : BackupProgress()
    }

    private val _backupProgress = MutableStateFlow<BackupProgress>(BackupProgress.Idle)
    val backupProgress: StateFlow<BackupProgress> = _backupProgress.asStateFlow()

    /** UI 消费了备份进度事件后调用，重置为 Idle */
    fun consumeBackupProgress() {
        _backupProgress.value = BackupProgress.Idle
    }

    /** 恢复成功后置为 true，UI 据此弹出"重启应用"确认对话框 */
    private val _needRestart = MutableStateFlow(false)
    val needRestart: StateFlow<Boolean> = _needRestart.asStateFlow()

    /**
     * Excel 导出/导入进度状态（UI 据此显示进度条与文字提示）。
     *
     * 关键：Excel 操作（XSSFWorkbook 构造 + wb.write）是 CPU/IO 密集型，
     * 必须通过 [withContext]([Dispatchers.IO]) 切到 IO 线程，否则主线程阻塞 3s+ 必 ANR。
     */
    sealed class ExportProgress {
        data object Idle : ExportProgress()
        data class Working(val current: Int, val total: Int, val message: String) : ExportProgress()
        data class Done(val successCount: Int, val failCount: Int, val message: String) : ExportProgress()
        data class Failed(val message: String) : ExportProgress()
    }

    private val _exportProgress = MutableStateFlow<ExportProgress>(ExportProgress.Idle)
    val exportProgress: StateFlow<ExportProgress> = _exportProgress.asStateFlow()

    /** UI 消费了导出进度事件后调用，重置为 Idle */
    fun consumeExportProgress() {
        _exportProgress.value = ExportProgress.Idle
    }

    /**
     * === v24 优化3：自动收集 ExportProgress / BackupProgress 变化并桥接到 progressState ===
     *
     * 通过 init block 在 viewModelScope 中订阅两个内部 StateFlow，
     * 任一变化时同步映射到统一 [progressState]，UI 层只需订阅一个 StateFlow 即可。
     */
    init {
        safeLaunch {
            _exportProgress.collect { p ->
                syncProgressFromExport(p)
            }
        }
        safeLaunch {
            _backupProgress.collect { p ->
                syncProgressFromBackup(p)
            }
        }
    }

    /**
     * === v24 优化3：统一进度状态（与 ProgressDialog 配合）===
     *
     * 桥接 [ExportProgress] 与 [BackupProgress]：在两者变化时同步更新此 StateFlow，
     * 使 UI 层只需订阅一个 [progressState] 即可展示统一的 [com.shangmentiyu.sportscoach.ui.theme.ProgressDialog]。
     *
     * - 进行中：弹出 ProgressDialog，禁用返回，显示进度文案/百分比
     * - 完成：显示"完成"文案 1.5s 后自动消失（UI 层用 LaunchedEffect 控制）
     * - 失败：以完成态展示错误文案（文案本身已含错误信息）
     */
    private val _progressState = MutableStateFlow(ProgressState.Idle)
    val progressState: StateFlow<ProgressState> = _progressState.asStateFlow()

    /** UI 消费了进度事件后调用，重置为 Idle（关闭 ProgressDialog） */
    fun consumeProgressState() {
        _progressState.value = ProgressState.Idle
    }

    /** 内部工具：将 ExportProgress 同步映射到统一 ProgressState */
    private fun syncProgressFromExport(p: ExportProgress) {
        _progressState.value = when (p) {
            is ExportProgress.Working -> ProgressState.workingCounted(
                current = p.current - 1,
                total = p.total,
                messagePrefix = p.message.substringBefore("（").trim()
            )
            is ExportProgress.Done -> ProgressState.done(p.message)
            is ExportProgress.Failed -> ProgressState.done(p.message)
            ExportProgress.Idle -> ProgressState.Idle
        }
    }

    /** 内部工具：将 BackupProgress 同步映射到统一 ProgressState */
    private fun syncProgressFromBackup(p: BackupProgress) {
        _progressState.value = when (p) {
            is BackupProgress.Working -> {
                if (p.total > 0) {
                    ProgressState.workingCounted(p.current - 1, p.total, p.message)
                } else {
                    ProgressState.working(p.message, -1f)
                }
            }
            is BackupProgress.Done -> ProgressState.done(p.message)
            BackupProgress.Idle -> ProgressState.Idle
        }
    }

    /** 自动保存防抖任务：连续输入时只保留最后一次写库 */
    private var saveJob: Job? = null

    /** 防抖延迟（毫秒）：用户停止输入 500ms 后才真正写库 */
    private val saveDebounceMs = 500L

    /** 最新待保存的教练姓名（用户最近一次输入，尚未写库） */
    private var pendingCoach: String? = null

    /**
     * 独立保存作用域：onCleared 时 viewModelScope 已取消，
     * 用此作用域保证最后一次防抖未触发的改动也能写入数据库。
     */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 更新教练姓名：立即同步内存 StateFlow（UI 即时响应），
     * 数据库写入采用 500ms 防抖，连续输入只保留最后一次写库。
     */
    fun setCoach(name: String) {
        pendingCoach = name  // 记录最新待保存值
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(saveDebounceMs)
            settingsRepo.setCoach(name)
            pendingCoach = null  // 已写库，清除待保存标记
        }
    }

    /**
     * 强制立即写库（页面退出等场景）：取消未触发的防抖任务，同步最新值。
     * 优先使用 pendingCoach（用户最近输入），避免 onCleared 时 StateFlow 尚未更新导致数据丢失。
     */
    fun flushSave() {
        val toSave = pendingCoach ?: coach.value
        saveJob?.cancel()
        saveJob = null
        saveScope.launch {
            settingsRepo.setCoach(toSave)
            pendingCoach = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel 销毁时确保最后一次改动已写库：
        // 优先使用 pendingCoach（用户最近输入但尚未写库的值）
        saveJob?.cancel()
        val toSave = pendingCoach ?: coach.value
        saveScope.launch {
            settingsRepo.setCoach(toSave)
        }
    }

    /**
     * 导出今日课堂记录到用户通过 SAF 选择的目录。
     *
     * 性能优化（v16）：
     * - 所有 IO + Excel 写入切到 [Dispatchers.IO]，避免主线程 ANR
     * - 通过 [exportProgress] StateFlow 实时反馈进度给 UI
     * - 使用 [lessonRepo.getTodayLessons] 直接命中索引查询，不再全表加载
     */
    fun exportTodayRecords(treeUri: Uri) {
        safeLaunch {
            // 线程安全：使用 [java.time.LocalDate] 替代 [SimpleDateFormat]
            val today = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))

            // 1. 读取今日课时（SQL 直查命中 idx_lessons_date 索引）
            _exportProgress.value = ExportProgress.Working(0, 0, "正在读取今日记录…")
            val lessons = withContext(Dispatchers.IO) {
                lessonRepo.getTodayLessons().first()
            }

            if (lessons.isEmpty()) {
                _exportProgress.value = ExportProgress.Failed("今日无课堂记录")
                return@safeLaunch
            }

            val treeDir = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(app, treeUri)
            } ?: run {
                _exportProgress.value = ExportProgress.Failed("无法访问所选目录")
                return@safeLaunch
            }

            var success = 0
            var fail = 0
            for ((idx, lesson) in lessons.withIndex()) {
                _exportProgress.value = ExportProgress.Working(
                    current = idx + 1,
                    total = lessons.size,
                    message = "正在导出 ${lesson.studentName} 的记录（${idx + 1}/${lessons.size}）"
                )
                // 关键：Excel 写入是 CPU 密集型，必须 IO 线程
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val student = studentRepo.getByName(lesson.studentName)
                        val fileName = "${lesson.studentName}_${lesson.date}_课堂记录.xlsx"
                            .replace("/", "_").replace("\\", "_")
                        treeDir.findFile(fileName)?.delete()
                        val docFile = treeDir.createFile(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            fileName
                        ) ?: return@withContext false
                        app.contentResolver.openOutputStream(docFile.uri)?.use { os ->
                            ExcelSync.exportLessonReport(lesson, student, os)
                        } ?: false
                    } catch (e: Exception) { false }
                }
                if (ok) success++ else fail++
            }
            val msg = "导出完成: 成功${success}条" + if (fail > 0) "，失败${fail}条" else ""
            _exportProgress.value = ExportProgress.Done(success, fail, msg)
            _statusMessage.value = msg
        }
    }

    /**
     * 导出成绩档案到用户通过 SAF 选择的目录。
     * 每个学员独立文件，若档案已存在则读取旧档案追加工作表后整体回写。
     *
     * 性能优化（v16）：全部切 IO 线程 + 进度 Flow，避免大数据量导出时 ANR。
     */
    fun exportScoresArchive(treeUri: Uri) {
        safeLaunch {
            _exportProgress.value = ExportProgress.Working(0, 0, "正在读取成绩记录…")
            val lessons = withContext(Dispatchers.IO) {
                lessonRepo.getAllLessons().first()
                    .filter { it.scores.isNotBlank() && it.scores != "{}" }
            }

            if (lessons.isEmpty()) {
                _exportProgress.value = ExportProgress.Failed("无成绩记录可导出")
                return@safeLaunch
            }

            val treeDir = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(app, treeUri)
            } ?: run {
                _exportProgress.value = ExportProgress.Failed("无法访问所选目录")
                return@safeLaunch
            }

            var success = 0
            var fail = 0
            for ((idx, lesson) in lessons.withIndex()) {
                _exportProgress.value = ExportProgress.Working(
                    current = idx + 1,
                    total = lessons.size,
                    message = "正在导出 ${lesson.studentName} 的成绩档案（${idx + 1}/${lessons.size}）"
                )
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val student = studentRepo.getByName(lesson.studentName)
                        val name = (student?.name ?: lesson.studentName)
                            .replace("/", "_").replace("\\", "_")
                        val fileName = "$name.xlsx"

                        val existing = treeDir.findFile(fileName)
                        val inputStream: InputStream? = existing?.uri?.let { app.contentResolver.openInputStream(it) }

                        if (existing == null) {
                            treeDir.createFile(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                fileName
                            )
                        }
                        val target = treeDir.findFile(fileName)
                        if (target == null) {
                            inputStream?.close()
                            return@withContext false
                        }

                        val result = app.contentResolver.openOutputStream(target.uri)?.use { os ->
                            ExcelSync.exportScoresToArchive(lesson, student, inputStream, os) != null
                        } ?: false
                        inputStream?.close()
                        result
                    } catch (e: Exception) { false }
                }
                if (ok) success++ else fail++
            }
            val msg = "档案导出完成: 成功${success}条" + if (fail > 0) "，失败${fail}条" else ""
            _exportProgress.value = ExportProgress.Done(success, fail, msg)
            _statusMessage.value = msg
        }
    }

    /**
     * 从用户通过 SAF 选择的目录导入学员。
     * 遍历目录下所有 .xlsx 文件，解析 _meta 工作表。
     *
     * 性能优化（v16）：文件遍历 + Excel 解析切 IO 线程 + 进度 Flow。
     */
    fun importStudents(treeUri: Uri) {
        // 兼容旧调用：默认使用 APPEND 策略（追加，同名跳过）
        importStudentsWithStrategy(treeUri, ImportStrategy.APPEND)
    }

    /**
     * === v25 优化4：按指定策略从 Excel 档案导入学员 ===
     *
     * 流程：
     * 1. 遍历 SAF 目录下的所有 .xlsx 文件
     * 2. 解析 _meta 工作表得到 List<Student>
     * 3. 调用 [StudentRepository.importStudentsWithStrategy] 按策略处理同名学员
     * 4. 通过 [ExportProgress.Done] 反馈导入结果统计（新增/跳过/更新/失败）
     *
     * @param treeUri 用户通过 SAF 选择的目录 Uri
     * @param strategy 导入策略（APPEND / OVERWRITE / UPDATE_PART）
     */
    fun importStudentsWithStrategy(treeUri: Uri, strategy: ImportStrategy) {
        safeLaunch {
            _exportProgress.value = ExportProgress.Working(0, 0, "正在扫描档案文件…")
            val treeDir = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(app, treeUri)
            } ?: run {
                _exportProgress.value = ExportProgress.Failed("无法访问所选目录")
                return@safeLaunch
            }

            val xlsxFiles = withContext(Dispatchers.IO) {
                treeDir.listFiles().filter {
                    it.isFile && it.name?.endsWith(".xlsx") == true && !it.name!!.startsWith("~$")
                }
            }

            if (xlsxFiles.isEmpty()) {
                _exportProgress.value = ExportProgress.Failed("未找到可导入的学员档案")
                return@safeLaunch
            }

            _exportProgress.value = ExportProgress.Working(0, xlsxFiles.size, "正在解析档案…")
            val streams = mutableListOf<InputStream>()
            var fail = 0
            for ((idx, file) in xlsxFiles.withIndex()) {
                _exportProgress.value = ExportProgress.Working(
                    current = idx + 1,
                    total = xlsxFiles.size,
                    message = "正在解析 ${file.name}（${idx + 1}/${xlsxFiles.size}）"
                )
                val s = withContext(Dispatchers.IO) {
                    runCatching { app.contentResolver.openInputStream(file.uri) }.getOrNull()
                }
                if (s != null) streams.add(s) else fail++
            }

            val students = withContext(Dispatchers.IO) {
                ExcelSync.importStudentsFromExcel(streams)
            }
            streams.forEach { runCatching { it.close() } }

            if (students.isEmpty()) {
                _exportProgress.value = ExportProgress.Failed("未找到可导入的学员")
                return@safeLaunch
            }

            // v25 优化4：按用户选择的策略处理同名学员
            val result = studentRepo.importStudentsWithStrategy(students, strategy)
            val msg = result.toUserMessage()
            _exportProgress.value = ExportProgress.Done(result.added, fail, msg)
            _statusMessage.value = msg
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    /** 更新状态消息（用于更新检查等场景） */
    fun updateStatus(message: String) {
        _statusMessage.value = message
    }

    // ==================== 数据备份与恢复 ====================

    /**
     * 执行整库备份到用户通过 SAF 选择的目标文件 Uri。
     *
     * 流程：
     * 1. 标记进行中状态（UI 禁用按钮）
     * 2. 调用 [BackupRepository.backup] 执行备份，传入进度回调
     * 3. 通过 [statusMessage] 反馈结果给 UI，通过 [backupProgress] 反馈阶段进度
     *
     * @param targetUri 用户通过 SAF CreateDocument 选择的目标文件 Uri
     */
    fun backupData(targetUri: Uri) {
        safeLaunch {
            _backupInProgress.value = true
            _backupProgress.value = BackupProgress.Working("prepare", 0, 0, "正在准备备份…")
            try {
                val result = backupRepo.backup(targetUri) { phase, current, total, msg ->
                    _backupProgress.value = BackupProgress.Working(phase, current, total, msg)
                }
                _statusMessage.value = result.message
                _backupProgress.value = BackupProgress.Done(result.message)
            } finally {
                _backupInProgress.value = false
            }
        }
    }

    /**
     * 从用户通过 SAF 选择的备份文件恢复整库数据。
     *
     * 流程（P3-b 优化：恢复前自动安全备份）：
     * 1. 标记进行中状态（UI 禁用按钮）
     * 2. **先调用 [BackupRepository.backupToCache] 在 App 内部缓存目录创建当前数据的安全备份**
     *    —— 若恢复失败或备份文件损坏，可通过该缓存文件回滚，避免用户数据永久丢失
     * 3. 调用 [BackupRepository.restore] 执行恢复，传入进度回调
     * 4. 成功时标记 [needRestart]，UI 据此引导用户重启应用
     * 5. 失败时仅展示错误消息，不修改任何状态
     *
     * 警告：恢复会覆盖当前所有学员/课时包/排课/签到数据。
     *
     * @param sourceUri 用户通过 SAF OpenDocument 选择的备份文件 Uri
     */
    fun restoreData(sourceUri: Uri) {
        safeLaunch {
            _backupInProgress.value = true
            try {
                // === P3-b: 恢复前自动安全备份 ===
                // 在 App 缓存目录创建当前数据的安全备份，作为回滚保险
                _backupProgress.value = BackupProgress.Working("safety", 0, 0, "正在创建安全备份…")
                val safetyFile = File(
                    app.cacheDir,
                    "smty_safety_${System.currentTimeMillis()}.smty_backup"
                )
                val safetyResult = backupRepo.backupToCache(safetyFile) { phase, current, total, msg ->
                    _backupProgress.value = BackupProgress.Working(
                        "safety_$phase", current, total,
                        "安全备份：$msg"
                    )
                }
                if (!safetyResult.success) {
                    _statusMessage.value = "恢复已中止：自动安全备份失败，请重试"
                    _backupProgress.value = BackupProgress.Done(safetyResult.message)
                    return@safeLaunch
                }

                // === 执行恢复 ===
                _backupProgress.value = BackupProgress.Working("restore", 0, 0, "正在恢复数据…")
                val result = backupRepo.restore(sourceUri) { phase, current, total, msg ->
                    _backupProgress.value = BackupProgress.Working(phase, current, total, msg)
                }
                _statusMessage.value = result.message
                if (result.success && result.needRestart) {
                    // 恢复成功：保留安全备份直到下次启动（若用户反悔可通过文件管理器手动恢复）
                    _needRestart.value = true
                    _backupProgress.value = BackupProgress.Done(result.message)
                } else {
                    // 恢复失败：删除安全备份（当前数据未变更）
                    runCatching { safetyFile.delete() }
                    _backupProgress.value = BackupProgress.Done(result.message)
                }
            } finally {
                _backupInProgress.value = false
            }
        }
    }

    /**
     * 重启应用以加载恢复后的新数据。
     *
     * 通过 Intent 重启 MainActivity，结束当前任务栈，确保所有旧 ViewModel 失效、
     * 数据库重新打开、StateFlow 重新订阅，避免脏读。
     */
    fun restartApp() {
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        app.startActivity(intent)
        // 杀掉当前进程，彻底清理旧 ViewModel 与数据库连接
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /** 生成默认备份文件名（UI 在 SAF CreateDocument 时使用） */
    fun generateBackupFileName(): String = BackupManager.generateBackupFileName()

    /** UI 消费了 needRestart 事件后调用，重置标记 */
    fun consumeNeedRestart() {
        _needRestart.value = false
    }

    // ==================== 签到照片存储空间管理 ====================

    /** 一年毫秒数：用于"清理一年前签到照片"判定阈值 */
    private val ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000

    /** 签到照片目录名（与 BackupManager.PHOTOS_DIR_NAME 保持一致） */
    private val signPhotosDirName = "SignPhotos"

    /** 签到照片目录大小（字节），UI 据此展示 MB/GB */
    private val _signPhotosSize = MutableStateFlow(0L)
    val signPhotosSize: StateFlow<Long> = _signPhotosSize.asStateFlow()

    /** 签到照片总数 */
    private val _signPhotosCount = MutableStateFlow(0)
    val signPhotosCount: StateFlow<Int> = _signPhotosCount.asStateFlow()

    /** 一年前可清理的照片数量（预扫描，用于二次确认弹窗展示） */
    private val _cleanableCount = MutableStateFlow(0)
    val cleanableCount: StateFlow<Int> = _cleanableCount.asStateFlow()

    /**
     * 扫描签到照片目录：统计目录大小、文件总数、可清理数（一年前）。
     *
     * 性能：目录遍历在 [Dispatchers.IO] 执行，避免主线程卡顿。
     * 异常兜底：目录不存在或权限失败时返回 0，不阻塞 UI。
     */
    fun scanSignPhotos() {
        safeLaunch {
            try {
                val dir = File(app.filesDir, signPhotosDirName)
                if (!dir.exists()) {
                    _signPhotosSize.value = 0
                    _signPhotosCount.value = 0
                    _cleanableCount.value = 0
                    return@safeLaunch
                }
                var totalSize = 0L
                var totalCount = 0
                var cleanable = 0
                val threshold = System.currentTimeMillis() - ONE_YEAR_MS
                dir.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        totalSize += f.length()
                        totalCount++
                        if (f.lastModified() < threshold) cleanable++
                    }
                }
                _signPhotosSize.value = totalSize
                _signPhotosCount.value = totalCount
                _cleanableCount.value = cleanable
            } catch (e: Exception) {
                // 静默失败：存储统计不阻塞 UI
            }
        }
    }

    /**
     * 清理一年前的签到照片。
     *
     * 调用时机：用户在二次确认对话框点击"确认清理"后调用。
     * 流程：
     * 1. 遍历 SignPhotos 目录，删除 lastModified < (now - 1 年) 的文件
     * 2. 重新扫描目录大小与文件数
     * 3. 通过 [statusMessage] 反馈清理结果
     *
     * 安全保证：
     * - 仅删除 filesDir/SignPhotos/ 下的文件，不会跨目录
     * - 仅按文件修改时间判定，不依赖文件名解析（兼容历史数据）
     * - 异常路径不中断，已删除的文件不回滚
     */
    fun cleanOldSignPhotos() {
        safeLaunch {
            try {
                val dir = File(app.filesDir, signPhotosDirName)
                if (!dir.exists()) {
                    _statusMessage.value = "签到照片目录不存在"
                    return@safeLaunch
                }
                val threshold = System.currentTimeMillis() - ONE_YEAR_MS
                var deleted = 0
                var freedBytes = 0L
                dir.walkTopDown().forEach { f ->
                    if (f.isFile && f.lastModified() < threshold) {
                        val size = f.length()
                        if (f.delete()) {
                            deleted++
                            freedBytes += size
                        }
                    }
                }
                // 清理后重新扫描
                scanSignPhotos()
                val freed = formatBytes(freedBytes)
                _statusMessage.value = "已清理 $deleted 张一年前的签到照片，释放 $freed"
            } catch (e: Exception) {
                _statusMessage.value = "清理失败：${e.message}"
            }
        }
    }

    /**
     * 将字节数格式化为人类可读字符串（B/KB/MB/GB）。
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIdx = 0
        while (size >= 1024 && unitIdx < units.size - 1) {
            size /= 1024
            unitIdx++
        }
        return if (unitIdx == 0) "${bytes.toInt()} ${units[unitIdx]}"
        else String.format(Locale.getDefault(), "%.2f %s", size, units[unitIdx])
    }

    // ==================== v29 优化3：缓存管理（孤立照片 + 临时缓存） ====================

    /**
     * === v29 优化3：孤立照片判定阈值（6 个月） ===
     *
     * 业务规则：lessons 表中 photoPath / signOutPhotoPath / contentImages 引用的照片
     * 视为"有效引用"，无论时间多久都不清理；未被任何 lesson 引用且文件 lastModified
     * 超过此阈值的照片视为"孤立照片"，可一键清理。
     *
     * 与 [ONE_YEAR_MS] 区别：[ONE_YEAR_MS] 用于"按时间清理一年前所有照片"（含被引用的），
     * 本阈值用于"按引用关系清理未被引用的孤立文件"，更安全（不会删除还在用的照片）。
     */
    private val ORPHAN_THRESHOLD_MS = 180L * 24 * 60 * 60 * 1000  // 180 天

    /** 孤立照片数量（预扫描，UI 二次确认弹窗展示） */
    private val _orphanPhotoCount = MutableStateFlow(0)
    val orphanPhotoCount: StateFlow<Int> = _orphanPhotoCount.asStateFlow()

    /** 孤立照片占用空间（字节，用于二次确认弹窗展示可释放空间） */
    private val _orphanPhotoSize = MutableStateFlow(0L)
    val orphanPhotoSize: StateFlow<Long> = _orphanPhotoSize.asStateFlow()

    /** 应用缓存目录大小（字节，UI 展示 cacheDir 占用） */
    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: StateFlow<Long> = _cacheSize.asStateFlow()

    /** 应用缓存目录临时文件数量 */
    private val _cacheFileCount = MutableStateFlow(0)
    val cacheFileCount: StateFlow<Int> = _cacheFileCount.asStateFlow()

    /**
     * === v29 优化3：扫描孤立照片 ===
     *
     * 流程：
     * 1. 查询 lessons 表全部记录，收集所有"被引用"的照片路径作为白名单
     *    （photoPath、signOutPhotoPath、contentImages JSON 数组中的每一项）
     * 2. 遍历 filesDir/SignPhotos/ 目录下的所有文件
     * 3. 文件不在白名单中 + lastModified < (now - 180 天) → 标记为孤立
     * 4. 累加孤立数量与占用空间，通过 StateFlow 反馈给 UI
     *
     * 设计权衡：
     * - 用"被引用关系"判定而非"按时间一刀切"：避免删除学员最近还在用的照片
     * - 同时叠加 6 个月时间阈值：避免删除教练刚拍但 lesson 尚未落库的临时文件
     *   （极端场景：教练拍完照 App 闪退，lesson 未创建，照片孤立但很新，不应清理）
     *
     * 性能：lessons 全量查询 + 文件遍历均在 [Dispatchers.IO] 执行，避免阻塞主线程。
     * 异常兜底：失败时静默返回 0，不阻塞 UI 展示。
     */
    fun scanOrphanPhotos() {
        safeLaunch {
            try {
                val db = com.shangmentiyu.sportscoach.data.db.AppDatabase.getDatabase(app)
                val photosDir = File(app.filesDir, signPhotosDirName)
                if (!photosDir.exists()) {
                    _orphanPhotoCount.value = 0
                    _orphanPhotoSize.value = 0L
                    return@safeLaunch
                }

                withContext(Dispatchers.IO) {
                    // 1. 收集所有被引用的照片绝对路径（白名单）
                    val allLessons = db.lessonDao().getAllOnce()
                    val referencedSet = HashSet<String>(allLessons.size * 2)
                    for (lesson in allLessons) {
                        if (lesson.photoPath.isNotBlank()) referencedSet.add(lesson.photoPath)
                        if (lesson.signOutPhotoPath.isNotBlank()) {
                            referencedSet.add(lesson.signOutPhotoPath)
                        }
                        // 解析 contentImages JSON 数组（课后反馈训练内容图片）
                        if (lesson.contentImages.isNotBlank()
                            && lesson.contentImages != "[]"
                            && lesson.contentImages != "null"
                        ) {
                            try {
                                val arr = org.json.JSONArray(lesson.contentImages)
                                for (i in 0 until arr.length()) {
                                    val p = arr.optString(i, "")
                                    if (p.isNotBlank()) referencedSet.add(p)
                                }
                            } catch (_: Exception) {
                                // 单条 JSON 解析失败不影响整体扫描
                            }
                        }
                    }

                    // 2. 规范化白名单：提取文件绝对路径，兼容相对路径与绝对路径
                    val normalizedReferenced = referencedSet.mapTo(HashSet()) { normalizePhotoPath(it) }

                    // 3. 遍历照片目录，统计孤立文件
                    val threshold = System.currentTimeMillis() - ORPHAN_THRESHOLD_MS
                    var orphanCount = 0
                    var orphanSize = 0L
                    photosDir.walkTopDown().forEach { f ->
                        if (!f.isFile) return@forEach
                        val normalized = normalizePhotoPath(f.absolutePath)
                        val isReferenced = normalized in normalizedReferenced
                        if (!isReferenced && f.lastModified() < threshold) {
                            orphanCount++
                            orphanSize += f.length()
                        }
                    }

                    _orphanPhotoCount.value = orphanCount
                    _orphanPhotoSize.value = orphanSize
                }
            } catch (_: Exception) {
                // 静默失败：扫描不阻塞 UI
                _orphanPhotoCount.value = 0
                _orphanPhotoSize.value = 0L
            }
        }
    }

    /**
     * === v29 优化3：归一化照片路径 ===
     *
     * 统一比较口径：lesson 中存储的路径可能是相对路径（如 "SignPhotos/xxx.jpg"）
     * 或绝对路径（如 "/data/.../SignPhotos/xxx.jpg"），文件系统扫描得到的是绝对路径。
     *
     * 归一化策略：取文件名 + 文件长度作为唯一标识，避免路径前缀差异导致误判。
     * 若无法解析（如 lesson 中存的是 SAF Uri），保留原始字符串作为兜底。
     */
    private fun normalizePhotoPath(path: String): String {
        return try {
            val file = File(path)
            "${file.name}#${file.length()}"
        } catch (_: Exception) {
            path
        }
    }

    /**
     * === v29 优化3：清理孤立照片 ===
     *
     * 调用时机：用户在二次确认弹窗点击"确认清理"后调用。
     *
     * 流程：
     * 1. 重新扫描白名单（避免清理期间数据变化导致误删）
     * 2. 遍历 filesDir/SignPhotos/，删除未被引用 + 超过 6 个月的孤立文件
     * 3. 重新扫描孤立照片与签到照片目录统计，反馈清理结果
     *
     * 安全保证：
     * - 二次扫描白名单确保最新数据（清理期间教练可能新建了 lesson）
     * - 删除前双重校验：未被引用 + 超过阈值
     * - 异常不中断：已删除的文件不回滚
     * - 仅删除 filesDir/SignPhotos/ 下文件，不跨目录
     */
    fun cleanOrphanPhotos() {
        safeLaunch {
            try {
                val db = com.shangmentiyu.sportscoach.data.db.AppDatabase.getDatabase(app)
                val photosDir = File(app.filesDir, signPhotosDirName)
                if (!photosDir.exists()) {
                    _statusMessage.value = "签到照片目录不存在"
                    return@safeLaunch
                }

                var deleted = 0
                var freedBytes = 0L
                withContext(Dispatchers.IO) {
                    // 1. 重新收集白名单（最新数据）
                    val allLessons = db.lessonDao().getAllOnce()
                    val referencedSet = HashSet<String>(allLessons.size * 2)
                    for (lesson in allLessons) {
                        if (lesson.photoPath.isNotBlank()) referencedSet.add(lesson.photoPath)
                        if (lesson.signOutPhotoPath.isNotBlank()) {
                            referencedSet.add(lesson.signOutPhotoPath)
                        }
                        if (lesson.contentImages.isNotBlank()
                            && lesson.contentImages != "[]"
                            && lesson.contentImages != "null"
                        ) {
                            try {
                                val arr = org.json.JSONArray(lesson.contentImages)
                                for (i in 0 until arr.length()) {
                                    val p = arr.optString(i, "")
                                    if (p.isNotBlank()) referencedSet.add(p)
                                }
                            } catch (_: Exception) { }
                        }
                    }
                    val normalizedReferenced = referencedSet.mapTo(HashSet()) { normalizePhotoPath(it) }

                    // 2. 删除孤立 + 超过阈值的文件
                    val threshold = System.currentTimeMillis() - ORPHAN_THRESHOLD_MS
                    photosDir.walkTopDown().forEach { f ->
                        if (!f.isFile) return@forEach
                        val normalized = normalizePhotoPath(f.absolutePath)
                        if (normalized !in normalizedReferenced && f.lastModified() < threshold) {
                            val size = f.length()
                            if (f.delete()) {
                                deleted++
                                freedBytes += size
                            }
                        }
                    }
                }

                // 3. 清理后重新扫描，UI 状态最新
                scanOrphanPhotos()
                scanSignPhotos()
                val freed = formatBytes(freedBytes)
                _statusMessage.value = "已清理 $deleted 个孤立照片，释放 $freed"
                // v26 优化1：记录清理日志（审计溯源）
                auditLogRepo?.log(
                    action = "清理孤立照片",
                    summary = "清理 $deleted 个孤立照片，释放 $freed"
                )
            } catch (e: Exception) {
                _statusMessage.value = "清理失败：${e.message}"
            }
        }
    }

    /**
     * === v29 优化3：扫描应用缓存目录大小 ===
     *
     * 遍历 app.cacheDir 目录，统计全部临时文件大小与数量。
     * 用于 UI 展示"缓存占用"与可清理数量，让用户决定是否一键清理。
     *
     * 注意：cacheDir 中的文件包括系统临时文件、Glide 图片缓存、
     * BackupRepository 的安全备份等，全部统计为可清理（清理后系统会自动重建）。
     */
    fun scanCacheSize() {
        safeLaunch {
            try {
                val cacheDir = app.cacheDir
                if (!cacheDir.exists()) {
                    _cacheSize.value = 0L
                    _cacheFileCount.value = 0
                    return@safeLaunch
                }
                var totalSize = 0L
                var totalFiles = 0
                cacheDir.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        totalSize += f.length()
                        totalFiles++
                    }
                }
                _cacheSize.value = totalSize
                _cacheFileCount.value = totalFiles
            } catch (_: Exception) {
                // 静默失败：扫描不阻塞 UI
                _cacheSize.value = 0L
                _cacheFileCount.value = 0
            }
        }
    }

    /**
     * === v29 优化3：清理应用缓存目录 ===
     *
     * 删除 app.cacheDir 下的全部临时文件，但保留目录结构（避免系统重建目录失败）。
     *
     * 调用时机：用户在二次确认弹窗点击"确认清理缓存"后调用。
     *
     * 安全保证：
     * - 仅删除文件，保留目录（防止系统 / 第三方 SDK 因目录缺失而异常）
     * - 不删除 cacheDir 根目录本身
     * - 异常不中断：已删除的文件不回滚
     *
     * 注意：安全备份文件（smty_safety_*.smty_backup）也会被清理——这些文件仅在
     * 数据恢复失败时用于手动回滚，正常恢复成功后已无意义；用户主动清理缓存视为
     * 确认放弃回滚保险。
     */
    fun cleanCacheFiles() {
        safeLaunch {
            try {
                val cacheDir = app.cacheDir
                if (!cacheDir.exists()) {
                    _statusMessage.value = "缓存目录不存在"
                    return@safeLaunch
                }
                var deleted = 0
                var freedBytes = 0L
                withContext(Dispatchers.IO) {
                    cacheDir.walkTopDown().forEach { f ->
                        // 跳过根目录本身，仅删除子文件
                        if (f.isFile) {
                            val size = f.length()
                            if (f.delete()) {
                                deleted++
                                freedBytes += size
                            }
                        }
                    }
                }
                // 清理后重新扫描
                scanCacheSize()
                val freed = formatBytes(freedBytes)
                _statusMessage.value = "已清理 $deleted 个临时缓存文件，释放 $freed"
                // v26 优化1：记录清理日志
                auditLogRepo?.log(
                    action = "清理缓存文件",
                    summary = "清理 $deleted 个临时缓存文件，释放 $freed"
                )
            } catch (e: Exception) {
                _statusMessage.value = "缓存清理失败：${e.message}"
            }
        }
    }

    // ==================== v26 优化1：操作日志（审计溯源） ====================

    /** 全部操作日志（按时间倒序，UI 列表订阅） */
    val auditLogs: StateFlow<List<com.shangmentiyu.sportscoach.data.model.AuditLogEntity>> =
        (auditLogRepo?.getAllLogs() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 清空全部操作日志（用户在设置页主动点击"清理所有日志"时调用） */
    fun clearAuditLogs() {
        safeLaunch {
            auditLogRepo?.clearAll()
            _statusMessage.value = "已清空全部操作日志"
        }
    }

    // ==================== v26 优化4：孤儿数据自检工具 ====================

    /**
     * 孤儿数据自检结果（UI 据此显示"发现 X 条无效记录"对话框）
     */
    data class OrphanCheckResult(
        val orphanLessons: List<String>,      // 孤儿 lesson 的 studentName 列表
        val orphanSchedules: List<String>,   // 孤儿 schedule 的 studentName 列表
        val orphanPackages: List<String>     // 孤儿 package 的 studentName 列表
    ) {
        val total: Int get() = orphanLessons.size + orphanSchedules.size + orphanPackages.size
        val hasOrphans: Boolean get() = total > 0
    }

    /** 自检结果 StateFlow（null=未检查，非 null=检查完成） */
    private val _orphanCheckResult = MutableStateFlow<OrphanCheckResult?>(null)
    val orphanCheckResult: StateFlow<OrphanCheckResult?> = _orphanCheckResult.asStateFlow()

    /** 自检是否进行中（UI 显示加载动画） */
    private val _orphanChecking = MutableStateFlow(false)
    val orphanChecking: StateFlow<Boolean> = _orphanChecking.asStateFlow()

    /**
     * 扫描孤儿数据：检查 lessons / schedules / lesson_packages 表中
     * studentName 在 students 表（含已软删除）里查不到的记录。
     *
     * 流程：
     * 1. 查询 students 表所有姓名（含已软删除）作为白名单
     * 2. 遍历 lessons / schedules / lesson_packages，找出 studentName 不在白名单的记录
     * 3. 通过 [orphanCheckResult] 反馈给 UI，由用户确认是否清理
     *
     * 性能：所有查询切到 [Dispatchers.IO]，避免阻塞主线程。
     */
    fun checkOrphanData() {
        safeLaunch {
            _orphanChecking.value = true
            try {
                val db = com.shangmentiyu.sportscoach.data.db.AppDatabase.getDatabase(app)
                val result = withContext(Dispatchers.IO) {
                    val validNames = db.studentDao().getAllIncludeDeleted().first().map { it.name }.toSet()

                    val orphanLessons = db.lessonDao().getAllOnce().map { it.studentName }
                        .filter { it !in validNames }.distinct()
                    val orphanSchedules = db.scheduleDao().getAllOnce().map { it.studentName }
                        .filter { it !in validNames }.distinct()
                    val orphanPackages = db.lessonPackageDao().getAllOnce().map { it.studentName }
                        .filter { it !in validNames }.distinct()

                    OrphanCheckResult(orphanLessons, orphanSchedules, orphanPackages)
                }
                _orphanCheckResult.value = result
                _statusMessage.value = if (result.hasOrphans) {
                    "发现 ${result.total} 条孤儿数据，请确认是否清理"
                } else {
                    "数据库自检完成，未发现孤儿数据"
                }
            } finally {
                _orphanChecking.value = false
            }
        }
    }

    /**
     * 清理孤儿数据：删除 lessons / schedules / lesson_packages 表中
     * studentName 不在 students 表的记录。
     *
     * 调用时机：用户在确认对话框点击"清理"后调用。
     * 安全保证：在 [Dispatchers.IO] + [db.withTransaction] 单事务内原子完成，
     * 避免中途失败残留脏数据。
     *
     * 注意：必须使用 [androidx.room.withTransaction] 而非 [RoomDatabase.runInTransaction]，
     * 因为后者是 Java 同步代码块，无法调用 suspend DAO 方法。
     */
    fun cleanOrphanData() {
        safeLaunch {
            val result = _orphanCheckResult.value ?: return@safeLaunch
            if (!result.hasOrphans) {
                _statusMessage.value = "无可清理的孤儿数据"
                return@safeLaunch
            }
            val db = com.shangmentiyu.sportscoach.data.db.AppDatabase.getDatabase(app)
            var cleanedCount = 0
            withContext(Dispatchers.IO) {
                db.withTransaction {
                    for (name in result.orphanLessons) {
                        db.lessonDao().deleteByStudent(name)
                        cleanedCount++
                    }
                    for (name in result.orphanSchedules) {
                        db.scheduleDao().deleteByStudent(name)
                        cleanedCount++
                    }
                    for (name in result.orphanPackages) {
                        db.lessonPackageDao().deleteByStudent(name)
                        cleanedCount++
                    }
                }
            }
            _statusMessage.value = "已清理 $cleanedCount 条孤儿数据"
            _orphanCheckResult.value = null
            // v26 优化1：记录清理日志
            auditLogRepo?.log(
                action = "清理孤儿数据",
                summary = "清理 $cleanedCount 条孤儿数据（lessons=${result.orphanLessons.size} / schedules=${result.orphanSchedules.size} / packages=${result.orphanPackages.size}）"
            )
        }
    }

    /** 重置孤儿自检结果（UI 关闭对话框时调用） */
    fun clearOrphanCheckResult() {
        _orphanCheckResult.value = null
    }

    // ==================== v32 优化1：WebDAV 云盘备份配置 ====================

    /**
     * WebDAV 凭证加密存储（懒加载，仅设置页使用）。
     *
     * - 使用 [EncryptedSharedPreferences] 加密存储账号密码
     * - 业务参数（enabled/baseUrl/remoteDir）通过 [config] Flow 暴露给 UI
     */
    private val webDavStore: com.shangmentiyu.sportscoach.data.repo.WebDavCredentialsStore by lazy {
        com.shangmentiyu.sportscoach.data.repo.WebDavCredentialsStore(app)
    }

    /** WebDAV 配置 Flow（不含密码，UI 订阅刷新） */
    val webDavConfig: StateFlow<com.shangmentiyu.sportscoach.data.repo.WebDavConfig> =
        webDavStore.config
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), webDavStore.getFullConfig())

    /** 是否正在测试 WebDAV 连接（控制按钮禁用 + 加载动画） */
    private val _webDavTesting = MutableStateFlow(false)
    val webDavTesting: StateFlow<Boolean> = _webDavTesting.asStateFlow()

    /**
     * 保存 WebDAV 配置（业务参数明文 + 账号密码加密）。
     *
     * @param enabled 是否启用云盘推送
     * @param baseUrl WebDAV 服务地址
     * @param remoteDir 远程存放目录
     * @param username 用户名（应用级专用密码）
     * @param password 密码（应用级专用密码）
     */
    fun saveWebDavConfig(
        enabled: Boolean,
        baseUrl: String,
        remoteDir: String,
        username: String,
        password: String
    ) {
        safeLaunch {
            val cfg = com.shangmentiyu.sportscoach.data.repo.WebDavConfig(
                enabled = enabled,
                baseUrl = baseUrl.trim(),
                remoteDir = remoteDir.trim().ifBlank {
                    com.shangmentiyu.sportscoach.data.repo.WebDavCredentialsStore.DEFAULT_REMOTE_DIR
                },
                username = username.trim(),
                password = password  // 不 trim，密码可能含前后空格
            )
            webDavStore.saveConfig(cfg)
            _statusMessage.value = if (enabled) "WebDAV 已启用，下次备份将自动推送" else "WebDAV 已关闭"
            // v26 优化1：记录配置变更日志（不记录密码）
            auditLogRepo?.log(
                action = "WebDAV 配置保存",
                summary = "enabled=$enabled, baseUrl=${cfg.baseUrl}, remoteDir=${cfg.remoteDir}"
            )
        }
    }

    /**
     * 测试 WebDAV 连接（PROPFIND 深度 0）。
     *
     * 调用时机：设置页"测试连接"按钮。
     * 流程：先保存当前表单配置 → 再发起 PROPFIND 测试。
     */
    fun testWebDavConnection(
        baseUrl: String,
        remoteDir: String,
        username: String,
        password: String
    ) {
        safeLaunch {
            _webDavTesting.value = true
            try {
                // 先临时保存配置（不修改 enabled），让测试基于最新输入
                val currentEnabled = webDavStore.getFullConfig().enabled
                webDavStore.saveConfig(
                    com.shangmentiyu.sportscoach.data.repo.WebDavConfig(
                        enabled = currentEnabled,
                        baseUrl = baseUrl.trim(),
                        remoteDir = remoteDir.trim().ifBlank {
                            com.shangmentiyu.sportscoach.data.repo.WebDavCredentialsStore.DEFAULT_REMOTE_DIR
                        },
                        username = username.trim(),
                        password = password
                    )
                )
                val mgr = com.shangmentiyu.sportscoach.core.WebDavManager(app, webDavStore)
                val ok = mgr.testConnection()
                _statusMessage.value = if (ok) "WebDAV 连接成功" else "WebDAV 连接失败，请检查地址与凭证"
            } finally {
                _webDavTesting.value = false
            }
        }
    }

    /** 清除 WebDAV 配置（设置页"清除配置"按钮） */
    fun clearWebDavConfig() {
        safeLaunch {
            webDavStore.clear()
            _statusMessage.value = "WebDAV 配置已清除"
            auditLogRepo?.log(action = "WebDAV 配置清除", summary = "用户主动清除 WebDAV 凭证")
        }
    }

    /**
     * 仅切换 WebDAV 启用状态，不修改已保存的账号密码。
     *
     * 调用时机：设置页 Switch 开关切换。
     * 设计动机：避免在切换开关时传入空密码覆盖加密存储中的真实密码。
     */
    fun setWebDavEnabled(enabled: Boolean) {
        safeLaunch {
            webDavStore.setEnabled(enabled)
            _statusMessage.value = if (enabled) "WebDAV 已启用" else "WebDAV 已关闭"
            auditLogRepo?.log(
                action = "WebDAV 开关切换",
                summary = "enabled=$enabled"
            )
        }
    }

    // ==================== v32 优化2：训练动作积木库管理 ====================

    /**
     * 训练动作积木库 Flow（UI 订阅后自动刷新）。
     *
     * - 首次返回 [WebDavCredentialsStore.DEFAULT_EXERCISE_BLOCKS] 预置列表
     * - 用户自定义后返回持久化的列表
     */
    private val _exerciseBlocks = MutableStateFlow<List<String>>(emptyList())
    val exerciseBlocks: StateFlow<List<String>> = _exerciseBlocks.asStateFlow()

    /**
     * 进入设置页时加载一次积木库（UI LaunchedEffect 调用）。
     *
     * 异常保护：
     * - [webDavStore] 是 `by lazy` 初始化，首次访问会触发
     *   [com.shangmentiyu.sportscoach.data.repo.WebDavCredentialsStore] 构造，
     *   其中 [androidx.security.crypto.EncryptedSharedPreferences.create] 在部分设备上
     *   可能抛 KeyStore / GeneralSecurity / IO 异常（虽被 try-catch 兜成 null，
     *   但 lazy 委托本身可能因其他初始化路径失败而抛出）
     * - 此处用 [safeLaunch] 挂载 [appExceptionHandler]，确保任何异常被统一捕获并落盘，
     *   避免传播到 Compose 协程作用域导致"操作遇到异常"toast
     * - 失败时静默降级为空列表，不阻塞 UI 渲染
     */
    fun loadExerciseBlocks() {
        safeLaunch {
            try {
                _exerciseBlocks.value = webDavStore.getExerciseBlocks()
            } catch (e: Exception) {
                // 静默降级：积木库加载失败不应阻塞设置页其他模块
                android.util.Log.w(
                    "SettingsViewModel",
                    "loadExerciseBlocks 失败：${e.javaClass.simpleName} - ${e.message}"
                )
                _exerciseBlocks.value = emptyList()
            }
        }
    }

    /**
     * 添加新动作到积木库（去重）。
     *
     * @param name 动作名称（自动 trim，空白忽略）
     */
    fun addExerciseBlock(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        safeLaunch {
            webDavStore.addExerciseBlock(trimmed)
            _exerciseBlocks.value = webDavStore.getExerciseBlocks()
            _statusMessage.value = "已添加动作：$trimmed"
        }
    }

    /**
     * 删除指定动作积木。
     *
     * @param name 待删除的动作名称
     */
    fun removeExerciseBlock(name: String) {
        safeLaunch {
            webDavStore.removeExerciseBlock(name)
            _exerciseBlocks.value = webDavStore.getExerciseBlocks()
            _statusMessage.value = "已删除动作：$name"
        }
    }

    /**
     * 批量替换积木库（用户在对话框中编辑完整列表后一次性保存）。
     *
     * @param blocks 新的完整动作列表
     */
    fun setExerciseBlocks(blocks: List<String>) {
        safeLaunch {
            webDavStore.setExerciseBlocks(blocks)
            _exerciseBlocks.value = webDavStore.getExerciseBlocks()
            _statusMessage.value = "积木库已更新（共 ${blocks.size} 个动作）"
        }
    }

    // ==================== v32 优化3：UDP 设备自动发现 ====================

    /**
     * 桌面端连接状态（绿色指示灯）。
     *
     * - null：未检测到桌面端
     * - 非 null：包含桌面端 IP 与最后心跳时间
     *
     * 数据来源：[com.shangmentiyu.sportscoach.core.UdpDesktopDiscoveryService] 收到广播后写入
     */
    private val _desktopConnection = MutableStateFlow<DesktopConnection?>(null)
    val desktopConnection: StateFlow<DesktopConnection?> = _desktopConnection.asStateFlow()

    /** 桌面端连接信息数据类 */
    data class DesktopConnection(
        val host: String,
        val port: Int,
        val lastSeenAt: Long  // 收到广播的时间戳（毫秒）
    ) {
        /** 是否在 60 秒内有过广播（超过视为离线） */
        val isAlive: Boolean
            get() = System.currentTimeMillis() - lastSeenAt < 60_000L
    }

    /** 进入设置页时刷新一次桌面端连接状态 */
    fun refreshDesktopConnection() {
        val prefs = app.getSharedPreferences("desktop_discovery", Context.MODE_PRIVATE)
        val host = prefs.getString("host", "") ?: ""
        if (host.isBlank()) {
            _desktopConnection.value = null
            return
        }
        _desktopConnection.value = DesktopConnection(
            host = host,
            port = prefs.getInt("port", 0),
            lastSeenAt = prefs.getLong("last_seen_at", 0L)
        )
    }
}
