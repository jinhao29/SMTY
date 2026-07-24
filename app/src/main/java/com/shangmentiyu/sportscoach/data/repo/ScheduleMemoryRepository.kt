package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.ScheduleMemoryDao
import com.shangmentiyu.sportscoach.data.model.ScheduleMemory
import kotlinx.coroutines.flow.Flow

/**
 * 排课记忆 Repository：封装教练历史时间/地点的读写逻辑。
 *
 * 字段约定：field = "time" 表示上课时间，field = "location" 表示上课地点。
 */
class ScheduleMemoryRepository(private val dao: ScheduleMemoryDao) {

    /** 查询指定教练的历史值列表 */
    fun getMemories(coachName: String, field: String): Flow<List<ScheduleMemory>> =
        dao.getMemories(coachName, field)

    /** 查询全局最近历史值（不限教练） */
    fun getRecentMemories(field: String, limit: Int = 10): Flow<List<ScheduleMemory>> =
        dao.getRecentMemories(field, limit)

    /** 保存一条记忆（重复则更新 updatedAt） */
    suspend fun saveMemory(coachName: String, field: String, value: String) {
        if (value.isBlank()) return
        dao.saveMemory(ScheduleMemory(coachName = coachName, field = field, value = value))
    }

    /** 删除一条记忆 */
    suspend fun deleteMemory(coachName: String, field: String, value: String) {
        dao.deleteMemory(coachName, field, value)
    }
}
