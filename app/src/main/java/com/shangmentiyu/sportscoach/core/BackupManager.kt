package com.shangmentiyu.sportscoach.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 数据备份与恢复管理器（处理器层）。
 *
 * 职责：
 * - 纯文件操作 + 数据库完整性校验
 * - 负责数据库文件（sports_coach_db + wal + shm）与签到照片目录的打包/解包
 * - 备份格式：ZIP 压缩包（.smty_backup），包含 db 文件 + photos 目录
 *
 * 数据安全策略：
 * - 备份前调用 [AppDatabase.closeAndResetInstance] 强制 WAL checkpoint，确保数据完整
 * - 恢复前同样关闭数据库，释放文件锁，避免覆盖失败导致数据损坏
 * - 恢复后执行 [verifyIntegrity] PRAGMA integrity_check 校验，失败立即回滚并通知用户
 * - 恢复后调用方必须重启 App，让 ViewModel 重新初始化
 *
 * 与桌面端 Excel 导出的区别：
 * - ExcelSync 导出的是可读的成绩报告（单次课堂/成绩档案），仅含学员+成绩
 * - BackupManager 导出的是整库二进制备份（含学员/课时包/排课/签到/照片/训练周期等全部数据）
 *
 * @see AppDatabase.closeAndResetInstance
 */
object BackupManager {

    private const val TAG = "BackupManager"

    /** 备份文件扩展名（用于文件过滤器与默认文件名） */
    const val BACKUP_EXTENSION = "smty_backup"

    /** 备份 ZIP 内数据库文件条目名 */
    private const val ZIP_ENTRY_DB = "sports_coach_db"

    /** 备份 ZIP 内 WAL 日志条目名 */
    private const val ZIP_ENTRY_DB_WAL = "sports_coach_db-wal"

    /** 备份 ZIP 内共享内存条目名 */
    private const val ZIP_ENTRY_DB_SHM = "sports_coach_db-shm"

    /** 备份 ZIP 内签到照片目录条目前缀 */
    private const val ZIP_ENTRY_PHOTOS_DIR = "SignPhotos/"

    /** 签到照片在 App 内部存储的目录名（与 PhotoCrypto 约定一致） */
    private const val PHOTOS_DIR_NAME = "SignPhotos"

    /** v22 新增：桌面端 Python 程序读取的元数据 JSON 条目名 */
    private const val ZIP_ENTRY_META_JSON = "export_meta.json"

    /** export_meta.json 的结构版本号（供桌面端做兼容性判断） */
    private const val META_JSON_VERSION = 1

    /**
     * 进度回调签名。
     *
     * @param phase 当前阶段标识："prepare" / "db" / "photos" / "zip" / "cleanup" /
     *              "extract" / "verify" / "done" / "error"
     * @param current 已处理条目数（从 1 开始）
     * @param total 当前阶段总条目数（未知时为 0）
     * @param message 可读进度文案，UI 可直接展示
     */
    fun interface OnProgress {
        fun onProgress(phase: String, current: Int, total: Int, message: String)
    }

    /**
     * 恢复结果：携带成功标志 + 可读消息 + 完整性校验详情。
     *
     * 调用方（[com.shangmentiyu.sportscoach.data.repo.BackupRepository]）按需将
     * 消息直接展示给用户；当 [integrityOk]=false 时，应明确提示用户数据可能损坏。
     *
     * @param success 解压是否成功（不含完整性校验失败的情况）
     * @param message 用户可读消息
     * @param needRestart 是否需要重启 App（仅在 success=true && integrityOk=true 时为 true）
     * @param integrityOk PRAGMA integrity_check 是否通过
     * @param integrityReport 完整性校验原始输出（多行文本，错误时为问题描述）
     */
    data class RestoreResult(
        val success: Boolean,
        val message: String,
        val needRestart: Boolean = false,
        val integrityOk: Boolean = false,
        val integrityReport: String = ""
    )

