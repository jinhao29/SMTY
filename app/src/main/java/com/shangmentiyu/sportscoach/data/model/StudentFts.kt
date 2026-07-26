package com.shangmentiyu.sportscoach.data.model

import androidx.room.Entity
import androidx.room.Fts4

/**
 * 学员全文检索虚拟表（FTS4 外部内容表）。
 *
 * 设计要点：
 * - 使用 `@Fts4(contentEntity = Student::class)` 让 Room 自动创建
 *   `CREATE VIRTUAL TABLE studentFts USING fts4(..., content='students')`
 *   并自动生成 students 表的 INSERT/UPDATE/DELETE 触发器，
 *   保持 FTS 索引与主表实时同步，无需业务层手动维护。
 * - 仅索引高频搜索字段：name / school / phone，
 *   不索引数值字段（age/bmi 等），避免索引膨胀。
 * - 搜索通过 `MATCH` 操作符实现，比 `LIKE '%x%'` 快数十倍，
 *   且支持中文分词后的子串匹配（借助 SQLite ICU 分词器）。
 *
 * 注意：
 * - FTS 表不存储数据本体，仅存储倒排索引；
 *   查询时通过 rowid 关联 students 主表取得完整 [Student] 信息。
 * - 老用户升级走 [com.shangmentiyu.sportscoach.data.db.AppDatabase.MIGRATION_20_21]，
 *   新装用户由 Room 自动建表 + 触发器。
 */
@Fts4(contentEntity = Student::class)
@Entity(tableName = "studentFts")
data class StudentFts(
    val name: String,
    val school: String,
    val phone: String
)
