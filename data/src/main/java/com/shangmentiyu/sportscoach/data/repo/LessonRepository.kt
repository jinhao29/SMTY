package com.shangmentiyu.sportscoach.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.shangmentiyu.sportscoach.data.db.LessonDao
import com.shangmentiyu.sportscoach.data.model.Lesson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * 课时 Repository（管理层）。
 *
 * 日期/时间格式化统一使用 [DateTimeFormatter]（线程安全），可安全地在多协程并发场景下共享调用。
 * [DateTimeFormatter] 内部不可变，无 [SimpleDateFormat] 的 Calendar 共享状态污染问题。
 */
class LessonRepository(private val dao: LessonDao) {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    /** 当前日期字符串（yyyy-MM-dd），基于 [LocalDate.now] 线程安全获取 */
    private fun todayDateStr(): String = LocalDate.now().format(dateFormatter)

    /** 当前时间字符串（HH:mm），基于 [LocalTime.now] 线程安全获取 */
    private fun nowTimeStr(): String = LocalTime.now().format(timeFormatter)

    fun getAllLessons(): Flow<List<Lesson>> = dao.getAll()
    fun getLessonsByStudent(name: String): Flow<List<Lesson>> = dao.getByStudent(name)
    /** v46：双通道查询（studentId 优先、studentName 回退） */
    fun getLessonsByStudentDual(studentId: String?, name: String): Flow<List<Lesson>> =
        dao.getByStudentDual(studentId, name)
    fun getTodayLessons(): Flow<List<Lesson>> = dao.getByDate(todayDateStr())
    fun getTodayCount(): Flow<Int> = dao.countByDate(todayDateStr())
    fun getTotalCount(): Flow<Int> = dao.count()

