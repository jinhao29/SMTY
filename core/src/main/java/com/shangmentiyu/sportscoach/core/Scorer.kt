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
    /** 解析用户输入为数值 */
    fun parseValue(raw: String?, unit: String): Double {
        if (raw.isNullOrBlank()) throw IllegalArgumentException("成绩为空")
        val s = raw.trim()
        if (unit == "分秒") return parseTime(s)
        return s.toDoubleOrNull() ?: throw IllegalArgumentException("格式错误")
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
