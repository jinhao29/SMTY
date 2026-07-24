package com.shangmentiyu.sportscoach.core

import com.shangmentiyu.sportscoach.core.AbilityAnalyzer.AbilityRadar

/**
 * AI 训练计划生成器（处理器层）。
 *
 * 纯逻辑单元：根据学员五维能力雷达数据，
 * 识别最弱维度并从动作库中筛选针对性训练动作，
 * 自动组装成一份完整的个性化训练计划。
 *
 * 无状态、无 Android 依赖，便于单元测试。
 */
object TrainingPlanGenerator {

    /** 五维名称（与 AbilityAnalyzer.DIMENSIONS 对齐） */
    private val DIMENSIONS = AbilityAnalyzer.DIMENSIONS

    /**
     * 维度 → 针对性训练动作库。
     * 每个维度提供 3-4 个动作，按推荐优先级排序。
     */
    private val DIMENSION_EXERCISES: Map<String, List<Exercise>> = mapOf(
        "速度" to listOf(
            Exercise("30米冲刺", 6, "30米", "起跑反应快，全程加速"),
            Exercise("50米冲刺", 4, "50米", "前倾起跑，保持步频"),
            Exercise("高抬腿跑", 4, "20米", "膝盖高抬，前脚掌着地"),
            Exercise("起跑反应练习", 5, "10米", "听信号起跑，加速启动")
        ),
        "力量" to listOf(
            Exercise("深蹲", 4, "20次", "膝盖不过脚尖，臀部后坐"),
            Exercise("俯卧撑", 3, "15次", "身体成直线，肘部夹紧"),
            Exercise("弓步蹲", 3, "12次/侧", "前膝90度，后腿伸直"),
            Exercise("平板支撑", 3, "60秒", "核心收紧，臀部不下塌")
        ),
        "耐力" to listOf(
            Exercise("定时慢跑", 1, "800米", "保持匀速，呼吸节奏稳定"),
            Exercise("跳绳", 4, "1分钟", "前脚掌着地，节奏稳定"),
            Exercise("波比跳", 3, "15次", "全身爆发，动作连贯"),
            Exercise("4分钟跳绳", 1, "4分钟", "持续跳跃，调整呼吸")
        ),
        "柔韧" to listOf(
            Exercise("坐位体前屈", 3, "30秒", "直腿，缓慢前屈不弹震"),
            Exercise("站位体前屈", 3, "30秒", "膝盖伸直，手指触地"),
            Exercise("股四头肌拉伸", 2, "20秒/侧", "单脚站立，脚跟贴臀"),
            Exercise("肩部拉伸", 2, "20秒/侧", "手臂横胸前，对侧压肘")
        ),
        "灵敏" to listOf(
            Exercise("折返跑", 4, "4趟", "转身降重心，触线变向"),
            Exercise("10米×4折返跑", 4, "4趟", "快速变向，触线转身"),
            Exercise("侧滑步", 3, "10米", "低重心，快速侧移"),
            Exercise("登山跑", 3, "30秒", "交替提膝，核心收紧")
        )
    )

    /** 固定热身动作（每次训练前必做） */
    private val WARMUP_EXERCISES: List<Exercise> = listOf(
        Exercise("慢跑", 1, "5分钟", "保持匀速，逐步提升心率"),
        Exercise("开合跳", 3, "20次", "手脚协调，落地屈膝缓冲"),
        Exercise("动态拉伸", 1, "5分钟", "全身大肌群激活")
    )

    /** 固定放松动作（每次训练后必做） */
    private val COOLDOWN_EXERCISES: List<Exercise> = listOf(
        Exercise("全身拉伸", 1, "5分钟", "每个动作保持20秒"),
        Exercise("呼吸放松", 1, "2分钟", "深呼吸，缓慢呼气")
    )

    /** 推荐的单个训练动作（含推荐理由与优先级） */
    data class RecommendedExercise(
        val exercise: Exercise,
        val dimension: String,       // "热身" / 五维名称 / "放松"
        val reason: String,          // 推荐理由
        val priority: Int            // 0=热身/放松, 1=弱项针对性, 2=辅助
    )

