package com.shangmentiyu.sportscoach.data.model

import androidx.compose.runtime.Stable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.shangmentiyu.sportscoach.core.JsonSafe
import org.json.JSONArray
import org.json.JSONObject

/**
 * 训练周期实体：教练为学员编排的多周训练计划。
 *
 * 一个周期由若干"周计划"组成，每周计划包含目标与训练动作模板。
 * 周期结束后可基于该周期内的课时记录做阶段性总结。
 *
 * 安全性：[parseWeeklyPlans] 走 [JsonSafe] 兜底，脏数据返回空列表，
 * 不会因为周期 JSON 异常导致训练计划页崩溃。
 *
 * 性能：v26 优化2添加 [Stable] 注解，Compose 编译器按字段对比实例，
 * 减少 LazyColumn 滑动时的无效重组。
 */
@Stable
@Entity(tableName = "training_cycles")
data class TrainingCycle(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString().take(8),
    val studentName: String,                  // 学员姓名（软关联，保留用于显示）
    val studentId: String? = null,            // 学员唯一ID（软关联外键，v20 引入，旧数据 NULL）
    val name: String,                         // 周期名称（如"暑期4周体能强化"）
    val goal: String = "",                    // 周期目标
    val totalWeeks: Int = 4,                  // 总周数
    val startDate: String,                    // 开始日期 YYYY-MM-DD
    val endDate: String = "",                 // 结束日期 YYYY-MM-DD（空=按周数推算）
    val weeklyPlanJson: String = "[]",        // 周计划 JSON 数组
    val status: String = "进行中",            // 进行中 / 已完成 / 已归档
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 是否已完成 */
    val isCompleted: Boolean get() = status == "已完成"

    /** 解析周计划列表 */
    fun parseWeeklyPlans(): List<WeeklyPlan> {
        if (weeklyPlanJson.isBlank() || weeklyPlanJson == "[]") return emptyList()
        val arr = JsonSafe.parseArray(weeklyPlanJson) ?: return emptyList()
        val result = mutableListOf<WeeklyPlan>()
        for (i in 0 until arr.length()) {
            // 单周计划解析失败时跳过，不影响其他有效周
            val obj = arr.optJSONObject(i) ?: continue
            result.add(
                WeeklyPlan(
                    weekIndex = obj.optInt("weekIndex", i + 1),
                    title = obj.optString("title", "第${i + 1}周"),
                    goal = obj.optString("goal", ""),
                    focus = obj.optString("focus", ""),
                    exercisesJson = obj.optString("exercisesJson", "[]")
                )
            )
        }
        return result
    }

    /** 序列化周计划列表 */
    fun withWeeklyPlans(plans: List<WeeklyPlan>): TrainingCycle {
        val arr = JSONArray()
        for (p in plans) {
            arr.put(JSONObject().apply {
                put("weekIndex", p.weekIndex)
                put("title", p.title)
                put("goal", p.goal)
                put("focus", p.focus)
                put("exercisesJson", p.exercisesJson)
            })
        }
        return copy(weeklyPlanJson = arr.toString())
    }
}

/**
 * 单周训练计划（不持久化，作为 TrainingCycle.weeklyPlanJson 的元素）。
 *
 * 性能：v26 优化2添加 [Stable] 注解，作为 Compose State 使用时避免无效重组。
 */
@Stable
data class WeeklyPlan(
    val weekIndex: Int = 1,           // 第几周（1-based）
    val title: String = "",           // 周标题（如"基础适应周"）
    val goal: String = "",            // 本周目标
    val focus: String = "",           // 训练重点（如"核心力量"）
    val exercisesJson: String = "[]"  // 本周推荐动作 JSON
)
