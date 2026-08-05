package com.shangmentiyu.sportscoach.data.model

import androidx.room.TypeConverter
import com.shangmentiyu.sportscoach.core.JsonSafe
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room 类型转换器（v23 扩展）。
 *
 * 提供两类 JSON 字段的相互转换：
 * 1. [ExerciseItem] 列表 ↔ JSON 字符串（[exerciseListToJson] / [jsonToExerciseList]）
 *    - 用于 [Schedule.content] / [Lesson.content] 等训练内容字段
 *    - 当前 Entity 字段类型仍为 `String`（兼容现有 Repository 的手写 JSON 解析逻辑），
 *      未来若将字段类型升级为 `List<ExerciseItem>`，本 Converter 自动生效
 * 2. [String] 列表 ↔ JSON 字符串（[stringListToJson] / [jsonToStringList]）
 *    - 用于 [Schedule.contentImages] / [Schedule.equipment] / [Lesson.contentImages]
 *      等字符串列表字段
 *    - 同样保留 Entity 字段为 `String` 类型，未来升级时直接生效
 *
 * 安全性：所有 `jsonTo*` 方法均通过 [JsonSafe] 兜底，
 * 脏数据（手动改库、迁移残留、旧版格式异常）不会导致整个数据库读取崩溃，
 * 而是返回空列表，保证学员记录仍可打开。
 *
 * 注册：本类需在 [com.shangmentiyu.sportscoach.data.db.AppDatabase] 的 `@TypeConverters`
 * 注解中声明，Room 编译器会自动识别 `@TypeConverter` 注解的方法。
 */
class Converters {

    // ==================== ExerciseItem 列表 ↔ JSON ====================

    /**
     * [ExerciseItem] 列表 → JSON 字符串。
     *
     * 序列化字段与 [jsonToExerciseList] 解析字段一一对应：
     * - name / sets / reps / intensity / done / note
     *
     * 空列表返回 `"[]"`，与 Room 默认值一致，避免 NULL 处理。
     */
    @TypeConverter
    fun exerciseListToJson(list: List<ExerciseItem>): String {
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
     * JSON 字符串 → [ExerciseItem] 列表。
     *
     * 兜底策略：
     * - 空/blank/null JSON → 空列表
     * - [JsonSafe.parseArray] 解析失败 → 空列表
     * - 单条 item 解析失败 → 跳过该条，不影响其他有效项
     *
     * === v33 数据流加固：入参改为可空 [String]? ===
     * 原 `json: String` 在 Room 边界场景下若收到 null（旧迁移残留 / 手动改库 / 字段未赋值），
     * Kotlin 会抛 NPE 导致整个数据库读取崩溃，学员记录无法打开。
     * 改为可空入参 + null 兜底返回空列表，保证脏数据不致全局崩溃。
     */
    @TypeConverter
    fun jsonToExerciseList(json: String?): List<ExerciseItem> {
        if (json.isNullOrBlank()) return emptyList()
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

    // ==================== String 列表 ↔ JSON（v23 新增） ====================

    /**
     * [String] 列表 → JSON 字符串。
     *
     * 用于图片路径列表（[Schedule.contentImages] / [Lesson.contentImages]）、
     * 器材列表（[Schedule.equipment]）等场景。
     *
     * 空列表返回 `"[]"`，与 Room 默认值一致。
     * 元素中若包含双引号等特殊字符，[JSONArray.put] 会自动转义。
     */
    @TypeConverter
    fun stringListToJson(list: List<String>): String {
        val arr = JSONArray()
        for (item in list) {
            arr.put(item)
        }
        return arr.toString()
    }

    /**
     * JSON 字符串 → [String] 列表。
     *
     * 兜底策略与 [jsonToExerciseList] 一致：
     * - 空/blank/null JSON → 空列表
     * - [JsonSafe.parseArray] 解析失败 → 空列表
     * - 单个元素解析失败 → 跳过，不影响其他有效项
     * - 空白字符串元素会被过滤（[isNotBlank]），避免图片路径列表中混入空串
     *
     * === v33 数据流加固：入参改为可空 [String]? ===
     * 同 [jsonToExerciseList]，防止 Room 边界 null 导致 NPE 崩溃。
     */
    @TypeConverter
    fun jsonToStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = JsonSafe.parseArray(json) ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.optString(i)
            // 过滤空白字符串：图片路径/器材名不应为空，避免 UI 渲染空白项
            if (item.isNotBlank()) result.add(item)
        }
        return result
    }
}
