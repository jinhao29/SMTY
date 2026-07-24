package com.shangmentiyu.sportscoach.data.model

import androidx.room.TypeConverter
import com.shangmentiyu.sportscoach.core.JsonSafe
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room 类型转换器：[ExerciseItem] 列表与 JSON 字符串互转。
 *
 * 安全性：[jsonToExerciseList] 使用 [JsonSafe] 兜底，
 * 脏数据（手动改库、迁移残留、旧版格式异常）不会导致整个数据库读取崩溃，
 * 而是返回空列表，保证学员记录仍可打开。
 */
class Converters {
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

    @TypeConverter
    fun jsonToExerciseList(json: String): List<ExerciseItem> {
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
}
