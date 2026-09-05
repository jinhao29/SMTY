package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.CoachScheduleDao
import com.shangmentiyu.sportscoach.data.db.CoachWorkloadDao
import com.shangmentiyu.sportscoach.data.db.CoachWorkloadRow
import com.shangmentiyu.sportscoach.data.db.CoachDailyLoadRow
import com.shangmentiyu.sportscoach.data.model.CoachSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/**
 * 教练排班与工作量 Repository（管理层）。
 *
 * 职责：
 * - 可上课时段 CRUD（保存前同教练同周几时段重叠检测）
 * - 工作量统计（排课数 / 助教数 / 消课数 / 每日负荷）
 *
 * 说明：排课时的教练时间冲突检测（同一教练同时段两节课）
 * 已由 [ScheduleRepository.checkCoachConflict] 在排课保存流程中实现，此处不重复。
 */
class CoachScheduleRepository(
    private val coachScheduleDao: CoachScheduleDao,
    private val workloadDao: CoachWorkloadDao
) {

    fun getByCoach(coachName: String): Flow<List<CoachSchedule>> = coachScheduleDao.getByCoach(coachName)

    fun getAll(): Flow<List<CoachSchedule>> = coachScheduleDao.getAll()

    /**
     * 新增/更新可上课时段。
     *
     * @throws IllegalArgumentException 同教练同周几时间段与既有时段重叠时抛出（用户可读文案）
     */
    suspend fun upsert(schedule: CoachSchedule): Long {
        validateTimeFormat(schedule)
        val existing = coachScheduleDao.getByCoach(schedule.coachName)
            .firstOrNullList()
            .filter { it.id != schedule.id && it.dayOfWeek == schedule.dayOfWeek }
        existing.firstOrNull { overlaps(it, schedule) }?.let { conflict ->
            throw IllegalArgumentException(
                "时段冲突：${dayName(schedule.dayOfWeek)} ${conflict.startTime}-${conflict.endTime} 已排过"
            )
        }
        return coachScheduleDao.upsert(schedule)
    }

    suspend fun delete(id: Long) = coachScheduleDao.deleteById(id)

    /** 各教练当前活跃排课数（主讲口径，实时 Flow） */
    fun scheduledCountByCoach(): Flow<List<CoachWorkloadRow>> = workloadDao.scheduledCountByCoach()

    /** 各教练助教排课数（实时 Flow） */
    fun assistCountByCoach(): Flow<List<CoachWorkloadRow>> = workloadDao.assistCountByCoach()

    /** 结算期各教练实际消课数（已签退，一次性查询） */
    suspend fun consumedCountByCoach(start: String, end: String): List<CoachWorkloadRow> =
        workloadDao.consumedCountByCoach(start, end)

    /** 日期区间内教练每日课时数（负荷预警用，含未签退） */
    suspend fun dailyLoad(start: String, end: String): List<CoachDailyLoadRow> =
        workloadDao.dailyLoad(start, end)

    // ---------- 内部工具 ----------

    private suspend fun <T> Flow<List<T>>.firstOrNullList(): List<T> =
        this.firstOrNull() ?: emptyList()

    private fun validateTimeFormat(schedule: CoachSchedule) {
        val start = toMinutes(schedule.startTime)
        val end = toMinutes(schedule.endTime)
        if (start == null || end == null) throw IllegalArgumentException("时间格式应为 HH:mm")
        if (start >= end) throw IllegalArgumentException("结束时间必须晚于开始时间")
    }

    /** 两个时段是否重叠（含边界相接，如 09:00-10:00 与 10:00-11:00 视为不重叠） */
    private fun overlaps(a: CoachSchedule, b: CoachSchedule): Boolean {
        val aStart = toMinutes(a.startTime) ?: return false
        val aEnd = toMinutes(a.endTime) ?: return false
        val bStart = toMinutes(b.startTime) ?: return false
        val bEnd = toMinutes(b.endTime) ?: return false
        return aStart < bEnd && bStart < aEnd
    }

    private fun toMinutes(hhmm: String): Int? {
        val parts = hhmm.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val m = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return h * 60 + m
    }

    companion object {
        fun dayName(dayOfWeek: Int): String =
            listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
                .getOrElse(dayOfWeek) { "周$dayOfWeek" }
    }
}
