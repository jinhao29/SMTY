package com.shangmentiyu.sportscoach.core

/** 动作数据 */
data class Exercise(
    val name: String,
    val sets: Int,
    val reps: String,
    val note: String
)

/** 模板中的动作引用 */
data class TemplateRef(
    val category: String,
    val exerciseName: String
)

object TemplateData {
    // 动作库（按分类）
    private val EXERCISES: Map<String, List<Exercise>> = mapOf(
        "热身" to listOf(
            Exercise("慢跑", 1, "5分钟", "保持匀速，逐步提升心率"),
            Exercise("高抬腿", 3, "30秒", "膝盖抬至腰部，前脚掌着地"),
            Exercise("开合跳", 3, "20次", "手脚协调，落地屈膝缓冲"),
            Exercise("动态拉伸", 1, "5分钟", "全身大肌群激活"),
        ),
        "力量" to listOf(
            Exercise("俯卧撑", 3, "15次", "身体成直线，肘部夹紧"),
            Exercise("深蹲", 4, "20次", "膝盖不过脚尖，臀部后坐"),
            Exercise("平板支撑", 3, "60秒", "核心收紧，臀部不下塌"),
            Exercise("仰卧起坐", 4, "20次", "起身呼气，下落缓慢"),
            Exercise("弓步蹲", 3, "12次/侧", "前膝90度，后腿伸直"),
        ),
        "速度" to listOf(
            Exercise("30米冲刺", 6, "30米", "起跑反应快，全程加速"),
            Exercise("50米冲刺", 4, "50米", "前倾起跑，保持步频"),
            Exercise("折返跑", 4, "4趟", "转身降重心，触线变向"),
            Exercise("高抬腿跑", 4, "20米", "膝盖高抬，前脚掌着地"),
        ),
        "柔韧" to listOf(
            Exercise("坐位体前屈", 3, "30秒", "直腿，缓慢前屈不弹震"),
            Exercise("站位体前屈", 3, "30秒", "膝盖伸直，手指触地"),
            Exercise("肩部拉伸", 2, "20秒/侧", "手臂横胸前，对侧压肘"),
            Exercise("股四头肌拉伸", 2, "20秒/侧", "单脚站立，脚跟贴臀"),
        ),
        "核心" to listOf(
            Exercise("俄罗斯转体", 3, "20次", "双脚抬离，躯干旋转"),
            Exercise("死虫式", 3, "12次/侧", "下背贴地，对侧伸展"),
            Exercise("侧平板支撑", 2, "30秒/侧", "身体成直线，髋不上塌"),
            Exercise("登山跑", 3, "30秒", "交替提膝，核心收紧"),
        ),
        "拉伸放松" to listOf(
            Exercise("全身拉伸", 1, "5分钟", "每个动作保持20秒"),
            Exercise("小腿拉伸", 2, "30秒/侧", "弓步推墙，后腿伸直"),
            Exercise("背部拉伸", 2, "30秒", "婴儿式，臀部坐脚跟"),
            Exercise("呼吸放松", 1, "2分钟", "深呼吸，缓慢呼气"),
        ),
    )

    // 课堂模板
    private val TEMPLATES: Map<String, List<TemplateRef>> = mapOf(
        "体能基础" to listOf(
            TemplateRef("热身", "慢跑"), TemplateRef("热身", "开合跳"),
            TemplateRef("力量", "深蹲"), TemplateRef("力量", "俯卧撑"),
            TemplateRef("核心", "平板支撑"),
            TemplateRef("拉伸放松", "全身拉伸"),
        ),
        "速度冲刺" to listOf(
            TemplateRef("热身", "高抬腿"), TemplateRef("热身", "动态拉伸"),
            TemplateRef("速度", "30米冲刺"), TemplateRef("速度", "折返跑"),
            TemplateRef("力量", "弓步蹲"),
            TemplateRef("拉伸放松", "股四头肌拉伸"),
        ),
        "柔韧提升" to listOf(
            TemplateRef("热身", "慢跑"),
            TemplateRef("柔韧", "坐位体前屈"), TemplateRef("柔韧", "站位体前屈"),
            TemplateRef("柔韧", "肩部拉伸"), TemplateRef("柔韧", "股四头肌拉伸"),
            TemplateRef("拉伸放松", "全身拉伸"),
        ),
        "核心力量" to listOf(
            TemplateRef("热身", "开合跳"),
            TemplateRef("核心", "俄罗斯转体"), TemplateRef("核心", "死虫式"),
            TemplateRef("核心", "侧平板支撑"), TemplateRef("核心", "登山跑"),
            TemplateRef("拉伸放松", "背部拉伸"),
        ),
        "中考专项" to listOf(
            TemplateRef("热身", "慢跑"), TemplateRef("热身", "动态拉伸"),
            TemplateRef("速度", "50米冲刺"), TemplateRef("力量", "仰卧起坐"),
            TemplateRef("柔韧", "坐位体前屈"),
            TemplateRef("拉伸放松", "全身拉伸"),
        ),
    )

    fun listCategories(): List<String> = EXERCISES.keys.toList()
    fun listExercises(category: String): List<Exercise> = EXERCISES[category] ?: emptyList()
    fun listTemplates(): List<String> = TEMPLATES.keys.toList()

    /** 获取模板动作列表，返回 (分类, Exercise) 对 */
    fun getTemplate(name: String): List<Pair<String, Exercise>> {
        val refs = TEMPLATES[name] ?: return emptyList()
        val result = mutableListOf<Pair<String, Exercise>>()
        for (ref in refs) {
            val exercise = EXERCISES[ref.category]?.find { it.name == ref.exerciseName }
            if (exercise != null) result.add(ref.category to exercise)
        }
        return result
    }
}
