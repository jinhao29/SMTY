package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.BodyMetricHistoryDao
import com.shangmentiyu.sportscoach.data.model.BodyMetricHistory
import com.shangmentiyu.sportscoach.data.model.Student
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 身体形态历史 Repository（管理层）。
 *
 * 管理学员身高/体重/BMI 的历史记录，
 * 同时协调更新 Student 表的当前身高/体重/BMI 字段。
 *
 * 日期格式化统一使用 [DateTimeFormatter]（线程安全），无 [SimpleDateFormat] 的 Calendar 状态污染问题。
 */
class BodyMetricRepository(
    private val dao: BodyMetricHistoryDao,
    private val studentRepo: StudentRepository
) {
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

    /** 当前日期字符串（yyyy-MM-dd），基于 [LocalDate.now] 线程安全获取 */
    private fun todayStr(): String = LocalDate.now().format(dateFormatter)

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
     *
     * 天数差使用 [ChronoUnit.DAYS.between] 基于 [LocalDate] 计算，无时区/日历复用污染问题。
     */
    fun computeDelta(first: BodyMetricHistory, last: BodyMetricHistory): MetricDelta {
        return MetricDelta(
            heightDelta = last.heightCm - first.heightCm,
            weightDelta = last.weightKg - first.weightKg,
            bmiDelta = last.bmi - first.bmi,
            daysBetween = try {
                val d1 = LocalDate.parse(first.date, dateFormatter)
                val d2 = LocalDate.parse(last.date, dateFormatter)
                ChronoUnit.DAYS.between(d1, d2).toInt()
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
