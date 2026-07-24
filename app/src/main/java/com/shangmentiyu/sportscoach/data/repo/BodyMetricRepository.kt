package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.BodyMetricHistoryDao
import com.shangmentiyu.sportscoach.data.model.BodyMetricHistory
import com.shangmentiyu.sportscoach.data.model.Student
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 身体形态历史 Repository（管理层）。
 *
 * 管理学员身高/体重/BMI 的历史记录，
 * 同时协调更新 Student 表的当前身高/体重/BMI 字段。
 *
 * 注意：[SimpleDateFormat] 非线程安全，禁止作为成员变量持有；
 * 统一通过 [todayStr] 在方法内新建实例使用。
 */
class BodyMetricRepository(
    private val dao: BodyMetricHistoryDao,
    private val studentRepo: StudentRepository
) {
    /** 当前日期字符串（yyyy-MM-dd），每次调用新建 SimpleDateFormat 实例，避免并发污染 */
    private fun todayStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun getByStudent(name: String): Flow<List<BodyMetricHistory>> = dao.getByStudent(name)
    suspend fun getByStudentOnce(name: String): List<BodyMetricHistory> = dao.getByStudentOnce(name)

    suspend fun getByStudentInRange(name: String, start: String, end: String): List<BodyMetricHistory> =
        dao.getByStudentInRange(name, start, end)

    /**
     * 记录一次测量并同步更新 Student 表的当前身高/体重/BMI。
     * @return 新记录的 id
     */
    suspend fun record(
        studentName: String,
        date: String = todayStr(),
        heightCm: Int,
        weightKg: Float,
        note: String = ""
    ): String {
        val record = BodyMetricHistory(
            studentName = studentName,
            date = date,
            heightCm = heightCm,
            weightKg = weightKg,
            note = note
        )
        dao.insert(record)
        // 同步更新 Student 表的当前值
        val student = studentRepo.getByName(studentName)
        if (student != null) {
            val bmi = record.bmi
            val updated = student.copy(
                heightCm = heightCm,
                weightKg = weightKg,
                bmi = bmi,
                updatedAt = System.currentTimeMillis()
            )
            studentRepo.updateStudent(updated)
        }
        return record.id
    }

    suspend fun delete(id: String) = dao.deleteById(id)
    suspend fun deleteByStudent(name: String) = dao.deleteByStudent(name)

    /**
     * 计算两条记录之间的指标变化。
     */
    fun computeDelta(first: BodyMetricHistory, last: BodyMetricHistory): MetricDelta {
        return MetricDelta(
            heightDelta = last.heightCm - first.heightCm,
            weightDelta = last.weightKg - first.weightKg,
            bmiDelta = last.bmi - first.bmi,
            daysBetween = try {
                // 方法内新建 SimpleDateFormat，避免多协程并发解析时 Calendar 状态污染
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val d1 = sdf.parse(first.date)?.time ?: 0L
                val d2 = sdf.parse(last.date)?.time ?: 0L
                ((d2 - d1) / (24 * 3600 * 1000L)).toInt()
            } catch (_: Exception) { 0 }
        )
    }

    data class MetricDelta(
        val heightDelta: Int,
        val weightDelta: Float,
        val bmiDelta: Float,
        val daysBetween: Int
    )
}
