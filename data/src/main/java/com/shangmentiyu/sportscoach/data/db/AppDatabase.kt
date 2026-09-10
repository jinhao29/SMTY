package com.shangmentiyu.sportscoach.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.shangmentiyu.sportscoach.data.model.ArchivedLesson
import com.shangmentiyu.sportscoach.data.model.AuditLogEntity
import com.shangmentiyu.sportscoach.data.model.BodyMetricHistory
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.CoachPayout
import com.shangmentiyu.sportscoach.data.model.CoachPayoutRequest
import com.shangmentiyu.sportscoach.data.model.CoachSchedule
import com.shangmentiyu.sportscoach.data.model.CoachStudentBinding
import com.shangmentiyu.sportscoach.data.model.DietTemplateEntity
import com.shangmentiyu.sportscoach.data.model.FeeRecord
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.ParentReport
import com.shangmentiyu.sportscoach.data.model.PcSyncState
// PlanImage 仍被 PreClassScheduleCard / PreClassTab 引用（电脑端训练计划截图展示），
// 并非死代码，保留实体与 DAO。
import com.shangmentiyu.sportscoach.data.model.PlanImage
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.model.ScheduleMemory
import com.shangmentiyu.sportscoach.data.model.SignInRecord
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.model.StudentDietRecord
import com.shangmentiyu.sportscoach.data.model.StudentFts
import com.shangmentiyu.sportscoach.data.model.TrainingCycle

