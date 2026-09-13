    package com.shangmentiyu.sportscoach.core

/** 评分结果 */
data class ScoreResult(
    val score: Double?,    // 得分0-100，null表示失败
    val grade: String,     // 等级
    val value: Double?,    // 解析后的数值
    val ok: Boolean,       // 是否成功
    val msg: String        // 错误信息
)

object Scorer {
    /**
     * 全角 → 半角归一化。
     *
     * 真机观察（2026-09-13，vivo）：中文输入法在成绩输入框里会把句点打成全角「。」，
     * 小数点/冒号/引号也可能整体用全角（．：＇＂），于是用户看着是「7。5」，
     * 系统判「格式错误」——属可用性缺陷，不是用户输错。
     *
     * 规则（与 PC `scorer.normalize_input` 同一张映射表，跨端口径一致）：
     * 1. U+FF01–U+FF5E 全角 ASCII 整体平移 0xFEE0（覆盖 ．：＇＂＋－ and ０-９）
     * 2. 「。」U+3002 → '.'（中文句号，IME 最常见的误产）
     * 3. 「−」U+2212 → '-'（真减号，被 IME/复制粘贴引入时会绕过负数拦截）
     */
    fun normalizeInput(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            sb.append(
                when (ch.code) {
                    in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
                    0x3002 -> '.'    // 。
                    0x2212 -> '-'    // −
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    /** 解析用户输入为数值 */
    fun parseValue(raw: String?, unit: String): Double {
        if (raw.isNullOrBlank()) throw IllegalArgumentException("成绩为空")
        val s = normalizeInput(raw).trim()
        // v50 补丁：分秒分支同样拒绝负数（如 "-5" 秒），与非分秒分支口径一致
        if (unit == "分秒" && s.startsWith("-")) throw IllegalArgumentException("成绩不能为负数")
        if (unit == "分秒") return parseTime(s)
        // === v50：显式拒绝负数成绩 ===
        // 原实现直接 toDoubleOrNull()，负数（如 "-5"）会被 scoreLess 误判为满分，
        // 或由 coerceIn(0,100) 静默吸收；现统一抛"成绩不能为负数"，由上层 ok=false 拦截不入库
        val value = s.toDoubleOrNull() ?: throw IllegalArgumentException("格式错误")
        if (value < 0) throw IllegalArgumentException("成绩不能为负数")
        return value
    }

    /** 解析分秒为秒数 */
    private fun parseTime(s: String): Double {
        var str = s.replace("分", ":").replace("秒", "")
        str = str.replace("′", "'").replace("″", "\"").replace("’", "'").replace("”", "\"")
        // 4'05" 格式
        if ("'" in str && "\"" in str) {
            val parts = str.replace("\"", "").split("'")
            return timePartsToSeconds(parts)
        }
        // 4'05 格式
        if ("'" in str) {
            val parts = str.split("'")
            return timePartsToSeconds(parts)
        }
        // 4:05 格式
        if (":" in str) {
            val parts = str.split(":")
            return timePartsToSeconds(parts)
        }
        // 纯秒数
        return str.toDoubleOrNull() ?: throw IllegalArgumentException("时间格式错误")
    }

    /**
     * 将 "分" 与 "秒" 两段字符串解析为总秒数。
     *
     * 防御性解析：脏数据（如 "4'"、缺秒、含非数字）不会抛 IndexOutOfBounds / NumberFormat，
     * 而是返回 0.0 并提示格式错误，避免上层 try-catch 丢失上下文。
     */
    private fun timePartsToSeconds(parts: List<String>): Double {
        if (parts.size < 2) throw IllegalArgumentException("时间格式错误")
        val min = parts[0].toIntOrNull() ?: throw IllegalArgumentException("时间格式错误")
        val sec = parts[1].toDoubleOrNull() ?: throw IllegalArgumentException("时间格式错误")
        if (min < 0 || sec < 0 || sec >= 60) throw IllegalArgumentException("时间格式错误")
        return min * 60.0 + sec
    }

    /** 数值格式化为显示文本 */
    fun formatValue(value: Double?, unit: String): String {
        if (value == null) return ""
        if (unit == "分秒") {
            val m = (value / 60).toInt()
            val sec = value - m * 60
            return if (sec == sec.toInt().toDouble()) {
                "${m}'${sec.toInt().toString().padStart(2, '0')}\""
            } else {
                "${m}'${"%.1f".format(sec)}\""
            }
        }
        return if (value == value.toInt().toDouble()) value.toInt().toString() else value.toString()
    }

    /** 计算单项得分 */
    fun calcScore(std: Std, gender: String, rawValue: String): ScoreResult {
        val value = try {
            parseValue(rawValue, std.unit)
        } catch (e: Exception) {
            return ScoreResult(null, "", null, false, e.message ?: "解析失败")
        }
        val full = if (gender == "男") std.boysFull else std.girlsFull
        val pass = if (gender == "男") std.boysPass else std.girlsPass
        val score = if (std.direction == MORE) scoreMore(value, full, pass) else scoreLess(value, full, pass)
        val clamped = score.coerceIn(0.0, 100.0)
        return ScoreResult(clamped, gradeLabel(clamped), value, true, "")
    }

    private fun scoreMore(value: Double, full: Double, pass: Double): Double {
        if (value >= full) return 100.0
        if (value <= pass) {
            if (pass == 0.0) return if (value <= 0) 0.0 else (value / full * 60).coerceAtLeast(0.0)
            val ratio = (value / pass).coerceAtLeast(0.0)
            return ratio * 60.0
        }
        return 60.0 + (value - pass) / (full - pass) * 40.0
    }

    private fun scoreLess(value: Double, full: Double, pass: Double): Double {
        if (value <= full) return 100.0
        if (value >= pass) {
            val extra = value - pass
            val span = pass - full
            if (span <= 0) return 30.0
            return (60.0 - extra / span * 60.0).coerceAtLeast(0.0)
        }
        return 60.0 + (pass - value) / (pass - full) * 40.0
    }

    fun gradeLabel(score: Double): String {
        if (score >= 90) return "优秀"
        if (score >= 75) return "良好"
        if (score >= 60) return "及格"
        return "不及格"
    }

    /** 计算总分（有效得分的平均分） */
    fun calcTotal(scores: List<ScoreResult>): Double {
        val valid = scores.filter { it.ok }.mapNotNull { it.score }
        if (valid.isEmpty()) return 0.0
        return valid.sum() / valid.size
    }
}
