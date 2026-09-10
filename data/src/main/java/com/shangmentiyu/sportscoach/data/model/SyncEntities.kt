package com.shangmentiyu.sportscoach.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * PC 收费记录镜像（v35，PC→手机只读同步）。
 *
 * - PC 端 收费记录.xlsx 是唯一权威源；手机端仅镜像展示，不提供编辑/删除。
 * - 主键 [id] 由自然键（学员|日期|金额|课时|方式|备注）派生：PC 端重复推送
 *   同一批记录时幂等（REPLACE 同键覆盖），PC 删除的记录手机端保留（删除不传播）。
 */
@Entity(
    tableName = "fee_records",
    indices = [Index(value = ["studentName"]), Index(value = ["date"])]
)
data class FeeRecord(
    @PrimaryKey val id: String,           // 自然键摘要（stableKey 生成）
    val studentName: String,
    val date: String,                     // YYYY-MM-DD
    val amount: Double,                   // 金额（元）
    val hours: Double,                    // 对应课时数
    val method: String,                   // 收款方式
    val note: String,                     // 备注
    val syncedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** 由 PC 收费记录自然键生成确定性主键：同一条收费在重复同步下 id 恒定 */
        fun stableKey(studentName: String, date: String, amount: Double,
                      hours: Double, method: String, note: String): String {
            val raw = "$studentName|$date|$amount|$hours|$method|$note"
            val digest = java.security.MessageDigest.getInstance("MD5")
                .digest(raw.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * PC 消课对账状态（v35）：记录每个学员已从 PC 折算进课时包已用的「PC 独录消课数」。
 *
 * 背景：PC 端直接录课时（手机端无记录）时，PC 已上课时 > 手机端扣课事实。
 * 同步按差值把 PC 独录消课折算进手机课时包 usedLessons，[appliedPcLessons]
 * 记录已折算的累计值，保证重复同步幂等、且单调不减（PC 删除消课不回退手机）。
 */
@Entity(tableName = "pc_sync_state")
data class PcSyncState(
    @PrimaryKey val studentName: String,
    val appliedPcLessons: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * 删除墓碑标记（2026-09-10）：deleteStudent 物理删除学员行后，
         * 在 pc_sync_state 写入 appliedPcLessons = TOMBSTONE 的行。
         * PC→手机 students.xlsx 同步（importStudentsBlockingUpdatePart）
         * 对墓碑命中的名字跳过新增，防止 PC 端仍有档案的学员被同步复活。
         * 用户重新添加同名学员（addStudent*）时清除墓碑。
         */
        const val TOMBSTONE = -1

        fun tombstone(studentName: String) = PcSyncState(
            studentName = studentName,
            appliedPcLessons = TOMBSTONE
        )
    }
}
