package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.core.AutoBackupScheduler
import com.shangmentiyu.sportscoach.core.JsonSafe
import com.shangmentiyu.sportscoach.data.db.LessonDao
import com.shangmentiyu.sportscoach.data.db.ScheduleDao
import com.shangmentiyu.sportscoach.data.model.ExerciseItem
import com.shangmentiyu.sportscoach.data.model.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 排课 Repository（管理层）：管理周期性课程安排。
 *
 * 与 [LessonRepository] 的区别：
 * - Lesson 表示已发生的单次课堂记录（含签到、成绩、小结）
 * - Schedule 表示固定时段的周期性排课（如"每周一 10:00 给张三上课"）
 *
 * 本类封装：
 * - 基本 CRUD（增删改查）
 * - 教练时间冲突检测（[checkCoachConflict] + [CoachConflictException]）
 * - 训练内容 JSON 解析（[parseContent] / [contentToJson]）
 * - 课表视图所需的"按天分组"查询
 * - 历史废弃占位排课清理（[clearUnfinishedPastLongTermLessons]）
 *
 * 日期格式化统一使用 [DateTimeFormatter]（线程安全），无 [SimpleDateFormat] 的 Calendar 状态污染问题。
 *
 * @param lessonDao v33 新增：清理长期排课生成的历史废弃 Lesson 占位记录时使用。
 *                  仅 [clearUnfinishedPastLongTermLessons] 方法依赖此 DAO，
 *                  其他方法保持纯 ScheduleDao 访问，避免越权访问 lessons 表。
 */
