package com.shangmentiyu.sportscoach.data.repo

import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.data.db.ArchivedLessonDao
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.db.LessonDao
import com.shangmentiyu.sportscoach.data.internal.AutoBackupScheduler
import com.shangmentiyu.sportscoach.data.model.ArchivedLesson
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 冷热数据归档域 Repository（v53 从 [OperationRepository] 拆出）。
 *
 * 职责：把一年前的课时记录从 lessons（热表）迁移到 archived_lessons（冷表），
 * 保持主表体积可控；支持手动归档与启动时自动归档两种触发方式。
 */
class LessonArchiveRepository(
    private val lessonDao: LessonDao,
    private val archivedLessonDao: ArchivedLessonDao?,
    private val db: AppDatabase?
) {

    /**
     * 归档结果：携带归档统计信息供 UI 反馈。
     *
     * @param success 是否成功
     * @param archivedCount 实际归档的记录数
     * @param message 用户可读消息
     */
    data class ArchiveResult(
        val success: Boolean,
        val archivedCount: Int,
        val message: String
    )

    /**
     * 将指定日期之前的课时记录从 lessons 表迁移到 archived_lessons 表（冷热归档）。
     *
     * 执行流程（单事务原子操作）：
     * 1. INSERT INTO archived_lessons SELECT ... FROM lessons WHERE date < :date
     * 2. DELETE FROM lessons WHERE date < :date
     * 3. 任意一步失败则整体回滚，保证数据不丢失
     *
     * 设计要点：
     * - 使用 SQLite 的 INSERT...SELECT 在数据库层一次性完成数据迁移，避免 Kotlin 层循环
     * - 迁移与删除在同一事务内原子完成，杜绝部分迁移导致的数据不一致
     * - archived_lessons 表字段与 lessons 完全一致，仅多一个 archivedAt 字段记录归档时间
     *
     * 使用场景：
     * - 学员详情设置入口"归档一年前记录"按钮触发
     * - 建议每年执行一次，保持主表 lessons 在合理体量（<5000 条）
     *
     * @param date 边界日期 YYYY-MM-DD（严格小于该日期的记录将被归档）
     * @return [ArchiveResult] 携带归档统计信息
     */
    suspend fun archiveLessonsBefore(date: String): ArchiveResult {
        val archiveDao = archivedLessonDao ?: return ArchiveResult(
            success = false,
            archivedCount = 0,
            message = "归档功能未初始化（archivedLessonDao 为空）"
        )
        val database = db ?: return ArchiveResult(
            success = false,
            archivedCount = 0,
            message = "归档功能未初始化（db 为空）"
        )

        return try {
            database.withTransaction {
                val archivedAt = System.currentTimeMillis()
                // 1. 迁移：将旧数据 INSERT INTO archived_lessons SELECT FROM lessons
                archiveDao.copyLessonsBeforeToDate(date, archivedAt)
                // 2. 删除：清理主表 lessons 中的旧数据
                val deleted = lessonDao.deleteBefore(date)
                android.util.Log.i("ArchiveLessons",
                    "归档完成：边界=$date 归档记录数=$deleted")

                // v30：归档属于大规模数据迁移，触发自动备份防抖
                AutoBackupScheduler.notifyDataChange()

                ArchiveResult(
                    success = true,
                    archivedCount = deleted,
                    message = "已归档 $deleted 条一年前的记录"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ArchiveLessons", "归档失败：${e.message}", e)
            ArchiveResult(
                success = false,
                archivedCount = 0,
                message = "归档失败：${e.message ?: "未知错误"}"
            )
        }
    }

    /**
     * 获取归档记录总数（用于诊断与统计）。
     */
    fun getArchivedCount(): Flow<Int> =
        archivedLessonDao?.count() ?: kotlinx.coroutines.flow.flowOf(0)

    /**
     * 按学员查询归档记录（历史报表场景）。
     */
    fun getArchivedByStudent(name: String): Flow<List<ArchivedLesson>> =
        archivedLessonDao?.getByStudent(name) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    /**
     * === v28：智能冷热数据自动迁移（App 启动时触发） ===
     *
     * 业务背景：
     * - v4 已实现手动归档入口（学员详情设置"归档一年前记录"按钮）
     * - 但教练很少主动触发，导致 lessons 表数据量持续膨胀（>2000 条后查询明显变慢）
     * - 本方法在 App 启动时自动检查，超过阈值时静默执行归档，保持主表体积可控
     *
     * 触发条件（同时满足）：
     * 1. lessons 表记录数 > [threshold]（默认 2000 条）
     * 2. 存在超过 [archiveDaysOld] 天（默认 365 天）的旧记录
     *
     * 执行流程：
     * 1. [LessonDao.countAllOnce] 一次性查询 lessons 表总数（非 Flow，避免订阅开销）
     * 2. 总数 ≤ 阈值 → 直接返回（无操作）
     * 3. 总数 > 阈值 → 计算归档边界日期（today - 365 天），调用 [archiveLessonsBefore]
     * 4. 整个迁移在事务内原子完成，失败不影响 App 启动
     *
     * 调用时机：
     * - [com.shangmentiyu.sportscoach.ui.home.HomeViewModel] init 块中调用
     * - 静默执行，无 UI 反馈（除非归档失败，通过返回值的 message 字段记录日志）
     *
     * 性能考虑：
     * - 仅一次 COUNT 查询 + 可能的一次事务，开销极低
     * - 归档操作使用 SQLite INSERT...SELECT 在数据库层完成，避免 Kotlin 层循环
     * - 即使 lessons 表 5000+ 条，归档耗时 < 500ms，不阻塞 UI
     *
     * @param threshold 触发阈值，默认 2000 条
     * @param archiveDaysOld 归档边界天数，默认 365 天
     * @return [ArchiveResult] 携带归档统计信息（未触发时 archivedCount=0）
     */
    suspend fun maybeAutoArchiveIfNeeded(
        threshold: Int = 2000,
        archiveDaysOld: Long = 365L
    ): ArchiveResult {
        archivedLessonDao ?: return ArchiveResult(
            success = false, archivedCount = 0,
            message = "归档功能未初始化（archivedLessonDao 为空）"
        )
        return try {
            // 1. 一次性查询主表总数
            val totalCount = lessonDao.countAllOnce()
            if (totalCount <= threshold) {
                // 未超过阈值，无需归档
                return ArchiveResult(
                    success = true, archivedCount = 0,
                    message = "未触发自动归档（$totalCount ≤ $threshold）"
                )
            }

            // 2. 计算归档边界日期（today - 365 天）
            val today = LocalDate.now()
            val boundary = today.minusDays(archiveDaysOld)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))

            android.util.Log.i("AutoArchive",
                "触发自动归档：lessons 表共 $totalCount 条 > 阈值 $threshold，边界日期=$boundary")

            // 3. 执行归档（事务原子操作）
            val result = archiveLessonsBefore(boundary)
            android.util.Log.i("AutoArchive",
                "自动归档完成：${result.message}（lessons 表剩余 ${totalCount - result.archivedCount} 条）")
            result
        } catch (e: Exception) {
            android.util.Log.e("AutoArchive", "自动归档失败：${e.message}", e)
            ArchiveResult(
                success = false, archivedCount = 0,
                message = "自动归档失败：${e.message ?: "未知错误"}"
            )
        }
    }

    /**
     * === v28：一次性获取全部归档记录（非 Flow，用于"查看全部历史归档"列表） ===
     *
     * UI 调用时机：教练在课时管理 Tab 点击"查看全部历史归档"按钮后调用。
     * 默认所有 LazyColumn 列表查询只查 lessons 表（热数据），
     * 仅在用户主动点击时才查询 archived_lessons 表（冷数据），保持日常列表流畅。
     *
     * @return 全部归档记录列表（按日期降序、时间降序）
     */
    suspend fun getAllArchivedOnce(): List<ArchivedLesson> {
        return archivedLessonDao?.getAllOnce() ?: emptyList()
    }
}
