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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 课时 Repository（管理层）。
 *
 * 注意：[SimpleDateFormat] 非线程安全，禁止作为成员变量持有；
 * 统一通过 [todayDateStr] / [nowTimeStr] 在方法内新建实例使用。
 */
class LessonRepository(private val dao: LessonDao) {

    /** 当前日期字符串（yyyy-MM-dd），每次调用新建 SimpleDateFormat 实例，避免并发污染 */
    private fun todayDateStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** 当前时间字符串（HH:mm），每次调用新建 SimpleDateFormat 实例 */
    private fun nowTimeStr(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    fun getAllLessons(): Flow<List<Lesson>> = dao.getAll()
    fun getLessonsByStudent(name: String): Flow<List<Lesson>> = dao.getByStudent(name)
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

    /**
     * 查询从指定日期起的所有课时（按日期升序、时间升序）。
     * 用于学员列表"下一节课"显示：取每个学员的第一条记录即为下一节课。
     *
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     */
    fun getFrom(fromDate: String): Flow<List<Lesson>> = dao.getFrom(fromDate)

    suspend fun getById(id: String): Lesson? = dao.getById(id)

    /** 一次性获取学员全部课时（非 Flow） */
    suspend fun getByStudentOnce(name: String): List<Lesson> = dao.getByStudent(name).first()

    /**
     * 创建课时记录：自动生成 ID、当前日期与时间。
     * @return 新建的课时 ID
     */
    suspend fun createLesson(studentName: String, coach: String, packageId: String = ""): String {
        // UUID 前 12 位：熵 48 bit，按生日悖论百万级数据无碰撞
        val id = UUID.randomUUID().toString().take(12)
        val lesson = Lesson(
            id = id,
            date = todayDateStr(),
            time = nowTimeStr(),
            studentName = studentName,
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
     * 课后签退：记录签退时间与可选的签退照片路径。
     *
     * 注意：签退不消课（消课在签到时已完成），仅补充签退信息。
     *
     * @param lessonId 课时 ID
     * @param photoPath 签退照片路径（空=未拍照）
     * @return 是否签退成功（课时不存在时返回 false）
     */
    suspend fun signOut(lessonId: String, photoPath: String = ""): Boolean {
        val lesson = dao.getById(lessonId) ?: return false
        dao.update(lesson.copy(
            signOutTime = nowTimeStr(),
            signOutPhotoPath = photoPath
        ))
        return true
    }
}
