package com.shangmentiyu.sportscoach.core

import com.shangmentiyu.sportscoach.data.model.ExerciseItem

/**
 * === v28 优化3：智能训练内容推荐器（纯逻辑层） ===
 *
 * 业务背景：
 * - 教练在"添加排课"时往往从空白开始填写训练内容，缺乏科学依据
 * - 本推荐器基于学员最近一次体测成绩，自动识别弱项（50米跑、BMI 等），
 *   生成一套"弱项纠正训练"默认文本供教练参考
 *
 * 数据来源：
 * - 学员最近一次体测成绩（由 [AbilityAnalyzer.extractScores] 解析 lessons.scores 字段）
 * - 学员最近一次身体形态记录的 BMI 值
 *
 * 推荐策略：
 * 1. 取最近一次成绩中等级为"及格"或"不及格"的项目（弱项）
 * 2. 按维度（速度/力量/耐力/柔韧/灵敏）匹配预设训练模板
 * 3. BMI ≥ 24（超重）时附加燃脂训练模板
 * 4. 按"速度 → 力量 → 耐力 → 柔韧 → 灵敏 → 燃脂"优先级返回最多 6 项
 *
 * 设计原则：
 * - 纯函数对象（无状态），便于单元测试
 * - 推荐结果仅作为"默认起始模板"，教练可自由修改/删除/新增
 * - 不依赖 Android Framework，便于未来扩展到桌面端
 */
object TrainingContentRecommender {

    /** BMI 超重阈值（kg/m²），≥ 该值视为超重并附加燃脂训练 */
    private const val BMI_OVERWEIGHT_THRESHOLD = 24.0f

    /** 单次推荐最多动作数，避免列表过长教练难以浏览 */
    private const val MAX_EXERCISES = 6

    /** 弱项等级集合：成绩等级为以下任一时视为弱项 */
    private val WEAK_GRADES = setOf("及格", "不及格")

    /**
     * 维度 → 推荐训练动作模板映射表。
     *
     * 设计说明：
     * - 每个维度预置 2 个动作，覆盖"基础+进阶"两档
     * - 动作参数（组数/次数/强度）参考中考体育训练常用方案
     * - 调用方可按需取首个或全部动作
     */
    private val DIMENSION_TEMPLATES: Map<String, List<ExerciseItem>> = mapOf(
        // 速度弱项（如 50 米跑成绩差）
        "速度" to listOf(
            ExerciseItem(name = "高抬腿跑", sets = 3, reps = "30秒", intensity = "中"),
            ExerciseItem(name = "30米冲刺跑", sets = 4, reps = "3组", intensity = "高")
        ),
        // 力量弱项（如立定跳远成绩差）
        "力量" to listOf(
            ExerciseItem(name = "深蹲跳", sets = 3, reps = "10次", intensity = "中"),
            ExerciseItem(name = "弓步走", sets = 3, reps = "20米", intensity = "中")
        ),
        // 耐力弱项（如 800/1000 米跑成绩差）
        "耐力" to listOf(
            ExerciseItem(name = "变速跑", sets = 1, reps = "400米", intensity = "中"),
            ExerciseItem(name = "匀速慢跑", sets = 1, reps = "800米", intensity = "低")
        ),
        // 柔韧弱项（如坐位体前屈成绩差）
        "柔韧" to listOf(
            ExerciseItem(name = "动态拉伸", sets = 2, reps = "15次", intensity = "低"),
            ExerciseItem(name = "静态体前屈", sets = 3, reps = "30秒", intensity = "低")
        ),
        // 灵敏弱项（如折返跑、球类成绩差）
        "灵敏" to listOf(
            ExerciseItem(name = "绳梯训练", sets = 3, reps = "5组", intensity = "中"),
            ExerciseItem(name = "10米折返跑", sets = 4, reps = "3组", intensity = "高")
        )
    )

    /** BMI 超重附加燃脂训练模板 */
    private val BMI_FAT_BURN_TEMPLATES: List<ExerciseItem> = listOf(
        ExerciseItem(name = "开合跳", sets = 4, reps = "30次", intensity = "中"),
        ExerciseItem(name = "波比跳", sets = 3, reps = "15次", intensity = "高"),
        ExerciseItem(name = "平板支撑", sets = 3, reps = "45秒", intensity = "中")
    )

    /**
     * 基于体测成绩与 BMI 生成推荐训练内容。
     *
     * 算法：
     * 1. 从 [scores] 中筛选最近一次成绩中等级为弱项的项目
     * 2. 通过 [AbilityAnalyzer] 的项目→维度映射查表，找到对应训练模板
     * 3. BMI ≥ 24 时附加燃脂训练模板
     * 4. 按"速度 → 力量 → 耐力 → 柔韧 → 灵敏 → 燃脂"优先级返回最多 6 项
     *
     * @param scores 学员全部体测成绩（按日期升序），可空
     * @param latestBmi 学员最近一次 BMI 值，0 表示无数据
     * @return 推荐的训练动作列表（最多 6 项，空数据返回空列表）
     */
    fun recommend(
        scores: List<AbilityAnalyzer.ScoreEntry>?,
        latestBmi: Float
    ): List<ExerciseItem> {
        val result = mutableListOf<ExerciseItem>()

        // 1. 分析体测弱项 → 推荐对应维度训练
        if (!scores.isNullOrEmpty()) {
            // 取最近一次成绩（按日期升序，最后一条即为最近）
            // 按 projectName 分组取最近一次成绩
            val latestByName = scores
                .groupBy { it.projectName }
                .mapValues { (_, entries) -> entries.last() }

            // 筛选弱项并按维度优先级排序
            val weakDimensions = latestByName.values
                .filter { it.grade in WEAK_GRADES }
                .mapNotNull { entry ->
                    AbilityAnalyzer.getDimensionByProject(entry.projectName)
                }
                .distinct()
                .sortedBy { dimensionPriority(it) }

            // 每个弱项维度取首个动作（避免一个维度塞太多动作）
            for (dim in weakDimensions) {
                val template = DIMENSION_TEMPLATES[dim] ?: continue
                if (template.isNotEmpty()) {
                    result.add(template.first())
                    if (result.size >= MAX_EXERCISES) return result.toList()
                }
            }
        }

        // 2. BMI 超重 → 附加燃脂训练（独立于体测成绩判定）
        if (latestBmi > 0f && latestBmi >= BMI_OVERWEIGHT_THRESHOLD) {
            for (item in BMI_FAT_BURN_TEMPLATES) {
                if (result.size >= MAX_EXERCISES) break
                // 避免重复添加同名动作
                if (result.none { it.name == item.name }) {
                    result.add(item)
                }
            }
        }

        return result.toList()
    }

    /**
     * 维度推荐优先级：速度 → 力量 → 耐力 → 柔韧 → 灵敏。
     *
     * 设计理由：
     * - 速度训练见效快、动作简单，优先推荐
     * - 力量是基础体能，影响其他维度，次优
     * - 耐力训练周期长，再次
     * - 柔韧训练频次高但单次短，靠后
     * - 灵敏训练对场地要求高，最后
     */
    private fun dimensionPriority(dimension: String): Int = when (dimension) {
        "速度" -> 1
        "力量" -> 2
        "耐力" -> 3
        "柔韧" -> 4
        "灵敏" -> 5
        else -> 99
    }
}
