package com.shangmentiyu.sportscoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shangmentiyu.sportscoach.data.model.SignInRecord
import kotlinx.coroutines.flow.Flow

/**
 * === v32：签到记录 DAO ===
 *
 * 记录教练手动签到/签退的每次操作（操作人 + 时间戳），
 * 同时以 (studentName, lessonId, type) 唯一索引充当重复操作的数据库级防线。
 */
@Dao
interface SignInDao {

    /**
     * 插入一条签到/签退记录。
     * [OnConflictStrategy.IGNORE]：同一学员 + 同一课时 + 同一类型已存在时静默忽略，
     * 返回 -1（未插入），调用方据此判定为重复操作。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: SignInRecord): Long

    /** 某课时是否已有指定类型的记录（防重入口） */
    @Query("SELECT COUNT(*) FROM sign_in_records WHERE lessonId = :lessonId AND type = :type")
    suspend fun countByLessonAndType(lessonId: String, type: String): Int

    /** 某学员某课时的签到记录（签退时回填/审计） */
    @Query("SELECT * FROM sign_in_records WHERE studentName = :studentName AND lessonId = :lessonId AND type = :type LIMIT 1")
    suspend fun findByLessonAndType(studentName: String, lessonId: String, type: String): SignInRecord?

    /** 全量记录（审计/导出用） */
    @Query("SELECT * FROM sign_in_records ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SignInRecord>>
}
