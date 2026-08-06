package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.TrainingCycleDao
import com.shangmentiyu.sportscoach.data.model.TrainingCycle
import com.shangmentiyu.sportscoach.data.model.WeeklyPlan
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 训练周期 Repository（管理层）。
 *
 * 从原 [OperationRepository] 拆分而来，单一职责只管理训练周期数据。
 *
 * 职责：
 * - 训练周期 CRUD
 * - 创建周期并自动生成空的周计划列表（[createCycle]）
 *
 * 设计说明：
 * - 周期结束日期计算基于 [LocalDate] 不可变对象，线程安全，无 Calendar 状态污染
 */
class TrainingCycleRepository(
    private val cycleDao: TrainingCycleDao
) {

    // === 训练周期 ===
    fun getAllCycles(): Flow<List<TrainingCycle>> = cycleDao.getAll()
    fun getActiveCycles(): Flow<List<TrainingCycle>> = cycleDao.getActive()
    fun getCyclesByStudent(name: String): Flow<List<TrainingCycle>> = cycleDao.getByStudent(name)
    suspend fun getCycleById(id: String): TrainingCycle? = cycleDao.getById(id)
    suspend fun addCycle(cycle: TrainingCycle) = cycleDao.insert(cycle)
    suspend fun updateCycle(cycle: TrainingCycle) = cycleDao.update(cycle)
    suspend fun deleteCycle(id: String) = cycleDao.deleteById(id)

    /**
     * 创建周期并自动生成空的周计划列表。
     */
    suspend fun createCycle(
        studentName: String,
        name: String,
        goal: String,
        totalWeeks: Int,
        startDate: String
    ): String {
        val cycle = TrainingCycle(
            studentName = studentName,
            name = name,
            goal = goal,
            totalWeeks = totalWeeks,
            startDate = startDate,
            endDate = calcEndDate(startDate, totalWeeks)
        ).withWeeklyPlans(emptyWeeklyPlans(totalWeeks))
        cycleDao.insert(cycle)
        return cycle.id
    }

    /** 生成空的周计划列表 */
    private fun emptyWeeklyPlans(totalWeeks: Int): List<WeeklyPlan> =
        (1..totalWeeks).map { i ->
            WeeklyPlan(
                weekIndex = i,
                title = "第$i 周",
                goal = "",
                focus = "",
                exercisesJson = "[]"
            )
        }

    /** 计算周期结束日期（线程安全：基于 [LocalDate] 不可变对象，无 Calendar 状态污染） */
    private fun calcEndDate(startDate: String, weeks: Int): String {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
            val start = LocalDate.parse(startDate, formatter)
            // +weeks 周 -1 天 = 周期最后一天
            val end = start.plusWeeks(weeks.toLong()).minusDays(1)
            end.format(formatter)
        } catch (_: Exception) { "" }
    }
}