    /** 生成的训练计划 */
    data class TrainingPlan(
        val studentName: String,
        val radar: AbilityRadar,
        val weakDimensions: List<String>,        // 弱项维度（由弱到强排序）
        val exercises: List<RecommendedExercise>,
        val summary: String,                     // 计划摘要
        val createdAt: String                    // 生成时间 yyyy-MM-dd HH:mm
    )

    /**
     * 根据五维能力雷达生成个性化训练计划。
     *
     * 算法：
     * 1. 将五维按得分升序排序，识别最弱 2 个维度
     * 2. 为最弱维度选 2 个针对性动作（priority=1）
     * 3. 为次弱维度选 1 个辅助动作（priority=2）
     * 4. 头尾加上热身与放松（priority=0）
     * 5. 若所有维度均分较低（<60），额外补 1 个核心力量动作
     *
     * @param studentName 学员姓名
     * @param radar 五维能力雷达
     * @return 个性化训练计划
     */
    fun generate(studentName: String, radar: AbilityRadar): TrainingPlan {
        // 1. 维度按得分升序排序
        val dimsWithScore = DIMENSIONS.map { dim ->
            dim to radarScore(radar, dim)
        }.sortedBy { it.second }

        val weakDimensions = dimsWithScore.take(2).map { it.first }

        val exercises = mutableListOf<RecommendedExercise>()

        // 2. 热身
        WARMUP_EXERCISES.forEach { ex ->
            exercises.add(RecommendedExercise(ex, "热身", "训练前必备，激活身体", 0))
        }

        // 3. 最弱维度：2 个针对性动作
        val weakest = weakDimensions.firstOrNull()
        if (weakest != null) {
            DIMENSION_EXERCISES[weakest]?.take(2)?.forEach { ex ->
                exercises.add(
                    RecommendedExercise(
                        exercise = ex,
                        dimension = weakest,
                        reason = "针对最弱维度【$weakest】重点强化",
                        priority = 1
                    )
                )
            }
        }

        // 4. 次弱维度：1 个辅助动作
        val secondary = weakDimensions.getOrNull(1)
        if (secondary != null && secondary != weakest) {
            DIMENSION_EXERCISES[secondary]?.firstOrNull()?.let { ex ->
                exercises.add(
                    RecommendedExercise(
                        exercise = ex,
                        dimension = secondary,
                        reason = "辅助提升【$secondary】维度",
                        priority = 2
                    )
                )
            }
        }

        // 5. 整体偏弱时补充核心
        val avgScore = radar.toList().average()
        if (avgScore < 60.0) {
            exercises.add(
                RecommendedExercise(
                    exercise = Exercise("平板支撑", 3, "60秒", "核心收紧，臀部不下塌"),
                    dimension = "力量",
                    reason = "整体基础偏弱，强化核心力量",
                    priority = 2
                )
            )
        }

        // 6. 放松
        COOLDOWN_EXERCISES.forEach { ex ->
            exercises.add(RecommendedExercise(ex, "放松", "训练后放松，促进恢复", 0))
        }

        val summary = buildSummary(studentName, weakDimensions, avgScore)

        return TrainingPlan(
            studentName = studentName,
            radar = radar,
            weakDimensions = weakDimensions,
            exercises = exercises,
            summary = summary,
            createdAt = nowFormatted()
        )
    }

    /** 从 AbilityRadar 取出指定维度的得分 */
    private fun radarScore(radar: AbilityRadar, dim: String): Float = when (dim) {
        "速度" -> radar.speed
        "力量" -> radar.strength
        "耐力" -> radar.endurance
        "柔韧" -> radar.flexibility
        "灵敏" -> radar.agility
        else -> 0f
    }

    /** 生成训练计划摘要文字 */
    private fun buildSummary(studentName: String, weakDimensions: List<String>, avgScore: Double): String {
        val weakText = weakDimensions.joinToString("、")
        val level = when {
            avgScore < 60 -> "基础阶段"
            avgScore < 75 -> "进阶阶段"
            avgScore < 85 -> "巩固阶段"
            else -> "突破阶段"
        }
        return "学员【$studentName】当前整体水平：$level。" +
            "重点突破维度：$weakText。建议每周 2-3 次针对性训练，4 周后复测评估进展。"
    }

    /** 当前时间格式化 yyyy-MM-dd HH:mm */
    private fun nowFormatted(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }
}