    /**
     * 分页加载全部历史课时（按日期降序、时间降序）。
     *
     * 使用 Paging 3 + Room 的 PagingSource 集成：
     * - 每页默认 30 条，预取距离 15 条（滑动到底部前提前加载下一页）
     * - Room 自动管理 PagingSource 的失效与重新查询（数据变更时通过 invalidation 回调触发）
     * - 命中 idx_lessons_date_time_asc 索引的反向扫描，避免全表排序
     *
     * 适用场景：
     * - 历史课时列表页（"滑动到底部再加载下一页"）
     * - 大数据量场景（5000+ 条记录）下避免一次性加载导致内存峰值与卡顿
     *
     * 注意：当前 GrowthScreen 等基于 Flow<List<Lesson>> 的页面未接入此分页接口，
     * 因为它们需要全量数据做按月分组聚合，强行换 Paging 会破坏分组 UI。
     * 此接口预留给未来新增的"纯线性历史课时列表"页面使用。
     *
     * @return Flow<PagingData<Lesson>>，UI 通过 collectAsLazyPagingItems() 订阅
     */
    fun pagedAllLessons(): Flow<PagingData<Lesson>> = Pager(
        config = PagingConfig(
            pageSize = 30,
            prefetchDistance = 15,
            initialLoadSize = 60,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { dao.pagingAll() }
    ).flow.map { it.map { lesson -> lesson } }

    /**
     * 分页加载指定学员的历史课时（按日期降序、时间降序）。
     *
     * 命中 idx_lessons_student_date_time 复合索引，避免全表扫描。
     *
     * @param name 学员姓名
     * @return Flow<PagingData<Lesson>>，UI 通过 collectAsLazyPagingItems() 订阅
     */
    fun pagedLessonsByStudent(name: String): Flow<PagingData<Lesson>> = Pager(
        config = PagingConfig(
            pageSize = 30,
            prefetchDistance = 15,
            initialLoadSize = 60,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { dao.pagingByStudent(name) }
    ).flow.map { it.map { lesson -> lesson } }

    /** v46：双通道分页查询（studentId 优先、studentName 回退） */
    fun pagedLessonsByStudentDual(studentId: String?, name: String): Flow<PagingData<Lesson>> = Pager(
        config = PagingConfig(
            pageSize = 30,
            prefetchDistance = 15,
            initialLoadSize = 60,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { dao.pagingByStudentDual(studentId, name) }
    ).flow.map { it.map { lesson -> lesson } }

    /**
     * 查询从指定日期起的所有课时（按日期升序、时间升序）。
     * 用于学员列表"下一节课"显示：取每个学员的第一条记录即为下一节课。
     *
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     */
    fun getFrom(fromDate: String): Flow<List<Lesson>> = dao.getFrom(fromDate)

    /**
     * 查询从指定日期起的"未签退"课时（按日期升序、时间升序）。
     *
     * 与 [getFrom] 区别：过滤掉已签退（signOutTime 非空）的课时。
     * 用于学员列表"下一节课"显示——签退后的课时视为已完成，
     * 不应再作为"下一节课"显示给教练。
     *
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     */
    fun getUpcomingFrom(fromDate: String): Flow<List<Lesson>> = dao.getUpcomingFrom(fromDate)

    /**
     * 忘记签退提醒：查询过去日期已签到但未签退的课时（按日期降序、时间降序）。
     *
     * 数据库层已叠加 signOutTime 为空的兜底条件（排除 v24 迁移的 status 残留"已签到"旧数据），
     * 详见 [LessonDao.getUnsignedOutLessonsBefore]。
     *
     * @param today 当前日期 YYYY-MM-DD（边界，date 严格小于此值才计入）
     */
    fun getUnsignedOutLessonsBefore(today: String): Flow<List<Lesson>> =
        dao.getUnsignedOutLessonsBefore(today)

    suspend fun getById(id: String): Lesson? = dao.getById(id)

    /** 小班课：查询同 groupScheduleId 的所有课时记录 */
    suspend fun getByGroupScheduleId(groupScheduleId: String): List<Lesson> =
        dao.getByGroupScheduleId(groupScheduleId)

    /** 一次性获取学员全部课时（非 Flow） */
    suspend fun getByStudentOnce(name: String): List<Lesson> = dao.getByStudent(name).first()

    /** v46：双通道一次性获取学员全部课时（studentId 优先、studentName 回退） */
    suspend fun getByStudentOnceDual(studentId: String?, name: String): List<Lesson> =
        dao.getByStudentDual(studentId, name).first()

    /**
     * 创建课时记录：自动生成 ID、当前日期与时间。
     *
     * @param studentId 学员唯一ID（软关联外键，v46 断流修复：成绩/计划创建的课时必须绑定，
     *                  保证 studentId 通道可查询；旧调用方不传则为 null，走 studentName 软关联兜底）
     * @return 新建的课时 ID
     */
    suspend fun createLesson(
        studentName: String,
        coach: String,
        packageId: String = "",
        studentId: String? = null
    ): String {
        // UUID 前 12 位：熵 48 bit，按生日悖论百万级数据无碰撞
        val id = UUID.randomUUID().toString().take(12)
        val lesson = Lesson(
            id = id,
            date = todayDateStr(),
            time = nowTimeStr(),
            studentName = studentName,
            studentId = studentId,
            coach = coach,
            packageId = packageId
        )
        dao.insert(lesson)
        return id
    }

    suspend fun updateLesson(lesson: Lesson) = dao.update(lesson)
    suspend fun deleteLesson(id: String) = dao.deleteById(id)
    suspend fun deleteByStudent(name: String) = dao.deleteByStudent(name)

    /**
     * 批量删除课时记录（课后反馈 Tab 多选模式批量删除使用）。
     *
     * 使用 [LessonDao.deleteByIds] 单条 SQL 原子删除，避免循环调用 [deleteLesson]
     * 产生多次数据库往返。空列表保护：IN 子句空列表会触发语法错误，此处提前拦截。
     *
     * @param ids 待删除的课时 ID 列表
     * @return 实际删除的记录数
     */
    suspend fun deleteLessons(ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        return dao.deleteByIds(ids)
    }
}
