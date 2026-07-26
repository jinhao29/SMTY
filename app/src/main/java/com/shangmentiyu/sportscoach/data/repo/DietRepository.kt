package com.shangmentiyu.sportscoach.data.repo

import android.util.Log
import com.shangmentiyu.sportscoach.data.db.DietDao
import com.shangmentiyu.sportscoach.data.model.DietTemplateEntity
import com.shangmentiyu.sportscoach.data.model.MealItem
import com.shangmentiyu.sportscoach.data.model.StudentDietRecord
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

/**
 * 饮食管理仓储：封装模板查询、学员绑定、备注更新的 IO 操作。
 *
 * - 模板数据在 [com.shangmentiyu.sportscoach.data.db.AppDatabase] 初始化时预置，
 *   此处仅提供查询接口，不再写入。
 * - 学员绑定记录支持一人一方案，新绑定覆盖旧绑定。
 * - 餐次内容 JSON 解析在此处统一完成，UI 层直接拿 [List<MealItem>]。
 */
class DietRepository(private val dao: DietDao) {

    /** 预置模板 ID（与 AppDatabase 初始化一致） */
    object TemplateIds {
        const val REGULAR = "tpl_regular"        // 常规健康发育型
        const val TRAINING = "tpl_training"      // 高强度体能训练型
        const val FAT_LOSS = "tpl_fat_loss"      // 减脂 / 控制体重型
    }

    suspend fun getAllTemplates(): List<DietTemplateEntity> = dao.getAllTemplates()

    suspend fun getTemplateById(id: String): DietTemplateEntity? = dao.getTemplateById(id)

    fun getStudentRecordFlow(studentName: String): Flow<StudentDietRecord?> =
        dao.getLatestRecordFlow(studentName)

    suspend fun getStudentRecord(studentName: String): StudentDietRecord? =
        dao.getLatestRecord(studentName)

    /**
     * 应用模板给学员（覆盖旧绑定）。
     *
     * @param studentName 学员姓名
     * @param templateId  模板 ID
     * @param templateName 模板名称快照
     * @param notes       5 餐备注，顺序：早 / 上午加餐 / 午 / 下午加餐 / 晚
     * @param meals       5 餐自定义食材内容（空串表示用模板默认），顺序同上
     */
    suspend fun applyTemplate(
        studentName: String,
        templateId: String,
        templateName: String,
        notes: DietNotes,
        meals: DietMeals = DietMeals()
    ) {
        dao.deleteRecordsByStudent(studentName)
        dao.insertRecord(
            StudentDietRecord(
                studentName = studentName,
                templateId = templateId,
                templateName = templateName,
                breakfastNote = notes.breakfast,
                morningSnackNote = notes.morningSnack,
                lunchNote = notes.lunch,
                afternoonSnackNote = notes.afternoonSnack,
                dinnerNote = notes.dinner,
                breakfastMeals = meals.breakfast,
                morningSnackMeals = meals.morningSnack,
                lunchMeals = meals.lunch,
                afternoonSnackMeals = meals.afternoonSnack,
                dinnerMeals = meals.dinner
            )
        )
    }

    /**
     * 仅更新学员的 5 餐备注与自定义食材（不切换模板）。
     */
    suspend fun updateNotes(studentName: String, notes: DietNotes, meals: DietMeals = DietMeals()) {
        val existing = dao.getLatestRecord(studentName) ?: return
        dao.updateRecord(
            existing.copy(
                breakfastNote = notes.breakfast,
                morningSnackNote = notes.morningSnack,
                lunchNote = notes.lunch,
                afternoonSnackNote = notes.afternoonSnack,
                dinnerNote = notes.dinner,
                breakfastMeals = meals.breakfast,
                morningSnackMeals = meals.morningSnack,
                lunchMeals = meals.lunch,
                afternoonSnackMeals = meals.afternoonSnack,
                dinnerMeals = meals.dinner
            )
        )
    }

    suspend fun deleteByStudentName(studentName: String) = dao.deleteByStudentName(studentName)

    /**
     * 解析餐次 JSON 为 [MealItem] 列表。
     *
     * 容错策略：JSON 异常时返回空列表，不阻断 UI 渲染。
     */
    fun parseMeals(json: String): List<MealItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                MealItem(
                    category = obj.optString("category", ""),
                    content = obj.optString("content", "")
                )
            }
        } catch (e: Exception) {
            Log.w("DietRepository", "parseMeals failed: ${e.message}")
            emptyList()
        }
    }

    /** 5 餐备注打包类 */
    data class DietNotes(
        val breakfast: String = "",
        val morningSnack: String = "",
        val lunch: String = "",
        val afternoonSnack: String = "",
        val dinner: String = ""
    )

    /** 5 餐自定义食材内容打包类（空串表示使用模板默认） */
    data class DietMeals(
        val breakfast: String = "",
        val morningSnack: String = "",
        val lunch: String = "",
        val afternoonSnack: String = "",
        val dinner: String = ""
    )

    /**
     * 将 [MealItem] 列表序列化为 JSON 字符串（用于保存教练自定义食材内容）。
     *
     * 与 [parseMeals] 互逆，确保存取格式一致。
     */
    fun serializeMeals(items: List<MealItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(org.json.JSONObject().apply {
                put("category", item.category)
                put("content", item.content)
            })
        }
        return arr.toString()
    }
}
