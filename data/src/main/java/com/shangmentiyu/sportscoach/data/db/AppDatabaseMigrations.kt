package com.shangmentiyu.sportscoach.data.db

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库全部 schema 迁移定义（v4 -> v32），按版本升序排列。
 *
 * 从 [AppDatabase] 拆出（单一职责）：本文件只负责 schema 演进历史，
 * [AppDatabase] 只负责 DAO 声明与单例生命周期管理。
 * 迁移 SQL 逐字保留，行为与拆分前完全一致。
 */
internal object AppDatabaseMigrations {
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

        /**
         * v16 → v17：students 表新增身高遗传潜力与后天预测字段。
         *
         * 新增 5 列，全部带默认值，兼容老数据：
         * - fatherHeight      REAL    NOT NULL DEFAULT 0.0  父亲身高
         * - motherHeight      REAL    NOT NULL DEFAULT 0.0  母亲身高
         * - avgSleepHours     REAL    NOT NULL DEFAULT 0.0  日常平均睡眠小时
         * - nutritionScore    INTEGER NOT NULL DEFAULT 0    营养均衡评分(1-5)
         * - sportsMinsPerWeek INTEGER NOT NULL DEFAULT 0    每周运动总时长(分钟)
         */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE students ADD COLUMN fatherHeight REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE students ADD COLUMN motherHeight REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE students ADD COLUMN avgSleepHours REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE students ADD COLUMN nutritionScore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE students ADD COLUMN sportsMinsPerWeek INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v17 → v18：新增饮食管理两张表。
         *
         * - diet_templates：3+2 饮食法模板表（预置 3 套模板，应用启动时通过 callback 插入）
         * - student_diet_records：学员饮食绑定记录表（一人一方案，最新绑定覆盖旧绑定）
         *
         * 模板表的预置数据在 [RoomDatabase.Callback.onCreate] 中插入，
         * 仅在数据库首次创建时执行，老用户升级时不会重复插入（已用 IGNORE 冲突策略保护）。
         */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS diet_templates (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        breakfast TEXT NOT NULL,
                        morningSnack TEXT NOT NULL,
                        lunch TEXT NOT NULL,
                        afternoonSnack TEXT NOT NULL,
                        dinner TEXT NOT NULL,
                        preWorkoutTip TEXT NOT NULL DEFAULT '',
                        postWorkoutTip TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS student_diet_records (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        studentName TEXT NOT NULL,
                        templateId TEXT NOT NULL,
                        templateName TEXT NOT NULL,
                        breakfastNote TEXT NOT NULL DEFAULT '',
                        morningSnackNote TEXT NOT NULL DEFAULT '',
                        lunchNote TEXT NOT NULL DEFAULT '',
                        afternoonSnackNote TEXT NOT NULL DEFAULT '',
                        dinnerNote TEXT NOT NULL DEFAULT '',
                        appliedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                // 迁移时也插入预置模板（老用户升级路径）
                insertPresetTemplates(db)
            }
        }

        /**
         * v18 → v19：student_diet_records 表新增 5 个自定义餐次食材字段。
         *
         * 新增 5 列，全部带默认空串，兼容老数据：
         * - breakfastMeals      TEXT NOT NULL DEFAULT ''  早餐自定义食材 JSON
         * - morningSnackMeals   TEXT NOT NULL DEFAULT ''  上午加餐自定义食材 JSON
         * - lunchMeals          TEXT NOT NULL DEFAULT ''  午餐自定义食材 JSON
         * - afternoonSnackMeals TEXT NOT NULL DEFAULT ''  下午加餐自定义食材 JSON
         * - dinnerMeals         TEXT NOT NULL DEFAULT ''  晚餐自定义食材 JSON
         *
         * 空串语义：使用模板默认食材；非空串：教练已覆写为自定义内容。
         */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE student_diet_records ADD COLUMN breakfastMeals TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE student_diet_records ADD COLUMN morningSnackMeals TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE student_diet_records ADD COLUMN lunchMeals TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE student_diet_records ADD COLUMN afternoonSnackMeals TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE student_diet_records ADD COLUMN dinnerMeals TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v19 → v20：软删除标志 + 软关联外键（「软关联+改名事务」策略）。
         *
         * 改动范围：
         * - students 表新增 2 列：
         *   - isActive INTEGER NOT NULL DEFAULT 1（软删除标志，1=活跃，0=已删除）
         *   - studentId TEXT（软关联外键，NULL=旧数据未生成，由业务层后续回填）
         *
         * - 7 张子表各新增 1 列：
         *   - lessons / schedules / lesson_packages / training_cycles /
         *     body_metric_history / parent_reports / student_diet_records
         *   - 均新增 studentId TEXT（NULL=旧数据）
         *
         * 设计要点：
         * 1. 不建立物理外键约束，避免 CASCADE 误删历史数据；
         * 2. 保留 studentName 字段不动，UI 显示与历史报表不受影响；
         * 3. studentId 初始填充为 NULL，旧数据不会因升级而崩溃；
         * 4. 业务层通过 [StudentRepository.backfillStudentIds] 在应用启动后
         *    后台扫描，逐步将 NULL 的 studentId 升级为唯一 UUID。
         *
         * SQLite 注意：
         * - ALTER TABLE ADD COLUMN 不能加 NOT NULL 约束除非有 DEFAULT；
         * - 可空列不加 NOT NULL，老数据自动为 NULL；
         * - Boolean 在 SQLite 中存储为 INTEGER（0/1）。
         */
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // === students 表：新增软删除标志 + 软关联外键 ===
                db.execSQL("ALTER TABLE students ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE students ADD COLUMN studentId TEXT")

