package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.core.JsonSafe
import com.shangmentiyu.sportscoach.data.db.ScheduleDao
import com.shangmentiyu.sportscoach.data.model.ExerciseItem
import com.shangmentiyu.sportscoach.data.model.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
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
 * - 训练内容 JSON 解析（[parseContent] / [contentToJson]）
 * - 课表视图所需的"按天分组"查询
 *
 * 注意：[SimpleDateFormat] 非线程安全，统一通过 [todayDateStr] 在方法内新建实例。
 */
class ScheduleRepository(private val dao: ScheduleDao) {

    /** 当前日期字符串（yyyy-MM-dd），每次调用新建 SimpleDateFormat 实例 */
    private fun todayDateStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

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
     * 新增排课：自动填充生效日期为今天。
     * @param isLongTerm 是否长期排课，勾选后每周自动生成对应时间的课表
     * @return 新建的排课 ID
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
        return schedule.id
    }

    /** 直接更新一条排课（编辑场景） */
    suspend fun updateSchedule(schedule: Schedule) = dao.update(schedule)

    /** 删除一条排课 */
    suspend fun deleteSchedule(id: String) = dao.deleteById(id)

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
}
