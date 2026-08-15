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

    /** v46：双通道分页查询（studentId 优先、studentName 回退） */
    @Query("SELECT * FROM lessons WHERE studentId = :studentId OR (studentId IS NULL AND studentName = :name) ORDER BY date DESC, time DESC")
    fun pagingByStudentDual(studentId: String?, name: String): PagingSource<Int, Lesson>

    @Query("SELECT * FROM lessons WHERE studentName = :name ORDER BY date DESC, time DESC")
    fun getByStudent(name: String): Flow<List<Lesson>>

    /** v46：双通道查询（studentId 优先、studentName 回退） */
    @Query("SELECT * FROM lessons WHERE studentId = :studentId OR (studentId IS NULL AND studentName = :name) ORDER BY date DESC, time DESC")
    fun getByStudentDual(studentId: String?, name: String): Flow<List<Lesson>>

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
     * === v46 综合整治：双通道查询（studentId 优先，studentName 回退） ===
     *
     * 旧数据（v20 前）studentId 为 NULL，按姓名匹配兜底；
     * 新数据（studentId 非空）以唯一 ID 精确关联，杜绝改名断链。
     * studentName 字段保留且随改名级联更新，双通道不会重复计数（同一行只返回一次）。
     */
    @Query("SELECT * FROM lessons WHERE studentId = :studentId OR (studentId IS NULL AND studentName = :name) ORDER BY date DESC, time DESC")
    suspend fun getByStudentDualOnce(studentId: String?, name: String): List<Lesson>

    /** v46：双通道统计未消课课时（排课额度封顶用，studentId 优先、name 回退） */
    @Query("SELECT COUNT(*) FROM lessons WHERE date >= :fromDate AND (packageId = '' OR packageId IS NULL) AND (studentId = :studentId OR (studentId IS NULL AND studentName = :name))")
    suspend fun countUnconsumedFromDual(studentId: String?, name: String, fromDate: String): Int

    /** v46：双通道查重（长期排课生成去重用，studentId 优先、name 回退） */
    @Query("SELECT COUNT(*) FROM lessons WHERE date = :date AND time = :time AND (studentId = :studentId OR (studentId IS NULL AND studentName = :name))")
    suspend fun countByStudentDateTimeDual(studentId: String?, name: String, date: String, time: String): Int

    /**
     * 批量去重查询：查询学员在指定日期范围内、指定时间点已存在的排课日期集合。
     *
     * 用于 [com.shangmentiyu.sportscoach.data.repo.OperationRepository.batchAutoSchedule]
     * 在事务前预查已有排课，过滤重复日期，避免 UNIQUE(studentName, date, time) 约束冲突。
     * 双通道：studentId 优先、studentName 回退。
     *
     * @param studentId 学员唯一 ID（可空，旧数据无 ID）
     * @param name 学员姓名（studentId 为空时回退匹配）
     * @param fromDate 日期范围起点（含，yyyy-MM-dd）
     * @param toDate 日期范围终点（含，yyyy-MM-dd）
     * @param time 上课时间 HH:mm
     * @return 已存在排课的日期字符串列表（yyyy-MM-dd）
     */
    @Query("SELECT date FROM lessons WHERE (studentId = :studentId OR (studentId IS NULL AND studentName = :name)) AND date >= :fromDate AND date <= :toDate AND time = :time")
    suspend fun getExistingDatesByStudentAndTime(studentId: String?, name: String, fromDate: String, toDate: String, time: String): List<String>

    /**
     * === v49 彻底重构：三要素额度统计（已消耗 / 待消耗 / 按天查重） ===
     *
     * 剩余可排课时 = 总课时(活跃包剩余之和) - 已消耗(已签退) - 待消耗(占位)。
     * 以下查询为三要素公式的数据基础，双通道（studentId 优先、studentName 回退）。
     * （v49 起替代旧的 countLongTermPendingFrom / countLongTermPendingFromDual，
     *   新的「待消耗」判定以 signOutTime 是否为空为准，语义更贴近业务定义）
     */

    /** 已签退课时数（signOutTime 非空），即三要素公式中的「已消耗」；体验课不消耗课时包，故排除 */
    @Query(
        "SELECT COUNT(*) FROM lessons WHERE " +
            "(studentId = :studentId OR (studentId IS NULL AND studentName = :name)) " +
            "AND (signOutTime IS NOT NULL AND signOutTime != '') " +
            "AND isTrial = 0"
    )
    suspend fun countCheckedOutLessonsDual(studentId: String?, name: String): Int

    /** 待消耗占位课时数（长期自动生成 + 未签退 + date >= fromDate），即三要素公式中的「待消耗」；体验课不计入占位消耗 */
    @Query(
        "SELECT COUNT(*) FROM lessons WHERE " +
            "(studentId = :studentId OR (studentId IS NULL AND studentName = :name)) " +
            "AND date >= :fromDate " +
            "AND lessonType LIKE '%(长期自动)%' " +
            "AND (signOutTime IS NULL OR signOutTime = '') " +
            "AND isTrial = 0"
    )
    suspend fun countPendingPlaceholderLessonsDual(studentId: String?, name: String, fromDate: String): Int

    /**
     * === 根治口径：待消耗占位课时数（已排但未签退，仅统计今天及未来） ===
     *
     * 凡 lessons 表中 signOutTime 为空（未签退即未消课扣减）且非体验课、日期 >= 今天的记录，
     * 视为「待消耗」，占用剩余额度，杜绝排课超卖；过去的未签退课时是历史遗留，不占用额度。
     */
    @Query(
        "SELECT COUNT(*) FROM lessons WHERE " +
            "(studentId = :studentId OR (studentId IS NULL AND studentName = :name)) " +
            "AND date >= :today " +
            "AND (signOutTime IS NULL OR signOutTime = '') " +
            "AND isTrial = 0"
    )
    suspend fun countUncheckedOutLessonsDual(studentId: String?, name: String, today: String): Int

    /** 按天查重：学员在某天是否已有课时记录（长期排课生成器按天检查「当天已排」） */
    @Query(
        "SELECT COUNT(*) FROM lessons WHERE " +
            "(studentId = :studentId OR (studentId IS NULL AND studentName = :name)) " +
            "AND date = :date"
    )
    suspend fun countLessonsByStudentDateDual(studentId: String?, name: String, date: String): Int

    /**
     * === v32：签到翻转入口查询 ===
     *
     * 查学员指定日期首条「未签退」课时（排课占位 / 已签到但未签退），
     * 签到逻辑优先翻转该条而非新建，避免同一学员同日重复创建课时。
     * 双通道：studentId 优先、studentName 回退。
     */
    @Query(
        "SELECT * FROM lessons WHERE " +
            "(studentId = :studentId OR (studentId IS NULL AND studentName = :name)) " +
            "AND date = :date " +
            "AND (signOutTime IS NULL OR signOutTime = '') " +
            "ORDER BY time ASC LIMIT 1"
    )
    suspend fun findPendingByStudentDateDual(studentId: String?, name: String, date: String): Lesson?

    @Insert
    suspend fun insert(lesson: Lesson)

    /**
     * 批量插入课时记录（自动排课事务内一次性写入）。
     *
     * 用于 [com.shangmentiyu.sportscoach.data.repo.OperationRepository.batchAutoSchedule]
     * 在单个 Room 事务中一次性写入所有课时记录，避免逐条插入。
     */
    @Insert
    suspend fun insertAll(lessons: List<Lesson>)

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

    /**
     * 批量删除课时记录（课后反馈 Tab 多选模式批量删除使用）。
     *
     * 单条 SQL `DELETE ... WHERE id IN (...)` 由 SQLite 原子执行，满足原子性要求，
     * 无需额外包裹事务。空列表时 IN 子句会被 Room 编译为 `IN ()` 导致语法错误，
     * 调用方须在 Repository 层提前拦截。
     *
     * @param ids 待删除的课时 ID 列表
     * @return 实际删除的记录数
     */
    @Query("DELETE FROM lessons WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int

    @Query("DELETE FROM lessons WHERE studentName = :name")
    suspend fun deleteByStudent(name: String)

    /**
     * 基于 studentId 软删除学员的课时记录（级联删除，双通道）。
     *
     * 与 [deleteByStudent] 的区别：
     * - 旧方法按 studentName（可能重名）定位，本方法优先按 studentId（唯一）定位
     * - 旧数据（studentId 为 NULL，v20 前）回退按 studentName 定位，避免漏删孤儿课时
     * - 适用于 [StudentRepository.softDeleteStudentById] 级联删除场景
     *
     * @param studentId 学员唯一 ID（非空）
     * @param name 学员姓名（studentId 为 NULL 的旧数据回退用）
     */
    @Query("DELETE FROM lessons WHERE studentId = :studentId OR (studentId IS NULL AND studentName = :name)")
    suspend fun deleteByStudentIdDual(studentId: String, name: String)

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
     * 用于 [com.shangmentiyu.sportscoach.app.framework.ScheduleReminderWorker]
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
     * === v32：清理过期未签到课表 ===
     *
     * 清理规则（修正版）：
     * 1. date < :today —— 仅清理已过期课表，绝不影响今天及未来排课
     * 2. status = '待签到' —— 仅清理「排课后从未签到」的占位课时
     *    （签到会翻转为"已签到"，签退会置为"已签退"，均不受影响）
     * 3. signOutTime 为空 —— 兜底排除已签退记录
     *
     * 已签到 / 已签退的真实课时记录是业务凭证，绝不清理。
     *
     * @param today 当前日期 YYYY-MM-DD（边界日期，date 严格小于此值的记录才会被清理）
     * @return 被物理删除的记录数（供 UI 弹窗反馈清理数量）
     */
    @Query("""
        DELETE FROM lessons
        WHERE date < :today
            AND status = '待签到'
            AND (signOutTime IS NULL OR signOutTime = '')
    """)
    suspend fun deleteExpiredUnsignedLessons(today: String): Int

    /** 小班课：查询同 groupScheduleId 的所有课时记录 */
    @Query("SELECT * FROM lessons WHERE groupScheduleId = :groupScheduleId")
    suspend fun getByGroupScheduleId(groupScheduleId: String): List<com.shangmentiyu.sportscoach.data.model.Lesson>

    /** 统计学员的正式课时记录数（isTrial=0），用于"首次自动体验课"判断 */
    @Query(
        "SELECT COUNT(*) FROM lessons WHERE " +
            "(studentId = :studentId OR (studentId IS NULL AND studentName = :name)) " +
            "AND isTrial = 0"
    )
    suspend fun countFormalLessonsDual(studentId: String?, name: String): Int

    /**
     * 修复脚本：查询已排课但未签退且已关联课时包的课时记录。
     *
     * 这些记录是旧版自动排课在排课阶段错误扣费（设置 packageId + 增加 usedLessons）的遗留数据。
     * 修复时需将这些记录的 packageId 清空，并回退对应课时包的 usedLessons。
     *
     * @return 未签退且 packageId 非空的课时记录列表
     */
    @Query(
        "SELECT * FROM lessons WHERE " +
            "packageId != '' AND packageId IS NOT NULL " +
            "AND status != '已签退'"
    )
    suspend fun getUnconsumedWithPackageId(): List<Lesson>

    /**
     * 修复脚本：批量清除未签退课时的 packageId（将排课占位恢复为待消课状态）。
     *
     * 清除后，签退时 [OperationRepository.consumeLessonForCheckOut] 会正常执行课时包扣减，
     * 实现排课与消课完全分离。
     *
     * @return 受影响行数（被清除 packageId 的课时记录数）
     */
    @Query(
        "UPDATE lessons SET packageId = '' WHERE " +
            "packageId != '' AND packageId IS NOT NULL " +
            "AND status != '已签退'"
    )
    suspend fun clearPackageIdForUnconsumed(): Int
}
