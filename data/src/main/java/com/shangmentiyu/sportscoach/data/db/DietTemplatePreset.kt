package com.shangmentiyu.sportscoach.data.db

import com.shangmentiyu.sportscoach.data.model.DietTemplateEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * 饮食模板预置数据（3+2 饮食法）。
 *
 * 纯本地硬编码，无任何网络请求。在 [AppDatabase] 初始化时插入数据库。
 *
 * 三套模板覆盖三类典型学员：
 * - [REGULAR]：常规健康发育型（适合大多数学员）
 * - [TRAINING]：高强度体能训练型（适合训练量大的学员，加碳水与蛋白）
 * - [FAT_LOSS]：减脂 / 控制体重型（适合体重超标学员，控碳水加蛋白）
 *
 * JSON 格式由本文件统一生成，避免手写 JSON 字符串出错。
 */
object DietTemplatePreset {

    /** 模板一：常规健康发育型 */
    fun regular(): DietTemplateEntity = DietTemplateEntity(
        id = "tpl_regular",
        name = "常规健康发育型",
        description = "适合大多数学员，营养均衡，满足日常发育所需",
        breakfast = mealJson(
            "主食" to "全麦面包 2 片 / 玉米 1 根",
            "优质蛋白" to "水煮蛋 1 个 + 牛奶 250ml",
            "蔬果" to "苹果半个 / 小番茄 5 颗"
        ),
        morningSnack = mealJson(
            "能量补充" to "香蕉 1 根 / 酸奶 1 杯 / 坚果一小把（约 15g）"
        ),
        lunch = mealJson(
            "主食" to "糙米饭 1 碗（约 150g）",
            "高蛋白肉类" to "去皮鸡胸肉 100g / 清蒸鱼 100g / 牛肉 80g",
            "蔬菜" to "西兰花 + 胡萝卜 + 时令蔬菜（约 200g）"
        ),
        afternoonSnack = mealJson(
            "运动前 / 后补充" to "全麦饼干 3 片 / 苹果 1 个 / 蛋白饮品 1 杯"
        ),
        dinner = mealJson(
            "粗粮碳水" to "红薯 1 个 / 杂粮粥 1 碗",
            "易消化蛋白" to "豆腐 100g / 清蒸鱼 80g",
            "绿叶蔬菜" to "菠菜 / 油菜（约 150g）"
        ),
        preWorkoutTip = "训练前 1-2 小时：香蕉 1 根 + 全麦面包 1 片，提供持续能量",
        postWorkoutTip = "训练后 30 分钟：牛奶 250ml + 鸡蛋 1 个，黄金蛋白补充窗口"
    )

    /** 模板二：高强度体能训练型 */
    fun training(): DietTemplateEntity = DietTemplateEntity(
        id = "tpl_training",
        name = "高强度体能训练型",
        description = "适合训练量大的学员，适当增加碳水与蛋白质摄入",
        breakfast = mealJson(
            "主食" to "全麦面包 3 片 / 燕麦粥 1 大碗",
            "优质蛋白" to "水煮蛋 2 个 + 牛奶 300ml",
            "蔬果" to "香蕉 1 根 + 蓝莓一小把"
        ),
        morningSnack = mealJson(
            "能量补充" to "全麦三明治半个 + 坚果 20g + 酸奶 1 杯"
        ),
        lunch = mealJson(
            "主食" to "糙米饭 1.5 碗（约 220g）",
            "高蛋白肉类" to "瘦牛肉 150g / 鸡胸肉 150g / 三文鱼 120g",
            "蔬菜" to "西兰花 + 甜椒 + 蘑菇（约 250g）"
        ),
        afternoonSnack = mealJson(
            "运动前 / 后补充" to "蛋白饮 1 杯 + 香蕉 1 根 + 全麦面包 1 片"
        ),
        dinner = mealJson(
            "粗粮碳水" to "红薯 1.5 个 / 杂粮饭 1 碗",
            "易消化蛋白" to "清蒸鱼 120g + 豆腐 100g",
            "绿叶蔬菜" to "菠菜 / 油菜（约 200g）"
        ),
        preWorkoutTip = "训练前 1.5 小时：燕麦粥 + 鸡蛋 1 个，碳水充足保训练强度",
        postWorkoutTip = "训练后 30 分钟：蛋白饮 1 杯 + 香蕉 1 根，快速补糖补蛋白促恢复"
    )

    /** 模板三：减脂 / 控制体重型 */
    fun fatLoss(): DietTemplateEntity = DietTemplateEntity(
        id = "tpl_fat_loss",
        name = "减脂 / 控制体重型",
        description = "适合体重超标学员，控制碳水总量，提高蛋白与蔬菜比例",
        breakfast = mealJson(
            "主食" to "全麦面包 1 片 / 玉米半根",
            "优质蛋白" to "水煮蛋 1 个 + 无糖豆浆 250ml",
            "蔬果" to "黄瓜 1 根 / 小番茄 6 颗"
        ),
        morningSnack = mealJson(
            "能量补充" to "无糖酸奶 1 小杯 + 坚果 10g"
        ),
        lunch = mealJson(
            "主食" to "糙米饭半碗（约 80g）",
            "高蛋白肉类" to "去皮鸡胸肉 120g / 清蒸鱼 100g",
            "蔬菜" to "西兰花 + 黄瓜 + 时令蔬菜（约 300g，多蔬菜少主食）"
        ),
        afternoonSnack = mealJson(
            "运动前 / 后补充" to "蛋白饮 1 杯 + 苹果半个"
        ),
        dinner = mealJson(
            "粗粮碳水" to "红薯半个 / 杂粮粥半碗",
            "易消化蛋白" to "清蒸鱼 100g + 豆腐 80g",
            "绿叶蔬菜" to "菠菜 / 油菜（约 200g）"
        ),
        preWorkoutTip = "训练前 1 小时：苹果半个 + 鸡蛋 1 个，轻负担能量补充",
        postWorkoutTip = "训练后 30 分钟：蛋白饮 1 杯 + 鸡蛋 1 个，控糖保蛋白"
    )

    /** 全部预置模板 */
    fun all(): List<DietTemplateEntity> = listOf(regular(), training(), fatLoss())

    /**
     * 工具：把可变参数的 category/content 对组装成 JSON 字符串。
     *
     * 使用 [Pair] 简化调用，避免手写 JSON 出错。
     */
    private fun mealJson(vararg items: Pair<String, String>): String {
        val arr = JSONArray()
        items.forEach { (category, content) ->
            arr.put(JSONObject().apply {
                put("category", category)
                put("content", content)
            })
        }
        return arr.toString()
    }
}