                // === 7 张子表：新增软关联外键 studentId（NULL=旧数据，由业务层后续回填） ===
                db.execSQL("ALTER TABLE lessons ADD COLUMN studentId TEXT")
                db.execSQL("ALTER TABLE schedules ADD COLUMN studentId TEXT")
                db.execSQL("ALTER TABLE lesson_packages ADD COLUMN studentId TEXT")
                db.execSQL("ALTER TABLE training_cycles ADD COLUMN studentId TEXT")
                db.execSQL("ALTER TABLE body_metric_history ADD COLUMN studentId TEXT")
                db.execSQL("ALTER TABLE parent_reports ADD COLUMN studentId TEXT")
                db.execSQL("ALTER TABLE student_diet_records ADD COLUMN studentId TEXT")
            }
        }

        /**
         * v20 → v21：新增 studentFts FTS4 虚拟表，用于学员全文检索。
         *
         * 设计说明：
         * - `@Fts4(contentEntity = Student::class)` 让 Room 在新装用户场景下自动生成
         *   FTS4 外部内容表 + INSERT/UPDATE/DELETE 触发器，保持索引与主表同步。
         * - 但老用户升级路径下 Room 不会自动建触发器，必须在本 Migration 中手动创建，
         *   触发器 SQL 必须与 Room 自动生成的格式严格一致，否则后续 schema 校验会失败。
         * - 索引字段：name / school / phone（与 [StudentFts] 实体定义保持一致）。
         *
         * 触发器约定（与 Room 自动生成格式对齐）：
         * - room_data_create_studentFts_BEFORE_UPDATE / _AFTER_UPDATE / _AFTER_INSERT / _AFTER_DELETE
         * - 仅索引主表数据，不冗余存储；查询时通过 rowid 关联 students。
         *
         * 末尾执行 `INSERT INTO studentFts(studentFts) VALUES('rebuild')`
         * 基于现有 students 数据一次性回填索引，确保老用户升级后立即可用搜索。
         */
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 创建 FTS4 外部内容表（content 表指向 students 主表）
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS `studentFts` USING FTS4(
                        `name` TEXT NOT NULL,
                        `school` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        content=`students`
                    )
                    """.trimIndent()
                )

                // 2. 创建同步触发器：students 表增删改时自动维护 FTS 索引
                //    （触发器命名遵循 Room 自动生成约定，确保 schema 验证通过）
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_studentFts_BEFORE_UPDATE
                    BEFORE UPDATE ON `students`
                    BEGIN
                        DELETE FROM `studentFts` WHERE `docid`=OLD.`rowid`;
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_studentFts_BEFORE_DELETE
                    BEFORE DELETE ON `students`
                    BEGIN
                        DELETE FROM `studentFts` WHERE `docid`=OLD.`rowid`;
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_studentFts_AFTER_UPDATE
                    AFTER UPDATE ON `students`
                    BEGIN
                        INSERT INTO `studentFts`(`rowid`, `name`, `school`, `phone`)
                        VALUES (NEW.`rowid`, NEW.`name`, NEW.`school`, NEW.`phone`);
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_studentFts_AFTER_INSERT
                    AFTER INSERT ON `students`
                    BEGIN
                        INSERT INTO `studentFts`(`rowid`, `name`, `school`, `phone`)
                        VALUES (NEW.`rowid`, NEW.`name`, NEW.`school`, NEW.`phone`);
                    END
                    """.trimIndent()
                )

                // 3. 基于现有 students 数据一次性回填 FTS 索引
                //    （外部内容表的标准重建命令，会扫描 content 表全量重建）
                db.execSQL("INSERT INTO `studentFts`(`studentFts`) VALUES('rebuild')")
            }
        }

        /**
         * v21 → v22：新增 archived_lessons 表（冷数据归档表）。
         *
         * 设计说明：
         * - 字段与 lessons 表完全一致，仅表名不同 + 多一个 archivedAt 字段
         * - 用于冷热数据归档策略：一年前的课时记录从 lessons 表迁移到本表
         * - 主表 lessons 仅保留近一年数据，索引体积下降，首页/学员详情查询显著加速
         *
         * 索引设计（与 lessons 表对齐，便于归档后按学员/日期查询）：
         * - idx_archived_lessons_student_date：按学员+日期归档查询
         * - idx_archived_lessons_date：按日期归档查询
         *
         * 注意：本迁移仅建表 + 建索引，不迁移任何数据。
         * 数据迁移由业务层 [com.shangmentiyu.sportscoach.data.repo.OperationRepository.archiveLessonsBefore]
         * 在用户主动触发"归档一年前记录"时执行。
         */
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS archived_lessons (
                        id TEXT NOT NULL PRIMARY KEY,
                        date TEXT NOT NULL,
                        time TEXT NOT NULL,
                        studentName TEXT NOT NULL,
                        studentId TEXT,
                        content TEXT NOT NULL DEFAULT '[]',
                        scores TEXT NOT NULL DEFAULT '{}',
                        summary TEXT NOT NULL DEFAULT '',
                        duration INTEGER NOT NULL DEFAULT 60,
                        coach TEXT NOT NULL DEFAULT '',
                        location TEXT NOT NULL DEFAULT '',
                        lessonType TEXT NOT NULL DEFAULT '训练课',
                        attendance TEXT NOT NULL DEFAULT '准时',
                        attitude TEXT NOT NULL DEFAULT '认真',
                        performance INTEGER NOT NULL DEFAULT 7,
                        nextGoal TEXT NOT NULL DEFAULT '',
                        coachComment TEXT NOT NULL DEFAULT '',
                        packageId TEXT NOT NULL DEFAULT '',
                        photoPath TEXT NOT NULL DEFAULT '',
                        signOutTime TEXT NOT NULL DEFAULT '',
                        signOutPhotoPath TEXT NOT NULL DEFAULT '',
                        contentImages TEXT NOT NULL DEFAULT '[]',
                        status TEXT NOT NULL DEFAULT '已签到',
                        archivedAt INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_archived_lessons_student_date " +
                        "ON archived_lessons(studentName, date)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_archived_lessons_date " +
                        "ON archived_lessons(date)"
                )
            }
        }

        /**
         * v22 → v23：新增 audit_logs 表（操作日志审计表）。
         *
         * 设计说明：
         * - 用于记录关键增删改动作（新增/修改/删除 学员/课时包/排课等）
         * - 字段含 operator（操作人）/ action（操作类型）/ targetStudent（受影响学员）/
         *   beforeJson / afterJson / summary / createdAt
         * - 索引 idx_audit_log_created_at 支持按时间倒序查询（主查询路径）
         * - 索引 idx_audit_log_target_student 支持按学员过滤
         *
         * 注意：本迁移仅建表 + 建索引，不迁移任何业务数据。
         * 日志由业务层 [com.shangmentiyu.sportscoach.data.repo.AuditLogRepository.log]
         * 在用户操作时增量写入。
         */
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS audit_logs (
                        id TEXT NOT NULL PRIMARY KEY,
                        operator TEXT NOT NULL DEFAULT '教练',
                        action TEXT NOT NULL,
                        targetStudent TEXT NOT NULL DEFAULT '',
                        beforeJson TEXT NOT NULL DEFAULT '',
                        afterJson TEXT NOT NULL DEFAULT '',
                        summary TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_logs(createdAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_audit_log_target_student ON audit_logs(targetStudent)"
                )
            }
        }

        /**
         * v23 → v24：lessons 表新增 status 字段，标记课时签到/签退状态。
         *
         * 业务背景：
         * - 重构签到消课逻辑为"签退后消耗课时"
         * - 签到时仅创建 Lesson 记录，status = "已签到"，packageId = ""
         * - 签退时事务内扣减课时包 + 更新 status = "已签退" + 写入 signOutTime + packageId
         * - 余额计算时区分"已签到未签退"（pendingCheckOut）与"未消费"两种状态
         *
         * 老数据处理：
         * - 已有 Lesson 全部标记为 "已签到"（默认值）
         * - 已有 signOutTime 非空的记录在下次签退流程时会自动迁移为 "已签退"
         * - 不影响历史报表与已扣减课时包数据
         *
         * 字段定义：
         * - status TEXT NOT NULL DEFAULT '已签到'
         */
        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lessons ADD COLUMN status TEXT NOT NULL DEFAULT '已签到'")
            }
        }

        /**
         * v24 → v25：新增 student_plan_images 表（电脑端训练计划截图同步）。
         *
         * 业务背景：
         * - 桌面端 PySide6 截图训练计划页面 → 局域网 HTTP 服务 → LanImageReceiver 下载
         * - 文件名格式：{学员姓名}_{YYYYMMDD}_plan.png
         * - 解析文件名提取学员姓名，软关联到 students 表（不强外键，避免删学员时 CASCADE 误删）
         *
         * 表结构：
         * - id            TEXT    PRIMARY KEY（UUID 前 8 位）
         * - studentName   TEXT    NOT NULL（软关联，保留兼容旧数据）
         * - imagePath     TEXT    NOT NULL（filesDir/ImportedPlans/xxx.png 绝对路径）
         * - sourceHost    TEXT    NOT NULL DEFAULT ''（来源 PC 的 IP，溯源用）
         * - originalFilename TEXT NOT NULL DEFAULT ''（原始文件名）
         * - note          TEXT    NOT NULL DEFAULT ''（教练可手动补充备注）
         * - createdAt     INTEGER NOT NULL DEFAULT 0（毫秒时间戳）
         *
         * 索引设计（与 PlanImageEntity @Index 对齐）：
         * - idx_plan_images_student：按学员姓名查询（学员详情画廊主查询路径）
         * - idx_plan_images_created：按时间倒序查询（"今日同步的训练计划"列表）
         *
         * 注意：本迁移仅建表 + 建索引，不迁移任何业务数据。
         * 数据由 [com.shangmentiyu.sportscoach.core.LanImageReceiver] 在用户主动同步时增量写入。
         */
        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS student_plan_images (
                        id TEXT NOT NULL PRIMARY KEY,
                        studentName TEXT NOT NULL,
                        imagePath TEXT NOT NULL,
                        sourceHost TEXT NOT NULL DEFAULT '',
                        originalFilename TEXT NOT NULL DEFAULT '',
                        note TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_plan_images_student ON student_plan_images(studentName)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_plan_images_created ON student_plan_images(createdAt)"
                )
            }
        }

        /**
         * === v29 修复：v25 → v26 重建 archived_lessons 表，移除列的 DEFAULT 值 ===
         *
         * 根因1（崩溃堆栈 java.lang.IllegalStateException: Migration didn't properly handle:
         * archived_lessons）：
         * - v21→v22 迁移用 `CREATE TABLE archived_lessons (... DEFAULT '[]', DEFAULT '{}',
         *   DEFAULT '准时', DEFAULT 60, DEFAULT 7, DEFAULT 0, ...)` 建表
         * - 但 [ArchivedLesson] Entity 字段未使用 `@ColumnInfo(defaultValue = ...)`
         * - Room schema 校验：Expected 列 defaultValue='undefined'（无默认值），
         *   Found 列 defaultValue='0'/'[]'/'准时' 等，两者不匹配抛 IllegalStateException
         * - 触发时机：[OperationRepository.maybeAutoArchiveIfNeeded] 首次访问该表时崩溃
         *
         * 根因2（崩溃堆栈 SQLiteException: no such column: status）：
         * - 线上用户实际 archived_lessons 表 schema 与代码声明不一致：缺少 status 列
         * - 推测成因：历史版本恢复/数据迁移导致 archived_lessons 表结构残缺
         * - 原 INSERT INTO ... SELECT status FROM archived_lessons 会因列缺失而崩溃
         *
         * 修复策略：
         * - SQLite 不支持 ALTER 修改列的 DEFAULT，必须重建表
         * - 创建无 DEFAULT 的新表 → 动态检测旧表列 → INSERT INTO ... SELECT 复制旧数据 →
         *   DROP 旧表 → RENAME 新表 → 重建索引
         * - 对缺失的 status 列用静态值 '已签到' 填充，避免 SELECT 引用不存在的列
         *
         * 数据安全：通过 INSERT INTO ... SELECT 保留全部归档记录，索引名保持一致。
         */
        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 动态检测旧表实际拥有的列，避免 SELECT 引用不存在的列而崩溃
                //    修复线上数据库 archived_lessons 表结构残缺（缺 status 列）的问题
                val oldColumns = mutableSetOf<String>()
                db.query("PRAGMA table_info(archived_lessons)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    if (nameIdx >= 0) {
                        while (cursor.moveToNext()) {
                            oldColumns.add(cursor.getString(nameIdx))
                        }
                    }
                }
                // 旧表不存在时直接建新表，避免后续 SQL 失败
                val hasOldTable = oldColumns.isNotEmpty()
                // 对缺失列提供回退值（与 Entity 默认值一致）
                val statusExpr = if (oldColumns.contains("status")) "status" else "'已签到'"
                val archivedAtExpr = if (oldColumns.contains("archivedAt")) "archivedAt" else "0"
                val createdAtExpr = if (oldColumns.contains("createdAt")) "createdAt" else "0"

                // 2. 创建新表（与 Entity 字段定义完全一致，无 DEFAULT 值）
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS archived_lessons_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        date TEXT NOT NULL,
                        time TEXT NOT NULL,
                        studentName TEXT NOT NULL,
                        studentId TEXT,
                        content TEXT NOT NULL,
                        scores TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        coach TEXT NOT NULL,
                        location TEXT NOT NULL,
                        lessonType TEXT NOT NULL,
                        attendance TEXT NOT NULL,
                        attitude TEXT NOT NULL,
                        performance INTEGER NOT NULL,
                        nextGoal TEXT NOT NULL,
                        coachComment TEXT NOT NULL,
                        packageId TEXT NOT NULL,
                        photoPath TEXT NOT NULL,
                        signOutTime TEXT NOT NULL,
                        signOutPhotoPath TEXT NOT NULL,
                        contentImages TEXT NOT NULL,
                        status TEXT NOT NULL,
                        archivedAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                // 3. 复制旧表数据到新表（动态适配旧表实际列结构）
                if (hasOldTable) {
                    db.execSQL(
                        """
                        INSERT INTO archived_lessons_new (
                            id, date, time, studentName, studentId, content, scores, summary,
                            duration, coach, location, lessonType, attendance, attitude,
                            performance, nextGoal, coachComment, packageId, photoPath,
                            signOutTime, signOutPhotoPath, contentImages, status,
                            archivedAt, createdAt
                        )
                        SELECT
                            id, date, time, studentName, studentId, content, scores, summary,
                            duration, coach, location, lessonType, attendance, attitude,
                            performance, nextGoal, coachComment, packageId, photoPath,
                            signOutTime, signOutPhotoPath, contentImages, $statusExpr,
                            $archivedAtExpr, $createdAtExpr
                        FROM archived_lessons
                        """.trimIndent()
                    )
                    // 4. 删除旧表（索引会随之自动删除）
                    db.execSQL("DROP TABLE archived_lessons")
                }
                // 5. 重命名新表为正式表名
                db.execSQL("ALTER TABLE archived_lessons_new RENAME TO archived_lessons")
                // 6. 重建索引（与原索引名一致，保持查询计划稳定）
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_archived_lessons_student_date " +
                        "ON archived_lessons(studentName, date)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_archived_lessons_date " +
                        "ON archived_lessons(date)"
                )
            }
        }

        /**
         * 向 diet_templates 表插入预置 3 套模板。
         *
         * 使用 INSERT OR IGNORE，重复插入不会报错，保证幂等。
         * 同时被 [MIGRATION_17_18] 和 [RoomDatabase.Callback.onCreate] 调用，
         * 确保新装用户与老用户升级用户都能拿到模板数据。
         */
        private fun insertPresetTemplates(db: SupportSQLiteDatabase) {
            val templates = DietTemplatePreset.all()
            templates.forEach { t ->
                // 使用 SQLite 原生 SQL 插入，避免在 Migration 中调用 DAO
                val cv = android.content.ContentValues().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("description", t.description)
                    put("breakfast", t.breakfast)
                    put("morningSnack", t.morningSnack)
                    put("lunch", t.lunch)
                    put("afternoonSnack", t.afternoonSnack)
                    put("dinner", t.dinner)
                    put("preWorkoutTip", t.preWorkoutTip)
                    put("postWorkoutTip", t.postWorkoutTip)
                    put("createdAt", t.createdAt)
                }
                db.insert("diet_templates", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, cv)
            }
        }

        /**
         * v26 → v27：为 lessons 和 schedules 表添加查询索引（任务2 性能调优）。
         *
         * 新增索引：
         * - lessons.studentId（单列）→ idx_lessons_student_id
         * - lessons.studentId + date（复合）→ idx_lessons_student_id_date
         * - schedules.studentId → idx_schedules_student_id
         * - schedules.studentName → idx_schedules_student_name
         * - schedules.startDate → idx_schedules_start_date
         * - schedules.dayOfWeek → idx_schedules_day_of_week
         * - schedules.studentId + dayOfWeek → idx_schedules_student_day
         *
         * 注意：CREATE INDEX IF NOT EXISTS 保证幂等，重复执行不报错。
         */
        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // lessons 表新增索引
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_lessons_student_id ON lessons(studentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_lessons_student_id_date ON lessons(studentId, date)")
                // schedules 表新增索引
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedules_student_id ON schedules(studentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedules_student_name ON schedules(studentName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedules_start_date ON schedules(startDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedules_day_of_week ON schedules(dayOfWeek)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedules_student_day ON schedules(studentId, dayOfWeek)")
            }
        }

        /**
         * === v48：student_plan_images 表新增 studentId 列（双通道软关联）===
         * ALTER TABLE 不会破坏数据；新列默认 NULL，旧数据靠 studentName 兜底。
         * 索引用 IF NOT EXISTS 保证幂等。
         */
        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE student_plan_images ADD COLUMN studentId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_plan_images_student_id ON student_plan_images(studentId)")
            }
        }

        /**
         * === v49：体验课支持（schedules / lessons 表新增 isTrial 列）===
         *
         * - isTrial：0=常规排课，1=体验课（未注册学员临时体验课，不消耗课时包余额）
         * - INTEGER NOT NULL DEFAULT 0 保证存量数据自动归为常规排课，无需数据迁移
         * - studentId 列本就可空（v20 起），体验课写入 NULL + studentName 存临时姓名，
         *   无需修改列约束
         */
        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN isTrial INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE lessons ADD COLUMN isTrial INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * === v51 数据流加固：students.studentId 唯一索引 ===
         *
         * studentId 是学员软关联唯一键，此前由 UUID 前缀生成但无数据库唯一约束，
         * 理论上存在碰撞风险（迁移/回填/导入路径均可能产生重复 ID）。
         *
         * 迁移步骤：
         * 1. 先为历史重复 studentId 重新生成 48bit 随机 ID（保留每组 rowid 最小的行，
         *    即"归并去重"），避免 CREATE UNIQUE INDEX 因重复值抛出 SQLiteConstraintException；
         *    hex(randomblob(6)) 生成 12 位十六进制，与历史 8 位 ID 长度不同，不可能碰撞。
         * 2. 创建唯一索引（IF NOT EXISTS 幂等）。
         *    SQLite 唯一索引允许多个 NULL，历史 studentId=NULL 行不受影响。
         *
         * ⚠️ 一致性要求：该索引已在 [com.shangmentiyu.sportscoach.data.model.Student]
         * 实体的 @Entity(indices=...) 中同步声明（unique=true，同名）。
         * 迁移创建 + 实体声明必须同时存在，否则 Room 迁移后的 schema 校验
         * （TableInfo 索引集合精确比对）会因索引不匹配抛 IllegalStateException
         * 导致 App 启动闪退。
         */
        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE students SET studentId = hex(randomblob(6))
                    WHERE studentId IS NOT NULL
                      AND rowid NOT IN (
                          SELECT MIN(rowid) FROM students
                          WHERE studentId IS NOT NULL
                          GROUP BY studentId
                      )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_students_student_id ON students(studentId)")
            }
        }

        /**
         * v30 → v31：小班课支持。
         * - schedules 表新增 groupScheduleId 字段（TEXT，可空，默认 NULL）
         * - lessons 表新增 groupScheduleId 字段（TEXT，可空，默认 NULL）
         */
        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN groupScheduleId TEXT")
                db.execSQL("ALTER TABLE lessons ADD COLUMN groupScheduleId TEXT")
            }
        }

        /**
         * v31 → v32：排课与签到分离。
         * - 新增 sign_in_records 表（签到/签退操作留痕 + 防重唯一索引）
         * - lesson_packages 表新增 paidAmount 字段（实收金额，-1 = 未记录视同已付清）
         */
        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sign_in_records (
                        id TEXT NOT NULL PRIMARY KEY,
                        studentName TEXT NOT NULL,
                        studentId TEXT,
                        lessonId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        operator TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_sign_in_records_student_lesson_type " +
                        "ON sign_in_records(studentName, lessonId, type)"
                )
                db.execSQL("ALTER TABLE lesson_packages ADD COLUMN paidAmount REAL NOT NULL DEFAULT -1.0")
            }
        }

        /**
         * v32 → v33：教练管理模块。
         * - coaches 表新增角色 / 上级 / 证书 / 薪资参数 / 日排课上限字段
         * - schedules 表新增 assistantCoach（助教）字段
         * - 新增 coach_schedules（可上课时段）/ coach_payouts（薪资结算）/
         *   coach_payout_requests（提现申请）三张表
         */
        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coaches ADD COLUMN role TEXT NOT NULL DEFAULT 'parttime'")
                db.execSQL("ALTER TABLE coaches ADD COLUMN superiorId TEXT")
                db.execSQL("ALTER TABLE coaches ADD COLUMN certificates TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE coaches ADD COLUMN baseSalary REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE coaches ADD COLUMN lessonRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE coaches ADD COLUMN commissionRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE coaches ADD COLUMN dailyLimit INTEGER NOT NULL DEFAULT 8")
                db.execSQL("ALTER TABLE schedules ADD COLUMN assistantCoach TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS coach_schedules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        coachName TEXT NOT NULL,
                        dayOfWeek INTEGER NOT NULL,
                        startTime TEXT NOT NULL,
                        endTime TEXT NOT NULL,
                        note TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_coach_schedules_coach " +
                        "ON coach_schedules(coachName)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS coach_payouts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        coachName TEXT NOT NULL,
                        periodStart TEXT NOT NULL,
                        periodEnd TEXT NOT NULL,
                        lessonCount INTEGER NOT NULL,
                        baseAmount REAL NOT NULL,
                        lessonFee REAL NOT NULL,
                        commission REAL NOT NULL,
                        totalAmount REAL NOT NULL,
                        status TEXT NOT NULL DEFAULT '待发放',
                        note TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_coach_payouts_period " +
                        "ON coach_payouts(coachName, periodStart, periodEnd)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS coach_payout_requests (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        coachName TEXT NOT NULL,
                        amount REAL NOT NULL,
                        status TEXT NOT NULL DEFAULT '待审核',
                        appliedAt INTEGER NOT NULL DEFAULT 0,
                        processedAt INTEGER NOT NULL DEFAULT 0,
                        note TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v33 → v34：教练管理模块增强（教练绑定学员）。
         * - 新增 coach_student_bindings（教练-学员绑定）表
         */
        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS coach_student_bindings (
                        coachName TEXT NOT NULL,
                        studentName TEXT NOT NULL,
                        studentId TEXT,
                        boundAt INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(coachName, studentName)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_coach_student_coach " +
                        "ON coach_student_bindings(coachName)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_coach_student_student " +
                        "ON coach_student_bindings(studentName)"
                )
            }
        }

        /**
         * 数据库首次创建时的回调：插入预置饮食模板数据。
         *
         * 仅在数据库文件首次创建时触发（新装用户），老用户升级走 [MIGRATION_17_18]。
         */
        internal val DB_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                insertPresetTemplates(db)
            }
        }

    /** 全部迁移，按版本升序；顺序即 Room addMigrations 的注册顺序，不可打乱。 */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
        MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
        MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24,
        MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29,
        MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34
    )
}