class ScheduleRepository(
    private val dao: ScheduleDao,
    private val lessonDao: LessonDao
) {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

    /** 当前日期字符串（yyyy-MM-dd），基于 [LocalDate.now] 线程安全获取 */
    private fun todayDateStr(): String = LocalDate.now().format(dateFormatter)

    /** 所有排课（按 周几→开始时间 排序） */
    fun getAllSchedules(): Flow<List<Schedule>> = dao.getAll()

    /** 一次性获取所有排课列表（非 Flow，用于长期课自动生成等一次性检查） */
    suspend fun getAllSchedulesOnce(): List<Schedule> = dao.getAllOnce()

    /** 启用中的排课 */
    fun getActiveSchedules(): Flow<List<Schedule>> = dao.getActive()

    /** 按学员查询启用中的排课 */
    fun getSchedulesByStudent(name: String): Flow<List<Schedule>> = dao.getByStudent(name)

    /** 按教练查询启用中的排课 */
    fun getSchedulesByCoach(name: String): Flow<List<Schedule>> = dao.getByCoach(name)

    /** 按 dayOfWeek 查询启用中的排课（1=周一 ... 7=周日） */
    fun getSchedulesByDay(dayOfWeek: Int): Flow<List<Schedule>> = dao.getByDay(dayOfWeek)

    suspend fun getById(id: String): Schedule? = dao.getById(id)

    /**
     * 教练时间冲突检测（v23 引入）。
     *
     * 冲突判定：同一教练（coachName 完全相同）在同一天（dayOfWeek 相同）同一开始时间（startTime 完全相同）
     * 已存在另一条启用中的排课，则视为冲突。
     *
     * 设计权衡：
     * - 仅按 startTime 精确匹配，不做时间段重叠计算（避免引入复杂的时间区间算法，
     *   与现有 UI 字段语义一致：用户填写的是开始时间，durationMinutes 仅作展示）
     * - 编辑场景通过 [excludeScheduleId] 排除自身，避免"自己与自己冲突"
     * - 已停用（isActive=false）的排课不参与冲突判定，可自由复用时段
     *
     * @param coachName 教练姓名（空串视为"默认教练"，仍参与判定以保证一致性）
     * @param dayOfWeek ISO 周几（1=周一 ... 7=周日）
     * @param startTime 开始时间字符串（HH:mm）
     * @param excludeScheduleId 编辑场景需排除自身的 ID；新建场景传 null
     * @throws CoachConflictException 冲突时抛出，由调用方捕获并弹窗提示
     */
    suspend fun checkCoachConflict(
        coachName: String,
        dayOfWeek: Int,
        startTime: String,
        excludeScheduleId: String? = null
    ) {
        val normalizedCoach = coachName.ifBlank { "默认教练" }
        val normalizedStart = startTime.trim()
        if (normalizedStart.isBlank()) return  // 时间为空时由上层校验，本方法跳过

        val all = dao.getAllOnce()
        val conflict = all.firstOrNull { s ->
            s.isActive &&
                s.id != excludeScheduleId &&
                s.coachName.ifBlank { "默认教练" } == normalizedCoach &&
                s.dayOfWeek == dayOfWeek &&
                s.startTime.trim() == normalizedStart
        } ?: return
        throw CoachConflictException(
            coachName = normalizedCoach,
            dayOfWeek = dayOfWeek,
            startTime = normalizedStart,
            existingStudentName = conflict.studentName
        )
    }

    /**
     * 新增排课：自动填充生效日期为今天。
     *
     * 保存前调用 [checkCoachConflict] 检测教练时间冲突，冲突时抛出 [CoachConflictException]
     * 并阻止本次写入（dao.insert 不会执行），由 ViewModel 捕获并弹窗提示。
     *
     * @param isLongTerm 是否长期排课，勾选后每周自动生成对应时间的课表
     * @return 新建的排课 ID
     * @throws CoachConflictException 教练时间冲突
     */
    suspend fun addSchedule(
        studentName: String,
        coachName: String,
        dayOfWeek: Int,
        startTime: String,
        durationMinutes: Int = 60,
        location: String = "",
        lessonType: String = "训练课",
        isLongTerm: Boolean = false,
        content: List<ExerciseItem> = emptyList(),
        contentImages: List<String> = emptyList(),
        color: String = "blue",
        note: String = "",
        equipment: List<String> = emptyList()
    ): String {
        // 冲突检测：新建场景无需排除自身
        checkCoachConflict(coachName, dayOfWeek, startTime, excludeScheduleId = null)

        val schedule = Schedule(
            studentName = studentName,
            coachName = coachName,
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            durationMinutes = durationMinutes,
            location = location,
            lessonType = lessonType,
            startDate = todayDateStr(),
            isLongTerm = isLongTerm,
            content = contentToJson(content),
            contentImages = imagesToJson(contentImages),
            color = color,
            note = note,
            equipment = equipmentToJson(equipment)
        )
        dao.insert(schedule)
        // v30：新增排课属于核心数据变更，触发自动备份防抖
        AutoBackupScheduler.notifyDataChange()
        return schedule.id
    }

    /**
     * 直接更新一条排课（编辑场景）。
     *
     * 保存前调用 [checkCoachConflict] 检测教练时间冲突，编辑场景排除自身 ID，
     * 冲突时抛出 [CoachConflictException] 并阻止更新，由 ViewModel 捕获并弹窗提示。
     *
     * === Bug 修复：检测 update 静默失败 ===
     * ScheduleDao.update 返回受影响行数。若返回 0，说明待编辑的记录已被删除
     * （如用户在编辑过程中触发了"清空全部课表"），原代码静默返回成功让用户误以为已保存。
     * 现抛 [NoSuchElementException] 由 ViewModel catch 后向用户提示具体原因。
     *
     * @throws CoachConflictException 教练时间冲突
     * @throws NoSuchElementException 待更新记录不存在（已被删除）
     */
    suspend fun updateSchedule(schedule: Schedule) {
        checkCoachConflict(
            coachName = schedule.coachName,
            dayOfWeek = schedule.dayOfWeek,
            startTime = schedule.startTime,
            excludeScheduleId = schedule.id
        )
        val affected = dao.update(schedule)
        if (affected != 1) {
            throw NoSuchElementException("排课记录不存在或已被删除（id=${schedule.id}，affected=$affected）")
        }
        // v30：更新排课属于核心数据变更，触发自动备份防抖
        AutoBackupScheduler.notifyDataChange()
    }

    /**
     * === v25 优化5：强制新增排课（用户在 UI 二次确认"强制替换"后调用）===
     *
     * 流程：
     * 1. 查询同教练 + 同 dayOfWeek + 同 startTime 且启用中的旧排课
     * 2. 逐条 [deleteById] 删除冲突项（含训练内容/记忆一并清理）
     * 3. 直接 [dao.insert] 写入新排课（不再触发 [checkCoachConflict]）
     *
     * 适用场景：教练临时换班，需把已排课的学员替换为另一学员，而非调整时间。
     *
     * @return 新建的排课 ID
     */
    suspend fun addScheduleForce(
        studentName: String,
        coachName: String,
        dayOfWeek: Int,
        startTime: String,
        durationMinutes: Int = 60,
        location: String = "",
        lessonType: String = "训练课",
        isLongTerm: Boolean = false,
        content: List<ExerciseItem> = emptyList(),
        contentImages: List<String> = emptyList(),
        color: String = "blue",
        note: String = "",
        equipment: List<String> = emptyList()
    ): String {
        deleteConflictingSchedules(coachName, dayOfWeek, startTime, excludeScheduleId = null)

        val schedule = Schedule(
            studentName = studentName,
            coachName = coachName,
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            durationMinutes = durationMinutes,
            location = location,
            lessonType = lessonType,
            startDate = todayDateStr(),
            isLongTerm = isLongTerm,
            content = contentToJson(content),
            contentImages = imagesToJson(contentImages),
            color = color,
            note = note,
            equipment = equipmentToJson(equipment)
        )
        dao.insert(schedule)
        // v30：强制新增排课属于核心数据变更，触发自动备份防抖
        AutoBackupScheduler.notifyDataChange()
        return schedule.id
    }

    /**
     * === v25 优化5：强制更新排课（用户在 UI 二次确认"强制替换"后调用）===
     *
     * 流程：
     * 1. 查询同教练 + 同 dayOfWeek + 同 startTime 且启用中的旧排课（排除自身 ID）
     * 2. 逐条 [deleteById] 删除冲突项
     * 3. 直接 [dao.update] 写入编辑后的排课（不再触发 [checkCoachConflict]）
     *
     * === Bug 修复：检测 update 静默失败 ===
     * 同 [updateSchedule]，若待更新记录已被删除则抛 [NoSuchElementException]。
     */
    suspend fun updateScheduleForce(schedule: Schedule) {
        deleteConflictingSchedules(
            coachName = schedule.coachName,
            dayOfWeek = schedule.dayOfWeek,
            startTime = schedule.startTime,
            excludeScheduleId = schedule.id
        )
        val affected = dao.update(schedule)
        if (affected != 1) {
            throw NoSuchElementException("排课记录不存在或已被删除（id=${schedule.id}，affected=$affected）")
        }
        // v30：强制更新排课属于核心数据变更，触发自动备份防抖
        AutoBackupScheduler.notifyDataChange()
    }

    /**
     * === v25 优化5：内部工具——删除所有冲突排课（强制替换场景使用）===
     *
     * 删除规则：同教练（空串视为"默认教练"）+ 同 dayOfWeek + 同 startTime 且 isActive=true 的排课，
     * 排除 [excludeScheduleId]（编辑场景排除自身）。
     */
    private suspend fun deleteConflictingSchedules(
        coachName: String,
        dayOfWeek: Int,
        startTime: String,
        excludeScheduleId: String?
    ) {
        val normalizedCoach = coachName.ifBlank { "默认教练" }
        val normalizedStart = startTime.trim()
        if (normalizedStart.isBlank()) return

        val all = dao.getAllOnce()
        all.filter { s ->
            s.isActive &&
                s.id != excludeScheduleId &&
                s.coachName.ifBlank { "默认教练" } == normalizedCoach &&
                s.dayOfWeek == dayOfWeek &&
                s.startTime.trim() == normalizedStart
        }.forEach { s -> dao.deleteById(s.id) }
    }

    /** 删除一条排课 */
    suspend fun deleteSchedule(id: String) {
        dao.deleteById(id)
        // v30：删除排课属于核心数据变更，触发自动备份防抖
        AutoBackupScheduler.notifyDataChange()
    }

    /**
     * 解析上课器材 JSON 为字符串列表。
     * 使用 [JsonSafe] 兜底：脏数据返回空列表，不崩溃。
     */
    fun parseEquipment(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        val arr = JsonSafe.parseArray(json) ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val name = arr.optString(i)
            if (name.isNotBlank()) result.add(name)
        }
        return result
    }

    /** 将器材字符串列表序列化为 JSON 数组字符串 */
    fun equipmentToJson(list: List<String>): String {
        val arr = JSONArray()
        for (name in list) {
            arr.put(name)
        }
        return arr.toString()
    }

    /**
     * 解析训练内容图片路径 JSON 为字符串列表。
     * 使用 [JsonSafe] 兜底：脏数据返回空列表，不崩溃。
     */
    fun parseImages(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        val arr = JsonSafe.parseArray(json) ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val path = arr.optString(i)
            if (path.isNotBlank()) result.add(path)
        }
        return result
    }

    /** 将图片路径字符串列表序列化为 JSON 数组字符串 */
    fun imagesToJson(list: List<String>): String {
        val arr = JSONArray()
        for (path in list) {
            arr.put(path)
        }
        return arr.toString()
    }

    /**
     * 解析训练内容 JSON 为 [ExerciseItem] 列表。
     * 使用 [JsonSafe] 兜底：脏数据返回空列表，不崩溃。
     */
    fun parseContent(json: String): List<ExerciseItem> {
        if (json.isBlank()) return emptyList()
        val arr = JsonSafe.parseArray(json) ?: return emptyList()
        val result = mutableListOf<ExerciseItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            result.add(
                ExerciseItem(
                    name = obj.optString("name"),
                    sets = obj.optInt("sets", 3),
                    reps = obj.optString("reps"),
                    intensity = obj.optString("intensity", "中"),
                    done = obj.optBoolean("done", false),
                    note = obj.optString("note")
                )
            )
        }
        return result
    }

    /** 将 [ExerciseItem] 列表序列化为 JSON 字符串 */
    fun contentToJson(list: List<ExerciseItem>): String {
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
     * 获取课表视图所需的"按天分组"数据：7 个 List<Schedule>，下标 0=周一 ... 6=周日。
     * 已过滤未启用项，按 startTime 升序。
     */
    fun getWeekSchedules(): Flow<List<List<Schedule>>> =
        dao.getActive().map { all ->
            (1..7).map { day ->
                all.filter { it.dayOfWeek == day }.sortedBy { it.startTime }
            }
        }

    /**
     * === Bug 修复2：一键清理"历史废弃占位排课" ===
     *
     * 业务背景：
     * - 长期排课（schedule.isLongTerm=true）会自动按 dayOfWeek 生成 Lesson 记录
     * - 历史 Bug 导致即使不勾选长期排课、或为已过去的日期也生成了大量 Lesson 占位记录
     * - 这些记录污染了历史周历视图，导致页面"乱七八糟"
     *
     * 清理逻辑（在 [LessonDao.deleteUnfinishedPastLongTermLessons] 中执行）：
     * 1. status != '已签退'（保留已签退的历史真实记录，作为学员上课凭证）
     * 2. date < 今天（只清理过去日期，不影响今天及未来）
     * 3. lessonType LIKE '%(长期自动)%'（仅清理长期排课自动生成的占位记录）
     *
     * 已签退的真实课时记录（学员已实际消课）不会被清理，仍保留在历史周历中，
     * 由 UI 层通过置灰 + "已过去"角标区分展示。
     *
     * 物理删除 vs 逻辑删除：
     * - 物理删除（DELETE）：因为这些 Lesson 是无价值的占位记录，从未实际签到，
     *   不需要保留审计痕迹，物理删除可释放存储空间并避免再次污染视图。
     * - 已签退的记录属于业务凭证，绝不在本方法中清理。
     *
     * @return 被物理删除的记录数（供 UI 通过 toast 反馈清理结果，如"已清理 23 条过去无效排课"）
     */
    suspend fun clearUnfinishedPastLongTermLessons(): Int {
        val today = todayDateStr()
        val deleted = lessonDao.deleteUnfinishedPastLongTermLessons(today)
        // v30：清理属于核心数据变更，触发自动备份防抖
        if (deleted > 0) {
            AutoBackupScheduler.notifyDataChange()
        }
        return deleted
    }
}
