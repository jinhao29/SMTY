package com.shangmentiyu.sportscoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shangmentiyu.sportscoach.data.model.Student

/**
 * 学员全文检索 DAO（数据层）。
 *
 * 使用规范：
 * - 写入由 Room 自动生成的触发器维护，业务层通常无需调用 [insert] / [deleteAll]；
 *   仅在迁移或重建索引场景下手动调用 [rebuild] 清空并重建。
 * - 搜索统一通过 [searchIds] 拿到命中学员的姓名（Student 主键），再由
 *   [StudentDao] 关联查询完整 [Student] 信息，避免在 FTS 表里冗余存储。
 *
 * 与 [StudentDao] 的关系：
 * - 本 DAO 只负责"FTS 索引"维度，不返回完整学员对象；
 * - StudentRepository.searchStudent() 内部组合本 DAO + StudentDao 完成业务搜索。
 */
@Dao
interface StudentFtsDao {

    /**
     * 全文检索：返回命中学员的姓名列表（即 students 表主键）。
     *
     * SQL 说明：
     * - `studentFts MATCH :query` 走 FTS4 倒排索引，远快于 `LIKE '%x%'`。
     * - 返回 rowid 即对应 students 表的 rowid，再 JOIN 取 name（主键）。
     * - `name` 字段即 students 表主键，故直接作为关联键返回。
     *
     * @param query FTS 查询表达式，例如 `"张三"`、`"张*"`、`"school:实验"`。
     *              调用方需做基础转义，避免被识别为 FTS 操作符。
     */
    @Query(
        """
        SELECT s.name
        FROM students s
        JOIN studentFts f ON f.rowid = s.rowid
        WHERE studentFts MATCH :query
          AND s.isActive = 1
        ORDER BY s.createdAt ASC
        """
    )
    suspend fun searchIds(query: String): List<String>

    /** 清空 FTS 索引（仅用于重建索引场景，日常业务不要调用） */
    @Query("DELETE FROM studentFts")
    suspend fun deleteAll()

    /**
     * 重建 FTS 索引：基于 students 主表当前数据重新填充倒排索引。
     *
     * 调用时机：
     * - [com.shangmentiyu.sportscoach.data.db.AppDatabase.MIGRATION_20_21] 迁移完成后；
     * - 数据恢复后怀疑索引损坏时由 BackupManager 触发。
     *
     * 实现说明：
     * - `INSERT INTO studentFts(studentFts) VALUES('rebuild')` 是
     *   FTS4 外部内容表的标准重建命令，会根据 content 表全量重建索引。
     */
    @Query("INSERT INTO studentFts(studentFts) VALUES('rebuild')")
    suspend fun rebuild()

    /** Room 要求 @Insert 至少存在一个被注解的方法（用于自动生成触发器） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: com.shangmentiyu.sportscoach.data.model.StudentFts)
}
