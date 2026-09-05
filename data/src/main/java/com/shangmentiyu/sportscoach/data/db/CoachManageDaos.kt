package com.shangmentiyu.sportscoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shangmentiyu.sportscoach.data.model.CoachPayout
import com.shangmentiyu.sportscoach.data.model.CoachPayoutRequest
import com.shangmentiyu.sportscoach.data.model.CoachSchedule
import com.shangmentiyu.sportscoach.data.model.CoachStudentBinding
import kotlinx.coroutines.flow.Flow

/** 教练可上课时段 DAO（v33 教练管理） */
@Dao
interface CoachScheduleDao {

    @Query("SELECT * FROM coach_schedules WHERE coachName = :coachName ORDER BY dayOfWeek, startTime")
    fun getByCoach(coachName: String): Flow<List<CoachSchedule>>

    @Query("SELECT * FROM coach_schedules ORDER BY coachName, dayOfWeek, startTime")
    fun getAll(): Flow<List<CoachSchedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: CoachSchedule): Long

    @Query("DELETE FROM coach_schedules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** 教练薪资结算 DAO（v33 教练管理） */
@Dao
interface CoachPayoutDao {

    @Query("SELECT * FROM coach_payouts ORDER BY periodStart DESC, coachName")
    fun getAll(): Flow<List<CoachPayout>>

    @Query("SELECT * FROM coach_payouts WHERE periodStart = :start AND periodEnd = :end ORDER BY coachName")
    fun getByPeriod(start: String, end: String): Flow<List<CoachPayout>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payout: CoachPayout): Long

    @Query("UPDATE coach_payouts SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM coach_payouts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** 教练提现申请 DAO（v33 教练管理） */
@Dao
interface CoachPayoutRequestDao {

    @Query("SELECT * FROM coach_payout_requests ORDER BY appliedAt DESC")
    fun getAll(): Flow<List<CoachPayoutRequest>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: CoachPayoutRequest): Long

    @Query("UPDATE coach_payout_requests SET status = :status, processedAt = :processedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, processedAt: Long)
}

/** 教练工作量统计行（聚合查询投影） */
data class CoachWorkloadRow(
    val name: String,
    val count: Int
)

/** 教练每日课时行（负荷预警聚合投影） */
data class CoachDailyLoadRow(
    val coach: String,
    val date: String,
    val count: Int
)

/** 教练工作量统计 DAO（v33：跨表聚合 schedules / lessons，仅服务教练管理模块） */
@Dao
interface CoachWorkloadDao {

    /** 各教练当前活跃排课数（主讲口径） */
    @Query(
        "SELECT coachName AS name, COUNT(*) AS count FROM schedules " +
            "WHERE isActive = 1 AND coachName != '' GROUP BY coachName"
    )
    fun scheduledCountByCoach(): Flow<List<CoachWorkloadRow>>

    /** 各教练作为助教的活跃排课数 */
    @Query(
        "SELECT assistantCoach AS name, COUNT(*) AS count FROM schedules " +
            "WHERE isActive = 1 AND assistantCoach != '' GROUP BY assistantCoach"
    )
    fun assistCountByCoach(): Flow<List<CoachWorkloadRow>>

    /** 结算期内各教练实际消课数（已签退才计费） */
    @Query(
        "SELECT coach AS name, COUNT(*) AS count FROM lessons " +
            "WHERE date BETWEEN :start AND :end AND coach != '' AND signOutTime != '' " +
            "GROUP BY coach"
    )
    suspend fun consumedCountByCoach(start: String, end: String): List<CoachWorkloadRow>

    /** 结算期内各教练全部课时记录数（含未签退，负荷口径） */
    @Query(
        "SELECT coach AS name, COUNT(*) AS count FROM lessons " +
            "WHERE date BETWEEN :start AND :end AND coach != '' GROUP BY coach"
    )
    suspend fun lessonCountByCoach(start: String, end: String): List<CoachWorkloadRow>

    /** 日期区间内教练每日课时数（含助教口径，负荷预警用） */
    @Query(
        "SELECT coach AS coach, date AS date, COUNT(*) AS count FROM lessons " +
            "WHERE date BETWEEN :start AND :end AND coach != '' GROUP BY coach, date"
    )
    suspend fun dailyLoad(start: String, end: String): List<CoachDailyLoadRow>
}

/** 教练-学员绑定 DAO（v34 教练绑定学员） */
@Dao
interface CoachStudentBindingDao {

    @Query("SELECT * FROM coach_student_bindings WHERE coachName = :coachName ORDER BY studentName")
    fun getByCoach(coachName: String): Flow<List<CoachStudentBinding>>

    @Query("SELECT * FROM coach_student_bindings ORDER BY coachName, studentName")
    fun getAll(): Flow<List<CoachStudentBinding>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(binding: CoachStudentBinding)

    @Query("DELETE FROM coach_student_bindings WHERE coachName = :coachName AND studentName = :studentName")
    suspend fun delete(coachName: String, studentName: String)

    /** 删除教练时级联清理其全部绑定 */
    @Query("DELETE FROM coach_student_bindings WHERE coachName = :coachName")
    suspend fun deleteByCoach(coachName: String)
}