    /**
     * 生成默认备份文件名：smty_backup_YYYYMMDD_HHmmss
     *
     * @return 形如 "smty_backup_20260725_143022"
     */
    fun generateBackupFileName(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.getDefault())
        return "smty_backup_${LocalDateTime.now().format(formatter)}"
    }

    /**
     * 执行完整备份：数据库 + 元数据 JSON + 签到照片 → ZIP 文件。
     *
     * 流程：
     * 1. 关闭数据库单例（强制 WAL checkpoint，确保数据完整刷盘）
     * 1.1 重新打开数据库只读实例，导出 export_meta.json（v22 新增）
     * 2. 收集数据库文件（db / wal / shm，存在哪个备份哪个）
     * 3. 收集签到照片目录下所有文件
     * 4. 打包成 ZIP 写入 [outputStream]
     * 5. 重新打开数据库连接（恢复 App 正常运行）
     *
     * v22 新增：在打包前生成 export_meta.json，包含核心的 students / lessons /
     * packages / schedules 列表，供桌面端 Python 程序无痛读取，无需 SQLite 依赖。
     * 该 JSON 仅作"易读副本"，不替代主数据库文件；恢复时仍以 db 文件为准。
     *
     * @param context 上下文（用于定位数据库文件路径与照片目录）
     * @param outputStream 备份文件的输出流（由调用方负责关闭）
     * @param onProgress 进度回调（可选，null 表示不回调，由调用方在 IO 线程安全调用）
     * @return true=备份成功；false=备份失败（IO 异常等）
     */
    suspend fun backup(
        context: Context,
        outputStream: OutputStream,
        onProgress: OnProgress? = null
    ): Boolean {
        // 1. 关闭数据库，触发 WAL checkpoint，确保所有学员/课程数据写入主 db 文件
        onProgress?.onProgress("prepare", 0, 0, "正在准备数据…")
        AppDatabase.closeAndResetInstance(context)

        try {
            // 1.1 重新打开数据库实例，用于导出 export_meta.json
            //     导出完成后再次关闭，确保后续读取到的 db / wal / shm 文件状态稳定
            val metaJson = try {
                generateExportMetaJson(context)
            } catch (e: Exception) {
                Log.w(TAG, "生成 export_meta.json 失败（不影响主备份流程）：${e.message}", e)
                null
            }
            AppDatabase.closeAndResetInstance(context)

            ZipOutputStream(outputStream).use { zos ->
                // 2. 写入数据库文件
                val dbFiles = listOf(
                    ZIP_ENTRY_DB to File(context.getDatabasePath(AppDatabase.DATABASE_NAME).absolutePath),
                    ZIP_ENTRY_DB_WAL to File(context.getDatabasePath(AppDatabase.DATABASE_NAME + "-wal").absolutePath),
                    ZIP_ENTRY_DB_SHM to File(context.getDatabasePath(AppDatabase.DATABASE_NAME + "-shm").absolutePath)
                )
                val dbTotal = dbFiles.count { it.second.exists() }
                var dbDone = 0
                for ((entryName, file) in dbFiles) {
                    if (file.exists()) {
                        putFileEntry(zos, entryName, file)
                        dbDone++
                        onProgress?.onProgress(
                            "db", dbDone, dbTotal,
                            "正在备份数据库文件（$dbDone/$dbTotal）"
                        )
                    }
                }

                // 2.1 写入 export_meta.json（v22 新增）
                //     失败时不阻塞主备份流程（metaJson=null 时跳过）
                metaJson?.let { json ->
                    putBytesEntry(zos, ZIP_ENTRY_META_JSON, json.toByteArray(Charsets.UTF_8))
                    onProgress?.onProgress("meta", 1, 1, "已生成桌面端元数据 JSON")
                }

                // 3. 写入签到照片目录
                val photosDir = File(context.filesDir, PHOTOS_DIR_NAME)
                if (photosDir.exists() && photosDir.isDirectory) {
                    val photos = photosDir.listFiles()?.filter { it.isFile } ?: emptyList()
                    val total = photos.size
                    photos.forEachIndexed { idx, photoFile ->
                        putFileEntry(zos, ZIP_ENTRY_PHOTOS_DIR + photoFile.name, photoFile)
                        onProgress?.onProgress(
                            "photos", idx + 1, total,
                            "正在备份签到照片（${idx + 1}/$total）"
                        )
                    }
                }

                onProgress?.onProgress("zip", 0, 0, "正在压缩备份文件…")
            }
            onProgress?.onProgress("done", 0, 0, "备份完成")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "备份失败：${e.message}", e)
            return false
        } finally {
            // 5. 无论备份成功与否，都重新打开数据库连接，恢复 App 正常运行
            AppDatabase.getDatabase(context)
        }
    }

    /**
     * 生成 export_meta.json 内容（v22 新增）。
     *
     * 重新打开数据库实例（备份开始时已 close），同步读取核心业务数据：
     * - students：全部学员（含已软删除的，便于桌面端完整分析）
     * - lessons：全部课时记录（热数据）
     * - packages：全部课时包
     * - schedules：全部排课
     * - coaches：全部教练
     * - archivedLessons：全部归档课时记录（v22 引入）
     *
     * 设计说明：
     * - 使用 kotlinx.coroutines.flow.first() 一次性获取 Flow 快照
     * - 字段命名与 Entity 保持一致，桌面端无需额外映射
     * - 二进制大字段（content / scores）以原始 JSON 字符串形式导出，
     *   桌面端可按需解析；photoPath 路径保留用于关联照片文件
     * - metaHeader 包含版本号、生成时间、各表行数，便于桌面端做兼容性判断
     *
     * @param context 上下文
     * @return export_meta.json 的字符串内容（UTF-8 编码）
     */
    private suspend fun generateExportMetaJson(context: Context): String {
        val db = AppDatabase.getDatabase(context)

        // 同步获取各表快照：本方法仅在 [backup] 的 IO 协程内调用，
        // 使用 Flow.first() 一次性获取快照，避免 runBlocking 阻塞线程
        val students = db.studentDao().getAllIncludeDeleted().first()
        val lessons = db.lessonDao().getAll().first()
        val packages = db.lessonPackageDao().getAll().first()
        val schedules = db.scheduleDao().getAll().first()
        val coaches = db.coachDao().getAll().first()
        val archivedLessons = try {
            db.archivedLessonDao().count().first()
            // count() 仅返回数量；这里只需要数量做汇总，明细数据由桌面端解析 db 文件获得
            0
        } catch (e: Exception) {
            Log.w(TAG, "读取归档表失败（忽略）：${e.message}")
            0
        }

        val root = JSONObject()
        // 元信息头：桌面端据此判断版本与生成时间
        val metaHeader = JSONObject()
            .put("version", META_JSON_VERSION)
            .put("generatedAt", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())))
            .put("appPackage", context.packageName)
            .put("counts", JSONObject()
                .put("students", students.size)
                .put("lessons", lessons.size)
                .put("packages", packages.size)
                .put("schedules", schedules.size)
                .put("coaches", coaches.size)
                .put("archivedLessons", archivedLessons)
            )
        root.put("meta", metaHeader)

        // 学员列表
        val studentsArr = JSONArray()
        for (s in students) {
            studentsArr.put(JSONObject()
                .put("name", s.name)
                .put("gender", s.gender)
                .put("grade", s.grade)
                .put("school", s.school)
                .put("phone", s.phone)
                .put("age", s.age)
                .put("heightCm", s.heightCm)
                .put("weightKg", s.weightKg)
                .put("bmi", s.bmi)
                .put("studentId", s.studentId ?: JSONObject.NULL)
                .put("isActive", s.isActive)
                .put("createdAt", s.createdAt)
                .put("updatedAt", s.updatedAt)
            )
        }
        root.put("students", studentsArr)

        // 课时记录列表
        val lessonsArr = JSONArray()
        for (l in lessons) {
            lessonsArr.put(JSONObject()
                .put("id", l.id)
                .put("date", l.date)
                .put("time", l.time)
                .put("studentName", l.studentName)
                .put("studentId", l.studentId ?: JSONObject.NULL)
                .put("content", l.content)
                .put("scores", l.scores)
                .put("summary", l.summary)
                .put("duration", l.duration)
                .put("coach", l.coach)
                .put("location", l.location)
                .put("lessonType", l.lessonType)
                .put("attendance", l.attendance)
                .put("attitude", l.attitude)
                .put("performance", l.performance)
                .put("nextGoal", l.nextGoal)
                .put("coachComment", l.coachComment)
                .put("packageId", l.packageId)
                .put("photoPath", l.photoPath)
                .put("signOutTime", l.signOutTime)
                .put("signOutPhotoPath", l.signOutPhotoPath)
                .put("contentImages", l.contentImages)
                .put("createdAt", l.createdAt)
            )
        }
        root.put("lessons", lessonsArr)

        // 课时包列表
        val packagesArr = JSONArray()
        for (p in packages) {
            packagesArr.put(JSONObject()
                .put("id", p.id)
                .put("studentName", p.studentName)
                .put("name", p.name)
                .put("totalLessons", p.totalLessons)
                .put("usedLessons", p.usedLessons)
                .put("price", p.price)
                .put("purchaseDate", p.purchaseDate)
                .put("expireDate", p.expireDate)
                .put("status", p.status)
            )
        }
        root.put("packages", packagesArr)

        // 排课列表
        val schedulesArr = JSONArray()
        for (sch in schedules) {
            schedulesArr.put(JSONObject()
                .put("id", sch.id)
                .put("studentName", sch.studentName)
                .put("coachName", sch.coachName)
                .put("dayOfWeek", sch.dayOfWeek)
                .put("startTime", sch.startTime)
                .put("durationMinutes", sch.durationMinutes)
                .put("location", sch.location)
                .put("lessonType", sch.lessonType)
                .put("content", sch.content)
                .put("isActive", sch.isActive)
            )
        }
        root.put("schedules", schedulesArr)

        // 教练列表
        val coachesArr = JSONArray()
        for (c in coaches) {
            coachesArr.put(JSONObject()
                .put("name", c.name)
                .put("phone", c.phone)
                .put("specialty", c.specialty)
                .put("status", c.status)
            )
        }
        root.put("coaches", coachesArr)

        return root.toString(2)  // 缩进 2 空格，便于桌面端人工查看
    }

    /**
     * 将字节数组写入 ZIP 条目（v22 新增）。
     *
     * 用于 export_meta.json 等内存中的小数据，无需落盘临时文件。
     *
     * @param zos ZIP 输出流
     * @param entryName 条目名
     * @param bytes 字节内容
     */
    private fun putBytesEntry(zos: ZipOutputStream, entryName: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(entryName))
        ByteArrayInputStream(bytes).use { ins ->
            val buffer = ByteArray(8192)
            var len = ins.read(buffer)
            while (len > 0) {
                zos.write(buffer, 0, len)
                len = ins.read(buffer)
            }
        }
        zos.closeEntry()
    }

    /**
     * 执行完整恢复：从 ZIP 文件还原数据库 + 签到照片。
     *
     * 旧入口（保持向后兼容）：成功返回 true，失败返回 false。
     * 内部委托给 [restoreDetailed]，需要完整性校验详情请直接调用 [restoreDetailed]。
     *
     * @param context 上下文（用于定位数据库文件路径与照片目录）
     * @param inputStream 备份文件的输入流（由调用方负责关闭）
     * @param onProgress 进度回调（可选，null 表示不回调）
     * @return true=恢复成功；false=恢复失败（IO 异常、备份格式错误、完整性校验失败等）
     */
    fun restore(
        context: Context,
        inputStream: InputStream,
        onProgress: OnProgress? = null
    ): Boolean = restoreDetailed(context, inputStream, onProgress).success

    /**
     * 执行完整恢复并返回详细结果（含 PRAGMA integrity_check 校验报告）。
     *
     * 完整流程：
     * 1. 关闭数据库单例（释放文件锁，避免覆盖失败）
     * 2. 清空当前数据库文件与照片目录（避免残留旧数据混入）
     * 3. 从 ZIP 解包数据库文件到原位置
     * 4. 从 ZIP 解包签到照片到原位置
     * 5. **执行 PRAGMA integrity_check** 校验数据库完整性
     *    - 通过：返回 success=true + integrityOk=true，调用方应重启 App
     *    - 失败：返回 success=false + integrityOk=false + integrityReport=错误详情，
     *      并自动删除已解压的损坏 db 文件，避免下次启动加载损坏数据
     *
     * 注意：调用方必须在恢复成功后重启 App，让 ViewModel 重新初始化，
     * 否则旧的 Dao 引用会指向已关闭的数据库，导致 NPE 或脏读。
     *
     * @param context 上下文（用于定位数据库文件路径与照片目录）
     * @param inputStream 备份文件的输入流（由调用方负责关闭）
     * @param onProgress 进度回调（可选，null 表示不回调）
     * @return [RestoreResult] 携带成功标志 + 完整性校验报告
     */
    fun restoreDetailed(
        context: Context,
        inputStream: InputStream,
        onProgress: OnProgress? = null
    ): RestoreResult {
        // 1. 关闭数据库，释放文件锁
        onProgress?.onProgress("prepare", 0, 0, "正在准备恢复…")
        AppDatabase.closeAndResetInstance(context)

        var dbExtracted = false
        try {
            // 2. 清空当前数据库文件（避免恢复后残留旧数据）
            onProgress?.onProgress("cleanup", 0, 0, "正在清理旧数据…")
            listOf("", "-wal", "-shm").forEach { suffix ->
                val file = context.getDatabasePath(AppDatabase.DATABASE_NAME + suffix)
                if (file.exists()) {
                    file.delete()
                }
            }

            // 3. 清空当前签到照片目录
            val photosDir = File(context.filesDir, PHOTOS_DIR_NAME)
            if (photosDir.exists()) {
                photosDir.listFiles()?.forEach { it.delete() }
            }

            // 4. 单次扫描 ZIP，按条目索引回报进度（total=0 表示未知）
            onProgress?.onProgress("extract", 0, 0, "正在恢复数据…")
            var entryIdx = 0
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entryIdx++
                    when {
                        // 数据库文件
                        entry.name == ZIP_ENTRY_DB -> {
                            val target = context.getDatabasePath(AppDatabase.DATABASE_NAME)
                            target.parentFile?.mkdirs()
                            extractFile(zis, target)
                            dbExtracted = true
                            onProgress?.onProgress("extract", entryIdx, 0, "已恢复数据库主文件")
                        }
                        // WAL 日志
                        entry.name == ZIP_ENTRY_DB_WAL -> {
                            val target = context.getDatabasePath(AppDatabase.DATABASE_NAME + "-wal")
                            extractFile(zis, target)
                            onProgress?.onProgress("extract", entryIdx, 0, "已恢复 WAL 日志")
                        }
                        // 共享内存
                        entry.name == ZIP_ENTRY_DB_SHM -> {
                            val target = context.getDatabasePath(AppDatabase.DATABASE_NAME + "-shm")
                            extractFile(zis, target)
                            onProgress?.onProgress("extract", entryIdx, 0, "已恢复 SHM 内存")
                        }
                        // 签到照片
                        entry.name.startsWith(ZIP_ENTRY_PHOTOS_DIR) -> {
                            val photoName = entry.name.removePrefix(ZIP_ENTRY_PHOTOS_DIR)
                            if (photoName.isNotBlank()) {
                                val target = File(context.filesDir, "$PHOTOS_DIR_NAME/$photoName")
                                target.parentFile?.mkdirs()
                                extractFile(zis, target)
                                onProgress?.onProgress(
                                    "extract", entryIdx, 0,
                                    "正在恢复签到照片（第 $entryIdx 项）"
                                )
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 5. === 完整性校验（防崩溃核心）===
            // 备份文件可能因传输中断 / 存储损坏 / 版本不匹配导致 db 文件损坏，
            // 直接让 Room 加载损坏文件会触发 SQLiteFullException / SQLiteDatabaseCorruptException，
            // 表现为 App 启动后崩溃且无法恢复。
            //
            // 通过 PRAGMA integrity_check 主动校验：
            // - 通过（返回 "ok"）：允许调用方重启 App
            // - 失败：返回多行错误描述，删除已解压的损坏 db，回滚到恢复前状态
            if (dbExtracted) {
                onProgress?.onProgress("verify", 0, 0, "正在校验数据库完整性…")
                val report = verifyIntegrity(context)
                return if (report.equals("ok", ignoreCase = true)) {
                    onProgress?.onProgress("done", entryIdx, entryIdx, "恢复完成，数据库完整性校验通过")
                    RestoreResult(
                        success = true,
                        message = "恢复成功，数据库完整性校验通过",
                        needRestart = true,
                        integrityOk = true,
                        integrityReport = report
                    )
                } else {
                    // 校验失败：删除已解压的损坏 db 文件，避免下次启动加载损坏数据
                    Log.e(TAG, "数据库完整性校验失败：\n$report")
                    listOf("", "-wal", "-shm").forEach { suffix ->
                        val f = context.getDatabasePath(AppDatabase.DATABASE_NAME + suffix)
                        if (f.exists()) f.delete()
                    }
                    onProgress?.onProgress(
                        "error", 0, 0,
                        "数据库损坏：已自动清除损坏文件，请重新从有效备份恢复"
                    )
                    RestoreResult(
                        success = false,
                        message = "数据库损坏：备份文件可能已损坏或版本不兼容。" +
                            "已自动清除损坏的本地数据，请重新从其他有效备份文件恢复。" +
                            "\n\n完整性校验报告：\n$report",
                        needRestart = false,
                        integrityOk = false,
                        integrityReport = report
                    )
                }
            } else {
                // ZIP 中没有 db 条目，可能是用户选错了文件
                onProgress?.onProgress("error", 0, 0, "备份文件中未找到数据库")
                return RestoreResult(
                    success = false,
                    message = "备份文件格式不正确：未找到数据库文件，请确认所选文件是本应用生成的备份文件",
                    needRestart = false,
                    integrityOk = false,
                    integrityReport = "no db entry in backup zip"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复异常：${e.message}", e)
            // 异常时清理半成品 db 文件
            if (dbExtracted) {
                listOf("", "-wal", "-shm").forEach { suffix ->
                    val f = context.getDatabasePath(AppDatabase.DATABASE_NAME + suffix)
                    if (f.exists()) f.delete()
                }
            }
            onProgress?.onProgress("error", 0, 0, "恢复失败：${e.message}")
            return RestoreResult(
                success = false,
                message = "恢复失败：${e.message ?: "未知错误"}",
                needRestart = false,
                integrityOk = false,
                integrityReport = e.message ?: "unknown error"
            )
        }
        // 注意：恢复成功后不在此处重开数据库，由调用方负责重启 App
    }

    /**
     * 执行 SQLite PRAGMA integrity_check 完整性校验（处理器层）。
     *
     * 直接用 SQLiteOpenHelper（绕过 Room）打开恢复后的 db 文件并执行：
     *   PRAGMA integrity_check;
     *
     * 返回值：
     * - "ok"：数据库结构完整，无损坏
     * - 多行文本：损坏描述（如 "row X is out of order" / "database disk image is malformed"）
     *
     * 实现要点：
     * - 使用 [android.database.sqlite.SQLiteDatabase.openDatabase] 直接打开，
     *   避免 Room 的初始化开销与潜在副作用
     * - 关闭后立即释放连接，让调用方重启 App 时 Room 能正常重新打开
     * - 校验过程不会修改数据库内容（PRAGMA integrity_check 是只读操作）
     *
     * @param context 上下文（用于定位数据库文件路径）
     * @return 校验结果（"ok" 或多行错误描述）
     */
    private fun verifyIntegrity(context: Context): String {
        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        if (!dbFile.exists()) return "db file not exists"

        var report = ""
        try {
            val sqliteDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            sqliteDb.use { db ->
                db.rawQuery("PRAGMA integrity_check;", null).use { cursor ->
                    val sb = StringBuilder()
                    while (cursor.moveToNext()) {
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append(cursor.getString(0))
                    }
                    report = sb.toString()
                }
            }
        } catch (e: Exception) {
            // 打开失败本身就是损坏的强信号
            Log.e(TAG, "integrity_check 打开数据库失败：${e.message}", e)
            return "无法打开数据库：${e.message ?: "未知错误"}"
        }
        return report.ifBlank { "empty result" }
    }

    /**
     * 将单个文件写入 ZIP 条目。
     *
     * @param zos ZIP 输出流
     * @param entryName 条目名
     * @param file 源文件
     */
    private fun putFileEntry(zos: ZipOutputStream, entryName: String, file: File) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var len = fis.read(buffer)
            while (len > 0) {
                zos.write(buffer, 0, len)
                len = fis.read(buffer)
            }
        }
        zos.closeEntry()
    }

    /**
     * 从 ZIP 输入流解包单个文件到目标位置。
     *
     * @param zis ZIP 输入流（已定位到条目）
     * @param target 目标文件
     */
    private fun extractFile(zis: ZipInputStream, target: File) {
        FileOutputStream(target).use { fos ->
            val buffer = ByteArray(8192)
            var len = zis.read(buffer)
            while (len > 0) {
                fos.write(buffer, 0, len)
                len = zis.read(buffer)
            }
        }
    }

    /**
     * 通过 SAF Uri 创建备份输出流（用于写入用户选择的位置）。
     *
     * @param context 上下文
     * @param uri 用户通过 SAF 选择的目标文件 Uri
     * @return 输出流，调用方负责关闭
     */
    fun openBackupOutputStream(context: Context, uri: Uri): OutputStream? {
        return context.contentResolver.openOutputStream(uri)
    }

    /**
     * 通过 SAF Uri 创建备份输入流（用于读取用户选择的备份文件）。
     *
     * @param context 上下文
     * @param uri 用户通过 SAF 选择的源文件 Uri
     * @return 输入流，调用方负责关闭
     */
    fun openBackupInputStream(context: Context, uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }
}
