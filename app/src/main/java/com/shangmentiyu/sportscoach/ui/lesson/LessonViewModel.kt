package com.shangmentiyu.sportscoach.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.core.JsonSafe
import com.shangmentiyu.sportscoach.data.model.ExerciseItem
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.ScoreItem
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.ScheduleMemoryRepository
import com.shangmentiyu.sportscoach.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class LessonViewModel(
    private val lessonRepo: LessonRepository,
    private val opRepo: OperationRepository,
    private val memoryRepo: ScheduleMemoryRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    companion object {
        /** 签到地点记忆字段名（与排课记忆 "location" 区分，独立记忆签到页地点） */
        const val FIELD_CHECKIN_LOCATION = "checkin_location"

        /** 教练默认值：设置中无教练名时强制显示 */
        const val DEFAULT_COACH = "李"
    }

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.core.CoroutineExt.createAppExceptionHandler(_toast, "LessonViewModel")

    private val _lesson = MutableStateFlow<Lesson?>(null)
    val lesson: StateFlow<Lesson?> = _lesson.asStateFlow()

    private val _exercises = MutableStateFlow<List<ExerciseItem>>(emptyList())
    val exercises: StateFlow<List<ExerciseItem>> = _exercises.asStateFlow()

    /** 当前课时消耗自的课时包名（空=未关联课时包） */
    private val _packageName = MutableStateFlow("")
    val packageName: StateFlow<String> = _packageName.asStateFlow()

    /** 教练默认值：优先读取设置中的教练名，为空时强制 "李" */
    val defaultCoach: StateFlow<String> = settingsRepo.coach
        .map { it.ifBlank { DEFAULT_COACH } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_COACH)

    /** 最近使用过的签到地点记忆（供地点输入框下拉建议，去重按最近优先） */
    val locationMemories: StateFlow<List<String>> = memoryRepo.getRecentMemories(FIELD_CHECKIN_LOCATION)
        .map { list -> list.map { it.value }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 自动保存防抖任务：连续输入时只保留最后一次写库 */
    private var saveJob: Job? = null

    /** 防抖延迟（毫秒）：用户停止输入 500ms 后才真正写库 */
    private val saveDebounceMs = 500L

    /**
     * 独立保存作用域：onCleared 时 viewModelScope 已取消，
     * 用此作用域保证最后一次防抖未触发的改动也能写入数据库。
     */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun loadLesson(id: String) {
        viewModelScope.launch(appExceptionHandler) {
            val l = lessonRepo.getById(id)
            _lesson.value = l
            if (l != null) {
                _exercises.value = parseExercises(l.content)
                // 查询课时包名
                _packageName.value = if (l.packageId.isBlank()) {
                    ""
                } else {
                    opRepo.getPkgById(l.packageId)?.name ?: "（课时包已删除）"
                }
            }
        }
    }

    private fun parseExercises(json: String): List<ExerciseItem> {
        if (json.isBlank()) return emptyList()
        // 使用 JsonSafe 兜底：脏数据返回空列表，不崩溃
        val arr = JsonSafe.parseArray(json) ?: return emptyList()
        val result = mutableListOf<ExerciseItem>()
        for (i in 0 until arr.length()) {
            // 单条 item 解析失败时跳过，不影响其他有效项
            val obj = arr.optJSONObject(i) ?: continue
            result.add(ExerciseItem(
                name = obj.optString("name"),
                sets = obj.optInt("sets", 3),
                reps = obj.optString("reps"),
                intensity = obj.optString("intensity", "中"),
                done = obj.optBoolean("done", false),
                note = obj.optString("note")
            ))
        }
        return result
    }

    private fun exercisesToJson(list: List<ExerciseItem>): String {
        val arr = JSONArray()
        for (item in list) {
            arr.put(JSONObject().apply {
                put("name", item.name)
                put("sets", item.sets)
                put("reps", item.reps)
                put("intensity", item.intensity)
                put("done", item.done)
                put("note", item.note)
            })
        }
        return arr.toString()
    }

    /**
     * 更新课时字段：内存 StateFlow 立即更新（UI 即时响应），
     * 数据库写入采用 500ms 防抖，连续输入只保留最后一次写库。
     *
     * 保存成功后顺带沉淀记忆：
     * - 教练名（非空且发生变化）→ SettingsRepository，作为后续签到页默认教练
     * - 上课地点（非空且发生变化）→ ScheduleMemoryRepository(field="checkin_location")，
     *   下次打开签到页时自动填充/提供下拉建议
     */
    fun updateLesson(updater: (Lesson) -> Lesson) {
        val current = _lesson.value ?: return
        val updated = updater(current)
        _lesson.value = updated
        // 取消上一次未触发的写库任务，重置防抖计时
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(saveDebounceMs)
            lessonRepo.updateLesson(updated)
            // 成功保存后写记忆（幂等：重复值仅更新 updatedAt）
            if (updated.location.isNotBlank() && updated.location != current.location) {
                memoryRepo.saveMemory(
                    updated.coach.ifBlank { DEFAULT_COACH },
                    FIELD_CHECKIN_LOCATION,
                    updated.location
                )
            }
            if (updated.coach.isNotBlank() && updated.coach != current.coach) {
                settingsRepo.setCoach(updated.coach)
            }
        }
    }

    /**
     * 课后签退：记录签退时间（当前 HH:mm）与可选的签退照片路径。
     *
     * 签退不消课（消课在签到时已完成），仅补充签退时间与照片。
     * 签退照片路径由 UI 层先通过 [updateLesson] 写入 signOutPhotoPath 字段，
     * 此方法只负责设置 signOutTime 并立即持久化（不走防抖，确保签退时间精确）。
     *
     * @param onDone 签退完成回调（主线程），参数为是否成功
     */
    fun signOut(onDone: (Boolean) -> Unit = {}) {
        val current = _lesson.value ?: run { onDone(false); return }
        viewModelScope.launch(appExceptionHandler) {
            val photoPath = current.signOutPhotoPath
            val ok = lessonRepo.signOut(current.id, photoPath)
            if (ok) {
                // 签退成功：沉淀地点记忆（供下次签到页自动填充）
                if (current.location.isNotBlank()) {
                    memoryRepo.saveMemory(
                        current.coach.ifBlank { DEFAULT_COACH },
                        FIELD_CHECKIN_LOCATION,
                        current.location
                    )
                }
                // 重新加载课时，刷新 signOutTime 字段
                val refreshed = lessonRepo.getById(current.id)
                if (refreshed != null) {
                    _lesson.value = refreshed
                }
            }
            onDone(ok)
        }
    }

    /**
     * 强制立即写库（页面退出等场景）：取消未触发的防抖任务，同步最新值。
     */
    fun flushSave() {
        val current = _lesson.value ?: return
        saveJob?.cancel()
        saveJob = null
        saveScope.launch {
            lessonRepo.updateLesson(current)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel 销毁时确保最后一次改动已写库：
        // saveScope 独立于 viewModelScope，不会随 onCleared 取消
        saveJob?.cancel()
        val current = _lesson.value
        if (current != null) {
            saveScope.launch {
                lessonRepo.updateLesson(current)
            }
        }
        // 留出窗口让挂起的写库完成（不阻塞主线程）
        // saveScope 不主动 cancel，由 JVM 回收
    }

    fun updateExercises(newList: List<ExerciseItem>) {
        _exercises.value = newList
        updateLesson { it.copy(content = exercisesToJson(newList)) }
    }

    fun addExercise(item: ExerciseItem) {
        val list = _exercises.value.toMutableList()
        list.add(item)
        updateExercises(list)
    }

    fun updateExercise(index: Int, item: ExerciseItem) {
        val list = _exercises.value.toMutableList()
        if (index in list.indices) {
            list[index] = item
            updateExercises(list)
        }
    }

    fun removeExercise(index: Int) {
        val list = _exercises.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            updateExercises(list)
        }
    }
}
