package com.shangmentiyu.sportscoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shangmentiyu.sportscoach.data.model.ArchivedLesson
import kotlinx.coroutines.flow.Flow

/**
 * 归档课时 DAO（数据层）。
 *
 * 用于访问 [ArchivedLesson] 表（冷数据），与 [LessonDao]（热数据）物理隔离。
 *
 * 查询场景：
 * - 历史报表：按学员查询归档记录
 * - 数据完整性核对：按日期范围查询
 *
 * 注意：本表为冷数据表，不参与日常签到/排课/统计等高频查询路径，
 * 避免历史数据累积拖慢主表 [Lesson] 查询性能。
 */
@Dao
interface ArchivedLessonDao {

    /** 插入单条归档记录（REPLACE 策略：主键冲突时覆盖，保证幂等） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lesson: ArchivedLesson)

    /** 批量插入归档记录（事务内执行，性能优于循环单条插入） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lessons: List<ArchivedLesson>)

    /** 按学员查询全部归档记录（按日期降序、时间降序） */
    @Query("SELECT * FROM archived_lessons WHERE studentName = :name ORDER BY date DESC, time DESC")
    fun getByStudent(name: String): Flow<List<ArchivedLesson>>

    /** 按日期范围查询归档记录（用于历史报表） */
    @Query("SELECT * FROM archived_lessons WHERE date >= :fromDate AND date <= :toDate ORDER BY date DESC, time DESC")
    fun getByDateRange(fromDate: String, toDate: String): Flow<List<ArchivedLesson>>

    /** 一次性获取学员全部归档记录（非 Flow，用于报表导出等一次性场景） */
    @Query("SELECT * FROM archived_lessons WHERE studentName = :name ORDER BY date DESC, time DESC")
    suspend fun getByStudentOnce(name: String): List<ArchivedLesson>

    /** 按 ID 查询归档记录 */
    @Query("SELECT * FROM archived_lessons WHERE id = :id")
    suspend fun getById(id: String): ArchivedLesson?

    /** 按 ID 删除归档记录 */
    @Query("DELETE FROM archived_lessons WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 删除学员的所有归档记录（学员改名/物理删除时级联调用） */
    @Query("DELETE FROM archived_lessons WHERE studentName = :name")
    suspend fun deleteByStudent(name: String)

    /** 学员改名：级联更新 archived_lessons 表的 studentName 字段 */
    @Query("UPDATE archived_lessons SET studentName = :newName WHERE studentName = :oldName")
    suspend fun renameStudent(oldName: String, newName: String)

    /** 归档记录总数（用于诊断与统计） */
    @Query("SELECT COUNT(*) FROM archived_lessons")
    fun count(): Flow<Int>

    /**
     * 物理迁移：将指定日期之前的全部热数据 lessons 行直接插入到本归档表。
     *
     * 设计说明：
     * - 使用 INSERT...SELECT 在 SQLite 层一次性完成数据迁移，避免 Kotlin 层循环
     * - 字段一一对应，archivedAt 与 createdAt 由 SQL 表达式填充
     * - 仅 INSERT，不 DELETE：DELETE 由调用方在事务内显式调用 [LessonDao.deleteBefore]
     *   保证迁移与删除在同一事务内原子完成
     * - v28：迁移 status 字段（v24 起 lessons 表新增 status 字段，归档时必须同步迁移）
     *
     * @param date 边界日期 YYYY-MM-DD（严格小于该日期的记录将被迁移）
     */
    @Query(
        """
        INSERT INTO archived_lessons (
            id, date, time, studentName, studentId, content, scores, summary,
            duration, coach, location, lessonType, attendance, attitude,
            performance, nextGoal, coachComment, packageId, photoPath,
            signOutTime, signOutPhotoPath, contentImages, status, archivedAt, createdAt
        )
        SELECT
            id, date, time, studentName, studentId, content, scores, summary,
            duration, coach, location, lessonType, attendance, attitude,
            performance, nextGoal, coachComment, packageId, photoPath,
            signOutTime, signOutPhotoPath, contentImages, status,
            :archivedAt, createdAt
        FROM lessons
        WHERE date < :date
        """
    )
    suspend fun copyLessonsBeforeToDate(date: String, archivedAt: Long)

    /**
     * === v28：查询全部归档记录（非 Flow，用于"查看全部历史归档"列表展示） ===
     *
     * 按日期降序、时间降序排列，与 [LessonDao.getAll] 排序保持一致，
     * 便于在"查看全部历史归档"列表中复用相同的卡片组件。
     *
     * 数据量考虑：归档表通常为历史全量数据（可能数千条），但用户主动点击"查看全部"
     * 才查询，且归档表不参与日常高频路径，一次性加载可接受。
     * 若未来归档表超 5000 条，可改用 Paging 3 分页加载。
     *
     * @return 全部归档记录列表（按日期降序、时间降序）
     */
    @Query("SELECT * FROM archived_lessons ORDER BY date DESC, time DESC")
    suspend fun getAllOnce(): List<ArchivedLesson>

    /**
     * === v28：按学员查询归档记录（非 Flow，用于 PDF 报告生成） ===
     *
     * 用于学员成长 PDF 报告：获取该学员历史全部归档课时（含已归档的旧训练记录），
     * 用于计算最近 5 次训练成绩与历史统计。
     *
     * @param name 学员姓名
     * @return 该学员的全部归档课时（按日期升序）
     */
    @Query("SELECT * FROM archived_lessons WHERE studentName = :name ORDER BY date ASC")
    suspend fun getByStudentOnceAsc(name: String): List<ArchivedLesson>
}
