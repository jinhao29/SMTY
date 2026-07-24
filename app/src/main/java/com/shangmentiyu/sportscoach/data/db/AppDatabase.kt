package com.shangmentiyu.sportscoach.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shangmentiyu.sportscoach.data.model.BodyMetricHistory
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.ParentReport
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.model.ScheduleMemory
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.model.TrainingCycle

@Database(
    entities = [Student::class, Lesson::class, LessonPackage::class, Coach::class, Schedule::class, ParentReport::class, TrainingCycle::class, BodyMetricHistory::class, ScheduleMemory::class],
    version = 16,
    exportSchema = false
)
@TypeConverters(com.shangmentiyu.sportscoach.data.model.Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun lessonDao(): LessonDao
    abstract fun lessonPackageDao(): LessonPackageDao
    abstract fun coachDao(): CoachDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun parentReportDao(): ParentReportDao
    abstract fun trainingCycleDao(): TrainingCycleDao
    abstract fun bodyMetricHistoryDao(): BodyMetricHistoryDao
    abstract fun scheduleMemoryDao(): ScheduleMemoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v4 → v5：students 表新增身体形态字段。
         * - age       INTEGER NOT NULL DEFAULT 0
         * - heightCm  INTEGER NOT NULL DEFAULT 0
         * - weightKg  REAL    NOT NULL DEFAULT 0
         * - bmi       REAL    NOT NULL DEFAULT 0
         * - updatedAt INTEGER NOT NULL DEFAULT 0
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE students ADD COLUMN age INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE students ADD COLUMN heightCm INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE students ADD COLUMN weightKg REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE students ADD COLUMN bmi REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE students ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v5 → v6：lessons 表新增 packageId 字段，记录签到消耗的课时包。
         * - packageId TEXT NOT NULL DEFAULT ''
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lessons ADD COLUMN packageId TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v6 → v7：lessons 表新增 photoPath 字段，记录签到照片路径。
         * - photoPath TEXT NOT NULL DEFAULT ''
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lessons ADD COLUMN photoPath TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v7 → v8：新增 training_cycles 表，存储学员的多周训练周期计划。
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS training_cycles (
                        id TEXT NOT NULL PRIMARY KEY,
                        studentName TEXT NOT NULL,
                        name TEXT NOT NULL,
                        goal TEXT NOT NULL DEFAULT '',
                        totalWeeks INTEGER NOT NULL DEFAULT 4,
                        startDate TEXT NOT NULL,
                        endDate TEXT NOT NULL DEFAULT '',
                        weeklyPlanJson TEXT NOT NULL DEFAULT '[]',
                        status TEXT NOT NULL DEFAULT '进行中',
                        note TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v8 → v9：新增 body_metric_history 表，记录学员身体形态变化历史。
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS body_metric_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        studentName TEXT NOT NULL,
                        date TEXT NOT NULL,
                        heightCm INTEGER NOT NULL DEFAULT 0,
                        weightKg REAL NOT NULL DEFAULT 0,
                        note TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v9 → v10：schedules 表新增 content（训练内容 JSON）和 color（卡片颜色）字段，
         * 支持课表视图（参考 Wake Up 课表）的课程内容编辑与彩色卡片展示。
         * - content TEXT NOT NULL DEFAULT '[]'
         * - color  TEXT NOT NULL DEFAULT 'blue'
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN content TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE schedules ADD COLUMN color TEXT NOT NULL DEFAULT 'blue'")
            }
        }

        /**
         * v10 → v11：schedules 表新增 preClassTask（课前任务 JSON）字段，
         * 支持上课前任务编排与课堂展示。
         * - preClassTask TEXT NOT NULL DEFAULT '[]'
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN preClassTask TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /**
         * v11 → v12：
         * - lessons 表新增 signOutTime（签退时间）和 signOutPhotoPath（签退照片路径）字段，
         *   支持课后签退与签退拍照。
         * - schedules 表新增 equipment（上课器材 JSON）字段，
         *   支持排课时选择绳梯/小栏架/敏捷圈等 10 种器材。
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lessons ADD COLUMN signOutTime TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE lessons ADD COLUMN signOutPhotoPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE schedules ADD COLUMN equipment TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /**
         * v12 → v13：schedules 表新增 contentImages（训练内容图片路径 JSON）字段，
         * 支持从电脑截图导入训练计划图片。
         * - contentImages TEXT NOT NULL DEFAULT '[]'
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN contentImages TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /**
         * v13 → v14：
         * - schedules 表新增 isLongTerm（是否长期排课）字段，勾选后每周自动生成对应时间的课表。
         * - 新增 schedule_memory 表，记录教练历史用过的上课时间/地点，供排课时下拉选择。
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN isLongTerm INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS schedule_memory (
                        coachName TEXT NOT NULL,
                        field TEXT NOT NULL,
                        value TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(coachName, field, value)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v14 → v15：lessons 表新增 contentImages（课后反馈训练内容图片 JSON）字段，
         * 支持教练在课后反馈 Tab 添加图片便于反馈给家长。
         * - contentImages TEXT NOT NULL DEFAULT '[]'
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lessons ADD COLUMN contentImages TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /**
         * v15 → v16：lessons 表新增 5 个索引，覆盖所有高频查询路径。
         *
         * 索引设计依据（按查询频率从高到低）：
         * - idx_lessons_date：首页今日课时、统计计数（最高频）
         * - idx_lessons_student_date_time：学员详情历史列表
         * - idx_lessons_date_time_asc：学员列表"下一节课"查询
         * - idx_lessons_student_date_time_unique：长期排课查重（唯一索引，防重复插入）
         * - idx_lessons_student_date_pkg：长期排课课时包余额计算
         *
         * 全部使用 IF NOT EXISTS，确保重复迁移不会报错。
         * 不修改任何表结构，不删除任何数据，仅加速查询。
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_lessons_student_date_time ON lessons(studentName, date, time)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_lessons_date ON lessons(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_lessons_date_time_asc ON lessons(date, time)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_lessons_student_date_time_unique ON lessons(studentName, date, time)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_lessons_student_date_pkg ON lessons(studentName, date, packageId)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sports_coach_db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                    // 仅在降级（用户从高版本回滚到低版本）时清库重建；
                    // 升级路径必须通过显式 Migration 完成，避免迁移失败时误删学员数据。
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * 关闭并重置数据库单例（仅用于整库备份/恢复流程）。
         *
         * 调用时机：
         * - 备份：在复制数据库文件前调用，确保所有 WAL 日志刷盘，避免备份到不完整的数据
         * - 恢复：在覆盖数据库文件前调用，释放文件锁，避免 "database is locked" 错误
         *
         * 调用后下次访问 [getDatabase] 会重新创建实例并打开数据库连接。
         * 注意：调用后所有持有旧 Dao / Repository 引用的 ViewModel 都会失效，
         * 恢复数据后必须重启 App 让 ViewModel 重新初始化。
         *
         * @param context 上下文（用于触发 WAL checkpoint）
         */
        fun closeAndResetInstance(context: Context) {
            synchronized(this) {
                INSTANCE?.let { db ->
                    try {
                        // 强制将 WAL 日志写入主数据库文件，确保备份/覆盖的是完整数据
                        // 使用 raw query 触发 checkpoint，避免备份到残缺数据导致学员/课程丢失
                        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
                    } catch (e: Exception) {
                        // checkpoint 失败不阻断流程，仍尝试关闭，避免文件锁死
                    }
                    try {
                        db.close()
                    } catch (e: Exception) {
                        // 忽略关闭异常，单例仍需重置
                    }
                }
                INSTANCE = null
            }
        }

        /**
         * 数据库文件名（用于备份/恢复定位文件）。
         * Room 默认会在 databasePath 下生成 <dbName>、<dbName>-wal、<dbName>-shm 三个文件。
         */
        const val DATABASE_NAME = "sports_coach_db"
    }
}
