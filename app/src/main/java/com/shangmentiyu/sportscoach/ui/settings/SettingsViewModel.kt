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

    /** 恢复成功后置为 true，UI 据此弹出"重启应用"确认对话框 */
    private val _needRestart = MutableStateFlow(false)
    val needRestart: StateFlow<Boolean> = _needRestart.asStateFlow()

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
     * 使用 ContentResolver + DocumentFile 读写，适配 Android 10+ 分区存储。
     */
    fun exportTodayRecords(treeUri: Uri) {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            var success = 0
            var fail = 0

            val lessons = lessonRepo.getAllLessons().first().filter { it.date == today }

            if (lessons.isEmpty()) {
                _statusMessage.value = "今日无课堂记录"
                return@launch
            }

            val treeDir = DocumentFile.fromTreeUri(app, treeUri) ?: run {
                _statusMessage.value = "无法访问所选目录"
                return@launch
            }

            for (lesson in lessons) {
                val student = studentRepo.getByName(lesson.studentName)
                val fileName = "${lesson.studentName}_${lesson.date}_课堂记录.xlsx"
                    .replace("/", "_").replace("\\", "_")
                // 同名文件已存在时先删除，避免追加到旧文件
                treeDir.findFile(fileName)?.delete()
                val docFile = treeDir.createFile(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    fileName
                )
                if (docFile == null) {
                    fail++
                    continue
                }
                val ok = app.contentResolver.openOutputStream(docFile.uri)?.use { os ->
                    ExcelSync.exportLessonReport(lesson, student, os)
                } ?: false
                if (ok) success++ else fail++
            }
            _statusMessage.value = "导出完成: 成功${success}条" + if (fail > 0) "，失败${fail}条" else ""
        }
    }

    /**
     * 导出成绩档案到用户通过 SAF 选择的目录。
     * 每个学员独立文件，若档案已存在则读取旧档案追加工作表后整体回写。
     */
    fun exportScoresArchive(treeUri: Uri) {
        viewModelScope.launch {
            var success = 0
            var fail = 0

            val lessons = lessonRepo.getAllLessons().first()
                .filter { it.scores.isNotBlank() && it.scores != "{}" }

            if (lessons.isEmpty()) {
                _statusMessage.value = "无成绩记录可导出"
                return@launch
            }

            val treeDir = DocumentFile.fromTreeUri(app, treeUri) ?: run {
                _statusMessage.value = "无法访问所选目录"
                return@launch
            }

            for (lesson in lessons) {
                val student = studentRepo.getByName(lesson.studentName)
                val name = (student?.name ?: lesson.studentName)
                    .replace("/", "_").replace("\\", "_")
                val fileName = "$name.xlsx"

                // 先读取已有档案（若存在）的输入流
                val existing = treeDir.findFile(fileName)
                val inputStream = existing?.uri?.let { app.contentResolver.openInputStream(it) }

                // 写入临时新文件后覆盖原文件：先创建/复用同名 DocumentFile
                if (existing == null) {
                    treeDir.createFile(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        fileName
                    )
                }
                val target = treeDir.findFile(fileName)
                if (target == null) {
                    inputStream?.close()
                    fail++
                    continue
                }

                val ok = app.contentResolver.openOutputStream(target.uri)?.use { os ->
                    ExcelSync.exportScoresToArchive(lesson, student, inputStream, os) != null
                } ?: false
                inputStream?.close()
                if (ok) success++ else fail++
            }
            _statusMessage.value = "档案导出完成: 成功${success}条" + if (fail > 0) "，失败${fail}条" else ""
        }
    }

    /**
     * 从用户通过 SAF 选择的目录导入学员。
     * 遍历目录下所有 .xlsx 文件，解析 _meta 工作表。
     */
    fun importStudents(treeUri: Uri) {
        viewModelScope.launch {
            val treeDir = DocumentFile.fromTreeUri(app, treeUri) ?: run {
                _statusMessage.value = "无法访问所选目录"
                return@launch
            }

            val streams = mutableListOf<java.io.InputStream>()
            for (file in treeDir.listFiles()) {
                if (file.isFile && file.name?.endsWith(".xlsx") == true &&
                    !file.name!!.startsWith("~$")
                ) {
                    val s = app.contentResolver.openInputStream(file.uri)
                    if (s != null) streams.add(s)
                }
            }

            if (streams.isEmpty()) {
                _statusMessage.value = "未找到可导入的学员档案"
                return@launch
            }

            val students = ExcelSync.importStudentsFromExcel(streams)
            streams.forEach { runCatching { it.close() } }

            if (students.isEmpty()) {
                _statusMessage.value = "未找到可导入的学员"
                return@launch
            }
            studentRepo.importStudents(students)
            _statusMessage.value = "成功导入${students.size}名学员"
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
     * 2. 调用 [BackupRepository.backup] 执行备份
     * 3. 通过 [statusMessage] 反馈结果给 UI
     *
     * @param targetUri 用户通过 SAF CreateDocument 选择的目标文件 Uri
     */
    fun backupData(targetUri: Uri) {
        viewModelScope.launch {
            _backupInProgress.value = true
            try {
                val result = backupRepo.backup(targetUri)
                _statusMessage.value = result.message
            } finally {
                _backupInProgress.value = false
            }
        }
    }

    /**
     * 从用户通过 SAF 选择的备份文件恢复整库数据。
     *
     * 流程：
     * 1. 标记进行中状态（UI 禁用按钮）
     * 2. 调用 [BackupRepository.restore] 执行恢复
     * 3. 成功时标记 [needRestart]，UI 据此引导用户重启应用
     * 4. 失败时仅展示错误消息，不修改任何状态
     *
     * 警告：恢复会覆盖当前所有学员/课时包/排课/签到数据。
     *
     * @param sourceUri 用户通过 SAF OpenDocument 选择的备份文件 Uri
     */
    fun restoreData(sourceUri: Uri) {
        viewModelScope.launch {
            _backupInProgress.value = true
            try {
                val result = backupRepo.restore(sourceUri)
                _statusMessage.value = result.message
                if (result.success && result.needRestart) {
                    _needRestart.value = true
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
