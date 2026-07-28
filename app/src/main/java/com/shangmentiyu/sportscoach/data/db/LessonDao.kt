package com.shangmentiyu.sportscoach.data.db

import androidx.paging.PagingSource
import androidx.room.*
import com.shangmentiyu.sportscoach.data.model.Lesson
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons ORDER BY date DESC, time DESC")
    fun getAll(): Flow<List<Lesson>>

    /**
     * 分页查询全部课时（按日期降序、时间降序）。
     * 用于历史课时列表，配合 Paging 3 实现"滑动到底部再加载下一页"，
     * 避免一次性加载 5000+ 条记录导致内存峰值与 Compose 重组卡顿。
     *
     * 使用 idx_lessons_date_time_asc 索引的反向扫描。
     */
    @Query("SELECT * FROM lessons ORDER BY date DESC, time DESC")
    fun pagingAll(): PagingSource<Int, Lesson>

    /**
     * 按学员分页查询课时（按日期降序、时间降序）。
     * 用于学员详情页历史课时列表，避免学员课时记录过多时一次性加载。
     *
     * 使用 idx_lessons_student_date_time 索引。
     */
    @Query("SELECT * FROM lessons WHERE studentName = :name ORDER BY date DESC, time DESC")
    fun pagingByStudent(name: String): PagingSource<Int, Lesson>

    @Query("SELECT * FROM lessons WHERE studentName = :name ORDER BY date DESC, time DESC")
    fun getByStudent(name: String): Flow<List<Lesson>>

    @Query("SELECT * FROM lessons WHERE date = :date ORDER BY time DESC")
    fun getByDate(date: String): Flow<List<Lesson>>

    /**
     * 查询从指定日期起的所有课时（按日期升序、时间升序）。
     * 用于学员列表"下一节课"显示：取每个学员的第一条记录即为下一节课。
     *
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     */
    @Query("SELECT * FROM lessons WHERE date >= :fromDate ORDER BY date ASC, time ASC")
    fun getFrom(fromDate: String): Flow<List<Lesson>>

    /**
     * 查询从指定日期起的"未签退"课时（按日期升序、时间升序）。
     *
     * 与 [getFrom] 区别：过滤掉已签退（signOutTime 非空）的课时。
     * 用于学员列表"下一节课"显示——签退后的课时视为已完成，
     * 不应再作为"下一节课"显示给教练。
     *
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     */
    @Query(
        "SELECT * FROM lessons WHERE date >= :fromDate " +
            "AND (signOutTime = '' OR signOutTime IS NULL) " +
            "ORDER BY date ASC, time ASC"
    )
    fun getUpcomingFrom(fromDate: String): Flow<List<Lesson>>

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun getById(id: String): Lesson?

    @Query("SELECT COUNT(*) FROM lessons WHERE date = :date")
    fun countByDate(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM lessons")
    fun count(): Flow<Int>

    /**
     * === v28：一次性查询 lessons 表总记录数（非 Flow） ===
     *
     * 用于 [com.shangmentiyu.sportscoach.data.repo.OperationRepository.maybeAutoArchiveIfNeeded]
     * 在 App 启动时检查数据量是否超过阈值（默认 2000 条），决定是否触发自动归档。
     *
     * 与 [count] 区别：本方法返回 Int 而非 Flow<Int>，适合一次性检查场景，
     * 避免订阅 Flow 后续要手动取消订阅的开销。
     */
    @Query("SELECT COUNT(*) FROM lessons")
    suspend fun countAllOnce(): Int

    /**
     * 查重：同一学员+同一日期+同一时间是否已有课时记录。
     * 用于长期排课自动生成时避免重复插入。
     */
    @Query("SELECT COUNT(*) FROM lessons WHERE studentName = :studentName AND date = :date AND time = :time")
    suspend fun countByStudentDateTime(studentName: String, date: String, time: String): Int

    /**
     * 统计学员指定日期起未消课的课时数量（packageId 为空表示尚未扣减课时包）。
     * 用于长期排课生成时关联课时包余额，避免超额排课。
     *
     * @param studentName 学员姓名
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     * @return 未消课的课时数量
     */
    @Query("SELECT COUNT(*) FROM lessons WHERE studentName = :studentName AND date >= :fromDate AND (packageId = '' OR packageId IS NULL)")
    suspend fun countUnconsumedFrom(studentName: String, fromDate: String): Int

    /**
     * === v27：统计长期自动生成且尚未签退的课时数量 ===
     *
     * 用户需求："签退后消耗课时"重构后，[countUnconsumedFrom] 仍按 packageId 是否为空判断，
     * 但签退流程改为：签到时创建 status="已签到"、packageId="" 的 Lesson；
     * 签退时才扣减课时包并更新 status="已签退"、packageId=目标包ID。
     *
     * 因此"未消费"的课时应包含两类：
     * 1. status="已签到"（签到未签退，等待签退消课）
     * 2. packageId="" 且 status != "已签退"（长期自动生成的待签到课时）
     *
     * 本方法只统计"长期自动"类型的待签到课时，避免与教练手动签到的课时混淆。
     * 长期自动课时识别：lessonType 包含"(长期自动)"。
     *
     * 用于 [com.shangmentiyu.sportscoach.ui.operation.OperationViewModel.ensureLongTermLessonsForWeek]
     * 计算可用余额 = 有效课时包剩余 - 长期自动未签退课时数。
     *
     * @param studentName 学员姓名
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     * @return 长期自动生成且未签退的课时数量
     */
    @Query(
        "SELECT COUNT(*) FROM lessons WHERE studentName = :studentName " +
            "AND date >= :fromDate " +
            "AND lessonType LIKE '%(长期自动)%' " +
            "AND status != '已签退'"
    )
    suspend fun countLongTermPendingFrom(studentName: String, fromDate: String): Int

    @Insert
    suspend fun insert(lesson: Lesson)

    /**
     * === v27：返回受影响行数，便于签退消课事务校验 ===
     *
     * 与 [LessonPackageDao.update] 一致，返回 Int 表示受影响行数。
     * 调用方据此判断 update 是否真正生效（预期 1），避免数据未落库导致的不一致。
     *
     * 历史调用方（如 [com.shangmentiyu.sportscoach.data.repo.LessonRepository.updateLesson]）
     * 不使用返回值，Kotlin 自动丢弃，无需修改。
     */
    @Update
    suspend fun update(lesson: Lesson): Int

    @Delete
    suspend fun delete(lesson: Lesson)

    @Query("DELETE FROM lessons WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM lessons WHERE studentName = :name")
    suspend fun deleteByStudent(name: String)

    /**
     * 删除指定日期之前的所有课时记录（热数据归档清理）。
     *
     * 与 [com.shangmentiyu.sportscoach.data.db.ArchivedLessonDao.copyLessonsBeforeToDate]
     * 在同一事务内执行：
     * - 先 INSERT...SELECT 将旧数据迁移到 archived_lessons 表
     * - 再调用本方法 DELETE 旧数据
     *
     * 注意：必须先迁移再删除，否则数据丢失。
     *
     * @param date 边界日期 YYYY-MM-DD（严格小于该日期的记录将被删除）
     * @return 受影响行数（已删除的记录数）
     */
    @Query("DELETE FROM lessons WHERE date < :date")
    suspend fun deleteBefore(date: String): Int

    /**
     * 一次性查询指定日期的课时（非 Flow，用于后台任务）。
     *
     * 用于 [com.shangmentiyu.sportscoach.core.ScheduleReminderWorker]
     * 查询明天的排课记录，触发本地通知。
     *
     * @param date 日期 YYYY-MM-DD
     * @return 该日期的全部课时列表（按时间升序）
     */
    @Query("SELECT * FROM lessons WHERE date = :date ORDER BY time ASC")
    suspend fun getByDateOnce(date: String): List<com.shangmentiyu.sportscoach.data.model.Lesson>

    /**
     * v26 优化4：一次性查询全部课时（非 Flow，用于孤儿数据自检）。
     *
     * 与 [ScheduleDao.getAllOnce] / [LessonPackageDao.getAllOnce] 配合，
     * 在设置页"数据库修复与检查"中扫描 studentName 不在 students 表的孤儿记录。
     *
     * 不分页、不排序，仅用于后台扫描，数据量大时只在自检时调用一次。
     */
    @Query("SELECT * FROM lessons")
    suspend fun getAllOnce(): List<com.shangmentiyu.sportscoach.data.model.Lesson>

    /** 学员改名：级联更新 lessons 表的 studentName 字段 */
    @Query("UPDATE lessons SET studentName = :newName WHERE studentName = :oldName")
    suspend fun renameStudent(oldName: String, newName: String)

    /**
     * === v33 数据流加固：按 studentId 级联改名（推荐路径） ===
     *
     * 与 [renameStudent] 区别：本方法基于 studentId 精准定位，不受同名干扰。
     * 仅更新 studentId 匹配的行，旧数据 studentId 为 NULL 不会被误改。
     *
     * @param studentId 学员唯一 ID
     * @param newName 新姓名
     * @return 受影响行数（用于审计与诊断）
     */
    @Query("UPDATE lessons SET studentName = :newName WHERE studentId = :studentId")
    suspend fun updateStudentNameByStudentId(studentId: String, newName: String): Int

    /**
     * === Bug 修复2：清理过去未完成的长期排课记录（历史废弃占位排课） ===
     *
     * 业务背景：
     * - 长期排课（schedule.isLongTerm=true）会自动按 dayOfWeek 生成 Lesson 记录
     * - 历史 Bug 导致即使不勾选长期排课、或为已过去的日期也生成了大量 Lesson 占位记录
     * - 这些记录 status != '已签退'、date < 今天、对应的 schedule 为 isLongTerm=true
     * - 它们污染了历史周历视图，且无业务价值（学员未实际签到），需要物理删除
     *
     * 清理规则：
     * 1. status != '已签退'（保留已签退的历史真实记录，作为学员上课凭证）
     * 2. date < :today（只清理过去日期，不影响今天及未来）
     * 3. lessonType LIKE '%(长期自动)%'（仅清理长期自动生成的占位排课）
     *    → 通过 lessonType 字段标记识别，避免与 schedule 表 JOIN 带来的性能开销
     *    → lessonType 字段在 [com.shangmentiyu.sportscoach.data.repo.OperationRepository.generateLongTermLesson]
     *      生成时已附加 "(长期自动)" 后缀
     *
     * 已签退的真实课时记录（学员已实际消课）不会被清理，仍保留在历史周历中，
     * 由 UI 层 [com.shangmentiyu.sportscoach.ui.schedule.ScheduleScreen.KeepScheduleCard]
     * 通过置灰 + "已过去"角标区分展示。
     *
     * @param today 当前日期 YYYY-MM-DD（边界日期，date 严格小于此值的记录才会被清理）
     * @return 被物理删除的记录数（供 UI 通过 toast 反馈清理结果）
     */
    @Query("""
        DELETE FROM lessons
        WHERE status != '已签退'
            AND date < :today
            AND lessonType LIKE '%(长期自动)%'
    """)
    suspend fun deleteUnfinishedPastLongTermLessons(today: String): Int
}
