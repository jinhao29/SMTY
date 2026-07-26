package com.shangmentiyu.sportscoach.data.model

import androidx.compose.runtime.Stable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 饮食模板实体（3+2 饮食法）。
 *
 * 存储全天 5 餐（早 / 上午加餐 / 午 / 下午加餐 / 晚）的搭配模板。
 * 应用启动时由 [com.shangmentiyu.sportscoach.data.db.AppDatabase] 预置 3 套模板：
 * - 模板一：常规健康发育型
 * - 模板二：高强度体能训练型
 * - 模板三：减脂 / 控制体重型
 *
 * 餐次内容采用 JSON 字符串存储，结构：[MealItem] 序列化后 JSON。
 * 这样新增 / 调整餐次内容无需改库结构，扩展性好。
 *
 * 性能：v26 优化2添加 [Stable] 注解，作为 Compose State 使用时避免无效重组。
 */
@Stable
@Entity(tableName = "diet_templates")
data class DietTemplateEntity(
    @PrimaryKey val id: String,                  // 模板 ID：tpl_regular / tpl_training / tpl_fat_loss
    val name: String,                            // 模板名称
    val description: String,                     // 模板描述（适用人群）
    val breakfast: String,                       // 早餐 JSON：[MealItem]
    val morningSnack: String,                    // 上午加餐 JSON：[MealItem]
    val lunch: String,                           // 午餐 JSON：[MealItem]
    val afternoonSnack: String,                  // 下午加餐 JSON：[MealItem]
    val dinner: String,                          // 晚餐 JSON：[MealItem]
    val preWorkoutTip: String = "",              // 训练前 1-2 小时黄金饮食提示
    val postWorkoutTip: String = "",             // 训练后 30 分钟黄金饮食提示
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 学员饮食绑定记录实体。
 *
 * 一个学员同一时间只绑定一个模板（最新绑定覆盖旧绑定）。
 * 教练可在模板基础上为每个餐次：
 * - 覆写食材内容（[breakfastMeals] 等自定义字段，空串时回退到模板默认）
 * - 添加备注（[breakfastNote] 等，如"该学员对牛奶过敏，更换为豆浆"）
 *
 * 性能：v26 优化2添加 [Stable] 注解，作为 Compose State 使用时避免无效重组。
 */
@Stable
@Entity(tableName = "student_diet_records")
data class StudentDietRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentName: String,                     // 学员姓名（软关联，保留用于显示）
    val studentId: String? = null,               // 学员唯一ID（软关联外键，v20 引入，旧数据 NULL）
    val templateId: String,                      // 绑定的模板 ID
    val templateName: String,                    // 模板名称快照（便于历史查看）
    val breakfastNote: String = "",               // 早餐教练备注
    val morningSnackNote: String = "",            // 上午加餐教练备注
    val lunchNote: String = "",                   // 午餐教练备注
    val afternoonSnackNote: String = "",          // 下午加餐教练备注
    val dinnerNote: String = "",                  // 晚餐教练备注
    // === 自定义餐次食材内容（v19 新增，空串表示使用模板默认） ===
    val breakfastMeals: String = "",              // 早餐自定义食材 JSON（[MealItem] 序列化）
    val morningSnackMeals: String = "",           // 上午加餐自定义食材 JSON
    val lunchMeals: String = "",                  // 午餐自定义食材 JSON
    val afternoonSnackMeals: String = "",         // 下午加餐自定义食材 JSON
    val dinnerMeals: String = "",                 // 晚餐自定义食材 JSON
    val appliedAt: Long = System.currentTimeMillis()
)

/**
 * 单餐次条目（用于 JSON 序列化）。
 *
 * 一个餐次可包含多个 [MealItem]，如早餐 = 主食 + 优质蛋白 + 蔬果。
 *
 * 性能：v26 优化2添加 [Stable] 注解，作为 Compose State 使用时避免无效重组。
 */
@Stable
data class MealItem(
    val category: String = "",                   // 类别：主食 / 优质蛋白 / 蔬果 / 能量补充 / 高蛋白肉类 / 绿叶蔬菜 等
    val content: String = ""                     // 具体内容：如"全麦面包 2 片"
)
