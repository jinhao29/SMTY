package com.shangmentiyu.sportscoach.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.core.BackupManager
import com.shangmentiyu.sportscoach.data.repo.BackupRepository
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.SettingsRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import com.shangmentiyu.sportscoach.excel.ExcelSync
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(
    private val app: Application,
    private val lessonRepo: LessonRepository,
    private val studentRepo: StudentRepository,
    private val settingsRepo: SettingsRepository,
    private val backupRepo: BackupRepository
) : ViewModel() {

    val coach: StateFlow<String> = settingsRepo.coach
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val todayCount = lessonRepo.getTodayCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val totalCount = lessonRepo.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

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
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // 1. 读取今日课时（SQL 直查命中 idx_lessons_date 索引）
            _exportProgress.value = ExportProgress.Working(0, 0, "正在读取今日记录…")
            val lessons = withContext(Dispatchers.IO) {
                lessonRepo.getTodayLessons().first()
            }

            if (lessons.isEmpty()) {
                _exportProgress.value = ExportProgress.Failed("今日无课堂记录")
                return@launch
            }

            val treeDir = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(app, treeUri)
            } ?: run {
                _exportProgress.value = ExportProgress.Failed("无法访问所选目录")
                return@launch
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
        viewModelScope.launch {
            _exportProgress.value = ExportProgress.Working(0, 0, "正在读取成绩记录…")
            val lessons = withContext(Dispatchers.IO) {
                lessonRepo.getAllLessons().first()
                    .filter { it.scores.isNotBlank() && it.scores != "{}" }
            }

            if (lessons.isEmpty()) {
                _exportProgress.value = ExportProgress.Failed("无成绩记录可导出")
                return@launch
            }

            val treeDir = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(app, treeUri)
            } ?: run {
                _exportProgress.value = ExportProgress.Failed("无法访问所选目录")
                return@launch
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
        viewModelScope.launch {
            _exportProgress.value = ExportProgress.Working(0, 0, "正在扫描档案文件…")
            val treeDir = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(app, treeUri)
            } ?: run {
                _exportProgress.value = ExportProgress.Failed("无法访问所选目录")
                return@launch
            }

            val xlsxFiles = withContext(Dispatchers.IO) {
                treeDir.listFiles().filter {
                    it.isFile && it.name?.endsWith(".xlsx") == true && !it.name!!.startsWith("~$")
                }
            }

            if (xlsxFiles.isEmpty()) {
                _exportProgress.value = ExportProgress.Failed("未找到可导入的学员档案")
                return@launch
            }

            _exportProgress.value = ExportProgress.Working(0, xlsxFiles.size, "正在解析档案…")
            val streams = mutableListOf<InputStream>()
            var success = 0
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
                return@launch
            }
            success = students.size
            studentRepo.importStudents(students)
            val msg = "成功导入${success}名学员"
            _exportProgress.value = ExportProgress.Done(success, fail, msg)
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
                    return@launch
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
}