@Database(
    entities = [Student::class, Lesson::class, LessonPackage::class, Coach::class, Schedule::class, ParentReport::class, TrainingCycle::class, BodyMetricHistory::class, ScheduleMemory::class, DietTemplateEntity::class, StudentDietRecord::class, StudentFts::class, ArchivedLesson::class, AuditLogEntity::class, PlanImage::class, SignInRecord::class, CoachSchedule::class, CoachPayout::class, CoachPayoutRequest::class, CoachStudentBinding::class, FeeRecord::class, PcSyncState::class],
    version = 35,
    exportSchema = true
)
@TypeConverters(com.shangmentiyu.sportscoach.data.model.Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun studentFtsDao(): StudentFtsDao
    abstract fun lessonDao(): LessonDao
    abstract fun lessonPackageDao(): LessonPackageDao
    abstract fun coachDao(): CoachDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun parentReportDao(): ParentReportDao
    abstract fun trainingCycleDao(): TrainingCycleDao
    abstract fun bodyMetricHistoryDao(): BodyMetricHistoryDao
    abstract fun scheduleMemoryDao(): ScheduleMemoryDao
    abstract fun dietDao(): DietDao
    /** v22 新增：归档课时 DAO（冷数据访问） */
    abstract fun archivedLessonDao(): ArchivedLessonDao
    /** v26 优化1 新增：操作日志 DAO（审计溯源） */
    abstract fun auditLogDao(): AuditLogDao
    /** v25 新增：训练计划图片 DAO（电脑端截图推送，PreClassScheduleCard 展示） */
    abstract fun planImageDao(): PlanImageDao
    /** v32 新增：签到记录 DAO（排课与签到分离 + 防重） */
    abstract fun signInDao(): SignInDao
    /** v33 新增：教练管理模块 DAO（可上课时段 / 薪资结算 / 提现申请 / 工作量统计） */
    abstract fun coachScheduleDao(): CoachScheduleDao
    abstract fun coachPayoutDao(): CoachPayoutDao
    abstract fun coachPayoutRequestDao(): CoachPayoutRequestDao
    abstract fun coachWorkloadDao(): CoachWorkloadDao
    /** v34 新增：教练-学员绑定 DAO */
    abstract fun coachStudentBindingDao(): CoachStudentBindingDao
    /** v35 新增：PC 收费记录镜像 + PC 消课对账状态 DAO（双端数据真统一） */
    abstract fun feeRecordDao(): FeeRecordDao
    abstract fun pcSyncStateDao(): PcSyncStateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * SQLCipher 原生库是否已加载（v1.0.5）。
         *
         * net.zetetic 4.17.0 起初始化方式改为 System.loadLibrary("sqlcipher")，
         * 旧的 net.sqlcipher.database.SQLiteDatabase.loadLibs(context) 已废弃。
         * 只需加载一次，用 @Volatile 标记避免重复 dlopen 开销。
         */
        @Volatile
        private var sqlcipherLoaded = false

        /**
         * 是否启用数据库文件加密（v1.0.5）。
         *
         * ⚠️ **仅供单元测试关闭**：SQLCipher 依赖 native 库（libsqlcipher.so），
         * Robolectric 跑在桌面 JVM 上无法加载 Android ABI 的 .so，
         * 所有走 [getDatabase] 的既有测试都会抛 UnsatisfiedLinkError。
         *
         * 生产代码路径**不提供**任何关闭入口——默认恒为 true，
         * 只有测试通过 [devDisableEncryptionForTesting] 显式关闭。
         */
        @Volatile
        private var encryptionEnabled = true

        /**
         * 关闭数据库加密（**仅限单元测试**）。
         *
         * 调用后 [getDatabase] 走原生 SQLite，用于让既有 Room 测试在 JVM 上继续可跑。
         * 真机上加密行为不受影响（不会有生产代码调用此方法）。
         */
        @androidx.annotation.VisibleForTesting
        fun devDisableEncryptionForTesting() {
            encryptionEnabled = false
            // 同步关闭裸连接工具的加密路径：否则 BackupManager.verifyIntegrity 等
            // 仍会调 System.loadLibrary("sqlcipher") 在 JVM 上抛 UnsatisfiedLinkError
            com.shangmentiyu.sportscoach.data.internal.EncryptedDbOpener.useNativeForTesting = true
        }

        /** 恢复加密开关为默认开启（测试 tearDown 用，避免静态状态跨用例泄漏） */
        @androidx.annotation.VisibleForTesting
        fun devResetEncryptionForTesting() {
            encryptionEnabled = true
            com.shangmentiyu.sportscoach.data.internal.EncryptedDbOpener.useNativeForTesting = false
        }

        private fun ensureSqlcipherLoaded() {
            if (sqlcipherLoaded) return
            synchronized(this) {
                if (sqlcipherLoaded) return
                // ⚠️ loadLibrary 失败抛 UnsatisfiedLinkError（Error，非 Exception）。
                // 这里收敛为带可读文案的 IllegalStateException，避免用户只看到一行
                // 原生链接错误的堆栈（与 PlainDbMigrator / EncryptedDbOpener 同一策略）。
                try {
                    System.loadLibrary("sqlcipher")
                } catch (e: Throwable) {
                    throw IllegalStateException(
                        "数据库加密组件加载失败（当前设备 ABI 不受支持）：${e.message}", e
                    )
                }
                sqlcipherLoaded = true
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // === 终极防丢机制：打开数据库前先做启动前避风港备份 + 版本检查 ===
                // 必须在 Room.databaseBuilder().build() 之前执行：
                // 1. backupIfDbExists：复制当前 db 文件到 filesDir/PreUpdateBackup/，保留最近 3 份
                // 2. checkVersionAndEmergencyBackup：若 db 文件版本 > 代码版本（降级场景），
                //    生成急救备份并抛 RuntimeException 让 App 闪退，避免 Room 清库
                // 这两步确保即使后续 Room 打开失败，也有一份"启动前"的完整数据库可恢复
                com.shangmentiyu.sportscoach.data.internal.PreUpdateBackupManager
                    .backupIfDbExists(context.applicationContext)
                com.shangmentiyu.sportscoach.data.internal.PreUpdateBackupManager
                    .checkVersionAndEmergencyBackup(context.applicationContext, DATABASE_VERSION)

                // === v1.0.5 数据库文件加密（SQLCipher）===
                // 1. 明文库 → 加密库一次性迁移（必须在 Room 打开之前完成，
                //    否则 SQLCipher 打不开明文库会报"文件不是数据库"；迁移失败自动保留明文库）
                // 2. 加载 SQLCipher 原生库（必须先于任何 SQLCipher API 调用）
                // 3. 从 Android Keystore 取口令（密钥不落盘明文，Keystore 不可用直接抛异常）
                // 4. 通过 SupportOpenHelperFactory 注入，Room 读写全程由 SQLCipher 接管
                // 注意：数据库文件名与所有 Migration 保持不变，业务代码零改动
                val dbName = activeDatabaseName()
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbName
                )
                    .addMigrations(*AppDatabaseMigrations.ALL)
                    .addCallback(AppDatabaseMigrations.DB_CALLBACK)

                if (encryptionEnabled) {
                    val migrated = com.shangmentiyu.sportscoach.data.internal.PlainDbMigrator
                        .migrateIfNeeded(context.applicationContext, dbName)
                    if (!migrated) {
                        throw IllegalStateException(
                            "数据库加密失败：无法把现有数据迁移到加密格式。\n" +
                                "你的原始数据未被改动，请联系开发者。"
                        )
                    }
                    ensureSqlcipherLoaded()
                    // 口令为 64 位十六进制字符串；转成 UTF-8 字节交给 SQLCipher 做 PBKDF2，
                    // 与 SQL 层 ATTACH ... KEY '<同一字符串>' 派生结果一致（见 DatabaseKeyManager）
                    val passphrase =
                        com.shangmentiyu.sportscoach.data.internal.DatabaseKeyManager
                            .getOrCreatePassphraseBytes(context.applicationContext)
                    builder.openHelperFactory(
                        net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase)
                    )
                }

                val instance = builder
                    // === 终极防丢机制：严禁任何破坏性清库 fallback ===
                    // 历史教训：fallbackToDestructiveMigrationOnDowngrade() 在数据库文件版本
                    // 大于代码版本时，会直接删除整个数据库重建，导致学员数据全部丢失。
                    // 现在移除所有 destructive fallback：
                    // - 升级无 migration → Room 抛 IllegalStateException（App 闪退，数据不丢）
                    // - 降级 → Room 抛 IllegalStateException（App 闪退，数据不丢）
                    // 启动前的避风港备份已确保即使闪退，数据也可从 filesDir/PreUpdateBackup/ 恢复
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * 当前模式对应的数据库文件名（v23.12 多租户·物理隔离）。
         *
         * 上门体育 = sports_coach_db（既有库）；俱乐部 = sports_coach_club_db（独立库）。
         * 两个库共用同一套 Entity/DAO/Migration（schema 相同，version 一起走）。
         * 选库依据 [ModeManager.activeMode] 必须在首次 getDatabase 前由
         * Application.onCreate 同步初始化。
         */
        fun activeDatabaseName(): String =
            if (com.shangmentiyu.sportscoach.data.internal.ModeManager.activeMode ==
                com.shangmentiyu.sportscoach.data.internal.ModeManager.MODE_CLUB) {
                CLUB_DATABASE_NAME
            } else {
                DATABASE_NAME
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

        /** 俱乐部模式专用库（v23.12 物理隔离；schema 与主库完全一致） */
        const val CLUB_DATABASE_NAME = "sports_coach_club_db"

        /**
         * 当前代码声明的数据库版本（与 @Database version 保持一致）。
         *
         * 用于在 [com.shangmentiyu.sportscoach.data.internal.PreUpdateBackupManager.checkVersionAndEmergencyBackup]
         * 中与数据库文件实际版本对比，检测降级场景；
         * 也用于 [com.shangmentiyu.sportscoach.data.internal.BackupManager] 恢复前
         * 对比备份库版本，拒绝来自更高版本 App 的备份，防止恢复后闪退。
         *
         * 修改 @Database version 时必须同步修改此常量，否则版本检查会失效。
         */
        const val DATABASE_VERSION = 35
    }
}
